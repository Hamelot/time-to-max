/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.SkillColor;
import net.runelite.client.ui.components.MouseDragEventForwarder;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.QuantityFormatter;

class XpInfoBox extends JPanel
{
	static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");

	static
	{
		TWO_DECIMAL_FORMAT.setRoundingMode(RoundingMode.DOWN);
	}

	// Templates
	private static final String PROGRESS_BAR_TOOLTIP =
		"<html>%s Actions done<br/>"
			+ "%s Actions/hr<br/>"
			+ "%s %s%s</html>";
	private static final String PROGRESS_BAR_TOOLTIP_NO_ACTIONS =
		"<html>%s %s%s</html>";
	private static final String HTML_LABEL_TEMPLATE =
		"<html><body style='color:%s'>%s<span style='color:white'>%s</span></body></html>";

	private static final String REMOVE_STATE = "Remove from canvas";
	private static final String ADD_STATE = "Add to canvas";

	private static final EmptyBorder DEFAULT_PROGRESS_WRAPPER_BORDER = new EmptyBorder(0, 7, 7, 7);
	private static final EmptyBorder COMPACT_PROGRESS_WRAPPER_BORDER = new EmptyBorder(5, 1, 5, 5);

	// Instance members
	private final JComponent panel;

	@Getter(AccessLevel.PACKAGE)
	private final Skill skill;

	/* The tracker's wrapping container */
	private final JPanel container = new JPanel();

	/* Contains the skill icon and the stats panel */
	private final JPanel headerPanel = new JPanel();

	/* Contains all the skill information (exp gained, per hour, etc) */
	private final JPanel statsPanel = new JPanel();

	// Contains progress bar and compact-view icon
	private final JPanel progressWrapper = new JPanel();

	// Contains skill icon
	private final JLabel compactSkillIcon;

	private final ProgressBar progressBar = new ProgressBar();

	private final JLabel topLeftStat = new JLabel();
	private final JLabel bottomLeftStat = new JLabel();
	private final JLabel topRightStat = new JLabel();
	private final JLabel bottomRightStat = new JLabel();
	//private final JLabel targetXpStat = new JLabel();
	private final JMenuItem pauseSkill = new JMenuItem("Pause");
	private final JMenuItem canvasItem = new JMenuItem(ADD_STATE);

	private final TimeToMaxConfig timeToMaxConfig;

	private boolean paused = false;

	XpInfoBox(TimeToMaxPlugin timeToMaxPlugin, TimeToMaxConfig timeToMaxConfig, JComponent panel, Skill skill, SkillIconManager iconManager)
	{
		this.timeToMaxConfig = timeToMaxConfig;
		this.panel = panel;
		this.skill = skill;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(5, 0, 0, 0));

		container.setLayout(new BorderLayout());
		container.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Create reset per hour menu
		final JMenuItem resetPerHour = new JMenuItem("Reset/hr");
		resetPerHour.addActionListener(e -> timeToMaxPlugin.resetSkillPerHourState(skill));

		// Create reset others menu
		pauseSkill.addActionListener(e -> timeToMaxPlugin.pauseSkill(skill, !paused));

