# BoardFlow Gamification System

A deterministic, data-driven layer that makes the play history feel alive. No XP bars, no levels to grind, no streaks to maintain — just the data speaking in a voice that rewards attention.

## Design Principles

- **Rarity is deterministic.** Every tier is earned by real data thresholds, never random.
- **No exclamation marks.** Periods everywhere. The tone is dry, warm, and slightly wry.
- **Emojis only on `RecordMoment`.** Post-log moments (FirstWin, NewHighScore, WinStreak) use emoji; all other surfaces do not.
- **Nothing fires unless the data warrants it.** Insights are absent before they are earned.
- **No new infrastructure.** All phases operate on the existing `List<LoggedPlay>` data.

---

## Rarity Tiers

Defined in `model/Models.kt` as `InsightRarity`.

| Tier | Label | sortWeight | Meaning |
|---|---|---|---|
| COMMON | Moment | 0 | Baseline observation |
| NOTABLE | Notable | 1 | Emerging pattern |
| RARE | Landmark | 2 | Meaningful milestone |
| EPIC | Chronicle | 3 | Significant achievement |
| LEGENDARY | Legacy | 4 | Rare, lasting record |

Rarity drives visual treatment throughout the app: background alpha, border colour/opacity, icon tint, and motion (shimmer, haptic). Extension functions on `InsightRarity` in `PlayStatsTab.kt` — `cardBrush()`, `primaryTextColor()`, `mutedTextColor()`, `accentColor()`, `borderColor()` — provide all rarity-derived colours.

---

## Phase 1 — Rarity on All Insight Surfaces

**Status: Shipped**

Every insight surface now carries a `rarity` field and renders it visually.

### Changes

**`model/Models.kt`**
- `InsightRarity` enum moved here from the UI layer (domain concept, needed by `RecordMoment`).
- `RecordMoment` sealed class gained a `rarity` computed property:
  - `FirstWin` → NOTABLE
  - `NewHighScore` → NOTABLE
  - `WinStreak` (≥7) → EPIC, (≥4) → RARE, else → NOTABLE
- `RecordMoment.displayText` uses emoji: `🎉`, `🏆`, `🔥`.

**`ui/history/PlayStatsHelpers.kt`**
- `ContextualInsight` data class: `rarity: InsightRarity` field added.
- `InsightCandidate` (private): `rarity: InsightRarity` field added; propagated through `gameContextualInsight()`.
- `PlayInsight` data class introduced: `data class PlayInsight(val text: String, val rarity: InsightRarity = InsightRarity.COMMON)`. Replaces `List<String>` return from `playInsights()`.
- `playInsights()` now returns `List<PlayInsight>`.

**`ui/history/InsightStripCard.kt`**
- `PlayInsightStrip` now takes `PlayInsight` instead of `String`.
- `ContextualInsightStrip` passes `insight.rarity` down.
- `InsightStripCard` (private): rarity scales bgAlpha (0.42–0.55), borderColor (primary → tertiary → amber), borderAlpha (0.08–0.50), iconAlpha (0.72–1.0).

**`ui/history/HistoryScreen.kt`**
- `PlayInsightStrip(insight = insight)` call updated; bare `String` fallback items wrapped in `PlayInsight(...)`.

---

## Phase 2 — New Insight Categories

**Status: Shipped**

Five new insight types added to the contextual engine and the Smart Observation system.

### 2a. Approaching Milestone (`buildInsightCandidates`)

Fires when a game is 1 or 2 plays away from the next milestone (10 / 25 / 50 / 100 / 200), but only when no exact milestone fired for this render.

| Gap | Text | Rarity |
|---|---|---|
| 1 | "One away from 50. Something's coming." | NOTABLE |
| 2 | "Two plays from 50. Getting close." | NOTABLE |

### 2b. Head-to-Head Rivalry (`buildInsightCandidates`)

Replaces the simple win-count leader with pairwise session tracking across the game's entire history.

- Finds the pair with the most co-plays (minimum 4 together).
- Shows personalised text when the current player is one of the two (`currentPlayerName`):
  - "You lead Martin 6–3 across 9 sessions."
  - "Jana leads you 7–4 across 11 sessions."
  - "Deadlocked with Petra. 5 each across 10 sessions."
- Falls back to simple win-leader when no pair reaches the threshold.

| Sessions together | Rarity |
|---|---|
| 4–9 | NOTABLE |
| 10+ | RARE |

### 2c. Anniversary Insights (`buildSmartObservations`)

When the first play date for any game falls within ±7 days of today (at least 1 year ago), surfaces:

- "One year ago, you played Wingspan for the first time. 47 plays since." → NOTABLE
- "3 years ago, you played Wingspan for the first time. 47 plays since." → RARE (2+ years)

### 2d. Patron Game (`buildSmartObservations`)

Identifies the game you keep returning to after breaks. Counts "comeback sessions" — plays where the previous play of that same game was 14+ days earlier. Requirements: 8+ total plays, 4+ logged dates, 3+ comebacks.

- "Wingspan is the game you keep coming back to. 8 returns after a break." → NOTABLE
- RARE at 6+ comebacks.

### 2e. Dormant Nudge Enrichment (`buildInsightCandidates`)

Dormant insight now includes the total play count for weight:

- "Hasn't hit the table in 3 months. 47 plays in the books."

---

## Phase 3 — Game Mastery Levels

**Status: Shipped**

A single silent label on each game's detail screen based on personal play count. No bar, no animation, no ceremony.

