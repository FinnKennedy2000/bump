# Bump — pregnancy tracker for Android

Design spec. 2026-08-29.

## Purpose

A personal, offline Android app that tracks a pregnancy: what week it is, how the
baby is developing, how long is left, plus a small amount of logging (appointments,
kicks, weight, notes). A homescreen widget shows the current week at a glance.

Personal use, sideloaded. No Play Store release, no accounts, no backend, no
analytics, no network permission.

## Core insight

The pregnancy itself is **one date**. Given a due date and today, the current week,
trimester, days remaining and progress fraction are all pure functions. Nothing about
the pregnancy needs to be stored beyond that date, and nothing needs to be kept in
sync. The widget derives its display from the same single value.

Everything else — appointments, kick sessions, weight entries, notes — is a user-owned
log that the app never derives anything from.

## Architecture

Single Gradle module `:app`. Kotlin, Jetpack Compose, Material 3.

- `compileSdk`/`targetSdk` 35, `minSdk` 26
- AGP 8.7.3, Kotlin 2.0.21, Gradle 8.14, JDK 17
- No dependency injection framework, no navigation library, no database

Layering is three thin packages:

| Package | Responsibility | Depends on |
|---|---|---|
| `domain` | Pure date/derivation functions and week content model | nothing (pure Kotlin) |
| `data` | `AppState` model, DataStore persistence, repository | `domain` |
| `ui` | Compose screens and theme | `domain`, `data` |
| `widget` | Glance widget | `domain`, `data` |

`domain` has no Android dependencies so it is unit-testable on the JVM with no
instrumentation or Robolectric.

## Persistence

The entire app state is one `@Serializable` tree persisted as a single JSON file via
`DataStore<AppState>` with a custom `Serializer` backed by `kotlinx.serialization`.

```kotlin
@Serializable
data class AppState(
    val dueDate: LocalDate? = null,
    val babyName: String? = null,
    val appointments: List<Appointment> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    val notes: List<Note> = emptyList(),
    val kickSessions: List<KickSession> = emptyList(),
)
```

`dueDate == null` is the app's only real state machine: null means show onboarding,
non-null means show the app.

**Why not Room.** Four small collections, no queries, no joins, no relational access
patterns, and a realistic ceiling of a few hundred rows across nine months. A single
JSON document read into memory is simpler than four entities, four DAOs, a database
class, KSP, and a migration story — and DataStore hands the UI a `Flow<AppState>`
without any of it.

**Known ceiling.** Every write serialises and rewrites the whole file, and there is no
way to query a subset. At a few hundred rows this costs nothing measurable. If the
data ever grows past a few thousand rows, or gains a query that shouldn't load
everything, move to Room. Marked in code with a `ponytail:` comment.

**Durability.** DataStore writes atomically (write to temp, fsync, rename), so a crash
mid-write cannot corrupt the file. A corrupt or unreadable file must not crash the app
on launch; the serializer's `readFrom` catches `SerializationException` and throws
`CorruptionException`, and DataStore is configured with a
`ReplaceFileCorruptionHandler` that falls back to `AppState()`. This is the one place
data loss is possible, and it is preferred over an unlaunchable app.

Dates are stored as ISO-8601 strings (`LocalDate.toString()`), times as epoch millis.
`java.time` is available on `minSdk` 26 without desugaring.

## Domain functions

All pure, all in `domain/Pregnancy.kt`, all taking `today` as a parameter rather than
reading the clock, so tests need no clock abstraction:

- `gestationalDay(dueDate, today): Int` — days since conception-adjusted start, where
  day 0 is 280 days before the due date
- `week(dueDate, today): Int` — completed weeks, 0..42+
- `dayOfWeek(dueDate, today): Int` — 0..6 within the current week
- `daysToGo(dueDate, today): Int` — may be negative when overdue
- `trimester(week): Int` — 1 (weeks 0–12), 2 (13–27), 3 (28+)
- `progress(dueDate, today): Float` — 0f..1f, clamped

Standard obstetric convention: gestational age is measured from the last menstrual
period, and a term pregnancy is 280 days. The due date is therefore LMP + 280 days,
and gestational day = 280 − daysToGo.

### Edge cases the tests must cover

- Today == due date → week 40, 0 days to go
- Overdue → week > 40, negative days to go, `progress` clamped to 1f
- Very early (day 0/1) → week 0, `progress` clamped to 0f
- A due date far in the future → week 0, not negative
- Week boundaries → day 6 → day 7 rolls the week exactly once
- `trimester` boundaries at weeks 12/13 and 27/28

## Week content

`res/raw/weeks.json` — 41 entries (weeks 0–40), each:

```json
{ "week": 22, "size": "Papaya", "lengthCm": 27.8, "weightG": 430,
  "blurb": "..." }
```

Bundled in the APK, parsed once and cached. Offline permanently, no API, no key.

Length figures follow the standard convention: crown-rump length up to week 20 and
crown-heel length from week 21, which is why the number appears to jump at that point.
The UI states which measurement it is showing.

