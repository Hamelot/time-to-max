package com.timetomax;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;

class XpPauseState
{
    // Internal state
    private final Map<Skill, XpPauseStateSingle> skillPauses = new EnumMap<>(Skill.class);
    private final XpPauseStateSingle overall = new XpPauseStateSingle();
    private boolean prevIsLoggedIn = false;

    boolean pauseSkill(Skill skill)
    {
        return findPauseState(skill).manualPause();
    }

    boolean pauseOverall()
    {
        return overall.manualPause();
    }

    boolean unpauseSkill(Skill skill)
    {
        return findPauseState(skill).unpause();
    }

    boolean unpauseOverall()
    {
        return overall.unpause();
    }

    boolean isPaused(Skill skill)
    {
        return findPauseState(skill).isPaused();
    }

    boolean isOverallPaused()
    {
        return overall.isPaused();
    }

    void tickXp(Skill skill, long currentXp, int pauseAfterMinutes)
    {
        final XpPauseStateSingle state = findPauseState(skill);
        tick(state, currentXp, pauseAfterMinutes);
    }

    void tickOverall(long currentXp, int pauseAfterMinutes)
    {
        tick(overall, currentXp, pauseAfterMinutes);
    }

    private void tick(XpPauseStateSingle state, long currentXp, int pauseAfterMinutes)
    {
        if (state.getXp() != currentXp)
        {
            state.xpChanged(currentXp);
        }
        else if (pauseAfterMinutes > 0)
        {
            final long now = System.currentTimeMillis();
            final int pauseAfterMillis = pauseAfterMinutes * 60 * 1000;
            final long lastChangeMillis = state.getLastChangeMillis();
            // When config.pauseSkillAfter is 0, it is effectively disabled
            if (lastChangeMillis != 0 && (now - lastChangeMillis) >= pauseAfterMillis)
            {
                state.timeout();
            }
        }
    }

    void tickLogout(boolean pauseOnLogout, boolean loggedIn)
    {
        // Deduplicated login and logout calls
        if (!prevIsLoggedIn && loggedIn)
        {
            prevIsLoggedIn = true;

            for (Skill skill : Skill.values())
            {
                findPauseState(skill).login();
            }
            overall.login();
        }
        else if (prevIsLoggedIn && !loggedIn)
        {
            prevIsLoggedIn = false;

            // If configured, then let the pause state know to pause with reason: logout
            if (pauseOnLogout)
            {
                for (Skill skill : Skill.values())
                {
                    findPauseState(skill).logout();
                }
                overall.logout();
            }
        }
    }

    private XpPauseStateSingle findPauseState(Skill skill)
    {
        return skillPauses.computeIfAbsent(skill, (s) -> new XpPauseStateSingle());
    }
}
