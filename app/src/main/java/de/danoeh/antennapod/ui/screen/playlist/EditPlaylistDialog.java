package de.danoeh.antennapod.ui.screen.playlist;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.databinding.AddPlaylistFeedRowBinding;
import de.danoeh.antennapod.databinding.EditPlaylistBinding;
import de.danoeh.antennapod.databinding.EditPlaylistFeedOptionsBinding;
import de.danoeh.antennapod.databinding.EditPlaylistFeedRowBinding;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.model.feed.PlaylistFeedSettings;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.ui.common.ConfirmationDialog;
import de.danoeh.antennapod.ui.common.DateFormatter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Creates a new playlist or edits an existing one.
 * All podcast rows and their options are staged in this dialog and persisted on OK.
 */
public class EditPlaylistDialog extends DialogFragment {

    public static final String TAG = "EditPlaylistDialog";
    private static final String ARGUMENT_PLAYLIST_ID = "playlistId";

    private EditPlaylistBinding binding;
    private Disposable disposable;
    private final List<Feed> feeds = new ArrayList<>();
    private final Map<Long, Date> latestEpisodeDates = new HashMap<>();
    private final Map<Long, Integer> playlistCounts = new HashMap<>();
    private final List<PlaylistFeedSettings> feedSettings = new ArrayList<>();
    private FeedSettingsAdapter feedSettingsAdapter;
    private long playlistId = 0;

    public static EditPlaylistDialog newInstance() {
        return new EditPlaylistDialog();
    }

    public static EditPlaylistDialog newInstanceFor(long playlistId) {
        EditPlaylistDialog dialog = new EditPlaylistDialog();
        Bundle args = new Bundle();
        args.putLong(ARGUMENT_PLAYLIST_ID, playlistId);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            playlistId = getArguments().getLong(ARGUMENT_PLAYLIST_ID, 0);
        }
        binding = EditPlaylistBinding.inflate(getLayoutInflater());
        feedSettingsAdapter = new FeedSettingsAdapter();
        binding.feedList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.feedList.setAdapter(feedSettingsAdapter);
        binding.addFeedButton.setOnClickListener(v -> showFeedPicker());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(playlistId == 0 ? R.string.new_playlist_label : R.string.edit_playlist_label)
                .setView(binding.getRoot())
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(R.string.cancel_label, null);
        if (playlistId != 0) {
            builder.setNeutralButton(R.string.delete_playlist_label, null);
        }
        AlertDialog dialog = builder.show();

