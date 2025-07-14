package diced.bread;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
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

import diced.bread.client.JobFilter.JobFilter;
import diced.bread.client.JobFilter.JobIdInclusionFilter;
import diced.bread.client.JobFilter.TitleContainsFilter;
import diced.bread.client.JobFilter.TitleDoesNotContainFilter;
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

    private static final String SERVICE_ACCOUNT_KEY_PATH = "service-account_dave.json";

    private static final Logger logger = LogManager.getLogger(JobGet.class);

    private static final Option old = Option.builder("o")
            .longOpt("old")
            .desc("run old system")
            .required(false)
            .build();

    private static final Option itSeek = Option.builder("it")
        .longOpt("seekIt")
        .desc("run seek ")
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

        if (commandLine.hasOption(old)) {
            old(commandLine);
            return;
        }

        if (commandLine.hasOption(writeBatch)) {
            writeBatch(commandLine, filters);
            return;
        }

        if (commandLine.hasOption(readBatch)) {
            readBatch(commandLine);
            return;
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

        try {
            DriveContainer drive = new DriveContainer(SERVICE_ACCOUNT_KEY_PATH);
            DocContainer doc = new DocContainer(SERVICE_ACCOUNT_KEY_PATH);

            listing.forEach((url, jobInfo) -> {
                CLWriterProcess thread = new CLWriterProcess(url, jobInfo, drive, doc);
                processes.add(thread);
                thread.start();
            });

        } catch (IOException | GeneralSecurityException e) {
            logger.error("google container service failed " + e);
        } finally {
            processes.clear();
        }

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
            
            boolean storeOk = jg.checkStorageQuotaOk();
            jg.deleteOldFiles();
            if (!storeOk)
                return;
            jg.run();
        } catch (IOException | GeneralSecurityException e) {
            logger.error("Failed to login " + e);
        }
    }

    private void writeBatch(CommandLine commandLine, List<JobFilter> filters) {
        logger.info("writeBatch start");
        String file = commandLine.getOptionValue(writeBatch);
        if (file == null) {
            file = DEFAULT_BATCH_SELECT_FILE;
        }

        new File(STORE_ROOT_FOLDER).mkdirs();
        ScrapedLogger s = new ScrapedLogger(STORE_ROOT_FOLDER + "scrapped.log");

        SeekClient client = new SeekClient(s);
        filters.forEach(e -> client.addFilter(e));

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
        options.addOption(old)
                .addOptionGroup(batchOperations)
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

}
