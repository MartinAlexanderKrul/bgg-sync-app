# BoardFlow Widgets

BoardFlow ships three home-screen widgets. All are defined in `app/src/main/res/xml/` and implemented in `app/src/main/kotlin/…/ui/widget/`.

---

## 1. Quick Scan  (`QuickScanWidget`)

**File:** `ui/widget/QuickScanWidget.kt`  
**Layout:** `res/layout/widget_quick_scan.xml`  
**Info:** `res/xml/quick_scan_widget_info.xml`

| Property | Value |
|---|---|
| Default size | 1 × 1 cell |
| Resizable | No |
| Description | *"Launch BoardFlow directly into Quick Scan"* |

**What it does:**  
One-tap shortcut directly into the OCR/QR score-scanning flow. The widget shows the BoardFlow logo, a "SCAN" label above, and a "SCORE" label below. Tapping anywhere launches `MainActivity` with `ACTION_QUICK_SCAN`.

---

## 2. Launcher  (`LauncherWidget`)

**File:** `ui/widget/LauncherWidget.kt`  
**Layout:** `res/layout/widget_launcher.xml`  
**Info:** `res/xml/launcher_widget_info.xml`

| Property | Value |
|---|---|
| Default size | 1 × 1 cell |
| Resizable | No |
| Description | *(none set)* |

**What it does:**  
Standard icon-style launcher — round app icon with the app name rendered in Cinzel Decorative below it. Tapping opens the app at its default screen. Useful for users who prefer a branded shortcut over the system app icon.

---

## 3. Last Session  (`InsightsWidget`)

**File:** `ui/widget/InsightsWidget.kt`  
**Layouts:** `res/layout/widget_insights*.xml` (5 responsive tiers)  
**Info:** `res/xml/insights_widget_info.xml`

| Property | Value |
|---|---|
| Default size | 3 × 2 cells |
| Resizable | Yes — horizontal and vertical, down to 1 × 1 |
| Description | *"Shows your last board game session with players, scores, and streaks. Resize to control how much detail is shown."* |

### How it works

The widget reads your local play history once per update cycle and selects the highest-priority **snapshot** from six candidate types:

| Priority | Candidate | Shown when |
|---|---|---|
| 120 | **Approaching Landmark** | A game is 1–2 plays away from 10/25/50/100/200 |
| 110 | **Landmark** | A game just hit 25/50/100/200 plays (within 2 days) |
| 80 | **Last Session** | Always available if any plays exist |
| 70 | **Rivalry Pulse** | Two players have ≥ 3 head-to-head plays in the last 7 days |
| 55 | **Waiting** | A game with ≥ 2 plays hasn't been played in 30+ days |
| 40 | **Season** | At least 1 play logged this calendar month |

The widget avoids showing the same text two days in a row (rotates to the next candidate).

**Tapping** the body opens the app's play history. **Tapping the camera icon** jumps straight into Quick Scan.

---

### Responsive size tiers

The widget reads its current dimensions from the Android options bundle on every update and resize, so the layout adapts instantly when you drag to resize.

| Tier | Width | Height | Layout file | Content |
|---|---|---|---|---|
| **Micro** | any | < 60 dp | `widget_insights_micro.xml` | Camera icon only (centred). Tap launches Quick Scan. |
| **Compact** | ≥ 110 dp | 60–100 dp | `widget_insights_compact.xml` | Single line: `GameName · Day`. Camera icon on the right. |
| **Standard** | ≥ 110 dp | 100–170 dp | `widget_insights.xml` | Cinzel header + `GameName · Day` + players/winner. **Default (3 × 2).** |
| **Tall** | ≥ 110 dp | 170–250 dp | `widget_insights_tall.xml` | Standard + divider + each player on their own line with score and ★ for winner. Falls back to session date if no players logged. |
| **Large** | ≥ 110 dp | ≥ 250 dp | `widget_insights_large.xml` | Tall + second divider + text from the next-highest-priority snapshot (e.g. a streak, rival pulse, or milestone teaser). |

**Wide upgrade:** Any tier with width ≥ 290 dp additionally shows the app logo (`@drawable/app_logo`) to the left of the content.

---

### Adding a new snapshot type

1. Add a private function in `InsightsWidget.kt` that returns `WidgetSnapshot?`.
2. Call it inside `buildSnapshotCandidates()` and add its result to the `listOfNotNull(…)`.
3. Set a `priority` value to control when it wins over other candidates (see table above).
4. If the snapshot carries a raw `LoggedPlay` (for TALL/LARGE rich display), set `play = …` and `playDate = …` on the returned `WidgetSnapshot`.

---

### Layout IDs reference

| View ID | Layouts | Purpose |
|---|---|---|
| `widget_insights_root` | All | Tap target → open app |
| `widget_insights_scan` | All | Tap target → Quick Scan |
| `widget_insights_logo` | Standard, Tall, Large | App logo; `GONE` by default, shown when width ≥ 290 dp |
| `widget_insights_header` | Standard, Tall, Large | Cinzel Decorative bitmap (rendered via `renderSingleLine`) |
| `widget_insights_text` | Compact, Standard, Tall, Large | Primary text line(s) |
| `widget_insights_players` | Tall, Large | Player list with scores |
| `widget_insights_hero_section` | Large | Wrapper for hero insight (divider + text); `GONE` until a second candidate is available |
| `widget_insights_hero` | Large | Hero insight text |
