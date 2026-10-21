package de.danoeh.antennapod.event;

public class PlaylistEvent {

    public enum Action {
        CREATED, UPDATED, DELETED
    }

    public final Action action;
    public final long playlistId;

    private PlaylistEvent(Action action, long playlistId) {
        this.action = action;
        this.playlistId = playlistId;
    }

    public static PlaylistEvent created(long playlistId) {
        return new PlaylistEvent(Action.CREATED, playlistId);
    }

    public static PlaylistEvent updated(long playlistId) {
        return new PlaylistEvent(Action.UPDATED, playlistId);
    }

    public static PlaylistEvent deleted(long playlistId) {
        return new PlaylistEvent(Action.DELETED, playlistId);
    }
}
