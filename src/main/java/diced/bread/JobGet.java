package diced.bread;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
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

import diced.bread.client.SeekClient;
import diced.bread.model.JobInfo;
import diced.bread.persist.BatchSelectWriter;
import diced.bread.persist.ScrapedLogger;

public class JobGet {
    private final String STORE_ROOT_FOLDER = "store/";
    private final String DEFAULT_BATCH_SELECT_FILE = "batch.md";

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

        }

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
        Map<URI, JobInfo> listing = client.getJobInfo();
        BatchSelectWriter batchSelectWriter = new BatchSelectWriter(file);
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
}
