package diced.bread.persist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SummaryWriter {
        private static final Logger logger = LogManager.getLogger(SummaryWriter.class);


    private String outFolderDir;
    private String pdfDir;
    private File summaryList;

    public SummaryWriter(String outFolderDir) {
        this.outFolderDir = outFolderDir;
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss");
        String formattedDate = formatter.format(date);
        String dir = "out/" + formattedDate + "/";
        pdfDir = dir + "pdf/";
        new File(pdfDir).mkdirs();
        summaryList = new File(dir + "list.md");
    }

    public void appendJob(JobApply jobApply){
        try (FileWriter fileWriter = new FileWriter(summaryList, true)) {
            fileWriter.write(jobApply.toString() + "\n");
        } catch (IOException e) {
            logger.error(e);
        }
    }

    public void appendFile(ByteArrayOutputStream data, String fileName){
        File f = new File(pdfDir + fileName);   
        try (OutputStream dos = new FileOutputStream(f)) {
            dos.write(data.toByteArray());
            dos.flush();
        } catch (IOException e) {
            logger.error("failed to write file" + e);;
        }
    }
}
