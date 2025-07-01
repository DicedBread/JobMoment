package diced.bread.google;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import com.google.api.client.googleapis.batch.BatchCallback;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.json.GoogleJsonErrorContainer;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

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
            String fileLoc = outputPath + ".pdf";
            PDDocument pf = Loader.loadPDF(outputStream.toByteArray());
            pf.removePage(0);
            pf.save(fileLoc);

            pf.close();
            logger.info("document " + documentId + " saved to " + outputPath);
        } catch (IOException e) {
            logger.error("failed to download " + documentId + " error " + e);
        }
    }

    public ByteArrayOutputStream downloadData(String documentId) {
        try {
            ByteArrayOutputStream initOutputStream = new ByteArrayOutputStream();
            driveService.files().export(documentId, "application/pdf").executeMediaAndDownloadTo(initOutputStream);
            PDDocument pf = Loader.loadPDF(initOutputStream.toByteArray());
            pf.removePage(0);
            initOutputStream.close();
            ByteArrayOutputStream finalOutputStream = new ByteArrayOutputStream();

            pf.save(finalOutputStream);
            pf.close();
            return finalOutputStream;
        } catch (IOException e) {
            logger.error("failed to download " + documentId + " error " + e);
        }
        return null;
    }



    /***
     * gets list of file from google drive
     * @return 
     */
    public List<File> getAll() {
        try {
            // TODO manage page tokens 
            String pageToken = null;
            FileList l = driveService.files().list().setFields("nextPageToken, files(id, name, createdTime, ownedByMe)")
                    .execute();
                logger.info("page token " + l.getNextPageToken());
            return l.getFiles();
        } catch (IOException e) {
            logger.error("failed to get files " + e);
        }
        return new ArrayList<>();
    }

    /*** 
     * deletes files provided in list 
     */
    public void deleteAll(List<File> files) {
        BatchRequest batch = driveService.batch();
        files.forEach(file -> {
            try {
                batch.queue(driveService.files().delete(file.getId()).buildHttpRequest(),
                        Void.class,
                        GoogleJsonErrorContainer.class,
                        new BatchCallback<Void, GoogleJsonErrorContainer>() {
                            @Override
                            public void onSuccess(Void t, HttpHeaders responseHeaders) throws IOException {
                                logger.info("deleted id: " + file.getId() + " " + file.getName());
                            }

                            @Override
                            public void onFailure(GoogleJsonErrorContainer err, HttpHeaders responseHeaders)
                                    throws IOException {
                                logger.error("failed to delete file "+ file.getId() + " " + err);
                            }
                        });
            } catch (IOException ex) {
                logger.error("failed to add id: " + file.getId() + " to queue " + file);
            }
        });
        logger.info("deleting " + files.size() + "files");
        try {
            batch.execute();
            logger.info("deleted " + files.size() + "files");
        } catch (IOException e) {
            logger.error("failed to batch delete " + e);
        }
    }
}
