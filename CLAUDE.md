# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Gradle wrapper. Java 21 toolchain. Groovy 3.0.22 (build) — alpha-1 5.0.0 jars sit unused in `lib/` (not on classpath via `build.gradle`).

```bash
./gradlew build                  # compile + checkstyle + jacoco
./gradlew shadowJar              # fat jar -> build/libs/syntheakds-*-all.jar
./gradlew run                    # runs org.example.syntheakds.Main
java -jar build/libs/syntheakds-*-all.jar   # runs Main
./gradlew test                   # JUnit 5 (no tests present yet; src/test/ does not exist)
```

No CLI args parsed. Tuning happens by editing static fields in `src/main/groovy/org/example/syntheakds/config/SyntheaKDSConfig.groovy` (`patientCount`, `seed`, `state`, `endTime`, `threadPoolSize`, `patientIdentifierSystem`, …) and rebuilding. README's "int: number of patients" parameter is aspirational, not wired up.

## Pipeline Architecture

Two-phase pipeline, single JVM:

1. **Generate** — `Main.configureGeneratorOptions()` programmatically sets `org.mitre.synthea.helpers.Config` flags (only FHIR R4 export on, CCDA/STU3/DSTU2 off, UUID filenames, no subfolders, alive-only) then runs `org.mitre.synthea.engine.Generator`. Synthea writes FHIR bundles into `output/<timestamp>/tmp_output/fhir/`.
2. **Convert** — `Processor<Path>` walks those bundles with a fixed pool of 4 threads, applies `ProcessingTask` per file. Each task parses the bundle JSON (Jackson), dispatches every entry through `Converter.convert(JsonNode)` → resource-type switch → MII KDS-profiled HAPI FHIR R4 object, serializes via `SyntheaKDSConfig.ctx.newJsonParser()`, and writes to `output/<timestamp>/kds/`. Side effect: `ConsentFactory.createConsentResource()` synthesizes an MII Broad Consent per patient bundle; consent dates aggregated into `output/<timestamp>/authored.json`. After conversion, `tmp_output/fhir/` is **wiped** (`FileUtils.cleanDirectory`) — raw Synthea output is not retained.

Output layout per run:
```
output/<epoch-millis>/
  tmp_output/fhir/   # transient, deleted after conversion
  kds/               # MII KDS-conformant FHIR R4 resources
  authored.json      # consent dates index
output/mappings/rxnorm/mapping_atc.json   # cross-run cache, persists
```

## Resource Mapping

`Converter` is the single dispatch point — adding a new resource type means a new `convertX(JsonNode)` method **and** a new `case` in the switch. Each `convertX` is responsible for: setting the MII profile URL on `meta`, copying id/identifier, and translating Synthea's SNOMED-heavy codings to whatever the KDS profile expects. Unhandled types log `[!]No handling available` and return `null` — `ProcessingTask` filters nulls before serializing.

`RxNormTranslator` exists because Synthea encodes meds in RxNorm but MII medication profiles want ATC. It hits `https://rxnav.nlm.nih.gov/REST/rxcui/{code}/...`, caches lookups in-memory, and persists the cache on JVM shutdown via a shutdown hook writing `output/mappings/rxnorm/mapping_atc.json`. The bundled `src/main/resources/rxnorm/mapping_atc.json` is the seed cache loaded if the persistent file is missing. Network access required on first run with unseen RxCUIs.

`DateExtractor` pulls effective/recorded dates from each resource; `ProcessingTask` picks one (seeded `Random(42)`) as the Consent's `dateTime` for patient bundles, skipping `hospitalInformation*` / `practitionerInformation*` files.

## MII KDS Profile URLs

The set of profile URLs hard-coded in `Converter` and `ConsentFactory` is the canonical list of what this project claims to produce. If the README's profile versions and the URLs in code drift, the code is authoritative — Simplifier links rot.

## Logging

Log4j2 config at `src/main/resources/config/log4j2.xml`, loaded explicitly by `Main.configureLog4j2()` via `Utils.findResourceURI` (so it works both unpacked and inside the shadow jar). Logs go to `log/` at the repo root.

## Things That Will Bite You

- `output/<timestamp>/tmp_output/fhir/` is deleted at end of `Main.main()`. If conversion crashes mid-run, raw Synthea output remains; if it succeeds, it's gone.
- `Test.groovy` next to `Main.groovy` is a scratch entry point, not a unit test. There is no `src/test/` tree.
- `shadowJar` excludes `App.class`; do not rename `Main` to `App` without revisiting.
- `Processor` thread pool size is hard-coded to 4 inside `Processor.groovy` (does not read `SyntheaKDSConfig.threadPoolSize` — that field only feeds Synthea's generator pool).
- `patientIdentifierSystem` in `SyntheaKDSConfig` has no type declaration (untyped `static`) — adding `String` is fine but currently inconsistent with siblings.
