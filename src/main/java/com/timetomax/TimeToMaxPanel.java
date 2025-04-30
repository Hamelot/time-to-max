package com.timetomax;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;

@Slf4j
class TimeToMaxPanel extends PluginPanel
{
	private final JPanel skillsContainer = new JPanel();
	private final JScrollPane scrollPane = new JScrollPane();
	private final PluginErrorPanel errorPanel = new PluginErrorPanel();
	private final Map<Skill, SkillTimeToMaxPanel> skillPanels = new EnumMap<>(Skill.class);
	private final JLabel intervalsRemainingLabel = new JLabel();
	private final JComboBox<TrackingInterval> intervalComboBox = new JComboBox<>(TrackingInterval.values());
	private final JPanel settingsPanel = new JPanel();
	private final JTextField targetDateField = new JTextField(10);
	private final Client client;
	private final TimeToMaxConfig config;
	private final ConfigManager configManager;
	private final SkillIconManager skillIconManager;
	private final TimeToMaxPlugin plugin;
	private SkillsTracker skillsTracker;
	private boolean isTrackerInitialized = false;

	@Inject
	TimeToMaxPanel(
		Client client,
		TimeToMaxConfig config,
		ConfigManager configManager,
		SkillIconManager skillIconManager,
		TimeToMaxPlugin plugin)
	{
		super(false);

		this.client = client;
		this.config = config;
		this.configManager = configManager;
		this.skillIconManager = skillIconManager;
		this.plugin = plugin;

		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		// Create layout
		final JPanel layoutPanel = new JPanel();
		layoutPanel.setLayout(new BorderLayout(0, 0));
		layoutPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(layoutPanel, BorderLayout.NORTH);

		// Configure reset button
		JButton resetButton = new JButton("Reset XP");
		resetButton.setToolTipText("Reset the XP baseline to current values");
		resetButton.setFocusPainted(false);
		resetButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		resetButton.setForeground(Color.WHITE);
		resetButton.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
		resetButton.addActionListener(e -> resetSkills());

		// Create header panel with title and intervals info
		JPanel headerPanel = new JPanel();
		headerPanel.setLayout(new BorderLayout());
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

		// Title area
		JLabel titleLabel = new JLabel("Time to Max");
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 14));

		// Intervals line
		intervalsRemainingLabel.setForeground(Color.WHITE);
		intervalsRemainingLabel.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));

		// Create a panel for title and intervals
		JPanel titlePanel = new JPanel();
		titlePanel.setLayout(new GridLayout(2, 1, 0, 2));
		titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		titlePanel.add(titleLabel);
		titlePanel.add(intervalsRemainingLabel);

		headerPanel.add(titlePanel, BorderLayout.CENTER);
		layoutPanel.add(headerPanel, BorderLayout.NORTH);

		// Create settings panel
		settingsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR.darker());
		settingsPanel.setBorder(new CompoundBorder(
			new MatteBorder(1, 0, 1, 0, ColorScheme.DARKER_GRAY_COLOR),
			new EmptyBorder(8, 8, 8, 8)
		));
		settingsPanel.setLayout(new GridBagLayout());

		// Initialize date field
		targetDateField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		targetDateField.setForeground(Color.WHITE);
		targetDateField.setToolTipText("Target date to max (YYYY-MM-DD)");
		targetDateField.setBorder(new LineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		targetDateField.setCaretColor(Color.WHITE);

		// Initialize apply button
		JButton applyButton = new JButton("Apply");
		applyButton.setFocusPainted(false);
		applyButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		applyButton.setForeground(Color.WHITE);
		applyButton.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
		applyButton.addActionListener(e -> applyConfig());

		// Initialize interval combo box
		intervalComboBox.setFocusable(false);
		intervalComboBox.setForeground(Color.WHITE);
		intervalComboBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
			{
				Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (c instanceof JLabel && value instanceof TrackingInterval)
				{
					JLabel label = (JLabel) c;
					label.setText(value.toString());
					if (!isSelected)
					{
						label.setForeground(Color.WHITE);
					}
				}
				return c;
			}
		});
		intervalComboBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		intervalComboBox.setToolTipText("Select the interval for XP tracking");

		// Set up grid bag constraints
		GridBagConstraints gridBagConstraints = new GridBagConstraints();
		gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
		gridBagConstraints.insets = new Insets(2, 2, 2, 2);

		// Date field row
		JLabel dateLabel = new JLabel("Date:");
		dateLabel.setForeground(Color.WHITE);
		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 0;
		gridBagConstraints.gridwidth = 1;
		gridBagConstraints.weightx = 0.25;
		settingsPanel.add(dateLabel, gridBagConstraints);

		gridBagConstraints.gridx = 1;
		gridBagConstraints.gridy = 0;
		gridBagConstraints.gridwidth = 1;
		gridBagConstraints.weightx = 0.75;
		settingsPanel.add(targetDateField, gridBagConstraints);

		// Interval row
		JLabel intervalLabel = new JLabel("Interval:");
		intervalLabel.setForeground(Color.WHITE);
		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 1;
		gridBagConstraints.gridwidth = 1;
		gridBagConstraints.weightx = 0.25;
		settingsPanel.add(intervalLabel, gridBagConstraints);

		gridBagConstraints.gridx = 1;
		gridBagConstraints.gridy = 1;
		gridBagConstraints.gridwidth = 1;
		gridBagConstraints.weightx = 0.75;
		settingsPanel.add(intervalComboBox, gridBagConstraints);

		// Button row
		JPanel buttonRow = new JPanel(new GridLayout(1, 2, 5, 0));
		buttonRow.setBackground(ColorScheme.DARK_GRAY_COLOR.darker());

		buttonRow.add(resetButton);
		buttonRow.add(applyButton);

		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 2;
		gridBagConstraints.gridwidth = 2;
		gridBagConstraints.weightx = 1.0;
		gridBagConstraints.insets = new Insets(5, 2, 2, 2);
		settingsPanel.add(buttonRow, gridBagConstraints);

		layoutPanel.add(settingsPanel, BorderLayout.CENTER);

		// Create skills container
		skillsContainer.setLayout(new BoxLayout(skillsContainer, BoxLayout.Y_AXIS));
		skillsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Set up scroll pane for skills container
		scrollPane.setViewportView(skillsContainer);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16); // Smoother scrolling

		// Create error panel
		errorPanel.setContent("", "Log in to view your progress towards max level.");

		// Initialize UI with current config
		loadConfigFromSettings();
		showNoData();
	}

	private void loadConfigFromSettings()
	{
		try
		{
			intervalComboBox.setSelectedItem(config.trackingInterval());
			targetDateField.setText(config.targetDate());
		}
		catch (Exception e)
		{
			log.warn("Error loading config settings", e);
		}
	}

	private void applyConfig()
	{
		try
		{
			// Validate date format
			String dateText = targetDateField.getText();
			LocalDate.parse(dateText); // This will throw an exception if the format is invalid

			// Update config
			TrackingInterval interval = (TrackingInterval) intervalComboBox.getSelectedItem();
			configManager.setConfiguration("timetomax", "trackingInterval", interval);
			configManager.setConfiguration("timetomax", "targetDate", dateText);

			// Update panel
			updateAllInfo();

			// Show confirmation
			JOptionPane.showMessageDialog(this,
				"Settings updated successfully!",
				"Time to Max", JOptionPane.INFORMATION_MESSAGE);

		}
		catch (DateTimeParseException e)
		{
			JOptionPane.showMessageDialog(this,
				"Invalid date format. Please use YYYY-MM-DD format.",
				"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	// Method for plugin to set the tracker when it's initialized
	public void setSkillsTracker(SkillsTracker skillsTracker)
	{
		this.skillsTracker = skillsTracker;
		this.isTrackerInitialized = (skillsTracker != null);

		// Immediately update the UI if tracker is set
		if (isTrackerInitialized && client.getGameState().getState() >= 30)
		{
			SwingUtilities.invokeLater(this::updateAllInfo);
		}
	}

	void showNoData()
	{
		settingsPanel.setVisible(false);
		scrollPane.setVisible(false);
		remove(scrollPane);
		add(errorPanel, BorderLayout.CENTER);
		errorPanel.setVisible(true);
		revalidate();
	}

	void showTrackerData()
	{
		errorPanel.setVisible(false);
		remove(errorPanel);
		add(scrollPane, BorderLayout.CENTER);
		scrollPane.setVisible(true);
		settingsPanel.setVisible(true);
		revalidate();
	}

	void resetSkills()
	{
		// Ask for confirmation before resetting
		int confirm = JOptionPane.showConfirmDialog(
			this,
			"Are you sure you want to reset your XP baseline?\n" +
				"All XP gains will be measured from the current values.",
			"Confirm Reset",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		);

		if (confirm == JOptionPane.YES_OPTION)
		{
			// Call the plugin's resetXpBaseline method
			if (client.getGameState().getState() >= 30)
			{
				plugin.resetXpBaseline();
				JOptionPane.showMessageDialog(this,
					"XP Baseline has been reset.\nAll XP gains will be measured from now.",
					"Time to Max", JOptionPane.INFORMATION_MESSAGE);
			}
		}
	}

	void updateAllInfo()
	{
		if (client.getGameState().getState() < 30 || !isTrackerInitialized)
		{
			showNoData();
			return;
		}

		// Make sure the tracker UI is shown
		if (!scrollPane.isVisible())
		{
			showTrackerData();
		}

		// Update header info
		updateHeaderInfo();

		// Update all skill panels
		rebuildSkillPanels();
	}

	private void updateHeaderInfo()
	{
		// Get target date
		LocalDate targetDate;
		try
		{
			targetDate = LocalDate.parse(config.targetDate());
		}
		catch (DateTimeParseException e)
		{
			targetDate = LocalDate.now().plusMonths(6);
		}

		// Calculate intervals remaining
		TrackingInterval interval = config.trackingInterval();
		long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), targetDate);

		long intervalsRemaining;
		switch (interval)
		{
			case WEEK:
				intervalsRemaining = (daysRemaining + 6) / 7; // Round up to nearest week
				break;
			case MONTH:
				intervalsRemaining = (daysRemaining + 29) / 30; // Approximate months
				break;
			default:
				intervalsRemaining = daysRemaining;
				break;
		}

		String intervalText = intervalsRemaining == 1 ? interval.toString() : interval + "s";
		intervalsRemainingLabel.setText(intervalsRemaining + " " + intervalText + " remaining");
	}

	private void rebuildSkillPanels()
	{
		// Clear existing skill panels
		skillPanels.clear();
		skillsContainer.removeAll();

		// Get target date
		LocalDate targetDate;
		try
		{
			targetDate = LocalDate.parse(config.targetDate());
		}
		catch (DateTimeParseException e)
		{
			targetDate = LocalDate.now().plusMonths(6);
		}

		// Get all skills that aren't maxed yet
		List<Skill> unmaxedSkills = new ArrayList<>();
		for (Skill skill : Skill.values())
		{

			int currentXp = client.getSkillExperience(skill);
			if (currentXp < XpCalculator.MAX_XP)
			{
				unmaxedSkills.add(skill);
			}
		}

		// If no skills left to max, show the congratulations message
		if (unmaxedSkills.isEmpty())
		{
			JPanel congratsPanel = getCongratsJPanel();
			skillsContainer.add(congratsPanel);

			// Add bottom padding
			JPanel bottomPadding = new JPanel();
			bottomPadding.setBackground(ColorScheme.DARK_GRAY_COLOR);
			skillsContainer.add(bottomPadding);

			// Update the UI
			skillsContainer.revalidate();
			skillsContainer.repaint();
			return;
		}

		// Sort skills into two categories: active and completed
		List<Skill> activeSkills = new ArrayList<>();
		List<Skill> completedSkills = new ArrayList<>();

		for (Skill skill : unmaxedSkills)
		{
			int xpGained = skillsTracker.getSessionXpGained(skill);
			// Use the baseline XP value for calculating required XP, not current XP
			int baselineXp = skillsTracker.getBaselineXp(skill);
			int requiredXp = XpCalculator.getRequiredXpPerInterval(baselineXp, targetDate, config.trackingInterval());

			if (xpGained >= requiredXp && requiredXp > 0)
			{
				completedSkills.add(skill);
			}
			else
			{
				activeSkills.add(skill);
			}
		}

		// Sort active skills by XP gained (descending)
		activeSkills.sort((s1, s2) -> {
			int xp1 = skillsTracker.getSessionXpGained(s1);
			int xp2 = skillsTracker.getSessionXpGained(s2);
			return Integer.compare(xp2, xp1);
		});

		// Sort completed skills by XP gained (descending)
		completedSkills.sort((s1, s2) -> {
			int xp1 = skillsTracker.getSessionXpGained(s1);
			int xp2 = skillsTracker.getSessionXpGained(s2);
			return Integer.compare(xp2, xp1);
		});

		// Add active skill panels first
		for (Skill skill : activeSkills)
		{
			SkillTimeToMaxPanel skillPanel = new SkillTimeToMaxPanel(
				skill,
				client,
				skillIconManager,
				config,
				skillsTracker
			);
			skillPanels.put(skill, skillPanel);
			skillsContainer.add(skillPanel);
		}

		// Add a separator if we have both active and completed skills
		if (!activeSkills.isEmpty() && !completedSkills.isEmpty())
		{
			JPanel separator = new JPanel();
			separator.setBackground(ColorScheme.DARK_GRAY_COLOR);
			separator.setLayout(new BorderLayout());
			separator.setBorder(new EmptyBorder(5, 0, 5, 0));

			JLabel completedLabel = new JLabel("Completed Skills", SwingConstants.CENTER);
			completedLabel.setForeground(Color.WHITE);
			completedLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
			separator.add(completedLabel, BorderLayout.CENTER);

			skillsContainer.add(separator);
		}

		// Add completed skill panels at the bottom
		for (Skill skill : completedSkills)
		{
			SkillTimeToMaxPanel skillPanel = new SkillTimeToMaxPanel(
				skill,
				client,
				skillIconManager,
				config,
				skillsTracker
			);
			skillPanels.put(skill, skillPanel);
			skillsContainer.add(skillPanel);
		}

		// Add bottom padding
		JPanel bottomPadding = new JPanel();
		bottomPadding.setBackground(ColorScheme.DARK_GRAY_COLOR);
		skillsContainer.add(bottomPadding);

		// Update the UI
		skillsContainer.revalidate();
		skillsContainer.repaint();
	}

	private static JPanel getCongratsJPanel()
	{
		JPanel congratsPanel = new JPanel();
		congratsPanel.setLayout(new BorderLayout());
		congratsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		congratsPanel.setBorder(new EmptyBorder(20, 10, 20, 10));

		JLabel congratsLabel = new JLabel("<html><center>Congratulations, you can finally play the game!</center></html>", SwingConstants.CENTER);
		congratsLabel.setForeground(Color.WHITE);
		congratsLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

		congratsPanel.add(congratsLabel, BorderLayout.CENTER);
		return congratsPanel;
	}
}