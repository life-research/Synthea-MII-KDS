package org.example.syntheakds.processing;

import com.fasterxml.jackson.databind.JsonNode;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Per-patient conversion pipeline. Consumes the entry array of a Synthea bundle (Jackson)
 * and produces a KDS-profiled HAPI Bundle plus the consent date string written to authored.json.
 */
public final class BundleConverter {

    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final Random random = new Random(42);

    public static final class Result {
        public final Bundle bundle;
        public final String patientId;
        public final String consentDate;

        public Result(Bundle bundle, String patientId, String consentDate) {
            this.bundle = bundle;
            this.patientId = patientId;
            this.consentDate = consentDate;
        }
    }

    private BundleConverter() {}

    /**
     * @param entries entry array node of a Synthea FHIR R4 bundle
     * @return KDS bundle plus consent metadata (consentDate may be null for non-patient bundles)
     */
    public static Result process(JsonNode entries) {
        List<String> dates = new ArrayList<>();
        List<Resource> instances = new ArrayList<>();
        String id = null;

        for (JsonNode entry : entries) {
            JsonNode resource = entry.get("resource");
            instances.add(Converter.convert(resource));
            if ("Patient".equals(resource.get("resourceType").asText())) {
                id = resource.get("id").asText();
            }
            dates.add(DateExtractor.extract(resource));
        }

        String consentDate = null;
        if (id != null) {
            dates.removeIf(d -> d == null);
            Collections.sort(dates);
            if (!dates.isEmpty()) {
                String lastDate = dates.get(dates.size() - 1);
                OffsetDateTime year = OffsetDateTime.parse(lastDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME).minusYears(5);
                consentDate = year.format(OUTPUT_FORMATTER);
                instances.add(ConsentFactory.createConsentResource(id, consentDate));

                int randomInt;
                synchronized (random) {
                    randomInt = random.nextInt(100);
                }
                if (randomInt > 90) addConsent(lastDate, instances, id, 10);
                if (randomInt > 95) addConsent(lastDate, instances, id, 15);
                if (randomInt > 98) addConsent(lastDate, instances, id, 35);
            }
        }

        Bundle bundle = FhirUtils.createBundle(instances);
        return new Result(bundle, id, consentDate);
    }

    private static void addConsent(String lastDate, List<Resource> instances, String id, int offset) {
        OffsetDateTime year = OffsetDateTime.parse(lastDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME).minusYears(offset);
        String date = year.format(OUTPUT_FORMATTER);
        instances.add(ConsentFactory.createConsentResource(id, date));
    }
}
