package com.aireviewer.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JSON-file-backed history of completed analyses. Stores {@link AnalysisRecord}s as a single JSON
 * array in one file, read and rewritten in full on every change — appropriate for the
 * append-and-occasionally-browse workload this history actually sees (one write per completed
 * analysis, a handful of reads per session), not something that needs an index or a database.
 * <p>
 * Every write goes to a temporary file in the same directory first and is only then moved into
 * place, so a crash mid-write can never leave the history file half-written and unreadable — the
 * previous, still-complete version stays in place until the move succeeds. {@link #append} is
 * synchronized around its read-modify-write cycle so two callers appending at the same time can't
 * both read the same starting list and each silently overwrite the other's entry.
 */
public final class AnalysisHistoryStore {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisHistoryStore.class);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path historyFilePath;

    public AnalysisHistoryStore(Path historyFilePath) {
        this.historyFilePath = Objects.requireNonNull(historyFilePath, "historyFilePath");
    }

    /**
     * Appends {@code record} to the history. Never overwrites an existing entry implicitly — a
     * duplicate {@link AnalysisRecord#id()} simply results in two entries with the same id, since
     * assigning and de-duplicating ids is the caller's responsibility, not this store's.
     */
    public synchronized void append(AnalysisRecord record) throws PersistenceException {
        Objects.requireNonNull(record, "record");
        List<AnalysisRecord> updated = new ArrayList<>(listAll());
        updated.add(record);
        writeAll(updated);
        LOG.info("Appended analysis {} to history ({} entries total)", record.id(), updated.size());
    }

    /**
     * All stored records, oldest first. Empty (never {@code null}) if the history file does not
     * exist yet — that is the normal state before the first analysis ever completes.
     */
    public List<AnalysisRecord> listAll() throws PersistenceException {
        if (!Files.isRegularFile(historyFilePath)) {
            return List.of();
        }
        try {
            AnalysisRecord[] records = objectMapper.readValue(historyFilePath.toFile(), AnalysisRecord[].class);
            return List.of(records);
        } catch (IOException e) {
            throw new PersistenceException("Failed to read history file: " + historyFilePath, e);
        }
    }

    /**
     * The record with the given id, if one is stored.
     */
    public Optional<AnalysisRecord> findById(String id) throws PersistenceException {
        Objects.requireNonNull(id, "id");
        return listAll().stream().filter(record -> record.id().equals(id)).findFirst();
    }

    private void writeAll(List<AnalysisRecord> records) throws PersistenceException {
        try {
            Path parentDirectory = historyFilePath.toAbsolutePath().getParent();
            Files.createDirectories(parentDirectory);
            Path tempFile = Files.createTempFile(parentDirectory, "history", ".tmp");
            try {
                objectMapper.writeValue(tempFile.toFile(), records);
                Files.move(tempFile, historyFilePath,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new PersistenceException("Failed to write history file: " + historyFilePath, e);
        }
    }
}
