package de.danoeh.antennapod.model.feed;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Object for a user-created smart playlist.
 */
public class Playlist implements Serializable {

    public static final int MAX_AGE_DISABLED = 0;

    private long id;
    private String name;
    private List<Long> feedIds = new ArrayList<>();
    private boolean filterUnplayed = false;
    private boolean filterDownloaded = false;
    private int maxAgeDays = MAX_AGE_DISABLED;

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

    public List<Long> getFeedIds() {
        return feedIds;
    }

    public void setFeedIds(List<Long> feedIds) {
        this.feedIds = feedIds;
    }

    public boolean isFilterUnplayed() {
        return filterUnplayed;
    }

    public void setFilterUnplayed(boolean filterUnplayed) {
        this.filterUnplayed = filterUnplayed;
    }

    public boolean isFilterDownloaded() {
        return filterDownloaded;
    }

    public void setFilterDownloaded(boolean filterDownloaded) {
        this.filterDownloaded = filterDownloaded;
    }

    public int getMaxAgeDays() {
        return maxAgeDays;
    }

    public void setMaxAgeDays(int maxAgeDays) {
        this.maxAgeDays = maxAgeDays;
    }

    public boolean hasMaxAge() {
        return maxAgeDays > MAX_AGE_DISABLED;
    }
}
