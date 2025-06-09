package diced.bread.persist;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class JobOutWriter {
    private String dir;

    private JobListWriter writer;
    private String pdfDir;

    public JobOutWriter() {
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss");
        String formattedDate = formatter.format(date);
        dir = "out/" + formattedDate + "/";
        pdfDir = dir + "pdf/";
        new File(pdfDir).mkdirs();
        writer = new JobListWriter(dir + "listings.md");
    }

    public String getDir() {
        return dir;
    }

    public String getPdfDir() {
        return pdfDir;
    }

    public JobListWriter getWriter() {
        return writer;
    }
}
