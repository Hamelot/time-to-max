package com.timetomax;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
public class SkillsTracker {
    private final List<SkillsSnapshot> snapshots = new CopyOnWriteArrayList<>();
    private final ConfigManager configManager;
    private final Client client;
    private final String configKey;
    private final String baselineKey;
    private final String lastResetKey;
    
    // Store baseline XP values from when tracking started in the current session
    private final Map<Skill, Integer> baselineXp = new EnumMap<>(Skill.class);
    private boolean baselineSet = false;
    private LocalDateTime lastResetTime;

    public SkillsTracker(ConfigManager configManager, Client client, String username) {
        this.configManager = configManager;
        this.client = client;
        this.configKey = "timetomax." + username + ".snapshots";
        this.baselineKey = "timetomax." + username + ".baseline";
        this.lastResetKey = "timetomax." + username + ".lastreset";
        loadSnapshots();
        loadBaseline();
        loadLastResetTime();
    }

    public void captureSnapshot(Client client) {
        SkillsSnapshot snapshot = new SkillsSnapshot();
        
        // If this is the first snapshot of the session, set the baseline
        boolean isFirstSnapshot = !baselineSet;
        
        // Capture XP for all skills
        for (Skill skill : Skill.values()) {
            try {
                int xp = client.getSkillExperience(skill);
                snapshot.setExperience(skill, xp);
                
                // If this is the first snapshot, record the baseline XP
                if (isFirstSnapshot) {
                    baselineXp.put(skill, xp);
                }
            } catch (Exception e) {
                log.warn("Failed to get XP for skill: {}", skill.getName(), e);
            }
        }
        
        if (isFirstSnapshot) {
            baselineSet = true;
            saveBaseline();
            log.debug("Set baseline XP values for all skills");
        }
        
        snapshots.add(snapshot);
        saveSnapshots();
        log.debug("Captured new XP snapshot at: {}", snapshot.getTimestamp());
    }
    
    public SkillsSnapshot getLatestSnapshot() {
        if (snapshots.isEmpty()) {
            return null;
        }
        return snapshots.get(snapshots.size() - 1);
    }

    /**
     * Get the raw XP gained for a skill since the baseline was set
     */
    public int getSessionXpGained(Skill skill) {
        SkillsSnapshot current = getLatestSnapshot();
        
        if (current == null || !baselineSet || !baselineXp.containsKey(skill)) {
            return 0;
        }
        
        return current.getExperience(skill) - baselineXp.get(skill);
    }
    
    /**
     * Get the baseline XP value for a skill
     * @param skill The skill to get baseline XP for
     * @return The baseline XP value, or 0 if not available
     */
    public int getBaselineXp(Skill skill) {
        if (!baselineSet || !baselineXp.containsKey(skill)) {
            return 0;
        }
        return baselineXp.get(skill);
    }
    
