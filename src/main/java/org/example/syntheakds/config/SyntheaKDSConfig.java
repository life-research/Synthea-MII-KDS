package org.example.syntheakds.config;

import ca.uhn.fhir.context.FhirContext;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class SyntheaKDSConfig {

    // Preset values
    public static final Path basePath = Paths.get("").toAbsolutePath();
    public static final Path mapDirPath = basePath.resolve(Paths.get("output", "mappings"));
    public static final Path outputDirPath = basePath.resolve(Paths.get("output", Long.toString(System.currentTimeMillis())));
    public static final Path tmpDirPath = outputDirPath.resolve("tmp_output");
    public static final Path patDirPath = tmpDirPath.resolve("fhir");
    public static final Path kdsDirPath = outputDirPath.resolve("kds");
    public static final FhirContext ctx = FhirContext.forR4();

    // Adjustable values
    public static long seed = 172483905238L;
    public static long clinicianSeed = 172483905238L;
    public static long referenceTime = 1724882400000L;
    public static long endTime = 1724882400000L;
    public static int patientCount = 100000;
    public static int threadPoolSize = -1;
    public static String state = "Massachusetts";
    public static String patientIdentifierSystem = "http://fts.smith.care";

    private SyntheaKDSConfig() {}
}
