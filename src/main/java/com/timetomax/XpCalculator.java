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
			lastTargetDate = targetDate;
		}

		// Check if we need to start a new period
		if (shouldStartNewPeriod(skill, interval))
		{
			targetStartXp.put(skill, currentXp);
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
	 * @param currentXp  Current XP in the skill
	 * @param targetDate Target date to reach max level
	 * @return XP required per day
	 */
	public static int getRequiredXpPerDay(int currentXp, LocalDate targetDate)
	{
		long daysUntilTarget = ChronoUnit.DAYS.between(LocalDate.now(), targetDate);
		if (daysUntilTarget <= 0)
		{
			return MAX_XP - currentXp; // Target date is today or in the past
		}

		int xpRemaining = MAX_XP - currentXp;
		if (xpRemaining <= 0)
		{
			return 0;
		}

		return (int) Math.ceil((double) xpRemaining / daysUntilTarget);
	}

	/**
	 * Get the required XP per interval to reach max level by the target date
	 *
	 * @param currentXp  Current XP in the skill
	 * @param targetDate Target date to reach max level
	 * @param interval   The interval (day, week, month)
	 * @return XP required per interval
	 */
	public static int getRequiredXpPerInterval(int currentXp, LocalDate targetDate, TrackingInterval interval)
	{
		int xpPerDay = getRequiredXpPerDay(currentXp, targetDate);

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
			lastTargetDate = null;
		}
		else
		{
			targetStartXp.remove(skill);
			periodStartDates.remove(skill);
		}
	}
}