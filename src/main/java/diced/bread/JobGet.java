package diced.bread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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

import diced.bread.client.JobFilter.JobIdInclusionFilter;
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

public class JobGet {
    private final String STORE_ROOT_FOLDER = "store/";
    private final String DEFAULT_BATCH_SELECT_FILE = "batch.md";
    private final String DEFAULT_SUMMARY_ROOT_FOLDER = "out/";

    private static final String SERVICE_ACCOUNT_KEY_PATH = "service-account.json";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private static final Logger logger = LogManager.getLogger(JobGet.class);

    private static final Option old = Option.builder("o")
            .longOpt("old")
            .desc("run old system")
            .required(false)
            .build();

    private static final Option writeBatch = Option.builder("wb")
            .longOpt("writeBatch")
            .desc("write a batch file")
            .optionalArg(true)
            .argName("file to write to")
            .required(false)
            .build();

    private static final Option readBatch = Option.builder("rb")
            .longOpt("readBatch")
            .desc("read a batch file")
            .optionalArg(true)
            .argName("file to read from")
            .required(false)
            .build();

    public void run(CommandLine commandLine) {

        if (commandLine.hasOption(old)) {
            old(commandLine);
            return;
        }

        if (commandLine.hasOption(writeBatch)) {
            writeBatch(commandLine);
            return;
        }

        if (commandLine.hasOption(readBatch)) {
            readBatch(commandLine);
            return;
        }

    }

    private void readBatch(CommandLine commandLine) {
        String fileName = commandLine.getOptionValue(readBatch);
        if (fileName == null) {
            fileName = DEFAULT_BATCH_SELECT_FILE;
        }
        File file = new File(fileName);
        if (!file.exists()) {
            logger.error("batch file " + fileName + " does not exist");
            return;
        }

        new File(STORE_ROOT_FOLDER).mkdirs();
        ScrapedLogger s = new ScrapedLogger(STORE_ROOT_FOLDER + "scrapped.log");
        SeekClient client = new SeekClient(s);
        JobIdInclusionFilter filter = new JobIdInclusionFilter(BatchSelectWriter.parseBatchSelectFile(file));
        client.addFilter(filter);
        Map<URI, JobInfo> listing = client.getJobInfo();

        int count = listing.size();
        logger.info("jobs found " + count);
        if (count > 20) {
            logger.warn(count + " listings stopping process");
            return;
        }

        List<CLWriterProcess> processes = new ArrayList<>();
        logger.info("starting " + listing.keySet().size() + " CL processors");

        var drive = getDriveContainer();
        var doc = getDocContainer();

        listing.forEach((k, v) -> {
            CLWriterProcess thread = new CLWriterProcess(k, v, drive, doc);
            processes.add(thread);
            thread.start();
        });

        if (!processes.isEmpty()) {
            SummaryWriter summary = new SummaryWriter(DEFAULT_SUMMARY_ROOT_FOLDER);
            for (CLWriterProcess process : processes) {
                try {
                    process.join();
                    if (process.getDocId() != null) {
                        collect(summary, process.getJobInfo(), process.getPdfData(), s);
                    }
                } catch (InterruptedException ex) {
                    logger.error(ex);
                }
            }
        }
    }

    private void collect(SummaryWriter summary, JobInfo jobInfo, ByteArrayOutputStream pdfData, ScrapedLogger store) {
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
        store.logScrapeRecord(jobInfo.getScrapeRecord());
    }

    private void old(CommandLine commandLine) {
        try {
            JobGetter_SeekStore jg = new JobGetter_SeekStore();
            if (!jg.checkStorageQuotaOk())
                return;
            jg.deleteOldFiles();
            jg.run();
        } catch (IOException | GeneralSecurityException e) {
            logger.error("Failed to login " + e);
        }
    }

    private void writeBatch(CommandLine commandLine) {
        logger.info("writeBatch start");
        String file = commandLine.getOptionValue(writeBatch);
        if (file == null) {
            file = DEFAULT_BATCH_SELECT_FILE;
        }

        new File(STORE_ROOT_FOLDER).mkdirs();
        ScrapedLogger s = new ScrapedLogger(STORE_ROOT_FOLDER + "scrapped.log");

        SeekClient client = new SeekClient(s);

        List<String> excludeIfContains = List.of("senior", "manager", "lead", "head", "advisor");
        client.addFilter(new JobTitleFilter(excludeIfContains, true));
        
        Map<URI, JobInfo> listing = client.getJobInfo();
        BatchSelectWriter batchSelectWriter = new BatchSelectWriter(file);
        logger.info("writing " + listing.size() + " listings to batch");
        listing.forEach((k, v) -> {
            batchSelectWriter.appendJob(v);
        });
        logger.info("writeBatch end");
    }

    public static void main(String[] args) {
        Options options = new Options();

        OptionGroup batchOperations = new OptionGroup().addOption(writeBatch).addOption(readBatch);
        options.addOption(old).addOptionGroup(batchOperations);

        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine commandLine = parser.parse(options, args);
            new JobGet().run(commandLine);
        } catch (ParseException e) {
            logger.error(e);
        }
    }

    private GoogleCredentials initCredentials() throws IOException, GeneralSecurityException {
        // Load service account credentials
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new FileInputStream(SERVICE_ACCOUNT_KEY_PATH))
                .createScoped(Collections.singleton(DocsScopes.DOCUMENTS))
                .createScoped(Collections.singleton(DriveScopes.DRIVE));
        return credentials;
    }

    // private DocContainer getDocContainer() {
    //     try {
    //         HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    //         GoogleCredentials credentials = initCredentials();

    //         Docs docService = new Docs.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
    //                 .setApplicationName(APPLICATION_NAME)
    //                 .build();

    //         return new DocContainer(docService);
    //     } catch (IOException | GeneralSecurityException e) {
    //         logger.error("google login failed " + e);
    //     }
    //     return null;
    // }

    // private DriveContainer getDriveContainer() {
    //     try {
    //         HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    //         GoogleCredentials credentials = initCredentials();

    //         Drive driveService = new Drive.Builder(new NetHttpTransport(), JSON_FACTORY,
    //                 new HttpCredentialsAdapter(credentials))
    //                 .setApplicationName(APPLICATION_NAME)
    //                 .build();

    //         return new DriveContainer(driveService);
    //     } catch (IOException | GeneralSecurityException e) {
    //         logger.error("google login failed " + e);
    //     }
    //     return null;
    // }
}
