# Time To Max — Dev Tools
`build.gradle`'s `run` task now passes `-Dtimetomax.dev=true`. Dev commands are silently no-op without it, so production users never get access.

## Dev panel label

When dev mode is on, the panel shows a dev clock at the top of the target panel:

```
Dev clock: 2026-05-25 23:59:55 [+2d 12h]
```

- First half = effective wall time (`XpCalculator.now()` after offset).
- Bracketed half = current offset, or `no offset` when real time.
- Refreshes once per second from the existing `updateTargetPanel` tick.

## Commands

| Command | Effect |
|---|---|
| `::ttmreset` | Wipe state, re-initialize from current XP (already existed) |
| `::ttmdate` | Print effective time + offset |
| `::ttmdate +1d` | Jump forward 1 day; time keeps ticking from there |
| `::ttmdate -6h` | Jump back 6 hours |
| `::ttmdate +30m` / `::ttmdate +45s` | Minute/second shifts |
| `::ttmdate +1d12h30m` | Combos — any chain of `\d+[dhms]` |
| `::ttmdate 2026-05-30` | Jump to that date at 00:00:00 |
| `::ttmdate 2026-05-30T23:59:55` | Jump to specific datetime (boundary tests) |
| `::ttmdate clear` (or `off`, `reset`, `none`) | Zero the offset |
| `::ttmstate` | Dump: effective time, offset, interval, ISO week, target/days remaining, current period start, earliest skill startDate, would-reset-now flag |
| `::ttmgetstart` | Per-initialized-skill: startDate + startXp |

Offset is process-local, not persisted — restart = clean slate.