package diced.bread.model;

public class JobInfo {
    private final String listingUrl;
    private final String companyName;
    private final String jobTitle;
    private final boolean isSoftware;
    
    public JobInfo(String listingUrl, String companyName, String jobTitle, boolean isSoftware) {
        this.listingUrl = listingUrl;
        this.companyName = companyName;
        this.jobTitle = jobTitle;
        this.isSoftware = isSoftware;
    }  

    public String getListingUrl() {
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
}
