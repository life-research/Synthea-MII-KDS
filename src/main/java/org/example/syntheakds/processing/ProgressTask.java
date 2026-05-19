package org.example.syntheakds.processing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.function.Consumer;

public final class ProgressTask implements Consumer<Queue<Object>> {

    private static final Logger logger = LogManager.getLogger(ProgressTask.class);

    @Override
    public void accept(Queue<Object> q) {
        if (q.size() <= 0) return;
        double factor = 50.0 / q.size();
        try {
            do {
                int remaining = 0;
                if (q.size() > 0) remaining = Math.max((int) Math.floor(q.size() * factor), 0);
                String bar = "#".repeat(50 - remaining) + " ".repeat(remaining);
                System.out.print("Progress: [" + bar + "]\r");
                Thread.sleep(250);
            } while (q.size() > 0);
            System.out.print("Progress: [" + "#".repeat(50) + "]\r");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("[!]Progress task interrupted");
        }
    }
}
