package com.shortener.repository;

import com.shortener.entity.ClickLog;
import java.util.List;

public interface CustomClickLogRepository {
    void batchInsert(List<ClickLog> clickLogs);
}
