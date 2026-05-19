package org.example.syntheakds.processing;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accumulates patient-id → consent-date pairs in memory, flushes a single JSON object on close.
 */
public final class AuthoredJsonSink implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path file;
    private final Map<String, String> entries = new ConcurrentHashMap<>();

    public AuthoredJsonSink(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        this.file = file;
    }

    public void put(String patientId, String consentDate) {
        if (patientId == null || consentDate == null) return;
        entries.put(patientId, consentDate);
    }

    @Override
    public void close() throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            root.put(e.getKey(), e.getValue());
        }
        MAPPER.writer(new DefaultPrettyPrinter()).writeValue(file.toFile(), root);
    }
}
