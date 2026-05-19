# BoardFlow Gamification, Memory, And Recommendation Reference

BoardFlow has a deterministic engagement layer built from play history rather than XP systems or random rewards. This document covers the shipped mechanics that make the data feel alive.

## Design Principles

- deterministic rules over random rewards
- no pressure-heavy gamification loops
- signals appear only when the data supports them
- reuse existing history data instead of adding a parallel progression system

## Insight Rarity

Defined in `model/Models.kt` as `InsightRarity`.

| Tier | Label | Meaning |
| --- | --- | --- |
| `COMMON` | Moment | baseline observation |
| `NOTABLE` | Notable | emerging pattern or small milestone |
| `RARE` | Landmark | meaningful achievement |
| `EPIC` | Chronicle | large achievement or memorable trend |
| `LEGENDARY` | Legacy | rare long-term record |

Rarity drives visual treatment in the history stats surfaces:

- background alpha
- border emphasis
- accent color
- shimmer and haptic treatment on the strongest cards

## Insight Surfaces

Core files:

- `ui/history/PlayStatsHelpers.kt`
- `ui/history/InsightStripCard.kt`
- `ui/history/PlayStatsTab.kt`
- `ui/collection/GameDetailDialog.kt`

Current insight surfaces:

- `ContextualInsightStrip`
- `PlayInsightStrip`
- `HeroObservationCard`
- `PeriodReviewCard`
- `Table Brief`
- post-log `RecordMoment`

## Shipped Insight Categories

The current system includes:

- milestone observations
- approaching-milestone nudges
- rivalry observations
- dormant-game nudges
- anniversary observations
- patron-game observations
- period review summaries

`RecordMoment` currently surfaces:

- first win
- new high score
- win streak

## Game Mastery

`ui/collection/GameDetailDialog.kt` renders a mastery pill in `YourStatsCard`.

Current labels:

| Plays | Label |
| --- | --- |
| `1-4` | Learning |
| `5-14` | Familiar |
| `15-29` | Comfortable |
| `30-49` | Practiced |
| `50-99` | Deep |
| `100+` | Mastered |

This is intentionally quiet UI. There is no progression bar or ceremony.

## Hero Observation Motion

`HeroObservationCard` in `PlayStatsTab.kt` currently uses:

- spring entrance scale from `0.95` to `1.0`
- one-time shimmer for `EPIC` and `LEGENDARY`
- `LongPress` haptic on `EPIC` and `LEGENDARY` reveal

## Period Review

`buildPeriodReview()` creates an auto-generated review card at the top of Stats.

Trigger windows:

- January 1-7: previous year review
- days 1-5 of other months: previous month review

Content includes:

- total plays
- unique games
- new players
- one highlight sentence when there is enough supporting data

## Session Memory

BoardFlow supports a lightweight journaling layer tied to individual plays.

Memory can include:

- moods
- quote
- note
- chronicle line

Primary files:

- `data/SessionMemoryJson.kt`
- `data/CanonicalCollectionStore.kt`
- `data/chronicle/SessionChronicleService.kt`
- `ui/history/HistoryScreen.kt`

### Persistence

Session memories live in the Room table `play_memories`.

Important notes:

- current Room DB version is `8`
- `play_memories` is independent of BGG sync
- read paths overlay stored memory onto both local and cached BGG plays
- if no Room memory exists, legacy `$$mood:` and `$$quote:` lines in comments can still be parsed as fallback

### Chronicle Generation

Chronicles are generated through `SessionChronicleService`.

Flow:

1. Build a source key from game, players, moods, quote, and related memory inputs.
2. Reuse the existing chronicle when the source key still matches.
3. Persist memory immediately, then launch generation in the background if needed.
4. Try Gemini first.
5. Fall back deterministically when Gemini fails or is unavailable.

Chronicle behavior:

- generation can be disabled globally through the `chronicle_enabled` preference
- opening a play with memory but no chronicle can trigger generation automatically
- pending plays expose a placeholder state through `chroniclePendingPlayIds`

## Challenges

Challenges are personal goals computed directly from play history.

Core files:

- `model/Models.kt`
- `AppViewModel.kt`
- `ui/challenges/ChallengesScreen.kt`
- `ui/history/HistoryScreen.kt`

### Challenge Types

Current supported types:

- `PLAY_N_TIMES`
- `PLAY_SPECIFIC_GAME`
- `PLAY_N_DISTINCT`
- `PLAYER_WIN_STREAK`
- `PLAY_WITH_GROUP_N_TIMES`
- `PLAY_STREAK`
- `PLAY_N_UNPLAYED`

### Progress Model

`ChallengeProgress` currently tracks:

- `currentCount`
- `goalCount`
- `remainingText`
- `isComplete`
- `isFailed`
- `isActive`
- `fraction`

### Persistence

Challenges are stored in `SecurePreferences`, not Room.

Why:

- they are user-owned metadata
- they do not belong to BGG or Sheets
- they do not need canonical merge behavior

### Lifecycle

Current shipped lifecycle:

- load
- auto-create monthly challenge when needed
- create
- delete
- live progress calculation

The current app does not yet ship challenge edit, pause, or archive flows.

## Recommendations

BoardFlow currently ships two recommendation systems controlled by `recommendationsEnabled`.

### New Play Recommendations

`NewPlayScreen` shows recommendation lanes when:

- the query is blank
- recommendations are enabled
- enough collection and history context exists

### Post-Save Good Picks

`LogPlayScreen` shows a collapsible "Try next" section after logging when:

- recommendations are enabled
- the player count can be matched against owned games

### Player Count Fit Rules

The current scoring priority in `AppViewModel.playerCountFitScore()` is:

| Source | Score | Result |
| --- | --- | --- |
| `notRecommendedPlayers` | `0.0` | filtered out |
| `bestPlayers` | `2.0` | strongest fit |
| `recommendedPlayers` | `1.6` | strong fit |
| official `minPlayers-maxPlayers` | `1.2` | fallback fit |
| no match | `0.0` | filtered out |

### Data Sources

The relevant collection fields live on canonical games:

- `bestPlayers`
- `recommendedPlayers`
- `notRecommendedPlayers`
- `minPlayers`
- `maxPlayers`

The `notRecommendedPlayers` field was added in migration `6 -> 7`.

## Quick Reference

| Feature | Primary file |
| --- | --- |
| rarity model | `model/Models.kt` |
| contextual and smart observations | `ui/history/PlayStatsHelpers.kt` |
| insight strips | `ui/history/InsightStripCard.kt` |
| stats hero card | `ui/history/PlayStatsTab.kt` |
| mastery pill | `ui/collection/GameDetailDialog.kt` |
| challenge state | `AppViewModel.kt` |
| challenge UI | `ui/challenges/ChallengesScreen.kt` |
| pre-log recommendations | `ui/search/NewPlayScreen.kt` |
| post-log recommendations | `ui/review/LogPlayScreen.kt` |
| chronicle orchestration | `data/chronicle/SessionChronicleService.kt` |
