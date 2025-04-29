package com.timetomax;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.time.LocalDate;

@ConfigGroup("timetomax")
public interface TimeToMaxConfig extends Config
{
	@ConfigItem(
		keyName = "trackingInterval",
		name = "XP Tracking Interval",
		description = "The time interval to track XP gains (day, week, or month)"
	)
	default TrackingInterval trackingInterval()
	{
		return TrackingInterval.WEEK;
	}

	@ConfigItem(
		keyName = "targetDate",
		name = "Target Date",
		description = "The target date to reach max level (format: YYYY-MM-DD)"
	)
	default String targetDate()
	{
		// Default to 6 months from now
		return LocalDate.now().plusMonths(6).toString();
	}

	@ConfigItem(
		keyName = "showChatNotifications",
		name = "Show Goal Notifications in chat",
		description = "Show game chat notifications when you've gained enough XP to meet your interval goal"
	)
	default boolean showChatNotifications()
	{
		return true;
	}
}
