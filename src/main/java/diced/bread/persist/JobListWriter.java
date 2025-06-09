package diced.bread.persist;

import java.io.FileWriter;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class JobListWriter {
    private static final Logger logger = LogManager.getLogger(JobListWriter.class);

    private FileWriter file;
    public JobListWriter(String filePath){
        try{
            file = new FileWriter(filePath);
        }catch(IOException e){
            logger.error(e);
        }
    }

    public void append(JobApply jobApply){        
        try {
            file.write(jobApply.toString() + "\n");
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
