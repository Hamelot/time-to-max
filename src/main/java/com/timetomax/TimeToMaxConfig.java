/*
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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Units;

import java.time.LocalDate;

@ConfigGroup("timeToMax")
public interface TimeToMaxConfig extends Config
{
	@ConfigSection(
		name = "Maxing Settings",
		description = "Settings related to max calculations",
		position = 0
	)
	String maxingSection = "maxing";

	@ConfigSection(
		name = "Info Label Settings",
		description = "Settings for the info labels",
		position = 1
	)
	String infoLabelSection = "infolabel";

	@ConfigSection(
		name = "Overlay",
		description = "Canvas overlay options.",
		position = 2
	)
	String overlaySection = "overlay";

	@ConfigItem(
		section = maxingSection,
		position = 0,
		keyName = "targetDate",
		name = "Target Date",
		description = "The target date to reach max level (format: YYYY-MM-DD)"
	)
	default String targetDate()
	{
		// Default to 1 year from now
		return LocalDate.now().plusYears(1).toString();
	}

	@ConfigItem(
		section = maxingSection,
		position = 1,
		keyName = "trackingInterval",
		name = "Tracking Interval",
		description = "The interval used for tracking XP progress (day, week, month)"
	)
	default TrackingInterval trackingInterval()
	{
		return TrackingInterval.DAY;
	}

	@ConfigItem(
		section = maxingSection,
		position = 2,
		keyName = "maxSkillMode",
		name = "Max Skill Mode",
		description = "Use level 99, 200m, or a custom xp count as xp goal"
	)
	default MaxSkillMode maxSkillMode()
	{
		return MaxSkillMode.NORMAL;
	}

	@ConfigItem(
		section = maxingSection,
		position = 3,
		keyName = "xpOverride",
		name = "Override Xp",
		description = "Use provided minimum xp values in target calculation"
	)
	default boolean xpOverride()
	{
		return false;
	}

	@ConfigItem(
		section = maxingSection,
		position = 4,
		keyName = "minimumXpOverride",
		name = "Minimum Daily Xp",
		description = "Amount of xp per non-99 to override the calculations, and give a countdown to max."
	)
	default int minimumXpOverride()
	{
		return 50_000;
	}

	@ConfigItem(
		section = maxingSection,
		position = 5,
		keyName = "targetDateWithXpOverride",
		name = "Target Date with XP Override",
		description = "Holds data for target date if user gets the minimum xp required with override setting.",
		hidden = true
	)
	default String targetDateWithXpOverride()
	{
		return LocalDate.now().plusYears(1).toString();
	}

	@ConfigItem(
		section = maxingSection,
		position = 6,
		keyName = "prioritizeRecentXpSkills",
		name = "Move recently trained skills to top",
		description = "Configures whether skills should be organized by most recently gained XP."
	)
	default boolean prioritizeRecentXpSkills()
	{
		return true;
	}

	@ConfigItem(
		section = maxingSection,
		position = 7,
		keyName = "pinCompletedSkillsToBottom",
		name = "Pin completed skills to bottom",
		description = "Configures whether completed skills should be pinned to the bottom of the list."
	)
	default boolean pinCompletedSkillsToBottom()
	{
		return true;
	}

	@ConfigItem(
		section = maxingSection,
		position = 7,
		keyName = "collapseCompletedSkills",
		name = "Collapse completed skills",
		description = "Configures whether completed skills should be collapsed."
	)
	default boolean collapseCompletedSkills()
	{
		return true;
	}

	@ConfigItem(
		section = maxingSection,
		position = 8,
		keyName = "highlightLowestSkill",
		name = "Highlight lowest skill",
		description = "Paints a thin border around skill with the lowest xp"
	)
	default boolean highlightLowestSkill()
	{
		return true;
	}

	@ConfigItem(
		section = infoLabelSection,
		position = 0,
		keyName = "logoutPausing",
		name = "Pause on logout",
		description = "Configures whether skills should pause on logout."
	)
	default boolean pauseOnLogout()
	{
		return true;
	}

	@ConfigItem(
		section = infoLabelSection,
		position = 1,
		keyName = "pauseSkillAfter",
		name = "Auto pause after",
		description = "Configures how many minutes passes before pausing a skill while in game and there's no XP, 0 means disabled."
	)
	@Units(Units.MINUTES)
	default int pauseSkillAfter()
	{
		return 0;
	}

	@ConfigItem(
		section = infoLabelSection,
		position = 2,
		keyName = "resetSkillRateAfter",
		name = "Auto reset after",
		description = "Configures how many minutes passes before resetting a skill's per hour rates while in game and there's no XP, 0 means disabled."
	)
	@Units(Units.MINUTES)
	default int resetSkillRateAfter()
	{
		return 0;
	}

	@ConfigItem(
		section = infoLabelSection,
		position = 3,
		keyName = "xpPanelLabel1",
		name = "Top-left XP info label",
		description = "Configures the information displayed in the top-left of XP info box."
	)
	default XpPanelLabel xpPanelLabel1()
	{
		return XpPanelLabel.XP_GAINED;
	}

	@ConfigItem(
		section = infoLabelSection,
		position = 4,
		keyName = "xpPanelLabel2",
		name = "Top-right XP info label",
		description = "Configures the information displayed in the top-right of XP info box."
	)

	default XpPanelLabel xpPanelLabel2()
	{
		return XpPanelLabel.XP_LEFT;
	}

	@ConfigItem(
		section = infoLabelSection,
		position = 5,
		keyName = "xpPanelLabel3",
		name = "Bottom-left XP info label",
		description = "Configures the information displayed in the bottom-left of XP info box."
	)
	default XpPanelLabel xpPanelLabel3()
	{
		return XpPanelLabel.XP_HOUR;
	}

	@ConfigItem(
		section = infoLabelSection,
		position = 6,
		keyName = "xpPanelLabel4",
		name = "Bottom-right XP info label",
		description = "Configures the information displayed in the bottom-right of XP info box."
	)
	default XpPanelLabel xpPanelLabel4()
	{
		return XpPanelLabel.ACTIONS_LEFT;
	}

	@ConfigItem(
		section = infoLabelSection,
		position = 7,
		keyName = "progressBarLabel",
		name = "Progress bar label",
		description = "Configures the info box progress bar to show time to goal or percentage complete."
	)
	default XpProgressBarLabel progressBarLabel()
	{
		return XpProgressBarLabel.PERCENTAGE;
	}

	@ConfigItem(
		section = infoLabelSection,
		position = 8,
		keyName = "progressBarTooltipLabel",
		name = "Tooltip label",
		description = "Configures the info box progress bar tooltip to show time to goal or percentage complete."
	)
	default XpProgressBarLabel progressBarTooltipLabel()
	{
		return XpProgressBarLabel.TIME_TO_LEVEL;
	}

	@ConfigItem(
		position = 0,
		keyName = "skillTabOverlayMenuOptions",
		name = "Add skill tab canvas menu option",
		description = "Configures whether a menu option to show/hide canvas XP trackers will be added to skills on the skill tab.",
		section = overlaySection
	)
	default boolean skillTabOverlayMenuOptions()
	{
		return true;
	}

	@ConfigItem(
		position = 1,
		keyName = "onScreenDisplayMode",
		name = "On-screen tracker display mode (top)",
		description = "Configures the information displayed in the first line of on-screen XP overlays.",
		section = overlaySection
	)
	default XpPanelLabel onScreenDisplayMode()
	{
		return XpPanelLabel.XP_GAINED;
	}

	@ConfigItem(
		position = 2,
		keyName = "onScreenDisplayModeBottom",
		name = "On-screen tracker display mode (bottom)",
		description = "Configures the information displayed in the second line of on-screen XP overlays.",
		section = overlaySection
	)
	default XpPanelLabel onScreenDisplayModeBottom()
	{
		return XpPanelLabel.XP_HOUR;
	}
}
