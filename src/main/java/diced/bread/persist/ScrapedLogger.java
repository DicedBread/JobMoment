package diced.bread.persist;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import diced.bread.model.ScrapeRecord;

public class ScrapedLogger {

    private static final Logger logger = LogManager.getLogger(ScrapedLogger.class);
    private static Set<ScrapeRecord> cache;
    private static HashMap<String, Set<String>> searchCache;
    private Pattern pattern = Pattern.compile("^([^,]+),([^,]+),([^,]+)$");
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");


    File file;

    public ScrapedLogger(String file) {
        this.file = new File(file);
    }

    public void logScrapeRecord(ScrapeRecord scrapeRecord) {
        try (java.io.FileWriter writer = new java.io.FileWriter(file, true)) {
            String line = String.format("%s,%s,%s%n",
                    scrapeRecord.provider(),
                    scrapeRecord.id(),
                    sdf.format(scrapeRecord.date()));
            writer.write(line);
            if (cache == null) {
                cache = new HashSet<>();
            }
            cache.add(scrapeRecord);
            logger.info("Logged ScrapeRecord: {}", line.trim());
        } catch (IOException e) {
            logger.error("Failed to log ScrapeRecord", e);
        }
    }

    public Set<ScrapeRecord> getSavedIds() {
        if (cache == null || searchCache == null) {
            logger.info("loading from store file");
            cache = new HashSet<>();
            searchCache = new HashMap<>();
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher matcher = pattern.matcher(line);
                        if (matcher.matches()) {
                            String first = matcher.group(1).trim();
                            String second = matcher.group(2).trim();
                            String dateStr = matcher.group(3).trim();
                            Date date;
                            try {
                                date = sdf.parse(dateStr);
                            } catch (ParseException e) {
                                logger.warn("Failed to parse date: {}", dateStr, e);
                                continue;
                            }
                            ScrapeRecord record = new ScrapeRecord(first, second, date);
                            cacheRecord(record);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Failed to read scrappedLog.log", e);
                }
            }
        }
        return cache;
    }

    public boolean existsFromId(String prov, String id){
        getSavedIds();
        if(!searchCache.containsKey(prov)) return false;
        return searchCache.get(prov).contains(id);
    }

    private void cacheRecord(ScrapeRecord record){
        cache.add(record);
        if(!searchCache.containsKey(record.provider())){
            searchCache.put(record.provider(), new HashSet<String>());
        }
        searchCache.get(record.provider()).add(record.id());
    }
}
