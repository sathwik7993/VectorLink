package com.shortener.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IdGeneratorServiceTest {

    private static final String NEXTVAL = "SELECT nextval('seq_short_urls')";

    /** Mimics 'seq_short_urls': starts at 1,000,000 and increments by the allocation size. */
    private JdbcTemplate sequenceBackedJdbc(AtomicLong sequence) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(NEXTVAL, Long.class))
                .thenAnswer(inv -> sequence.getAndAdd(IdGeneratorService.ALLOCATION_SIZE));
        return jdbc;
    }

    @Test
    void handsOutAWholeBlockPerDatabaseRoundTrip() {
        JdbcTemplate jdbc = sequenceBackedJdbc(new AtomicLong(1_000_000));
        IdGeneratorService generator = new IdGeneratorService(jdbc);

        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 2 * IdGeneratorService.ALLOCATION_SIZE; i++) {
            ids.add(generator.nextId());
        }

        assertThat(ids.get(0)).isEqualTo(999_001L);
        assertThat(ids.get(IdGeneratorService.ALLOCATION_SIZE - 1)).isEqualTo(1_000_000L);
        assertThat(ids.get(IdGeneratorService.ALLOCATION_SIZE)).isEqualTo(1_000_001L);
        assertThat(new HashSet<>(ids)).hasSize(ids.size());
        verify(jdbc, times(2)).queryForObject(NEXTVAL, Long.class);
    }

    @Test
    void twoInstancesSharingASequenceNeverCollide() throws Exception {
        AtomicLong sequence = new AtomicLong(1_000_000);
        IdGeneratorService a = new IdGeneratorService(sequenceBackedJdbc(sequence));
        IdGeneratorService b = new IdGeneratorService(sequenceBackedJdbc(sequence));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            IdGeneratorService target = (i % 2 == 0) ? a : b;
            futures.add(pool.submit(target::nextId));
        }
        Set<Long> ids = new HashSet<>();
        for (Future<Long> f : futures) {
            ids.add(f.get());
        }
        pool.shutdown();

        assertThat(ids).hasSize(10_000);
    }

    @Test
    void initAdvancesSequencePastLegacyIds() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM short_urls", Long.class)).thenReturn(2_500_000L);
        when(jdbc.queryForObject("SELECT last_value FROM seq_short_urls", Long.class)).thenReturn(1_000_000L);

        new IdGeneratorService(jdbc).init();

        verify(jdbc).queryForObject(eq("SELECT setval('seq_short_urls', ?)"), eq(Long.class), eq(2_501_000L));
    }

    @Test
    void initLeavesSequenceAloneWhenAlreadyAhead() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM short_urls", Long.class)).thenReturn(999_500L);
        when(jdbc.queryForObject("SELECT last_value FROM seq_short_urls", Long.class)).thenReturn(1_000_000L);

        new IdGeneratorService(jdbc).init();

        verify(jdbc, never()).queryForObject(eq("SELECT setval('seq_short_urls', ?)"), eq(Long.class), anyLong());
    }
}
