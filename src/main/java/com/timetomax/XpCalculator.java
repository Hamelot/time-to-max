package com.timetomax;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * Utility class for XP calculations and display
 */
public class XpCalculator
{
	// XP required for level 99 in each skill
	public static final int MAX_XP = 13_034_431;

	// Store starting XP for each skill for target tracking
	private static final Map<Skill, Integer> targetStartXp = new HashMap<>();
	private static final Map<Skill, LocalDate> periodStartDates = new HashMap<>();
	private static LocalDate lastTargetDate = null;
	// Cache for the required XP per day values to ensure consistency
	private static final Map<Skill, Integer> cachedXpPerDay = new HashMap<>();

	/**
	 * Check if a new period should start for a skill based on the interval
	 *
	 * @param skill    The skill to check
	 * @param interval The current tracking interval
	 * @return true if a new period should start
	 */
	public static boolean shouldStartNewPeriod(Skill skill, TrackingInterval interval)
	{
		LocalDate periodStart = periodStartDates.get(skill);
		if (periodStart == null)
		{
			return true;
		}

		LocalDate now = LocalDate.now();
		switch (interval)
		{
			case DAY:
				return !now.equals(periodStart);
			case WEEK:
				return ChronoUnit.WEEKS.between(periodStart, now) > 0;
			case MONTH:
				return ChronoUnit.MONTHS.between(periodStart, now) > 0;
			default:
				return true;
		}
	}
	/**
	 * Records the starting XP for target tracking and updates period tracking
	 *
	 * @param skill      The skill to record
	 * @param currentXp  The current XP for the skill
	 * @param targetDate The target date (to detect changes)
	 * @param interval   The tracking interval
	 */
	public static void recordTargetStartXp(Skill skill, int currentXp, LocalDate targetDate, TrackingInterval interval)
	{
		// Reset tracking if target date changes
		if (lastTargetDate == null || !lastTargetDate.equals(targetDate))
		{
			targetStartXp.clear();
			periodStartDates.clear();
			cachedXpPerDay.clear(); // Clear cached values when target date changes
			lastTargetDate = targetDate;
		}

		// Check if we need to start a new period or initialize tracking
		if (!targetStartXp.containsKey(skill))
		{
			targetStartXp.put(skill, Math.max(0, currentXp)); // Initialize tracking
			periodStartDates.put(skill, LocalDate.now());
			
			// Cache the XP per day value for consistency
			int xpPerDay = getRequiredXpPerDay(currentXp, targetDate);
			cachedXpPerDay.put(skill, xpPerDay);
		}
		else if (shouldStartNewPeriod(skill, interval))
		{
			// Only update period start date without modifying targetStartXp or cached values
			periodStartDates.put(skill, LocalDate.now());
		}
	}

	/**
	 * Get the XP gained since target tracking started for this period
	 *
	 * @param skill     The skill to check
	 * @param currentXp The current XP in the skill
	 * @return XP gained since period start, or 0 if not started
	 */
	public static int getTargetXpGained(Skill skill, int currentXp)
	{
		Integer startXp = targetStartXp.get(skill);
		if (startXp == null)
		{
			return 0;
		}
		return Math.max(0, currentXp - startXp);
	}
	/**
	 * Get the required XP per day to reach max level by the target date
	 *
	 * @param startXp  Start XP in the skill
	 * @param targetDate Target date to reach max level
	 * @return XP required per day
	 */
	public static int getRequiredXpPerDay(int startXp, LocalDate targetDate)
	{
		long daysUntilTarget = ChronoUnit.DAYS.between(LocalDate.now(), targetDate);
		if (daysUntilTarget <= 0)
		{
			return MAX_XP - startXp; // Target date is today or in the past
		}

		int xpRemaining = MAX_XP - startXp;
		if (xpRemaining <= 0)
		{
			return 0;
		}

		return (int) Math.ceil((double) xpRemaining / daysUntilTarget);
	}
	
	/**
	 * Get the required XP per day, with caching to ensure consistency
	 * 
	 * @param skill The skill to get XP per day for
	 * @param startXp Start XP in the skill
	 * @param targetDate Target date to reach max level
	 * @return XP required per day
	 */
	public static int getRequiredXpPerDayCached(Skill skill, int startXp, LocalDate targetDate)
	{
		// Return cached value if it exists
		if (cachedXpPerDay.containsKey(skill))
		{
			return cachedXpPerDay.get(skill);
		}
		
		// Calculate and cache the value
		int xpPerDay = getRequiredXpPerDay(startXp, targetDate);
		cachedXpPerDay.put(skill, xpPerDay);
		return xpPerDay;
	}
	/**
	 * Get the required XP per interval to reach max level by the target date
	 *
	 * @param startXp  Start XP in the skill
	 * @param targetDate Target date to reach max level
	 * @param interval   The interval (day, week, month)
	 * @return XP required per interval
	 */
	public static int getRequiredXpPerInterval(int startXp, LocalDate targetDate, TrackingInterval interval)
	{
		int xpPerDay = getRequiredXpPerDay(startXp, targetDate);

		switch (interval)
		{
			case WEEK:
				return xpPerDay * 7;
			case MONTH:
				return xpPerDay * 30;
			default:
				return xpPerDay;
		}
	}
	
	/**
	 * Get the required XP per interval using cached daily XP values
	 *
	 * @param skill The skill to get XP for
	 * @param startXp Start XP in the skill
	 * @param targetDate Target date to reach max level
	 * @param interval The interval (day, week, month)
	 * @return XP required per interval
	 */
	public static int getRequiredXpPerIntervalCached(Skill skill, int startXp, LocalDate targetDate, TrackingInterval interval)
	{
		int xpPerDay = getRequiredXpPerDayCached(skill, startXp, targetDate);

		switch (interval)
		{
			case WEEK:
				return xpPerDay * 7;
			case MONTH:
				return xpPerDay * 30;
			default:
				return xpPerDay;
		}
	}

	/**
	 * @param skill The skill to check
	 * @return XP tracked from the start for a skill, or zero if no start xp found
	 */
	public static int getTargetStartXp(Skill skill)
	{
		Integer startXp = targetStartXp.get(skill);
		if (startXp == null)
		{
			return 0;
		}
		return startXp;
	}
	/**
	 * Clear target tracking data for a skill or all skills
	 *
	 * @param skill The skill to clear, or null to clear all skills
	 */
	public static void clearTargetTracking(Skill skill)
	{
		if (skill == null)
		{
			targetStartXp.clear();
			periodStartDates.clear();
			cachedXpPerDay.clear(); // Clear cached values
			lastTargetDate = null;
		}
		else
		{
			targetStartXp.remove(skill);
			periodStartDates.remove(skill);
			cachedXpPerDay.remove(skill); // Clear cached value for this skill
		}
	}

	/**
	 * Check if a skill is currently being tracked
	 *
	 * @param skill The skill to check
	 * @return true if the skill is being tracked, false otherwise
	 */
	public static boolean isSkillTracked(Skill skill)
	{
		return targetStartXp.containsKey(skill);
	}
}