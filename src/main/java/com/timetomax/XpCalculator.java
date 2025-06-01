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
	public static final int LEVEL_99_XP = 13_034_431;
	public static final int MAX_XP = 200_000_000;

	// Store starting XP for each skill for target tracking
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
	 * @param targetDate The target date (to detect changes)
	 * @param interval   The tracking interval
	 */
	public static void recordIntervalStartDate(Skill skill, LocalDate targetDate, TrackingInterval interval)
	{
		// Reset tracking if target date changes
		if (lastTargetDate == null || !lastTargetDate.equals(targetDate))
		{
			intervalStartDates.clear();
			lastTargetDate = targetDate;
		}

		if (!intervalStartDates.containsKey(skill))
		{
			intervalStartDates.put(skill, LocalDate.now());
		}
		else if (shouldStartNewPeriod(skill, interval))
		{
			intervalStartDates.put(skill, LocalDate.now());
		}
	}

	/**
	 * Get the required XP per day to reach max level by the target date
	 *
	 * @param startXp    Start XP in the skill
	 * @param config     Instance of the TimeToMaxConfig
	 * @return XP required per day
	 */
	public static int getRequiredXpPerDay(int startXp, TimeToMaxConfig config)
	{
		long daysUntilTarget = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(config.targetDate()));
		if (daysUntilTarget <= 0)
		{
			if (config.maxSkillMode() == MaxSkillMode.NORMAL)
			{
				return LEVEL_99_XP - startXp; // Target date is today or in the past
			}
			else if (config.maxSkillMode() == MaxSkillMode.COMPLETIONIST)
			{
				return MAX_XP - startXp;
			}
		}

		int xpRemaining = 0;

		if (config.maxSkillMode() == MaxSkillMode.NORMAL)
		{
			xpRemaining = LEVEL_99_XP - startXp;
			if (xpRemaining <= 0)
			{
				return 0;
			}
		}
		else if (config.maxSkillMode() == MaxSkillMode.COMPLETIONIST)
		{
			xpRemaining = MAX_XP - startXp;
			if (xpRemaining <= 0)
			{
				return 0;
			}

		}

		return (int) Math.ceil((double) xpRemaining / daysUntilTarget);
	}

	/**
	 * Get the required XP per interval to reach max level by the target date
	 *
	 * @param startXp    Start XP in the skill
	 * @param config instance of TimeToMaxConfig
	 * @return XP required per interval
	 */
	public static int getRequiredXpPerInterval(int startXp, TimeToMaxConfig config)
	{
		int xpPerDay = getRequiredXpPerDay(startXp, config);

		switch (config.trackingInterval())
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