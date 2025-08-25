package diced.bread.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import diced.bread.client.JobFilter.JobFilter;
import diced.bread.client.prospledata.Root;
import diced.bread.client.seekdata.Datum;
import diced.bread.model.JobInfo;
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

        Map<String, Datum> rawData = new HashMap<>();
        
        
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
                return root.data.opportunitiesSearch.totalCount;
            } else {
                logger.error("Failed to fetch job count. Status code: " + response.statusCode());
            }
        } catch (JsonSyntaxException | IOException | InterruptedException | URISyntaxException e) {
            logger.error("Error fetching job count", e);
        }
        return 0;
    }

    private String getLink(int offset, int limit) {
        String url = String.format(
                """
                        https://prosple-gw.global.ssl.fastly.net/internal?operationName=OpportunitiesSearchWithoutStudyFieldFacets&variables=
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
                        }&extensions={
                            "persistedQuery": {
                                "version": 1,
                                "sha256Hash": "ce165d376efa75d024a360439eab240552b2072af08f0e1bda85cd1ba2ad5372"
                            }
                        }
                        """,
                offset, limit);
        return url;
    }

}
