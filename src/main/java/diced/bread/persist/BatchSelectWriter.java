package diced.bread.persist;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import diced.bread.model.JobInfo;

public class BatchSelectWriter {
    private static final Logger logger = LogManager.getLogger(BatchSelectWriter.class);
    private static final Pattern pattern = Pattern.compile("^\\- \\[( |x)\\] \\[(.+?)\\]\\((.+?)\\) (.+)$");
    

    File file;

    public BatchSelectWriter(String fileName) {
        this.file = new File(fileName);
    }

    // - [x] [id](link) title
    public void appendJob(JobInfo jobInfo) {
        try (FileWriter fileWriter = new FileWriter(file, true)) {
            String id = jobInfo.getScrapeRecord().id();
            String url = jobInfo.getListingUrl().toString();
            String title = jobInfo.getJobTitle();
            String companyName = jobInfo.getCompanyName();
            String line = "- [ ] [" + id + "](" + url + ") " + title + " | " + companyName + "\n";
            fileWriter.write(line);
        } catch (IOException e) {
            logger.error(e);
        }
    }


    public static Set<String> parseBatchSelectFile(File file) {
        Set<String> out = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    String checkbox = matcher.group(1); // " " or "x"
                    String id = matcher.group(2);
                    String link = matcher.group(3);
                    String title = matcher.group(4);
                    if(checkbox.equals("x")){
                        out.add(id);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error reading batch select file", e);
        }
        return out;
    }
}
