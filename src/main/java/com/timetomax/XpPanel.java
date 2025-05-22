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
	private final Client client;
	private final Map<Skill, XpInfoBox> infoBoxes = new HashMap<>();
	private final JLabel overallExpGained = new JLabel(XpInfoBox.htmlLabel("Gained: ", 0));
	private final JLabel overallExpHour = new JLabel(XpInfoBox.htmlLabel("Per hour: ", 0));

	private final JPanel overallPanel = new JPanel();

	// New panel for target XP information
	private final JPanel targetPanel = new JPanel();
	private final JLabel targetDateLabel = new JLabel(XpInfoBox.htmlLabel("Target Date: ", ""));
	private final JLabel targetIntervalLabel = new JLabel(XpInfoBox.htmlLabel("Tracking: ", ""));

	/* This displays the "track xp" text */
	private final PluginErrorPanel errorPanel = new PluginErrorPanel();

	XpPanel(TimeToMaxPlugin timeToMaxPlugin, TimeToMaxConfig timeToMaxConfig, Client client, SkillIconManager iconManager)
	{
		super();
		this.client = client;

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

		// Create reset all menu
		final JMenuItem reset = new JMenuItem("Reset All");
		reset.addActionListener(e -> timeToMaxPlugin.resetAndInitState());

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
		popupMenu.add(reset);
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
		targetPanel.setLayout(new GridLayout(3, 1));
		targetPanel.setVisible(true); // Make target panel visible by default

		targetDateLabel.setFont(FontManager.getRunescapeSmallFont());
		targetIntervalLabel.setFont(FontManager.getRunescapeSmallFont());

		targetPanel.add(targetDateLabel);
		targetPanel.add(targetIntervalLabel);

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
		layoutPanel.add(overallPanel);
		layoutPanel.add(infoBoxPanel);

		for (Skill skill : Skill.values())
		{
			infoBoxes.put(skill, new XpInfoBox(timeToMaxPlugin, timeToMaxConfig, infoBoxPanel, skill, iconManager));
		}

		errorPanel.setContent("Time To Max", "Log in and view and track the minimum xp required to meet your maxing goal.");
		add(errorPanel);
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

			targetDateLabel.setText(XpInfoBox.htmlLabel("Target Date: ", targetDate.toString()));
			targetIntervalLabel.setText(XpInfoBox.htmlLabel("Tracking: ", "Per " + interval.toString().toLowerCase()));

			targetPanel.setVisible(true);
			targetPanel.revalidate();
			targetPanel.repaint();
		}
		catch (DateTimeParseException e)
		{
			targetDateLabel.setText(XpInfoBox.htmlLabel("Target Date: ", "Invalid date format"));
			targetIntervalLabel.setText(XpInfoBox.htmlLabel("Tracking: ", config.trackingInterval().toString()));

			targetPanel.setVisible(true);
			targetPanel.revalidate();
			targetPanel.repaint();
		}
	}
}
