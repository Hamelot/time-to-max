package com.timetomax;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.Experience;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.Notifier;

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@PluginDescriptor(
	name = "Time to Max"
)
public class TimeToMaxPlugin extends Plugin
{
    // Static field to preserve baseline data between plugin toggle cycles
    private static SkillsTracker persistentTracker = null;
    private static boolean hadPreviousSession = false;
    
    // Config keys for persistent storage between client restarts
    private static final String ACTIVE_SESSION_KEY = "activeSession";
    private static final String SESSION_USERNAME_KEY = "sessionUsername";
    private static final String BASELINE_PERSIST_KEY = "timetomax.baseline.persist";
    private static boolean hasActiveBaseline = false;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private TimeToMaxConfig config;
	
	@Inject
	private ConfigManager configManager;
	
	@Inject
	private OverlayManager overlayManager;
	
	@Inject
	private ClientToolbar clientToolbar;
	
	@Inject
	private SkillIconManager skillIconManager;
	
	@Inject
	private Notifier notifier;
	
	private TimeToMaxPanel panel;
	private NavigationButton navButton;
	private SkillsTracker skillsTracker;
	private boolean waitingForPlayerName = false;
	private int loginTicks = 0;
	private static final int WAIT_TICKS = 5; // Wait 5 ticks after login before capturing snapshot
	
	// Add a flag to track if the ::ttm_resetxp hint has been shown this session
	private boolean resetXpHintShown = false;
	
	// Track the last known XP for each skill to detect changes
	private final Map<Skill, Integer> lastSkillXp = new EnumMap<>(Skill.class);
	
	// For tracking XP changes on each game tick
	private boolean initialXpSet = false;

	// Track active skills - ones that have had XP changes recently
	private final Map<Skill, Integer> activeSkills = new EnumMap<>(Skill.class);
	private static final int ACTIVE_SKILL_TIMEOUT = 50; // Keep skills active for 50 ticks (30 seconds)
	
	// Track which skills have already triggered a notification for this session
	private final Set<Skill> notifiedSkills = new HashSet<>();

