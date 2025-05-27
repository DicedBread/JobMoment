package diced.bread;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

public class DriveContainer {
    Drive driveService;
    private static final Logger logger = LogManager.getLogger(DriveContainer.class);

    private final String COPY_FOLDER_ID = "1UKUiye_UQ3XgfbrvEQ-oWIJScasmjHaK";

    public DriveContainer(Drive drive) {
        this.driveService = drive;
    }

    public String createCopy(String documentId, String newTitle) {
        logger.info("creating copy of " + documentId + " as title: " + newTitle);
        try {
            File f = new File().setParents(List.of(COPY_FOLDER_ID)).setName(newTitle);
            File c = driveService.files().copy(documentId, f).execute();
            logger.info("created copy of " + documentId + " as title: " + newTitle + "id: " + c.getId());
            return c.getId();
        } catch (IOException ex) {
            logger.error("failed to create copy of " + documentId + " " + newTitle + " error " + ex);
        }
        return null;
    }

    public void download(String documentId, String outputPath) {
        logger.info("downloading " + documentId + " to " + outputPath);
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            driveService.files().export(documentId, "application/pdf").executeMediaAndDownloadTo(outputStream);
            FileOutputStream fos = new FileOutputStream(outputPath + ".pdf");
            outputStream.writeTo(fos);
            logger.info("document " + documentId + " saved to " + outputPath);
        } catch (IOException e) {
            logger.error("failed to download " + documentId + " error " + e);
        }
    }
}
