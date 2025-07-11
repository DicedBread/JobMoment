package diced.bread.client.JobFilter;

import java.util.Set;
import java.util.stream.Collectors;

import diced.bread.model.JobInfo;

/***
 * exclude job if title contains words
 */
public class TitleContainsFilter implements JobFilter {

    private final Set<String> words;

    public TitleContainsFilter(Set<String> words) {
        this.words = words.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean shouldExclude(JobInfo jobInfo) {
        String title = jobInfo.getJobTitle().toLowerCase();
        for (String word : words) {
            if (title.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
