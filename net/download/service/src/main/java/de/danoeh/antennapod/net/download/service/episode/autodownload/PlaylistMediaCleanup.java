package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PlaylistMatcher;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;

/**
 * Removes downloaded media for episodes that are no longer included by any playlist.
 * Episodes in the queue, favorites, and the currently loaded media are kept.
 * Throttled: runs at most once per minute even when many downloads complete in a row.
 */
public class PlaylistMediaCleanup {

    private static final String TAG = "PlaylistMediaCleanup";
    private static final long MIN_INTERVAL_MS = 60_000;

    private static volatile long lastRun = 0;

    public static void performCleanup(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastRun < MIN_INTERVAL_MS) {
            return;
        }
        lastRun = now;

        List<Playlist> playlists = DBReader.getPlaylists();
        Set<Long> playlistFeedIds = PlaylistMatcher.getPlaylistFeedIds(playlists);
        if (playlistFeedIds.isEmpty()) {
            return;
        }
        List<FeedItem> downloadedItems = DBReader.getEpisodes(0, Integer.MAX_VALUE,
                new FeedItemFilter(FeedItemFilter.DOWNLOADED), SortOrder.DATE_NEW_OLD);
        List<FeedItem> toDelete = new ArrayList<>();
        for (FeedItem item : downloadedItems) {
            if (!playlistFeedIds.contains(item.getFeedId())) {
                continue;
            }
            if (item.isTagged(FeedItem.TAG_QUEUE) || item.isTagged(FeedItem.TAG_FAVORITE)) {
                continue;
            }
            FeedMedia media = item.getMedia();
            if (media != null
                    && PlaybackPreferences.getCurrentlyPlayingMediaType() == FeedMedia.PLAYABLE_TYPE_FEEDMEDIA
                    && PlaybackPreferences.getCurrentlyPlayingFeedMediaId() == media.getId()) {
                continue;
            }
            if (!PlaylistMatcher.isIncludedInAnyPlaylist(item, playlists)) {
                toDelete.add(item);
            }
        }
        for (FeedItem item : toDelete) {
            try {
                DBWriter.deleteFeedMediaOfItem(context, item.getMedia()).get();
            } catch (InterruptedException e) {
                Log.e(TAG, Log.getStackTraceString(e));
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
    }
}