        // To prevent dismissing the dialog on validation errors
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> savePlaylist());
        if (playlistId != 0) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> confirmDeletePlaylist());
        }
        loadData();
        return dialog;
    }

    private void loadData() {
        disposable = Observable.fromCallable(() -> {
            Playlist playlist = null;
            if (playlistId != 0) {
                for (Playlist candidate : DBReader.getPlaylists()) {
                    if (candidate.getId() == playlistId) {
                        playlist = candidate;
                        break;
                    }
                }
            }
            return new LoadedData(DBReader.getFeedList(), DBReader.getFeedLatestEpisodeDates(),
                    DBReader.getFeedPlaylistCounts(), playlist);
        })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onDataLoaded);
    }

    private void onDataLoaded(LoadedData data) {
        feeds.clear();
        feeds.addAll(data.feeds);
        latestEpisodeDates.clear();
        latestEpisodeDates.putAll(data.latestEpisodeDates);
        playlistCounts.clear();
        playlistCounts.putAll(data.playlistCounts);
        if (data.playlist != null) {
            binding.playlistNameInput.setText(data.playlist.getName());
            feedSettings.clear();
            for (PlaylistFeedSettings settings : data.playlist.getFeedSettings()) {
                feedSettings.add(copySettings(settings));
            }
        }
        feedSettingsAdapter.notifyDataSetChanged();
    }

    private static PlaylistFeedSettings copySettings(PlaylistFeedSettings settings) {
        PlaylistFeedSettings copy = new PlaylistFeedSettings(settings.getFeedId());
        copy.setFilterUnplayed(settings.isFilterUnplayed());
        copy.setFilterDownloaded(settings.isFilterDownloaded());
        copy.setMaxAgeDays(settings.getMaxAgeDays());
        return copy;
    }

    @Nullable
    private Feed getFeedById(long feedId) {
        for (Feed feed : feeds) {
            if (feed.getId() == feedId) {
                return feed;
            }
        }
        return null;
    }

    private class FeedSettingsAdapter extends RecyclerView.Adapter<FeedSettingsAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(EditPlaylistFeedRowBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PlaylistFeedSettings settings = feedSettings.get(position);
            Feed feed = getFeedById(settings.getFeedId());
            holder.binding.txtvFeedTitle.setText(feed != null ? feed.getTitle() : "");
            holder.binding.txtvFeedOptions.setText(buildOptionsSummary(settings));
            holder.binding.imgvEditFeed.setOnClickListener(v -> {
                if (feed != null) {
                    showFeedOptionsDialog(feed, settings);
                }
            });
            holder.binding.imgvRemoveFeed.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    feedSettings.remove(adapterPosition);
                    notifyItemRemoved(adapterPosition);
                }
            });
        }

        @Override
        public int getItemCount() {
            return feedSettings.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final EditPlaylistFeedRowBinding binding;

            Holder(EditPlaylistFeedRowBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private String buildOptionsSummary(PlaylistFeedSettings settings) {
        List<String> parts = new ArrayList<>();
        if (settings.isFilterUnplayed()) {
            parts.add(getString(R.string.playlist_summary_unplayed_label));
        }
        if (settings.isFilterDownloaded()) {
            parts.add(getString(R.string.playlist_summary_downloaded_label));
        }
        if (settings.hasMaxAge()) {
            parts.add(getString(R.string.playlist_summary_max_age_label, settings.getMaxAgeDays()));
        }
        if (parts.isEmpty()) {
            return getString(R.string.playlist_summary_all_label);
        }
        return TextUtils.join(" · ", parts);
    }

    private void showFeedPicker() {
        final List<Feed> pickerFeeds = new ArrayList<>();
        for (Feed feed : feeds) {
            if (getStagedSettings(feed.getId()) == null) {
                pickerFeeds.add(feed);
            }
        }
        if (pickerFeeds.isEmpty()) {
            EventBus.getDefault().post(
                    new MessageEvent(getString(R.string.playlist_all_podcasts_added_label)));
            return;
        }
        ArrayAdapter<Feed> adapter = new ArrayAdapter<Feed>(
                requireContext(), R.layout.add_playlist_feed_row, pickerFeeds) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                AddPlaylistFeedRowBinding rowBinding;
                if (convertView == null) {
                    rowBinding = AddPlaylistFeedRowBinding.inflate(
                            LayoutInflater.from(parent.getContext()), parent, false);
                    convertView = rowBinding.getRoot();
                    convertView.setTag(rowBinding);
                } else {
                    rowBinding = (AddPlaylistFeedRowBinding) convertView.getTag();
                }
                Feed feed = getItem(position);
                rowBinding.txtvFeedTitle.setText(feed.getTitle());
                Date latest = latestEpisodeDates.get(feed.getId());
                if (latest != null) {
                    rowBinding.txtvLatestEpisode.setText(getString(R.string.playlist_latest_episode_label,
                            DateFormatter.formatAbbrev(getContext(), latest)));
                    rowBinding.txtvLatestEpisode.setVisibility(View.VISIBLE);
                } else {
                    rowBinding.txtvLatestEpisode.setVisibility(View.GONE);
                }
                Integer count = playlistCounts.get(feed.getId());
                if (count != null && count > 0) {
                    rowBinding.txtvPlaylistCount.setText(getString(R.string.playlist_in_playlists_label, count));
                    rowBinding.txtvPlaylistCount.setVisibility(View.VISIBLE);
                } else {
                    rowBinding.txtvPlaylistCount.setVisibility(View.GONE);
                }
                return convertView;
            }
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.playlist_select_podcasts_label)
                .setAdapter(adapter, (dialog, which) -> showFeedOptionsDialog(pickerFeeds.get(which), null))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    @Nullable
    private PlaylistFeedSettings getStagedSettings(long feedId) {
        for (PlaylistFeedSettings settings : feedSettings) {
            if (settings.getFeedId() == feedId) {
                return settings;
            }
        }
        return null;
    }

    private void showFeedOptionsDialog(Feed feed, @Nullable PlaylistFeedSettings settings) {
        final PlaylistFeedSettings edited =
                settings != null ? settings : new PlaylistFeedSettings(feed.getId());
        final EditPlaylistFeedOptionsBinding optionsBinding =
                EditPlaylistFeedOptionsBinding.inflate(getLayoutInflater());
        optionsBinding.filterUnplayedCheckbox.setChecked(edited.isFilterUnplayed());
        optionsBinding.filterDownloadedCheckbox.setChecked(edited.isFilterDownloaded());
        optionsBinding.maxAgeDaysInput.setText(edited.hasMaxAge()
                ? String.valueOf(edited.getMaxAgeDays()) : "");
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(feed.getTitle())
                .setView(optionsBinding.getRoot())
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(R.string.cancel_label, null)
                .show();

        // To prevent dismissing the dialog on validation errors
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (applyFeedOptions(optionsBinding, feed, edited)) {
                dialog.dismiss();
            }
        });
    }

    private boolean applyFeedOptions(EditPlaylistFeedOptionsBinding optionsBinding,
                                    Feed feed, PlaylistFeedSettings edited) {
        int maxAgeDays = 0;
        String maxAgeDaysText = optionsBinding.maxAgeDaysInput.getText().toString().trim();
        if (!TextUtils.isEmpty(maxAgeDaysText)) {
            try {
                maxAgeDays = Integer.parseInt(maxAgeDaysText);
            } catch (NumberFormatException e) {
                EventBus.getDefault().post(
                        new MessageEvent(getString(R.string.playlist_filter_max_age_label)));
                return false;
            }
            if (maxAgeDays < 0) {
                maxAgeDays = 0;
            }
        }
        edited.setFilterUnplayed(optionsBinding.filterUnplayedCheckbox.isChecked());
        edited.setFilterDownloaded(optionsBinding.filterDownloadedCheckbox.isChecked());
        edited.setMaxAgeDays(maxAgeDays);
        if (getStagedSettings(feed.getId()) == null) {
            feedSettings.add(edited);
        }
        feedSettingsAdapter.notifyDataSetChanged();
        return true;
    }

    private void savePlaylist() {
        String name = binding.playlistNameInput.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            EventBus.getDefault().post(new MessageEvent(getString(R.string.playlist_name_empty_error)));
            return;
        }
        if (feedSettings.isEmpty()) {
            EventBus.getDefault().post(new MessageEvent(getString(R.string.playlist_no_podcasts_error)));
            return;
        }

        Playlist playlist = new Playlist(playlistId, name);
        playlist.setFeedSettings(new ArrayList<>(feedSettings));
        if (playlistId == 0) {
            DBWriter.createPlaylist(playlist);
        } else {
            DBWriter.updatePlaylist(playlist);
        }
        dismiss();
    }

    private void confirmDeletePlaylist() {
        ConfirmationDialog dialog = new ConfirmationDialog(requireContext(),
                R.string.delete_playlist_label, R.string.playlist_delete_confirmation) {
            @Override
            public void onConfirmButtonPressed(DialogInterface dialog) {
                dialog.dismiss();
                DBWriter.deletePlaylist(new Playlist(playlistId, null));
                dismiss();
            }
        };
        dialog.createNewDialog().show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private static class LoadedData {
        final List<Feed> feeds;
        final Map<Long, Date> latestEpisodeDates;
        final Map<Long, Integer> playlistCounts;
        final Playlist playlist;

        LoadedData(List<Feed> feeds, Map<Long, Date> latestEpisodeDates,
                   Map<Long, Integer> playlistCounts, Playlist playlist) {
            this.feeds = feeds;
            this.latestEpisodeDates = latestEpisodeDates;
            this.playlistCounts = playlistCounts;
            this.playlist = playlist;
        }
    }
}
