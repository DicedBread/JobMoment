package diced.bread.persist;

import java.io.File;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import diced.bread.model.ScrapeRecord;

public class ScrapedLogger {

    private static final Logger logger = LogManager.getLogger(ScrapedLogger.class);
    private static final Set<ScrapedLogger> cache; 

    File file;

    public void ScrapedLogger() {
        new File("/store").mkdirs();
        file = new File("store/scrappedLog.log");
    }

    public void logScrapeRecord(ScrapeRecord scrapeRecord){
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Set<ScrapeRecord> getSavedIds(){
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