Each week also carries an optional `emoji`, set only where one genuinely depicts the
object named — about two thirds of them. A wrong picture is worse than no picture, and
shipping 41 drawings for the rest is not worth an APK, so the **Sizes screen draws the
measurement instead** and the emoji is a garnish on top of it.

Lengths below a centimetre are rendered in millimetres. `"%.1f cm"` turns every early
week into "0.0 cm", which reads as no baby at all.

Sizes and figures are population averages for orientation only. The app shows a
permanent, non-dismissible line on the Now screen: *"General information only — not
medical advice. Talk to your midwife or doctor about your pregnancy."* This is not
negotiable regardless of the app being personal.

## Baby details

`AppState` carries what you learn as you go, all optional and all editable from one
sheet reached by tapping the name on the Now screen:

- `babyName` — the chosen name
- `babyNickname` — what you call the bump before there is a name; kept separate because
  they are different facts that arrive at different times and one does not replace the
  other
- `babySex` — `UNKNOWN | GIRL | BOY | SURPRISE`. `SURPRISE` is deliberately distinct
  from `UNKNOWN`: one is a decision, the other is a gap, and collapsing them would make
  the app keep asking a question already answered
- the due date itself, because an early estimate is routinely corrected at the dating
  scan and there was previously no way to change it after onboarding

Every field is defaulted, so a saved `AppState` written before these existed still
decodes — asserted by a test that parses the exact JSON shape already on disk. Getting
this wrong would hand every installed copy to the corruption handler and wipe it.

## Care milestones

`res/raw/milestones.json` — the typical NHS antenatal pathway (England, NICE NG201) as
20 entries, each a gestational **week window** rather than a date:

```json
{ "fromWeek": 11, "toWeek": 14, "title": "Dating scan",
  "detail": "...", "kind": "SCAN" }
```

A milestone is a fact about pregnancies in general, not about this one, so it is stored
as weeks and resolved against the due date by `MilestoneTable.forPregnancy(dueDate)`.
Week `n` spans gestational days `7n`..`7n+6`, so a window runs from the first day of
`fromWeek` to the last day of `toWeek`.

Nothing is persisted and nothing derives from a milestone. It becomes real data only
when the user turns it into an `Appointment` — which is why the calendar's milestone
rows carry an "Add to my appointments" action that opens the existing `AddEntrySheet`
prefilled. Suggested and booked stay visibly separate.

`kind` is `SCAN | APPOINTMENT | TEST | CLASS | ADMIN`, used purely to colour dots from
the Moon supportive hues. A test asserts every kind is used, so the calendar legend can
never advertise a colour the user will never see.

**This is the highest-risk content in the app.** A schedule of appointments reads as
instruction in a way that "your baby is the size of a papaya" does not, so every
milestone view carries *"Typical NHS schedule — your midwife will confirm your actual
dates"* on top of the standard disclaimer.

## UI

Four destinations held in a `rememberSaveable` index behind a `NavigationBar`. No
navigation library — there are no arguments, no deep links and no back stack to model.

**Onboarding** (shown whenever `dueDate == null`): pick a due date, or enter the LMP
date and let the app add 280 days. Uses the M3 `DatePicker`.

**Now** — the primary screen:
- The baby's name (or bump name), tappable to edit all the details
- Week and day ("Week 22 · Day 3"), trimester, and the sex once known
- Size comparison with length and weight
- Countdown ("126 days to go") and a progress bar
- This week's blurb
- Next upcoming appointment, if any
- The next care milestone ahead of you
- The medical disclaimer

**Calendar**:
- Month grid, Monday-first, arrows to change month and a "Today" reset
- Each day cell carries up to four 4dp dots: milestone kinds, a saved appointment, and
  any weight or note logged that day
- Today is outlined, the due date is filled with `primaryContainer`, selection is filled
  with `primary`
- Tapping a day expands its detail underneath: gestational age, saved appointments, and
  any milestone windows covering it

**Sizes** — reached by tapping the size card on Now, not a fifth tab:
- Every measured week, 3 to 40, opening on the week you are in and marked "You are here"
- Emoji where one exists, and for every week a measured bar with end caps, so it reads
  as a length rather than a progress bar
- Two scales: *To scale* draws every week against week 40, so scrolling shows the growth;
  *Actual size* draws real centimetres using the display's reported `xdpi`. That figure
  is approximate on many devices and the later weeks run off the edge, so the screen
  says both rather than claiming precision it does not have

**Log**:
- Appointments — title, date/time, optional note; upcoming sorted first, past collapsed
- Weight — entries plus a simple line chart drawn with Compose `Canvas` (no chart
  library for one sparkline)
- Notes — free text tagged with the week it was written in

Adding uses a single `ModalBottomSheet` whose content switches on the entry type.

**Kicks**:
- A large tap target that records a kick, a running count and session elapsed time
- Sessions saved to history with start time, count and duration
- No target-count logic or clinical interpretation — it counts and times, nothing more

### Deletion

Every logged item can be deleted via long-press, with an undo snackbar. Deletes are
immediate in state; the snackbar restores from a held copy.

## Theme — Moon Design System v1

Extracted from the user's Figma copy (`NzdAux1pg8cyj4fsex8IXC`). Moon is token-first:
zero paint styles, one `Colors` variable collection with Light and Dark modes, 54 text
styles, all DM Sans.

