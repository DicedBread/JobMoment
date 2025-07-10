package diced.bread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

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
import com.google.api.services.drive.model.About.StorageQuota;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import diced.bread.client.JobFilter.DoJobInSetFilter;
import diced.bread.client.JobFilter.JobFilter;
import diced.bread.client.JobFilter.JobTitleFilter;
import diced.bread.client.SeekClient;
import diced.bread.google.DocContainer;
import diced.bread.google.DriveContainer;
import diced.bread.model.JobInfo;
import diced.bread.persist.BatchSelectWriter;
import diced.bread.persist.JobApply;
import diced.bread.persist.ScrapedLogger;
import diced.bread.persist.SummaryWriter;
import diced.bread.process.CLWriterProcess;

public class JobGetter_SeekStore {
    private final boolean SAVE = true;
    private final boolean batchSearch = false;

    private final String BATCH_SELECT_FILE = "batch.md";
    private final String SUMMARY_ROOT_FOLDER = "out/";
    private final String STORE_ROOT_FOLDER = "store/";

    private static final Logger logger = LogManager.getLogger(JobGetter_SeekStore.class);

    private static final String APPLICATION_NAME = "Google Docs API Java Service Account";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String SERVICE_ACCOUNT_KEY_PATH = "service-account.json";
    // private static final String DOCUMENT_ID =
    // "16fm0Fyo_N7TulVRo4q8-SJ_DZQqUzPjtKobnAtyW_iY";

    DriveContainer drive;
    DocContainer doc;
    ScrapedLogger store;

    List<JobFilter> filters;

    public void run() {
        logger.info("running");

        SeekClient seek = new SeekClient(store);
        filters.forEach(e -> seek.addFilter(e));
        Map<URI, JobInfo> listing = seek.getJobInfo();
        int count = listing.size();
        logger.info("jobs found " + count);

        if (batchSearch) {
            BatchSelectWriter batchSelectWriter = new BatchSelectWriter(BATCH_SELECT_FILE);
            listing.forEach((k, v) -> {
                batchSelectWriter.appendJob(v);
            });
            logger.info("logged batch file ending process");
            return;
        }

        if (count > 20) {
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
            for (CLWriterProcess process : processes) {
                try {
                    process.join();
                    if (process.getDocId() != null) {
                        collect(summary, process.getJobInfo(), process.getPdfData());
                    }
                } catch (InterruptedException ex) {
                    logger.error(ex);
                }
            }
        }

        logger.info("end");
    }

    private void collect(SummaryWriter summary, JobInfo jobInfo, ByteArrayOutputStream pdfData) {
        if (jobInfo == null || pdfData == null) {
            logger.error("something null " + jobInfo + " " + pdfData);
            return;
        }
        String jobTitleForm = jobInfo.getJobTitle().replaceAll("\\W+", "");
        String companyNameForm = jobInfo.getCompanyName().replaceAll("\\W+", "");
        String fileName = jobTitleForm + "_" + companyNameForm + ".pdf";

        JobApply job = new JobApply(jobInfo.getListingUrl(),
                jobInfo.getCompanyName(), false);

        summary.appendJob(job);
        summary.appendFile(pdfData, fileName);
        if (SAVE) {
            store.logScrapeRecord(jobInfo.getScrapeRecord());
        }
    }

    private void setupFilters() {
        List<String> includeIfContains = List.of("entry", "support", "level 1", "junior", "internship", "graduate",
                "tester", "l1", "intern");
        List<String> excludeIfContains = List.of("senior", "manager", "lead", "head", "advisor");

        filters = new ArrayList<>();
        filters.add(new JobTitleFilter(excludeIfContains, true));
        if (!batchSearch && !new File(BATCH_SELECT_FILE).exists()) {
            filters.add(new JobTitleFilter(includeIfContains, false));
        }

        if (new File(BATCH_SELECT_FILE).exists()) {
            logger.info("batch file found opening");
            filters.add(new DoJobInSetFilter(BatchSelectWriter.parseBatchSelectFile(new File(BATCH_SELECT_FILE))));
        }
    }

    public void deleteOldFiles() {
        long oneWeekMillis = 7L * 24 * 60 * 60 * 1000;
        long now = System.currentTimeMillis();
        Date weekAgo = new Date(now - oneWeekMillis);

        SimpleDateFormat rfc3339 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        rfc3339.setTimeZone(TimeZone.getTimeZone("UTC"));
        String weekAgoRfc3339 = rfc3339.format(weekAgo);

        List<com.google.api.services.drive.model.File> v = drive.getAll();

        var toDel = v.stream().filter(e -> {
            if (!e.getOwnedByMe())
                return false;
            boolean overAWeekAgo = false;
            if (e.getCreatedTime() != null) {
                overAWeekAgo = now - e.getCreatedTime().getValue() > oneWeekMillis;
            }
            // System.out.println(e.getId() + " " + e.getCreatedTime() + " overAWeekAgo=" +
            // overAWeekAgo + " " + e.getOwnedByMe());
            return overAWeekAgo;
        }).toList();

        logger.info("files found " + v.size() + " deleting " + toDel.size());
        drive.deleteAll(toDel);
    }

    public JobGetter_SeekStore() throws IOException, GeneralSecurityException {
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

    // public static void main(String... args) throws IOException, GeneralSecurityException {
    //     JobGetter jg = new JobGetter();
    //     if(!jg.checkStorageQuotaOk()) return;
    //     jg.deleteOldFiles();
    //     jg.run();
    // }

    public boolean checkStorageQuotaOk() {
        StorageQuota quota = drive.getStorageQuota();
        if(quota == null) {
            logger.error("no quota found ending process");
            return false;
        };
        if(quota.getLimit() <= 0){
            logger.error("quota limit 0 ending process");
            return false;
        }
        double percent = quota.getUsage() / quota.getLimit();
        logger.info("quota used " + (percent * 100) + "%");
        logger.info("quota used " + quota.getUsage() +" of " + quota.getLimit());
        return true;
    }
}