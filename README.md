# Time to Max

A RuneLite plugin that helps you track your progress towards maxing all skills in Old School RuneScape. Set a target date and track your XP gains to stay on schedule for achieving all 99s.

## Features

### XP Tracking and Forecasting

- **Interval-Based XP Tracking**: Choose to track your XP gains on a daily, weekly, or monthly basis
- **Target Date Setting**: Set a specific date by which you want to max all your skills (defaults to 1 year from now)
- **Progress Calculation**: Automatically calculates how much XP you need per day/week/month to achieve your max goal
- **Automatic Reset**: XP tracking automatically resets at the start of each new interval (day/week/month)
- **Time-Based Projections**: See how long it will take to reach your goals at your current XP rates

### Progress Visualization

- **Skill Sorting**: Skills are organized into "Active" and "Completed" categories based on whether you've met your XP goal for the current interval
- **Recent Skills Prioritization**: Option to move recently trained skills to the top of the list for easier tracking
- **XP Gained Display**: Shows how much XP you've gained in each skill during the current interval
- **Required XP Display**: Shows how much XP you need to gain for each skill to stay on track
- **Progress Bar**: Visual representation of your progress towards each skill's interval goal
- **Remaining Time**: Shows the number of days/weeks/months remaining until your target date
- **Actions Left**: See how many more actions are needed to reach your XP goals

### UI Customization

- **Configurable Info Boxes**: Customize what information is displayed in each corner of the XP info boxes
- **Progress Bar Labels**: Choose between percentage or time-to-level displays on progress bars
- **Tooltip Information**: Configure tooltip content for additional information
- **On-Screen Tracking**: Add individual skills to the game canvas for real-time tracking
- **Skill Tab Integration**: Right-click options on the in-game skill tab to add/remove canvas trackers

### Auto-Pause Features

- **Logout Pausing**: Option to automatically pause XP tracking when you log out
- **Inactivity Detection**: Auto-pause tracking after a configurable period of inactivity
- **Rate Reset**: Option to reset XP rates after a period of inactivity

### Organization Options

- **Pin Completed Skills**: Option to pin completed skills to the bottom of the list
- **Collapse Completed Skills**: Hide completed skills to focus on remaining goals
- **Sort by Recent XP**: Organize skills based on most recently gained XP

### Commands

- **::ttmreset**: Reset your XP baseline at any time with the `::ttmreset` command
- **Confirmation Dialogs**: Prevents accidental resets with confirmation prompts

### Completionist Mode

- **Normal vs Completionist Mode**: Toggle between tracking time to max for level 99 in all skills or achieving 200 million XP in all skills.

### XP Override and Custom Calculations

- **Override XP Values**: Manually override XP values for any skill to customize your tracking calculations
- **Custom Calculation Logic**: Drive all calculation logic from your custom XP values rather than actual in-game XP
- **Focus Assistance**: Option to mark the lowest skill with a visual outline to help focus on skills that need the most attention

## How to Use

1. Install the plugin through the RuneLite Plugin Hub
2. Set your target max date in the configuration panel (YYYY-MM-DD format)
3. Choose your preferred tracking interval (day, week, month)
4. Play the game normally, and the plugin will track your XP gains
5. Check the Time to Max side panel to see your progress towards each skill
6. Reset your baseline manually at any time with the `::ttmreset` command
7. Right-click skills in the in-game skill tab to add them to your canvas overlay

## Configuration Options

### Target Settings

- **Target Date**: Set your goal completion date in YYYY-MM-DD format
- **Tracking Interval**: Choose between daily, weekly, or monthly tracking

### Pause Settings

- **Pause on Logout**: Toggle whether tracking pauses when you log out
- **Auto Pause After**: Set how many minutes of inactivity before tracking pauses
- **Auto Reset After**: Configure how long before XP rates reset after inactivity

### Display Settings

- **Info Box Labels**: Configure what information appears in each section of the XP info boxes
- **Progress Bar Style**: Choose between percentage or time-to-level displays
- **Tooltip Content**: Customize what information appears in tooltips
- **Skill Organization**: Configure how skills are sorted and displayed
- **Lowest Skill Outline**: Toggle visual outline on the skill with the lowest progress to help focus your training

### Advanced Settings

- **XP Override**: Manually set custom XP values for any skill to override actual game values
- **Custom Calculations**: Use overridden XP values as the basis for all plugin calculations and projections

## Tips for Success

- Set a realistic target date based on your available playtime
- Focus on skills showing in the "Active" category that need more XP
- Use the automatic interval reset feature to stay accountable every day/week/month
- Add your most important skills to the canvas overlay for constant monitoring
- Adjust your target date if your play schedule changes

## Donating

If you find this plugin worth a dollar or two and want to [buy me a coffee](https://buymeacoffee.com/hamelot), I would be most grateful!

## License

This project is licensed under the BSD 2-Clause License - see the LICENSE file for details.