The **token layer** is ported — colours, type ramp, radii, spacing — into `MoonTheme`,
which wraps `MaterialTheme` so every M3 component picks it up from one file.

Moon's ~40 Figma components (Accordion, Carousel, Combobox, Table, Pagination, …) are
**not** ported. M3 has equivalents that render in Moon's colours for free. Porting the
component library is a separate project, not a prerequisite for this one.

### Colour mapping

| Moon | Light | Dark | M3 role |
|---|---|---|---|
| piccolo | `#4E46B4` | `#4E46B4` | `primary` |
| goten | `#FFFFFF` | `#FFFFFF` | `onPrimary` |
| jiren | `#4E46B4` 12% | `#4E46B4` 32% | `primaryContainer` |
| hit | `#40A69F` | `#40A69F` | `secondary` |
| goku | `#F5F5F5` | `#0B0B0B` | `background` |
| gohan | `#FFFFFF` | `#1F1F1F` | `surface` |
| bulma | `#000000` | `#FFFFFF` | `onBackground` / `onSurface` |
| trunks | `#595D62` | **`#A3A8AE`** | `onSurfaceVariant` |
| beerus | `#E2E2E2` | `#444444` | `outlineVariant` |
| dodoria | `#D33030` | `#D33030` | `error` |

Supportive hues (roshi green, krillin amber, whis blue, chichi pink, cell mint, …) and
the `heles` hover overlay have no M3 role, so they are exposed through a
`LocalMoonExtras` CompositionLocal.

**Deliberate deviation from Moon.** Moon defines `trunks` (secondary text) as
`#595D62` in *both* modes. On Moon's own dark background (`#0B0B0B`) that measures
about 3.4:1 — below the WCAG AA 4.5:1 minimum for body text. The dark scheme uses
`#A3A8AE` instead (~9:1). Contrast is not something to simplify away, and this is the
only place the port knowingly departs from the source.

### Type

DM Sans bundled as static TTFs (Regular/Medium/Bold) in `res/font`. Google Fonts
downloadable fonts would add a Play Services dependency and a network dependency at
first paint for no benefit in a sideloaded app.

Nine of Moon's 54 styles map to M3 `Typography`:

| M3 slot | Moon | Size/LH/Tracking |
|---|---|---|
| `displaySmall` | UI 4xl [S] | 40 / 48 / −0.5 |
| `headlineMedium` | UI 3xl [S] | 32 / 40 / −0.5 |
| `headlineSmall` | UI 2xl [S] | 24 / 32 / 0 |
| `titleLarge` | UI xl [S] | 20 / 32 / 0 |
| `titleMedium` | UI md [S] | 16 / 24 / 0 |
| `bodyLarge` | Paragraph md [R] | 16 / 24 / 0 |
| `bodyMedium` | Paragraph sm [R] | 14 / 20 / 0 |
| `labelLarge` | UI sm [S] | 14 / 24 / 0 |
| `labelSmall` | UI 2xs [S] | 10 / 16 / 0 |

### Shape and spacing

Moon's radii (4 / 8 / 12 / 16 / 9999) map directly to M3 `Shapes`
`extraSmall`→`extraLarge`, with the pill exposed as `MoonShapes.pill`.

Spacing `p-1`..`p-12` (4…48, step 4) becomes a `Spacing` object used for all padding
and gaps.

## Widget

Glance `GlanceAppWidget`, showing `Week 22 · 🥭 Papaya · 126 days to go` plus a progress
bar, tappable to open the app. Resizable; the smallest size drops to week and
countdown only.

It reads the same DataStore and recomputes from `dueDate`, so it holds no state of its
own and can never disagree with the app.

**Refresh.** The displayed value changes exactly once a day, at midnight.

1. The app calls `update()` on the widget whenever `dueDate` changes.
2. The receiver additionally listens for the system's `ACTION_DATE_CHANGED` broadcast,
   which fires at midnight — the platform already does the scheduling.
3. `updatePeriodMillis` is set as a backstop.

If `ACTION_DATE_CHANGED` proves unreliable when registered from the manifest on modern
API levels, the fallback is a daily `PeriodicWorkRequest`. That trade is deliberately
deferred until observed rather than assumed, because it costs a dependency and a
scheduler for something the platform may already provide.

## Testing

JVM unit tests over `domain`, covering every edge case listed under Domain functions,
the week-content lookup (in range, clamped above 40, clamped below 0), and milestone
window arithmetic (inclusive bounds, ordering, `on`/`nextFrom` behaviour). Both bundled
JSON files are parsed from `src/main/res/raw` by a test, so malformed content fails the
build rather than the app.

The domain functions are the only non-trivial logic in the app; the rest is Compose
layout and a serializer. No instrumentation tests, no UI tests, no fixtures, no mocks.

## Out of scope

Accounts, cloud sync, partner sharing, multi-pregnancy, contraction timer, push
notifications, Play Store metadata, widget configuration UI, dependency injection,
navigation library, chart library, and Moon's component library. Each is easy to add
later against this structure; none is needed to use the app.
