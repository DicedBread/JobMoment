package diced.bread.client.JobFilter;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import diced.bread.model.JobInfo;

public class JobTitleFilter {

    private final Set<String> words;
    private final boolean excludeIfDoesContain;

    public JobTitleFilter(List<String> words, boolean excludeIfDoesContain) {
        this.words = words.stream().map(e -> e.toLowerCase()).collect(Collectors.toSet());
        this.excludeIfDoesContain = excludeIfDoesContain;
    }

    /***
     * return true if contains or does not contain depending on
     * 
     * @param jobInfo
     * @return
     */
    public boolean shouldExclude(JobInfo jobInfo) {
        for (String t : words) {
            String title = jobInfo.getJobTitle().toLowerCase();
            boolean titleDoesContainWord = title.contains(t);
            boolean v = (titleDoesContainWord && excludeIfDoesContain)
                    || (!titleDoesContainWord && !excludeIfDoesContain);
            if (!excludeIfDoesContain) {
                if(titleDoesContainWord){
                    return false;
                }
            } else {
                if (v) {
                    return true;
                }
            }
        }
        return !excludeIfDoesContain;
    }
}
