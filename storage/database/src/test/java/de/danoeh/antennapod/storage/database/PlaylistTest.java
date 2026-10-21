package de.danoeh.antennapod.storage.database;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import static org.junit.Assert.assertEquals;
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
        PodDBAdapter.deleteDatabase();
        PodDBAdapter.tearDownTests();
    }

    @Test
    public void testCreateUpdateDeletePlaylist() {
        Playlist playlist = new Playlist(0, "News");
        playlist.setFilterUnplayed(true);
        playlist.setMaxAgeDays(3);
        playlist.setFeedIds(Arrays.asList(1L, 2L));
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.createPlaylist(playlist);
        adapter.close();
        assertTrue(playlist.getId() != 0);

        List<Playlist> playlists = DBReader.getPlaylists();
        assertEquals(1, playlists.size());
        assertEquals("News", playlists.get(0).getName());
        assertTrue(playlists.get(0).isFilterUnplayed());
        assertEquals(3, playlists.get(0).getMaxAgeDays());
        assertEquals(Arrays.asList(1L, 2L), playlists.get(0).getFeedIds());

        playlist.setName("News fresh");
        playlist.setFilterUnplayed(false);
        playlist.setMaxAgeDays(0);
        playlist.setFeedIds(Collections.singletonList(1L));
        adapter.open();
        adapter.updatePlaylist(playlist);
        adapter.close();

        playlists = DBReader.getPlaylists();
        assertEquals(1, playlists.size());
        assertEquals("News fresh", playlists.get(0).getName());
        assertTrue(!playlists.get(0).isFilterUnplayed());
        assertEquals(0, playlists.get(0).getMaxAgeDays());
        assertEquals(Collections.singletonList(1L), playlists.get(0).getFeedIds());

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
        playlist.setFeedIds(Collections.singletonList(feedA.getId()));
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
        playlist.setFeedIds(Collections.singletonList(feed.getId()));
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
        playlist.setFilterUnplayed(true);
        playlist.setFeedIds(Collections.singletonList(feed.getId()));
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
        playlist.setFilterDownloaded(true);
        playlist.setFeedIds(Collections.singletonList(feed.getId()));
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
        playlist.setMaxAgeDays(5);
        playlist.setFeedIds(Collections.singletonList(feed.getId()));
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
        playlist.setFilterUnplayed(true);
        playlist.setFilterDownloaded(true);
        playlist.setMaxAgeDays(5);
        playlist.setFeedIds(Collections.singletonList(feed.getId()));
        createPlaylist(playlist);

        List<FeedItem> episodes = DBReader.getPlaylistEpisodes(playlist, 0, Integer.MAX_VALUE);
        assertEquals(1, episodes.size());
        assertEquals("match", episodes.get(0).getTitle());
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
        playlist.setFeedIds(Collections.singletonList(feed.getId()));
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
        playlist.setFeedIds(Collections.emptyList());
        createPlaylist(playlist);

        assertEquals(0, DBReader.getPlaylistEpisodes(playlist, 0, Integer.MAX_VALUE).size());
        assertEquals(0, DBReader.getPlaylistEpisodeCount(playlist));
    }

    @Test
    public void testFeedDeletionRemovesPlaylistAssociation() {
        Feed feed = createPersistedFeedWithItems("feed", 2, FeedItem.UNPLAYED, true);
        Playlist playlist = new Playlist(0, "Doomed");
        playlist.setFeedIds(Collections.singletonList(feed.getId()));
        createPlaylist(playlist);

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.removeFeed(feed);
        adapter.close();

        Playlist reloaded = DBReader.getPlaylists().get(0);
        assertEquals(Collections.emptyList(), reloaded.getFeedIds());
        assertEquals(0, DBReader.getPlaylistEpisodeCount(playlist));
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
