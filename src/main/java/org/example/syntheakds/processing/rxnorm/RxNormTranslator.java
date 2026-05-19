package org.example.syntheakds.processing.rxnorm;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.syntheakds.config.SyntheaKDSConfig;
import org.example.syntheakds.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RxNormTranslator {

    private static final Logger logger = LogManager.getLogger(RxNormTranslator.class);

    private static final String baseUrl = "https://rxnav.nlm.nih.gov/REST/rxcui/";
    private static final Path mapPath = SyntheaKDSConfig.mapDirPath.resolve(Paths.get("rxnorm", "mapping_atc.json"));
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<String, List<String>> cachedAnswers = loadCache();

    public List<String> translate(String rxnormCode) {
        List<String> atcCodes = cachedAnswers.get(rxnormCode);
        if (atcCodes == null) {
            atcCodes = lookupCode(rxnormCode);
            cachedAnswers.put(rxnormCode, atcCodes);
            return atcCodes;
        } else if (atcCodes.isEmpty()) {
            return Collections.emptyList();
        } else {
            return atcCodes;
        }
    }

    private static Map<String, List<String>> loadCache() {
        Map<String, List<String>> map = new ConcurrentHashMap<>();
        File file = mapPath.toFile();
        JsonNode root;
        try {
            if (file.exists()) {
                logger.debug("[+]ATC mapping file four rxNorm codes found.");
                root = objectMapper.readTree(file);
            } else {
                logger.info("[+]No ATC mapping file for rxNorm codes could be found. Using fallback.");
                InputStream stream = Utils.findResource(Paths.get("rxnorm", "mapping_atc.json"));
                if (stream != null) {
                    root = objectMapper.readTree(stream);
                } else {
                    return map;
                }
                file.toPath().getParent().toFile().mkdirs();
            }

            for (JsonNode codeNode : root.get("codes")) {
                List<String> atcs = new ArrayList<>();
                for (JsonNode c : codeNode.get("atc")) {
                    atcs.add(c.asText());
                }
                map.put(codeNode.get("rxnorm").asText(), atcs);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Persist new lookups on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.debug("[+]Writing mapping file.");
            ArrayNode listNode = objectMapper.createArrayNode();
            ObjectNode cacheRoot = objectMapper.createObjectNode();
            cacheRoot.set("codes", listNode);
            for (Map.Entry<String, List<String>> entry : cachedAnswers.entrySet()) {
                ArrayNode atcNode = objectMapper.createArrayNode();
                ObjectNode entryNode = objectMapper.createObjectNode();
                entryNode.set("rxnorm", new TextNode(entry.getKey()));
                entryNode.set("atc", atcNode);
                for (String v : entry.getValue()) {
                    atcNode.add(v);
                }
                listNode.add(entryNode);
            }
            try {
                ObjectWriter writer = objectMapper.writer(new DefaultPrettyPrinter());
                writer.writeValue(file, cacheRoot);
            } catch (IOException e) {
                logger.warn("[!]Failed to write ATC mapping cache: {}", e.getMessage());
            }
        }));

        return map;
    }

    private static List<String> lookupCode(String rxnormCode) {
        List<String> atcList = new ArrayList<>();
        try {
            URL ingredientsUrl = new URL(baseUrl + rxnormCode + "/related.json?tty=IN&tty=MIN");
            JsonNode json;
            try (InputStream in = ingredientsUrl.openConnection().getInputStream()) {
                json = objectMapper.readTree(in);
            }

            JsonNode conceptGroup = json.get("relatedGroup").get("conceptGroup");
            for (JsonNode cg : conceptGroup) {
                JsonNode conceptProperties = cg.get("conceptProperties");
                if (conceptProperties == null) continue;
                for (JsonNode cp : conceptProperties) {
                    URL atcUrl = new URL(baseUrl + cp.get("rxcui").asText() + "/property.json?propName=ATC");
                    JsonNode atcJson;
                    try (InputStream in = atcUrl.openConnection().getInputStream()) {
                        atcJson = objectMapper.readTree(in);
                    }
                    JsonNode propConceptGroup = atcJson.get("propConceptGroup");
                    if (propConceptGroup == null) continue;
                    JsonNode propConcept = propConceptGroup.get("propConcept");
                    if (propConcept == null) continue;
                    for (JsonNode pc : propConcept) {
                        atcList.add(pc.get("propValue").asText());
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("[!]RxNorm lookup failed for {}: {}", rxnormCode, e.getMessage());
        }

        return atcList;
    }
}
