# Photos — design

One gallery of pictures — bump photos, scans, keepsakes — shared between the two paired
phones. Each photo has a date (so a pregnancy week, derived, never stored) and an optional
caption.

## Data

- `Photo : Logged` in `AppState.photos`: `id`, `date`, `caption`, `blob`, `updatedAt`,
  `deleted`. Merged by the existing per-item last-write-wins in `data/Merge.kt`; deletes
  are tombstones.
- `blob` is the lowercase hex sha256 of the image bytes. The bytes live outside the
  DataStore document, in `filesDir/photos/<blob>.jpg`. A blob is immutable, so it never
  needs merging — only copying.

## Import

- System Photo Picker (`ActivityResultContracts.PickVisualMedia`, images only). No storage
  permission, no new dependency.
- Decoded with `BitmapFactory` (`inSampleSize` to stay near a 2048 px long edge), rotated
  by EXIF orientation (`android.media.ExifInterface`), re-encoded JPEG q85. Re-encoding
  strips all EXIF, including GPS.
- Skipped: in-app camera. Take it with the camera app and pick it.

## Sync

Backend, all Bearer-authorised like `/state`:

| Method | Path                          | Notes                                              |
|--------|-------------------------------|----------------------------------------------------|
| GET    | `/spaces/{id}/photos`         | `{"photos": [sha, ...]}`                           |
| PUT    | `/spaces/{id}/photos/{sha}`   | raw bytes; 400 if sha256(body) ≠ sha; 413 > 8 MB; idempotent |
| GET    | `/spaces/{id}/photos/{sha}`   | the bytes, `image/jpeg`; 404 if absent             |

Table `photos(space_id uuid references spaces on delete cascade, sha256 text, bytes bytea,
primary key (space_id, sha256))`.

Client: after the state round, `Syncer` runs a photo pass over the live (non-tombstoned)
photos of the merged document — upload blobs held locally that the server lacks, download
blobs the server has that this phone lacks. Which blobs to move is a pure function
(`photoTransfers`) tested on the JVM. Failures surface on the sync status row like any
other sync failure. A photo whose blob has not arrived yet shows a placeholder.

## UI

- Log: photo rows (thumbnail + caption) appear under All. The Photos chip swaps the list
  for the gallery — a lazy grid grouped by week, newest first. Add under Photos offers
  Photo (pick → date + caption → save).
- Week sheet: that week's photos as a strip.
- Viewer: full-screen image, caption and date, edit caption/date, delete with undo.
- Thumbnails decoded off the main thread into a small `LruCache`. No Coil.

## Deliberate limits

- Photos are excluded from Android cloud backup: the 25 MB quota would make the whole
  backup fail. Included in device-to-device transfer. The sync server is the backup.
- Deleting a photo tombstones its metadata but keeps the bytes (local and server) so undo
  works. `ponytail:` — sweep unreferenced blobs if storage ever matters.

## Tests

- JVM: photos merge and tombstone like other items; `photoTransfers` picks the right
  uploads and downloads and ignores tombstoned photos.
- Backend (real Postgres): round trip, hash mismatch rejected, idempotent re-upload,
  size cap, auth required, spaces isolated.
