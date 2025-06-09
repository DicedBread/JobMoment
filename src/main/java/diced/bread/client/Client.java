package diced.bread.client;

import java.net.URI;
import java.util.Map;

import diced.bread.model.JobInfo;

public interface Client {
    public Map<URI, JobInfo> getJobInfo();
}
