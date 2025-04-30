package com.timetomax;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.HashMap;

@Slf4j
public class SkillsTracker implements Serializable
{
	// Using transient to prevent CopyOnWriteArrayList from being serialized directly
	private transient List<SkillsSnapshot> snapshots = new CopyOnWriteArrayList<>();
	private transient final ConfigManager configManager;
	private transient final Client client;
	private final String configKey;
	private final String baselineKey;
	private final String lastResetKey;
	private final String username;
	private final Gson gson; // Use injected Gson

	// Store baseline XP values from when tracking started in the current session
	private final Map<Skill, Integer> baselineXp = new EnumMap<>(Skill.class);
	private boolean baselineSet = false;
	private String lastResetTimeStr; // Store as string for better serialization

	// Track the current period to prevent false interval change detection
	private String currentPeriodKey;

	// Unique keys for each player's configuration
	private static final String CONFIG_GROUP = "timetomax";

	// Special key for storing direct baseline values - better compatibility with test environment
	private static final String DIRECT_BASELINE_KEY = "timetomax.directbaseline";

	// Key for baseline persistence flag
	private static final String BASELINE_PERSIST_KEY = "timetomax.baseline.persist";

	// File storage for more reliable persistence in test environment
	private static final File BASELINE_STORAGE_DIR = new File(RuneLite.RUNELITE_DIR, "time-to-max");

	// For testing day change logic
	private static LocalDate overrideCurrentDate = null;

	public SkillsTracker(ConfigManager configManager, Client client, String username, Gson gson)
	{
		this.configManager = configManager;
		this.client = client;
		this.username = username;
		this.configKey = CONFIG_GROUP + "." + username + ".snapshots";
		this.baselineKey = CONFIG_GROUP + "." + username + ".baseline";
		this.lastResetKey = CONFIG_GROUP + "." + username + ".lastreset";
		this.gson = gson; // Store the injected Gson

		snapshots = new CopyOnWriteArrayList<>();

		// First try to load from file (most reliable)
		if (!loadBaselineFromFile())
		{
			// If file loading failed, try ConfigManager
			loadBaseline();
		}

		loadSnapshots();
		loadLastResetTime();

		// Initialize the current period tracking
		updateCurrentPeriodKey();

		// For testing environment: Store both in the ConfigManager and in a file
		if (baselineSet && !baselineXp.isEmpty())
		{
			storeDirectBaseline();
			saveBaselineToFile();
		}
	}

	// Gets the file where baseline data is stored for this user
	private File getBaselineFile()
	{
		if (!BASELINE_STORAGE_DIR.exists())
		{
			BASELINE_STORAGE_DIR.mkdirs();
		}
		return new File(BASELINE_STORAGE_DIR, username + "-baseline.json");
	}

	// Save baseline data to a file for maximum persistence
	private void saveBaselineToFile()
	{
		try
		{
			File file = getBaselineFile();
			Map<String, Integer> exportData = new HashMap<>();

			// Convert Skill enum keys to strings for JSON storage
			for (Map.Entry<Skill, Integer> entry : baselineXp.entrySet())
			{
				exportData.put(entry.getKey().name(), entry.getValue());
			}

			// Add timestamp for verification
			exportData.put("_timestamp", (int) (System.currentTimeMillis() / 1000));

			try (FileWriter writer = new FileWriter(file))
			{
				gson.toJson(exportData, writer);
			}

			log.debug("Saved baseline data to file: {}", file.getAbsolutePath());
			return;
		}
		catch (Exception e)
		{
			log.error("Failed to save baseline data to file", e);
		}
	}

	// Load baseline data from file
	private boolean loadBaselineFromFile()
	{
		try
		{
			File file = getBaselineFile();

			if (!file.exists())
			{
				log.debug("No baseline file exists yet at: {}", file.getAbsolutePath());
				return false;
			}

			try (FileReader reader = new FileReader(file))
			{
				Type type = new TypeToken<Map<String, Integer>>()
				{
				}.getType();
				Map<String, Integer> importData = gson.fromJson(reader, type);

				if (importData != null && !importData.isEmpty())
				{
					baselineXp.clear();

					for (Map.Entry<String, Integer> entry : importData.entrySet())
					{
						// Skip the timestamp entry
						if (entry.getKey().equals("_timestamp"))
						{
							continue;
						}

						try
						{
							Skill skill = Skill.valueOf(entry.getKey());
							baselineXp.put(skill, entry.getValue());
						}
						catch (Exception e)
						{
							log.debug("Skipping invalid skill in file data: {}", entry.getKey());
						}
					}

					if (!baselineXp.isEmpty())
					{
						baselineSet = true;
						log.info("Successfully loaded baseline from file storage: {} skills", baselineXp.size());
						return true;
					}
				}
			}
		}
		catch (Exception e)
		{
			log.error("Error loading baseline from file", e);
		}

		return false;
	}