		// Create popup menu
		final JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setBorder(new EmptyBorder(5, 5, 5, 5));
		popupMenu.add(resetPerHour);
		popupMenu.add(pauseSkill);
		popupMenu.add(canvasItem);
		popupMenu.addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent popupMenuEvent)
			{
				canvasItem.setText(timeToMaxPlugin.hasOverlay(skill) ? REMOVE_STATE : ADD_STATE);
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent popupMenuEvent)
			{
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent popupMenuEvent)
			{
			}
		});

		canvasItem.addActionListener(e ->
		{
			if (canvasItem.getText().equals(REMOVE_STATE))
			{
				timeToMaxPlugin.removeOverlay(skill);
			}
			else
			{
				timeToMaxPlugin.addOverlay(skill);
			}
		});

		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerPanel.setLayout(new BorderLayout());
		statsPanel.setLayout(new DynamicGridLayout(2, 2));
		statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		statsPanel.setBorder(new EmptyBorder(9, 2, 9, 2));

		topLeftStat.setFont(FontManager.getRunescapeSmallFont());
		bottomLeftStat.setFont(FontManager.getRunescapeSmallFont());
		topRightStat.setFont(FontManager.getRunescapeSmallFont());
		bottomRightStat.setFont(FontManager.getRunescapeSmallFont());


		statsPanel.add(topLeftStat);     // top left
		statsPanel.add(topRightStat);    // top right
		statsPanel.add(bottomLeftStat);  // bottom left
		statsPanel.add(bottomRightStat); // bottom right

		JLabel headerSkillIcon = getSkillIcon(iconManager, skill, 35, 35, false);
		headerPanel.add(headerSkillIcon, BorderLayout.WEST);
		headerPanel.add(statsPanel, BorderLayout.CENTER);

		progressWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		progressWrapper.setLayout(new BorderLayout());
		progressWrapper.setBorder(DEFAULT_PROGRESS_WRAPPER_BORDER);

		progressBar.setMaximumValue(100);
		progressBar.setBackground(new Color(61, 56, 49));
		progressBar.setForeground(SkillColor.find(skill).getColor());
		progressBar.setDimmedText("Paused");
		progressBar.setFont(FontManager.getRunescapeSmallFont());
		progressBar.setLeftLabel("0%");
		progressBar.setRightLabel("100%");
		progressBar.setCenterLabel("0%");

		compactSkillIcon = getSkillIcon(iconManager, skill, 25, 16, true);
		compactSkillIcon.setVisible(false);

		progressWrapper.add(compactSkillIcon, BorderLayout.WEST);
		progressWrapper.add(progressBar, BorderLayout.CENTER);

		container.add(headerPanel, BorderLayout.NORTH);
		container.add(progressWrapper, BorderLayout.SOUTH);

		container.setComponentPopupMenu(popupMenu);
		progressBar.setComponentPopupMenu(popupMenu);

		// forward mouse drag events to parent panel for drag and drop reordering
		MouseDragEventForwarder mouseDragEventForwarder = new MouseDragEventForwarder(panel);
		container.addMouseListener(mouseDragEventForwarder);
		container.addMouseMotionListener(mouseDragEventForwarder);
		progressBar.addMouseListener(mouseDragEventForwarder);
		progressBar.addMouseMotionListener(mouseDragEventForwarder);

		// collapse/expand on mouse click
		final MouseAdapter clickToggleCompact = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					toggleCompactView();
				}
			}
		};
		container.addMouseListener(clickToggleCompact);
		progressBar.addMouseListener(clickToggleCompact);

		add(container, BorderLayout.NORTH);
	}

	void reset()
	{
		canvasItem.setText(ADD_STATE);
		setCompactView(false);
		panel.remove(this);
		panel.revalidate();
	}

	void update(boolean updated, boolean paused, XpSnapshotSingle xpSnapshotSingle)
	{
		SwingUtilities.invokeLater(() -> rebuildAsync(updated, paused, xpSnapshotSingle));
	}

	private void toggleCompactView()
	{
		final boolean isCompact = !headerPanel.isVisible();
		setCompactView(!isCompact);
	}

	private void setCompactView(final boolean compact)
	{
		progressWrapper.setBorder(compact ? COMPACT_PROGRESS_WRAPPER_BORDER : DEFAULT_PROGRESS_WRAPPER_BORDER);
		headerPanel.setVisible(!compact);
		compactSkillIcon.setVisible(compact);
	}

	private static JLabel getSkillIcon(SkillIconManager iconManager, Skill skill, int width, int height, boolean small)
	{
		JLabel skillIcon = new JLabel();

		skillIcon.setIcon(new ImageIcon(iconManager.getSkillImage(skill, small)));
		skillIcon.setPreferredSize(new Dimension(width, height));
		skillIcon.setHorizontalAlignment(SwingConstants.CENTER);
		skillIcon.setVerticalAlignment(SwingConstants.CENTER);

		return skillIcon;
	}

	private void rebuildAsync(boolean updated, boolean skillPaused, XpSnapshotSingle xpSnapshotSingle)
	{
		if (updated)
		{
			if (getParent() != panel)
			{
				panel.add(this);
				panel.revalidate();
			}

			if (timeToMaxConfig.prioritizeRecentXpSkills())
			{
				panel.setComponentZOrder(this, 0);
			}

			paused = skillPaused;
			
				// Get settings and XP values
			TrackingInterval interval = timeToMaxConfig.trackingInterval();
			LocalDate targetDate;
			try
			{
				targetDate = LocalDate.parse(timeToMaxConfig.targetDate());
			}
			catch (DateTimeParseException e)
			{
				targetDate = LocalDate.now().plusYears(1);
			}

			// Always use consistent XP values from the snapshot
			int goalStartXp = xpSnapshotSingle.getStartGoalXp();
			int xpGained = xpSnapshotSingle.getXpGainedInSession();
			int requiredXp = XpCalculator.getRequiredXpPerInterval(goalStartXp, targetDate, interval);
			int progressPercent = requiredXp > 0 ? Math.min(100, (int) ((double) xpGained / requiredXp * 100)) : 0;

			// Update progress bar
			progressBar.setValue(progressPercent);
			progressBar.setLeftLabel("");
			progressBar.setRightLabel(QuantityFormatter.quantityToRSDecimalStack(requiredXp, true));
			progressBar.setPositions(Collections.emptyList());

			// Set center label based on completion status
			if (progressPercent >= 100)
			{
				progressBar.setCenterLabel("Complete");
			}
			else
			{
				String progress = String.format("%d%%", progressPercent);
				progressBar.setCenterLabel(progress);
			}

			// Prepare tooltip text
			XpProgressBarLabel tooltipLabel = timeToMaxConfig.progressBarTooltipLabel();
			String targetProgressText = String.format(
				"<br/>%s/%s target XP for %s",
				QuantityFormatter.quantityToRSDecimalStack(xpGained, true),
				QuantityFormatter.quantityToRSDecimalStack(requiredXp, true),
				interval.toString().toLowerCase());

			// Set tooltip based on skill type
			if (isCombatSkill(skill))
			{
				progressBar.setToolTipText(String.format(
					PROGRESS_BAR_TOOLTIP_NO_ACTIONS,
					tooltipLabel.getValueFunc().apply(xpSnapshotSingle),
					tooltipLabel == XpProgressBarLabel.PERCENTAGE ? "of goal" : "till goal xp",
					targetProgressText));
			}
			else
			{
				progressBar.setToolTipText(String.format(
					PROGRESS_BAR_TOOLTIP,
					xpSnapshotSingle.getActionsInSession(),
					xpSnapshotSingle.getActionsPerHour(),
					tooltipLabel.getValueFunc().apply(xpSnapshotSingle),
					tooltipLabel == XpProgressBarLabel.PERCENTAGE ? "of goal" : "till goal xp",
					targetProgressText));
			}

			progressBar.setDimmed(skillPaused);

			// Update XP panel labels
			topLeftStat.setText(htmlLabel(timeToMaxConfig.xpPanelLabel1(), xpSnapshotSingle));

			// For the XP_LEFT label, show remaining XP needed for this interval
			if (timeToMaxConfig.xpPanelLabel2() == XpPanelLabel.XP_LEFT)
			{
				String key = XpPanelLabel.XP_LEFT.getKey() + ": ";
				int remainingXpForInterval = Math.max(0, requiredXp - xpGained);
				String value = QuantityFormatter.quantityToRSDecimalStack(remainingXpForInterval, true);
				topRightStat.setText(htmlLabel(key, value));
			}
			else
			{
				topRightStat.setText(htmlLabel(timeToMaxConfig.xpPanelLabel2(), xpSnapshotSingle));
			}

			// For ACTIONS_LEFT label, make sure it shows actions to reach the interval goal
			if (timeToMaxConfig.xpPanelLabel1() == XpPanelLabel.ACTIONS_LEFT ||
				timeToMaxConfig.xpPanelLabel2() == XpPanelLabel.ACTIONS_LEFT ||
				timeToMaxConfig.xpPanelLabel3() == XpPanelLabel.ACTIONS_LEFT ||
				timeToMaxConfig.xpPanelLabel4() == XpPanelLabel.ACTIONS_LEFT)
			{
				String key = XpPanelLabel.ACTIONS_LEFT.getKey() + ": ";
				// Use the actions remaining from the snapshot which is now calculated correctly
				String value = xpSnapshotSingle.getActionsRemainingToGoal() == Integer.MAX_VALUE 
					? "N/A" 
					: QuantityFormatter.quantityToRSDecimalStack(xpSnapshotSingle.getActionsRemainingToGoal(), true);
				
				// Update all labels configured to show Actions left
				if (timeToMaxConfig.xpPanelLabel1() == XpPanelLabel.ACTIONS_LEFT)
				{
					topLeftStat.setText(htmlLabel(key, value));
				}
				if (timeToMaxConfig.xpPanelLabel2() == XpPanelLabel.ACTIONS_LEFT)
				{
					topRightStat.setText(htmlLabel(key, value));
				}
				if (timeToMaxConfig.xpPanelLabel3() == XpPanelLabel.ACTIONS_LEFT)
				{
					bottomLeftStat.setText(htmlLabel(key, value));
				}
				if (timeToMaxConfig.xpPanelLabel4() == XpPanelLabel.ACTIONS_LEFT)
				{
					bottomRightStat.setText(htmlLabel(key, value));
				}
			}

			// For XP_GAINED label, consistently show XP from snapshot
			if (timeToMaxConfig.xpPanelLabel1() == XpPanelLabel.XP_GAINED ||
				timeToMaxConfig.xpPanelLabel2() == XpPanelLabel.XP_GAINED ||
				timeToMaxConfig.xpPanelLabel3() == XpPanelLabel.XP_GAINED ||
				timeToMaxConfig.xpPanelLabel4() == XpPanelLabel.XP_GAINED)
			{
				String key = XpPanelLabel.XP_GAINED.getKey() + ": ";
				String value = QuantityFormatter.quantityToRSDecimalStack(xpGained, true);

				// Update all labels configured to show XP gained
				if (timeToMaxConfig.xpPanelLabel1() == XpPanelLabel.XP_GAINED)
				{
					topLeftStat.setText(htmlLabel(key, value));
				}
				if (timeToMaxConfig.xpPanelLabel2() == XpPanelLabel.XP_GAINED)
				{
					topRightStat.setText(htmlLabel(key, value));
				}
				if (timeToMaxConfig.xpPanelLabel3() == XpPanelLabel.XP_GAINED)
				{
					bottomLeftStat.setText(htmlLabel(key, value));
				}
				else
				{
					bottomLeftStat.setText(htmlLabel(timeToMaxConfig.xpPanelLabel3(), xpSnapshotSingle));
				}
				if (timeToMaxConfig.xpPanelLabel4() == XpPanelLabel.XP_GAINED)
				{
					bottomRightStat.setText(htmlLabel(key, value));
				}
				else
				{
					bottomRightStat.setText(htmlLabel(timeToMaxConfig.xpPanelLabel4(), xpSnapshotSingle));
				}
			}
			else
			{
				// Default behavior for other label types
				topLeftStat.setText(htmlLabel(timeToMaxConfig.xpPanelLabel1(), xpSnapshotSingle));
				topRightStat.setText(htmlLabel(timeToMaxConfig.xpPanelLabel2(), xpSnapshotSingle));
				bottomLeftStat.setText(htmlLabel(timeToMaxConfig.xpPanelLabel3(), xpSnapshotSingle));
				bottomRightStat.setText(htmlLabel(timeToMaxConfig.xpPanelLabel4(), xpSnapshotSingle));
			}
		}
		// Handle paused state changes
		else if (!paused && skillPaused)
		{
			progressBar.setDimmed(true);
			paused = true;
			pauseSkill.setText("Unpause");
		}
		else if (paused && !skillPaused)
		{
			progressBar.setDimmed(false);
			paused = false;
			pauseSkill.setText("Pause");
		}
	}

	private String htmlLabel(XpPanelLabel panelLabel, XpSnapshotSingle xpSnapshotSingle)
	{
		// hide actions for combat skills
		if (isActions(panelLabel) && isCombatSkill(skill))
		{
			return "";
		}

		String key = panelLabel.getKey() + ": ";
		String value = panelLabel.getValueFunc().apply(xpSnapshotSingle);
		return htmlLabel(key, value);
	}

	private static boolean isActions(XpPanelLabel panelLabel)
	{
		return panelLabel == XpPanelLabel.ACTIONS_LEFT || panelLabel == XpPanelLabel.ACTIONS_HOUR || panelLabel == XpPanelLabel.ACTIONS_DONE;
	}

	private static boolean isCombatSkill(Skill skill)
	{
		return skill == Skill.ATTACK || skill == Skill.STRENGTH || skill == Skill.DEFENCE || skill == Skill.HITPOINTS || skill == Skill.RANGED;
	}

	static String htmlLabel(String key, int value)
	{
		String valueStr = QuantityFormatter.quantityToRSDecimalStack(value, true);
		return htmlLabel(key, valueStr);
	}

	static String htmlLabel(String key, String valueStr)
	{
		return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), key, valueStr);
	}
}
