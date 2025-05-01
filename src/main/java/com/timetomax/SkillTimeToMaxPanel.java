package com.timetomax;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Slf4j
class SkillTimeToMaxPanel extends JPanel
{
	private static final Border PANEL_BORDER = BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARKER_GRAY_COLOR);
	private static final Color COMPLETED_COLOR = new Color(0, 182, 91);
	private static final Color UNCOMPLETED_COLOR = new Color(250, 74, 75);

	private final JProgressBar progressBar = new JProgressBar();
	private final JLabel xpGained = new JLabel();
	private final JLabel xpRemaining = new JLabel();
	private final Skill skill;
	private final Client client;
	private final TimeToMaxConfig config;
	private final SkillsTracker skillsTracker;

	SkillTimeToMaxPanel(
		Skill skill,
		Client client,
		SkillIconManager iconManager,
		TimeToMaxConfig config,
		SkillsTracker skillsTracker)
	{
		this.skill = skill;
		this.client = client;
		this.config = config;
		this.skillsTracker = skillsTracker;

		setBorder(new CompoundBorder(PANEL_BORDER, new EmptyBorder(5, 0, 5, 0)));
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Add skill icon
		final BufferedImage skillImg = iconManager.getSkillImage(skill);
		final JLabel icon = new JLabel(new ImageIcon(skillImg));
		icon.setBorder(new EmptyBorder(0, 5, 0, 0));
		add(icon, BorderLayout.WEST);

		// Create panel for skill information
		final JPanel skillPanel = new JPanel();
		skillPanel.setBorder(new EmptyBorder(0, 5, 0, 5));
		skillPanel.setLayout(new GridLayout(3, 1));
		skillPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Skill name
		JLabel skillName = new JLabel();
		skillName.setText(skill.getName());
		skillName.setForeground(Color.WHITE);
		skillName.setFont(FontManager.getRunescapeBoldFont());

		// Add labels to panel
		skillPanel.add(skillName);
		skillPanel.add(xpGained);
		skillPanel.add(xpRemaining);

		// Add progress bar
		progressBar.setStringPainted(true);
		progressBar.setForeground(ColorScheme.BRAND_ORANGE);
		progressBar.setPreferredSize(new Dimension(100, 16));
		progressBar.setMinimumSize(new Dimension(100, 16));

		JPanel progressWrapper = new JPanel();
		progressWrapper.setLayout(new BorderLayout());
		progressWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		progressWrapper.setBorder(new EmptyBorder(0, 5, 5, 5));
		progressWrapper.add(progressBar, BorderLayout.CENTER);

		// Add components to main panel
		add(skillPanel, BorderLayout.CENTER);
		add(progressWrapper, BorderLayout.SOUTH);

		// Initialize with skill data
		updateSkillData();
	}

	private void updateSkillData()
	{
		// Current XP in the skill
		int currentXp = client.getSkillExperience(skill);

		// Get baseline XP (the XP at the start of the tracking period)
		int baselineXp = skillsTracker.getBaselineXp(skill);

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

		// Get session XP gain
		int xpGainedSession = skillsTracker.getSessionXpGained(skill);

		// Get XP needed for this interval based on BASELINE XP, not current XP
		// This ensures the target doesn't change as you gain XP
		TrackingInterval interval = config.trackingInterval();
		int requiredXp = XpCalculator.getRequiredXpPerInterval(baselineXp, targetDate, interval);
		int xpToMax = XpCalculator.MAX_XP - currentXp;

		// Set label values
		xpGained.setText(String.format("%,d/%,d XP gained", xpGainedSession, requiredXp));

		// Set goal completion message
		if (xpGainedSession >= requiredXp && requiredXp > 0)
		{
			xpRemaining.setText("Complete!");
			xpRemaining.setForeground(COMPLETED_COLOR);
		}
		else
		{
			xpRemaining.setText(String.format("%,d XP remaining", Math.max(0, requiredXp - xpGainedSession)));
			xpRemaining.setForeground(UNCOMPLETED_COLOR);
		}

		// Update progress bar
		int progressPercent = 0;
		if (requiredXp > 0)
		{
			progressPercent = (int) Math.min(100, (xpGainedSession * 100.0) / requiredXp);
		}

		progressBar.setValue(progressPercent);
		progressBar.setString(String.format("%d%%", progressPercent));

		// Colorize the progress bar and text based on completion
		if (progressPercent >= 100)
		{
			progressBar.setForeground(COMPLETED_COLOR);
		}
		else
		{
			progressBar.setForeground(ColorScheme.BRAND_ORANGE);
		}
	}
}