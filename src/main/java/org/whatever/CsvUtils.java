package org.whatever;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class CsvUtils {
    private static final Path CSV_FILENAME = Main.DIR_OUTPUT.resolve("output.csv");

    private static void deleteFile() {
        try {
            Files.deleteIfExists(CSV_FILENAME);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file " + CSV_FILENAME, e);
        }
    }

    public static void prepareFile() {
        deleteFile();
        writeLineToFile(DataSetEntry.getCsvHeader());
    }

    public static void writeLineToFile(String line) {
        try (BufferedWriter writer = Files.newBufferedWriter(CSV_FILENAME, StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
            writer.write(line);
            writer.newLine();
        } catch (Exception e) {
            throw new RuntimeException("Failed to write to file " + CSV_FILENAME, e);
        }
    }
}