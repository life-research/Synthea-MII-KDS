package org.example.syntheakds.processing

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.example.syntheakds.config.SyntheaKDSConfig
import org.example.syntheakds.utils.Utils

import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class Processor<T> {

    private static final Logger logger = LogManager.getLogger(Processor.class)

    private final ObjectMapper objectMapper
    private final ThreadPoolExecutor pool
    private final List<T> items
    private final Consumer<T> task

    Processor(List<T> items, Consumer<T> task){
        this.objectMapper = new ObjectMapper()
        this.pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(4)
        this.items = items
        this.task = task
    }

    void run(){
        logger.info("[#]Running processor ...")
        var cnt = 0
        def n = this.items.size()
        Utils.writeFile("{\n", SyntheaKDSConfig.outputDirPath, "authored.json")
        this.items.each {item ->
            this.pool.execute(() -> task.accept(item))
        }

        this.pool.shutdown()

        try {
            def thread = Thread.start {new ProgressTask().accept(this.pool.queue)}
            this.pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
            thread.join()
        }
        catch (InterruptedException exc){
            logger.error("[!]Processor was interrupted while waiting for tasks to finish:\n${exc.getMessage()}")
        }
        def file = SyntheaKDSConfig.outputDirPath.resolve("authored.json").toFile()
        def content = file.text
        if (content.length() > 0) {
            file.text = content[0..-3]
        }
        Utils.writeFile("\n}", SyntheaKDSConfig.outputDirPath, "authored.json")
    }

}
