/*
 * Copyright (c) 2017, Cameron <moberg@tuta.io>
 * Copyright (c) 2018, Levi <me@levischuck.com>
 * Copyright (c) 2020, Anthony <https://github.com/while-loop>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.timetomax;

import java.util.Arrays;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Experience;

@Slf4j
class XpStateSingle
{
	private int actions = 0;
	private int actionsSinceReset = 0;
	private boolean actionsHistoryInitialized = false;
	private final int[] actionExps = new int[10];
	private int actionExpIndex = 0;

	@Getter
	@Setter
	private long startXp;

	@Getter
	@Setter
	private long endXp;

	@Getter
	@Setter
	private int startDay = 31;

	@Getter
	@Setter
	private int startMonth = 12;

	@Getter
	@Setter
	private int startYear = 9999;

	@Getter
	private int xpGainedSinceReset = 0;

	private int xpGainedBeforeReset = 0;

	// how long the skill has been trained for in ms
	@Setter
	private long skillTime = 0;
	// the last time the skill xp changed in ms
	@Getter
	private long lastChangeMillis;
	private int startLevelExp = 0;
	private int endLevelExp = 0;

	@Getter
	@Setter
	private boolean lowestSkill = false;

	XpStateSingle(long startXp, long endXp)
	{
		this.startXp = startXp;
		this.endXp = endXp;
	}

	XpStateSingle(long startXp)
	{
		this.startXp = startXp;
	}

	long getCurrentXp()
	{
		return startXp + getTotalXpGained();
	}

	int getTotalXpGained()
	{
		return xpGainedBeforeReset + xpGainedSinceReset;
	}

	private int getActionsHr()
	{
		return toHourly(actionsSinceReset);
	}

	private int toHourly(int value)
	{
		return (int) ((1.0 / (getTimeElapsedInSeconds() / 3600.0)) * value);
	}

	private long getTimeElapsedInSeconds()
	{
		// If the skill started just now, we can divide by near zero, this results in odd behavior.
		// To prevent that, pretend the skill has been active for a minute (60 seconds)
		// This will create a lower estimate for the first minute,
		// but it isn't ridiculous like saying 2 billion XP per hour.
		return Math.max(60, skillTime / 1000);
	}

	private int getXpRemaining()
	{
		// Always use endLevelExp which is the current goal (either user defined or next level)
		// endLevelExp is properly set in updateGoals based on the user's configured goal
		int xpGained = getTotalXpGained();
		int xpGoal = endLevelExp - startLevelExp;
		return Math.max(0, xpGoal - xpGained);
	}

	private int getActionsRemaining()
	{
		if (actionsHistoryInitialized)
		{
			// Use XP remaining to the actual goal (endLevelExp) rather than next level
			long xpRemaining = getXpRemaining() * actionExps.length;
			long totalActionXp = 0;

			for (int actionXp : actionExps)
			{
				totalActionXp += actionXp;
			}

			// Let's not divide by zero (or negative)
			if (totalActionXp > 0)
			{
				// Make sure to account for the very last action at the end
				long remainder = xpRemaining % totalActionXp;
				long quotient = xpRemaining / totalActionXp;
				return Math.toIntExact(quotient + (remainder > 0 ? 1 : 0));
			}
		}

		return Integer.MAX_VALUE;
	}

	private double getSkillProgress()
	{
		double xpGained = getTotalXpGained();
		double xpGoal = endLevelExp - startLevelExp;
		return (xpGained / xpGoal) * 100;
	}

	private long getSecondsTillLevel()
	{
		long seconds = getTimeElapsedInSeconds();
		if (seconds <= 0 || xpGainedSinceReset <= 0)
		{
			return -1;
		}

		// formula is xpRemaining / xpPerSecond
		// xpPerSecond being total xp gained / seconds
		// This can be simplified so division is only done once and we can work in whole numbers!
		return (getXpRemaining() * seconds) / xpGainedSinceReset;
	}

	private String getTimeTillLevel(XpGoalTimeType goalTimeType)
	{
		long remainingSeconds = getSecondsTillLevel();
		if (remainingSeconds < 0)
		{
			return "\u221e";
		}

		// Java 8 doesn't have good duration / period objects to represent spans of time that can be formatted
		// Rather than importing another dependency like joda time (which is practically built into java 10)
		// below will be a custom formatter that handles spans larger than 1 day
		long durationDays = remainingSeconds / (24 * 60 * 60);
		long durationHours = (remainingSeconds % (24 * 60 * 60)) / (60 * 60);
		long durationHoursTotal = remainingSeconds / (60 * 60);
		long durationMinutes = (remainingSeconds % (60 * 60)) / 60;
		long durationSeconds = remainingSeconds % 60;

		switch (goalTimeType)
		{
			case DAYS:
				if (durationDays > 1)
				{
					return String.format("%d days %02d:%02d:%02d", durationDays, durationHours, durationMinutes, durationSeconds);
				}
				else if (durationDays == 1)
				{
					return String.format("1 day %02d:%02d:%02d", durationHours, durationMinutes, durationSeconds);
				}
			case HOURS:
				if (durationHoursTotal > 1)
				{
					return String.format("%d hours %02d:%02d", durationHoursTotal, durationMinutes, durationSeconds);
				}
				else if (durationHoursTotal == 1)
				{
					return String.format("1 hour %02d:%02d", durationMinutes, durationSeconds);
				}
			case SHORT:
			default:
				// durationDays = 0 or durationHoursTotal = 0 or goalTimeType = SHORT if we got here.
				// return time remaining in hh:mm:ss or mm:ss format where hh can be > 24
				if (durationHoursTotal > 0)
				{
					return String.format("%d:%02d:%02d", durationHoursTotal, durationMinutes, durationSeconds);
				}

				// Minutes and seconds will always be present
				return String.format("%02d:%02d", durationMinutes, durationSeconds);
		}
	}

	int getXpHr()
	{
		return toHourly(xpGainedSinceReset);
	}

	void resetPerHour()
	{
		//reset actions per hour
		actionsSinceReset = 0;

		//preserve total xp gained while resetting the per-hour tracking
		xpGainedBeforeReset += xpGainedSinceReset;
		xpGainedSinceReset = 0;
		lastChangeMillis = System.currentTimeMillis();
		setSkillTime(0);
	}

	boolean update(long currentXp)
	{
		if (startXp == -1)
		{
			log.warn("Attempted to update skill state {} but was not initialized with current xp", this);
			return false;
		}

		// Calculate XP gained since last update
		long previousTotal = getTotalXpGained();
		int actionExp = (int) (currentXp - (startXp + previousTotal));

		// No experience gained
		if (actionExp == 0)
		{
			return false;
		}

		if (actionsHistoryInitialized)
		{
			actionExps[actionExpIndex] = actionExp;
		}
		else
		{
			// populate all values in our action history array with this first value that we see
			// so the average value of our action history starts out as this first value we see
			Arrays.fill(actionExps, actionExp);
			actionsHistoryInitialized = true;
		}

		actionExpIndex = (actionExpIndex + 1) % actionExps.length;
		actions++;
		actionsSinceReset++;

		// Calculate experience gained
		xpGainedSinceReset = (int) (currentXp - (startXp + xpGainedBeforeReset));
		lastChangeMillis = System.currentTimeMillis();

		return true;
	}

	void updateGoals(int goalStartXp, int goalEndXp)
	{
		// Since we're calculating start and end goal, we just set the values directly
		// Default to 0 if the goal is not set
		startLevelExp = Math.max(goalStartXp, 0);

		endLevelExp = Math.max(goalEndXp, 0);
		endXp = endLevelExp;
	}

	void updateStartDate(int startDay, int startMonth, int startYear)
	{
		this.startDay = startDay;
		this.startMonth = startMonth;
		this.startYear = startYear;
	}

	public void tick(long delta)
	{
		// Track time as long as we have gained XP since baseline
		if (xpGainedSinceReset <= 0)
		{
			return;
		}
		skillTime += delta;
	}

	LocalDate convertToLocalDate(int year, int month, int day)
	{
		try
		{
			String dateText = String.format("%04d-%02d-%02d", year, month, day);
			return LocalDate.parse(dateText);
		}
		catch (Exception ex)
		{
			return LocalDate.parse("9999-12-31");
		}
	}

	XpSnapshotSingle snapshot()
	{
		return XpSnapshotSingle.builder()
			.startLevel(Experience.getLevelForXp(startLevelExp))
			.endLevel(Experience.getLevelForXp(endLevelExp))
			.startDay(getStartDay())
			.startMonth(getStartMonth())
			.startYear(getStartYear())
			.xpGainedInSession(getTotalXpGained())
			.xpRemainingToGoal(getXpRemaining())
			.xpPerHour(getXpHr())
			.skillProgressToGoal(getSkillProgress())
			.actionsInSession(actions)
			.actionsRemainingToGoal(getActionsRemaining())
			.actionsPerHour(getActionsHr())
			.timeTillGoal(getTimeTillLevel(XpGoalTimeType.DAYS))
			.timeTillGoalHours(getTimeTillLevel(XpGoalTimeType.HOURS))
			.timeTillGoalShort(getTimeTillLevel(XpGoalTimeType.SHORT))
			.startGoalXp(startLevelExp)
			.endGoalXp(endLevelExp)
			.lowestSkill(lowestSkill)
			.build();
	}

	XpSaveSingle save()
	{
		XpSaveSingle save = new XpSaveSingle();
		save.startXp = startXp;
		save.endXp = endXp;
		save.startDay = startDay;
		save.startMonth = startMonth;
		save.startYear = startYear;
		save.lowestSkill = lowestSkill;
		save.xpGainedBeforeReset = xpGainedBeforeReset;
		save.xpGainedSinceReset = xpGainedSinceReset;
		save.time = skillTime;
		return save;
	}

	void restore(XpSaveSingle save)
	{
		startXp = save.startXp;
		endXp = save.endXp;
		startDay = save.startDay;
		startMonth = save.startMonth;
		startYear = save.startYear;
		lowestSkill = save.lowestSkill;
		xpGainedBeforeReset = save.xpGainedBeforeReset;
		xpGainedSinceReset = save.xpGainedSinceReset;
		skillTime = save.time;
	}
}
