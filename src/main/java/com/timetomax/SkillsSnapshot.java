package com.timetomax;

import lombok.Data;
import net.runelite.api.Skill;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
public class SkillsSnapshot {
    private final Map<String, Integer> skillExperienceMap = new HashMap<>();
    private final LocalDateTime timestamp;

    public SkillsSnapshot() {
        this.timestamp = LocalDateTime.now();
    }

    public void setExperience(Skill skill, int experience) {
        skillExperienceMap.put(skill.name(), experience);
    }

    public int getExperience(Skill skill) {
        return skillExperienceMap.getOrDefault(skill.name(), 0);
    }
}