package diced.bread;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.drive.model.About.StorageQuota;

import diced.bread.client.Client;
import diced.bread.client.JobFilter.JobFilter;
import diced.bread.client.JobFilter.JobIdInclusionFilter;
import diced.bread.client.JobFilter.TitleContainsFilter;
import diced.bread.client.JobFilter.TitleDoesNotContainFilter;
import diced.bread.client.SeekClientIt;
import diced.bread.client.SeekClientRetail;
import diced.bread.google.DocContainer;
import diced.bread.google.DriveContainer;
import diced.bread.google.GoogleOAuth;
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
    private final int DEFAULT_MAX_CL_WRITER = 10;

    private static final Logger logger = LogManager.getLogger(JobGet.class);

    private static final Option itSeek = Option.builder("it")
            .longOpt("seekIt")
            .desc("run seek client for it positions")
            .build();
    
    private static final Option retailSeek = Option.builder("re")
            .longOpt("seekRetail")
            .desc("run seek client for retail positions")
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

    private static final Option includeIfContains = Option.builder("in")
            .longOpt("includeIfContains")
            .hasArg()
            .desc("include jobs with words from file")
            .argName("file name")
            .required(false)
            .build();

    private static final Option excludeIfContains = Option.builder("ex")
            .longOpt("excludeIfContains")
            .hasArg()
            .desc("exclude jobs with words from file")
            .argName("file name")
            .required(false)
            .build();

    public void run(CommandLine commandLine) {
        List<JobFilter> filters = buildTitleFilters(commandLine);
        if (filters == null)
            return;

        new File(STORE_ROOT_FOLDER).mkdirs();
        ScrapedLogger store = new ScrapedLogger(STORE_ROOT_FOLDER + "scrapped.log");
        Client client = getClient(commandLine, store);

        // if (commandLine.hasOption(itSeek)) {
        //     filters.forEach(e -> client.addFilter(e));
        //     runCoverLetterQuery(client, store);
        // }

        if(!commandLine.hasOption(writeBatch) && !commandLine.hasOption(readBatch)){
            filters.forEach(e -> client.addFilter(e));
            if(commandLine.hasOption(itSeek)){
                runCoverLetterQuery(client, store);
            }else{
                logger.warn("new cl writer needed");
            }
        }

        if (commandLine.hasOption(writeBatch)) {
            filters.forEach(e -> client.addFilter(e));
            writeBatch(commandLine, client);
            return;
        }

        if (commandLine.hasOption(readBatch)) {
            readBatch(commandLine, client, store);
            return;
        }
    }

    private Client getClient(CommandLine commandLine, ScrapedLogger store){
        if(commandLine.hasOption(itSeek)){
            return new SeekClientIt(store); 
        }

        if(commandLine.hasOption(retailSeek)){
            return new SeekClientRetail(store);
        }

        return null;
    }

    private void readBatch(CommandLine commandLine, Client client, ScrapedLogger store) {
        String fileName = commandLine.getOptionValue(readBatch);
        if (fileName == null) {
            fileName = DEFAULT_BATCH_SELECT_FILE;
        }
        File file = new File(fileName);
        if (!file.exists()) {
            logger.error("batch file " + fileName + " does not exist");
            return;
        }
        JobIdInclusionFilter filter = new JobIdInclusionFilter(BatchSelectWriter.parseBatchSelectFile(file));
        client.addFilter(filter);
        runCoverLetterQuery(client, store);
    }

    private void runCoverLetterQuery(Client client, ScrapedLogger store) {
        Map<URI, JobInfo> listing = client.getJobInfo();

        int count = listing.size();
        logger.info("jobs found " + count);
        int numOfOpp = DEFAULT_MAX_CL_WRITER;
        if (count > numOfOpp) {
            logger.warn("doing " + numOfOpp + " of " + count + " jobs");
            List<Entry<URI, JobInfo>> list = new ArrayList<>(listing.entrySet()).subList(0, numOfOpp);
            Map<URI, JobInfo> limitedListing = new HashMap<>();
            for (Entry<URI, JobInfo> entry : list) {
                limitedListing.put(entry.getKey(), entry.getValue());
            }
            listing = limitedListing;
        }

        List<CLWriterProcess> processes = new ArrayList<>();
        logger.info("starting " + listing.keySet().size() + " CL processors");

        try {
            Credential cred = GoogleOAuth.authorize();
            DriveContainer drive = new DriveContainer(cred);
            DocContainer doc = new DocContainer(cred);

            listing.forEach((url, jobInfo) -> {
                CLWriterProcess thread = new CLWriterProcess(url, jobInfo, drive, doc);
                processes.add(thread);
                thread.start();
            });

        } catch (IOException | GeneralSecurityException e) {
            logger.error("google auth failed " + e);
            processes.clear();
        }

        if (!processes.isEmpty()) {
            SummaryWriter summary = new SummaryWriter(DEFAULT_SUMMARY_ROOT_FOLDER);
            for (CLWriterProcess process : processes) {
                try {
                    process.join();
                    if (process.getDocId() != null) {
                        collect(summary, process.getJobInfo(), process.getPdfData(), store);
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

    // private void old(CommandLine commandLine) {
    // try {
    // JobGetter_SeekStore jg = new JobGetter_SeekStore();

    // boolean storeOk = jg.checkStorageQuotaOk();
    // jg.deleteOldFiles();
    // if (!storeOk)
    // return;
    // jg.run();
    // } catch (IOException | GeneralSecurityException e) {
    // logger.error("Failed to login " + e);
    // }
    // }

    private void writeBatch(CommandLine commandLine, Client client) {
        logger.info("writeBatch start");
        String file = commandLine.getOptionValue(writeBatch);
        if (file == null) {
            file = DEFAULT_BATCH_SELECT_FILE;
        }

        Map<URI, JobInfo> listings = client.getJobInfo();
        BatchSelectWriter batchSelectWriter = new BatchSelectWriter(file);
        logger.info("writing " + listings.size() + " listings to batch");
        ArrayList<JobInfo> list = new ArrayList<>(listings.values());
        list.sort((b, a) -> {
            if (a.getListingDate() == null && b.getListingDate() == null)
                return 0;
            if (a.getListingDate() == null)
                return 1;
            if (b.getListingDate() == null)
                return -1;
            return a.getListingDate().compareTo(b.getListingDate());
        });
        list.forEach(v -> {
            batchSelectWriter.appendJob(v);
        });
        logger.info("writeBatch end");
    }

    public static void main(String[] args) {
        Options options = new Options();

        OptionGroup batchOperations = new OptionGroup().addOption(writeBatch).addOption(readBatch);
        OptionGroup jobTypeGroup = new OptionGroup().addOption(itSeek).addOption(retailSeek);
        jobTypeGroup.isRequired();

        options
                .addOptionGroup(batchOperations)
                .addOptionGroup(jobTypeGroup)
                .addOption(includeIfContains)
                .addOption(excludeIfContains);

        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine commandLine = parser.parse(options, args);
            new JobGet().run(commandLine);
        } catch (ParseException e) {
            logger.error(e);
        }
    }

    private List<JobFilter> buildTitleFilters(CommandLine line) {
        List<JobFilter> filters = new ArrayList<>();

        if (line.hasOption(excludeIfContains)) {
            String fileName = line.getOptionValue(excludeIfContains);
            List<String> words = readWordsFromFile(fileName);
            if (words == null)
                return null;
            filters.add(new TitleContainsFilter(new HashSet<>(words))); // exclude if contains
        }

        if (line.hasOption(includeIfContains)) {
            String fileName = line.getOptionValue(includeIfContains);
            List<String> words = readWordsFromFile(fileName);
            if (words == null)
                return null;
            filters.add(new TitleDoesNotContainFilter(new HashSet<>(words))); // include if contains
        }

        return filters;
    }

    // Helper method to read words from a file (one word per line)
    private List<String> readWordsFromFile(String fileName) {
        List<String> words = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    words.add(line.trim().toLowerCase());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to read words from file: " + fileName);
            logger.debug(e);
            return null;
        }
        return words;
    }

    public boolean checkStorageQuotaOk() {
        try {
            Credential cred = GoogleOAuth.authorize();
            DriveContainer drive = new DriveContainer(cred);

            StorageQuota quota = drive.getStorageQuota();
            if (quota == null) {
                logger.error("no quota found ending process");
                return false;
            }
            ;
            if (quota.getLimit() <= 0) {
                logger.error("quota limit 0 ending process");
                try {
                    logger.error(quota.toPrettyString());
                } catch (IOException e) {
                    logger.error(e);
                }
                return false;
            }
            double percent = quota.getUsage() / quota.getLimit();
            logger.info("quota used " + (percent * 100) + "%");
            logger.info("quota used " + quota.getUsage() + " of " + quota.getLimit());
            return true;
        } catch (IOException | GeneralSecurityException e) {
            logger.error("google login failed");
            return false;
        }

    }
}
