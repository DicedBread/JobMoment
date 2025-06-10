package diced.bread.client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SeekStore {

    private static final Logger logger = LogManager.getLogger(SeekStore.class);
    private final File logFile;

    public SeekStore() {
        new File("store/seek/").mkdirs();
        logFile = new File("store/seek/seek_ids.log");
    }

    /**
     * Logs the given collection of Seek data IDs to the log file.
     */
    public void logSeekDataIds(Collection<String> ids) {
        try (FileWriter writer = new FileWriter(logFile, true)) {
            for (String id : ids) {
                writer.write(id + System.lineSeparator());
            }
            logger.info("Logged {} Seek data IDs to {}", ids.size(), logFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to log Seek data IDs", e);
        }
    }

    /**
     * Loads already saved Seek data IDs from the log file.
     * @return a Set of IDs, or an empty set if none exist.
     */
    public Set<String> loadSavedIds() {
        Set<String> ids = new HashSet<>();
        if (logFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(logFile.toPath());
                ids.addAll(lines);
                logger.info("Loaded {} Seek data IDs from {}", ids.size(), logFile.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to load Seek data IDs", e);
            }
        } else {
            logger.info("No Seek data log file found at {}", logFile.getAbsolutePath());
        }
        return ids;
    }
}