package diced.bread;

import java.net.URI;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import diced.bread.client.JobFilter.JobTitleFilter;
import diced.bread.model.JobInfo;
import diced.bread.model.ScrapeRecord;

public class JobFilterTest {

    

    @Test
    public void ExIfDoesContain_Present() throws Exception {
        List<String> words = List.of("testWord");

        JobTitleFilter filter = new JobTitleFilter(words, true);
        JobInfo job = new JobInfo(
                new URI("https://example.com/job/1"),
                "Example Company",
                "Software Engineer testWord",
                true,
                new ScrapeRecord("seek", "1", new Date()), null
        );
        assertTrue("should exclude as words present", filter.shouldExclude(job));
    }

    @Test
    public void ExIfDoesContain_NotPresent() throws Exception {
        List<String> words = List.of("testWord");


        JobTitleFilter filter = new JobTitleFilter(words, true);
        JobInfo job = new JobInfo(
                new URI("https://example.com/job/1"),
                "Example Company",
                "Software Engineer",
                true,
                new ScrapeRecord("seek", "1", new Date()), null
        );
        assertFalse("should not exclude as words not present", filter.shouldExclude(job));
    }

    @Test
    public void ExIfDoesNotContain_Present() throws Exception {
        List<String> words = List.of("testWord");

        JobTitleFilter filter = new JobTitleFilter(words, false);
        JobInfo job = new JobInfo(
                new URI("https://example.com/job/1"),
                "Example Company",
                "Software Engineer TestWord",
                true,
                new ScrapeRecord("seek", "1", new Date()), null
        );
        assertFalse("Should not exclude", filter.shouldExclude(job));
    }

    @Test
    public void ExIfDoesNotContain_NotPresent_multiWord() throws Exception {
        List<String> words = List.of("randomWord", "testWord");


        JobTitleFilter filter = new JobTitleFilter(words, false);
        JobInfo job = new JobInfo(
                new URI("https://example.com/job/1"),
                "Example Company",
                "Software Engineer",
                true,
                new ScrapeRecord("seek", "1", new Date()), null
        );
        assertTrue("Should exclude", filter.shouldExclude(job));
    }


        @Test
    public void ExIfDoesContain_Present_multiWord() throws Exception {
        List<String> words = List.of("randomWord", "testWord");


        JobTitleFilter filter = new JobTitleFilter(words, true);
        JobInfo job = new JobInfo(
                new URI("https://example.com/job/1"),
                "Example Company",
                "Software Engineer testWord",
                true,
                new ScrapeRecord("seek", "1", new Date()), null
        );
        assertTrue("should exclude as words present", filter.shouldExclude(job));
    }

    @Test
    public void ExIfDoesContain_NotPresent_multiWord() throws Exception {
        List<String> words = List.of("randomWord", "testWord");



        JobTitleFilter filter = new JobTitleFilter(words, true);
        JobInfo job = new JobInfo(
                new URI("https://example.com/job/1"),
                "Example Company",
                "Software Engineer",
                true,
                new ScrapeRecord("seek", "1", new Date()), null
        );
        assertFalse("should not exclude as words not present", filter.shouldExclude(job));
    }

    @Test
    public void ExIfDoesNotContain_Present_multiWord() throws Exception {
        List<String> words = List.of("randomWord", "testWord");

        JobTitleFilter filter = new JobTitleFilter(words, false);
        JobInfo job = new JobInfo(
                new URI("https://example.com/job/1"),
                "Example Company",
                "Software Engineer TestWord",
                true,
                new ScrapeRecord("seek", "1", new Date()), null
        );
        assertFalse("Should not exclude", filter.shouldExclude(job));
    }

    @Test
    public void ExIfDoesNotContain_NotPresent() throws Exception {
        List<String> words = List.of("testWord");

        JobTitleFilter filter = new JobTitleFilter(words, false);
        JobInfo job = new JobInfo(
                new URI("https://example.com/job/1"),
                "Example Company",
                "Software Engineer",
                true,
                new ScrapeRecord("seek", "1", new Date()), null
        );
        assertTrue("Should exclude", filter.shouldExclude(job));
    }
}
