/*
 * Copyright (c) 2017, Cameron <moberg@tuta.io>
 * Copyright (c) 2018, Levi <me@levischuck.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.timetomax;

import com.google.common.annotations.VisibleForTesting;
import static com.google.common.base.MoreObjects.firstNonNull;
import com.google.inject.Binder;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import static com.timetomax.XpWorldType.NORMAL;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Time To Max",
	description = "Enable the XP Tracker panel",
	tags = {"experience", "levels", "panel"}
)
@Slf4j
public class TimeToMaxPlugin extends Plugin
{
	private static final String MENUOP_ADD_CANVAS_TRACKER = "Add to canvas";
	private static final String MENUOP_REMOVE_CANVAS_TRACKER = "Remove from canvas";

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private TimeToMaxConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private XpState xpState;

	@Inject
	private ConfigManager configManager;

	private NavigationButton navButton;
	@Setter(AccessLevel.PACKAGE)
	@VisibleForTesting
	private XpPanel xpPanel;
	private XpWorldType lastWorldType;
	private long lastAccount;
	private long lastTickMillis = 0;
	private int initializeTracker;

	private final XpPauseState xpPauseState = new XpPauseState();

	@Provides
	TimeToMaxConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TimeToMaxConfig.class);
	}

	@Override
	public void configure(Binder binder)
	{
		binder.bind(TimeToMaxService.class).to(TimeToMaxServiceImpl.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		// Create panel first
		xpPanel = new XpPanel(this, config, client, skillIconManager);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "timetomax.png");

		navButton = NavigationButton.builder()
			.tooltip("Time to Max")
			.icon(icon)
			.priority(3)
			.panel(xpPanel)
			.build();

		clientToolbar.addNavigation(navButton);

		// Initialize values
		lastAccount = -1L;
		lastTickMillis = 0;

		// Use clientThread for operations that interact with the game client
		clientThread.invokeLater(() -> {
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				lastAccount = client.getAccountHash();
				lastWorldType = worldSetToType(client.getWorldType());
			}
		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.removeIf(e -> e instanceof XpInfoBoxOverlay);
		xpState.reset();
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			// LOGGED_IN is triggered between region changes too.
			// Check that the username changed or the world type changed.
			XpWorldType type = worldSetToType(client.getWorldType());

			if (client.getAccountHash() != lastAccount || lastWorldType != type)
			{
				// Reset
				log.debug("World change: {} -> {}, {} -> {}",
					lastAccount, client.getAccountHash(),
					firstNonNull(lastWorldType, "<unknown>"),
					firstNonNull(type, "<unknown>"));

				lastAccount = client.getAccountHash();
				lastWorldType = type;
				resetState();

				// Must be set from hitting the LOGGING_IN or HOPPING case below
				assert initializeTracker > 0;
			}
		}
		else if (state == GameState.LOGGING_IN || state == GameState.HOPPING)
		{
			initializeTracker = 2;
		}
		else if (state == GameState.LOGIN_SCREEN)
		{
			Player local = client.getLocalPlayer();
			if (local == null)
			{
				return;
			}

			String username = local.getName();
			if (username == null)
			{
				return;
			}
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		XpSave save = xpState.save();
		if (save != null)
		{
			saveSaveState(event.getPreviousProfile(), save);
		}
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		XpSave save = xpState.save();
		if (save != null)
		{
			saveSaveState(configManager.getRSProfileKey(), save);
		}
	}

	private XpWorldType worldSetToType(EnumSet<WorldType> types)
	{
		XpWorldType xpType = NORMAL;
		for (WorldType type : types)
		{
			XpWorldType t = XpWorldType.of(type);
			if (t != NORMAL)
			{
				xpType = t;
			}
		}
		return xpType;
	}

	/**
	 * Adds an overlay to the canvas for tracking a specific skill.
	 *
	 * @param skill the skill for which the overlay should be added
	 */
	void addOverlay(Skill skill)
	{
		removeOverlay(skill);
		overlayManager.add(new XpInfoBoxOverlay(this, config, skill, skillIconManager.getSkillImage(skill)));
	}

	/**
	 * Removes an overlay from the overlayManager if it's present.
	 *
	 * @param skill the skill for which the overlay should be removed.
	 */
	void removeOverlay(Skill skill)
	{
		overlayManager.removeIf(e -> e instanceof XpInfoBoxOverlay && ((XpInfoBoxOverlay) e).getSkill() == skill);
	}

	/**
	 * Check if there is an overlay on the canvas for the skill.
	 *
	 * @param skill the skill which should have an overlay.
	 * @return true if the skill has an overlay.
	 */
	boolean hasOverlay(final Skill skill)
	{
		return overlayManager.anyMatch(o -> o instanceof XpInfoBoxOverlay && ((XpInfoBoxOverlay) o).getSkill() == skill);
	}

	/**
	 * Reset internal state and re-initialize all skills with XP currently cached by the RS client
	 * This is called by the user manually clicking resetSkillState in the UI.
	 * It reloads the current skills from the client after resetting internal state.
	 */
	void resetAndInitState()
	{
		clearSaveState(configManager.getRSProfileKey());
		resetState();

		for (Skill skill : Skill.values())
		{
			long currentXp = client.getSkillExperience(skill);
			xpState.initializeSkill(skill, currentXp);
			removeOverlay(skill);
		}

		xpState.initializeOverall(client.getOverallExperience());
	}

	/**
	 * Throw out everything, the user has chosen a different account or world type.
	 * This resets both the internal state and UI elements
	 */
	private void resetState()
	{
		xpState.reset();
		xpPanel.resetAllInfoBoxes();
		xpPanel.updateTotal(new XpSnapshotSingle.XpSnapshotSingleBuilder().build());
		overlayManager.removeIf(e -> e instanceof XpInfoBoxOverlay);
	}

	/**
	 * Reset an individual skill with the client's current known state of the skill
	 * Will also clear the skill from the UI and reset its baseline XP.
	 *
	 * @param skill Skill to reset
	 */
	void resetSkillState(Skill skill)
	{
		int currentXp = client.getSkillExperience(skill);
		xpState.initializeSkill(skill, currentXp);
		xpPanel.resetSkill(skill);
		removeOverlay(skill);
	}

	/**
	 * Reset all skills except for the one provided
	 *
	 * @param skill Skill to ignore during reset
	 */
	void resetOtherSkillState(Skill skill)
	{
		for (Skill s : Skill.values())
		{
			if (skill != s)
			{
				resetSkillState(s);
			}
		}
	}

	/**
	 * Reset the xp gained since last reset of the skill
	 * Does not clear the skill from the UI.
	 *
	 * @param skill Skill to reset per hour rate
	 */
	void resetSkillPerHourState(Skill skill)
	{
		xpState.resetSkillPerHour(skill);
	}

	/**
	 * Reset the xp gained since last reset of all skills including OVERALL
	 * Does not clear the UI.
	 */
	void resetAllSkillsPerHourState()
	{
		for (Skill skill : Skill.values())
		{
			xpState.resetSkillPerHour(skill);
		}
		xpState.resetOverallPerHour();
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged)
	{
		final Skill skill = statChanged.getSkill();
		final int currentXp = statChanged.getXp();
		final int currentLevel = Experience.getLevelForXp(currentXp);

		// Skip processing for skills that are already maxed
		if (config.maxSkillMode() == MaxSkillMode.NORMAL)
		{
			if (currentLevel >= Experience.MAX_REAL_LEVEL)
			{
				xpPanel.resetSkill(skill);
				removeOverlay(skill);
				return;
			}
		}
		else if (config.maxSkillMode() == MaxSkillMode.COMPLETIONIST)
		{
			if (currentXp == Experience.MAX_SKILL_XP)
			{
				xpPanel.resetSkill(skill);
				removeOverlay(skill);
				return;
			}
		}

		// If this is the initial skill sync on login, initialize but don't process
		if (initializeTracker > 0)
		{
			return;
		}

		// Get the lowest starting xp in xpState before any changes
		int lowestStartXp = xpState.findLowestSkillXp();
		
		// Set the initial lowest skill flags for comparison
		xpState.setLowestSkillFlag(lowestStartXp);

		// Calculate goal XP values using the period tracking system
		final int goalStartXp = (int) getSkillState(skill).getStartXp();
		final int intervalXp = XpCalculator.getRequiredXpPerInterval(
			goalStartXp,
			config);
		final int goalEndXp = goalStartXp + intervalXp;

		// Update the skill state and UI
		final XpUpdateResult updateResult = xpState.updateSkill(
			skill,
			currentXp,
			goalStartXp,
			goalEndXp);

		// Update the startDate for the skill if it isn't already set
		if (xpState.getSkill(skill).getStartYear() == 9999)
		{
			xpState.getSkill(skill).updateStartDate(LocalDate.now().getDayOfMonth(), LocalDate.now().getMonthValue(),LocalDate.now().getYear());
		}
		
		// Recalculate lowest skill flag after skill state update
		int lowestStartXpAfterUpdate = xpState.findLowestSkillXp();
		xpState.setLowestSkillFlag(lowestStartXpAfterUpdate);

		// Update the skill that changed
		xpPanel.updateSkillExperience(updateResult == XpUpdateResult.UPDATED, xpPauseState.isPaused(skill),
			skill, getSkillSnapshot(skill));
		
		// If the lowest skill changed or we have a significant state change, update all skills to refresh the highlighting
		if (lowestStartXp != lowestStartXpAfterUpdate || updateResult == XpUpdateResult.INITIALIZED)
		{
			log.debug("Rebuilding all skills due to lowest skill change or initialization");
			rebuildSkills();
		}

		// Also update the total experience
		xpState.updateOverall(client.getOverallExperience());
		xpPanel.updateTotal(xpState.getTotalSnapshot());

		// Update the target panel to reflect current XP rates
		xpPanel.updateTargetPanel(config);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (initializeTracker > 0 && --initializeTracker == 0)
		{
			XpSave save;
			// Restore from saved state
			if (!xpState.isOverallInitialized() && (save = loadSaveState(configManager.getRSProfileKey())) != null)
			{
				log.debug("Loading xp state from save");
				xpState.restore(save);
				LocalDate now = LocalDate.now();

				for (Skill skill : save.skills.keySet())
				{
					XpStateSingle skillState = getSkillState(skill);
					int startXp = (int) skillState.getStartXp();
					int intervalXp = XpCalculator.getRequiredXpPerInterval(startXp, config);
					int goalXp = startXp + intervalXp;
					skillState.updateGoals(startXp, goalXp);
					if (xpState.getSkill(skill).getStartYear() == 9999)
					{
						xpState.getSkill(skill).updateStartDate(now.getDayOfMonth(), now.getMonthValue(),now.getYear());
					}
				}

				// apply state to the panel
				for (Skill skill : save.skills.keySet())
				{
					xpPanel.updateSkillExperience(true, false, skill, getSkillSnapshot(skill));
				}
			}

			// Check for xp gained while logged out
			for (Skill skill : Skill.values())
			{
				if (!xpState.isInitialized(skill))
				{
					continue;
				}

				XpStateSingle skillState = getSkillState(skill);
				final int currentXp = client.getSkillExperience(skill);
				if (skillState.getCurrentXp() != currentXp)
				{
					if (currentXp < skillState.getCurrentXp())
					{
						log.debug("Xp is going backwards! {} {} -> {}", skill, skillState.getCurrentXp(), currentXp);
						resetState();
						clearSaveState(configManager.getRSProfileKey());
						break;
					}

					log.debug("Skill xp for {} changed when offline: {} -> {}", skill, skillState.getCurrentXp(), currentXp);
					// Offset start xp for offline gains
					long diff = currentXp - skillState.getCurrentXp();
					skillState.setStartXp(skillState.getStartXp() + diff);
				}
			}

			// Initialize the tracker with the initial xp if not already initialized
			for (Skill skill : Skill.values())
			{
				if (!xpState.isInitialized(skill))
				{
					final int currentXp = client.getSkillExperience(skill);
					// goal exps are not necessary for skill initialization
					XpUpdateResult xpUpdateResult = xpState.updateSkill(skill, currentXp, -1, -1);
					assert xpUpdateResult == XpUpdateResult.INITIALIZED;
				}
			}

			// Initialize all non-maxed skills
			initializeNonMaxedSkills();

			// Initialize the overall xp
			if (!xpState.isOverallInitialized())
			{
				long overallXp = client.getOverallExperience();
				log.debug("Initializing XP tracker with {} overall exp", overallXp);
				xpState.initializeOverall(overallXp);
			}

			int lowestStartXp = xpState.findLowestSkillXp();
			xpState.setLowestSkillFlag(lowestStartXp);
		}

	}

	private void initializeNonMaxedSkills()
	{
		for (Skill skill : Skill.values())
		{
			final int currentXp = client.getSkillExperience(skill);
			final int currentLevel = Experience.getLevelForXp(currentXp);

			// Only show non-maxed skills
			if (config.maxSkillMode() == MaxSkillMode.NORMAL)
			{
				if ((currentLevel < Experience.MAX_REAL_LEVEL))
				{
					setCalculatedSkillExperience(skill, currentXp);
				}
			}
			else if (config.maxSkillMode() == MaxSkillMode.COMPLETIONIST)
			{
				if (currentXp < Experience.MAX_SKILL_XP)
				{
					setCalculatedSkillExperience(skill, currentXp);
				}
			}
		}
	}

	private void setCalculatedSkillExperience(Skill skill, int startXp)
	{
		// Calculate the interval goal based on current XP
		final int intervalXp = XpCalculator.getRequiredXpPerInterval(
			startXp,
			config);
		final int endGoalXp = startXp + intervalXp;

		XpStateSingle x = getSkillState(skill);
		x.updateGoals(startXp, endGoalXp);
		x.update(client.getSkillExperience(skill));

		if (xpState.getSkill(skill).getStartYear() == 9999)
		{
			xpState.getSkill(skill).updateStartDate(LocalDate.now().getDayOfMonth(), LocalDate.now().getMonthValue(),LocalDate.now().getYear());
		}

		xpPanel.updateSkillExperience(true, xpPauseState.isPaused(skill),
			skill, getSkillSnapshot(skill));
	}

	@Subscribe
	public void onMenuEntryAdded(final MenuEntryAdded event)
	{
		int widgetID = event.getActionParam1();

		if (WidgetUtil.componentToInterface(widgetID) != InterfaceID.STATS
			|| !event.getOption().startsWith("View")
			|| !config.skillTabOverlayMenuOptions())
		{
			return;
		}

		// Get skill from menu option, eg. "View <col=ff981f>Attack</col> guide"
		final String skillText = event.getOption().split(" ")[1];
		final Skill skill;

		try
		{
			skill = Skill.valueOf(Text.removeTags(skillText).toUpperCase());
		}
		catch (IllegalArgumentException ignored)
		{
			return;
		}
		client.getMenu().createMenuEntry(-1)
			.setTarget(skillText)
			.setOption(hasOverlay(skill) ? MENUOP_REMOVE_CANVAS_TRACKER : MENUOP_ADD_CANVAS_TRACKER)
			.setType(MenuAction.RUNELITE)
			.onClick(e ->
			{
				if (hasOverlay(skill))
				{
					removeOverlay(skill);
				}
				else
				{
					addOverlay(skill);
				}
			});
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted)
	{
		if (commandExecuted.getCommand().equals("ttmreset"))
		{
			log.debug("TTM Reset command triggered by command");
			handleTTMReset();
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "TTM has been reset by user.", null);
		}
	}

	XpStateSingle getSkillState(Skill skill)
	{
		return xpState.getSkill(skill);
	}

	XpSnapshotSingle getSkillSnapshot(Skill skill)
	{
		return xpState.getSkillSnapshot(skill);
	}

	/**
	 * Get the injected ConfigManager for other components to use
	 * @return the ConfigManager instance
	 */
	public ConfigManager getInjectedConfigManager()
	{
		return configManager;
	}

	@Schedule(
		period = 1,
		unit = ChronoUnit.SECONDS
	)
	public void tickSkillTimes()
	{
		int pauseSkillAfter = config.pauseSkillAfter();
		LocalDate earliestPeriodStart = null;
		// Adjust unpause states
		for (Skill skill : Skill.values())
		{
			long skillExperience = client.getSkillExperience(skill);
			xpPauseState.tickXp(skill, skillExperience, pauseSkillAfter);

			if (xpState.getSkill(skill).getStartYear() == 9999)
			{
				xpState.getSkill(skill).updateStartDate(LocalDate.now().getDayOfMonth(), LocalDate.now().getMonthValue(),LocalDate.now().getYear());
			}

			int startDay = xpState.getSkill(skill).getStartDay();
			int startMonth = xpState.getSkill(skill).getStartMonth();
			int startYear = xpState.getSkill(skill).getStartYear();
			LocalDate startDate = xpState.getSkill(skill).convertToLocalDate(startYear, startMonth, startDay);
			if (earliestPeriodStart == null || startDate.isBefore(earliestPeriodStart))
			{
				earliestPeriodStart = startDate;
			}
		}
		xpPauseState.tickOverall(client.getOverallExperience(), pauseSkillAfter);

		final boolean loggedIn = client.getGameState().getState() >= GameState.LOADING.getState();
		xpPauseState.tickLogout(config.pauseOnLogout(), loggedIn);

		if (lastTickMillis == 0)
		{
			lastTickMillis = System.currentTimeMillis();
			return;
		}

		final long nowMillis = System.currentTimeMillis();
		final long tickDelta = nowMillis - lastTickMillis;
		lastTickMillis = nowMillis;

		for (Skill skill : Skill.values())
		{
			if (!xpPauseState.isPaused(skill))
			{
				xpState.tick(skill, tickDelta);
			}
		}
		if (!xpPauseState.isOverallPaused())
		{
			xpState.tickOverall(tickDelta);
		}

		// If we have a period start date and we need to start a new period, trigger reset
		if (earliestPeriodStart != null && 
			XpCalculator.shouldStartNewIntervalForDate(config.trackingInterval(), earliestPeriodStart)
			&& client.getGameState().getState() >= GameState.LOADING.getState())
		{
			log.info("Interval change detected for {} interval - triggering reset", config.trackingInterval());
			handleTTMReset();
			String message = String.format("Time to Max: New %s has been detected. Resetting xp tracker", config.trackingInterval());
			clientThread.invoke(() ->{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
			});
		}

		rebuildSkills();

		xpPanel.updateTargetPanel(config);
	}

	@Schedule(
		period = 1,
		unit = ChronoUnit.MINUTES,
		asynchronous = true
	)
	public void tickStateSave()
	{
		if (xpState == null)
		{
			log.debug("Cannot save XP state: xpState is null");
			return;
		}

		XpSave save = xpState.save();
		if (save != null)
		{
			String profile = configManager.getRSProfileKey();
			saveSaveState(profile, save);
			log.debug("Saved XP state for profile: {}", profile);
		}
		// Find the earliest period start date from any skill
		LocalDate earliestPeriodStart = null;
		for (Skill skill : Skill.values())
		{
			LocalDate skillPeriodStart = XpCalculator.getIntervalStartDate(skill);
			if (skillPeriodStart != null && (earliestPeriodStart == null || skillPeriodStart.isBefore(earliestPeriodStart)))
			{
				earliestPeriodStart = skillPeriodStart;
			}
		}
	}

	private void rebuildSkills()
	{
		// Rebuild calculated values like xp/hr in panel
		for (Skill skill : Skill.values())
		{
			xpPanel.updateSkillExperience(false, xpPauseState.isPaused(skill), skill, getSkillSnapshot(skill));
		}

		xpPanel.updateTotal(xpState.getTotalSnapshot());
	}

	void pauseSkill(Skill skill, boolean pause)
	{
		if (pause ? xpPauseState.pauseSkill(skill) : xpPauseState.unpauseSkill(skill))
		{
			xpPanel.updateSkillExperience(false, xpPauseState.isPaused(skill), skill, getSkillSnapshot(skill));
		}
	}

	void pauseAllSkills(boolean pause)
	{
		for (Skill skill : Skill.values())
		{
			pauseSkill(skill, pause);
		}
		if (pause)
		{
			xpPauseState.pauseOverall();
		}
		else
		{
			xpPauseState.unpauseOverall();
		}
	}

	private void saveSaveState(String profile, XpSave state)
	{
		if (state != null && profile != null && !profile.isEmpty())
		{
			// Save to config
			try
			{
				configManager.setConfiguration("timeToMax", profile, "state", state);
				log.debug("Successfully saved XP state for profile: {}", profile);
			}
			catch (Exception e)
			{
				log.warn("Failed to save XP state", e);
			}
		}
	}

	private void clearSaveState(String profile)
	{
		if (profile != null && !profile.isEmpty())
		{
			configManager.unsetConfiguration("timeToMax", profile, "state");
		}
	}

	private XpSave loadSaveState(String profile)
	{
		return configManager.getConfiguration("timeToMax", profile, "state", XpSave.class);
	}

	private void handleTTMReset()
	{
		resetAndInitState();
		for (Skill s : Skill.values())
		{
			xpState.unInitializeSkill(s);
		}
		xpState.unInitializeOverall();
		XpSave save = new XpSave();
		saveSaveState(configManager.getRSProfileKey(), save);
		initializeTracker = 1;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"timeToMax".equals(event.getGroup()))
		{
			return;
		}

		// Check if the changed key is one we need to respond to
		if ("targetDate".equals(event.getKey()) || "trackingInterval".equals(event.getKey()) ||
			"maxSkillMode".equals(event.getKey()) || "xpOverride".equals(event.getKey()) ||
			"minimumXpOverride".equals(event.getKey()) || "highlightLowestSkill".equals(event.getKey()))
		{
			log.debug("Config changed: {} - Triggering recalculation", event.getKey());

			// Get the lowest starting xp in xpState
			int lowestStartXp = xpState.findLowestSkillXp();
			xpState.setLowestSkillFlag(lowestStartXp);

			if (config.xpOverride())
			{
				LocalDate targetDateWithXpOverride = XpCalculator.getMaxDateForLowestSkillWithOverride(
					lowestStartXp,
					config);
					if (targetDateWithXpOverride != null)
					{
						// Update the target date in the config if it is set to override
						configManager.setConfiguration("timeToMax", "targetDateWithXpOverride", targetDateWithXpOverride.toString());
					}
			}


			// Update the target panel with new config values
			xpPanel.updateTargetPanel(config);

			// Trigger reinitialization for all non-maxed skills
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				for (Skill skill : Skill.values())
				{
					final int currentXp = client.getSkillExperience(skill);
					final int currentLevel = Experience.getLevelForXp(currentXp);
					final int startXp = getSkillState(skill).getStartXp() == -1 ? currentXp : (int) getSkillState(skill).getStartXp();

					if (config.maxSkillMode().equals(MaxSkillMode.NORMAL))
					{
						// Remove skills over level 99
						if (currentLevel >= Experience.MAX_REAL_LEVEL)
						{
							xpPanel.resetSkill(skill);
							removeOverlay(skill);
						}
						else
						{
							// Recalculate goals for skills under level 99
							setCalculatedSkillExperience(skill, startXp);
						}
					}
					else if (config.maxSkillMode() == MaxSkillMode.COMPLETIONIST)
					{
						// Ensure skills under 200m XP are displayed and goals recalculated
						if (currentXp == Experience.MAX_SKILL_XP)
						{
							xpPanel.resetSkill(skill);
							removeOverlay(skill);
						}
						else
						{
							setCalculatedSkillExperience(skill, startXp);
						}
					}
				}
			}
		}
	}
}
