package diced.bread.google;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import com.google.api.client.googleapis.batch.BatchCallback;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonErrorContainer;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.About.StorageQuota;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

public class DriveContainer {
    Drive driveService;
    private static final Logger logger = LogManager.getLogger(DriveContainer.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "Google Docs API Java Service Account";

    private final String COPY_FOLDER_ID = "1UKUiye_UQ3XgfbrvEQ-oWIJScasmjHaK";

    @Deprecated
    public DriveContainer(Drive drive) {
        this.driveService = drive;
    }

    public DriveContainer(String serviceAccountKeyPath) throws IOException, GeneralSecurityException{
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredentials credentials = initCredentials(serviceAccountKeyPath);

        driveService = new Drive.Builder(httpTransport, JSON_FACTORY,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
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
     * 
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
        if (files.isEmpty())
            return;
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
                                logger.error("failed to delete file " + file.getId() + " " + err);
                            }
                        });
            } catch (IOException ex) {
                logger.error("failed to add id: " + file.getId() + " to queue " + file);
            }
        });
        logger.info("deleting " + files.size() + " files");
        try {
            batch.execute();
            logger.info("deleted " + files.size() + "files");
        } catch (IOException e) {
            logger.error("failed to batch delete " + e);
        }
    }

    public StorageQuota getStorageQuota() {
        try {
            About f = driveService.about().get().setFields("storageQuota").execute();
            return f.getStorageQuota();
        } catch (IOException e) {
            logger.error("failed to get storage quota " + e);
        }
        return null;
    }

    private GoogleCredentials initCredentials(String serviceAccountKeyPath)
            throws IOException, GeneralSecurityException {
        // Load service account credentials
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new FileInputStream(serviceAccountKeyPath))
                .createScoped(Collections.singleton(DocsScopes.DOCUMENTS))
                .createScoped(Collections.singleton(DriveScopes.DRIVE));
        return credentials;
    }
}