	// Gets the file where current snapshot is stored
	private File getSnapshotFile()
	{
		if (!BASELINE_STORAGE_DIR.exists())
		{
			BASELINE_STORAGE_DIR.mkdirs();
		}
		return new File(BASELINE_STORAGE_DIR, username + "-snapshot.json");
	}

	// Save current snapshot to file
	private void saveSnapshotToFile(SkillsSnapshot snapshot)
	{
		try
		{
			File file = getSnapshotFile();

			try (FileWriter writer = new FileWriter(file))
			{
				gson.toJson(snapshot, writer);
			}

			log.debug("Saved snapshot to file: {}", file.getAbsolutePath());
		}
		catch (Exception e)
		{
			log.error("Failed to save snapshot to file", e);
		}
	}

	// Load snapshot from file
	private SkillsSnapshot loadSnapshotFromFile()
	{
		try
		{
			File file = getSnapshotFile();

			if (!file.exists())
			{
				log.debug("No snapshot file exists yet at: {}", file.getAbsolutePath());
				return null;
			}

			try (FileReader reader = new FileReader(file))
			{
				return gson.fromJson(reader, SkillsSnapshot.class);
			}
		}
		catch (Exception e)
		{
			log.error("Error loading snapshot from file", e);
		}

		return null;
	}

	/**
	 * Returns the username associated with this tracker
	 */
	public String getUsername()
	{
		return username;
	}

	/**
	 * Updates the current period key based on the current date
	 */
	private void updateCurrentPeriodKey()
	{
		LocalDate now = getCurrentDate();
		this.currentPeriodKey = now.toString(); // Default for DAY tracking
	}

	/**
	 * Updates the current period key based on the specified interval
	 */
	public void updateCurrentPeriodKey(TrackingInterval interval)
	{
		LocalDate now = getCurrentDate();

		switch (interval)
		{
			case DAY:
				this.currentPeriodKey = now.toString();
				break;
			case WEEK:
				// Calculate the start of the current week (Monday as first day of week)
				LocalDate startOfWeek = now.minusDays(now.getDayOfWeek().getValue() - 1);
				this.currentPeriodKey = "WEEK-" + startOfWeek.toString();
				break;
			case MONTH:
				this.currentPeriodKey = "MONTH-" + now.getYear() + "-" + now.getMonthValue();
				break;
			default:
				this.currentPeriodKey = now.toString();
		}

		log.debug("Current period key set to: {}", this.currentPeriodKey);
	}

	public void captureSnapshot(Client client)
	{
		SkillsSnapshot snapshot = new SkillsSnapshot();

		// If this is the first snapshot of the session, set the baseline
		boolean isFirstSnapshot = !baselineSet;

		// Capture XP for all skills
		for (Skill skill : Skill.values())
		{
			try
			{
				int xp = client.getSkillExperience(skill);
				snapshot.setExperience(skill, xp);

				// If this is the first snapshot, record the baseline XP
				if (isFirstSnapshot)
				{
					baselineXp.put(skill, xp);
				}
			}
			catch (Exception e)
			{
				log.warn("Failed to get XP for skill: {}", skill.getName(), e);
			}
		}

		if (isFirstSnapshot)
		{
			baselineSet = true;
			saveBaseline();
			saveBaselineToFile(); // Also save to file for more reliability
			log.debug("Set baseline XP values for all skills");
		}

		snapshots.add(snapshot);
		saveSnapshots();
		saveSnapshotToFile(snapshot); // Also save to file
		log.debug("Captured new XP snapshot at: {}", snapshot.getTimestamp());
	}

	public SkillsSnapshot getLatestSnapshot()
	{
		if (snapshots.isEmpty())
		{
			// Try to load from file if no snapshots in memory
			SkillsSnapshot fileSnapshot = loadSnapshotFromFile();
			if (fileSnapshot != null)
			{
				snapshots.add(fileSnapshot);
				return fileSnapshot;
			}
			return null;
		}
		return snapshots.get(snapshots.size() - 1);
	}

