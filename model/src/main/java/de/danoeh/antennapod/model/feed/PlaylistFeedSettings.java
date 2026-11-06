package de.danoeh.antennapod.model.feed;

import java.io.Serializable;

/**
 * Per-podcast episode criteria for a podcast that is part of a playlist.
 */
public class PlaylistFeedSettings implements Serializable {

    public static final int MAX_AGE_DISABLED = 0;

    private final long feedId;
    private boolean filterUnplayed = false;
    private boolean filterDownloaded = false;
    private int maxAgeDays = MAX_AGE_DISABLED;

    public PlaylistFeedSettings(long feedId) {
        this.feedId = feedId;
    }

    public long getFeedId() {
        return feedId;
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
