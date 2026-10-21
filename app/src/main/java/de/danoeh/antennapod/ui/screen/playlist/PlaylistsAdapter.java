package de.danoeh.antennapod.ui.screen.playlist;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import de.danoeh.antennapod.databinding.PlaylistListitemBinding;
import de.danoeh.antennapod.model.feed.Playlist;

import java.util.List;

public class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.Holder> {

    public interface ItemClickedListener {
        void onPlaylistClicked(Playlist playlist);
    }

    public interface ItemEditClickedListener {
        void onPlaylistEditClicked(Playlist playlist);
    }

    private final ItemClickedListener itemClickedListener;
    private final ItemEditClickedListener itemEditClickedListener;
    private List<Playlist> playlists;

    public PlaylistsAdapter(ItemClickedListener itemClickedListener,
                             ItemEditClickedListener itemEditClickedListener) {
        this.itemClickedListener = itemClickedListener;
        this.itemEditClickedListener = itemEditClickedListener;
    }

    public void submitList(List<Playlist> playlists) {
        this.playlists = playlists;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(PlaylistListitemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Playlist playlist = playlists.get(position);
        holder.itemView.setOnClickListener(v -> itemClickedListener.onPlaylistClicked(playlist));
        holder.itemView.setOnLongClickListener(v -> {
            itemEditClickedListener.onPlaylistEditClicked(playlist);
            return true;
        });
        holder.binding.imgvItemProperties.setOnClickListener(
                v -> itemEditClickedListener.onPlaylistEditClicked(playlist));
        holder.binding.txtvPlaylistName.setText(playlist.getName());
    }

    @Override
    public int getItemCount() {
        return playlists == null ? 0 : playlists.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final PlaylistListitemBinding binding;

        Holder(PlaylistListitemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
