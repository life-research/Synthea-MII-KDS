package org.example.syntheakds.processing;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPOutputStream;

/**
 * Thread-safe gzipped NDJSON writer. One JSON resource per line, whole stream wrapped in a single
 * gzip member for best compression ratio across lines.
 */
public final class NdjsonSink implements AutoCloseable {

    private static final int GZIP_BUFFER = 64 * 1024;

    private final BufferedWriter writer;

    public NdjsonSink(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        GZIPOutputStream gzip = new GZIPOutputStream(
                Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
                GZIP_BUFFER);
        this.writer = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8), GZIP_BUFFER);
    }

    public synchronized void writeLine(String json) throws IOException {
        writer.write(json);
        writer.write('\n');
    }

    @Override
    public synchronized void close() throws IOException {
        writer.close();
    }
}
