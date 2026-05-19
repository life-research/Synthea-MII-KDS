package org.example.syntheakds.processing;

import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.syntheakds.config.SyntheaKDSConfig;
import org.example.syntheakds.utils.Utils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public final class ProcessingTask implements Consumer<Path> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final IParser parser = SyntheaKDSConfig.ctx.newJsonParser().setPrettyPrint(true);

    private static final Logger logger = LogManager.getLogger(ProcessingTask.class);
    private static final Random random = new Random(42);
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Override
    public void accept(Path path) {
        logger.trace("path: {}", path.getFileName());
        String content;
        try {
            content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        JsonNode bundleEntry;
        try {
            bundleEntry = objectMapper.readTree(content).get("entry");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<String> dates = new ArrayList<>();
        List<Resource> instances = new ArrayList<>();
        String id = null;
        for (JsonNode entry : bundleEntry) {
            JsonNode resource = entry.get("resource");
            instances.add(Converter.convert(resource));
            if ("Patient".equals(resource.get("resourceType").asText())) {
                id = resource.get("id").asText();
            }
            dates.add(DateExtractor.extract(resource));
        }

        String fileName = path.getFileName().toString();
        if (!fileName.startsWith("practitionerInformation") && !fileName.startsWith("hospitalInformation")) {
            logger.trace("id {}", id);
            dates.removeIf(d -> d == null);
            Collections.sort(dates);
            String lastDate = dates.get(dates.size() - 1);

            OffsetDateTime year = OffsetDateTime.parse(lastDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME).minusYears(5);
            String date = year.format(OUTPUT_FORMATTER);
            Utils.writeFile("  \"" + id + "\": \"" + date + "\",\n", SyntheaKDSConfig.outputDirPath, "authored.json");
            instances.add(ConsentFactory.createConsentResource(id, date));

            int randomInt = random.nextInt(100);
            if (randomInt > 90) addConsent(lastDate, instances, id, 10);
            if (randomInt > 95) addConsent(lastDate, instances, id, 15);
            if (randomInt > 98) addConsent(lastDate, instances, id, 35);
        }

        Bundle bundle = FhirUtils.createBundle(instances);
        String json = parser.encodeResourceToString(bundle);
        Utils.writeFile(json, SyntheaKDSConfig.kdsDirPath, path.getFileName().toString());
    }

    private static void addConsent(String lastDate, List<Resource> instances, String id, int offset) {
        logger.info("Generate consent with {} years offset for {}", offset, id);
        OffsetDateTime year = OffsetDateTime.parse(lastDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME).minusYears(offset);
        String date = year.format(OUTPUT_FORMATTER);
        instances.add(ConsentFactory.createConsentResource(id, date));
    }
}
