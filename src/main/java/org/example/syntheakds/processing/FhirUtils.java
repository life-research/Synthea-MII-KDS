package org.example.syntheakds.processing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

import java.util.List;
import java.util.Set;

public final class FhirUtils {

    private static final Logger logger = LogManager.getLogger(FhirUtils.class);

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "Patient", "Condition", "Observation", "Procedure",
            "MedicationRequest", "Encounter", "DiagnosticReport",
            "MedicationAdministration", "Consent"
    );

    private FhirUtils() {}

    public static Bundle createBundle(List<Resource> resources) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);

        for (Resource r : resources) {
            if (r == null) continue;
            String type = r.fhirType();
            if (SUPPORTED_TYPES.contains(type)) {
                addToBundle(r, type, bundle);
            } else {
                logger.warn("[!]Resource of type <<{}>> couldn't be handled!", type);
            }
        }

        return bundle;
    }

    private static void addToBundle(Resource resource, String resourceType, Bundle bundle) {
        bundle.addEntry()
                .setFullUrl("urn:uuid:" + resource.getIdElement().getValue())
                .setResource(resource)
                .getRequest()
                .setUrl(resourceType)
                .setMethod(Bundle.HTTPVerb.POST);
    }
}
