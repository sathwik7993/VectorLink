package com.shortener.repository;

import com.shortener.entity.ClickLog;
import java.util.List;

public interface CustomClickLogRepository {
    /**
     * Inserts click logs, skipping duplicates by (event_id, clicked_at).
     * Returns one entry per input row: 0 if the row was a duplicate, non-zero if it was inserted.
     */
    int[] batchInsert(List<ClickLog> clickLogs);
}
