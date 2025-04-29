# Time to Max

A RuneLite plugin that helps you track your progress towards maxing all skills in Old School RuneScape.

## Features

### XP Tracking and Forecasting

- **Interval-Based XP Tracking**: Choose to track your XP gains on a daily, weekly, or monthly basis
- **Target Date Setting**: Set a specific date by which you want to max all your skills
- **Progress Calculation**: Automatically calculates how much XP you need per day/week/month to achieve your max goal
- **Automatic Reset**: XP tracking automatically resets at the start of each new interval (day/week/month)

### Progress Visualization

- **Skill Sorting**: Skills are organized into "Active" and "Completed" categories based on whether you've met your XP goal for the current interval
- **XP Gained Display**: Shows how much XP you've gained in each skill during the current interval
- **Required XP Display**: Shows how much XP you need to gain for each skill to stay on track
- **Progress Bar**: Visual representation of your progress towards each skill's interval goal
- **Remaining Time**: Shows the number of days/weeks/months remaining until your target date

### Notifications

- **Goal Completion**: Receive chat notifications when you've gained enough XP in a skill for the current interval
- **Max Level Alert**: Updates the UI when a skill reaches level 99 to focus on remaining skills

### Commands

- **::resetxp**: Reset your XP baseline at any time with the `::resetxp` command
- **Confirmation Dialogs**: Prevents accidental resets with confirmation prompts

### Configuration

- **Target Date**: Set your goal completion date in YYYY-MM-DD format
- **Tracking Interval**: Choose between daily, weekly, or monthly tracking
- **Chat Notifications**: Toggle notifications when you reach your XP goals

## How to Use

1. Install the plugin through the RuneLite Plugin Hub
2. Set your target max date in the configuration panel
3. Choose your preferred tracking interval (day, week, month)
4. Play the game normally, and the plugin will track your XP gains
5. Check the Time to Max side panel to see your progress towards each skill
6. Reset your baseline manually at any time with the `::resetxp` command

## Tips for Success

- Set a realistic target date based on your available playtime
- Focus on skills showing in the "Active" category that need more XP
- Use the automatic interval reset feature to stay accountable every day/week/month
- Adjust your target date if your play schedule changes

## License

This project is licensed under the BSD 2-Clause License - see the LICENSE file for details.
