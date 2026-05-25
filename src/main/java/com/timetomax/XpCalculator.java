package com.timetomax;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

	// Dev-only time offset. now()/today() return real time + this offset.
	// Set via the ::ttmdate command (only active with -Dtimetomax.dev=true). Not persisted.
	// Using an offset (instead of a frozen LocalDateTime) means time keeps ticking naturally
	// from the override point, so the panel clock advances and boundary crossings happen live.
	private static volatile Duration timeOffset = Duration.ZERO;

	/**
	 * Returns the current date-time, honoring the dev-only offset if one is set.
	 * All interval / boundary / target-date logic should call this (or today()) instead of
	 * LocalDate.now() / LocalDateTime.now() so the override takes effect end-to-end.
	 */
	public static LocalDateTime now()
	{
		Duration off = timeOffset;
		return off.isZero() ? LocalDateTime.now() : LocalDateTime.now().plus(off);
	}

	public static LocalDate today()
	{
		return now().toLocalDate();
	}

	public static Duration getTimeOffset()
	{
		return timeOffset;
	}

	public static boolean isTimeOverridden()
	{
		return !timeOffset.isZero();
	}

	public static void clearTimeOffset()
	{
		timeOffset = Duration.ZERO;
	}

	/**
	 * Shift the dev offset by the given delta. Positive jumps forward, negative jumps back.
	 */
	public static void shiftTime(Duration delta)
	{
		if (delta != null)
		{
			timeOffset = timeOffset.plus(delta);
		}
	}

	/**
	 * Set the offset such that now() returns the given target time. Real-time ticking continues
	 * from that point (e.g., set to 23:59:50 to watch the clock cross midnight 10 seconds later).
	 */
	public static void setOverrideTarget(LocalDateTime target)
	{
		timeOffset = Duration.between(LocalDateTime.now(), target);
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
		long daysUntilTarget = ChronoUnit.DAYS.between(today(), LocalDate.parse(config.targetDate()));
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

		LocalDate now = today();
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

	/**
	 * Number of days remaining in the current interval, counting today as one of them.
	 * Used to compute the daily catch-up XP target inside a WEEK/MONTH interval.
	 *
	 * @param interval The tracking interval
	 * @return Days remaining (>= 1). Always 1 for DAY.
	 */
	public static int getDaysRemainingInInterval(TrackingInterval interval)
	{
		LocalDate now = today();
		switch (interval)
		{
			case WEEK:
				// Mon=1..Sun=7. From Mon, 7 days remain; from Sun, 1.
				return DayOfWeek.SUNDAY.getValue() - now.getDayOfWeek().getValue() + 1;
			case MONTH:
				return now.lengthOfMonth() - now.getDayOfMonth() + 1;
			case DAY:
			default:
				return 1;
		}
	}

	/**
	 * Get the start date of the current period for the given interval, anchored to today.
	 * Used so a reset that fires late (e.g. user wasn't logged in at the Monday boundary)
	 * still anchors the new reference to the actual start of the ISO period, preventing
	 * the reference date from drifting forward across consecutive missed boundaries.
	 *
	 * @param interval The tracking interval
	 * @return The start of the current period (today for DAY, Monday for WEEK, 1st of month for MONTH)
	 */
	public static LocalDate getCurrentPeriodStart(TrackingInterval interval)
	{
		LocalDate now = today();
		switch (interval)
		{
			case WEEK:
				return now.with(DayOfWeek.MONDAY);
			case MONTH:
				return now.withDayOfMonth(1);
			default:
				return now;
		}
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
				return today();
			}
			return today().plusDays(daysUntilTarget);
		}
		return null;
	}
}