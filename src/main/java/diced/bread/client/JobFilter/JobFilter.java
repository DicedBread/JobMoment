package diced.bread.client.JobFilter;

import diced.bread.model.JobInfo;

public interface JobFilter {
    public boolean shouldExclude(JobInfo jobInfo);
}
