package me.hesamai.advancedduty.duty.history;

import java.util.List;
import java.util.UUID;

/** Persistence of finished duty sessions. Blocking - call from the storage thread. */
public interface HistoryStorage {

    void append(SessionRecord record, int maxEntriesPerPlayer) throws Exception;

    /** Newest first. */
    List<SessionRecord> load(UUID uuid, int limit, int offset) throws Exception;

    int count(UUID uuid) throws Exception;

    /** Most recent sessions of everybody, newest first. */
    List<SessionRecord> loadRecent(int limit) throws Exception;

    void close();
}
