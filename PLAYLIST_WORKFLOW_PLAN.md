# Playlist Workflow and Retention Plan

Follow-up to `PLAYLIST_PER_PODCAST_PLAN.md`. Improves playlist editing, uses playlist criteria to
drive downloads, and expires old downloaded media without breaking other playlists.

## Behavior

### Playlist editor and podcast selection

- Replace the current modal podcast picker with a full-screen podcast selector launched from the
  playlist editor.
- Show podcasts not already in the playlist. Each row shows podcast title, latest episode's
  publication date, and count of other playlists containing that podcast.
- Selecting a podcast opens its existing per-podcast options editor. Keep changes staged until
  the playlist is saved.
- Keep a prominent + floating action button matching the Subscriptions screen's visual style,
  fixed at the bottom of the playlist editor. The podcast list scrolls independently, so the
  button stays visible with long lists.
- Do not change the Subscriptions screen FAB destination; it continues to open subscription
  discovery and add-feed tools.
- In an existing playlist, show its name as read-only. An explicit edit-name action opens a
  separate name-entry step with confirmation. Stage the confirmed name until the playlist is
  saved. Playlist creation still requires entering a name.

### Playlist-driven downloads

- Treat undownloaded episodes matching playlist podcast membership and its applicable
  per-podcast criteria as automatic-download candidates, subject to existing network, power, and
  episode-cache constraints.
- The "downloaded only" option remains a display filter; it must not prevent an undownloaded
  episode from becoming a download candidate. Once downloaded, that episode can appear in a
  downloaded-only playlist.
- Keep existing feed auto-download and queue candidate behavior intact.

### Age-based media retention

- Keep the existing maximum-age rule for hiding old episodes from playlist results.
- After a successful episode download, check downloaded episodes against all playlist criteria.
  Delete downloaded media only when the episode is older than its applicable playlist age limit
  and no other playlist currently includes it.
- Preserve episode metadata in the feed. If another playlist includes the episode, retain its
  downloaded media.

## Implementation areas

- `app/src/main/java/de/danoeh/antennapod/ui/screen/playlist/EditPlaylistDialog.java` and
  `app/src/main/res/layout/edit_playlist.xml`: staged editing, separate name step, scrollable
  podcast list, and fixed + FAB.
- Add a full-screen playlist podcast-selection screen using the current picker data:
  `DBReader.getFeedLatestEpisodeDates()` and `DBReader.getFeedPlaylistCounts()`.
- `net/download/service/.../AutomaticDownloadAlgorithm.java`: include playlist-matching
  undownloaded episodes without treating the downloaded-only display filter as a prerequisite.
- `net/download/service/.../EpisodeDownloadWorker.java` or a dedicated background cleanup path:
  run age-based playlist media cleanup after successful downloads.
- `storage/database/.../PodDBAdapter.java`: reuse or extend playlist matching logic for finding
  episodes that remain included by any playlist before deleting media.

## Verification

- Verify editor + FAB remains visible while adding at least eight podcasts and the list scrolls.
- Verify selector rows show latest publication date and other-playlist count; selecting podcasts
  and cancelling/saving preserves staging behavior.
- Verify existing playlist name cannot be overwritten in-place and confirmed edits persist only
  when the playlist is saved.
- Test playlist download candidates, including downloaded-only display filtering and age limits.
- Test that aged downloaded media is deleted after a successful download when no playlist still
  includes the episode, and retained when another playlist includes it.
- Compile with `./gradlew :app:assembleDebug` and run relevant database and download-service tests.

## Decision to confirm before implementation

- Whether creating a playlist or adding a podcast should immediately download already-published,
  matching backlog, or only make matching episodes from subsequent feed updates eligible.

## Status

- [x] Replace modal podcast picker with full-screen selector from playlist editor: added `SelectPlaylistFeedsFragment` launched from `EditPlaylistDialog`.
- [x] Show existing playlist name as read-only with a separate edit-name step; name change staged until the playlist is saved.
- [x] Use playlists as auto-download sources: `AutomaticDownloadAlgorithm` now includes playlist-matching undownloaded episodes (ignoring downloaded-only as a display filter).
- [x] Expire old downloaded media after successful downloads: `PlaylistMediaCleanup` removes media for playlist episodes no longer included by any playlist, preserving queue/favorites.
