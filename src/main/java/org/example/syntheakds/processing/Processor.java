package org.example.syntheakds.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.syntheakds.config.SyntheaKDSConfig;
import org.example.syntheakds.utils.Utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Processor<T> {

    private static final Logger logger = LogManager.getLogger(Processor.class);

    private final ObjectMapper objectMapper;
    private final ThreadPoolExecutor pool;
    private final List<T> items;
    private final Consumer<T> task;

    public Processor(List<T> items, Consumer<T> task) {
        this.objectMapper = new ObjectMapper();
        this.pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
        this.items = items;
        this.task = task;
    }

    public void run() {
        logger.info("[#]Running processor ...");
        Utils.writeFile("{\n", SyntheaKDSConfig.outputDirPath, "authored.json");
        for (T item : this.items) {
            this.pool.execute(() -> task.accept(item));
        }

        this.pool.shutdown();

        Thread progressThread = null;
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Queue<Object> queue = (Queue<Object>) (Queue) this.pool.getQueue();
            progressThread = new Thread(() -> new ProgressTask().accept(queue));
            progressThread.start();
            this.pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            progressThread.join();
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            logger.error("[!]Processor was interrupted while waiting for tasks to finish:\n{}", exc.getMessage());
        }

        Path file = SyntheaKDSConfig.outputDirPath.resolve("authored.json");
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.length() > 0) {
                // Strip trailing ",\n" of last appended entry — matches Groovy content[0..-3]
                String trimmed = content.substring(0, content.length() - 2);
                Files.writeString(file, trimmed, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Utils.writeFile("\n}", SyntheaKDSConfig.outputDirPath, "authored.json");
    }
}
