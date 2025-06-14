package com.timetomax;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
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
			if (config.maxSkillMode().equals(MaxSkillMode.NORMAL))
			{
				return LEVEL_99_XP - startXp; // Target date is today or in the past
			}
			else if (config.maxSkillMode() == MaxSkillMode.COMPLETIONIST)
			{
				return MAX_XP - startXp;
			}
		}

		int xpRemaining = 0;

		if (config.xpOverride())
		{
			return config.minimumXpOverride();
		}

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
				return now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) != referenceDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
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

	public static LocalDate getMaxDateForLowestSkillWithOverride(int lowestSkillXp, TimeToMaxConfig config)
	{
		if (config.xpOverride())
		{
			int xpRequired = MAX_XP;

			if (config.maxSkillMode().equals(MaxSkillMode.NORMAL))
			{
				xpRequired = LEVEL_99_XP - lowestSkillXp;
			}
			else if (config.maxSkillMode().equals(MaxSkillMode.COMPLETIONIST))
			{
				xpRequired = MAX_XP - lowestSkillXp;
			}

			var daysUntilTarget = (long) Math.ceil((double) xpRequired / config.minimumXpOverride());
			if (daysUntilTarget <= 0)
			{
				return LocalDate.now();
			}
			return LocalDate.now().plusDays(daysUntilTarget);
		}
		return null;
	}
}