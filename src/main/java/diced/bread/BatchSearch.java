package diced.bread;

import java.net.URI;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import diced.bread.client.Client;
import diced.bread.model.JobInfo;
import diced.bread.persist.BatchSelectWriter;

public class BatchSearch {
    private static final Logger logger = LogManager.getLogger(BatchSearch.class);

    private final Client client;
    private final String filename;

    public BatchSearch(Client client, String filename){
        this.client = client;
        this.filename = filename;
    }

    public void run(){
        Map<URI, JobInfo> listing = client.getJobInfo();
        BatchSelectWriter batchSelectWriter = new BatchSelectWriter(filename);
        listing.forEach((k, v) -> {
            batchSelectWriter.appendJob(v);
        });
        logger.info("written batch file");
    }
}