| Plays | Label |
|---|---|
| 1–4 | Learning |
| 5–14 | Familiar |
| 15–29 | Comfortable |
| 30–49 | Practiced |
| 50–99 | Deep |
| 100+ | Mastered |

### Implementation

**`ui/collection/GameDetailDialog.kt`**
- `masteryLabel(plays: Int): String?` — private pure function; returns `null` for 0 plays.
- `YourStatsCard`: mastery label rendered as a small `CircleShape` Surface pill immediately below the "plays" text, using `primary` at 10% alpha background and 78% alpha text. Absent when no plays exist.

---

## Phase 5 — HeroObservationCard Entrance Animation

**Status: Shipped**

Three layered motion behaviours added to `HeroObservationCard` in `PlayStatsTab.kt`. All are keyed to the current `observation` — they restart cleanly on every card change (including tap-to-cycle).

### Scale Entrance

Every observation entrance springs from 0.95 → 1.0 using:

```kotlin
spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
```

No overshoot, settles in ~300 ms. Applied via `Modifier.scale(scale.value)` on the outer Surface.

### Shimmer Sweep (Epic / Legendary only)

1.5 seconds after the card settles, a `Brush.horizontalGradient` sweeps left-to-right across the card over 800 ms (`FastOutSlowInEasing`). Rendered as a `Canvas` overlay inside the card's Box. Fires once per observation; never loops.

Gradient: `Transparent → White 20% → White 8% → Transparent`

### Haptic Feedback (Epic / Legendary only)

`HapticFeedbackType.LongPress` fires immediately on reveal via `LocalHapticFeedback`. Silent for Common, Notable, and Rare.

---

## Phase 6 — Period in Review Card

**Status: Shipped**

An auto-generated narrative card that surfaces at the top of the Stats screen (All-time view) during the first days of a new period. Not a notification — just appears and disappears based on the calendar.

### Trigger Windows

| Window | Reviews |
|---|---|
| Jan 1–7 | Previous year |
| 1st–5th of any other month | Previous month |

Year review takes priority (Jan 1–5 would otherwise also qualify as month-end).

### Content Format

```
April: 14 plays, 4 games, 3 new players. Martin finally won Brass.
```

Stats string: plays, games, new players (players appearing for the first time across all of your history).

### Highlight Logic (`buildPeriodHighlight`)

1. **"Finally won"** — looks for a player who won a game in the period but had played it at least twice before without ever winning. Picks the one with the most prior games (most overdue). "Martin finally won Brass."
2. **Fallback** — dominant winner for the most-played game in the period, minimum 2 wins. "[Player] led [Game] — N wins."
3. **No highlight** — if neither condition is met, the sentence ends after the stats: "April: 14 plays, 4 games."

### UI

`PeriodReviewCard` in `PlayStatsTab.kt`: a small primary-tinted Surface card. Period label uppercase at the top ("APRIL IN REVIEW"), headline as `bodyMedium` below. Sits above the contextual narrative header, only when `timeRange == StatsTimeRange.ALL`.

---

## Architecture Map

```
model/Models.kt
  InsightRarity          — rarity enum, domain layer
  RecordMoment           — post-log record moments with rarity + displayText

ui/history/PlayStatsHelpers.kt
  InsightType            — priority-ordered insight category enum
  InsightCandidate       — private: candidate before deduplication
  ContextualInsight      — public: game-scoped insight with rarity
  PlayInsight            — per-play insight with rarity
  SmartObservation       — all-time observation with rarity + subtext
  PeriodReview           — month/year review data class
  buildInsightCandidates()     — generates per-game contextual candidates
  gameContextualInsight()      — selects + deduplicates from candidates
  playInsights()               — insights for a single play (List<PlayInsight>)
  buildSmartObservations()     — all-time observation engine
  buildPeriodReview()          — month/year review generator
  shortName()                  — "Martin Alexander Krul" → "Martin K." for graph labels

ui/history/InsightStripCard.kt
  PlayInsightStrip             — rarity-aware strip for per-play insights
  ContextualInsightStrip       — rarity-aware strip for game-scoped insights
  InsightStripCard (private)   — shared rarity rendering core

ui/history/PlayStatsTab.kt
  InsightRarity extensions     — cardBrush, primaryTextColor, mutedTextColor,
                                  accentColor, borderColor
  HeroObservationCard          — spring entrance + shimmer + haptic
  PeriodReviewCard             — month/year review surface

ui/collection/GameDetailDialog.kt
  masteryLabel()               — play count → mastery label
  YourStatsCard                — renders mastery pill chip
```

---

## Surfaces at a Glance

| Surface | Location | Driven by | When visible |
|---|---|---|---|
| Mastery chip | Game detail → YourStatsCard | `masteryLabel(plays)` | Any game with ≥1 personal play |
| Contextual insight strip | Game detail dialog | `gameContextualInsight()` | Game has ≥1 logged play |
| Per-play insight strip | Play details dialog | `playInsights(play)` | Play has notable data |
| HeroObservationCard | Stats tab | `buildSmartObservations()` / `buildRangeObservations()` | ≥5 all-time plays |
| PeriodReviewCard | Stats tab (All-time) | `buildPeriodReview()` | First 5 days of new month, first 7 days of new year |
| Table Brief | Stats tab | `buildStatsBrief()` | Any selected range with enough play data |
| RecordMoment | Post-log card | `AppViewModel` record detection | After a play is logged |
