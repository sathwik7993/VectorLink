package com.shortener.repository;

import com.shortener.entity.ClickLog;
import com.shortener.entity.ClickLogKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClickLogRepository extends JpaRepository<ClickLog, ClickLogKey>, CustomClickLogRepository {
}
