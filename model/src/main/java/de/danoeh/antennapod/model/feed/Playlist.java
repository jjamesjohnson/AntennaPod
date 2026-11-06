package de.danoeh.antennapod.model.feed;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Object for a user-created smart playlist.
 */
public class Playlist implements Serializable {

    private long id;
    private String name;
    private List<PlaylistFeedSettings> feedSettings = new ArrayList<>();

    public Playlist() {
    }

    public Playlist(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<PlaylistFeedSettings> getFeedSettings() {
        return feedSettings;
    }

    public void setFeedSettings(List<PlaylistFeedSettings> feedSettings) {
        this.feedSettings = feedSettings;
    }

    public PlaylistFeedSettings getFeedSettings(long feedId) {
        for (PlaylistFeedSettings settings : feedSettings) {
            if (settings.getFeedId() == feedId) {
                return settings;
            }
        }
        return null;
    }
}
