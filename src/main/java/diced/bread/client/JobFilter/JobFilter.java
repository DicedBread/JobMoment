package diced.bread.client.JobFilter;

import java.util.Set;

import diced.bread.model.JobInfo;

public class JobFilter {


    Set<String> words = Set.of("entry", "support", "level 1", "junior");


    public boolean filter(JobInfo jobInfo){
        for (String t : words) {
            boolean v = jobInfo.getJobTitle().toLowerCase().contains(t);
            if(v) return v; 
        }
        return false;
    }
}
