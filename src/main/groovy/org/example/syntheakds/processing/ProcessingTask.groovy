package org.example.syntheakds.processing

import ca.uhn.fhir.parser.IParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.example.syntheakds.config.SyntheaKDSConfig
import org.example.syntheakds.utils.Utils

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Consumer

class ProcessingTask implements Consumer<Path> {

    private static final ObjectMapper objectMapper = new ObjectMapper()
    private static final IParser parser = SyntheaKDSConfig.ctx.newJsonParser().setPrettyPrint(true)

    private static final Logger logger = LogManager.getLogger(Converter.class)

    @Override
    void accept(Path path) {
        logger.trace("path: {}", path.fileName)
        // Read whole file into memory
        def content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
        def bundleEntry = objectMapper.readTree(content).get("entry")

        // Process content
        def dates = []
        def instances = []
        String id = null
        bundleEntry.each { entry ->

            def resource = entry.get("resource")
            instances << Converter.convert(resource)

            if (resource.get("resourceType").asText() == "Patient") {
                id = resource.get("id")
            }

            dates << DateExtractor.extract(resource)
        }


        logger.trace("id {}", id)
        if ((!path.fileName.toString().startsWith("practitionerInformation") && !path.fileName.toString().startsWith("hospitalInformation"))) {
            dates -= null
            dates = dates.sort()
            def lastDate = dates[-1]
            def year = OffsetDateTime.parse(lastDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME).minusYears(5)
            Utils.writeFile("  " + id + ": \"" + year.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")) + "\",\n", SyntheaKDSConfig.outputDirPath, "authored.json")
        }

        // Create and write bundle
        def bundle = FhirUtils.createBundle(instances)

        def json = parser.encodeResourceToString(bundle)
        Utils.writeFile(json, SyntheaKDSConfig.kdsDirPath, path.getFileName().toString())
    }
}
