package de.danoeh.antennapod.storage.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.model.feed.PlaylistFeedSettings;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PlaylistTest {

    private static final long DAY = 24L * 3600L * 1000L;

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        UserPreferences.init(context);
        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
    }

    @After
    public void tearDown() {
        PodDBAdapter.tearDownTests();
        context.getDatabasePath(PodDBAdapter.DATABASE_NAME).delete();
    }

    @Test
    public void testUpgradeFromBeforePlaylistTablesCreatesCurrentSchema() {
        recreateDatabaseAsVersion(3110000);
        PodDBAdapter.getInstance(); // Triggers the upgrade

        Playlist playlist = new Playlist(0, "Migrated");
        PlaylistFeedSettings settings = new PlaylistFeedSettings(1L);
        settings.setFilterUnplayed(true);
        playlist.getFeedSettings().add(settings);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.createPlaylist(playlist);
        adapter.close();

        List<Playlist> playlists = DBReader.getPlaylists();
        assertEquals(1, playlists.size());
        PlaylistFeedSettings stored = playlists.get(0).getFeedSettings(1L);
        assertNotNull(stored);
        assertTrue(stored.isFilterUnplayed());
    }

    @Test
    public void testUpgradeFrom3110001CopiesPlaylistWideFlags() {
        recreateDatabaseAsVersion(3110001,
                "CREATE TABLE " + PodDBAdapter.TABLE_NAME_PLAYLISTS + " (" + PodDBAdapter.KEY_ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT, " + PodDBAdapter.KEY_TITLE + " TEXT, "
                        + PodDBAdapter.KEY_FILTER_UNPLAYED + " INTEGER DEFAULT 0, "
                        + PodDBAdapter.KEY_FILTER_DOWNLOADED + " INTEGER DEFAULT 0, "
                        + PodDBAdapter.KEY_MAX_AGE_DAYS + " INTEGER DEFAULT 0)",
                "CREATE TABLE " + PodDBAdapter.TABLE_NAME_PLAYLIST_FEEDS + " (" + PodDBAdapter.KEY_ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT, " + PodDBAdapter.KEY_PLAYLIST
                        + " INTEGER, " + PodDBAdapter.KEY_FEED + " INTEGER)",
                "INSERT INTO " + PodDBAdapter.TABLE_NAME_PLAYLISTS + " (" + PodDBAdapter.KEY_TITLE + ", "
                        + PodDBAdapter.KEY_FILTER_UNPLAYED + ", " + PodDBAdapter.KEY_FILTER_DOWNLOADED
                        + ", " + PodDBAdapter.KEY_MAX_AGE_DAYS + ") VALUES ('News', 1, 0, 3)",
                "INSERT INTO " + PodDBAdapter.TABLE_NAME_PLAYLIST_FEEDS + " (" + PodDBAdapter.KEY_PLAYLIST
                        + ", " + PodDBAdapter.KEY_FEED + ") VALUES (1, 42)",
                "INSERT INTO " + PodDBAdapter.TABLE_NAME_PLAYLIST_FEEDS + " (" + PodDBAdapter.KEY_PLAYLIST
                        + ", " + PodDBAdapter.KEY_FEED + ") VALUES (1, 43)");
        PodDBAdapter.getInstance(); // Triggers the upgrade

        List<Playlist> playlists = DBReader.getPlaylists();
        assertEquals(1, playlists.size());
        assertEquals("News", playlists.get(0).getName());
        assertEquals(2, playlists.get(0).getFeedSettings().size());
        PlaylistFeedSettings first = playlists.get(0).getFeedSettings(42L);
        assertNotNull(first);
        assertTrue(first.isFilterUnplayed());
        assertTrue(!first.isFilterDownloaded());
        assertEquals(3, first.getMaxAgeDays());
        PlaylistFeedSettings second = playlists.get(0).getFeedSettings(43L);
        assertNotNull(second);
        assertTrue(second.isFilterUnplayed());
        assertEquals(3, second.getMaxAgeDays());
    }

    private void recreateDatabaseAsVersion(int version, String... statements) {
        PodDBAdapter.tearDownTests();
        File databaseFile = context.getDatabasePath(PodDBAdapter.DATABASE_NAME);
        if (databaseFile.exists() && !databaseFile.delete()) {
            throw new IllegalStateException("Could not delete database file");
        }
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
        for (String statement : statements) {
            database.execSQL(statement);
        }
        database.setVersion(version);
        database.close();
    }

    @Test
    public void testCreateUpdateDeletePlaylist() {
        Playlist playlist = new Playlist(0, "News");
        PlaylistFeedSettings first = new PlaylistFeedSettings(1L);
        first.setFilterUnplayed(true);
        first.setMaxAgeDays(3);
        PlaylistFeedSettings second = new PlaylistFeedSettings(2L);
        second.setFilterDownloaded(true);
        List<PlaylistFeedSettings> settingsList = new ArrayList<>();
        settingsList.add(first);
        settingsList.add(second);
        playlist.setFeedSettings(settingsList);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.createPlaylist(playlist);
        adapter.close();
        assertTrue(playlist.getId() != 0);

        List<Playlist> playlists = DBReader.getPlaylists();
        assertEquals(1, playlists.size());
        assertEquals("News", playlists.get(0).getName());
        assertEquals(2, playlists.get(0).getFeedSettings().size());

        PlaylistFeedSettings storedFirst = playlists.get(0).getFeedSettings(1L);
        assertNotNull(storedFirst);
        assertTrue(storedFirst.isFilterUnplayed());
        assertEquals(3, storedFirst.getMaxAgeDays());
        PlaylistFeedSettings storedSecond = playlists.get(0).getFeedSettings(2L);
        assertNotNull(storedSecond);
        assertTrue(storedSecond.isFilterDownloaded());
        assertEquals(0, storedSecond.getMaxAgeDays());

        playlist.setName("News fresh");
        playlist.setFeedSettings(Collections.singletonList(new PlaylistFeedSettings(3L)));
        adapter.open();
        adapter.updatePlaylist(playlist);
        adapter.close();

        playlists = DBReader.getPlaylists();
        assertEquals(1, playlists.size());
        assertEquals("News fresh", playlists.get(0).getName());
        assertEquals(1, playlists.get(0).getFeedSettings().size());
        assertEquals(3L, playlists.get(0).getFeedSettings().get(0).getFeedId());
        assertEquals(0, playlists.get(0).getFeedSettings().get(0).getMaxAgeDays());

        adapter.open();
        adapter.deletePlaylist(playlist.getId());
        adapter.close();
        assertTrue(DBReader.getPlaylists().isEmpty());
    }

    @Test
    public void testPlaylistEpisodesFromSelectedFeedsOnly() {
        Feed feedA = createPersistedFeedWithItems("feed-a", 2, FeedItem.UNPLAYED, true);
        Feed feedB = createPersistedFeedWithItems("feed-b", 2, FeedItem.UNPLAYED, true);

        Playlist playlist = new Playlist(0, "A only");
        playlist.getFeedSettings().add(new PlaylistFeedSettings(feedA.getId()));
        createPlaylist(playlist);

        List<FeedItem> episodes = DBReader.getPlaylistEpisodes(playlist, 0, Integer.MAX_VALUE);
        assertEquals(2, episodes.size());
        for (FeedItem item : episodes) {
            assertEquals(feedA.getId(), item.getFeedId());
        }
        assertEquals(2, DBReader.getPlaylistEpisodeCount(playlist));
    }

    @Test
    public void testPlaylistWithoutFiltersShowsAllEpisodes() {
        Feed feed = createPersistedFeedWithItems("feed", 3, FeedItem.PLAYED, false);
        Playlist playlist = new Playlist(0, "All");
        playlist.getFeedSettings().add(new PlaylistFeedSettings(feed.getId()));
        createPlaylist(playlist);

        assertEquals(3, DBReader.getPlaylistEpisodes(playlist, 0, Integer.MAX_VALUE).size());
        assertEquals(3, DBReader.getPlaylistEpisodeCount(playlist));
    }

    @Test
    public void testPlaylistUnplayedFilter() {
        Feed feed = new Feed("feed", null, "Feed");
        feed.setItems(new ArrayList<>());
        addItem(feed, "fresh-unplayed", System.currentTimeMillis(), FeedItem.UNPLAYED, true);
        addItem(feed, "old-played", System.currentTimeMillis(), FeedItem.PLAYED, true);
        addItem(feed, "new-item", System.currentTimeMillis(), FeedItem.NEW, true);
        persistFeed(feed);

        Playlist playlist = new Playlist(0, "Unplayed");
        PlaylistFeedSettings settings = new PlaylistFeedSettings(feed.getId());
        settings.setFilterUnplayed(true);
        playlist.getFeedSettings().add(settings);
        createPlaylist(playlist);

        List<FeedItem> episodes = DBReader.getPlaylistEpisodes(playlist, 0, Integer.MAX_VALUE);
        assertEquals(2, episodes.size()); // Includes "New" items, excludes played
        for (FeedItem item : episodes) {
            assertTrue(item.getTitle() + " should not be played", !item.isPlayed());
        }
    }

    @Test
    public void testPlaylistDownloadedFilter() {
        Feed feed = new Feed("feed", null, "Feed");
        feed.setItems(new ArrayList<>());
        addItem(feed, "downloaded", System.currentTimeMillis(), FeedItem.UNPLAYED, true);
        addItem(feed, "not-downloaded", System.currentTimeMillis(), FeedItem.UNPLAYED, false);
        persistFeed(feed);

        Playlist playlist = new Playlist(0, "Downloaded");
        PlaylistFeedSettings settings = new PlaylistFeedSettings(feed.getId());
        settings.setFilterDownloaded(true);
        playlist.getFeedSettings().add(settings);
        createPlaylist(playlist);

        List<FeedItem> episodes = DBReader.getPlaylistEpisodes(playlist, 0, Integer.MAX_VALUE);
        assertEquals(1, episodes.size());
        assertEquals("downloaded", episodes.get(0).getTitle());
    }

    @Test
    public void testPlaylistMaxAgeFilter() {
        long now = System.currentTimeMillis();
        Feed feed = new Feed("feed", null, "Feed");
        feed.setItems(new ArrayList<>());
        addItem(feed, "today", now, FeedItem.UNPLAYED, true);
        addItem(feed, "two-days-old", now - 2 * DAY, FeedItem.UNPLAYED, true);
        addItem(feed, "ten-days-old", now - 10 * DAY, FeedItem.UNPLAYED, true);
        persistFeed(feed);

        Playlist playlist = new Playlist(0, "Fresh");
        PlaylistFeedSettings settings = new PlaylistFeedSettings(feed.getId());
        settings.setMaxAgeDays(5);
        playlist.getFeedSettings().add(settings);
        createPlaylist(playlist);

        List<FeedItem> episodes = DBReader.getPlaylistEpisodes(playlist, 0, Integer.MAX_VALUE);
        assertEquals(2, episodes.size()); // The 10-days-old episode is too old
        for (FeedItem item : episodes) {
            assertTrue(item.getPubDate().getTime() >= now - 5 * DAY);
        }
    }

    @Test
    public void testPlaylistCombinedFilters() {
        long now = System.currentTimeMillis();
        Feed feed = new Feed("feed", null, "Feed");
        feed.setItems(new ArrayList<>());
        addItem(feed, "match", now, FeedItem.UNPLAYED, true);
        addItem(feed, "played", now, FeedItem.PLAYED, true);
        addItem(feed, "not-downloaded", now, FeedItem.UNPLAYED, false);
        addItem(feed, "too-old", now - 10 * DAY, FeedItem.UNPLAYED, true);
        persistFeed(feed);

        Playlist playlist = new Playlist(0, "Combined");
        PlaylistFeedSettings settings = new PlaylistFeedSettings(feed.getId());
        settings.setFilterUnplayed(true);
        settings.setFilterDownloaded(true);
        settings.setMaxAgeDays(5);
        playlist.getFeedSettings().add(settings);
        createPlaylist(playlist);

        List<FeedItem> episodes = DBReader.getPlaylistEpisodes(playlist, 0, Integer.MAX_VALUE);
        assertEquals(1, episodes.size());
        assertEquals("match", episodes.get(0).getTitle());
    }

    @Test
    public void testPerFeedSettingsAreIndependent() {
        long now = System.currentTimeMillis();
        Feed feedA = new Feed("feed-a", null, "Feed A");
        feedA.setItems(new ArrayList<>());
        addItem(feedA, "a-unplayed", now, FeedItem.UNPLAYED, true);
        addItem(feedA, "a-played", now, FeedItem.PLAYED, true);
        persistFeed(feedA);
        Feed feedB = new Feed("feed-b", null, "Feed B");
        feedB.setItems(new ArrayList<>());
        addItem(feedB, "b-played", now, FeedItem.PLAYED, true);
        persistFeed(feedB);

        Playlist playlist = new Playlist(0, "Independent");
        PlaylistFeedSettings settingsA = new PlaylistFeedSettings(feedA.getId());
        settingsA.setFilterUnplayed(true);
        playlist.getFeedSettings().add(settingsA);
        playlist.getFeedSettings().add(new PlaylistFeedSettings(feedB.getId())); // No filters
        createPlaylist(playlist);

        List<FeedItem> episodes = DBReader.getPlaylistEpisodes(playlist, 0, Integer.MAX_VALUE);
        assertEquals(2, episodes.size()); // A's unplayed item plus B's played item
        PlaylistFeedSettings reloaded = DBReader.getPlaylists().get(0).getFeedSettings(feedA.getId());
        assertNotNull(reloaded);
        assertTrue(reloaded.isFilterUnplayed());
    }

    @Test
    public void testPlaylistOrderingAndPagination() {
        long now = System.currentTimeMillis();
        Feed feed = new Feed("feed", null, "Feed");
        feed.setItems(new ArrayList<>());
        addItem(feed, "oldest", now - 3 * DAY, FeedItem.UNPLAYED, false);
        addItem(feed, "middle", now - 2 * DAY, FeedItem.UNPLAYED, false);
        addItem(feed, "newest", now, FeedItem.UNPLAYED, false);
        persistFeed(feed);

        Playlist playlist = new Playlist(0, "Ordered");
        playlist.getFeedSettings().add(new PlaylistFeedSettings(feed.getId()));
        createPlaylist(playlist);

        List<FeedItem> all = DBReader.getPlaylistEpisodes(playlist, 0, Integer.MAX_VALUE);
        assertEquals("newest", all.get(0).getTitle());
        assertEquals("middle", all.get(1).getTitle());
        assertEquals("oldest", all.get(2).getTitle());

        List<FeedItem> page = DBReader.getPlaylistEpisodes(playlist, 0, 2);
        assertEquals(2, page.size());
        assertEquals("newest", page.get(0).getTitle());
        List<FeedItem> next = DBReader.getPlaylistEpisodes(playlist, 2, 2);
        assertEquals(1, next.size());
        assertEquals("oldest", next.get(0).getTitle());
    }

    @Test
    public void testEmptyFeedSelectionMatchesNothing() {
        createPersistedFeedWithItems("feed", 2, FeedItem.UNPLAYED, true);
        Playlist playlist = new Playlist(0, "Empty");
        playlist.setFeedSettings(Collections.emptyList());
        createPlaylist(playlist);

        assertEquals(0, DBReader.getPlaylistEpisodes(playlist, 0, Integer.MAX_VALUE).size());
        assertEquals(0, DBReader.getPlaylistEpisodeCount(playlist));
    }

    @Test
    public void testFeedDeletionRemovesPlaylistAssociation() {
        Feed feed = createPersistedFeedWithItems("feed", 2, FeedItem.UNPLAYED, true);
        Playlist playlist = new Playlist(0, "Doomed");
        playlist.getFeedSettings().add(new PlaylistFeedSettings(feed.getId()));
        createPlaylist(playlist);

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.removeFeed(feed);
        adapter.close();

        Playlist reloaded = DBReader.getPlaylists().get(0);
        assertEquals(Collections.emptyList(), reloaded.getFeedSettings());
        assertEquals(0, DBReader.getPlaylistEpisodeCount(playlist));
    }

    @Test
    public void testReplacePlaylists() {
        Feed feedA = createPersistedFeedWithItems("feed-a", 1, FeedItem.UNPLAYED, true);
        createPersistedFeedWithItems("feed-b", 1, FeedItem.UNPLAYED, true);
        Playlist original = new Playlist(0, "Original");
        original.getFeedSettings().add(new PlaylistFeedSettings(feedA.getId()));
        createPlaylist(original);

        Playlist incoming = new Playlist(0, "Synced");
        incoming.getFeedSettings().add(new PlaylistFeedSettings(1L));
        Playlist incoming2 = new Playlist(0, "Synced 2");
        incoming2.getFeedSettings().add(new PlaylistFeedSettings(2L));
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.replacePlaylists(java.util.Arrays.asList(incoming, incoming2));
        adapter.close();

        List<Playlist> playlists = DBReader.getPlaylists();
        assertEquals(2, playlists.size());
        assertEquals("Synced", playlists.get(0).getName());
        assertEquals("Synced 2", playlists.get(1).getName());
        assertEquals(1L, playlists.get(0).getFeedSettings().get(0).getFeedId());
        assertEquals(2L, playlists.get(1).getFeedSettings().get(0).getFeedId());
    }

    @Test
    public void testFeedPickerData() {
        Feed feedA = createPersistedFeedWithItems("feed-a", 2, FeedItem.UNPLAYED, true);
        Feed feedB = createPersistedFeedWithItems("feed-b", 3, FeedItem.UNPLAYED, true);
        Playlist playlist = new Playlist(0, "Counts");
        playlist.getFeedSettings().add(new PlaylistFeedSettings(feedA.getId()));
        createPlaylist(playlist);

        assertEquals(feedB.getItems().get(0).getPubDate(),
                DBReader.getFeedLatestEpisodeDates().get(feedB.getId()));
        assertEquals(Integer.valueOf(1), DBReader.getFeedPlaylistCounts().get(feedA.getId()));
        assertNull(DBReader.getFeedPlaylistCounts().get(feedB.getId()));
    }

    private void createPlaylist(Playlist playlist) {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.createPlaylist(playlist);
        adapter.close();
    }

    private Feed createPersistedFeedWithItems(String url, int numItems, int readState, boolean downloaded) {
        Feed feed = new Feed(url, null, url);
        feed.setItems(new ArrayList<>());
        long now = System.currentTimeMillis();
        for (int i = 0; i < numItems; i++) {
            addItem(feed, "item-" + i, now - i * DAY, readState, downloaded);
        }
        persistFeed(feed);
        return feed;
    }

    private void addItem(Feed feed, String title, long pubDate, int readState, boolean downloaded) {
        FeedItem item = new FeedItem(0, title, "id-" + title, "link-" + title, new Date(pubDate), readState, feed);
        FeedMedia media = new FeedMedia(item, "url-" + title, 1024, "mime");
        if (downloaded) {
            media.setDownloaded(true, System.currentTimeMillis());
        }
        item.setMedia(media);
        feed.getItems().add(item);
    }

    private void persistFeed(Feed feed) {
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();
    }
}
