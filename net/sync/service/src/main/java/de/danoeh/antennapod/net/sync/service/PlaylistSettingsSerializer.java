package de.danoeh.antennapod.net.sync.service;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.model.feed.PlaylistFeedSettings;

/**
 * Converts playlists to and from the payload stored in the account-scoped
 * settings key of the sync server. Feed download URLs are used as stable
 * cross-device identifiers; local-folder feeds are not synced.
 */
public final class PlaylistSettingsSerializer {

    public static final String KEY = "antennapod_playlists";

    private PlaylistSettingsSerializer() {
    }

    @NonNull
    public static String serialize(@NonNull List<Playlist> playlists,
                                   @NonNull Map<Long, String> feedUrlsById) throws JSONException {
        JSONArray result = new JSONArray();
        for (Playlist playlist : playlists) {
            JSONObject playlistJson = new JSONObject();
            playlistJson.put("name", playlist.getName());
            JSONArray feeds = new JSONArray();
            for (PlaylistFeedSettings settings : playlist.getFeedSettings()) {
                String url = feedUrlsById.get(settings.getFeedId());
                if (url == null || url.startsWith(Feed.PREFIX_LOCAL_FOLDER)) {
                    continue;
                }
                JSONObject feedJson = new JSONObject();
                feedJson.put("url", url);
                feedJson.put("unplayed", settings.isFilterUnplayed());
                feedJson.put("downloaded", settings.isFilterDownloaded());
                feedJson.put("maxAgeDays", settings.getMaxAgeDays());
                feeds.put(feedJson);
            }
            playlistJson.put("feeds", feeds);
            result.put(playlistJson);
        }
        return result.toString();
    }

    @NonNull
    public static List<Playlist> deserialize(@NonNull String payload,
                                             @NonNull Map<String, Long> feedIdsByUrl) throws JSONException {
        List<Playlist> playlists = new ArrayList<>();
        JSONArray array = new JSONArray(payload);
        for (int i = 0; i < array.length(); i++) {
            JSONObject playlistJson = array.getJSONObject(i);
            Playlist playlist = new Playlist(0, playlistJson.optString("name", ""));
            JSONArray feeds = playlistJson.optJSONArray("feeds");
            if (feeds != null) {
                for (int j = 0; j < feeds.length(); j++) {
                    JSONObject feedJson = feeds.getJSONObject(j);
                    Long feedId = feedIdsByUrl.get(feedJson.optString("url"));
                    if (feedId == null) {
                        continue;
                    }
                    PlaylistFeedSettings settings = new PlaylistFeedSettings(feedId);
                    settings.setFilterUnplayed(feedJson.optBoolean("unplayed", false));
                    settings.setFilterDownloaded(feedJson.optBoolean("downloaded", false));
                    settings.setMaxAgeDays(feedJson.optInt("maxAgeDays", 0));
                    playlist.getFeedSettings().add(settings);
                }
            }
            playlists.add(playlist);
        }
        return playlists;
    }
}
