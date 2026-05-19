package org.example.syntheakds.processing;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class ConsentFactory {

    private ConsentFactory() {}

    /**
     * Creates a HAPI FHIR R4 Consent resource based on MII Broad Consent principles.
     *
     * @param patientId         patient ID used in the Reference.
     * @param consentDateString consent date/time in FHIR dateTime format (e.g. "2019-08-27T18:19:58+02:00").
     * @return a {@link Consent}, or null if inputs are invalid.
     */
    public static Consent createConsentResource(String patientId, String consentDateString) {
        if (patientId == null || patientId.trim().isEmpty()
                || consentDateString == null || consentDateString.trim().isEmpty()) {
            System.err.println("Error: patientId and consentDateString cannot be null or empty.");
            return null;
        }

        final String policySystem = "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3";
        final List<String> requiredPolicyCodes = List.of(
                "2.16.840.1.113883.3.1937.777.24.5.3.2", // IDAT erheben
                "2.16.840.1.113883.3.1937.777.24.5.3.3", // IDAT speichern, verarbeiten
                "2.16.840.1.113883.3.1937.777.24.5.3.6", // MDAT erheben
                "2.16.840.1.113883.3.1937.777.24.5.3.7"  // MDAT speichern, verarbeiten
        );

        String consentId = UUID.randomUUID().toString();

        Consent consent = new Consent();
        consent.setIdElement(new IdType("Consent", consentId));

        Meta meta = new Meta();
        meta.setVersionId("1");
        meta.setLastUpdated(Date.from(Instant.now()));
        consent.setMeta(meta);

        consent.setStatus(Consent.ConsentState.ACTIVE);

        CodeableConcept scope = new CodeableConcept();
        scope.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/consentscope")
                .setCode("research")
                .setDisplay("Research");
        consent.setScope(scope);

        CodeableConcept category = new CodeableConcept();
        category.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/consentcategorycodes")
                .setCode("rsdid")
                .setDisplay("Research Information Access");
        List<CodeableConcept> categories = new ArrayList<>();
        categories.add(category);
        consent.setCategory(categories);

        consent.setPatient(new Reference("urn:uuid:" + patientId));

        try {
            consent.setDateTimeElement(new DateTimeType(consentDateString));
        } catch (Exception e) {
            System.err.println("Error parsing consentDateString: " + consentDateString + " - " + e.getMessage());
            return null;
        }

        CodeableConcept policyRule = new CodeableConcept();
        policyRule.addCoding()
                .setSystem(policySystem)
                .setCode("MII_Broad_Consent");
        consent.setPolicyRule(policyRule);

        Consent.ProvisionComponent provision = new Consent.ProvisionComponent();
        provision.setType(Consent.ConsentProvisionType.DENY);

        List<Consent.ProvisionComponent> subProvisions = new ArrayList<>();
        for (String policyCode : requiredPolicyCodes) {
            Consent.ProvisionComponent subProv = new Consent.ProvisionComponent();
            subProv.setType(Consent.ConsentProvisionType.PERMIT);

            CodeableConcept subCode = new CodeableConcept();
            subCode.addCoding()
                    .setSystem(policySystem)
                    .setCode(policyCode);
            List<CodeableConcept> subCodes = new ArrayList<>();
            subCodes.add(subCode);
            subProv.setCode(subCodes);

            Period period = new Period();
            period.setStartElement(new DateTimeType(consentDateString));

            // End date = start + 30 years
            DateTimeType startDateTime = new DateTimeType(consentDateString);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(startDateTime.getValue());
            calendar.add(Calendar.YEAR, 30);

            DateTimeType endDateTime = new DateTimeType();
            endDateTime.setValue(calendar.getTime());
            period.setEndElement(endDateTime);

            subProv.setPeriod(period);
            subProvisions.add(subProv);
        }
        provision.setProvision(subProvisions);
        consent.setProvision(provision);

        return consent;
    }
}
