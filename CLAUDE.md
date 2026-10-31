# Bump — project conventions

Offline-first Android pregnancy tracker, plus a small sync backend so two phones share
one record. Personal app, sideloaded, two users.

## Non-negotiables

- **Offline-first, and network only for sync.** Every write lands in DataStore and is
  complete without a connection; sync only reconciles. The `INTERNET` permission exists
  for exactly one thing — the user's own sync backend in `backend/`. No analytics, no
  crash reporting, no third-party SDK, no ads. Until a space is paired the app makes no
  request at all, and it must stay fully usable if sync is never enabled. The one second use is user-triggered: the Names screen fetches a batch of first names from
  the keyless public randomuser.me API on "More names" or when the deck runs out (limited to the chosen origin when a filter is set, via randomuser's `nat` codes), never in the
  background, cached in `filesDir/fetched_names.json` and not synced. The other is the
  user-tapped "Check for updates" in Settings: it reads the public GitHub releases of
  `FinnKennedy2000/bump` via api.github.com and downloads `bump.apk` (tag `build-<versionCode>`,
  cut by `scripts/release.sh`), then hands it to the system installer; that is what the
  `REQUEST_INSTALL_PACKAGES` permission is for. Never automatic. Any *other*
  network use is a design change to discuss, not an implementation detail.
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
  The one exception is photo bytes: files in `filesDir/photos/<sha256>.jpg`
  (`data/PhotoFiles.kt`), with only the `Photo` metadata in `AppState`. Named by hash, a
  file never changes, so sync copies it (`photoTransfers` in `Merge.kt`) and never merges.
- Widget and app read the same store, so the widget holds no state of its own.
- Sync is client-merged: the server stores the document as an opaque blob and owns only a
  version number. Reconciliation rules live once, in `data/Merge.kt`, pure and JVM-tested.
  Never add a second copy of them to the backend.
- Deletes are tombstones (`Logged.deleted`), never removals — a removal cannot survive a
  round trip and the other phone would push the item back. `BumpRepository.state` strips
  them so no screen has to; `rawState` is for sync only.

## Deliberately absent

No Hilt/Koin, no Navigation library, no Room, no chart library, no image loader (Coil).
On the network side: no OkHttp/Ktor/Retrofit (a handful of endpoints do not need one), no
WorkManager (sync runs on resume and after writes), no push service or Firebase, no user
accounts (two people who already trust each other share one code).
The one background job is `notify/NameNotifications.kt`: while paired, a platform
JobScheduler job about every 15 minutes syncs and notifies about the partner's name
votes. It stays blind — a match or a partner-typed name may be named, list likes are only
counted — and it is cancelled on unpair. Anything else wanting background work joins it
there rather than adding a second mechanism.
Each was considered and rejected. Adding one back is a decision worth making explicitly, not a default.

## Style

- Every logged item gets a stable `id` and an `updatedAt`. Anything that mutates an item
  refreshes `updatedAt`, or sync will settle the conflict the wrong way.
- Mark deliberate simplifications that have a real ceiling with a `ponytail:` comment
  naming the ceiling and the upgrade path.
- Bundled reference content (`weeks.json`, `milestones.json`) lives in `res/raw`, is
  parsed once through `Bump`, and is validated by a JVM test that reads the real file.
- One drawn mark per week lives in `res/drawable/week_NN.xml` (3..40), looked up by
  number in `ui/components/WeekMark.kt`. Marks that go dark-on-dark have a
  `drawable-night/` variant. A JVM test checks the set is complete.
- Tests cover `domain`, the merge, and the bundled content. Compose layout is not unit
  tested. Backend tests need a real Postgres (`DATABASE_URL=...`); see `backend/README.md`.
