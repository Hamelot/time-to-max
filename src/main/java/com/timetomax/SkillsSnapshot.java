package com.timetomax;

import lombok.Data;
import net.runelite.api.Skill;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Data
public class SkillsSnapshot implements Serializable
{
	private static final long serialVersionUID = 1L;
	private final Map<String, Integer> skillExperienceMap = new HashMap<>();
	private final String timestamp;  // Stored as String for proper serialization

	public SkillsSnapshot()
	{
		this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
	}

	public void setExperience(Skill skill, int experience)
	{
		skillExperienceMap.put(skill.name(), experience);
	}

	public int getExperience(Skill skill)
	{
		return skillExperienceMap.getOrDefault(skill.name(), 0);
	}

	public LocalDateTime getTimestamp()
	{
		try
		{
			return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		}
		catch (Exception e)
		{
			return LocalDateTime.now(); // Fallback if parsing fails
		}
	}
}