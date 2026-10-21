package de.danoeh.antennapod.ui.screen.playlist;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.databinding.EditPlaylistBinding;
import de.danoeh.antennapod.event.MessageEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.Playlist;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.ui.common.ConfirmationDialog;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Creates a new playlist or edits an existing one.
 */
public class EditPlaylistDialog extends DialogFragment {

    public static final String TAG = "EditPlaylistDialog";
    private static final String ARGUMENT_PLAYLIST_ID = "playlistId";

    private EditPlaylistBinding binding;
    private Disposable disposable;
    private final List<Feed> feeds = new ArrayList<>();
    private final Set<Long> selectedFeedIds = new HashSet<>();
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
        binding.selectPodcastsButton.setOnClickListener(v -> showSelectPodcastsDialog());

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
            List<Feed> feedList = DBReader.getFeedList();
            Playlist playlist = null;
            if (playlistId != 0) {
                for (Playlist candidate : DBReader.getPlaylists()) {
                    if (candidate.getId() == playlistId) {
                        playlist = candidate;
                        break;
                    }
                }
            }
            return new Pair<>(feedList, playlist);
        })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onDataLoaded);
    }

    private void onDataLoaded(Pair<List<Feed>, Playlist> data) {
        feeds.clear();
        feeds.addAll(data.first);
        if (data.second != null) {
            Playlist playlist = data.second;
            binding.playlistNameInput.setText(playlist.getName());
            binding.filterUnplayedCheckbox.setChecked(playlist.isFilterUnplayed());
            binding.filterDownloadedCheckbox.setChecked(playlist.isFilterDownloaded());
            binding.maxAgeDaysInput.setText(
                    playlist.hasMaxAge() ? String.valueOf(playlist.getMaxAgeDays()) : "");
            selectedFeedIds.addAll(playlist.getFeedIds());
        }
    }

    private void showSelectPodcastsDialog() {
        List<String> feedTitles = new ArrayList<>();
        for (Feed feed : feeds) {
            feedTitles.add(feed.getTitle());
        }
        boolean[] checkedItems = new boolean[feeds.size()];
        for (int i = 0; i < feeds.size(); i++) {
            checkedItems[i] = selectedFeedIds.contains(feeds.get(i).getId());
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.playlist_select_podcasts_label)
                .setMultiChoiceItems(feedTitles.toArray(new String[0]), checkedItems,
                        (dialog, which, isChecked) -> {
                            if (isChecked) {
                                selectedFeedIds.add(feeds.get(which).getId());
                            } else {
                                selectedFeedIds.remove(feeds.get(which).getId());
                            }
                        })
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void savePlaylist() {
        String name = binding.playlistNameInput.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            EventBus.getDefault().post(new MessageEvent(getString(R.string.playlist_name_empty_error)));
            return;
        }
        if (selectedFeedIds.isEmpty()) {
            EventBus.getDefault().post(new MessageEvent(getString(R.string.playlist_no_podcasts_error)));
            return;
        }
        int maxAgeDays = 0;
        String maxAgeDaysText = binding.maxAgeDaysInput.getText().toString().trim();
        if (!TextUtils.isEmpty(maxAgeDaysText)) {
            try {
                maxAgeDays = Integer.parseInt(maxAgeDaysText);
            } catch (NumberFormatException e) {
                EventBus.getDefault().post(new MessageEvent(getString(R.string.playlist_filter_max_age_label)));
                return;
            }
            if (maxAgeDays < 0) {
                maxAgeDays = 0;
            }
        }

        Playlist playlist = new Playlist(playlistId, name);
        playlist.setFilterUnplayed(binding.filterUnplayedCheckbox.isChecked());
        playlist.setFilterDownloaded(binding.filterDownloadedCheckbox.isChecked());
        playlist.setMaxAgeDays(maxAgeDays);
        playlist.setFeedIds(new ArrayList<>(selectedFeedIds));
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
}
