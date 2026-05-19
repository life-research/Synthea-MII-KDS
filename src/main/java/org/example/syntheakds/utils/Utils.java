package org.example.syntheakds.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public final class Utils {

    private static final Logger logger = LogManager.getLogger(Utils.class);

    private static final ClassLoader cLoader = Thread.currentThread().getContextClassLoader();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter formatterMicro = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private Utils() {}

    public static InputStream findResource(Path path) {
        String stringPath = path.toString();
        logger.debug("[?]Searching for resource @ {} ...", stringPath);
        InputStream stream = cLoader.getResourceAsStream(stringPath);
        if (stream == null) logger.warn("[!]Could not find resource @ {}!", stringPath);
        return stream;
    }

    public static URL findResourceURL(Path path) {
        String stringPath = path.toString();
        logger.debug("[?]Searching for resource @ {} ...", stringPath);
        URL url = cLoader.getResource(stringPath.replace("\\", "/"));
        if (url == null) logger.warn("[!]Could not find resource @ {}!", stringPath);
        return url;
    }

    public static URI findResourceURI(Path path) {
        try {
            return findResourceURL(path).toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Path> findFilesInDir(Path dirPath, FileExtension fileExt) {
        List<Path> fileList = new ArrayList<>();
        Pattern pattern = Pattern.compile("^.*\\." + fileExt.toString() + "$");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
            for (Path entry : stream) {
                if (!Files.isRegularFile(entry)) continue;
                if (!pattern.matcher(entry.getFileName().toString()).matches()) continue;
                fileList.add(entry);
                logger.debug("Found {} file: '{}'", fileExt.toString().toUpperCase(), entry);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fileList;
    }

    public static void writeFile(String content, Path outputPath, String fileName) {
        Path target = outputPath.resolve(fileName);
        try {
            Files.write(target, content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path getJarDir() {
        try {
            return new File(Utils.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts Synthea date strings to {@link Date}. Returns null if input is null.
     */
    public static Date dateFromSyntheaDate(String date) {
        if (date == null) return null;
        OffsetDateTime syntheaDateTime = parseSyntheaDateTime(date);
        return Date.from(syntheaDateTime.toInstant());
    }

    private static OffsetDateTime parseSyntheaDateTime(String date) {
        try {
            return OffsetDateTime.parse(date, formatter);
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.parse(date, formatterMicro);
        }
    }

    public enum FileExtension {
        JSON("json");

        private final String ext;

        FileExtension(String ext) {
            this.ext = ext;
        }

        @Override
        public String toString() {
            return this.ext;
        }
    }
}
