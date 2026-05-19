package org.example.syntheakds.processing;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Thread-safe line-appender. One JSON resource per line.
 */
public final class NdjsonSink implements AutoCloseable {

    private final BufferedWriter writer;

    public NdjsonSink(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public synchronized void writeLine(String json) throws IOException {
        writer.write(json);
        writer.write('\n');
    }

    @Override
    public synchronized void close() throws IOException {
        writer.flush();
        writer.close();
    }
}
