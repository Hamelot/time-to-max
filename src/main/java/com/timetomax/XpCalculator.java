package com.timetomax;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Utility class for XP calculations and display
 */
public class XpCalculator
{
	// XP required for level 99 in each skill
	public static final int MAX_XP = 13_034_431;

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

}