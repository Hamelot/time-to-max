/*
 * Copyright (c) 2017, Cameron <moberg@tuta.io>
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
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

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.DragAndDropReorderPane;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.ImageUtil;

class XpPanel extends PluginPanel
{
	private final Map<Skill, XpInfoBox> infoBoxes = new HashMap<>();
	private final JLabel overallExpGained = new JLabel(XpInfoBox.htmlLabel("Gained: ", 0));
	private final JLabel overallExpHour = new JLabel(XpInfoBox.htmlLabel("Per hour: ", 0));

	private final JPanel overallPanel = new JPanel();

	// New panel for target XP information
	private final JPanel targetPanel = new JPanel();
	private final JLabel targetDateLabel = new JLabel(XpInfoBox.htmlLabel("Target Date: ", ""));
	private final JLabel targetIntervalLabel = new JLabel(XpInfoBox.htmlLabel("Tracking: ", ""));
	private final JLabel intervalsRemainingLabel = new JLabel(XpInfoBox.htmlLabel("Intervals remaining: ", ""));

	/* This displays the "track xp" text */
	private final PluginErrorPanel errorPanel = new PluginErrorPanel();

	XpPanel(TimeToMaxPlugin timeToMaxPlugin, TimeToMaxConfig timeToMaxConfig, Client client, SkillIconManager iconManager)
	{
		super();

		setBorder(new EmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		final JPanel layoutPanel = new JPanel();
		BoxLayout boxLayout = new BoxLayout(layoutPanel, BoxLayout.Y_AXIS);
		layoutPanel.setLayout(boxLayout);
		add(layoutPanel, BorderLayout.NORTH);

		overallPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		overallPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		overallPanel.setLayout(new BorderLayout());
		overallPanel.setVisible(false);

		// Create reset all per hour menu
		final JMenuItem resetPerHour = new JMenuItem("Reset All/hr");
		resetPerHour.addActionListener(e -> timeToMaxPlugin.resetAllSkillsPerHourState());

		// Create pause all menu
		final JMenuItem pauseAll = new JMenuItem("Pause All");
		pauseAll.addActionListener(e -> timeToMaxPlugin.pauseAllSkills(true));

		// Create unpause all menu
		final JMenuItem unpauseAll = new JMenuItem("Unpause All");
		unpauseAll.addActionListener(e -> timeToMaxPlugin.pauseAllSkills(false));


		// Create popup menu
		final JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
		popupMenu.add(resetPerHour);
		popupMenu.add(pauseAll);
		popupMenu.add(unpauseAll);

		overallPanel.setComponentPopupMenu(popupMenu);

		final JLabel overallIcon = new JLabel(new ImageIcon(ImageUtil.loadImageResource(getClass(), "/skill_icons/overall.png")));

		final JPanel overallInfo = new JPanel();
		overallInfo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		overallInfo.setLayout(new GridLayout(2, 1));
		overallInfo.setBorder(new EmptyBorder(0, 10, 0, 0));
		// Initialize the target panel
		targetPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		targetPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		targetPanel.setLayout(new GridLayout(4, 1));
		targetPanel.setVisible(true); // Make target panel visible by default

		targetDateLabel.setFont(FontManager.getRunescapeSmallFont());
		targetIntervalLabel.setFont(FontManager.getRunescapeSmallFont());
		intervalsRemainingLabel.setFont(FontManager.getRunescapeSmallFont());

		targetPanel.add(targetDateLabel);
		targetPanel.add(targetIntervalLabel);
		targetPanel.add(intervalsRemainingLabel);

		// Set initial values
		updateTargetPanel(timeToMaxConfig);

		overallExpGained.setFont(FontManager.getRunescapeSmallFont());
		overallExpHour.setFont(FontManager.getRunescapeSmallFont());

		overallInfo.add(overallExpGained);
		overallInfo.add(overallExpHour);
		overallPanel.add(overallIcon, BorderLayout.WEST);
		overallPanel.add(overallInfo, BorderLayout.CENTER);

		final JComponent infoBoxPanel = new DragAndDropReorderPane();

		// Add target panel to layout
		layoutPanel.add(targetPanel);

		errorPanel.setContent("Time To Max", "To start tracking how much XP you need per interval, make sure to:\n" +
			"<br/>\n" +
			"<br/>- Log in to your account\n" +
			"<br/>- Earn some XP\n" +
			"<br/><br/>Once you've done both, this plugin will begin calculating your XP needs automatically!");
		add(errorPanel);

		layoutPanel.add(overallPanel);
		layoutPanel.add(infoBoxPanel);

		for (Skill skill : Skill.values())
		{
			infoBoxes.put(skill, new XpInfoBox(timeToMaxPlugin, timeToMaxConfig, infoBoxPanel, skill, iconManager));
		}
	}

	void showOverallPanel()
	{
		overallPanel.setVisible(true);
	}

	void resetAllInfoBoxes()
	{
		infoBoxes.forEach((skill, xpInfoBox) -> xpInfoBox.reset());
	}

	void resetSkill(Skill skill)
	{
		final XpInfoBox xpInfoBox = infoBoxes.get(skill);
		xpInfoBox.reset();
	}

	void updateSkillExperience(boolean updated, boolean paused, Skill skill, XpSnapshotSingle xpSnapshotSingle)
	{
		final XpInfoBox xpInfoBox = infoBoxes.get(skill);
		xpInfoBox.update(updated, paused, xpSnapshotSingle);
	}

	void updateTotal(XpSnapshotSingle xpSnapshotTotal)
	{
		// if player has gained exp and hasn't switched displays yet, hide error panel and show overall info
		if (xpSnapshotTotal.getXpGainedInSession() > 0 && !overallPanel.isVisible())
		{
			overallPanel.setVisible(true);
			remove(errorPanel);
		}
		else if (xpSnapshotTotal.getXpGainedInSession() == 0 && overallPanel.isVisible())
		{
			overallPanel.setVisible(false);
			add(errorPanel);
		}

		SwingUtilities.invokeLater(() -> rebuildAsync(xpSnapshotTotal));
	}

	private void rebuildAsync(XpSnapshotSingle xpSnapshotTotal)
	{
		overallExpGained.setText(XpInfoBox.htmlLabel("Gained: ", xpSnapshotTotal.getXpGainedInSession()));
		overallExpHour.setText(XpInfoBox.htmlLabel("Per hour: ", xpSnapshotTotal.getXpPerHour()));
	}

	/**
	 * Updates the target panel with the current configuration values
	 */
	void updateTargetPanel(TimeToMaxConfig config)
	{
		try
		{
			LocalDate targetDate = LocalDate.parse(config.targetDate());
			TrackingInterval interval = config.trackingInterval();
			LocalDate now = LocalDate.now();

			long intervalsRemaining;
			String timeLeftInCurrentInterval;
			String intervalUnit;
			String currentIntervalLabel;

			java.time.LocalDateTime currentTime = java.time.LocalDateTime.now();

			switch (interval)
			{
				case DAY:
					intervalUnit = "Day";
					currentIntervalLabel = "day";

					// Calculate total days remaining to the target date
					intervalsRemaining = java.time.temporal.ChronoUnit.DAYS.between(now, targetDate);

					// Calculate hours remaining in the current day
					java.time.LocalDateTime endOfDay = currentTime.toLocalDate().atTime(23, 59, 59);
					long hoursRemaining = java.time.temporal.ChronoUnit.HOURS.between(currentTime, endOfDay);
					timeLeftInCurrentInterval = (hoursRemaining + 1) + " hour" + (hoursRemaining + 1 != 1 ? "s" : "");
					break;
				case WEEK:
					intervalUnit = "Week";
					currentIntervalLabel = "week";

					// Calculate total weeks remaining to the target date
					intervalsRemaining = java.time.temporal.ChronoUnit.WEEKS.between(now, targetDate);

					// Calculate days remaining in the current week (assuming week ends on Sunday)
					java.time.DayOfWeek currentDay = currentTime.getDayOfWeek();
					int daysUntilEndOfWeek = java.time.DayOfWeek.SUNDAY.getValue() - currentDay.getValue();
					if (daysUntilEndOfWeek < 0)
					{
						daysUntilEndOfWeek += 7; // If today is Sunday, there are 0 days left
					}
					timeLeftInCurrentInterval = daysUntilEndOfWeek + " day" + (daysUntilEndOfWeek != 1 ? "s" : "");
					break;
				case MONTH:
					intervalUnit = "Month";
					currentIntervalLabel = "month";

					// Calculate total months remaining to the target date
					intervalsRemaining = java.time.temporal.ChronoUnit.MONTHS.between(now.withDayOfMonth(1), targetDate.withDayOfMonth(1));

					// Calculate days remaining in the current month
					java.time.LocalDate lastDayOfMonth = currentTime.toLocalDate()
						.withDayOfMonth(currentTime.toLocalDate().lengthOfMonth());
					long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(
						currentTime.toLocalDate(), lastDayOfMonth) + 1; // +1 to include today
					timeLeftInCurrentInterval = daysRemaining + " day" + (daysRemaining != 1 ? "s" : "");
					break;
				default:
					intervalUnit = "Interval";
					currentIntervalLabel = "interval";
					intervalsRemaining = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(now, targetDate));
					timeLeftInCurrentInterval = "Unknown";
			}

			targetDateLabel.setText(XpInfoBox.htmlLabel("Target Date: ", targetDate.toString()));
			targetIntervalLabel.setText(XpInfoBox.htmlLabel("Tracking: ", "Per " + interval.toString().toLowerCase()));

			// Format the label with plural handling
			String intervalsRemainingText = intervalUnit + "s remaining to goal: " + intervalsRemaining;
			String timeLeftText = "Time left in current " + currentIntervalLabel + ": " + timeLeftInCurrentInterval;

			intervalsRemainingLabel.setText(XpInfoBox.htmlLabel(intervalsRemainingText, ""));
			JLabel timeLeftLabel = new JLabel(XpInfoBox.htmlLabel(timeLeftText, ""));
			timeLeftLabel.setFont(FontManager.getRunescapeSmallFont());

			// Update the target panel
			targetPanel.removeAll();
			targetPanel.add(targetDateLabel);
			targetPanel.add(targetIntervalLabel);
			targetPanel.add(intervalsRemainingLabel);
			targetPanel.add(timeLeftLabel);

			targetPanel.setVisible(true);
			targetPanel.revalidate();
			targetPanel.repaint();
		}
		catch (DateTimeParseException e)
		{
			targetDateLabel.setText(XpInfoBox.htmlLabel("Target Date: ", "Invalid date format"));
			targetIntervalLabel.setText(XpInfoBox.htmlLabel("Tracking: ", config.trackingInterval().toString()));
			intervalsRemainingLabel.setText(XpInfoBox.htmlLabel("Intervals remaining: ", "Unknown"));

			targetPanel.setVisible(true);
			targetPanel.revalidate();
			targetPanel.repaint();
		}
	}
}