	@Override
	protected void startUp() throws Exception
	{
		log.info("Time to Max plugin started!");
		
		// Create panel
		panel = injector.getInstance(TimeToMaxPanel.class);
		
		// Create navigation button with fallback to a default icon if the custom one isn't found
		BufferedImage icon;
		try {
			icon = ImageUtil.loadImageResource(getClass(), "timetomax.png");
		} catch (Exception e) {
			// Create a simple colored square as last resort
			icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = icon.createGraphics();
			graphics.setColor(Color.ORANGE);
			graphics.fillRect(0, 0, 32, 32);
			graphics.dispose();
		}
		
		navButton = NavigationButton.builder()
				.tooltip("Time to Max")
				.icon(icon)
				.priority(6)
				.panel(panel)
				.build();
		
		clientToolbar.addNavigation(navButton);
		
		// Reset state variables
		initialXpSet = false;
		waitingForPlayerName = false;
		activeSkills.clear();
		notifiedSkills.clear();
		lastSkillXp.clear();
		resetXpHintShown = false; // Reset the hint flag on startup
		
			// First, check if we have a tracker from plugin toggling
		if (persistentTracker != null) {
			log.info("Restoring previous session data from memory");
			skillsTracker = persistentTracker;
			hadPreviousSession = true;
			
			// Update the panel with the restored tracker
			if (panel != null) {
				panel.setSkillsTracker(skillsTracker);
			}
		} 
		// If no in-memory tracker, check for saved session data in config
		else {
			boolean hasActiveSession = Boolean.TRUE.equals(configManager.getConfiguration("timetomax", ACTIVE_SESSION_KEY, Boolean.class));
			String savedUsername = configManager.getConfiguration("timetomax", SESSION_USERNAME_KEY, String.class);
			
			if (hasActiveSession && savedUsername != null && !savedUsername.isEmpty()) {
				log.info("Found saved session data for user: {}", savedUsername);
				// We'll restore this once the player logs in
				hadPreviousSession = true;
			}
		}
		
		// If the client is already logged in, initialize the plugin as if just logged in
		if (client.getGameState() == GameState.LOGGED_IN) {
			log.info("Plugin started while player is logged in, initializing...");
			
			// Initialize the skills tracker if the player is already logged in
			clientThread.invoke(() -> {
				Player player = client.getLocalPlayer();
				
				if (player != null && player.getName() != null && !player.getName().isEmpty()) {
					String playerName = player.getName();
					log.debug("Player already logged in: {}", playerName);
					
					// Get saved username from config
					String savedUsername = configManager.getConfiguration("timetomax", SESSION_USERNAME_KEY, String.class);
					boolean hasActiveSession = Boolean.TRUE.equals(configManager.getConfiguration("timetomax", ACTIVE_SESSION_KEY, Boolean.class));
					
					// Check if we can restore a session from config
					if (skillsTracker == null && hasActiveSession && savedUsername != null && 
							savedUsername.equals(playerName)) {
						log.info("Restoring session data for {}", playerName);
						skillsTracker = new SkillsTracker(configManager, client, playerName);
						
						// Ensure we have valid baseline data
						if (skillsTracker.hasValidBaselineData()) {
							// Force a refresh of snapshot data with current XP values
							skillsTracker.forceSnapshotRefresh();
							hadPreviousSession = true;
						} else {
							// Try to load baseline from direct storage
							skillsTracker.tryLoadDirectBaseline();
							
							// If that worked, force a snapshot refresh
							if (skillsTracker.hasValidBaselineData()) {
								skillsTracker.forceSnapshotRefresh();
								hadPreviousSession = true;
							}
						}
					} 
					// Otherwise create a new tracker if needed
					else if (skillsTracker == null) {
						skillsTracker = new SkillsTracker(configManager, client, playerName);
						
						// Save the session info for persistence
						configManager.setConfiguration("timetomax", ACTIVE_SESSION_KEY, true);
						configManager.setConfiguration("timetomax", SESSION_USERNAME_KEY, playerName);
					}
					
					// Set the tracker in the panel
					if (panel != null) {
						panel.setSkillsTracker(skillsTracker);
					}
					
					// If we've restored a previous session, we don't need to capture initial values
					if (!hadPreviousSession) {
						// Capture initial XP values
						captureInitialXpValues();
					} else {
						// Just mark it as initialized
						initialXpSet = true;
						
						// Initialize the last known XP values from current values
						for (Skill skill : Skill.values()) {
							try {
								int xp = client.getSkillExperience(skill);
								lastSkillXp.put(skill, xp);
							} catch (Exception e) {
								log.warn("Error getting initial XP for {}", skill.getName(), e);
							}
						}
					}
					
					// Update the panel
					if (panel != null) {
						SwingUtilities.invokeLater(() -> panel.updateAllInfo());
					}
				}
				
				return true;
			});
		} else {
			// If not logged in, just show the empty panel
			panel.showNoData();
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Time to Max plugin stopped!");
		
		// Store the tracker in the static field for persistence between toggles
		if (skillsTracker != null) {
			log.debug("Preserving session data for next startup");
			persistentTracker = skillsTracker;
		}
		
		lastSkillXp.clear();
		activeSkills.clear();
		notifiedSkills.clear();
		
		clientToolbar.removeNavigation(navButton);
		panel = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			// We can't be sure the player name is available yet, so set a flag to wait for it
			waitingForPlayerName = true;
			loginTicks = 0;
			initialXpSet = false;
			activeSkills.clear();
		} else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
			// Reset state when logging out
			initialXpSet = false;
			waitingForPlayerName = false;
			activeSkills.clear();
			notifiedSkills.clear();
			
			if (panel != null) {
				panel.showNoData();
			}
		}
	}
	
	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted)
	{
		// Add command to reset XP baseline
		if (commandExecuted.getCommand().equals("ttm_resetxp"))
		{
			resetXpBaseline();
		}
		// Test command to set a future date for testing interval changes
		else if (commandExecuted.getCommand().equals("ttm_setdate") && commandExecuted.getArguments().length > 0)
		{
			try {
				// Try to parse the date from the argument (format: yyyy-MM-dd)
				String dateStr = commandExecuted.getArguments()[0];
				LocalDate testDate = LocalDate.parse(dateStr);
				
				// Set the override date
				SkillsTracker.setOverrideDate(testDate);
				
				// Force a check for interval changes immediately
				checkForDateChanges();
				
				// Show confirmation message
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
					"Time to Max: Test date set to " + dateStr + " - interval changes will use this date", null);
				
				// Update the panel
				if (panel != null) {
					SwingUtilities.invokeLater(() -> panel.updateAllInfo());
				}
			} catch (Exception e) {
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
					"Time to Max: Invalid date format. Use yyyy-MM-dd (e.g., 2025-05-01)", null);
			}
		}
		// Clear any test date and go back to using real time
		else if (commandExecuted.getCommand().equals("ttm_cleardate"))
		{
			SkillsTracker.clearOverrideDate();
			
			// Force a check for interval changes immediately
			checkForDateChanges();
			
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
				"Time to Max: Test date cleared, using real date now", null);
			
			// Update the panel
			if (panel != null) {
				SwingUtilities.invokeLater(() -> panel.updateAllInfo());
			}
		}
	}
	
	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		// Check if we're waiting for the player name
		if (waitingForPlayerName) {
			loginTicks++;
			
			// Get the local player and try to initialize the tracker
			Player player = client.getLocalPlayer();
			if (player != null && player.getName() != null && !player.getName().isEmpty()) {
				// We have a valid player name, initialize the tracker
				String playerName = player.getName();
				log.debug("Player name available: {}", playerName);
				
				if (skillsTracker == null) {
					skillsTracker = new SkillsTracker(configManager, client, playerName);
					
					// Check if we need to restore baseline data from previous session
					boolean hasPreviousBaselineData = skillsTracker.hasValidBaselineData();
					
						// If no baseline data found, try to load from direct storage
					if (!hasPreviousBaselineData) {
						skillsTracker.tryLoadDirectBaseline();
						hasPreviousBaselineData = skillsTracker.hasValidBaselineData();
					}
					
					// Set the current period key based on tracking interval
					skillsTracker.updateCurrentPeriodKey(config.trackingInterval());
					
					// Set the tracker in the panel when it's ready
					if (panel != null) {
						panel.setSkillsTracker(skillsTracker);
					}
					
					// If we're restored from a previous session, force a snapshot refresh
					if (hasPreviousBaselineData) {
						log.info("Found baseline data on login, refreshing snapshot");
						skillsTracker.forceSnapshotRefresh();
						hadPreviousSession = true;
					}
					
					// Regular welcome message
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
						"Time to Max: Tracking XP over " + config.trackingInterval() + " intervals.", null);
				}
				
				// Wait a few more ticks to ensure skills are loaded before capturing baseline
				if (loginTicks >= WAIT_TICKS) {
					captureInitialXpValues();
					waitingForPlayerName = false;
					
					// Check for a real date change only once at login
					checkForDateChanges();
					
					// Update the panel
					if (panel != null) {
						SwingUtilities.invokeLater(() -> panel.updateAllInfo());
					}
				}
			} else if (loginTicks > 20) {
				// If we've waited 20 ticks and still don't have a name, log a warning
				log.warn("Unable to get player name after {} ticks", loginTicks);
				waitingForPlayerName = false;
			}
			return;
		}
		
		// If we've initialized, manage active skills and check for XP changes
		if (initialXpSet) {
			
			// Process active skills timeouts
			processActiveSkills();
			
			// Only check for XP changes if we have active skills
			if (!activeSkills.isEmpty()) {
				checkForXpChanges();
				}
			
			// Check for date changes only once every 10 minutes (600 game ticks)
			// Game tick is ~0.6 seconds, so 600 ticks ≈ 6 minutes
			if (client.getTickCount() % 600 == 0) {
				checkForDateChanges();
			}
		}
	}
	
	/**
	 * Reset the XP baseline to current values - now public so the panel can access it
	 */
	public void resetXpBaseline()
	{
		if (skillsTracker != null && client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invoke(() -> {
				skillsTracker.resetBaseline(client);
				
				// Update lastSkillXp map with current values
				for (Skill skill : Skill.values())
				{
					try
					{
						int xp = client.getSkillExperience(skill);
						lastSkillXp.put(skill, xp);
					}
					catch (Exception e)
					{
						log.warn("Failed to update lastSkillXp for {}", skill.getName(), e);
					}
				}
				
				// Show confirmation message
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
					"Time to Max: XP baseline has been reset. All XP gains will be measured from now.", null);
				
				// Update the panel
				if (panel != null) {
					SwingUtilities.invokeLater(() -> panel.updateAllInfo());
				}
				
				// Clear the notified skills list when resetting the baseline
				notifiedSkills.clear();
				
				return true;
			});
		}
	}
	
	/**
	 * Process active skills - reduce their timeout counters and remove expired ones
	 */
	private void processActiveSkills() {
		// Create a list to hold skills that have timed out
		List<Skill> toRemove = new java.util.ArrayList<>();
		
		// Update all active skill timers
		for (Map.Entry<Skill, Integer> entry : activeSkills.entrySet()) {
			int newValue = entry.getValue() - 1;
			if (newValue <= 0) {
				toRemove.add(entry.getKey());
			} else {
				entry.setValue(newValue);
			}
		}
		
		// Remove any skills that have timed out
		for (Skill skill : toRemove) {
			activeSkills.remove(skill);
		}
	}
	
	/**
	 * Capture initial XP values for all skills
	 */
	private void captureInitialXpValues() {
		if (skillsTracker == null) {
			return;
		}
		
		captureXpSnapshot();
		initialXpSet = true;
		
		// Initialize the last known XP values
		for (Skill skill : Skill.values()) {
			try {
				int xp = client.getSkillExperience(skill);
				lastSkillXp.put(skill, xp);
				log.debug("Initial XP for {}: {}", skill.getName(), xp);
			} catch (Exception e) {
				log.warn("Error getting initial XP for {}", skill.getName(), e);
			}
		}
		
		// Only show the reset hint once per session
		if (!resetXpHintShown) {
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
				"Type ::ttm_resetxp to reset your XP baseline at any time.", null);
			resetXpHintShown = true;
		}
	}
	
	/**
	 * Check for XP changes - prioritizing active skills
	 */
	private void checkForXpChanges() {
		// Always check active skills first
		for (Skill skill : activeSkills.keySet()) {
			checkSkillXp(skill);
		}
	}
	
	/**
	 * Check if a specific skill's XP has changed
	 */
	private void checkSkillXp(Skill skill) {
		try {
			int currentXp = client.getSkillExperience(skill);
			Integer previousXp = lastSkillXp.get(skill);
			
			if (previousXp != null && currentXp > previousXp) {
				int gained = currentXp - previousXp;
				lastSkillXp.put(skill, currentXp);
				
				// Mark this skill as active for future checks
				activeSkills.put(skill, ACTIVE_SKILL_TIMEOUT);
				
				log.debug("XP change detected for {} on game tick: +{} XP", skill.getName(), gained);
				
				// Update the snapshot and display message
				handleXpGain(skill, gained);
			}
		} catch (Exception e) {
			log.warn("Error checking XP for {}", skill.getName(), e);
		}
	}
	
	@Subscribe
	public void onStatChanged(StatChanged statChanged)
	{
		if (client.getGameState() != GameState.LOGGED_IN || !initialXpSet) {
			return;
		}
		
		Skill skill = statChanged.getSkill();
		int xp = statChanged.getXp();
		
		// Mark this skill as active for future tick checks
		activeSkills.put(skill, ACTIVE_SKILL_TIMEOUT);
		
		// Check if we have the last XP value for this skill
		Integer lastXp = lastSkillXp.get(skill);
		if (lastXp == null) {
			lastSkillXp.put(skill, xp);
			return;
		}
		
		// If XP increased, display a message and update the snapshot
		if (xp > lastXp) {
			int xpGained = xp - lastXp;
			lastSkillXp.put(skill, xp);
			log.debug("StatChanged event: {} XP changed from {} to {} (+{})", 
				skill.getName(), lastXp, xp, xpGained);
			
			handleXpGain(skill, xpGained);
			
			// Check if skill reached level 99 - update the panel but don't send notifications
			int newLevel = Experience.getLevelForXp(xp);
			if (newLevel == 99 && xp >= XpCalculator.MAX_XP) {
				// Skill reached 99, update the panel to remove it
				if (panel != null) {
					SwingUtilities.invokeLater(() -> panel.updateAllInfo());
					}
				}
			}
		}
	
	/**
	 * Handle an XP gain for a skill
	 */
	private void handleXpGain(Skill skill, int xpGained) {
		// Update the latest snapshot (without affecting baseline)
		if (skillsTracker != null) {
			skillsTracker.updateLatestSnapshot(client);
		}
		
		// Get target date and interval info
		LocalDate targetDate;
		try {
			targetDate = LocalDate.parse(config.targetDate());
		} catch (DateTimeParseException e) {
			targetDate = LocalDate.now().plusMonths(6);
		}
			
		TrackingInterval interval = config.trackingInterval();
		int sessionXpGained = skillsTracker.getSessionXpGained(skill);
		int requiredXp = XpCalculator.getRequiredXpPerInterval(skillsTracker.getBaselineXp(skill), targetDate, interval);
			
		// Always update the panel when XP is gained
		if (panel != null) {
			SwingUtilities.invokeLater(() -> panel.updateAllInfo());
		}
			
		// Check if we need to send a notification for reaching the required XP
		if (config.showChatNotifications() && sessionXpGained >= requiredXp && requiredXp > 0) {
			// Only notify once per skill per session (until reset)
			if (!notifiedSkills.contains(skill)) {
				String notificationMessage = "";
                if (interval == TrackingInterval.WEEK || interval == TrackingInterval.MONTH) {
                    notificationMessage = String.format("You've gained enough XP in %s for this %s!",
                            skill.getName(),
                            interval.toString().toLowerCase());
                } else {
                    notificationMessage = String.format("You've gained enough XP in %s for today!", skill.getName());
                }

				// Send as a game chat message instead of a notification
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
					"Time to Max: " + notificationMessage, null);

				log.debug("Sent notification for {}: {}", skill.getName(), notificationMessage);
					
				// Mark this skill as notified
				notifiedSkills.add(skill);
			}
		}
	}
	
	private void captureXpSnapshot() {
		if (skillsTracker != null && client.getLocalPlayer() != null) {
			skillsTracker.captureSnapshot(client);
            log.debug("XP snapshot captured for {}", client.getLocalPlayer().getName());
		}
	}

	/**
	 * Check if we need to reset due to date changes (midnight, new week, new month)
	 */
	private void checkForDateChanges() {
		if (skillsTracker != null && initialXpSet) {
			// Check if we should reset based on the tracking interval
			if (skillsTracker.shouldResetBaseline(config.trackingInterval())) {
				log.info("Detected new {} interval - automatically resetting baseline", config.trackingInterval());
				
				// Reset the XP baseline
				skillsTracker.resetBaseline(client);
				
				// Clear the notified skills list when resetting the baseline
				notifiedSkills.clear();
				
				// Show message to user about auto-reset
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
					"Time to Max: New " + config.trackingInterval().toString().toLowerCase() + 
					" detected - XP tracking reset automatically.", null);
				
				// Update the panel
				if (panel != null) {
					SwingUtilities.invokeLater(() -> panel.updateAllInfo());
				}
			}
		}
	}

	@Provides
	TimeToMaxConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TimeToMaxConfig.class);
	}
}
