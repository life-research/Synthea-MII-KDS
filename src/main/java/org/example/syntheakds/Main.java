package org.example.syntheakds;

import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.example.syntheakds.config.SyntheaKDSConfig;
import org.example.syntheakds.processing.AuthoredJsonSink;
import org.example.syntheakds.processing.BundleConverter;
import org.example.syntheakds.processing.NdjsonSink;
import org.example.syntheakds.utils.Utils;
import org.hl7.fhir.r4.model.Bundle;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.export.FhirR4;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final IParser KDS_PARSER = SyntheaKDSConfig.ctx.newJsonParser().setPrettyPrint(false);
    private static final ThreadLocal<IParser> SYNTHEA_PARSER =
            ThreadLocal.withInitial(() -> SyntheaKDSConfig.ctx.newJsonParser());

    private Main() {}

    public static void main(String[] args) throws Exception {
        configureLog4j2();
        logger.info("[#]Starting application ...");

        if (args.length < 1) {
            logger.error("[!]Usage: syntheakds <patientCount>");
            System.exit(2);
        }
        try {
            int n = Integer.parseInt(args[0]);
            if (n <= 0) throw new NumberFormatException("must be > 0");
            SyntheaKDSConfig.patientCount = n;
        } catch (NumberFormatException e) {
            logger.error("[!]Invalid patient count '{}': {}", args[0], e.getMessage());
            System.exit(2);
        }

        createDirs();

        GeneratorOptions options = configureGeneratorOptions();
        Generator generator = new Generator(options);

        int workers = SyntheaKDSConfig.threadPoolSize > 0
                ? SyntheaKDSConfig.threadPoolSize
                : Runtime.getRuntime().availableProcessors();
        Path ndjsonPath = SyntheaKDSConfig.kdsDirPath.resolve("bundles.ndjson.gz");
        Path authoredPath = SyntheaKDSConfig.outputDirPath.resolve("authored.json");

        logger.info("[#]===== Run start =====");
        logger.info("[#]patients={} workers={} seed={} state={}",
                SyntheaKDSConfig.patientCount, workers, SyntheaKDSConfig.seed, SyntheaKDSConfig.state);
        logger.info("[#]endTime={} referenceTime={} identifierSystem={}",
                SyntheaKDSConfig.endTime, SyntheaKDSConfig.referenceTime, SyntheaKDSConfig.patientIdentifierSystem);
        logger.info("[#]outputDir={}", SyntheaKDSConfig.outputDirPath);
        logger.info("[#]ndjson={}", ndjsonPath);
        logger.info("[#]authored={}", authoredPath);
        logger.info("[#]Streaming {} patients through {} workers ...", SyntheaKDSConfig.patientCount, workers);

        long start = System.currentTimeMillis() / 1000L;
        final int target = SyntheaKDSConfig.patientCount;
        final int maxAttempts = target * 2;
        AtomicInteger nextIdx = new AtomicInteger();
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger capExceeded = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(target);

        try (NdjsonSink ndjson = new NdjsonSink(ndjsonPath);
             AuthoredJsonSink authored = new AuthoredJsonSink(authoredPath)) {

            ExecutorService pool = Executors.newFixedThreadPool(workers);
            Thread progress = startProgressThread(succeeded, failed, target);

            Runnable[] taskHolder = new Runnable[1];
            taskHolder[0] = () -> {
                if (attempts.incrementAndGet() > maxAttempts) {
                    capExceeded.incrementAndGet();
                    latch.countDown();
                    return;
                }
                int idx = nextIdx.getAndIncrement();
                try {
                    Person person = generator.generatePerson(idx);
                    Bundle synBundle = FhirR4.convertToFHIR(person, SyntheaKDSConfig.endTime);
                    String synJson = SYNTHEA_PARSER.get().encodeResourceToString(synBundle);
                    JsonNode entries = MAPPER.readTree(synJson).get("entry");

                    BundleConverter.Result result = BundleConverter.process(entries);

                    ndjson.writeLine(KDS_PARSER.encodeResourceToString(result.bundle));
                    authored.put(result.patientId, result.consentDate);
                    succeeded.incrementAndGet();
                    latch.countDown();
                } catch (Throwable t) {
                    failed.incrementAndGet();
                    logger.error("[!]Patient {} failed, scheduling replacement", idx, t);
                    try {
                        pool.execute(taskHolder[0]);
                    } catch (RejectedExecutionException rex) {
                        latch.countDown();
                    }
                }
            };

            for (int i = 0; i < target; i++) pool.execute(taskHolder[0]);

            latch.await();
            pool.shutdown();
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            progress.interrupt();
            progress.join();
        }

        long timeSpan = (System.currentTimeMillis() / 1000L) - start;
        int succeededCount = succeeded.get();
        int failedCount = failed.get();
        int attemptsCount = attempts.get();
        if (capExceeded.get() > 0) {
            logger.warn("[!]Hit retry cap ({}). succeeded={} target={}", maxAttempts, succeededCount, target);
        }
        logger.info("[#]Done in {}m {}s. attempts={} succeeded={} failed={} target={}",
                (int) Math.floor(timeSpan / 60.0), timeSpan % 60, attemptsCount, succeededCount, failedCount, target);
        logger.info("[#]ndjson={}", ndjsonPath);
        logger.info("[#]authored={}", authoredPath);
        logger.info("[#]===== Run end =====");
    }

    private static GeneratorOptions configureGeneratorOptions() {
        GeneratorOptions options = new GeneratorOptions();
        options.population = SyntheaKDSConfig.patientCount;
        options.clinicianSeed = SyntheaKDSConfig.clinicianSeed;
        options.referenceTime = SyntheaKDSConfig.referenceTime;
        options.endTime = SyntheaKDSConfig.endTime;
        options.threadPoolSize = SyntheaKDSConfig.threadPoolSize;
        options.seed = SyntheaKDSConfig.seed;
        options.state = SyntheaKDSConfig.state;

        // Disable every Synthea exporter — we pull bundles directly via FhirR4.convertToFHIR.
        Config.set("exporter.ccda.export", "false");
        Config.set("exporter.fhir.export", "false");
        Config.set("exporter.fhir_stu3.export", "false");
        Config.set("exporter.fhir_dstu2.export", "false");
        Config.set("exporter.hospital.fhir.export", "false");
        Config.set("exporter.practitioner.fhir.export", "false");
        Config.set("exporter.cpcds.export", "false");
        Config.set("exporter.csv.export", "false");
        Config.set("exporter.text.export", "false");
        Config.set("exporter.json.export", "false");
        Config.set("exporter.symptoms.csv.export", "false");
        Config.set("exporter.symptoms.text.export", "false");
        Config.set("exporter.clinical_note.export", "false");
        Config.set("generate.database_type", "none");
        Config.set("generate.only_dead_patients", "false");
        Config.set("generate.only_alive_patients", "true");
        return options;
    }

    private static Thread startProgressThread(AtomicInteger succeeded, AtomicInteger failed, int total) {
        Thread t = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    int done = succeeded.get();
                    int bars = total == 0 ? 50 : (int) ((done / (double) total) * 50);
                    String bar = "#".repeat(bars) + " ".repeat(50 - bars);
                    System.out.printf("Progress: [%s] %d/%d (%d failed)\r", bar, done, total, failed.get());
                    if (done >= total) break;
                    Thread.sleep(500);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            System.out.println();
        }, "progress");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void configureLog4j2() {
        Path configFilePath = Paths.get("config", "log4j2.xml");
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.setConfigLocation(Utils.findResourceURI(configFilePath));
    }

    private static void createDirs() throws IOException {
        java.nio.file.Files.createDirectories(SyntheaKDSConfig.outputDirPath);
        java.nio.file.Files.createDirectories(SyntheaKDSConfig.kdsDirPath);
    }
}
