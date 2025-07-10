package diced.bread.client.JobFilter;

import java.util.Set;

import diced.bread.model.JobInfo;

public class JobIdInclusionFilter implements JobFilter{

    Set<String> jobIds;
    
    public JobIdInclusionFilter(Set<String> jobIds){
        this.jobIds = jobIds;
    } 

    @Override
    public boolean shouldExclude(JobInfo jobInfo) {
        return !jobIds.contains(jobInfo.getScrapeRecord().id());        
    }
    
}
