package diced.bread.persist;

import java.net.URI;

public class JobApply {
    private URI url;
    private String pdfFileLoc;
    private boolean applied;

    public JobApply(URI url, String pdfFileLoc, boolean applied){
        this.url = url;
        this.pdfFileLoc = pdfFileLoc;
        this.applied = applied;
    }

    @Override
    public String toString() {
        String v = (applied)? "x" : " ";
        return "- [" + v + "] " + url.toString() +" | "+ pdfFileLoc; 
    }
}
