package diced.bread;

import java.io.File;
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

import diced.bread.client.JobFilter.JobTitleFilter;
import diced.bread.client.SeekClient;
import diced.bread.google.DocContainer;
import diced.bread.google.DriveContainer;
import diced.bread.model.JobInfo;
import diced.bread.persist.JobApply;
import diced.bread.persist.ScrapedLogger;
import diced.bread.persist.SummaryWriter;
import diced.bread.process.CLWriterProcess;

public class JobGetter {
    private final boolean SAVE = true;

    private final String SUMMARY_ROOT_FOLDER = "out/";
    private final String STORE_ROOT_FOLDER = "store/";

    private static final Logger logger = LogManager.getLogger(JobGetter.class);

    private static final String APPLICATION_NAME = "Google Docs API Java Service Account";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String SERVICE_ACCOUNT_KEY_PATH = "service-account.json";
    private static final String DOCUMENT_ID = "16fm0Fyo_N7TulVRo4q8-SJ_DZQqUzPjtKobnAtyW_iY";

    DriveContainer drive;
    DocContainer doc;
    ScrapedLogger store;

    List<JobTitleFilter> filters;

    public void run() {
        logger.info("running");

        SeekClient seek = new SeekClient(store);
        filters.forEach(e -> seek.addFilter(e));
        Map<URI, JobInfo> listing = seek.getJobInfo();
        int count = listing.size();
        logger.info("jobs found " + count);
        if(count > 20){
            logger.warn(count + " listings stopping process");
            return;
        }

        List<CLWriterProcess> processes = new ArrayList<>();
        logger.info("starting " + listing.keySet().size() + " CL processors");
        listing.forEach((k, v) -> {
            CLWriterProcess thread = new CLWriterProcess(k, v, drive, doc);
            processes.add(thread);
            thread.start();
        });

        if (!processes.isEmpty()) {
            SummaryWriter summary = new SummaryWriter(SUMMARY_ROOT_FOLDER);

            processes.forEach(process -> {
                try {
                    process.join();
                    if (process.getDocId() != null) {
                        String jobTitleForm = process.getJobInfo().getJobTitle().replaceAll("\\W+", "");
                        String companyNameForm = process.getJobInfo().getCompanyName().replaceAll("\\W+", "");
                        String fileName = jobTitleForm + "_" + companyNameForm + ".pdf";

                        JobApply job = new JobApply(process.getJobInfo().getListingUrl(),
                                process.getJobInfo().getCompanyName(), false);
                                
                        summary.appendJob(job);
                        summary.appendFile(drive.downloadData(process.getDocId()), fileName);
                        if(SAVE){
                            store.logScrapeRecord(process.getJobInfo().getScrapeRecord());
                        }
                    }
                } catch (InterruptedException ex) {
                    logger.error(ex);
                }
            });

        }

        logger.info("end");
    }

    private void setupFilters(){
        List<String> includeIfContains = List.of("entry", "support", "level 1", "junior", "internship", "graduate", "tester");
        List<String> excludeIfContains = List.of("senior", "manager");
        
        filters = new ArrayList<>();
        filters.add(new JobTitleFilter(excludeIfContains, true));
        filters.add(new JobTitleFilter(includeIfContains, false));
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

        new File(STORE_ROOT_FOLDER).mkdirs();
        store = new ScrapedLogger(STORE_ROOT_FOLDER + "scrapped.log");

        setupFilters();
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