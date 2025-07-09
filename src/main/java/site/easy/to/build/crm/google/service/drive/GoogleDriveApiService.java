package site.easy.to.build.crm.google.service.drive;

import site.easy.to.build.crm.entity.File;
import site.easy.to.build.crm.entity.OAuthUser;
import site.easy.to.build.crm.google.model.drive.GoogleDriveFile;
import site.easy.to.build.crm.google.model.drive.GoogleDriveFolder;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

public interface GoogleDriveApiService {
    public List<GoogleDriveFile> listFiles(OAuthUser oAuthUser) throws IOException, GeneralSecurityException;
    public List<GoogleDriveFile> listFilesInFolder(OAuthUser oAuthUser, String folderId) throws IOException, GeneralSecurityException;
    public void createWorkspaceFile(OAuthUser oAuthUser, String name, String type) throws IOException, GeneralSecurityException;
    public List<String> uploadWorkspaceFile(OAuthUser oAuthUser, List<File> files, String folderId) throws IOException, GeneralSecurityException;

    public String createFolder(OAuthUser oAuthUser, String folderName) throws IOException, GeneralSecurityException;

    void checkFolderExists(OAuthUser oAuthUser, String folderId) throws IOException, GeneralSecurityException;

    public void createFileInFolder(OAuthUser oAuthUser, String fileName, String folderId, String type) throws IOException, GeneralSecurityException;

    public List<GoogleDriveFolder> listFolders(OAuthUser oAuthUser) throws IOException, GeneralSecurityException;

    public void shareFileWithUser(OAuthUser oAuthUser, String fileId, String email, String role) throws IOException, GeneralSecurityException;

    public void findOrCreateTemplateFolder(OAuthUser oAuthUser, String folderName) throws IOException, GeneralSecurityException;

    public void deleteFile(OAuthUser oAuthUser, String fileId) throws IOException, GeneralSecurityException;
    public boolean isFileExists(OAuthUser oAuthUser, String fileId) throws IOException, GeneralSecurityException;

    // ===== NEW METHODS FOR PAYPROP FILE SYNC =====
    
    /**
     * Create a folder within a parent folder
     */
    public String createFolderInParent(OAuthUser oAuthUser, String folderName, String parentFolderId) throws IOException, GeneralSecurityException;
    
    /**
     * Move a file to a specific folder
     */
    public void moveFileToFolder(OAuthUser oAuthUser, String fileId, String folderId) throws IOException, GeneralSecurityException;
    
    /**
     * Upload a file with byte data to a specific parent folder
     */
    public String uploadFile(OAuthUser oAuthUser, String fileName, byte[] fileData, String parentFolderId) throws IOException, GeneralSecurityException;
    
    /**
     * Upload a file with MIME type to a specific parent folder
     */
    public String uploadFileWithMimeType(OAuthUser oAuthUser, String fileName, byte[] fileData, String mimeType, String parentFolderId) throws IOException, GeneralSecurityException;
    
    /**
     * Find folder by name in parent folder
     */
    public String findFolderByName(OAuthUser oAuthUser, String folderName, String parentFolderId) throws IOException, GeneralSecurityException;
    
    /**
     * Find or create folder in parent folder
     */
    public String findOrCreateFolderInParent(OAuthUser oAuthUser, String folderName, String parentFolderId) throws IOException, GeneralSecurityException;
}