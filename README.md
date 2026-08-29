# Bump

An offline Android pregnancy tracker. Week-by-week size and development info, a
countdown, appointments, kick counting, weight and notes, plus a homescreen widget.

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
| `domain` | Pure date arithmetic and the week content model. No Android imports. |
| `data`   | `AppState`, DataStore persistence, repository. |
| `ui`     | Compose screens and the Moon theme. |
| `widget` | The Glance homescreen widget. |

## Design system

Themed with [Moon Design System v1](https://www.figma.com/community), ported at the
**token** layer only — colours, DM Sans type ramp, radii, spacing — into `MoonTheme`,
which wraps `MaterialTheme` so M3 components pick it up from one file. Moon's Figma
component library is not ported.

One deliberate deviation: Moon defines its secondary text colour `trunks` (`#595D62`)
identically in light and dark. On its own dark background that is ~3.4:1, under the WCAG
AA floor for body text, so the dark scheme lightens it to `#A3A8AE`.

## Build

```sh
./gradlew test              # unit tests
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # release APK, signed with the debug key for sideloading
```

APKs land in `app/build/outputs/apk/`.

## Tests

JVM unit tests over `domain` — gestational arithmetic and its edge cases (due date,
overdue, pre-term, week rollover, trimester boundaries), the week-table lookup, and a
parse of the real bundled `weeks.json`. No instrumentation tests.

## A note on the content

Sizes, lengths and weights are population averages for orientation only. Lengths are
crown-rump to week 20 and crown-heel from week 21, which is why the figure jumps there.
The app is general information, not medical advice.

## Docs

`docs/superpowers/specs/2026-08-29-bump-pregnancy-tracker-design.md` — the full design
spec, including what was deliberately left out.
