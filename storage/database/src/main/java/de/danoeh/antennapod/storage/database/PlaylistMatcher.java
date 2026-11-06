package de.danoeh.antennapod.storage.database;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.model.feed.PlaylistFeedSettings;

/**
 * Helpers for playlist-driven episode actions.
 */
public final class PlaylistMatcher {

    private PlaylistMatcher() {
    }

    public static boolean isIncludedInAnyPlaylistForAutoDownload(FeedItem item, List<Playlist> playlists) {
        for (Playlist playlist : playlists) {
            for (PlaylistFeedSettings settings : playlist.getFeedSettings()) {
                if (settings.getFeedId() != item.getFeedId()) {
                    continue;
                }
                if (matchesAutoDownloadSettings(item, settings)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the given episode currently matches at least one of the given playlists,
     * using each playlist's active filters (unplayed, downloaded, max age).
     */
    public static boolean isIncludedInAnyPlaylist(FeedItem item, List<Playlist> playlists) {
        for (Playlist playlist : playlists) {
            for (PlaylistFeedSettings settings : playlist.getFeedSettings()) {
                if (settings.getFeedId() != item.getFeedId()) {
                    continue;
                }
                if (matchesSettings(item, settings)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the episode matches a playlist's criteria for automatic download.
     * The "downloaded only" display filter is ignored so undownloaded episodes can become
     * download candidates.
     */
    public static boolean matchesAutoDownloadSettings(FeedItem item, PlaylistFeedSettings settings) {
        if (settings.isFilterUnplayed() && item.isPlayed()) {
            return false;
        }
        if (settings.hasMaxAge()) {
            long cutoff = System.currentTimeMillis() - settings.getMaxAgeDays() * 24L * 3600L * 1000L;
            if (item.getPubDate() == null || item.getPubDate().getTime() < cutoff) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesSettings(FeedItem item, PlaylistFeedSettings settings) {
        if (settings.isFilterUnplayed() && item.isPlayed()) {
            return false;
        }
        if (settings.isFilterDownloaded()
                && !item.isDownloaded()
                && (item.getFeed() == null || !item.getFeed().isLocalFeed())) {
            return false;
        }
        if (settings.hasMaxAge()) {
            long cutoff = System.currentTimeMillis() - settings.getMaxAgeDays() * 24L * 3600L * 1000L;
            if (item.getPubDate() == null || item.getPubDate().getTime() < cutoff) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns all feed IDs that are referenced by at least one playlist.
     */
    public static Set<Long> getPlaylistFeedIds(List<Playlist> playlists) {
        Set<Long> feedIds = new HashSet<>();
        for (Playlist playlist : playlists) {
            for (PlaylistFeedSettings settings : playlist.getFeedSettings()) {
                feedIds.add(settings.getFeedId());
            }
        }
        return feedIds;
    }
}