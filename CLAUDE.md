# Bump — project conventions

Offline Android pregnancy tracker. Personal app, sideloaded, no backend.

## Non-negotiables

- **No `INTERNET` permission.** The app is offline by design. Anything that would need
  the network is a design change to discuss, not an implementation detail.
- **The medical disclaimer stays.** Permanent and non-dismissible on the Now screen. The
  app shows population averages; it must never read as clinical advice.
- **Care milestones are suggestions, never instructions.** The bundled NHS schedule is
  typical timings only. Every milestone view says so, and a milestone only becomes real
  data when the user explicitly turns it into an `Appointment`.
- **Kick counting stays uninterpreted.** Count and time only — no target counts, no
  "normal/abnormal" judgement.
- **Contrast is not negotiable.** Body text meets WCAG AA 4.5:1 in both themes. Where a
  token from the design doc fails that as text it is darkened or lightened along its own
  hue and the doc value kept for dots and rules only; see `Color.kt`.

## Architecture

- `domain` is pure Kotlin — no Android imports, ever. That is what keeps it testable on
  the JVM with no Robolectric. Functions take `today: LocalDate` rather than reading the
  clock.
- The due date is the only derived-from state. Never store week, trimester or countdown;
  compute them.
- All state lives in one `AppState` document in DataStore. Add a field, not a database.
- Widget and app read the same store, so the widget holds no state of its own.

## Deliberately absent

No Hilt/Koin, no Navigation library, no Room, no chart library. Each was considered and
rejected in the spec. Adding one back is a decision worth making explicitly, not a
default.

## Style

- Every logged item gets a stable `id` and an `updatedAt`.
- Mark deliberate simplifications that have a real ceiling with a `ponytail:` comment
  naming the ceiling and the upgrade path.
- Bundled reference content (`weeks.json`, `milestones.json`) lives in `res/raw`, is
  parsed once through `Bump`, and is validated by a JVM test that reads the real file.
- One drawn mark per week lives in `res/drawable/week_NN.xml` (3..40), looked up by
  number in `ui/components/WeekMark.kt`. A JVM test checks the set is complete.
- Tests cover `domain` and the bundled content. Compose layout is not unit tested.
