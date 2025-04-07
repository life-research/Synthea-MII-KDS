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
        // Basic input validation (Groovy Truth for non-null/non-empty strings)
        if (!patientId?.trim() || !consentDateString?.trim()) {
            System.err.println "Error: patientId and consentDateString cannot be null or empty."
            return null // Or throw new IllegalArgumentException("Inputs cannot be blank")
        }

        // Policy details
        final String policySystem = "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3"
        // Groovy immutable list literal
        final List<String> requiredPolicyCodes = [
                "2.16.840.1.113883.3.1937.777.24.5.3.2", // IDAT erheben
                "2.16.840.1.113883.3.1937.777.24.5.3.3", // IDAT speichern, verarbeiten
                "2.16.840.1.113883.3.1937.777.24.5.3.6", // MDAT erheben
                "2.16.840.1.113883.3.1937.777.24.5.3.7"  // MDAT speichern, verarbeiten
        ]

        // Generate a UUID for the Consent resource
        String consentId = UUID.randomUUID().toString()

        // Create the main Consent object
        Consent consent = new Consent()

        // Use Groovy property access (automatically calls setters)
        consent.idElement = new IdType("Consent", consentId)

        // Set Meta using 'with' closure for conciseness
        consent.meta = new Meta().with {
            versionId = "1"
            lastUpdated = Date.from(Instant.now()) // HAPI uses java.util.Date
            // Consider adding a profile if applicable:
            // addProfile("http://your-fhir-server/StructureDefinition/YourConsentProfile")
            it // Return the meta object itself from the closure
        }

        // Set Status
        consent.status = Consent.ConsentState.ACTIVE

        // Set Scope (CodeableConcept) using 'with' on the added coding
        consent.scope = new CodeableConcept().with {
            addCoding().with { coding ->
                coding.system = "http://terminology.hl7.org/CodeSystem/consentscope"
                coding.code = "research"
                coding.display = "Research"
            }
            it
        }

        // Set Category (List<CodeableConcept>) using Groovy list literal
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

        // Set Patient Reference using GString for interpolation
        consent.patient = new Reference("urn:uuid:${patientId}")
//        consent.patient = new Reference().setIdentifier(new Identifier().setSystem("http://fts.smith.care").setValue(patientId));


        // You could also set the display name if known: consent.patient.display = "Patient Name"

        // Set DateTime - Use try-catch for parsing robustness
        try {
            consent.dateTimeElement = new DateTimeType(consentDateString)
        } catch (Exception e) {
            System.err.println "Error parsing consentDateString: ${consentDateString} - ${e.message}"
            return null // Or re-throw
        }

        // Set Policy Rule (CodeableConcept)
        consent.policyRule = new CodeableConcept().with {
            addCoding().with { coding ->
                coding.system = policySystem
                coding.code = "MII_Broad_Consent"
            }
            it
        }

        // --- Set Provision ---
        consent.provision = new Consent.ProvisionComponent().with { provision ->
            provision.type = Consent.ConsentProvisionType.PERMIT

            // Create nested provisions using Groovy's list iteration ('each') and collection literal
            provision.provision = requiredPolicyCodes.collect { policyCode -> // 'collect' transforms list items
                new Consent.ProvisionComponent().with { subProv ->
                    subProv.type = Consent.ConsentProvisionType.PERMIT // Assuming permit for all
                    // Set 'code' for sub-provision (List<CodeableConcept>)
                    subProv.code = [ // Groovy list literal
                                     new CodeableConcept().with { codeableConcept ->
                                         codeableConcept.addCoding().with { coding ->
                                             coding.system = policySystem
                                             coding.code = policyCode
                                         }
                                         codeableConcept // return concept from inner 'with'
                                     }
                    ]
                    subProv // return sub-provision from outer 'with'
                }
            }
            provision // return main provision from top 'with'
        }


        // Optional: Add performer/organization reference if known
        // consent.addPerformer(new Reference("Organization/your-org-id"))

        // Return the created HAPI FHIR Consent object
        return consent
    }
}