	/**
	 * Get the raw XP gained for a skill since the baseline was set
	 */
	public int getSessionXpGained(Skill skill)
	{
		if (!baselineSet || !baselineXp.containsKey(skill))
		{
			return 0;
		}

		int baseXp = baselineXp.get(skill);

		// Try to get from latest snapshot first
		SkillsSnapshot current = getLatestSnapshot();
		if (current != null)
		{
			return current.getExperience(skill) - baseXp;
		}

		// If no snapshot available, create one
		if (client != null)
		{
			try
			{
				int currentXp = client.getSkillExperience(skill);
				return currentXp - baseXp;
			}
			catch (Exception e)
			{
				log.warn("Error getting current XP for {}", skill.getName(), e);
			}
		}

		return 0;
	}

	/**
	 * Get the baseline XP value for a skill
	 *
	 * @param skill The skill to get baseline XP for
	 * @return The baseline XP value, or 0 if not available
	 */
	public int getBaselineXp(Skill skill)
	{
		if (!baselineSet || !baselineXp.containsKey(skill))
		{
			return 0;
		}
		return baselineXp.get(skill);
	}

	/**
	 * Checks if the baseline should be reset based on the tracking interval
	 *
	 * @param interval The current tracking interval configuration
	 * @return true if a reset is needed, false otherwise
	 */
	public boolean shouldResetBaseline(TrackingInterval interval)
	{
		if (lastResetTimeStr == null)
		{
			return true; // Never reset before, so we should reset now
		}

		// Parse the last reset time
		LocalDateTime lastResetTime;
		try {
			lastResetTime = LocalDateTime.parse(lastResetTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		} catch (Exception e) {
			log.error("Failed to parse last reset time", e);
			return true; // If we can't parse the time, reset now
		}

		// Get current date (respecting any override for testing)
		LocalDate currentDate = getCurrentDate();
		
		// Get the date part of the last reset
		LocalDate lastResetDate = lastResetTime.toLocalDate();
		
		// Determine if we should reset based on interval type
		switch (interval)
		{
			case DAY:
				// Reset if the current date is different from the last reset date
				if (!currentDate.equals(lastResetDate)) {
					log.debug("Day changed from {} to {}, resetting baseline", lastResetDate, currentDate);
					currentPeriodKey = currentDate.toString();
					return true;
				}
				break;
				
			case WEEK:
				// Get start of current week and last reset week
				LocalDate currentWeekStart = currentDate.minusDays(currentDate.getDayOfWeek().getValue() - 1);
				LocalDate lastWeekStart = lastResetDate.minusDays(lastResetDate.getDayOfWeek().getValue() - 1);
				
				// Reset if the week has changed
				if (!currentWeekStart.equals(lastWeekStart)) {
					log.debug("Week changed from {} to {}, resetting baseline", lastWeekStart, currentWeekStart);
					currentPeriodKey = "WEEK-" + currentWeekStart.toString();
					return true;
				}
				break;
				
			case MONTH:
				// Reset if the month or year has changed
				if (currentDate.getYear() != lastResetDate.getYear() || 
					currentDate.getMonthValue() != lastResetDate.getMonthValue()) {
					log.debug("Month changed from {}-{} to {}-{}, resetting baseline", 
						lastResetDate.getYear(), lastResetDate.getMonthValue(),
						currentDate.getYear(), currentDate.getMonthValue());
					currentPeriodKey = "MONTH-" + currentDate.getYear() + "-" + currentDate.getMonthValue();
					return true;
				}
				break;
		}
		
		// No reset needed
		return false;
	}

	private void saveBaseline()
	{
		try
		{
			// Save the baseline XP map directly
			configManager.setConfiguration("timetomax", baselineKey, baselineXp);

			// Also save the reset time as string
			this.lastResetTimeStr = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
			saveLastResetTime();

			// Also set a global flag that baseline exists
			configManager.setConfiguration(CONFIG_GROUP, BASELINE_PERSIST_KEY, true);

			log.debug("Saved baseline XP values and reset time");
		}
		catch (Exception e)
		{
			log.error("Failed to save baseline XP values", e);
		}
	}

	private void loadBaseline()
	{
		try
		{
			// Load the configuration directly using ConfigManager's type handling
			baselineXp.clear();
			Object data = configManager.getConfiguration("timetomax", baselineKey);

			if (data != null && data instanceof Map)
			{
				Map<?, ?> configMap = (Map<?, ?>) data;
				for (Map.Entry<?, ?> entry : configMap.entrySet())
				{
					if (entry.getKey() instanceof String && entry.getValue() instanceof Number)
					{
						try
						{
							Skill skill = Skill.valueOf((String) entry.getKey());
							baselineXp.put(skill, ((Number) entry.getValue()).intValue());
						}
						catch (IllegalArgumentException e)
						{
							// Skip invalid skills
							log.debug("Skipping invalid skill name in config: {}", entry.getKey());
						}
					}
				}

				if (!baselineXp.isEmpty())
				{
					baselineSet = true;
					log.debug("Loaded baseline XP values from config: {} skills", baselineXp.size());
				}
			}
		}
		catch (Exception e)
		{
			log.error("Failed to load baseline XP values from config", e);
			// Reset baseline if failed to load
			baselineXp.clear();
			baselineSet = false;
		}
	}

	private void saveLastResetTime()
	{
		if (lastResetTimeStr != null)
		{
			configManager.setConfiguration("timetomax", lastResetKey, lastResetTimeStr);
		}
	}

	private void loadLastResetTime()
	{
		try
		{
			lastResetTimeStr = configManager.getConfiguration("timetomax", lastResetKey, String.class);
			if (lastResetTimeStr != null && !lastResetTimeStr.isEmpty())
			{
				log.debug("Loaded last reset time: {}", lastResetTimeStr);
			}
		}
		catch (Exception e)
		{
			log.error("Failed to load last reset time", e);
			lastResetTimeStr = null;
		}
	}

	/**
	 * Reset the baseline XP values to start fresh
	 */
	public void resetBaseline(Client client)
	{
		baselineXp.clear();
		baselineSet = false;

		// Capture new baseline values
		for (Skill skill : Skill.values())
		{
			try
			{
				int xp = client.getSkillExperience(skill);
				baselineXp.put(skill, xp);
			}
			catch (Exception e)
			{
				log.warn("Failed to reset baseline XP for skill: {}", skill.getName(), e);
			}
		}

		baselineSet = true;
		saveBaseline();
		saveBaselineToFile(); // Also save to file

		// Always create a new snapshot with current XP values after resetting
		updateLatestSnapshot(client);

		log.debug("Reset baseline XP values for all skills and created new snapshot");
	}

	private void saveSnapshots()
	{
		// Keep only the necessary snapshots (latest + one for each interval)
		pruneSnapshots();

		try
		{
			// Store snapshots directly
			configManager.setConfiguration("timetomax", configKey, snapshots);
			log.debug("Saved {} snapshots to config", snapshots.size());
		}
		catch (Exception e)
		{
			log.error("Failed to save snapshots to config", e);
		}
	}

	private void loadSnapshots()
	{
		try
		{
			// First try to load from file (most reliable)
			SkillsSnapshot fileSnapshot = loadSnapshotFromFile();
			if (fileSnapshot != null)
			{
				snapshots.clear();
				snapshots.add(fileSnapshot);
				log.debug("Loaded snapshot from file");
				return;
			}

			// Otherwise try ConfigManager
			Object data = configManager.getConfiguration("timetomax", configKey);
			if (data instanceof List)
			{
				List<?> loadedSnapshots = (List<?>) data;
				if (!loadedSnapshots.isEmpty() && loadedSnapshots.get(0) instanceof SkillsSnapshot)
				{
					snapshots.addAll((List<SkillsSnapshot>) loadedSnapshots);
					log.debug("Loaded {} snapshots from config", snapshots.size());
				}
			}
		}
		catch (Exception e)
		{
			log.error("Failed to load snapshots from config", e);
		}
	}

	private void pruneSnapshots()
	{
		// Keep latest snapshot
		if (snapshots.isEmpty())
		{
			return;
		}

		// Just keep the latest snapshot for simplicity
		SkillsSnapshot latest = snapshots.get(snapshots.size() - 1);
		snapshots.clear();
		snapshots.add(latest);
	}

	/**
	 * Updates the latest snapshot without affecting the baseline
	 * This allows tracking current XP without changing the target calculations
	 */
	public void updateLatestSnapshot(Client client)
	{
		SkillsSnapshot snapshot = new SkillsSnapshot();

		// Capture XP for all skills
		for (Skill skill : Skill.values())
		{
			try
			{
				int xp = client.getSkillExperience(skill);
				snapshot.setExperience(skill, xp);
			}
			catch (Exception e)
			{
				log.warn("Failed to get XP for skill: {}", skill.getName(), e);
			}
		}

		// Replace the latest snapshot instead of adding a new one
		if (!snapshots.isEmpty())
		{
			snapshots.remove(snapshots.size() - 1);
		}

		snapshots.add(snapshot);
		saveSnapshots();
		saveSnapshotToFile(snapshot); // Also save to file
		log.debug("Updated latest XP snapshot at: {}", snapshot.getTimestamp());
	}

	/**
	 * Creates a new snapshot from current XP values or ensures existing snapshot is valid
	 */
	public void loadOrCreateSnapshot()
	{
		if (baselineSet)
		{
			boolean needsSnapshot = snapshots.isEmpty();

			// If we have snapshots, check if they're valid
			if (!needsSnapshot && !snapshots.isEmpty())
			{
				SkillsSnapshot latest = getLatestSnapshot();
				if (latest == null)
				{
					needsSnapshot = true;
				}
				else
				{
					// Check if snapshot has valid data
					boolean hasValidData = false;
					for (Skill skill : Skill.values())
					{
						if (latest.getExperience(skill) > 0)
						{
							hasValidData = true;
							break;
						}
					}
					needsSnapshot = !hasValidData;
				}
			}

			if (needsSnapshot)
			{
				log.info("Creating fresh snapshot with current XP values after client restart");
				updateLatestSnapshot(client);
			}
			else
			{
				log.debug("Existing snapshots found and validated");
			}
		}
	}

	/**
	 * Force a refresh of the XP data after client restart
	 */
	public void forceSnapshotRefresh()
	{
		if (client != null && baselineSet)
		{
			log.info("Forcing snapshot refresh with current XP values");
			updateLatestSnapshot(client);
		}
	}

	/**
	 * Checks if there's valid baseline data loaded
	 *
	 * @return true if valid baseline data exists for at least one skill
	 */
	public boolean hasValidBaselineData()
	{
		if (!baselineSet || baselineXp.isEmpty())
		{
			return false;
		}

		// Check if we have at least one skill with a baseline value
		for (Skill skill : Skill.values())
		{
			if (baselineXp.getOrDefault(skill, 0) > 0)
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Stores a direct copy of the baseline in a global key for better test environment persistence
	 */
	private void storeDirectBaseline()
	{
		try
		{
			// Create a map of skill names to XP values
			Map<String, Integer> directBaselineMap = new HashMap<>();
			for (Map.Entry<Skill, Integer> entry : baselineXp.entrySet())
			{
				directBaselineMap.put(entry.getKey().name(), entry.getValue());
			}
			configManager.setConfiguration(CONFIG_GROUP, DIRECT_BASELINE_KEY, directBaselineMap);
			log.debug("Stored direct baseline copy for test environment persistence");
		}
		catch (Exception e)
		{
			log.error("Failed to store direct baseline", e);
		}
	}

	/**
	 * Try to load baseline from direct storage if regular method failed
	 */
	public void tryLoadDirectBaseline()
	{
		// Only try this if regular baseline loading failed
		if (!baselineSet || baselineXp.isEmpty())
		{
			try
			{
				// First try from file (most reliable)
				if (loadBaselineFromFile())
				{
					return;
				}

				// Then try from ConfigManager
				Object data = configManager.getConfiguration(CONFIG_GROUP, DIRECT_BASELINE_KEY);
				if (data != null && data instanceof Map)
				{
					Map<?, ?> directMap = (Map<?, ?>) data;
					boolean foundValidData = false;

					for (Map.Entry<?, ?> entry : directMap.entrySet())
					{
						if (entry.getKey() instanceof String && entry.getValue() instanceof Number)
						{
							try
							{
								Skill skill = Skill.valueOf((String) entry.getKey());
								baselineXp.put(skill, ((Number) entry.getValue()).intValue());
								foundValidData = true;
							}
							catch (IllegalArgumentException e)
							{
								log.debug("Skipping invalid skill in direct baseline: {}", entry.getKey());
							}
						}
					}

					if (foundValidData)
					{
						baselineSet = true;
						log.info("Successfully loaded baseline from direct storage");

						// Store it back in the player-specific key
						saveBaseline();
						saveBaselineToFile(); // Also save to file
					}
				}
			}
			catch (Exception e)
			{
				log.error("Failed to load direct baseline", e);
			}
		}
	}

	/**
	 * Override the current date for testing purposes
	 *
	 * @param date The date to use instead of the current date
	 */
	public static void setOverrideDate(LocalDate date)
	{
		overrideCurrentDate = date;
		log.info("Date override set to: {}", date);
	}

	/**
	 * Clear any date override and use the real current date
	 */
	public static void clearOverrideDate()
	{
		overrideCurrentDate = null;
		log.info("Date override cleared");
	}

	/**
	 * Get the current date, respecting any override that might be set
	 */
	public static LocalDate getCurrentDate()
	{
		return overrideCurrentDate != null ? overrideCurrentDate : LocalDate.now();
	}
}