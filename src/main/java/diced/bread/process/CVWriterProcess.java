package diced.bread.process;

import java.net.URI;

import diced.bread.DocContainer;
import diced.bread.DriveContainer;
import diced.bread.model.JobInfo;

public class CVWriterProcess extends Thread {
    private static final String BASE_DOCUMENT_ID = "16fm0Fyo_N7TulVRo4q8-SJ_DZQqUzPjtKobnAtyW_iY";

    private URI uri;
    private JobInfo jobInfo;
    private DocContainer docService;
    private DriveContainer driveService;
    private String docId;

    public CVWriterProcess(URI uri, JobInfo jobInfo, DriveContainer driveService, DocContainer docService) {
        this.uri = uri;
        this.jobInfo = jobInfo;
        this.driveService = driveService;
        this.docService = docService;
    }

    @Override
    public void run() {
        super.run();
        docId = driveService.createCopy(BASE_DOCUMENT_ID, jobInfo.getCompanyName());
        docService.findAndReplace(docId, jobInfo);
    }

    public JobInfo getJobInfo() {
        return jobInfo;
    }

    public String getDocId() {
        return docId;
    }
}
