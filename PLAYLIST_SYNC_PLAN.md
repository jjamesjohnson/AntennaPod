# Playlist Sync Plan

Follow-up to `PLAYLIST_SUPPORT_PLAN.md` and `PLAYLIST_PER_PODCAST_PLAN.md`. Syncs playlist
definitions between devices through the gPodder account-scoped Settings API.

## Backend support

- gpodder.net and Malipod Selfhosted implement the Settings API
  (`GET/POST /api/2/settings/{username}/{scope}.json`, scope `account`).
- goPodder, gpodder2go, and the Nextcloud gpoddersync app do not. For those backends, playlist
  sync is skipped silently and playlists stay local-only.

## Sync model

- One account-scoped settings key `antennapod_playlists` holds the entire playlist set as a
  JSON string. One key makes uploads and downloads atomic over the whole set.
- Payload: array of playlists, each with a name and per-podcast entries keyed by feed
  download URL with the per-podcast options (unplayed, downloaded, maxAgeDays).
- Conflict handling: last-write-wins for the whole key.
  - Any local playlist change (create, update, delete, sync apply) marks the key dirty.
  - A sync run with a dirty key uploads the local set, then clears the dirty flag.
  - A sync run without a dirty key pulls the key. If the server value differs from the local
    serialization, the server set replaces the local set (server wins).
- Feed download URLs are the stable cross-device identifiers. Local-folder feeds
  (`antennapod_local:` URLs) are excluded in both directions; on apply, feed URLs that are not
  subscribed locally are skipped — they appear automatically once the feed is subscribed and
  a later sync runs.
- No timestamps exist in the Settings API, so races between two devices that both write before
  syncing resolve to whichever upload lands last. Accepted trade-off of last-write-wins.

## Implementation

### `:net:sync:service-interface`

- `ISyncService` gains `getAccountSettings()` (returns the account settings JSON object as a
  string, `null` when the backend does not support settings) and
  `uploadAccountSettings(Map<String, String>)` (no-op on unsupported backends).
- `SynchronizationQueue` gains `enqueuePlaylistChange()` — marks the playlists key dirty.
- `SynchronizationQueueStub` implements it as a no-op.

### `:net:sync:gpoddernet`

- `GpodnetService.getAccountSettings()` — `GET /api/2/settings/{username}/account.json`.
- `GpodnetService.uploadAccountSettings(Map<String, String>)` — `POST` with a
  `{"set": {...}}` body, sending the whole playlists key.

### `:net:sync:service`

- `NextcloudSyncService` — settings methods signal unsupported (null / no-op).
- `SynchronizationQueueStorage` — persists a playlists-dirty flag.
- `SynchronizationQueueImpl.enqueuePlaylistChange()` — sets the dirty flag.
- `SyncService` — new playlist step after the existing subscription and episode-action steps:
  - Backend unsupported (null settings) → skip.
  - Dirty → serialize local playlists, upload, clear dirty.
  - Not dirty → pull, and when the payload differs from the local serialization, apply it.
- Serializer helper: local `Playlist`/`PlaylistFeedSettings` plus feed URL map to payload
  JSON and back. Pure conversion, unit-testable, no Android UI dependencies.

### `:storage:database`

- `PodDBAdapter.replacePlaylists(List<Playlist>)` — transactional delete-all + re-insert.
- `DBWriter.replacePlaylists(Context, List<Playlist>)` — sync-apply path. Posts playlist
  events for created/updated/deleted playlists so the UI refreshes. Does not enqueue a sync
  change (the applied state is the server state).
- `DBWriter.createPlaylist`, `updatePlaylist`, `deletePlaylist` — user actions, they call
  `SynchronizationQueue.getInstance().enqueuePlaylistChange()`.

### Tests

- Serializer round-trip: playlists with per-podcast options survive serialize/deserialize;
  local-folder feeds are excluded; equality comparison detects changes.
- `PlaylistTest`: `replacePlaylists` replaces the set atomically; user-action writes set the
  dirty flag through the stub.
- `SynchronizationQueueStub` no-ops the new enqueue.

### Verification

- `./gradlew :app:assembleDebug`, `:storage:database:testFreeDebugUnitTest`,
  `:net:sync:service:test...`, `./gradlew checkstyle lint`.
- Commit on `feature/smart-playlists`, push — fork PR #1 re-runs Checks and produces a fresh
  `app-play-debug.apk` artifact.

## Out of scope

- Merging concurrent edits from multiple devices (requires timestamps the API lacks).
- Syncing the playback queue or per-episode playlist membership.
- Malipod's own server-side episode playlists feature stays untouched; only definitions sync.
