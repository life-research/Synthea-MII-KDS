package org.example.syntheakds.processing;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.syntheakds.config.SyntheaKDSConfig;
import org.example.syntheakds.processing.rxnorm.RxNormTranslator;
import org.example.syntheakds.utils.Utils;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class Converter {

    private static final Logger logger = LogManager.getLogger(Converter.class);

    private static final RxNormTranslator translator = new RxNormTranslator();

    private Converter() {}

    public static Resource convert(JsonNode resourceNode) {
        switch (resourceNode.get("resourceType").asText()) {
            case "Patient":
                return convertPatient(resourceNode);
            case "Condition":
                return convertCondition(resourceNode);
            case "Observation":
                return convertObservation(resourceNode);
            case "Procedure":
                return convertProcedure(resourceNode);
            case "MedicationRequest":
                return convertMedicationRequest(resourceNode);
            case "Encounter":
                return convertEncounter(resourceNode);
            case "DiagnosticReport":
                return convertDiagnosticReport(resourceNode);
            case "MedicationAdministration":
                return convertMedicationAdministration(resourceNode);
            default:
                logger.debug("[!]No handling available for resource of type {}", resourceNode.get("resourceType"));
                return null;
        }
    }

    private static Coding codingFromNode(JsonNode c) {
        JsonNode display = c.get("display");
        return new Coding(
                c.get("system").asText(),
                c.get("code").asText(),
                display == null ? null : display.asText());
    }

    private static CodeableConcept codeableFromCodingArray(JsonNode codingArray) {
        CodeableConcept cc = new CodeableConcept();
        for (JsonNode c : codingArray) {
            cc.addCoding(codingFromNode(c));
        }
        return cc;
    }

    public static Patient convertPatient(JsonNode patientNode) {
        Patient patient = new Patient();

        patient.getMeta().addProfile(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient");

        String id = patientNode.get("id").asText();
        JsonNode identifier = patientNode.get("identifier").get(1);
        patient.setId(id);

        patient.addIdentifier(new Identifier()
                .setSystem(SyntheaKDSConfig.patientIdentifierSystem)
                .setValue(identifier.get("value").asText()));

        CodeableConcept idType = new CodeableConcept().addCoding(
                new Coding("http://terminology.hl7.org/CodeSystem/v2-0203", "MR", "Medical Record Number"));
        patient.addIdentifier(new Identifier()
                .setUse(Identifier.IdentifierUse.OFFICIAL)
                .setType(idType)
                .setSystem(identifier.get("system").asText())
                .setValue(identifier.get("value").asText()));

        HumanName name = patient.addName();
        JsonNode nameNode = patientNode.get("name").get(0);
        name.setUse(HumanName.NameUse.OFFICIAL);
        name.setFamily(nameNode.get("family").asText());
        for (JsonNode givenNode : nameNode.get("given")) {
            name.getGiven().add(new StringType(givenNode.asText()));
        }

        switch (patientNode.get("gender").asText()) {
            case "female":
                patient.setGender(Enumerations.AdministrativeGender.FEMALE);
                break;
            case "male":
                patient.setGender(Enumerations.AdministrativeGender.MALE);
                break;
            default:
                patient.setGender(Enumerations.AdministrativeGender.UNKNOWN);
        }

        // Birth Date: YYYY-MM-DD
        String[] dateParts = patientNode.get("birthDate").asText().split("-");
        int year = Integer.parseInt(dateParts[0]);
        int month = Integer.parseInt(dateParts[1]);
        int day = Integer.parseInt(dateParts[2]);
        @SuppressWarnings("deprecation")
        Date birth = new Date(year - 1900, month - 1, day);
        patient.setBirthDate(birth);

        JsonNode address = patientNode.get("address").get(0);
        Address addr = patient.addAddress()
                .setUse(Address.AddressUse.HOME)
                .setType(Address.AddressType.BOTH);
        List<StringType> lines = new ArrayList<>();
        for (JsonNode line : address.get("line")) {
            lines.add(new StringType(line.asText()));
        }
        addr.setLine(lines);
        addr.setCity(address.get("city").asText());
        addr.setPostalCode("");
        addr.setCountry(address.get("country").asText());

        JsonNode maritalStatus = patientNode.get("maritalStatus").get("coding").get(0);
        patient.setMaritalStatus(new CodeableConcept(new Coding(
                maritalStatus.get("system").asText(),
                maritalStatus.get("code").asText(),
                maritalStatus.get("display").asText())));

        if (patientNode.has("multipleBirthBoolean")) {
            patient.setMultipleBirth(new BooleanType(patientNode.get("multipleBirthBoolean").asBoolean(false)));
        }
        if (patientNode.has("multipleBirthInteger")) {
            patient.setMultipleBirth(new IntegerType(patientNode.get("multipleBirthInteger").asInt(0)));
        }

        JsonNode language = patientNode.get("communication").get(0).get("language").get("coding").get(0);
        patient.addCommunication().setLanguage(new CodeableConcept(new Coding(
                language.get("system").asText(),
                language.get("code").asText(),
                language.get("display").asText())));

        return patient;
    }

    public static Condition convertCondition(JsonNode conditionNode) {
        Condition condition = new Condition();
        condition.getMeta().addProfile(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose");

        condition.setId(conditionNode.get("id").asText());

        CodeableConcept clinical = new CodeableConcept();
        for (JsonNode cs : conditionNode.get("clinicalStatus").get("coding")) {
            clinical.addCoding(new Coding()
                    .setSystem(cs.get("system").asText())
                    .setCode(cs.get("code").asText()));
        }
        condition.setClinicalStatus(clinical);

        CodeableConcept verification = new CodeableConcept();
        for (JsonNode cs : conditionNode.get("verificationStatus").get("coding")) {
            verification.addCoding(new Coding()
                    .setSystem(cs.get("system").asText())
                    .setCode(cs.get("code").asText()));
        }
        condition.setVerificationStatus(verification);

        for (JsonNode c : conditionNode.get("category").get(0).get("coding")) {
            condition.addCategory().addCoding(codingFromNode(c));
        }

        condition.setCode(codeableFromCodingArray(conditionNode.get("code").get("coding")));

        condition.setSubject(new Reference(conditionNode.get("subject").get("reference").asText()));
        condition.setEncounter(new Reference(conditionNode.get("encounter").get("reference").asText()));

        String onset = conditionNode.get("onsetDateTime").asText();
        JsonNode abatementNode = conditionNode.get("abatementDateTime");
        String abatement = abatementNode == null ? null : abatementNode.asText();
        String recorded = conditionNode.get("recordedDate").asText();
        condition.setOnset(new StringType(onset));
        condition.setAbatement(new StringType(abatement));
        condition.setRecordedDate(Utils.dateFromSyntheaDate(recorded));

        return condition;
    }

    public static Observation convertObservation(JsonNode observationNode) {
        Observation obs = new Observation();
        obs.setId(observationNode.get("id").asText());

        JsonNode category = observationNode.get("category").get(0).get("coding");
        obs.addCategory(codeableFromCodingArray(category));

        obs.setCode(codeableFromCodingArray(observationNode.get("code").get("coding")));

        obs.setSubject(new Reference(observationNode.get("subject").get("reference").asText()));
        obs.setEncounter(new Reference(observationNode.get("encounter").get("reference").asText()));

        String effective = observationNode.get("effectiveDateTime").asText();
        obs.setEffective(new DateTimeType(Utils.dateFromSyntheaDate(effective)));

        String issued = observationNode.get("issued").asText();
        obs.setIssued(Utils.dateFromSyntheaDate(issued));

        // Value: blood pressure has neither — handled by component below.
        if (observationNode.has("valueCodeableConcept")) {
            obs.setValue(codeableFromCodingArray(observationNode.get("valueCodeableConcept").get("coding")));
        }
        // NB: original Groovy checks the typo "valueQunatity" so this branch never fires on real data.
        // Preserved verbatim for behavior parity.
        if (observationNode.has("valueQunatity")) {
            JsonNode value = observationNode.get("valueQuantity");
            obs.setValue(new Quantity()
                    .setValue(value.get("value").asDouble())
                    .setUnit(value.get("unit").asText())
                    .setSystem(value.get("system").asText())
                    .setCode(value.get("code").asText()));
        }

        JsonNode components = observationNode.get("component");
        handleSurveyComponent(components, obs);

        String categoryCode = category.get(0).get("code").asText();
        switch (categoryCode) {
            case "survey":
            case "vital-signs":
            case "exam":
            case "social-history":
            case "therapy":
                obs.getMeta().addProfile(
                        "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Vitalstatus");
                obs.setStatus(Observation.ObservationStatus.FINAL);
                break;
            case "laboratory":
            case "procedure":
            case "imaging":
                obs.getMeta().addProfile(
                        "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab");
                obs.setStatus(Observation.ObservationStatus.fromCode(observationNode.get("status").asText()));
                break;
            default:
                logger.warn("[!]Observation resource couldn't be matched to fitting category: code: {}", categoryCode);
                logger.warn(observationNode.toPrettyString());
        }

        return obs;
    }

    private static void handleSurveyComponent(JsonNode componentNode, Observation obs) {
        if (componentNode == null) return;
        List<Observation.ObservationComponentComponent> comps = new ArrayList<>();
        for (JsonNode compNode : componentNode) {
            Observation.ObservationComponentComponent comp = new Observation.ObservationComponentComponent();
            comp.setCode(codeableFromCodingArray(compNode.get("code").get("coding")));

            if (compNode.has("valueCodeableConcept")) {
                comp.setValue(codeableFromCodingArray(compNode.get("valueCodeableConcept").get("coding")));
            }
            if (compNode.has("valueQuantity")) {
                JsonNode value = compNode.get("valueQuantity");
                comp.setValue(new Quantity()
                        .setValue(value.get("value").asDouble())
                        .setUnit(value.get("unit").asText())
                        .setSystem(value.get("system").asText())
                        .setCode(value.get("code").asText()));
            }
            comps.add(comp);
        }
        obs.setComponent(comps);
    }

    public static Procedure convertProcedure(JsonNode procedureNode) {
        Procedure proc = new Procedure();
        proc.getMeta().addProfile(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-prozedur/StructureDefinition/Procedure");

        proc.setId(procedureNode.get("id").asText());
        proc.setStatus(Procedure.ProcedureStatus.fromCode(procedureNode.get("status").asText()));
        proc.setCode(codeableFromCodingArray(procedureNode.get("code").get("coding")));

        proc.setSubject(new Reference(procedureNode.get("subject").get("reference").asText()));
        proc.setEncounter(new Reference(procedureNode.get("encounter").get("reference").asText()));

        JsonNode performed = procedureNode.get("performedPeriod");
        proc.setPerformed(new Period()
                .setStart(Utils.dateFromSyntheaDate(performed.get("start").asText()))
                .setEnd(Utils.dateFromSyntheaDate(performed.get("end").asText())));

        JsonNode location = procedureNode.get("location");
        proc.setLocation(new Reference()
                .setReference(location.get("reference").asText())
                .setDisplay(location.get("display").asText()));

        return proc;
    }

    public static MedicationRequest convertMedicationRequest(JsonNode medicationNode) {
        JsonNode medCodeable = medicationNode.get("medicationCodeableConcept");
        JsonNode medCodes = medCodeable == null ? null : medCodeable.get("coding");
        CodeableConcept medicationCodes = new CodeableConcept();
        boolean matchingCodes = false;
        if (medCodes != null) {
            for (JsonNode c : medCodes) {
                List<String> atcCodes = translator.translate(c.get("code").asText());
                if (!atcCodes.isEmpty()) {
                    matchingCodes = true;
                    for (String atcCode : atcCodes) {
                        medicationCodes.addCoding(new Coding()
                                .setSystem("http://www.whocc.no/atc")
                                .setCode(atcCode));
                    }
                }
            }
        }
        if (!matchingCodes) return null;

        MedicationRequest mr = new MedicationRequest();
        mr.getMeta().addProfile(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationRequest");

        mr.setId(medicationNode.get("id").asText());
        mr.setStatus(MedicationRequest.MedicationRequestStatus.fromCode(medicationNode.get("status").asText()));
        mr.setIntent(MedicationRequest.MedicationRequestIntent.fromCode(medicationNode.get("intent").asText()));
        mr.setMedication(medicationCodes);

        JsonNode medicationReference = medicationNode.get("medicationReference");
        if (medicationReference != null) {
            mr.setMedication(new Reference().setReference(medicationReference.get("reference").asText()));
        }

        mr.setSubject(new Reference(medicationNode.get("subject").get("reference").asText()));
        mr.setEncounter(new Reference(medicationNode.get("encounter").get("reference").asText()));
        mr.setAuthoredOn(Utils.dateFromSyntheaDate(medicationNode.get("authoredOn").asText()));

        JsonNode requester = medicationNode.get("requester");
        mr.setRequester(new Reference()
                .setReference(requester.get("reference").asText())
                .setDisplay(requester.get("display").asText()));

        return mr;
    }

    public static Encounter convertEncounter(JsonNode encounterNode) {
        Encounter enc = new Encounter();
        enc.getMeta().addProfile(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung");

        enc.setId(encounterNode.get("id").asText());

        List<Identifier> identifiers = new ArrayList<>();
        for (JsonNode i : encounterNode.get("identifier")) {
            identifiers.add(new Identifier()
                    .setUse(Identifier.IdentifierUse.fromCode(i.get("use").asText()))
                    .setSystem(i.get("system").asText())
                    .setValue(i.get("value").asText()));
        }
        enc.setIdentifier(identifiers);

        enc.setStatus(Encounter.EncounterStatus.fromCode(encounterNode.get("status").asText()));

        JsonNode encClass = encounterNode.get("class");
        enc.setClass_(new Coding()
                .setSystem(encClass.get("system").asText())
                .setCode(encClass.get("code").asText()));

        JsonNode typeCoding = encounterNode.get("type").get(0).get("coding");
        CodeableConcept typeCc = new CodeableConcept();
        for (JsonNode c : typeCoding) {
            JsonNode display = c.get("display");
            typeCc.addCoding(new Coding(
                    c.get("system").asText(),
                    c.get("code").asText(),
                    display == null ? null : display.asText()));
        }
        enc.addType(typeCc);

        JsonNode subjectRef = encounterNode.get("subject");
        enc.setSubject(new Reference()
                .setReference(subjectRef.get("reference").asText())
                .setDisplay(subjectRef.get("display").asText()));

        List<Encounter.EncounterParticipantComponent> participants = new ArrayList<>();
        for (JsonNode p : encounterNode.get("participant")) {
            Encounter.EncounterParticipantComponent epc = new Encounter.EncounterParticipantComponent();
            CodeableConcept pType = new CodeableConcept();
            for (JsonNode c : p.get("type").get(0).get("coding")) {
                pType.addCoding(new Coding()
                        .setSystem(c.get("system").asText())
                        .setCode(c.get("code").asText())
                        .setDisplay(c.get("display").asText()));
            }
            epc.addType(pType);

            JsonNode pPeriod = p.get("period");
            epc.setPeriod(new Period()
                    .setStart(Utils.dateFromSyntheaDate(pPeriod.get("start").asText()))
                    .setEnd(Utils.dateFromSyntheaDate(pPeriod.get("end").asText())));

            JsonNode individual = p.get("individual");
            epc.setIndividual(new Reference()
                    .setReference(individual.get("reference").asText())
                    .setDisplay(individual.get("display").asText()));
            participants.add(epc);
        }
        enc.setParticipant(participants);

        JsonNode periodNode = encounterNode.get("period");
        enc.setPeriod(new Period()
                .setStart(Utils.dateFromSyntheaDate(periodNode.get("start").asText()))
                .setEnd(Utils.dateFromSyntheaDate(periodNode.get("end").asText())));

        List<Encounter.EncounterLocationComponent> locations = new ArrayList<>();
        for (JsonNode l : encounterNode.get("location")) {
            Encounter.EncounterLocationComponent elc = new Encounter.EncounterLocationComponent();
            JsonNode elcLocation = l.get("location");
            elc.setLocation(new Reference()
                    .setReference(elcLocation.get("reference").asText())
                    .setDisplay(elcLocation.get("display").asText()));
            locations.add(elc);
        }
        enc.setLocation(locations);

        JsonNode serviceProvider = encounterNode.get("serviceProvider");
        enc.setServiceProvider(new Reference()
                .setReference(serviceProvider.get("reference").asText())
                .setDisplay(serviceProvider.get("display").asText()));

        return enc;
    }

    public static DiagnosticReport convertDiagnosticReport(JsonNode diagRepNode) {
        if (!diagRepNode.has("category")) return null;
        JsonNode category = diagRepNode.get("category").get(0).get("coding");
        boolean isLabReport = false;
        for (JsonNode c : category) {
            if ("LAB".equals(c.get("code").asText())) {
                isLabReport = true;
                break;
            }
        }
        if (!isLabReport) return null;

        DiagnosticReport dr = new DiagnosticReport();
        dr.getMeta().addProfile(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/DiagnosticReportLab");

        dr.setId(diagRepNode.get("id").asText());
        dr.setStatus(DiagnosticReport.DiagnosticReportStatus.fromCode(diagRepNode.get("status").asText()));

        CodeableConcept catCc = new CodeableConcept();
        for (JsonNode c : category) {
            catCc.addCoding(new Coding()
                    .setSystem(c.get("system").asText())
                    .setCode(c.get("code").asText())
                    .setDisplay(c.get("display").asText()));
        }
        dr.addCategory(catCc);

        CodeableConcept codeCc = new CodeableConcept();
        for (JsonNode c : diagRepNode.get("code").get("coding")) {
            codeCc.addCoding(new Coding()
                    .setSystem(c.get("system").asText())
                    .setCode(c.get("code").asText())
                    .setDisplay(c.get("display").asText()));
        }
        dr.setCode(codeCc);

        dr.setSubject(new Reference(diagRepNode.get("subject").get("reference").asText()));
        dr.setEncounter(new Reference(diagRepNode.get("encounter").get("reference").asText()));

        dr.setEffective(new DateTimeType(Utils.dateFromSyntheaDate(diagRepNode.get("effectiveDateTime").asText())));
        dr.setIssued(Utils.dateFromSyntheaDate(diagRepNode.get("issued").asText()));

        List<Reference> performers = new ArrayList<>();
        for (JsonNode r : diagRepNode.get("performer")) {
            performers.add(new Reference()
                    .setReference(r.get("reference").asText())
                    .setDisplay(r.get("display").asText()));
        }
        dr.setPerformer(performers);

        List<Reference> results = new ArrayList<>();
        for (JsonNode r : diagRepNode.get("result")) {
            results.add(new Reference()
                    .setReference(r.get("reference").asText())
                    .setDisplay(r.get("display").asText()));
        }
        dr.setResult(results);

        return dr;
    }

    public static MedicationAdministration convertMedicationAdministration(JsonNode medAdmNode) {
        JsonNode medCodes = medAdmNode.get("medicationCodeableConcept").get("coding");
        CodeableConcept medicationCodes = new CodeableConcept();
        boolean matchingCodes = false;
        for (JsonNode c : medCodes) {
            List<String> atcCodes = translator.translate(c.get("code").asText());
            if (!atcCodes.isEmpty()) {
                matchingCodes = true;
                for (String atcCode : atcCodes) {
                    medicationCodes.addCoding(new Coding()
                            .setSystem("http://www.whocc.no/atc")
                            .setCode(atcCode));
                }
            }
        }
        if (!matchingCodes) return null;

        MedicationAdministration ma = new MedicationAdministration();
        ma.setId(medAdmNode.get("id").asText());
        ma.getMeta().addProfile(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationAdministration");

        ma.setStatus(MedicationAdministration.MedicationAdministrationStatus.fromCode(
                medAdmNode.get("status").asText()));

        ma.setMedication(medicationCodes);

        ma.setSubject(new Reference(medAdmNode.get("subject").get("reference").asText()));
        ma.setContext(new Reference(medAdmNode.get("context").get("reference").asText()));
        ma.setEffective(new DateTimeType(Utils.dateFromSyntheaDate(medAdmNode.get("effectiveDateTime").asText())));

        List<Reference> reasons = new ArrayList<>();
        for (JsonNode r : medAdmNode.get("reasonReference")) {
            reasons.add(new Reference(r.get("reference").asText()));
        }
        ma.setReasonReference(reasons);

        return ma;
    }
}
