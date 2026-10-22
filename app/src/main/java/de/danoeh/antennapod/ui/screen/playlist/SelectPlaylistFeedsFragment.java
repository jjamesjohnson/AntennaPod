package de.danoeh.antennapod.ui.screen.playlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.databinding.AddPlaylistFeedRowBinding;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.model.feed.PlaylistFeedSettings;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.ui.common.DateFormatter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Full-screen podcast selector for adding podcasts to a playlist.
 */
public class SelectPlaylistFeedsFragment extends Fragment {

    public static final String TAG = "SelectPlaylistFeedsFragment";
    public static final String REQUEST_KEY = "selectPlaylistFeeds";
    public static final String RESULT_FEED_ID = "feedId";
    private static final String ARGUMENT_PLAYLIST_ID = "playlistId";

    private final List<Feed> feeds = new ArrayList<>();
    private final Set<Long> existingFeedIds = new HashSet<>();
    private Map<Long, Date> latestEpisodeDates;
    private Map<Long, Integer> playlistCounts;
    private Disposable disposable;
    private boolean resultSent = false;

    public static SelectPlaylistFeedsFragment newInstance(long playlistId) {
        SelectPlaylistFeedsFragment fragment = new SelectPlaylistFeedsFragment();
        Bundle args = new Bundle();
        args.putLong(ARGUMENT_PLAYLIST_ID, playlistId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.select_playlist_feeds_fragment, container, false);
        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        ((MainActivity) requireActivity()).setupToolbarToggle(toolbar, true);

        RecyclerView recyclerView = root.findViewById(R.id.feedList);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(new FeedAdapter());
        loadData();
        return root;
    }

    private long getPlaylistId() {
        return getArguments() != null ? getArguments().getLong(ARGUMENT_PLAYLIST_ID, 0) : 0;
    }

    private void loadData() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(() -> {
            long playlistId = getPlaylistId();
            List<Playlist> playlists = DBReader.getPlaylists();
            Set<Long> existing = new HashSet<>();
            for (Playlist playlist : playlists) {
                if (playlist.getId() == playlistId) {
                    for (PlaylistFeedSettings settings : playlist.getFeedSettings()) {
                        existing.add(settings.getFeedId());
                    }
                    break;
                }
            }
            return new LoadedData(DBReader.getFeedList(), DBReader.getFeedLatestEpisodeDates(),
                    DBReader.getFeedPlaylistCounts(), existing);
        })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onDataLoaded);
    }

    private void onDataLoaded(LoadedData data) {
        feeds.clear();
        for (Feed feed : data.feeds) {
            if (!data.existingFeedIds.contains(feed.getId())) {
                feeds.add(feed);
            }
        }
        latestEpisodeDates = data.latestEpisodeDates;
        playlistCounts = data.playlistCounts;
        existingFeedIds.clear();
        existingFeedIds.addAll(data.existingFeedIds);
        RecyclerView recyclerView = getView() != null ? getView().findViewById(R.id.feedList) : null;
        if (recyclerView != null) {
            recyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    private void onFeedSelected(Feed feed) {
        resultSent = true;
        Bundle result = new Bundle();
        result.putLong(RESULT_FEED_ID, feed.getId());
        requireActivity().getSupportFragmentManager().setFragmentResult(REQUEST_KEY, result);
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!resultSent && isRemoving()) {
            Bundle result = new Bundle();
            result.putLong(RESULT_FEED_ID, 0);
            requireActivity().getSupportFragmentManager().setFragmentResult(REQUEST_KEY, result);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(AddPlaylistFeedRowBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Feed feed = feeds.get(position);
            holder.binding.txtvFeedTitle.setText(feed.getTitle());
            Date latest = latestEpisodeDates != null ? latestEpisodeDates.get(feed.getId()) : null;
            if (latest != null) {
                holder.binding.txtvLatestEpisode.setText(getString(R.string.playlist_latest_episode_label,
                        DateFormatter.formatAbbrev(getContext(), latest)));
                holder.binding.txtvLatestEpisode.setVisibility(View.VISIBLE);
            } else {
                holder.binding.txtvLatestEpisode.setVisibility(View.GONE);
            }
            Integer count = playlistCounts != null ? playlistCounts.get(feed.getId()) : null;
            if (count != null && count > 0) {
                holder.binding.txtvPlaylistCount.setText(getString(R.string.playlist_in_playlists_label, count));
                holder.binding.txtvPlaylistCount.setVisibility(View.VISIBLE);
            } else {
                holder.binding.txtvPlaylistCount.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(v -> onFeedSelected(feed));
        }

        @Override
        public int getItemCount() {
            return feeds.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final AddPlaylistFeedRowBinding binding;

            Holder(AddPlaylistFeedRowBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private static class LoadedData {
        final List<Feed> feeds;
        final Map<Long, Date> latestEpisodeDates;
        final Map<Long, Integer> playlistCounts;
        final Set<Long> existingFeedIds;

        LoadedData(List<Feed> feeds, Map<Long, Date> latestEpisodeDates,
                   Map<Long, Integer> playlistCounts, Set<Long> existingFeedIds) {
            this.feeds = feeds;
            this.latestEpisodeDates = latestEpisodeDates;
            this.playlistCounts = playlistCounts;
            this.existingFeedIds = existingFeedIds;
        }
    }
}