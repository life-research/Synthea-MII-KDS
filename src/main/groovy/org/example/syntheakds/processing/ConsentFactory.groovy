package org.example.syntheakds.processing

import org.hl7.fhir.r4.model.*
import java.time.Instant

class ConsentFactory {
        /**
     * Creates a HAPI FHIR R4 Consent resource based on MII Broad Consent principles.
     *
     * @param patientId The ID of the patient (will be used in the Reference).
     * @param consentDateString The date/time of the consent in FHIR dateTime format string (e.g., "2019-08-27T18:19:58+02:00").
     * @return A {@link org.hl7.fhir.r4.model.Consent} object, or null if inputs are invalid.
     */
    static Consent createConsentResource(String patientId, String consentDateString) {
        if (!patientId?.trim() || !consentDateString?.trim()) {
            System.err.println "Error: patientId and consentDateString cannot be null or empty."
            return null
        }

        final String policySystem = "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3"
        final List<String> requiredPolicyCodes = [
                "2.16.840.1.113883.3.1937.777.24.5.3.2", // IDAT erheben
                "2.16.840.1.113883.3.1937.777.24.5.3.3", // IDAT speichern, verarbeiten
                "2.16.840.1.113883.3.1937.777.24.5.3.6", // MDAT erheben
                "2.16.840.1.113883.3.1937.777.24.5.3.7"  // MDAT speichern, verarbeiten
        ]

        String consentId = UUID.randomUUID().toString()

        Consent consent = new Consent()

        consent.idElement = new IdType("Consent", consentId)

        consent.meta = new Meta().with {
            versionId = "1"
            lastUpdated = Date.from(Instant.now())
            it
        }

        consent.status = Consent.ConsentState.ACTIVE

        consent.scope = new CodeableConcept().with {
            addCoding().with { coding ->
                coding.system = "http://terminology.hl7.org/CodeSystem/consentscope"
                coding.code = "research"
                coding.display = "Research"
            }
            it
        }

        consent.category = [
                new CodeableConcept().with {
                    addCoding().with { coding ->
                        coding.system = "http://terminology.hl7.org/CodeSystem/consentcategorycodes"
                        coding.code = "rsdid"
                        coding.display = "Research Information Access"
                    }
                    it
                }
        ]

        consent.patient = new Reference("urn:uuid:${patientId}")

        try {
            consent.dateTimeElement = new DateTimeType(consentDateString)
        } catch (Exception e) {
            System.err.println "Error parsing consentDateString: ${consentDateString} - ${e.message}"
            return null
        }

        consent.policyRule = new CodeableConcept().with {
            addCoding().with { coding ->
                coding.system = policySystem
                coding.code = "MII_Broad_Consent"
            }
            it
        }

        consent.provision = new Consent.ProvisionComponent().with { provision ->
            provision.type = Consent.ConsentProvisionType.DENY

            provision.provision = requiredPolicyCodes.collect { policyCode ->
                new Consent.ProvisionComponent().with { subProv ->
                    subProv.type = Consent.ConsentProvisionType.PERMIT
                    subProv.code = [
                                     new CodeableConcept().with { codeableConcept ->
                                         codeableConcept.addCoding().with { coding ->
                                             coding.system = policySystem
                                             coding.code = policyCode
                                         }
                                         codeableConcept
                                     }
                    ]
                    subProv
                }
            }
            provision
        }

        return consent
    }
}