    /**
     * Checks if the baseline should be reset based on the tracking interval
     * @param interval The current tracking interval configuration
     * @return true if a reset is needed, false otherwise
     */
    public boolean shouldResetBaseline(TrackingInterval interval) {
        if (lastResetTime == null) {
            return true; // Never reset before, so we should reset now
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        switch (interval) {
            case DAY:
                // Reset if the last reset was on a different day
                return !lastResetTime.toLocalDate().equals(now.toLocalDate());
                
            case WEEK:
                // Calculate the start of the current week (Monday as first day of week)
                LocalDate nowDate = now.toLocalDate();
                LocalDate startOfWeek = nowDate.minusDays(nowDate.getDayOfWeek().getValue() - 1);
                LocalDate lastResetDate = lastResetTime.toLocalDate();
                // Reset if the last reset was in a different week
                return lastResetDate.isBefore(startOfWeek);
                
            case MONTH:
                // Reset if the last reset was in a different month
                return lastResetTime.getMonth() != now.getMonth() || 
                       lastResetTime.getYear() != now.getYear();
                
            default:
                return false;
        }
    }
    
    private void saveBaseline() {
        try {
            // Convert the map to a JSON string using ConfigManager
            configManager.setConfiguration("timetomax", baselineKey, baselineXp);
            
            // Also save the reset time
            this.lastResetTime = LocalDateTime.now();
            saveLastResetTime();
            
            log.debug("Saved baseline XP values and reset time");
        } catch (Exception e) {
            log.error("Failed to save baseline XP values", e);
        }
    }
    
    private void loadBaseline() {
        try {
            // Load the configuration directly using ConfigManager's type handling
            baselineXp.clear();
            Object data = configManager.getConfiguration("timetomax", baselineKey);
            
            if (data != null && data instanceof Map) {
                Map<?, ?> configMap = (Map<?, ?>) data;
                for (Map.Entry<?, ?> entry : configMap.entrySet()) {
                    if (entry.getKey() instanceof String && entry.getValue() instanceof Number) {
                        try {
                            Skill skill = Skill.valueOf((String) entry.getKey());
                            baselineXp.put(skill, ((Number) entry.getValue()).intValue());
                        } catch (IllegalArgumentException e) {
                            // Skip invalid skills
                            log.debug("Skipping invalid skill name in config: {}", entry.getKey());
                        }
                    }
                }
                
                if (!baselineXp.isEmpty()) {
                    baselineSet = true;
                    log.debug("Loaded baseline XP values from config");
                }
            }
        } catch (Exception e) {
            log.error("Failed to load baseline XP values from config", e);
            // Reset baseline if failed to load
            baselineXp.clear();
            baselineSet = false;
        }
    }
    
    private void saveLastResetTime() {
        if (lastResetTime != null) {
            configManager.setConfiguration("timetomax", lastResetKey, 
                    lastResetTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
    }
    
    private void loadLastResetTime() {
        try {
            String timeStr = configManager.getConfiguration("timetomax", lastResetKey);
            if (timeStr != null && !timeStr.isEmpty()) {
                lastResetTime = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                log.debug("Loaded last reset time: {}", lastResetTime);
            } else {
                lastResetTime = null;
            }
        } catch (Exception e) {
            log.error("Failed to load last reset time", e);
            lastResetTime = null;
        }
    }
    
    /**
     * Reset the baseline XP values to start fresh
     */
    public void resetBaseline(Client client) {
        baselineXp.clear();
        baselineSet = false;
        
        // Capture new baseline values
        for (Skill skill : Skill.values()) {
            try {
                int xp = client.getSkillExperience(skill);
                baselineXp.put(skill, xp);
            } catch (Exception e) {
                log.warn("Failed to reset baseline XP for skill: {}", skill.getName(), e);
            }
        }
        
        baselineSet = true;
        saveBaseline();
        log.debug("Reset baseline XP values for all skills");
    }
    
    private void saveSnapshots() {
        // Keep only the necessary snapshots (latest + one for each interval)
        pruneSnapshots();
        
        try {
            // Store snapshots directly without needing Gson
            configManager.setConfiguration("timetomax", configKey, snapshots);
            log.debug("Saved {} snapshots to config", snapshots.size());
        } catch (Exception e) {
            log.error("Failed to save snapshots to config", e);
        }
    }
    
    private void loadSnapshots() {
        try {
            // Load snapshots directly without needing Gson
            Object data = configManager.getConfiguration("timetomax", configKey);
            if (data instanceof List) {
                snapshots.addAll((List<SkillsSnapshot>) data);
                log.debug("Loaded {} snapshots from config", snapshots.size());
            }
        } catch (Exception e) {
            log.error("Failed to load snapshots from config", e);
        }
    }
    
    private void pruneSnapshots() {
        // Keep latest snapshot
        if (snapshots.isEmpty()) {
            return;
        }
        
        LocalDateTime oldestNeeded = LocalDateTime.now().minusDays(31);
        List<SkillsSnapshot> toRemove = new ArrayList<>();
        
        for (int i = 0; i < snapshots.size() - 1; i++) {
            SkillsSnapshot snapshot = snapshots.get(i);
            if (snapshot.getTimestamp().isBefore(oldestNeeded)) {
                toRemove.add(snapshot);
            }
        }
        
        snapshots.removeAll(toRemove);
    }
    
    /**
     * Updates the latest snapshot without affecting the baseline
     * This allows tracking current XP without changing the target calculations
     */
    public void updateLatestSnapshot(Client client) {
        if (snapshots.isEmpty()) {
            // If no snapshots, do a full capture instead
            captureSnapshot(client);
            return;
        }
        
        SkillsSnapshot snapshot = new SkillsSnapshot();
        
        // Capture XP for all skills
        for (Skill skill : Skill.values()) {
            try {
                int xp = client.getSkillExperience(skill);
                snapshot.setExperience(skill, xp);
            } catch (Exception e) {
                log.warn("Failed to get XP for skill: {}", skill.getName(), e);
            }
        }
        
        // Replace the latest snapshot instead of adding a new one
        if (!snapshots.isEmpty()) {
            snapshots.remove(snapshots.size() - 1);
        }
        
        snapshots.add(snapshot);
        saveSnapshots();
        log.debug("Updated latest XP snapshot at: {}", snapshot.getTimestamp());
    }
    
    /**
     * Helper class to adapt LocalDateTime to/from JSON
     */
    private static class LocalDateTimeAdapter implements com.google.gson.JsonSerializer<LocalDateTime>, 
            com.google.gson.JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public com.google.gson.JsonElement serialize(LocalDateTime src, java.lang.reflect.Type typeOfSrc, 
                com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(FORMATTER.format(src));
        }

        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT, 
                com.google.gson.JsonDeserializationContext context) {
            return LocalDateTime.parse(json.getAsString(), FORMATTER);
        }
    }
}