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
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.util.Date;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.DragAndDropReorderPane;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.ImageUtil;

@Slf4j
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
	// Configuration controls panel
	private final JPanel configPanel = new JPanel();
	private final JPanel configHeaderPanel = new JPanel();
	private final JButton configToggleButton = new JButton("▶ Configuration");	private final JPanel configContentPanel = new JPanel();
	private final JSpinner targetDateSpinner = new JSpinner(new SpinnerDateModel());
	private final JComboBox<TrackingInterval> trackingIntervalCombo = new JComboBox<>(TrackingInterval.values());
	private final JComboBox<MaxSkillMode> maxSkillModeCombo = new JComboBox<>(MaxSkillMode.values());
	private boolean configExpanded = false;
	// Reference to plugin for accessing injected dependencies
	//private final TimeToMaxPlugin plugin;
	private final ConfigManager configManager;

	/* This displays the "track xp" text */
	private final PluginErrorPanel errorPanel = new PluginErrorPanel();
	/**
	 * Sets up the configuration panel with controls for changing config values
	 */	private void setupConfigPanel(TimeToMaxConfig config)
	{
		// Set up main config panel
		configPanel.setLayout(new BorderLayout());
		configPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// Add thin white border around the entire config panel
		configPanel.setBorder(new LineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1));
		
		// Set up header panel with toggle button
		configHeaderPanel.setLayout(new BorderLayout());
		configHeaderPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		configHeaderPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		
		configToggleButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		configToggleButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		configToggleButton.setBorderPainted(false);
		configToggleButton.setFocusPainted(false);
		configToggleButton.setContentAreaFilled(false);
		configToggleButton.addActionListener(e -> toggleConfigPanel());
		
		configHeaderPanel.add(configToggleButton, BorderLayout.WEST);
		configPanel.add(configHeaderPanel, BorderLayout.NORTH);
		
		// Set up content panel
		configContentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		configContentPanel.setLayout(new GridLayout(6, 2, 5, 5));
		configContentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
				// Target Date
		configContentPanel.add(new JLabel("Target Date:"));
		
		// Set up date spinner
		targetDateSpinner.setEditor(new JSpinner.DateEditor(targetDateSpinner, "yyyy-MM-dd"));
		try {
			LocalDate configDate = LocalDate.parse(config.targetDate());
			Date date = Date.from(configDate.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant());
			targetDateSpinner.setValue(date);
		} catch (Exception e) {
			// Default to today's date if parsing fails
			targetDateSpinner.setValue(new Date());
		}
		
		targetDateSpinner.addChangeListener(e -> {
			Date selectedDate = (Date) targetDateSpinner.getValue();
			LocalDate localDate = selectedDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
			updateConfigValue("targetDate", localDate.toString());
		});
		
		configContentPanel.add(targetDateSpinner);
		
		// Tracking Interval
		configContentPanel.add(new JLabel("Tracking Interval:"));
		trackingIntervalCombo.setSelectedItem(config.trackingInterval());
		trackingIntervalCombo.addActionListener(e -> updateConfigValue("trackingInterval", ((TrackingInterval) trackingIntervalCombo.getSelectedItem()).name()));
		configContentPanel.add(trackingIntervalCombo);

		// Max Skill Mode
		configContentPanel.add(new JLabel("Max Skill Mode:"));
		maxSkillModeCombo.setSelectedItem(config.maxSkillMode());
		maxSkillModeCombo.addActionListener(e -> updateConfigValue("maxSkillMode", ((MaxSkillMode) maxSkillModeCombo.getSelectedItem()).name()));
		configContentPanel.add(maxSkillModeCombo);
		
		// Start collapsed by default
		configContentPanel.setVisible(false);
		configExpanded = false;
	}
	
	/**
	 * Toggles the visibility of the config panel content
	 */
	private void toggleConfigPanel()
	{
		configExpanded = !configExpanded;
		configContentPanel.setVisible(configExpanded);
		configToggleButton.setText(configExpanded ? "▼ Configuration" : "▶ Configuration");
		
		// Add or remove content panel based on expanded state
		if (configExpanded && configContentPanel.getParent() == null)
		{
			configPanel.add(configContentPanel, BorderLayout.CENTER);
		}
		else if (!configExpanded && configContentPanel.getParent() != null)
		{
			configPanel.remove(configContentPanel);
		}
		
		configPanel.revalidate();
		configPanel.repaint();
		this.revalidate();
		this.repaint();
	}

	/**
	 * Updates a config value using the ConfigManager
	 */
	private void updateConfigValue(String key, String value)
	{
		try
		{
			configManager.setConfiguration("timeToMax", key, value);
		}
		catch (Exception e)
		{
			// Handle validation errors gracefully - for now just log the error
			XpPanel.log.debug("Failed to update config value {} to '{}': {}", key, value, e.getMessage());
		}
	}

	XpPanel(TimeToMaxPlugin timeToMaxPlugin, TimeToMaxConfig timeToMaxConfig, Client client, SkillIconManager iconManager)
	{
		super();

		this.configManager = timeToMaxPlugin.getInjectedConfigManager();

		setBorder(new EmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		final JPanel layoutPanel = new JPanel();
		BoxLayout boxLayout = new BoxLayout(layoutPanel, BoxLayout.Y_AXIS);
		layoutPanel.setLayout(boxLayout);
		add(layoutPanel, BorderLayout.NORTH);

		// Initialize config panel
		setupConfigPanel(timeToMaxConfig);

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
		
		// Add config panel to layout
		layoutPanel.add(configPanel);

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

			LocalDateTime currentTime = LocalDateTime.now();
			LocalDateTime nextIntervalEnd;

			switch (interval)
			{
				case DAY:
					intervalUnit = "Day";
					currentIntervalLabel = "day";
					intervalsRemaining = ChronoUnit.DAYS.between(now, targetDate);
					nextIntervalEnd = currentTime.toLocalDate().atTime(23, 59, 59);
					break;
				case WEEK:
					intervalUnit = "Week";
					currentIntervalLabel = "week";
					intervalsRemaining = ChronoUnit.WEEKS.between(now, targetDate);
					// End of week (Sunday 23:59:59)
					int daysUntilEndOfWeek = DayOfWeek.SUNDAY.getValue() - currentTime.getDayOfWeek().getValue();
					if (daysUntilEndOfWeek < 0) daysUntilEndOfWeek += 7;
					nextIntervalEnd = currentTime.toLocalDate().plusDays(daysUntilEndOfWeek).atTime(23, 59, 59);
					break;
				case MONTH:
					intervalUnit = "Month";
					currentIntervalLabel = "month";
					intervalsRemaining = ChronoUnit.MONTHS.between(now.withDayOfMonth(1), targetDate.withDayOfMonth(1));
					LocalDate lastDayOfMonth = currentTime.toLocalDate().withDayOfMonth(currentTime.toLocalDate().lengthOfMonth());
					nextIntervalEnd = lastDayOfMonth.atTime(23, 59, 59);
					break;
				default:
					intervalUnit = "Interval";
					currentIntervalLabel = "interval";
					intervalsRemaining = Math.max(0, ChronoUnit.DAYS.between(now, targetDate));
					nextIntervalEnd = currentTime.plusDays(1).withHour(23).withMinute(59).withSecond(59);
			}

			Duration duration = Duration.between(currentTime, nextIntervalEnd);
			long totalSeconds = duration.getSeconds();
			if (totalSeconds < 0) totalSeconds = 0;

			long months = 0;
			long days = 0;
			long hours = 0;
			long minutes = 0;

			// Calculate months and days if needed
			java.time.LocalDateTime temp = currentTime;
			if (totalSeconds > 30L * 24 * 3600) {
				// More than 30 days
				months = ChronoUnit.MONTHS.between(temp.toLocalDate(), nextIntervalEnd.toLocalDate());
				temp = temp.plusMonths(months);
			}
			if (totalSeconds > 24 * 3600) {
				days = ChronoUnit.DAYS.between(temp.toLocalDate(), nextIntervalEnd.toLocalDate());
				temp = temp.plusDays(days);
			}
			Duration remainder = Duration.between(temp, nextIntervalEnd);
			hours = remainder.toHours();
			minutes = remainder.toMinutes() % 60;

			StringBuilder sb = new StringBuilder();
			if (totalSeconds > 30L * 24 * 3600) {
				if (months > 0) sb.append(months).append(" month").append(months != 1 ? "s " : " ");
				if (days > 0) sb.append(days).append(" day").append(days != 1 ? "s " : " ");
				sb.append(String.format("%02d:%02d", hours, minutes));
			} else if (totalSeconds > 24 * 3600) {
				if (days > 0) sb.append(days).append(" day").append(days != 1 ? "s " : " ");
				sb.append(String.format("%02d:%02d", hours, minutes));
			} else {
				sb.append(String.format("%02d:%02d", hours, minutes));
			}
			timeLeftInCurrentInterval = sb.toString().trim();

			targetDateLabel.setText(XpInfoBox.htmlLabel("Target Date: ", targetDate.toString()));
			targetIntervalLabel.setText(XpInfoBox.htmlLabel("Tracking: ", "Per " + interval.toString().toLowerCase()));

			String intervalsRemainingText = intervalUnit + "s remaining to goal: ";
			String timeLeftText = "Time left in current " + currentIntervalLabel + ": ";

			intervalsRemainingLabel.setText(XpInfoBox.htmlLabel(intervalsRemainingText, String.valueOf(intervalsRemaining)));
			JLabel timeLeftLabel = new JLabel(XpInfoBox.htmlLabel(timeLeftText, timeLeftInCurrentInterval));
			timeLeftLabel.setFont(FontManager.getRunescapeSmallFont());

			targetPanel.removeAll();
			targetPanel.add(targetDateLabel);
			targetPanel.add(targetIntervalLabel);
			targetPanel.add(intervalsRemainingLabel);
			targetPanel.add(timeLeftLabel);
			targetPanel.setVisible(true);
			targetPanel.revalidate();
			targetPanel.repaint();
			
			// Update config controls to reflect current values
			refreshConfigControls(config);
		}
		catch (DateTimeParseException e)
		{
			targetDateLabel.setText(XpInfoBox.htmlLabel("Target Date: ", "Invalid date format"));
			targetIntervalLabel.setText(XpInfoBox.htmlLabel("Tracking: ", config.trackingInterval().toString()));
			intervalsRemainingLabel.setText(XpInfoBox.htmlLabel("Intervals remaining: ", "Unknown"));

			targetPanel.setVisible(true);
			targetPanel.revalidate();
			targetPanel.repaint();
			
			// Update config controls to reflect current values even on error
			refreshConfigControls(config);
		}
	}
		/**
	 * Refreshes the config control values to match the current configuration
	 */	private void refreshConfigControls(TimeToMaxConfig config)
	{
		try {
			// Update all config controls to reflect current values
			try {
				LocalDate configDate = LocalDate.parse(config.targetDate());
				Date date = Date.from(configDate.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant());
				targetDateSpinner.setValue(date);
			} catch (Exception e) {
				// Default to today's date if parsing fails
				targetDateSpinner.setValue(new Date());
			}
			
			trackingIntervalCombo.setSelectedItem(config.trackingInterval());
			maxSkillModeCombo.setSelectedItem(config.maxSkillMode());
		} catch (Exception e) {
			// If there are any config errors (like invalid enum values), reset to defaults
			log.debug("Error refreshing config controls, resetting to defaults: {}", e.getMessage());
			
			// Reset enum configs to their default values
			try {
				configManager.setConfiguration("timeToMax", "trackingInterval", TrackingInterval.DAY.name());
				configManager.setConfiguration("timeToMax", "maxSkillMode", MaxSkillMode.NORMAL.name());
			} catch (Exception resetError) {
				log.debug("Failed to reset config to defaults: {}", resetError.getMessage());
			}
			
			// Set UI to default values
			targetDateSpinner.setValue(new Date());
			trackingIntervalCombo.setSelectedItem(TrackingInterval.DAY);
			maxSkillModeCombo.setSelectedItem(MaxSkillMode.NORMAL);
		}
	}
}
