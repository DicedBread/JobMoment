package diced.bread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentResponse;
import com.google.api.services.docs.v1.model.Color;
import com.google.api.services.docs.v1.model.DeleteContentRangeRequest;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.InsertTextRequest;
import com.google.api.services.docs.v1.model.Location;
import com.google.api.services.docs.v1.model.OptionalColor;
import com.google.api.services.docs.v1.model.Paragraph;
import com.google.api.services.docs.v1.model.ParagraphElement;
import com.google.api.services.docs.v1.model.Range;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.docs.v1.model.StructuralElement;
import com.google.api.services.docs.v1.model.TextStyle;
import com.google.api.services.docs.v1.model.UpdateTextStyleRequest;

import diced.bread.model.JobInfo;
import diced.bread.util.ConditionalParser;

public class DocContainer {
    private static final Logger logger = LogManager.getLogger(DocContainer.class);

    Docs docService;

    final private String YELLOW = "{\"rgbColor\":{\"green\":1.0,\"red\":1.0}}";
    final private String BLUE = "{\"rgbColor\":{\"blue\":1.0,\"green\":1.0}}";


    public DocContainer(Docs doc) {
        this.docService = doc;
    }

    public void findAndReplace(String docId, JobInfo jobInfo) {
        List<Request> ediList = new ArrayList<>();

        try {
            logger.info("replacing on file " + docId);
            Document doc = docService.documents().get(docId).execute();
            int endIndex = 0;
            
            List<StructuralElement> bodyContent = doc.getBody().getContent();
            bodyContent.forEach(e -> {
                Paragraph p = e.getParagraph();
                if (p == null)
                    return;
                try {
                    List<ParagraphElement> paraElements = p.getElements();
                    paraElements.forEach(paraSubElement -> {
                        parseParagraphElement(paraSubElement, jobInfo, ediList);
                    });
                } catch (Exception a) {
                    System.out.println(a);
                }
            });

            int s = bodyContent.get(bodyContent.size() - 1).getParagraph().getElements().size();
            endIndex = bodyContent.get(bodyContent.size() - 1).getParagraph().getElements().get(s - 1).getEndIndex();


            ArrayList<Request> l = new ArrayList<>(ediList);
            Collections.reverse(l);



            BatchUpdateDocumentRequest req = new BatchUpdateDocumentRequest().setRequests(l);
            BatchUpdateDocumentResponse res = docService.documents().batchUpdate(doc.getDocumentId(), req).execute();
            logger.info("replaced on file " + docId);
        } catch (IOException e) {
            logger.error(e);
        }
    }

    private void parseParagraphElement(ParagraphElement element, JobInfo jobInfo, List<Request> ediList) {
        int start = element.getStartIndex();
        int end = element.getEndIndex();
        String content = element.getTextRun().getContent();
        TextStyle style = element.getTextRun().getTextStyle();
        // boolean isBold = style.getBold();

        if (style.getBackgroundColor() == null)
            return;

        Color c = style.getBackgroundColor().getColor();

        if (c.toString().equals(BLUE)) {
            if(!ConditionalParser.isValid(content)) return;
            ConditionalParser val = new ConditionalParser(content);
            val.setValue(jobInfo.isIsSoftware());
            String newString = val.getResult();
            List<Request> res = replace(start, end, newString, content);
            Collections.reverse(res);
            ediList.addAll(res);

        } else {
            String newString = "test";

            if(content.contains("company name")){
                newString = jobInfo.getCompanyName();
            }else if (content.contains("position")) {
                newString = jobInfo.getJobTitle();
            }else{
                logger.warn("invalid text replace");
            }

            List<Request> res = replace(start, end, newString, content);
            Collections.reverse(res);
            ediList.addAll(res);
        }

        // System.out.println("start: " + start + " end: " + end + " length: " +
        // content.length());
        // System.out.println(content);
        // System.out.println("color: " + c);
    }

    /***
     * returns list of requests to replace text at index in order they should be sent
     * @param start
     * @param end
     * @param newText
     * @param content
     * @return
     */
    private List<Request> replace(int start, int end, String newText, String content) {
        ArrayList<Request> out = new ArrayList<>();

        boolean hasEndLine = content.charAt(content.length() - 1) == '\n';
        int endIndex = (hasEndLine) ? end - 1 : end;


        Request removeHighlight = new Request().setUpdateTextStyle(new UpdateTextStyleRequest().setTextStyle(
                new TextStyle()
                .setBackgroundColor(new OptionalColor().setColor(null
                ))
            ).setRange(new Range().setStartIndex(start).setEndIndex(endIndex))
            .setFields("backgroundColor")
            );

        out.add(removeHighlight);

        out.add(new Request().setInsertText(
            new InsertTextRequest()
                .setText(newText)
                .setLocation(new Location().setIndex(endIndex))
            )
        );        
        out.add(new Request().setDeleteContentRange(
            new DeleteContentRangeRequest()
            .setRange(
                new Range()
                    .setStartIndex(start)
                    .setEndIndex(endIndex))
            )
        );


        return out;
    }

}
