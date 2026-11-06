package de.danoeh.antennapod.net.sync.serviceinterface;

import java.util.List;
import java.util.Map;

public interface ISyncService {

    void login() throws SyncServiceException;

    SubscriptionChanges getSubscriptionChanges(long lastSync) throws SyncServiceException;

    UploadChangesResponse uploadSubscriptionChanges(
            List<String> addedFeeds, List<String> removedFeeds) throws SyncServiceException;

    EpisodeActionChanges getEpisodeActionChanges(long lastSync) throws SyncServiceException;

    UploadChangesResponse uploadEpisodeActions(List<EpisodeAction> queuedEpisodeActions)
            throws SyncServiceException;

    /**
     * Returns the account-scoped settings as a JSON object string,
     * or null if the backend does not support the settings API.
     */
    String getAccountSettings() throws SyncServiceException;

    /**
     * Uploads account-scoped settings.
     * This is a no-op if the backend does not support the settings API.
     */
    void uploadAccountSettings(Map<String, String> settings) throws SyncServiceException;

    void logout() throws SyncServiceException;
}
