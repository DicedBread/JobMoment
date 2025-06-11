package diced.bread.model;

import java.net.URI;

public class JobInfo {
    private final URI listingUrl;
    private final String companyName;
    private final String jobTitle;
    private final boolean isSoftware;
    private final ScrapeRecord scrapeRecord;
    
    public JobInfo(URI listingUrl, String companyName, String jobTitle, boolean isSoftware, ScrapeRecord scrapeRecord) {
        this.listingUrl = listingUrl;
        this.companyName = companyName;
        this.jobTitle = jobTitle;
        this.isSoftware = isSoftware;
        this.scrapeRecord = scrapeRecord;
    }  

    public URI getListingUrl() {
        return listingUrl;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public boolean isIsSoftware() {
        return isSoftware;
    }

    public ScrapeRecord getScrapeRecord() {
        return scrapeRecord;
    }
}
