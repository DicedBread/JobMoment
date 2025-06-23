package diced.bread.process;

import java.io.ByteArrayOutputStream;
import java.net.URI;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import diced.bread.google.DocContainer;
import diced.bread.google.DriveContainer;
import diced.bread.model.JobInfo;

public class CLWriterProcess extends Thread {
    private static final Logger logger = LogManager.getLogger(CLWriterProcess.class);

    private static final String BASE_DOCUMENT_ID = "16fm0Fyo_N7TulVRo4q8-SJ_DZQqUzPjtKobnAtyW_iY";

    private URI uri;
    private JobInfo jobInfo;
    private DocContainer docService;
    private DriveContainer driveService;
    private String docId;
    private ByteArrayOutputStream pdfData;

    public CLWriterProcess(URI uri, JobInfo jobInfo, DriveContainer driveService, DocContainer docService) {
        this.uri = uri;
        this.jobInfo = jobInfo;
        this.driveService = driveService;
        this.docService = docService;
    }

    @Override
    public void run() {
        super.run();
        docId = driveService.createCopy(BASE_DOCUMENT_ID, jobInfo.getCompanyName());
        if(docId == null){
            logger.error("failed to copy document ending process " + docId);
            return;
        }
        docService.findAndReplace(docId, jobInfo);
        logger.info("downloading pdf data");
        pdfData = driveService.downloadData(docId);
        logger.info("downloaded pdf data");
    }

    public JobInfo getJobInfo() {
        return jobInfo;
    }

    public String getDocId() {
        return docId;
    }

    public ByteArrayOutputStream getPdfData() {
        return pdfData;
    }
}
