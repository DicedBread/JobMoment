package diced.bread;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
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

        options.addOption(old);

        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine commandLine = parser.parse(options, args);
            new JobGet(commandLine).run();
        } catch (ParseException e) {
            logger.error(e);
        }

    }
}
