package site.easy.to.build.crm.google.service.docs;

import com.google.api.client.http.*;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.google.service.drive.GoogleDriveApiService;
import site.easy.to.build.crm.google.util.GoogleApiHelper;
import site.easy.to.build.crm.service.user.OAuthUserService;
import site.easy.to.build.crm.google.util.GsonUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GoogleDocsApiServiceImpl implements GoogleDocsApiService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDocsApiServiceImpl.class);
    private static final String DOCS_API_BASE_URL = "https://docs.googleapis.com/v1/documents";
    private static final String DRIVE_API_BASE_URL = "https://www.googleapis.com/drive/v3/files";

    @Autowired
    private OAuthUserService oAuthUserService;

    @Autowired
    private GoogleDriveApiService googleDriveApiService;

    @Override
    public String copyTemplateDocument(OAuthUser oAuthUser, String templateId, String targetFolderId, String newName)
            throws IOException, GeneralSecurityException {

        logger.info("Copying template document {} to folder {}", templateId, targetFolderId);

        String accessToken = oAuthUserService.refreshAccessTokenIfNeeded(oAuthUser);
        HttpRequestFactory requestFactory = GoogleApiHelper.createRequestFactory(accessToken);

        // Use Drive API to copy the file
        GenericUrl copyUrl = new GenericUrl(DRIVE_API_BASE_URL + "/" + templateId + "/copy");

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("name", newName);

        if (targetFolderId != null && !targetFolderId.isEmpty()) {
            JsonArray parents = new JsonArray();
            parents.add(targetFolderId);
            requestBody.add("parents", parents);
        }

        HttpContent content = ByteArrayContent.fromString("application/json", requestBody.toString());
        HttpRequest request = requestFactory.buildPostRequest(copyUrl, content);
        HttpResponse response = request.execute();

        String responseBody = response.parseAsString();
        JsonObject jsonResponse = GsonUtil.fromJson(responseBody);

        String copiedFileId = jsonResponse.get("id").getAsString();
        logger.info("Template copied successfully. New document ID: {}", copiedFileId);

        return copiedFileId;
    }

    @Override
    public void replaceAllMergeFields(OAuthUser oAuthUser, String documentId, Map<String, String> mergeData)
            throws IOException, GeneralSecurityException {

        logger.info("Replacing merge fields in document {} with {} fields", documentId, mergeData.size());

        String accessToken = oAuthUserService.refreshAccessTokenIfNeeded(oAuthUser);
        HttpRequestFactory requestFactory = GoogleApiHelper.createRequestFactory(accessToken);

        // Build batch update request with ReplaceAllTextRequest for each merge field
        JsonObject batchUpdateBody = new JsonObject();
        JsonArray requests = new JsonArray();

        for (Map.Entry<String, String> entry : mergeData.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String replacementText = entry.getValue() != null ? entry.getValue() : "";

            JsonObject replaceRequest = new JsonObject();
            JsonObject replaceAllText = new JsonObject();

            // ContainsText criteria
            JsonObject containsText = new JsonObject();
            containsText.addProperty("text", placeholder);
            containsText.addProperty("matchCase", true);

            replaceAllText.add("containsText", containsText);
            replaceAllText.addProperty("replaceText", replacementText);

            replaceRequest.add("replaceAllText", replaceAllText);
            requests.add(replaceRequest);
        }

        batchUpdateBody.add("requests", requests);

        GenericUrl batchUpdateUrl = new GenericUrl(DOCS_API_BASE_URL + "/" + documentId + ":batchUpdate");
        HttpContent content = ByteArrayContent.fromString("application/json", batchUpdateBody.toString());
        HttpRequest request = requestFactory.buildPostRequest(batchUpdateUrl, content);

        HttpResponse response = request.execute();
        logger.info("Merge fields replaced successfully in document {}", documentId);
    }

    @Override
    public String convertDocumentToPdf(OAuthUser oAuthUser, String documentId, String targetFolderId, String pdfName)
            throws IOException, GeneralSecurityException {

        logger.info("Converting document {} to PDF: {}", documentId, pdfName);

        String accessToken = oAuthUserService.refreshAccessTokenIfNeeded(oAuthUser);

        // Export document as PDF
        GenericUrl exportUrl = new GenericUrl(DRIVE_API_BASE_URL + "/" + documentId + "/export?mimeType=application/pdf");
        HttpRequestFactory requestFactory = GoogleApiHelper.createRequestFactory(accessToken);
        HttpRequest exportRequest = requestFactory.buildGetRequest(exportUrl);
        HttpResponse exportResponse = exportRequest.execute();

        // Read PDF bytes
        byte[] pdfBytes;
        try (InputStream inputStream = exportResponse.getContent();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            pdfBytes = outputStream.toByteArray();
        }

        // Upload PDF to target folder
        String pdfFileId = googleDriveApiService.uploadFileWithMimeType(
            oAuthUser,
            pdfName.endsWith(".pdf") ? pdfName : pdfName + ".pdf",
            pdfBytes,
            "application/pdf",
            targetFolderId
        );

        logger.info("Document converted to PDF successfully. PDF file ID: {}", pdfFileId);
        return pdfFileId;
    }

    @Override
    public byte[] downloadPdfAsBytes(OAuthUser oAuthUser, String pdfFileId)
            throws IOException, GeneralSecurityException {

        logger.info("Downloading PDF file {} as bytes", pdfFileId);

        String accessToken = oAuthUserService.refreshAccessTokenIfNeeded(oAuthUser);
        HttpRequestFactory requestFactory = GoogleApiHelper.createRequestFactory(accessToken);

        GenericUrl downloadUrl = new GenericUrl(DRIVE_API_BASE_URL + "/" + pdfFileId + "?alt=media");
        HttpRequest request = requestFactory.buildGetRequest(downloadUrl);
        HttpResponse response = request.execute();

        try (InputStream inputStream = response.getContent();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            byte[] pdfBytes = outputStream.toByteArray();
            logger.info("Downloaded PDF, size: {} bytes", pdfBytes.length);
            return pdfBytes;
        }
    }

    @Override
    public String getDocumentContent(OAuthUser oAuthUser, String documentId)
            throws IOException, GeneralSecurityException {

        String accessToken = oAuthUserService.refreshAccessTokenIfNeeded(oAuthUser);
        HttpRequestFactory requestFactory = GoogleApiHelper.createRequestFactory(accessToken);

        GenericUrl documentUrl = new GenericUrl(DOCS_API_BASE_URL + "/" + documentId);
        HttpRequest request = requestFactory.buildGetRequest(documentUrl);
        HttpResponse response = request.execute();

        String responseBody = response.parseAsString();
        JsonObject doc = GsonUtil.fromJson(responseBody);

        // Extract text content from document body
        StringBuilder content = new StringBuilder();
        if (doc.has("body") && doc.getAsJsonObject("body").has("content")) {
            JsonArray bodyContent = doc.getAsJsonObject("body").getAsJsonArray("content");
            extractTextFromContent(bodyContent, content);
        }

        return content.toString();
    }

    @Override
    public String generatePersonalizedDocument(OAuthUser oAuthUser, String templateId, Customer customer,
                                              Map<String, String> additionalMergeData, String targetFolderId,
                                              String documentName)
            throws IOException, GeneralSecurityException {

        logger.info("Generating personalized document for customer {} from template {}",
                   customer.getCustomerId(), templateId);

        // Build complete merge data (customer + additional)
        Map<String, String> mergeData = buildCustomerMergeData(customer);
        if (additionalMergeData != null) {
            mergeData.putAll(additionalMergeData);
        }

        // Add current date
        mergeData.put("current_date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        mergeData.put("current_date_long", LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")));

        // Copy template
        String copiedDocId = copyTemplateDocument(oAuthUser, templateId, targetFolderId, documentName + "_temp");

        try {
            // Replace merge fields
            replaceAllMergeFields(oAuthUser, copiedDocId, mergeData);

            // Convert to PDF
            String pdfFileId = convertDocumentToPdf(oAuthUser, copiedDocId, targetFolderId, documentName);

            // Delete temporary Google Doc
            deleteDocument(oAuthUser, copiedDocId);

            logger.info("Document generated successfully for customer {}. PDF ID: {}",
                       customer.getCustomerId(), pdfFileId);

            return pdfFileId;

        } catch (Exception e) {
            // Clean up temporary document on error
            try {
                deleteDocument(oAuthUser, copiedDocId);
            } catch (Exception cleanupError) {
                logger.error("Failed to cleanup temporary document: {}", copiedDocId, cleanupError);
            }
            throw e;
        }
    }

    @Override
    public Map<Long, String> generateDocumentsForMultipleCustomers(OAuthUser oAuthUser, String templateId,
                                                                  List<Customer> customers, String baseFolderId,
                                                                  String documentBaseName)
            throws IOException, GeneralSecurityException {

        logger.info("Generating documents for {} customers", customers.size());

        Map<Long, String> results = new HashMap<>();

        for (Customer customer : customers) {
            try {
                String documentName = documentBaseName + "_" + customer.getName().replaceAll("[^a-zA-Z0-9]", "_");
                String pdfFileId = generatePersonalizedDocument(
                    oAuthUser,
                    templateId,
                    customer,
                    null,
                    baseFolderId,
                    documentName
                );
                results.put(customer.getCustomerId(), pdfFileId);

                // Small delay to avoid rate limiting
                Thread.sleep(100);

            } catch (Exception e) {
                logger.error("Failed to generate document for customer {}", customer.getCustomerId(), e);
                // Continue with next customer
            }
        }

        logger.info("Generated {} documents out of {} customers", results.size(), customers.size());
        return results;
    }

    @Override
    public Map<String, String> buildCustomerMergeData(Customer customer) {
        Map<String, String> mergeData = new HashMap<>();

        // Customer - Individual Contact Fields
        mergeData.put("customer_first_name", nvl(customer.getFirstName()));
        mergeData.put("customer_last_name", nvl(customer.getLastName()));
        mergeData.put("customer_name", nvl(customer.getName()));
        mergeData.put("customer_email", nvl(customer.getEmail()));
        mergeData.put("customer_phone", nvl(customer.getPhone()));
        mergeData.put("customer_mobile", nvl(customer.getMobileNumber()));

        // Customer - Business Contact Fields
        mergeData.put("customer_business_name", nvl(customer.getBusinessName()));
        mergeData.put("customer_position", nvl(customer.getPosition()));
        mergeData.put("customer_vat_number", nvl(customer.getVatNumber()));
        mergeData.put("customer_company_registration", nvl(customer.getRegistrationNumber()));

        // Customer - Address Fields
        mergeData.put("customer_address", nvl(customer.getAddress()));
        mergeData.put("customer_city", nvl(customer.getCity()));
        mergeData.put("customer_state", nvl(customer.getState()));
        mergeData.put("customer_country", nvl(customer.getCountry()));
        mergeData.put("customer_postcode", nvl(customer.getPostcode()));

        // Customer type
        if (customer.getCustomerType() != null) {
            mergeData.put("customer_type", customer.getCustomerType().toString());
        } else {
            mergeData.put("customer_type", "");
        }

        // TODO: Add property-specific fields when customer has associated property
        // This would require loading the property relationship
        // For now, we'll add placeholders
        mergeData.put("property_address", "");
        mergeData.put("property_postcode", "");
        mergeData.put("property_monthly_rent", "");
        mergeData.put("block_name", "");
        mergeData.put("rent_amount", "");

        // Lease/Contract Fields
        mergeData.put("lease_start_date", "");
        mergeData.put("lease_end_date", "");

        // Property Viewing Fields
        // TODO: Load from property_viewings table when viewing context is available
        mergeData.put("viewing_date", "");
        mergeData.put("viewing_time", "");
        mergeData.put("viewing_type", "");
        mergeData.put("viewing_status", "");

        // Landlord Fields
        // TODO: Load from property owner when property context is available
        mergeData.put("landlord_name", "");
        mergeData.put("landlord_address", "");

        // Agent/User Fields
        // TODO: Populate with current user data - requires passing User object to this method
        mergeData.put("agent_first_name", "");
        mergeData.put("agent_last_name", "");
        mergeData.put("agent_name", "");
        mergeData.put("agent_email", "");
        mergeData.put("agent_phone", "");
        mergeData.put("agent_position", "");
        mergeData.put("agent_address", "");

        // Company/Agency Fields
        // TODO: Load from agency_settings table (may need to be created)
        mergeData.put("company_name", "");
        mergeData.put("company_address", "");

        // Date Fields
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter shortFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.format.DateTimeFormatter longFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy");
        mergeData.put("current_date", today.format(shortFormatter));
        mergeData.put("current_date_long", today.format(longFormatter));

        return mergeData;
    }

    @Override
    public void deleteDocument(OAuthUser oAuthUser, String documentId)
            throws IOException, GeneralSecurityException {

        logger.info("Deleting document {}", documentId);
        googleDriveApiService.deleteFile(oAuthUser, documentId);
    }

    @Override
    public boolean documentExists(OAuthUser oAuthUser, String documentId)
            throws IOException, GeneralSecurityException {

        return googleDriveApiService.isFileExists(oAuthUser, documentId);
    }

    // Helper methods

    private void extractTextFromContent(JsonArray content, StringBuilder sb) {
        for (JsonElement element : content) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("paragraph")) {
                JsonObject paragraph = obj.getAsJsonObject("paragraph");
                if (paragraph.has("elements")) {
                    JsonArray elements = paragraph.getAsJsonArray("elements");
                    for (JsonElement elem : elements) {
                        JsonObject textRun = elem.getAsJsonObject();
                        if (textRun.has("textRun")) {
                            String text = textRun.getAsJsonObject("textRun").get("content").getAsString();
                            sb.append(text);
                        }
                    }
                }
            }
        }
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}
