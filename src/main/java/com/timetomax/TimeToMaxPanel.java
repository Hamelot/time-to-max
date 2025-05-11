package com.timetomax;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import java.util.concurrent.ScheduledExecutorService;
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
	private final ScheduledExecutorService executor;
	private SkillsTracker skillsTracker;
	private boolean isTrackerInitialized = false;
	private Skill lastUpdatedSkill = null;

	@Inject
	TimeToMaxPanel(
		Client client,
		TimeToMaxConfig config,
		ConfigManager configManager,
		SkillIconManager skillIconManager,
		TimeToMaxPlugin plugin,
		ScheduledExecutorService executor)
	{
		super(false);

		this.client = client;
		this.config = config;
		this.configManager = configManager;
		this.skillIconManager = skillIconManager;
		this.plugin = plugin;
		this.executor = executor;

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
		// Create error panel with more detailed guidance
		errorPanel.setContent("Time to Max",
			"<html><body style='width: 220px'>" +
				"<p>Please log in to view your skill progress.</p>" +
				"<p>Once logged in, your XP progress toward max level will be tracked automatically.</p>" +
				"</body></html>");

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
		this.lastUpdatedSkill = null;

		// Immediately update the UI if tracker is set
		if (isTrackerInitialized && client.getGameState().getState() >= 30)
		{
			// Force a snapshot refresh to ensure we have the most up-to-date data
			skillsTracker.forceSnapshotRefresh();

			// Ensure a complete rebuild of the panel to properly sort skills into categories
			SwingUtilities.invokeLater(this::updateAllInfo);
		}
	}

	void showNoData()
	{
		scrollPane.setVisible(false);
		remove(scrollPane);

		// Reset our tracking of the most recently updated skill
		lastUpdatedSkill = null;

		// Update the error panel with an informative message
		errorPanel.setContent("Time to Max",
			"<html><body style='word-break: break-all'>" +
				"<p>Please log in to view your skill progress.</p><br/>" +
				"<p>Once logged in, your XP progress toward max will be tracked automatically.</p>" +
				"</body></html>");

		add(errorPanel, BorderLayout.CENTER);
		errorPanel.setVisible(true);

		// Make the settings panel visible even before login so users can configure it
		settingsPanel.setVisible(true);

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
		{            // Call the plugin's resetXpBaseline method
			if (client.getGameState().getState() >= 30)
			{
				plugin.resetXpBaseline();
				// Reset our tracking of the last updated skill
				lastUpdatedSkill = null;
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
		LocalDate targetDate = getTargetDate();

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

	/**
	 * Helper method to get the target date from config or default to 6 months
	 *
	 * @return The target date
	 */
	private LocalDate getTargetDate()
	{
		try
		{
			return LocalDate.parse(config.targetDate());
		}
		catch (DateTimeParseException e)
		{
			return LocalDate.now().plusMonths(6);
		}
	}

	private void rebuildSkillPanels()
	{
		// Run intensive data gathering operations in a background thread using the shared executor
		executor.submit(() -> {
			try
			{
				// Get target date - making it effectively final for use in lambda expressions
				final LocalDate targetDate = getTargetDate();

				// Get all skills that aren't maxed yet
				java.util.List<Skill> unmaxedSkills = new ArrayList<>();
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
					javax.swing.SwingUtilities.invokeLater(() -> {
						skillPanels.clear();
						skillsContainer.removeAll();

						JPanel congratsPanel = getCongratsJPanel();
						skillsContainer.add(congratsPanel);

						// Add bottom padding
						JPanel bottomPadding = new JPanel();
						bottomPadding.setBackground(ColorScheme.DARK_GRAY_COLOR);
						skillsContainer.add(bottomPadding);

						// Update the UI
						skillsContainer.revalidate();
						skillsContainer.repaint();
					});
					return;
				}

				// Sort skills into two categories: active and completed
				java.util.List<Skill> activeSkills = new ArrayList<>();
				java.util.List<Skill> completedSkills = new ArrayList<>();

				// Create a map to collect XP gain data to avoid multiple calls to getSessionXpGained
				java.util.Map<Skill, Integer> skillXpGainMap = new java.util.HashMap<>();

				// Create a map to collect baseline XP data
				java.util.Map<Skill, Integer> baselineXpMap = new java.util.HashMap<>();

				// Collect all XP data in a batch
				for (Skill skill : unmaxedSkills)
				{
					int xpGained = skillsTracker.getSessionXpGained(skill);
					skillXpGainMap.put(skill, xpGained);

					int baselineXp = skillsTracker.getBaselineXp(skill);
					baselineXpMap.put(skill, baselineXp);

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

				// Get the most recently active skill to put at the top
				Skill mostRecentSkill = null;
				int highestTimeout = -1;

				// Get active skills from the plugin to determine the most recently active one
				if (plugin != null)
				{
					// First try to get the most recent skill data from the plugin
					// Use reflection to access the private activeSkills map
					try
					{
						java.lang.reflect.Field activeSkillsField = TimeToMaxPlugin.class.getDeclaredField("activeSkills");
						activeSkillsField.setAccessible(true);
						Object activeSkillsObj = activeSkillsField.get(plugin);
						Map<Skill, Integer> pluginActiveSkills = new HashMap<>();

						// Type check and safe casting
						if (activeSkillsObj instanceof Map)
						{
							Map<?, ?> rawMap = (Map<?, ?>) activeSkillsObj;

							// Safely convert the map with proper type checking
							for (Map.Entry<?, ?> entry : rawMap.entrySet())
							{
								if (entry.getKey() instanceof Skill && entry.getValue() instanceof Integer)
								{
									pluginActiveSkills.put((Skill) entry.getKey(), (Integer) entry.getValue());
								}
							}
						}

						// Find the skill with the highest timeout (most recently active)
						for (Skill skill : activeSkills)
						{
							Integer timeout = pluginActiveSkills.get(skill);
							if (timeout != null && timeout > highestTimeout)
							{
								highestTimeout = timeout;
								mostRecentSkill = skill;
							}
						}
					}
					catch (Exception e)
					{
						log.debug("Could not access activeSkills in plugin: {}", e.getMessage());

						// Fallback: try to find the skill with the most recent XP gain
						int highestXpGain = -1;
						for (Skill skill : activeSkills)
						{
							int xpGain = skillXpGainMap.getOrDefault(skill, 0);
							if (xpGain > highestXpGain)
							{
								highestXpGain = xpGain;
								mostRecentSkill = skill;
							}
						}
						log.debug("Falling back to highest XP gain as the most recent skill");
					}
				}                // If lastUpdatedSkill is set and active, use it as the most recent skill
				if (lastUpdatedSkill != null && activeSkills.contains(lastUpdatedSkill))
				{
					mostRecentSkill = lastUpdatedSkill;
				}

				// Sort active skills by percentage completion (descending), with most recent skill at the top
				final Skill finalMostRecentSkill = mostRecentSkill;
				activeSkills.sort((s1, s2) -> {
					// If s1 is the most recent skill, it goes first
					if (s1.equals(finalMostRecentSkill))
					{
						return -1;
					}
					// If s2 is the most recent skill, it goes first
					if (s2.equals(finalMostRecentSkill))
					{
						return 1;
					}

					// Get baseline XP and required XP for both skills
					int baseXp1 = baselineXpMap.getOrDefault(s1, 0);
					int baseXp2 = baselineXpMap.getOrDefault(s2, 0);

					int requiredXp1 = XpCalculator.getRequiredXpPerInterval(baseXp1, targetDate, config.trackingInterval());
					int requiredXp2 = XpCalculator.getRequiredXpPerInterval(baseXp2, targetDate, config.trackingInterval());

					// Calculate percentage completed (avoid division by zero)
					int xp1 = skillXpGainMap.getOrDefault(s1, 0);
					int xp2 = skillXpGainMap.getOrDefault(s2, 0);

					// Calculate percentages as integers to compare whole number percentages
					int percent1 = (requiredXp1 > 0) ? (int) ((xp1 * 100.0) / requiredXp1) : 0;
					int percent2 = (requiredXp2 > 0) ? (int) ((xp2 * 100.0) / requiredXp2) : 0;

					// If percentages are the same (as whole numbers), use raw XP as tiebreaker
					if (percent1 == percent2)
					{
						return Integer.compare(xp2, xp1); // Higher XP first
					}

					// Otherwise sort by percentage (higher percentage first)
					return Integer.compare(percent2, percent1);
				});

				// Sort completed skills by XP gained (descending)
				completedSkills.sort((s1, s2) -> {
					int xp1 = skillXpGainMap.getOrDefault(s1, 0);
					int xp2 = skillXpGainMap.getOrDefault(s2, 0);
					return Integer.compare(xp2, xp1);
				});

				// Final copies for use in the UI update lambda
				final java.util.List<Skill> finalActiveSkills = activeSkills;
				final java.util.List<Skill> finalCompletedSkills = completedSkills;

				// Update UI on the EDT
				javax.swing.SwingUtilities.invokeLater(() -> {
					// Clear existing skill panels
					skillPanels.clear();
					skillsContainer.removeAll();
					for (Skill skill : finalActiveSkills)
					{
						SkillTimeToMaxPanel skillPanel = new SkillTimeToMaxPanel(
							skill,
							client,
							skillIconManager,
							config,
							skillsTracker,
							executor
						);
						skillPanels.put(skill, skillPanel);
						skillsContainer.add(skillPanel);
					}

					// Add a separator if we have both active and completed skills
					if (!finalActiveSkills.isEmpty() && !finalCompletedSkills.isEmpty())
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
					for (Skill skill : finalCompletedSkills)
					{
						SkillTimeToMaxPanel skillPanel = new SkillTimeToMaxPanel(
							skill,
							client,
							skillIconManager,
							config,
							skillsTracker,
							executor
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
				});
			}
			catch (Exception e)
			{
				log.error("Error rebuilding skill panels", e);
			}
		});
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

	/**
	 * Updates a single skill panel, possibly rebuilding the entire UI if this is a different skill than last time
	 * This prevents flickering when rapidly gaining XP, but still allows reordering when a different skill gains XP
	 *
	 * @param skill The skill to update
	 */
	void updateSkillPanel(Skill skill)
	{
		if (client.getGameState().getState() < 30 || !isTrackerInitialized || skillsTracker == null)
		{
			return;
		}

		// Check if this is a different skill than the last updated one
		boolean newSkillUpdated = (lastUpdatedSkill == null || !skill.equals(lastUpdatedSkill));

		// If this is the first update or a different skill than before, rebuild everything
		// to ensure correct ordering with the most recent skill at the top
		if (newSkillUpdated)
		{
			lastUpdatedSkill = skill;
			// Use invokeLater to ensure we're on the EDT and to avoid potential threading issues
			SwingUtilities.invokeLater(this::updateAllInfo);
			return;
		}

		// If it's the same skill as before, just update that panel without rebuilding
		SkillTimeToMaxPanel panel = skillPanels.get(skill);
		if (panel != null)
		{
			panel.updateData();
		}
	}

	/**
	 * Shows a loading message while data is being initialized
	 */
	void showLoading()
	{
		settingsPanel.setVisible(false);
		scrollPane.setVisible(false);
		remove(scrollPane);

		// Update the error panel to show a loading message with better guidance
		errorPanel.setContent("Time to Max",
			"<html><body style='word-break: break-all'>" +
				"<p>Please log in to view your skill progress.</p><br>/" +
				"<p>Once logged in, your XP progress toward max will be tracked automatically.</p>" +
				"</body></html>");

		add(errorPanel, BorderLayout.CENTER);
		errorPanel.setVisible(true);

		// Make the settings panel visible even before login so users can configure it
		settingsPanel.setVisible(true);

		revalidate();
	}

	/**
	 * Gets the skill panels map for cleanup purposes
	 *
	 * @return Map of skill panels
	 */
	public Map<Skill, SkillTimeToMaxPanel> getSkillPanels()
	{
		return skillPanels;
	}
}