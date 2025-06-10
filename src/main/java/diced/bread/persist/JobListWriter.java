package diced.bread.persist;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JobListWriter {
    private static final Logger logger = LogManager.getLogger(JobListWriter.class);

    private File file;

    public JobListWriter(String filePath) {
        file = new File(filePath);
    }

    public void append(JobApply jobApply) {
        try (FileWriter fileWriter = new FileWriter(file, true)) {
            fileWriter.write(jobApply.toString() + "\n");
        } catch (IOException e) {
            logger.error(e);
        }
    }
}
