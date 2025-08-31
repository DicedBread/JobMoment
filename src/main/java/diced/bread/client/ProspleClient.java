package diced.bread.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import diced.bread.client.JobFilter.JobFilter;
import diced.bread.client.prospledata.Opportunity;
import diced.bread.client.prospledata.Root;
import diced.bread.model.JobInfo;
import diced.bread.model.ScrapeRecord;
import diced.bread.persist.ScrapedLogger;

public class ProspleClient implements Client {

    private final String PROVIDER_NAME = this.getClass().getName();
    private static final Logger logger = LogManager.getLogger(ProspleClient.class);

    private ScrapedLogger scrapeStore;

    private List<JobFilter> filters = new ArrayList<>();

    public ProspleClient(ScrapedLogger scrapedLogger) {
        scrapeStore = scrapedLogger;
    }

    @Override
    public Map<URI, JobInfo> getJobInfo() {
        int count = getJobCount();
        Map<String, Opportunity> rawData = queryListings(count);

        HashMap<URI, JobInfo> out = new HashMap<>();

        rawData.forEach((id, listing) -> {
            
            String listingUrl = "https://nz.prosple.com" + listing.detailPageURL;
            
            String positionTitle = listing.title;
            // if(listing.parentEmployer == null){
            //     logger.warn(positionTitle + " id= " + id + " has no parent employer");
            //     return;
            // }
            String companyName = listing.parentEmployer.title;
            boolean isSoftware = true;
            // String subclass = listing.classifications.get(0).subclassification.id;
            Date date = listing.applicationsCloseDate;
            // isSoftware = subclass.equals("6290") || subclass.equals("6287"); // software
            // subclasses

            try {
                JobInfo ji = new JobInfo(
                        new URI(listingUrl),
                        companyName,
                        positionTitle,
                        isSoftware,
                        new ScrapeRecord(PROVIDER_NAME, id, new Date()),
                        date);

                if (filterOut(ji, id))
                    return;

                out.put(new URI(listingUrl), ji);
            } catch (URISyntaxException e) {
                logger.error(listingUrl + " invalid uri" + e);
            }
        });

        return out;

    }

    private Map<String, Opportunity> queryListings(int count) {
        Map<String, Opportunity> listings = new HashMap<>();
        
        try {
            String f = new URI(getLink(0, count)).toString();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(getLink(0, count)))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                JsonElement je = JsonParser.parseString(response.body());
                String prettyJsonString = gson.toJson(je);
                
                Root root = gson.fromJson(response.body(), Root.class);
                
                for (Opportunity datum : root.data.opportunitiesSearch.opportunities) {
                    listings.put(datum.id, datum);
                }

                logger.info("Fetched {} listings", listings.size());
            } else {
                logger.error("Failed to fetch listings. Status code: " + response.statusCode());
            }
        } catch (JsonSyntaxException | IOException | InterruptedException | URISyntaxException e) {
            logger.error("Error fetching listings", e);
        }

        return listings;
    }

    @Override
    public void addFilter(JobFilter jobFilter) {
        filters.add(jobFilter);
    }

    private int getJobCount() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(getLink(0, 1)))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Gson gson = new Gson();
                Root root = gson.fromJson(response.body(), Root.class);
                return root.data.opportunitiesSearch.resultCount;
            } else {
                logger.error("Failed to fetch job count. Status code: " + response.statusCode());
            }
        } catch (JsonSyntaxException | IOException | InterruptedException | URISyntaxException e) {
            logger.error("Error fetching job count", e);
        }
        return 0;
    }

    private String getLink(int offset, int limit) {
        String url = "https://prosple-gw.global.ssl.fastly.net/internal?operationName=OpportunitiesSearchWithoutStudyFieldFacets&variables=";
        String payload = String.format(
                """
                        {
                            "parameters": {
                                "gid": "2",
                                "range": {
                                    "offset": %d,
                                    "limit": %d
                                },
                                "sortBy": {
                                    "criteria": "NEWEST_OPPORTUNITIES",
                                    "direction": "DESC"
                                },
                                "workRightLocation": "29208",
                                "selectedStartDateRangeFacet": null,
                                "selectedLocationFacets": [
                                    {
                                        "id": "796",
                                        "childFacets": [
                                            {
                                                "id": "797",
                                                "childFacets": []
                                            }
                                        ]
                                    }
                                ],
                                "studyFieldsFilter": "506"
                            }
                        }
                    """,
                offset, limit).replaceAll("\\s+", "");

                String exte = """
                        {
                            "persistedQuery": {
                                "version": 1,
                                "sha256Hash": "ce165d376efa75d024a360439eab240552b2072af08f0e1bda85cd1ba2ad5372"
                            }
                        }
                        """.replaceAll("\\s+", "");
            String formatedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8);
            String formaterExt = URLEncoder.encode(exte, StandardCharsets.UTF_8);
        return url + formatedPayload + "&extensions=" + formaterExt;
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
}
