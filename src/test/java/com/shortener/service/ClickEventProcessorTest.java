package com.shortener.service;

import com.shortener.dto.ClickEvent;
import com.shortener.entity.DailyUrlAnalytics;
import com.shortener.repository.ClickLogRepository;
import com.shortener.repository.DailyUrlAnalyticsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ClickEventProcessorTest {

    private final ClickLogRepository clickLogRepository = mock(ClickLogRepository.class);
    private final DailyUrlAnalyticsRepository dailyRepository = mock(DailyUrlAnalyticsRepository.class);
    private final ClickEventProcessor processor = new ClickEventProcessor(clickLogRepository, dailyRepository);

    private static ClickEvent event(String id) {
        return new ClickEvent("abc123", Instant.parse("2026-10-29T10:00:00Z"), null, "Mozilla",
                "10.0.0.1", "IN", "desktop", id, "Chrome", "Windows", 0.0, 0.0, "Mumbai", "Maharashtra");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aggregatesOnlyNewlyInsertedEvents() {
        // Second event is a redelivery: insert reports 0 rows
        when(clickLogRepository.batchInsert(anyList())).thenReturn(new int[]{1, 0, 1});

        processor.processEvents(List.of(event("e1"), event("e2"), event("e3")));

        ArgumentCaptor<List<DailyUrlAnalytics>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailyRepository).batchUpsert(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getClickCount()).isEqualTo(2L);
        assertThat(captor.getValue().get(0).getBrowserChromeCount()).isEqualTo(2);
    }

    @Test
    void skipsAggregationWhenEveryEventIsADuplicate() {
        when(clickLogRepository.batchInsert(anyList())).thenReturn(new int[]{0, 0});

        processor.processEvents(List.of(event("e1"), event("e2")));

        verify(dailyRepository, never()).batchUpsert(anyList());
    }
}
