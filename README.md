# Bump

An offline Android pregnancy tracker. Three screens — Today, Journey, Log — covering
week-by-week size and development, a countdown, a drawn mark and to-scale measurement
for every week, the typical NHS antenatal schedule as a timeline or a month grid, the
baby's name and sex as you learn them, appointments, kick counting, weight and notes,
plus a homescreen widget.

Personal app, sideloaded. No accounts, no backend, no analytics, and no `INTERNET`
permission.

## The idea

The pregnancy is **one date**. Week, trimester, days remaining and progress are all pure
functions of the due date and today, so nothing about the pregnancy is stored beyond
that single value — and the widget derives its display from the same one, so the two can
never disagree.

Everything else (appointments, kicks, weights, notes) is a log the app reads back but
never derives anything from.

## Stack

Kotlin · Jetpack Compose · Material 3 · Glance · DataStore · kotlinx.serialization

Single `:app` module. No DI framework, no navigation library, no database, no chart
library — see the spec for why each was left out.

- minSdk 26, target/compileSdk 35
- AGP 8.7.3, Kotlin 2.0.21, Gradle 8.14, JDK 17

## Layout

| Package | What lives there |
|---|---|
| `domain` | Pure date arithmetic, the week content model and the care milestones. No Android imports. |
| `data`   | `AppState`, DataStore persistence, repository. |
| `ui`     | Compose screens and the Bump theme. |
| `widget` | The Glance homescreen widget. |

## Design system

Paper and ink. A warm off-white ground, hairline rules instead of stacked cards, clay as
the only colour that acts, and four muted hues that mean scan / appointment / test /
admin. Two families split by job: **Gambetta** (serif) for every measured figure — the
week number, the size, lengths, weights, dates — and **Satoshi** for the interface. Both
are bundled TTFs under the ITF Free Font License; the app has no `INTERNET` permission,
so no downloadable-fonts provider.

Tokens live in `ui/theme/Color.kt` and are mapped onto Material 3 in `BumpTheme`, so M3
components pick the palette up from one file. What M3 has no slot for — the hairline
tier, the micro-label tier, the four meanings — rides alongside as `BumpExtras`.

Three tokens deviate from the source design where it failed contrast as *text*: the
light micro-label (`#8A7D73` → `#736759`), the dark micro-label (`#776D64` → `#948A7F`)
and ochre used as words (`#C0871F` → `#8A5F12`). The original values are kept for dots
and rules. Body text clears WCAG AA 4.5:1 in both themes.

Each week 3–40 has one flat vector mark in `res/drawable/week_NN.xml`, generated from
the design doc and shown on Today, the timeline, the week sheet, the size chart and the
widget. There is no emoji set any more: two thirds coverage meant two thirds of the
weeks looked unfinished.

## Build

```sh
./gradlew test              # unit tests
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # release APK, signed with the debug key for sideloading
```

APKs land in `app/build/outputs/apk/`.

## Tests

JVM unit tests over `domain` — gestational arithmetic and its edge cases (due date,
overdue, pre-term, week rollover, trimester boundaries), the week-table lookup, care
milestone window arithmetic, and a parse of the real bundled `weeks.json` and
`milestones.json`. Plus a `data` test that decodes the exact `AppState` JSON shape
already on disk, so adding a field can never silently wipe a saved pregnancy. No
instrumentation tests.

## A note on the content

Sizes, lengths and weights are population averages for orientation only. Lengths are
crown-rump to week 20 and crown-heel from week 21, which is why the figure jumps there.
Every week is drawn to scale against week 40, and the week sheet will draw it at real
size on the glass if you ask — approximately, since a display's reported physical density
is only approximate, which the caption says.

Journey shows the typical NHS antenatal pathway (England, NICE NG201) as week
windows, not booked appointments. They are suggestions of what usually happens when;
your midwife confirms your actual dates. A milestone only becomes real data in the app
when you explicitly add it to your own appointments.

The app is general information, not medical advice.

## Docs

`docs/superpowers/specs/2026-08-29-bump-pregnancy-tracker-design.md` — the full design
spec, including what was deliberately left out.
