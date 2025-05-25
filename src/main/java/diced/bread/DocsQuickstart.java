package diced.bread;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.DocsScopes;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.docs.v1.model.DocumentTab;
import com.google.api.services.docs.v1.model.Paragraph;
import com.google.api.services.docs.v1.model.StructuralElement;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

public class DocsQuickstart {
    private static final String APPLICATION_NAME = "Google Docs API Java Service Account";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String SERVICE_ACCOUNT_KEY_PATH = "service-account.json";
    private static final String DOCUMENT_ID = "1IceB9pT6Q4huWmpJoYn6HLy6_IP9r7xDXt3QZ6vCDaA";

    public static void main(String... args) throws IOException, GeneralSecurityException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        // Load service account credentials
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new FileInputStream(SERVICE_ACCOUNT_KEY_PATH))
                .createScoped(Collections.singleton(DocsScopes.DOCUMENTS))
                .createScoped(Collections.singleton(DriveScopes.DRIVE));

        Docs service = new Docs.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Fetch the document
        Document response = service.documents().get(DOCUMENT_ID).setIncludeTabsContent(true).execute();
        String title = response.getTitle();
        DocumentTab tab = response.getTabs().get(0).getDocumentTab();
        tab.getBody().getContent().forEach(e -> {
            StructuralElement element = e;
            Paragraph para = element.getParagraph();
            // try {
            // // System.out.println(e.toPrettyString());
            // } catch (IOException e1) {
            // // TODO Auto-generated catch block
            // System.out.println(e1);
            // }
        });

        System.out.printf("The title of the doc is: %s\n", title);

        // Download as PDF
        // ByteArrayOutputStream pdfBytes = download(httpTransport, credentials, DOCUMENT_ID, "output.pdf");
        // try (FileOutputStream fos = new FileOutputStream("output.pdf")) {
        //     pdfBytes.writeTo(fos);
        //     System.out.println("PDF saved to output.pdf");
        // }

        downloadTab(httpTransport, credentials, DOCUMENT_ID, title);
    }

    /**
     * Downloads a Google Doc as a PDF file using the Drive API export endpoint.
     */
    public static ByteArrayOutputStream download(HttpTransport httpTransport, GoogleCredentials credentials,
            String documentId, String outputPath) throws IOException {

        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        Drive service = new Drive.Builder(new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer)
                .setApplicationName("Drive samples")
                .build();

        try {
            OutputStream outputStream = new ByteArrayOutputStream();
            service.files().export(documentId, "application/pdf").executeMediaAndDownloadTo(outputStream);
            // service.files().get(documentId).executeMediaAndDownloadTo(outputStream);
            return (ByteArrayOutputStream) outputStream;
        } catch (GoogleJsonResponseException e) {
            System.err.println("Unable to move file: " + e.getDetails());
            throw e;
        }

        // ByteArrayOutputStream pdfBytes = download(httpTransport, credentials, DOCUMENT_ID, "output.pdf");
        // try (FileOutputStream fos = new FileOutputStream("output.pdf")) {
        //     pdfBytes.writeTo(fos);
        //     System.out.println("PDF saved to output.pdf");
        // }

    }

    public static void downloadTab(HttpTransport httpTransport, GoogleCredentials credentials, String documentId, String outputPath) throws IOException {
        // The export endpoint for Google Docs via Drive API
        String exportUrl = String.format("https://www.googleapis.com/drive/v3/files/%s/export?mimeType=application/pdf?tab=t.0", documentId);

        // Add Drive scope for export
        if (!credentials.createScopedRequired()) {
            credentials = credentials.createScoped(Collections.singleton("https://www.googleapis.com/auth/drive.readonly"));
        }

        HttpRequest request = httpTransport.createRequestFactory(new HttpCredentialsAdapter(credentials))
                .buildGetRequest(new GenericUrl(exportUrl));
        HttpResponse response = request.execute();

        try (OutputStream out = new FileOutputStream(outputPath)) {
            response.download(out);
            System.out.println("Downloaded PDF to: " + outputPath);
        } finally {
            response.disconnect();
        }
    }

}