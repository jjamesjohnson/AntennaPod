package de.danoeh.antennapod.ui.screen.playlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.event.PlaylistEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.ui.episodeslist.EpisodesListFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Shows the episodes that currently match a playlist.
 */
public class PlaylistFragment extends EpisodesListFragment {

    public static final String TAG = "PlaylistFragment";
    private static final String ARGUMENT_PLAYLIST_ID = "playlistId";

    private Playlist playlist;

    public static PlaylistFragment newInstance(long playlistId) {
        PlaylistFragment fragment = new PlaylistFragment();
        Bundle args = new Bundle();
        args.putLong(ARGUMENT_PLAYLIST_ID, playlistId);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View root = super.onCreateView(inflater, container, savedInstanceState);
        toolbar.inflateMenu(R.menu.playlist);
        updateToolbar();
        emptyView.setTitle(R.string.playlists_label);
        emptyView.setMessage(R.string.no_playlist_episodes_label);
        return root;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (super.onMenuItemClick(item)) {
            return true;
        }
        if (item.getItemId() == R.id.play_playlist_item) {
            playPlaylist();
            return true;
        } else if (item.getItemId() == R.id.edit_playlist_item) {
            if (playlist != null) {
                EditPlaylistDialog.newInstanceFor(playlist.getId())
                        .show(getChildFragmentManager(), EditPlaylistDialog.TAG);
            }
            return true;
        }
        return false;
    }

    private void playPlaylist() {
        if (playlist == null) {
            return;
        }
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(() -> {
            List<FeedItem> playableItems = new ArrayList<>();
            for (FeedItem item : DBReader.getPlaylistEpisodes(playlist, 0, Integer.MAX_VALUE)) {
                if (item.getMedia() != null) {
                    playableItems.add(item);
                }
            }
            return playableItems;
        })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    if (items.isEmpty()) {
                        EventBus.getDefault().post(
                                new MessageEvent(getString(R.string.no_playlist_episodes_label)));
                        return;
                    }
                    DBWriter.setQueue(items);
                    new PlaybackServiceStarter(getContext(), items.get(0).getMedia())
                            .callEvenIfRunning(true)
                            .start();
                });
    }

    private Playlist getLoadedPlaylist() {
        if (playlist == null) {
            long playlistId = requireArguments().getLong(ARGUMENT_PLAYLIST_ID);
            for (Playlist candidate : DBReader.getPlaylists()) {
                if (candidate.getId() == playlistId) {
                    playlist = candidate;
                    break;
                }
            }
        }
        return playlist;
    }

    @NonNull
    @Override
    protected List<FeedItem> loadData() {
        Playlist loaded = getLoadedPlaylist();
        if (loaded == null) {
            return Collections.emptyList();
        }
        return DBReader.getPlaylistEpisodes(loaded, 0, page * EPISODES_PER_PAGE);
    }

    @NonNull
    @Override
    protected List<FeedItem> loadMoreData(int page) {
        Playlist loaded = getLoadedPlaylist();
        if (loaded == null) {
            return Collections.emptyList();
        }
        return DBReader.getPlaylistEpisodes(loaded, (page - 1) * EPISODES_PER_PAGE, EPISODES_PER_PAGE);
    }

    @Override
    protected int loadTotalItemCount() {
        Playlist loaded = getLoadedPlaylist();
        return loaded == null ? 0 : DBReader.getPlaylistEpisodeCount(loaded);
    }

    @Override
    protected FeedItemFilter getFilter() {
        // The playlist's criteria are per-podcast and can not be expressed in a single
        // FeedItemFilter. Episode events are handled with a full reload instead.
        return FeedItemFilter.unfiltered();
    }

    @Override
    protected String getFragmentTag() {
        return TAG;
    }

    @Override
    protected void updateToolbar() {
        if (playlist != null) {
            toolbar.setTitle(playlist.getName());
        }
    }

    @Override
    public void onEventMainThread(FeedItemEvent event) {
        loadItems();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaylistEvent event) {
        if (event.action == PlaylistEvent.Action.DELETED
                && event.playlistId == requireArguments().getLong(ARGUMENT_PLAYLIST_ID)) {
            getParentFragmentManager().popBackStack();
        } else if (event.action == PlaylistEvent.Action.UPDATED
                && event.playlistId == requireArguments().getLong(ARGUMENT_PLAYLIST_ID)) {
            playlist = null;
            loadItems();
        }
    }
}
