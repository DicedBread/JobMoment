package diced.bread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.Color;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.InsertTextRequest;
import com.google.api.services.docs.v1.model.Location;
import com.google.api.services.docs.v1.model.Paragraph;
import com.google.api.services.docs.v1.model.ParagraphElement;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.docs.v1.model.StructuralElement;
import com.google.api.services.docs.v1.model.TextStyle;

public class DocContainer {
    Docs docService;

    final private String YELLOW = "{\"rgbColor\":{\"green\":1.0,\"red\":1.0}}";
    final private String BLUE = "{\"rgbColor\":{\"blue\":1.0,\"green\":1.0}}";

    List<Request> test = new ArrayList<>();

    public DocContainer(Docs doc) {
        this.docService = doc;
    }

    public void findThing(String docId) {
        try {
            Document doc = docService.documents().get(docId).setIncludeTabsContent(true).execute();
            // DocumentTab tab = doc.getTabs().get(0).getDocumentTab();

            List<StructuralElement> bodyContent = doc.getBody().getContent();
            bodyContent.forEach(e -> {
                Paragraph p = e.getParagraph();
                if (p == null)
                    return;
                try {
                    List<ParagraphElement> paraElements = p.getElements();
                    paraElements.forEach(paraSubElement -> {
                        parseParagraphElement(paraSubElement);
                    });
                } catch (Exception a) {
                    System.out.println(a);
                }
            });

        } catch (IOException e) {
            System.out.println(e);
        }
    }

    private void parseParagraphElement(ParagraphElement element) {
        int start = element.getStartIndex();
        int end = element.getEndIndex();
        String content = element.getTextRun().getContent();
        TextStyle style = element.getTextRun().getTextStyle();
        if (style.getBackgroundColor() == null)
            return;

        Color c = style.getBackgroundColor().getColor();

        if (c.toString().equals(BLUE)) {
            // conditional
        } else {
            test.add(new Request()
                    .setInsertText(new InsertTextRequest().setText("test").setLocation(new Location().setIndex(end))));
        }

        System.out.println("start: " + start + " end: " + end + " length: " + content.length());
        System.out.println(content.trim().strip());
        System.out.println("color: " + c);
    }



}
