package org.example.syntheakds.processing

import com.fasterxml.jackson.databind.JsonNode
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class DateExtractor {

    private static final Logger logger = LogManager.getLogger(DateExtractor.class)

    static String extract(JsonNode resourceNode) {
        switch (resourceNode.get("resourceType").asText()) {
            case "Patient":
                return null
            case "Condition":
                return resourceNode.get("recordedDate").asText()
            case "Observation":
                return resourceNode.get("effectiveDateTime").asText()
            case "Procedure":
                return extractDateFromProcedure(resourceNode)
            case "MedicationRequest":
                return resourceNode.get("authoredOn").asText()
            case "Encounter":
                return getEnd(resourceNode.get("period"))
            case "DiagnosticReport":
                return resourceNode.get("effectiveDateTime").asText()
            case "MedicationAdministration":
                return resourceNode.get("effectiveDateTime").asText()
            default:
                logger.debug("[!]No handling available for resource of type ${resourceNode.get("resourceType")}")
                return null
        }
    }

    static String extractDateFromProcedure(JsonNode node) {
        var period = node.get("performedPeriod")
        if (period != null) {
            getEnd(period)
        } else {
            return node.get("performedDateTime").asText()
        }
    }

    static String getEnd(JsonNode period) {
        var end = period.get("end")
        if (end != null) {
            return end.asText()
        } else {
            return null
        }
    }


}
