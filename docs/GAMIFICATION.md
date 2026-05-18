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

---

## Session Memory and Chronicle

**Status: Shipped**

A personal journaling layer attached to any logged play. Users can capture the emotional tone of a session through moods and a memorable quote. The app then generates a single atmospheric sentence — a chronicle line — that preserves the feeling of that session. Not a gameplay summary; a memory.

### Memory Input

Users open a play's details and tap "Capture this session" (or Edit) to enter:

- **Moods** — multi-select chips from preset labels plus any custom moods the user has saved. Typing into the "Add another mood" field appends a new mood without requiring it to match an existing chip.
- **Quote** — a free-text memorable line from the session (max 100 chars).

Moods are saved to `SecurePreferences` as `custom_moods` the first time they appear and are available as chips in all future sessions.

### Chronicle Generation

After saving moods or a quote, the app generates a chronicle line:

1. `SessionChronicleService.plan()` computes a `sourceKey` (SHA-256 of game name, player names, colors, moods, quote). If the key matches the existing chronicle's source key, no re-generation is triggered.
2. If generation is needed, the memory is persisted immediately and a background coroutine is launched.
3. `GeminiChronicleLineGenerator` calls the Gemini API with a detailed prompt. Retries up to 4 times on 429/503; falls back to the next model on rate limit.
4. If Gemini fails or the device is offline, `FallbackChronicleComposer` produces a deterministic output derived from the source key — the same session always produces the same fallback line.
5. The final `chronicleLine` (max 110 chars) is persisted to the `play_memories` table and overlaid on load.

### UI Surfaces

- **`MemorySection`** in `PlayDetailsDialog`: shows moods as amber chips and the quote with a `—` prefix. An Edit button opens the memory editor.
- **`ChronicleInsightCard`**: golden-bordered card with `AutoStories` icon, appears above insights in `PlayDetailsDialog`. Shows `...` while generation is pending.
- **Auto-trigger**: opening `PlayDetailsDialog` for a play that has moods/quote but no chronicle calls `ensureChronicleForPlay()`, so chronicles are generated without requiring the user to re-save.

### Persistence

Chronicles are stored in the `play_memories` Room table (DB version 4, migration 3→4). This table is untouched by BGG sync. On read, both `getLoggedPlays()` and `getBggPlaysCache()` apply the overlay. If no DB entry exists, `parseMemoryFromNotes()` falls back to reading `$$mood:` and `$$quote:` lines written to the play's BGG comments field.

### Enable / Disable

A Chronicles toggle in Settings > AI (default: on). Turning it off immediately cancels all in-flight generation jobs and hides chronicle cards everywhere. Stored as `chronicle_enabled` in `SecurePreferences`.

### Data Model

`SessionMemory` fields relevant to Chronicle:

| Field | Type | Purpose |
|---|---|---|
| `moods` | `List<String>` | Selected mood labels |
| `quote` | `String` | Memorable line from session |
| `chronicleLine` | `String` | Generated memory sentence (max 110 chars) |
| `chronicleSourceKey` | `String` | SHA-256 of inputs; prevents duplicate generation |
| `chronicleCreatedAt` | `Long?` | Timestamp of generation |

BGG notes format (appended to play comments, stripped on display):
```
$$mood: chaotic, tense
$$quote: I had no idea what I was doing
```

---

---

## Challenges

**Status: Shipped**

User-defined play goals that track progress live against logged play history. Not a leaderboard or timed pressure feature — just a personal target with quiet, honest tracking.

### Goal Types

| Type | Description |
|---|---|
| `PLAY_N_TIMES` | Total plays across all games in the period |
| `PLAY_SPECIFIC_GAME` | Plays of a single named game in the period |
| `PLAY_N_DISTINCT` | Number of distinct games played in the period |

### Data Model (`model/Models.kt`)

```kotlin
data class Challenge(
    val id: String,         // UUID
    val title: String,
    val type: ChallengeType,
    val targetCount: Int,
    val gameId: Int?,       // PLAY_SPECIFIC_GAME only
    val gameName: String?,  // PLAY_SPECIFIC_GAME only
    val startDate: String?, // YYYY-MM-DD, optional filter
    val endDate: String?,   // YYYY-MM-DD, optional filter
    val createdAt: Long
)

data class ChallengeProgress(val challenge: Challenge, val currentCount: Int) {
    val isComplete: Boolean get() = currentCount >= challenge.targetCount
    val fraction: Float     get() = (currentCount / targetCount).coerceIn(0f, 1f)
}
```

