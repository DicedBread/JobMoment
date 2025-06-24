package diced.bread.client.JobFilter;

import java.util.Set;

import diced.bread.model.JobInfo;

public class DoJobInSetFilter implements JobFilter{

    Set<String> jobIds;
    
    public DoJobInSetFilter(Set<String> jobIds){
        this.jobIds = jobIds;
    } 

    @Override
    public boolean shouldExclude(JobInfo jobInfo) {
        return !jobIds.contains(jobInfo.getScrapeRecord().id());        
    }
    
}
