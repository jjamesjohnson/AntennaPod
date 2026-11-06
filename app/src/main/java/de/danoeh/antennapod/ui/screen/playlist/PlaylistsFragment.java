package de.danoeh.antennapod.ui.screen.playlist;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.PlaylistEvent;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.ui.common.EmptyViewHandler;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Shows a list of the user's playlists.
 */
public class PlaylistsFragment extends Fragment {

    public static final String TAG = "PlaylistsFragment";

    private MaterialToolbar toolbar;
    private ProgressBar progressBar;
    private PlaylistsAdapter adapter;
    private Disposable disposable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View root = inflater.inflate(R.layout.playlists_fragment, container, false);
        toolbar = root.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.playlists_label);
        toolbar.inflateMenu(R.menu.playlists);
        toolbar.setOnMenuItemClickListener(this::onMenuItemClicked);
        ((MainActivity) requireActivity()).setupToolbarToggle(toolbar, false);

        RecyclerView recyclerView = root.findViewById(R.id.recyclerView);
        adapter = new PlaylistsAdapter(this::onPlaylistClicked, this::showEditDialog);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        EmptyViewHandler emptyView = new EmptyViewHandler(getContext());
        emptyView.attachToRecyclerView(recyclerView);
        emptyView.setIcon(R.drawable.ic_playlist_play);
        emptyView.setTitle(R.string.playlists_label);
        emptyView.setMessage(R.string.no_playlists_label);
        emptyView.updateAdapter(adapter);

        progressBar = root.findViewById(R.id.progressBar);
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        loadPlaylists();
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private void loadPlaylists() {
        if (disposable != null) {
            disposable.dispose();
        }
        progressBar.setVisibility(View.VISIBLE);
        disposable = Observable.fromCallable(DBReader::getPlaylists)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        playlists -> {
                            progressBar.setVisibility(View.GONE);
                            adapter.submitList(playlists);
                        }, error -> {
                            progressBar.setVisibility(View.GONE);
                            Log.e(TAG, Log.getStackTraceString(error));
                        });
    }

    private boolean onMenuItemClicked(MenuItem item) {
        if (item.getItemId() == R.id.new_playlist_item) {
            new EditPlaylistDialog().show(getChildFragmentManager(), EditPlaylistDialog.TAG);
            return true;
        } else if (item.getItemId() == R.id.refresh_item) {
            FeedUpdateManager.getInstance().runOnceOrAsk(requireContext());
            return true;
        }
        return false;
    }

    private void onPlaylistClicked(Playlist playlist) {
        ((MainActivity) requireActivity()).loadChildFragment(PlaylistFragment.newInstance(playlist.getId()));
    }

    private void showEditDialog(Playlist playlist) {
        EditPlaylistDialog.newInstanceFor(playlist.getId())
                .show(getChildFragmentManager(), EditPlaylistDialog.TAG);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PlaylistEvent event) {
        loadPlaylists();
    }
}
