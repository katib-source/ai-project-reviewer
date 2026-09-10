package com.aireviewer.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisHistoryStoreTest {

    private static AnalysisRecord recordWithId(String id) {
        CriterionSummary criterion = new CriterionSummary("naming", "Naming conventions", 7.5, "Mostly consistent.");
        return new AnalysisRecord(id, "demo-project", "/tmp/demo-project", Instant.EPOCH, 8.0, List.of(criterion));
    }

    @Test
    void listAllOnANeverWrittenFileReturnsAnEmptyList(@TempDir Path tempDir) throws PersistenceException {
        AnalysisHistoryStore store = new AnalysisHistoryStore(tempDir.resolve("history.json"));

        assertEquals(List.of(), store.listAll());
    }

    @Test
    void appendThenListAllRoundTripsARecord(@TempDir Path tempDir) throws PersistenceException {
        AnalysisHistoryStore store = new AnalysisHistoryStore(tempDir.resolve("history.json"));
        AnalysisRecord record = recordWithId("analysis-1");

        store.append(record);

        assertEquals(List.of(record), store.listAll());
    }

    @Test
    void appendAddsToExistingEntriesRatherThanReplacingThem(@TempDir Path tempDir) throws PersistenceException {
        AnalysisHistoryStore store = new AnalysisHistoryStore(tempDir.resolve("history.json"));

        store.append(recordWithId("analysis-1"));
        store.append(recordWithId("analysis-2"));

        assertEquals(List.of("analysis-1", "analysis-2"),
                store.listAll().stream().map(AnalysisRecord::id).toList());
    }

    @Test
    void createsMissingParentDirectories(@TempDir Path tempDir) throws PersistenceException {
        Path nested = tempDir.resolve("nested/history/history.json");
        AnalysisHistoryStore store = new AnalysisHistoryStore(nested);

        store.append(recordWithId("analysis-1"));

        assertTrue(Files.isRegularFile(nested));
    }

    @Test
    void findByIdReturnsTheMatchingRecord(@TempDir Path tempDir) throws PersistenceException {
        AnalysisHistoryStore store = new AnalysisHistoryStore(tempDir.resolve("history.json"));
        store.append(recordWithId("analysis-1"));
        AnalysisRecord target = recordWithId("analysis-2");
        store.append(target);

        assertEquals(target, store.findById("analysis-2").orElseThrow());
    }

    @Test
    void findByIdReturnsEmptyWhenNoRecordMatches(@TempDir Path tempDir) throws PersistenceException {
        AnalysisHistoryStore store = new AnalysisHistoryStore(tempDir.resolve("history.json"));
        store.append(recordWithId("analysis-1"));

        assertTrue(store.findById("does-not-exist").isEmpty());
    }

    @Test
    void listAllThrowsOnAMalformedHistoryFile(@TempDir Path tempDir) throws Exception {
        Path historyFile = tempDir.resolve("history.json");
        Files.writeString(historyFile, "{ not valid json ");
        AnalysisHistoryStore store = new AnalysisHistoryStore(historyFile);

        assertThrows(PersistenceException.class, store::listAll);
    }

    @Test
    void concurrentAppendsNeverLoseAnEntry(@TempDir Path tempDir) throws Exception {
        AnalysisHistoryStore store = new AnalysisHistoryStore(tempDir.resolve("history.json"));
        int writerCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(writerCount);
        CountDownLatch startLine = new CountDownLatch(1);

        List<Runnable> tasks = IntStream.range(0, writerCount)
                .mapToObj(i -> (Runnable) () -> {
                    try {
                        startLine.await();
                        store.append(recordWithId("analysis-" + i));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (PersistenceException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
        tasks.forEach(pool::execute);

        startLine.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        Set<String> storedIds = store.listAll().stream().map(AnalysisRecord::id).collect(Collectors.toSet());
        Set<String> expectedIds = IntStream.range(0, writerCount)
                .mapToObj(i -> "analysis-" + i)
                .collect(Collectors.toSet());
        assertEquals(expectedIds, storedIds);
    }
}
