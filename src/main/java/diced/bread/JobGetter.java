package diced.bread;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import diced.bread.client.SeekClient;
import diced.bread.model.JobInfo;
import diced.bread.persist.JobApply;
import diced.bread.persist.JobOutWriter;
import diced.bread.process.CVWriterProcess;

public class JobGetter {
    private static final Logger logger = LogManager.getLogger(JobGetter.class);

    private static final String APPLICATION_NAME = "Google Docs API Java Service Account";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String SERVICE_ACCOUNT_KEY_PATH = "service-account.json";
    private static final String DOCUMENT_ID = "16fm0Fyo_N7TulVRo4q8-SJ_DZQqUzPjtKobnAtyW_iY";

    DriveContainer drive;
    DocContainer doc;

    public void run() {
        logger.info("running");

        // String newDoc = drive.createCopy(DOCUMENT_ID, "test out");

        // JobInfo g = new JobInfo("asd", "name", "pos title", false);

        

        // doc.findAndReplace(newDoc, g);

        SeekClient seek = new SeekClient();
        seek.GetData(1, 2);
        Map<URI, JobInfo> listing = seek.getJobInfo();

        List<CVWriterProcess> process = new ArrayList<>();

        listing.forEach((k, v) -> {
            CVWriterProcess thread = new CVWriterProcess(k, v, drive, doc);
            process.add(thread);
            thread.start();
        });

        
        JobOutWriter out = new JobOutWriter();

        process.forEach(e -> {
            try {
                e.join();
                drive.download(e.getDocId(), out.getPdfDir() + e.getJobInfo().getCompanyName().replaceAll("\\W+", ""));
                out.getWriter().append(new JobApply(e.getJobInfo().getListingUrl(), e.getJobInfo().getCompanyName(), false));
            } catch (InterruptedException ex) {
                logger.error(ex);
            }
        });


        // drive.download(newDoc, "test");

        logger.info("end");
    }

    private JobGetter() throws IOException, GeneralSecurityException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredentials credentials = initCredentials();

        Docs docService = new Docs.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
        Drive driveService = new Drive.Builder(new NetHttpTransport(), JSON_FACTORY,
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();

        doc = new DocContainer(docService);
        drive = new DriveContainer(driveService);
    }

    private GoogleCredentials initCredentials() throws IOException, GeneralSecurityException {
        // Load service account credentials
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new FileInputStream(SERVICE_ACCOUNT_KEY_PATH))
                .createScoped(Collections.singleton(DocsScopes.DOCUMENTS))
                .createScoped(Collections.singleton(DriveScopes.DRIVE));
        return credentials;
    }

    public static void main(String... args) throws IOException, GeneralSecurityException {
        JobGetter jg = new JobGetter();
        jg.run();
    }
}