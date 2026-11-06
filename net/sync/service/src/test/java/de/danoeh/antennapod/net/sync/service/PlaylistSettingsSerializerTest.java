package de.danoeh.antennapod.net.sync.service;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.model.feed.PlaylistFeedSettings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PlaylistSettingsSerializerTest {

    @Test
    public void testRoundTrip() throws JSONException {
        Playlist playlist = new Playlist(0, "Test");
        PlaylistFeedSettings settings = new PlaylistFeedSettings(3L);
        settings.setFilterUnplayed(true);
        settings.setFilterDownloaded(false);
        settings.setMaxAgeDays(14);
        playlist.getFeedSettings().add(settings);

        Map<Long, String> feedUrlsById = new HashMap<>();
        feedUrlsById.put(3L, "https://example.com/feed.xml");

        String payload = PlaylistSettingsSerializer.serialize(
                Collections.singletonList(playlist), feedUrlsById);

        Map<String, Long> feedIdsByUrl = new HashMap<>();
        feedIdsByUrl.put("https://example.com/feed.xml", 3L);

        List<Playlist> result = PlaylistSettingsSerializer.deserialize(payload, feedIdsByUrl);
        assertEquals(1, result.size());
        assertEquals("Test", result.get(0).getName());
        assertEquals(1, result.get(0).getFeedSettings().size());
        PlaylistFeedSettings resultSettings = result.get(0).getFeedSettings().get(0);
        assertEquals(3L, resultSettings.getFeedId());
        assertTrue(resultSettings.isFilterUnplayed());
        assertFalse(resultSettings.isFilterDownloaded());
        assertEquals(14, resultSettings.getMaxAgeDays());
    }

    @Test
    public void testSkipsLocalFolderFeeds() throws JSONException {
        Playlist playlist = new Playlist(0, "Local");
        PlaylistFeedSettings localSettings = new PlaylistFeedSettings(1L);
        localSettings.setFilterUnplayed(true);
        playlist.getFeedSettings().add(localSettings);

        Map<Long, String> feedUrlsById = new HashMap<>();
        feedUrlsById.put(1L, Feed.PREFIX_LOCAL_FOLDER + "/music");

        String payload = PlaylistSettingsSerializer.serialize(
                Collections.singletonList(playlist), feedUrlsById);

        Map<String, Long> feedIdsByUrl = new HashMap<>();
        List<Playlist> result = PlaylistSettingsSerializer.deserialize(payload, feedIdsByUrl);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getFeedSettings().isEmpty());
    }

    @Test
    public void testMultiplePlaylists() throws JSONException {
        List<Playlist> playlists = new ArrayList<>();
        Playlist p1 = new Playlist(0, "A");
        p1.getFeedSettings().add(new PlaylistFeedSettings(1L));
        playlists.add(p1);
        Playlist p2 = new Playlist(0, "B");
        p2.getFeedSettings().add(new PlaylistFeedSettings(2L));
        playlists.add(p2);

        Map<Long, String> feedUrlsById = new HashMap<>();
        feedUrlsById.put(1L, "https://a");
        feedUrlsById.put(2L, "https://b");

        String payload = PlaylistSettingsSerializer.serialize(playlists, feedUrlsById);

        Map<String, Long> feedIdsByUrl = new HashMap<>();
        feedIdsByUrl.put("https://a", 1L);
        feedIdsByUrl.put("https://b", 2L);

        List<Playlist> result = PlaylistSettingsSerializer.deserialize(payload, feedIdsByUrl);
        assertEquals(2, result.size());
        assertEquals("A", result.get(0).getName());
        assertEquals("B", result.get(1).getName());
    }
}
