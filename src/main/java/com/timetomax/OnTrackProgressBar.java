package com.timetomax;

import java.awt.Color;
import java.awt.Graphics;
import net.runelite.client.ui.components.ProgressBar;

/**
 * ProgressBar with an optional white on-track marker indicating
 * how much XP should have been gained so far in the current interval.
 */
class OnTrackProgressBar extends ProgressBar
{
    /** marker position in percent [0,100], or negative when not shown */
    private double onTrackPercent = -1;

    void setOnTrackPercent(double percent)
    {
        this.onTrackPercent = percent;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        if (onTrackPercent >= 0 && onTrackPercent <= 100)
        {
            int x = (int) (onTrackPercent / 100.0 * getWidth());
            g.setColor(Color.WHITE);
            g.fillRect(x - 1, 0, 2, getHeight());
        }
    }
}
