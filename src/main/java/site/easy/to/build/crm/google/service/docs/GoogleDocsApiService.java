package site.easy.to.build.crm.google.service.docs;

import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.OAuthUser;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with Google Docs API
 * Handles document template operations, merge fields, and PDF conversion
 */
public interface GoogleDocsApiService {

    /**
     * Copy a Google Docs template to a target folder
     *
     * @param oAuthUser The authenticated user
     * @param templateId The Google Docs template file ID
     * @param targetFolderId The folder ID where the copy should be created
     * @param newName The name for the copied document
     * @return The file ID of the copied document
     */
    String copyTemplateDocument(OAuthUser oAuthUser, String templateId, String targetFolderId, String newName)
            throws IOException, GeneralSecurityException;

    /**
     * Replace all merge fields in a document with actual values
     * Merge fields use the format: {{field_name}}
     *
     * @param oAuthUser The authenticated user
     * @param documentId The Google Docs document ID
     * @param mergeData Map of field names to replacement values
     */
    void replaceAllMergeFields(OAuthUser oAuthUser, String documentId, Map<String, String> mergeData)
            throws IOException, GeneralSecurityException;

    /**
     * Convert a Google Docs document to PDF and save to target folder
     *
     * @param oAuthUser The authenticated user
     * @param documentId The Google Docs document ID to convert
     * @param targetFolderId The folder where PDF should be saved
     * @param pdfName The name for the PDF file
     * @return The file ID of the created PDF
     */
    String convertDocumentToPdf(OAuthUser oAuthUser, String documentId, String targetFolderId, String pdfName)
            throws IOException, GeneralSecurityException;

    /**
     * Download a PDF file as byte array (for email attachment)
     *
     * @param oAuthUser The authenticated user
     * @param pdfFileId The Google Drive file ID of the PDF
     * @return Byte array of the PDF content
     */
    byte[] downloadPdfAsBytes(OAuthUser oAuthUser, String pdfFileId)
            throws IOException, GeneralSecurityException;

    /**
     * Get the text content of a document (for preview)
     *
     * @param oAuthUser The authenticated user
     * @param documentId The Google Docs document ID
     * @return The document text content
     */
    String getDocumentContent(OAuthUser oAuthUser, String documentId)
            throws IOException, GeneralSecurityException;

    /**
     * Complete workflow: Copy template, merge fields, convert to PDF
     * This is the main method used for generating personalized documents
     *
     * @param oAuthUser The authenticated user
     * @param templateId The template document ID
     * @param customer The customer for whom to generate the document
     * @param mergeData Additional merge fields beyond customer data
     * @param targetFolderId Where to save the PDF
     * @param documentName Base name for the document
     * @return The file ID of the generated PDF
     */
    String generatePersonalizedDocument(OAuthUser oAuthUser, String templateId, Customer customer,
                                       Map<String, String> mergeData, String targetFolderId, String documentName)
            throws IOException, GeneralSecurityException;

    /**
     * Batch generate documents for multiple customers
     * Useful for bulk operations like sending Section 48 to all tenants
     *
     * @param oAuthUser The authenticated user
     * @param templateId The template document ID
     * @param customers List of customers
     * @param baseFolderId Base folder for document storage
     * @param documentBaseName Base name for documents
     * @return Map of customer ID to generated PDF file ID
     */
    Map<Long, String> generateDocumentsForMultipleCustomers(OAuthUser oAuthUser, String templateId,
                                                            List<Customer> customers, String baseFolderId,
                                                            String documentBaseName)
            throws IOException, GeneralSecurityException;

    /**
     * Build merge data map from customer object
     * Extracts all customer fields into merge field format
     *
     * @param customer The customer object
     * @return Map of merge field names to values
     */
    Map<String, String> buildCustomerMergeData(Customer customer);

    /**
     * Build merge data map from customer and user objects
     * Extracts customer fields and agent/user fields into merge field format
     *
     * @param customer The customer object
     * @param currentUser The current user (for agent fields)
     * @return Map of merge field names to values
     */
    Map<String, String> buildCustomerMergeData(Customer customer, site.easy.to.build.crm.entity.User currentUser);

    /**
     * Delete a document (cleanup after PDF generation)
     *
     * @param oAuthUser The authenticated user
     * @param documentId The document ID to delete
     */
    void deleteDocument(OAuthUser oAuthUser, String documentId)
            throws IOException, GeneralSecurityException;

    /**
     * Check if a document exists and is accessible
     *
     * @param oAuthUser The authenticated user
     * @param documentId The document ID to check
     * @return true if document exists and is accessible
     */
    boolean documentExists(OAuthUser oAuthUser, String documentId)
            throws IOException, GeneralSecurityException;
}