### Persistence

Stored as a JSON array under `challenges` in `SecurePreferences`. Not in Room — challenges are user-owned metadata with no sync relationship to BGG or Sheets.

### State & Logic (`AppViewModel.kt`)

- `loadChallenges()` — called once at app startup via `LaunchedEffect` in `AppShell`.
- `addChallenge()` / `deleteChallenge()` — mutate and persist immediately.
- `getChallengeProgressList()` — pure calculation over `historyPlays`: filters plays by optional date range, then counts per goal type (sum of `quantity` for PLAY_N_TIMES and PLAY_SPECIFIC_GAME; distinct game IDs for PLAY_N_DISTINCT).

### UI

- **History → Challenges tab** (`ui/challenges/ChallengesScreen.kt`): primary surface. `ChallengeCard` shows title, description, progress bar, and count. Completed challenges show "Complete" with a primary-container background tint.
- **New Play screen active strip**: incomplete challenges shown as a contextual reminder above the game search list.
- **`CreateChallengeDialog`**: `AnimatedDialog` with title, type dropdown, optional game search (PLAY_SPECIFIC_GAME), target count, and optional date range.

---

## Good Picks (Post-Save Recommendations)

**Status: Shipped**

After a play is logged, the `PostSaveCard` shows a collapsible "Try next" section with up to 2 owned-game suggestions that fit the current player count. The system is deterministic and data-driven — no AI, no random selection.

### Player Count Scoring (`AppViewModel.playerCountFitScore`)

Priority order, checked in sequence:

| Source | Score | Condition |
|---|---|---|
| `notRecommendedPlayers` | 0.0 (filtered) | Player count appears in BGG "Not Recommended" poll result |
| `bestPlayers` | 2.0 | Player count appears in BGG "Best" poll result |
| `recommendedPlayers` | 1.6 | Player count appears in BGG "Recommended" poll result |
| Official min–max range | 1.2 | Player count within `minPlayers`–`maxPlayers` |
| No match | 0.0 (filtered) | Player count outside all known data |

`bestPlayers`, `recommendedPlayers`, and `notRecommendedPlayers` are comma-separated strings parsed from the BGG `suggested_numplayers` poll and stored as columns on `canonical_games` (DB version 7). The `notRecommendedPlayers` column was added in migration 6→7.

### Recommendation Lanes (`AppViewModel.getPostSaveRecommendations`)

| Lane | Logic |
|---|---|
| Exact group match | Same players as this session have played the game before together |
| Rematch | You've played it recently; scored by time-since and familiarity |
| Neglected favorites | ≥2 plays, last played 21+ days ago |
| Quick to table | Playing time ≤60 min; sorted by shortest time |

All lanes hard-filter on `fitsPlayerCount()` (i.e., `playerCountFitScore > 0.0`) before scoring.

### Reason Strings

The pick's displayed reason is contextual:
- "Best at N player(s)" — from `bestPlayers`
- "Recommended for N player(s)" — from `recommendedPlayers`
- "Fits M–N players" — from official range
- "Quick to table at about N min" — for the quick-to-table lane

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

AppViewModel.kt
  loadChallenges / addChallenge / deleteChallenge
  getChallengeProgressList()   — live progress over historyPlays
  getPostSaveRecommendations() — post-log good picks (lanes: group, rematch, neglected, quick)
  playerCountFitScore()        — BGG poll priority: notRec→0, best→2.0, rec→1.6, range→1.2
  fitsPlayerCount()            — gate: score > 0.0
  recommendationReasonForPlayerCount() — contextual reason string

ui/challenges/ChallengesScreen.kt
  ChallengesTabContent         — Challenges tab in History
  ChallengeCard                — single challenge row with progress bar
  CreateChallengeDialog        — new-challenge form

ui/search/NewPlayScreen.kt
  Active Challenges strip      — incomplete challenges above game list

ui/review/LogPlayScreen.kt
  PostSaveCard                 — "Try next" Good Picks section
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
| Good Picks ("Try next") | Post-log card | `getPostSaveRecommendations()` | After a play is logged, when owned games fit player count |
| Challenges list | History → Challenges tab | `getChallengeProgressList()` | Always (empty state when no challenges) |
| Active Challenges strip | New Play screen | `challenges` StateFlow | When ≥1 incomplete challenge exists |
