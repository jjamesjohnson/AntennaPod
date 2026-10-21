# Smart Playlist Support Plan

## Goal

Add user-created smart playlists built from selected podcasts and optional episode criteria. Playlists remain local in the first release; cross-device sync is deferred.

## Playlist behavior

- Each playlist has a name, one or more associated podcasts, and optional criteria: unplayed only, downloaded only, and a freshness limit in days.
- A playlist includes episodes from any associated podcast that satisfy every enabled criterion. With no status or age criteria enabled, it shows all matching episodes from associated podcasts.
- Freshness limit means “released within the last X days.” Exclude episodes whose publication date is older than the cutoff, so stale daily-news episodes age out. Evaluate cutoff against current time when loading results, not by storing a fixed date.
- Use newest-release-first ordering by default. Playlists are dynamic: membership changes as episode state, downloads, and publication age change.

## Implementation phases

### 1. Persistence and model

- Add a plain model for playlist definition and criteria, following `:model` conventions.
- Add SQLite playlist and playlist-to-podcast association tables in `:storage:database`; use podcast/feed IDs locally.
- Bump the database version and add an upgrade path in `DBUpgrader`.
- Add asynchronous playlist writes through `DBWriter` and playlist reads through `DBReader`.
- Remove associations when a podcast is permanently deleted. An empty podcast selection matches no episodes.

### 2. Matching queries

- Add a paginated reader query and result count for playlist episodes; avoid loading all episodes before filtering.
- Build parameterized SQL using the existing feed-item query patterns. Constrain by associated podcast IDs, then apply enabled unplayed, downloaded, and publication-age criteria.
- Preserve existing downloaded semantics for local feeds. Use the same cutoff calculation for result and count queries.

### 3. UI

- Add one Playlists destination with a manager for creating, editing, and deleting playlist definitions.
- The editor provides name entry, podcast selection, unplayed/downloaded toggles, and an optional days limit.
- Add playlist detail screen reusing existing episode-list rendering, pagination, sorting, and episode actions.
- Add translatable user-visible strings only to `ui/i18n/src/main/res/values/strings.xml`.

### 4. Playback integration

- Add a Play Playlist action that replaces the persisted playback queue with current playlist matches in playlist order, then starts the first episode.
- Perform replacement as one database operation and publish the appropriate queue update so the queue UI and playback service stay consistent.
- Show the existing empty-list state when no episodes currently match.

### 5. Verification

- Add database tests for migration, selected-podcast matching, combined criteria, no criteria, empty podcast selection, deleted associations, and age cutoff boundaries.
- Verify stale episodes disappear after cutoff and newly eligible episodes appear without rewriting playlist data.
- Verify playlist playback replaces—not appends to—the current queue.
- Build with `./gradlew :app:assembleDebug`.

## Deferred: gPodder sync

The gPodder API documents account-scoped JSON settings that could transport playlist definitions. This repository's GPodder and Nextcloud sync implementations do not currently implement settings sync. A later phase should confirm server compatibility, add sync support, use stable podcast URLs rather than local database IDs, and define merge/conflict behavior.
