package com.timetomax;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
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
	private static final Map<Skill, LocalDate> intervalStartDates = new HashMap<>();
	private static LocalDate lastTargetDate = null;

	/**
	 * Check if a new period should start for a skill based on the interval
	 *
	 * @param skill    The skill to check
	 * @param interval The current tracking interval
	 * @return true if a new period should start
	 */
	public static boolean shouldStartNewPeriod(Skill skill, TrackingInterval interval)
	{
		LocalDate periodStart = intervalStartDates.get(skill);
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
			intervalStartDates.clear();
			lastTargetDate = targetDate;
		}

		// Check if we need to start a new period or initialize tracking
		if (!targetStartXp.containsKey(skill))
		{
			targetStartXp.put(skill, Math.max(0, currentXp)); // Initialize tracking
			intervalStartDates.put(skill, LocalDate.now());
		}
		else if (shouldStartNewPeriod(skill, interval))
		{
			// Only update period start date without modifying targetStartXp or cached values
			intervalStartDates.put(skill, LocalDate.now());
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
	 * @param startXp    Start XP in the skill
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
	 * Get the required XP per interval to reach max level by the target date
	 *
	 * @param startXp    Start XP in the skill
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
			intervalStartDates.clear();
			lastTargetDate = null;
		}
		else
		{
			targetStartXp.remove(skill);
			intervalStartDates.remove(skill);
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

	/**
	 * Check if a skill has met its target XP goal
	 *
	 * @param skill     The skill to check
	 * @param currentXp The current XP for the skill
	 * @return true if the skill has reached the max XP or its target, false otherwise
	 */
	public static boolean isSkillTargetMet(Skill skill, int currentXp, LocalDate targetDate, TrackingInterval interval)
	{
		// Check if the skill has reached MAX_XP for level 99
		if (currentXp >= MAX_XP)
		{
			return true;
		}

		// If the skill is not being tracked, it can't have met its target
		if (!isSkillTracked(skill))
		{
			return false;
		}

		// If we're tracking this skill, check if the player has gained the required XP for this period
		int targetStartXp = getTargetStartXp(skill);
		int xpGained = getTargetXpGained(skill, currentXp);

		// If the target date is in the past, we consider the target met
		if (lastTargetDate != null && LocalDate.now().isAfter(lastTargetDate))
		{
			return true;
		}

		// The target is met if we've reached the daily/weekly/monthly XP goal
		// Note: This is a simplification. Ideally, we'd check if the player has met
		// their goal for the current interval (day/week/month).
		return xpGained >= getRequiredXpPerInterval(targetStartXp, targetDate, interval);
	}

	/**
	 * Overloaded method for backward compatibility
	 *
	 * @param skill The skill to check
	 * @return true if the skill has reached the max XP, false otherwise
	 */
	public static boolean isSkillTargetMet(Skill skill)
	{
		// If the skill is not being tracked, it can't have met its target
		if (!isSkillTracked(skill))
		{
			return false;
		}

		// Without access to current XP, we can only check if the starting XP was already at max
		int startXp = getTargetStartXp(skill);
		return startXp >= MAX_XP;
	}

	/**
	 * Check if a new interval should start based only on the interval type and reference date.
	 * This is interval-specific but skill-agnostic.
	 *
	 * @param interval      The current tracking interval
	 * @param referenceDate The reference date to compare against
	 * @return true if a new interval should start
	 */
	public static boolean shouldStartNewIntervalForDate(TrackingInterval interval, LocalDate referenceDate)
	{
		if (referenceDate == null)
		{
			return true;
		}

		LocalDate now = LocalDate.now();
		switch (interval)
		{
			case DAY:
				return !now.equals(referenceDate);
			case WEEK:
				// Check if the current date is in a different ISO week than the reference date
				return now.get(ChronoField.ALIGNED_WEEK_OF_YEAR) != referenceDate.get(ChronoField.ALIGNED_WEEK_OF_YEAR)
						|| now.getYear() != referenceDate.getYear();
			case MONTH:
				// Check if the current date is in a different calendar month than the reference date
				return now.getMonth() != referenceDate.getMonth() || now.getYear() != referenceDate.getYear();
			default:
				return true;
		}
	}

	/**
	 * Get the interval start date for a skill
	 *
	 * @param skill The skill to get the interval start date for
	 * @return The interval start date for the skill or null if not set
	 */
	public static LocalDate getIntervalStartDate(Skill skill)
	{
		return intervalStartDates.get(skill);
	}
}