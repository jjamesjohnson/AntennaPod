# Per-Podcast Playlist Settings Plan

Follow-up to `PLAYLIST_SUPPORT_PLAN.md`. Moves the episode criteria (unplayed, downloaded,
released-within-X-days) from playlist-wide flags to per-podcast settings stored on each
playlist-feed association.

## Behavior

- Editing a playlist shows its podcast list: each row has the podcast title, a caption of its
  active options (e.g. "Unplayed · Downloaded · Within 3 days", or "All episodes"), an edit
  (pencil) icon, and a remove (trash) icon.
- A + button opens a podcast picker: one tap adds. The picker lists podcasts not already in the
  playlist; each row shows the podcast title, the release date of its latest episode
  (abbreviated local format), and the number of playlists it is already in (shown when ≥ 1).
  Already-added podcasts are hidden.
- Tapping a podcast in the picker opens a small options editor (unplayed, downloaded, maximum
  age in days); its OK records the podcast with those options. The pencil icon opens the same
  editor for an existing row.
- All row edits (add, option changes, trash) are staged in the dialog; the main dialog's OK
  persists the whole playlist in one write. Cancel discards staged changes.
- Trash stages removal only; nothing is written until OK.
- Migration: existing playlists' global flags are copied onto every podcast row of that
  playlist, so behavior after upgrade is unchanged. (Recommended and accepted.)

## Implementation

### Model (`:model`)

- New `PlaylistFeedSettings` (feedId, filterUnplayed, filterDownloaded, maxAgeDays; 0 = age off).
- `Playlist` drops its global filter fields and holds `List<PlaylistFeedSettings>` instead of feed IDs.

### Persistence (`:storage:database`)

- `PlaylistFeeds` gains `filter_unplayed`, `filter_downloaded`, `max_age_days` (default 0).
  `Playlists` keeps only id + title (pair is unreleased; global filter columns dropped from create).
- Database version 3110001 → 3110002; upgrade: 3 ALTER TABLE ADD COLUMN + one UPDATE that copies
  each playlist's global flags onto all its rows.
- `PodDBAdapter`: `setPlaylistFeeds` writes per-row options; playlist feed cursor returns feed +
  options; matching WHERE clause becomes per-feed OR groups — `(feed = A AND A's unplayed/downloaded/
  age checks) OR (feed = B AND …)`; empty settings list matches nothing. Local-feed downloaded
  semantics preserved per group.
- Picker support queries: per-feed `MAX(pubDate)`; per-feed playlist count.
- `DBReader.getPlaylists()` loads settings per row; new `getFeedLatestEpisodeDates()` and
  `getFeedPlaylistCounts()` return maps keyed by feed id. `DBWriter` API unchanged.

### UI (`:app`)

- `edit_playlist.xml`: name input, feed list (`RecyclerView`), + button (`ic_add`); global
  checkboxes and days input removed.
- New `edit_playlist_feed_row.xml`: title + options caption, trailing pencil and trash icons.
- New `add_playlist_feed_row.xml` (picker): title, latest episode line, playlist-count line.
- New `edit_playlist_feed_options.xml`: unplayed checkbox, downloaded checkbox, days input.
- `EditPlaylistDialog`: staged settings list + inner row adapter; picker is a single-choice list
  (already-added hidden); options editor for add and edit; OK persists atomically.
- `PlaylistFragment`: per-feed criteria are no longer expressible as a global `FeedItemFilter`,
  so `getFilter()` returns unfiltered and `onEventMainThread(FeedItemEvent)` performs a full reload.

### Strings (`:ui:i18n`)

- New: add podcast (button), remove from playlist, picker rows (latest episode, in N playlists),
  options caption parts (unplayed, downloaded, within N days, all episodes).
- Existing options labels reused; unused global-only strings removed.

### Tests

- `PlaylistTest` rewritten for per-feed settings: round-trip create/update/delete, per-feed
  matching for each criterion, combined criteria, pagination/ordering, empty settings matches
  nothing, feed-deletion cleanup.
- New case: two podcasts in one playlist with different options, proving independent matching.

### Verification

- `./gradlew :app:assembleDebug`, `:storage:database:testFreeDebugUnitTest`, `./gradlew checkstyle lint`.
- Commit on `feature/smart-playlists`, push — fork PR #1 re-runs Checks and produces a fresh
  `app-play-debug.apk` artifact.

## Known compromise

- The feed list inside the `AlertDialog` uses wrap-content height; very large podcast counts may
  make the dialog tall. Acceptable at this scale; revisit with a bottom sheet if needed.
