package diced.bread.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import diced.bread.client.JobFilter.JobFilter;
import diced.bread.client.seekdata.Datum;
import diced.bread.client.seekdata.Root;
import diced.bread.model.JobInfo;
import diced.bread.model.ScrapeRecord;
import diced.bread.persist.ScrapedLogger;

public class SeekClientPartTime implements Client {
    private final String COUNT_URL = "https://www.seek.co.nz/api/jobsearch/v5/count?siteKey=NZ-Main&where=Northcote+Auckland&page=1&distance=15&pageSize=22&sortmode=ListedDate&workarrangement=2,1,3";

    private final String PROVIDER_NAME = this.getClass().getName();


    private static final Logger logger = LogManager.getLogger(SeekClientPartTime.class);
    public static final int MAX_PAGE_VAL = 100;

    private List<JobFilter> filters = new ArrayList<>();
    ScrapedLogger scrapeStore;

    public SeekClientPartTime(ScrapedLogger scrapedLogger) {
        scrapeStore = scrapedLogger;
    }


    // https://www.seek.co.nz/api/jobsearch/v5/search?siteKey=NZ-Main&where=Northcote+Auckland&page=1&classification=6043&distance=15&pageSize=22&sortmode=ListedDate&workarrangement=2,1,3
    public String getLink(int page, int pageSize) {
        String url = "https://www.seek.co.nz/api/jobsearch/v5/search?siteKey=NZ-Main&where=Northcote+Auckland&page=" + page + "&distance=15&pageSize="+pageSize+"&sortmode=ListedDate&workarrangement=2,1,3";
        return url;
    }

    @Override
    public void addFilter(JobFilter jobFilter) {
        filters.add(jobFilter);
    }

    @Override
    public HashMap<URI, JobInfo> getJobInfo() {
        int count = getJobCount();
        logger.info("retrieving " + count + " jobs");
        int divValue = MAX_PAGE_VAL;
        int v = (int) Math.floor(count / divValue);

        Map<String, Datum> rawData = new HashMap<>();

        for (int i = 1; i <= v; i++) {
            Map<String, Datum> val = GetData(i, divValue);
            rawData.putAll(val);
        }
        logger.info("cached " + rawData.size() + " jobs");

        HashMap<URI, JobInfo> out = new HashMap<>();
        rawData.forEach((id, listing) -> {
            String listingUrl = "https://www.seek.co.nz/job/" + id;
            String companyName = listing.advertiser.description;
            String positionTitle = listing.title;
            boolean isSoftware = false;
            String subclass = listing.classifications.get(0).subclassification.id;
            Date date = listing.listingDate;
            isSoftware = subclass.equals("6290") || subclass.equals("6287"); // software subclasses

            try {
                JobInfo ji = new JobInfo(
                        new URI(listingUrl),
                        companyName,
                        positionTitle,
                        isSoftware,
                        new ScrapeRecord(PROVIDER_NAME, id, new Date()),
                        date
                    );

                if (filterOut(ji, id))
                    return;

                out.put(new URI(listingUrl), ji);
            } catch (URISyntaxException e) {
                logger.error(listingUrl + " invalid uri" + e);
            }
        });

        return out;
    }

    /***
     * determines if the job should be filtered from the listings
     * 
     * @param jobInfo
     * @param id
     * @return true if job should be excluded from result otherwise false
     */
    private boolean filterOut(JobInfo jobInfo, String id) {
        boolean alreadyMade = scrapeStore.existsFromId(PROVIDER_NAME, id);
        if (alreadyMade)
            return true;

        for (JobFilter f : filters) {
            boolean filterEval = f.shouldExclude(jobInfo);
            logger.debug("val: " + filterEval + " " + jobInfo.getJobTitle());
            if (filterEval) {
                logger.debug("excluded " + jobInfo.getJobTitle());
                return true;
            }
        }
        logger.debug("included " + jobInfo.getJobTitle());

        return false;
    }

    /***
     * query seek for job listings
     * 
     * @param page
     * @param pageSize
     * @return raw job listings mapped to id
     */
    private Map<String, Datum> GetData(int page, int pageSize) {
        Map<String, Datum> out = new HashMap<>();

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(new URI(getLink(page, pageSize))).GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> res = client.send(req, BodyHandlers.ofString());
            
            if (res.statusCode() != 200) {
                logger.error("data fetch status " + res.statusCode() + ": " + res.body());
                return out;
            }

            String body = res.body();
            Root root = parseBody(body);
            if (root == null)
                return out;
            // logger.info("caching " + root.data.size() + " jobs");
            root.data.forEach(e -> {
                out.put(e.id, e);
            });
        } catch (URISyntaxException | IOException | InterruptedException e) {
            logger.error("failed to revive data " + e);
        }
        return out;
    }

    /***
     * parse seek json data into objects
     * 
     * @param body
     * @return root seek data object
     */
    private Root parseBody(String body) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Root root = gson.fromJson(o, Root.class);
            return root;
        } catch (JsonSyntaxException e) {
            logger.error("invalid json syntax " + e);
        }
        return null;
    }

    /**
     * query's seek to see how many listings available
     * 
     * @return listing count
     */
    private int getJobCount() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(new URI(COUNT_URL)).GET()
                    .build();

            HttpResponse<String> res = HttpClient.newHttpClient().send(req, BodyHandlers.ofString());

            if (res.statusCode() != 200)
                return 0;
            int count = Integer.parseInt(res.body());
            return count;
        } catch (URISyntaxException | IOException | InterruptedException | NumberFormatException e) {
            logger.error("failed to get job count " + e);
        }
        return 0;
    }

}
