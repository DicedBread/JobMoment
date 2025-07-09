package diced.bread;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JobGet {
    private static final Logger logger = LogManager.getLogger(JobGet.class);

    CommandLine commandLine;

    private static Option old = Option.builder("o")
            .longOpt("old")
            .desc("run old system")
            .required(false)
            .build();

    private static Option writeBatch = Option.builder("wb")
            .longOpt("writeBatch")
            .desc("write a batch file")
            .hasArg()
            .argName("file to write to")
            .required(false)
            .build();

    private static Option readBatch = Option.builder("rb")
            .longOpt("readBatch")
            .desc("read a batch file")
            .hasArg()
            .argName("file to read from")
            .required(false)
            .build();

    private JobGet(CommandLine cmdLine) {
        commandLine = cmdLine;
    }

    public void run() {
        if (commandLine.hasOption(old)) {
            try {
                JobGetter jg = new JobGetter();
                if (!jg.checkStorageQuotaOk())
                    return;
                jg.deleteOldFiles();
                jg.run();
            } catch (IOException | GeneralSecurityException e) {
                logger.error("Failed to login " + e);
            }
        }
    }

    public static void main(String[] args) {
        Options options = new Options();

        OptionGroup batchOperations = new OptionGroup().addOption(writeBatch).addOption(readBatch);
        options.addOption(old).addOptionGroup(batchOperations);
        
        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine commandLine = parser.parse(options, args);
            new JobGet(commandLine).run();
        } catch (ParseException e) {
            logger.error(e);
        }

    }
}
