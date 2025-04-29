package com.timetomax;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Experience;
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
    private final JLabel xpHour = new JLabel();
    private final JLabel xpLeft = new JLabel();
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
        skillPanel.setLayout(new GridLayout(4, 1));
        skillPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Skill name
        JLabel skillName = new JLabel();
        skillName.setText(skill.getName());
        skillName.setForeground(Color.WHITE);
        skillName.setFont(FontManager.getRunescapeBoldFont());
        
        // Add labels to panel
        skillPanel.add(skillName);
        skillPanel.add(xpGained);
        skillPanel.add(xpLeft);
        skillPanel.add(xpHour);
        
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
        
        // Get target date
        LocalDate targetDate;
        try {
            targetDate = LocalDate.parse(config.targetDate());
        } catch (DateTimeParseException e) {
            targetDate = LocalDate.now().plusMonths(6);
        }
        
        // Get session XP gain
        int xpGainedSession = skillsTracker.getSessionXpGained(skill);
        
        // Get XP needed for this interval 
        TrackingInterval interval = config.trackingInterval();
        int requiredXp = XpCalculator.getRequiredXpPerInterval(currentXp, targetDate, interval);
        
        // Current level and XP to max
        int currentLevel = Experience.getLevelForXp(currentXp);
        int xpToMax = XpCalculator.MAX_XP - currentXp;
        
        // Set label values
        xpGained.setText(String.format("%,d/%,d XP gained", xpGainedSession, requiredXp));
        
        if (xpToMax <= 0) {
            xpLeft.setText("Level 99");
            xpLeft.setForeground(COMPLETED_COLOR);
        } else {
            // Show remaining XP for target instead of XP to next level
            int remainingXpForTarget = Math.max(0, requiredXp - xpGainedSession);
            xpLeft.setText(String.format("%,d XP remaining for %s", remainingXpForTarget, interval.toString().toLowerCase()));
            xpLeft.setForeground(Color.WHITE);
        }
        
        // Set goal completion message
        if (xpGainedSession >= requiredXp && requiredXp > 0) {
            xpHour.setText("Complete!");
            xpHour.setForeground(COMPLETED_COLOR);
        } else {
            xpHour.setText(String.format("Need %,d more XP", Math.max(0, requiredXp - xpGainedSession)));
            xpHour.setForeground(UNCOMPLETED_COLOR);
        }
        
        // Update progress bar
        int progressPercent = 0;
        if (requiredXp > 0) {
            progressPercent = (int) Math.min(100, (xpGainedSession * 100.0) / requiredXp);
        }
        
        progressBar.setValue(progressPercent);
        progressBar.setString(String.format("%d%%", progressPercent));
        
        // Colorize the progress bar and text based on completion
        if (progressPercent >= 100) {
            progressBar.setForeground(COMPLETED_COLOR);
        } else {
            progressBar.setForeground(ColorScheme.BRAND_ORANGE);
        }
    }
}