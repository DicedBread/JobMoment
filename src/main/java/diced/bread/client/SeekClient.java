package diced.bread.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import diced.bread.client.seekdata.Datum;
import diced.bread.client.seekdata.Root;
import diced.bread.model.JobInfo;
import diced.bread.model.ScrapeRecord;
import diced.bread.persist.ScrapedLogger;

public class SeekClient implements Client {
    private static final Logger logger = LogManager.getLogger(SeekClient.class);
    private final Map<String, Datum> rawData = new HashMap<String, Datum>();

    public String getLink(int page, int pageSize) {
        return "https://www.seek.co.nz/api/jobsearch/v5/search?where=All+Auckland&page=" + page
                + "&classification=6281&sortmode=ListedDate&workarrangement=2,1,3&pageSize=" + pageSize;
    }

    public void GetData(int page, int pageSize) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(new URI(getLink(page, pageSize))).GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> res = client.send(req, BodyHandlers.ofString());

            String s = res.body();
            JsonObject o = JsonParser.parseString(s).getAsJsonObject();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String prettyJsonString = gson.toJson(o);

            Root r = gson.fromJson(o, Root.class);
            
            // SeekStore store = new SeekStore();
            ScrapedLogger store = new ScrapedLogger();

            // Set<String> saved = store.getSavedIds();
            // List<Datum> li = r.data.stream().filter(e -> saved.contains(e.id)).toList();
            System.out.println(r.data.size());
            r.data.forEach(e -> {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, -6);
                Date sevenDaysAgo = cal.getTime();

                // if(sevenDaysAgo.getTime() > e.listingDate.getTime()) return; 

                // from id and prov
                if (store.existsFromId(SeekClient.class.getName(), s)) return;
                rawData.put(e.id, e);
            });

            // store.logSeekDataIds(rawData.values().stream().map(e -> e.id).toList());

        } catch (URISyntaxException | IOException | InterruptedException e) {
            System.out.println(e);
        }
    }

    public HashMap<URI, JobInfo> getJobInfo() {
        HashMap<URI, JobInfo> out = new HashMap<>();
        rawData.forEach((k, v) -> {
            String listingUrl = "https://www.seek.co.nz/job/" + k;
            String companyName = v.advertiser.description;
            String positionTitle = v.title;
            boolean isSoftware = false;

            String id = v.id;
            // String allText = "";
            // v.bulletPoints.forEach(e -> {
            // allText.concat(e + " ");
            // });
            // allText.concat(companyName + " ");
            // allText.concat(positionTitle + " ");
            // allText.concat(companyName + " ");

            String subclass = v.classifications.get(0).subclassification.id;

            if (subclass.equals("6290") || subclass.equals("6287")) {
                isSoftware = true;
            }
            try {
                JobInfo ji = new JobInfo(new URI(listingUrl), companyName, positionTitle, isSoftware,
                    new ScrapeRecord(SeekClient.class.getName(), id, new Date()) 
                );
                out.put(new URI(listingUrl), ji);
            } catch (URISyntaxException e) {
                logger.error(listingUrl + " invalid uri" + e);
            }
        });

        return out;
    }

}
