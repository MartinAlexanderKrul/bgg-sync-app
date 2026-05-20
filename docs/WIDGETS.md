# BoardFlow Widgets

BoardFlow ships three home-screen widgets built with Jetpack Glance.

Shared widget code lives in:

- `ui/widget/SessionsWidget.kt`
- `ui/widget/DailyInsightWidget.kt`
- `ui/widget/StatsWidget.kt`

## Shared Architecture

### `SessionGlanceWidget`

Base class: `SessionGlanceWidget : GlanceAppWidget()`

It owns:

- size registration
- tier selection
- shared layout rendering
- header bitmap rendering
- widget tap behavior
- the `WidgetSnapshot` content model

### `WidgetSnapshot`

Current shared fields:

- `header`
- `primaryText`
- `subtitleText`
- `detailText`
- `accentColor`
- `gameId`

Subclasses populate the snapshot. The rendering layer stays content-agnostic.

### Header Rendering

The header is rendered as a bitmap so the widgets can keep a consistent branded title treatment across sizes.

### Tap Behavior

- body tap opens the app
- when `gameId != 0`, body tap opens History filtered to that game
- camera tap launches quick scan

## Size Tiers

Both widgets use responsive sizing with tiny, compact, small, and expanded layouts.

| Tier | General behavior |
| --- | --- |
| Tiny | camera-only quick action |
| Compact | header, main line, camera |
| Small | header, main line, subtitle, camera |
| Expanded | header, main line, subtitle, divider, details, camera |

## Session Widget

Receiver: `SessionWidget`

Purpose:

- show the most recent logged session

Typical content:

- header: `Last Session`
- primary text: game plus relative date
- subtitle: player count and winner summary
- detail text: per-player lines in larger sizes
- game id: the logged game's BGG id when available

Update model:

- periodic alarm-driven refresh

## Daily Insight Widget

Receiver: `DailyInsightWidget`

Purpose:

- show one rotating insight derived from the stats observation engine

Typical content:

- header: observation category
- primary text: insight sentence
- subtitle: usually empty
- detail text: usually empty
- game id: `0`

Accent color currently reflects observation rarity.

Update model:

- periodic alarm-driven refresh
- persisted day-tracking so the surfaced insight rotates over time rather than repeating blindly every refresh

## Stats Widget

Receiver: `StatsWidget`

Source: `ui/widget/StatsWidget.kt`

Purpose:

- show plays this month, top game, and the most urgent active challenge

Typical content:

- header: `This Month`
- primary text: play count for the current calendar month
- subtitle: unique game count and the top-played game name
- detail text (expanded): challenge name, progress, and deadline if one is active; top 3 games by play count

Accent color signals:

- Amber: active challenge with a deadline 3 or fewer days away
- Teal: active challenge with no imminent deadline
- Blue: no active challenges or challenge progress not tracked (pure stats view)

Challenge progress computed inline using the same type logic as `ChallengeNotificationWorker`. Streak-type challenges (`PLAY_STREAK`, `PLAYER_WIN_STREAK`) are excluded from widget progress calculation.

Default widget size: 2x1 cells, resizable in both directions.

Update model:

- periodic alarm-driven refresh (same 5-minute interval as the other widgets)
- action constant: `cz.nicolsburg.boardflow.ACTION_REFRESH_STATS`
- AlarmManager request code: `4`

## Widget Entry Points

The main app receives widget launches through `MainActivity` and `AppViewModel` state:

- widget quick scan uses `pendingWidgetQuickScan`
- widget open-play routing uses `pendingWidgetOpenGameId`

`AppShell` consumes both and navigates to the correct destination.

## Maintenance Notes

- keep new widget types on the shared `WidgetSnapshot` model when possible
- prefer extending the shared base widget instead of duplicating layout code
- update this document if a new widget, new size tier behavior, or new widget-triggered route is added
