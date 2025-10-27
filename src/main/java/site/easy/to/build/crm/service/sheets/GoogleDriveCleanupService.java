package site.easy.to.build.crm.service.sheets;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for cleaning up old Google Sheets statements from service account's Drive
 */
@Service
public class GoogleDriveCleanupService {

    @Value("${google.service-account-key}")
    private String serviceAccountKey;

    /**
     * Get formatted service account key with proper newlines
     */
    private String getFormattedServiceAccountKey() {
        return serviceAccountKey.replace("\\n", "\n");
    }

    /**
     * Create Google Drive service
     */
    private Drive createDriveService() throws IOException, GeneralSecurityException {
        String formattedKey = getFormattedServiceAccountKey();
        GoogleCredential credential = GoogleCredential
            .fromStream(new ByteArrayInputStream(formattedKey.getBytes()))
            .createScoped(Collections.singleton(DriveScopes.DRIVE));

        return new Drive.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential)
            .setApplicationName("CRM Property Management")
            .build();
    }

    /**
     * List all spreadsheets owned by service account
     */
    public List<DriveFileInfo> listAllSpreadsheets() throws IOException, GeneralSecurityException {
        Drive driveService = createDriveService();
        List<DriveFileInfo> allFiles = new ArrayList<>();

        String pageToken = null;
        do {
            FileList result = driveService.files().list()
                .setQ("mimeType='application/vnd.google-apps.spreadsheet' and trashed=false")
                .setPageSize(100)
                .setFields("nextPageToken, files(id, name, createdTime, size, parents)")
                .setPageToken(pageToken)
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .execute();

            List<File> files = result.getFiles();
            if (files != null && !files.isEmpty()) {
                for (File file : files) {
                    DriveFileInfo info = new DriveFileInfo();
                    info.setId(file.getId());
                    info.setName(file.getName());
                    info.setCreatedTime(file.getCreatedTime() != null ? file.getCreatedTime().toString() : "Unknown");
                    info.setSize(file.getSize() != null ? file.getSize() : 0L);
                    info.setParents(file.getParents());
                    allFiles.add(info);
                }
            }

            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        System.out.println("📊 Found " + allFiles.size() + " spreadsheets in service account Drive");
        return allFiles;
    }

    /**
     * Get total storage used by all spreadsheets
     */
    public StorageInfo getStorageInfo() throws IOException, GeneralSecurityException {
        Drive driveService = createDriveService();

        // Get about information (includes storage quota)
        com.google.api.services.drive.model.About about = driveService.about()
            .get()
            .setFields("storageQuota")
            .execute();

        StorageInfo info = new StorageInfo();
        if (about.getStorageQuota() != null) {
            info.setLimit(about.getStorageQuota().getLimit());
            info.setUsage(about.getStorageQuota().getUsage());
            info.setUsageInDrive(about.getStorageQuota().getUsageInDrive());
        }

        // Count spreadsheets
        List<DriveFileInfo> spreadsheets = listAllSpreadsheets();
        info.setSpreadsheetCount(spreadsheets.size());

        long totalSpreadsheetSize = spreadsheets.stream()
            .mapToLong(DriveFileInfo::getSize)
            .sum();
        info.setSpreadsheetSize(totalSpreadsheetSize);

        return info;
    }

    /**
     * Delete spreadsheets older than the specified number of days
     * @param daysToKeep Keep spreadsheets created within this many days
     * @return Number of files deleted
     */
    public int deleteOldSpreadsheets(int daysToKeep) throws IOException, GeneralSecurityException {
        Drive driveService = createDriveService();
        List<DriveFileInfo> allFiles = listAllSpreadsheets();

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        System.out.println("🗑️ Deleting spreadsheets created before: " + cutoffDate);

        int deletedCount = 0;
        long freedSpace = 0;

        for (DriveFileInfo file : allFiles) {
            try {
                // Parse created time
                LocalDateTime createdTime = LocalDateTime.parse(
                    file.getCreatedTime().substring(0, 19),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
                );

                if (createdTime.isBefore(cutoffDate)) {
                    System.out.println("🗑️ Deleting: " + file.getName() + " (created: " + file.getCreatedTime() + ")");

                    driveService.files().delete(file.getId())
                        .setSupportsAllDrives(true)
                        .execute();

                    deletedCount++;
                    freedSpace += file.getSize();
                }
            } catch (Exception e) {
                System.err.println("❌ Failed to delete " + file.getName() + ": " + e.getMessage());
            }
        }

        System.out.println("✅ Deleted " + deletedCount + " old spreadsheets");
        System.out.println("💾 Freed approximately " + (freedSpace / 1024 / 1024) + " MB");

        return deletedCount;
    }

    /**
     * Delete specific spreadsheet by ID
     */
    public void deleteSpreadsheet(String spreadsheetId) throws IOException, GeneralSecurityException {
        Drive driveService = createDriveService();

        driveService.files().delete(spreadsheetId)
            .setSupportsAllDrives(true)
            .execute();

        System.out.println("✅ Deleted spreadsheet: " + spreadsheetId);
    }

    /**
     * Delete ALL spreadsheets (use with caution!)
     */
    public int deleteAllSpreadsheets() throws IOException, GeneralSecurityException {
        Drive driveService = createDriveService();
        List<DriveFileInfo> allFiles = listAllSpreadsheets();

        System.out.println("⚠️ WARNING: Deleting ALL " + allFiles.size() + " spreadsheets!");

        int deletedCount = 0;
        for (DriveFileInfo file : allFiles) {
            try {
                driveService.files().delete(file.getId())
                    .setSupportsAllDrives(true)
                    .execute();
                deletedCount++;

                if (deletedCount % 10 == 0) {
                    System.out.println("🗑️ Deleted " + deletedCount + "/" + allFiles.size() + " files...");
                }
            } catch (Exception e) {
                System.err.println("❌ Failed to delete " + file.getName() + ": " + e.getMessage());
            }
        }

        System.out.println("✅ Deleted " + deletedCount + " spreadsheets");
        return deletedCount;
    }

    // Inner classes for data transfer
    public static class DriveFileInfo {
        private String id;
        private String name;
        private String createdTime;
        private Long size;
        private List<String> parents;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCreatedTime() { return createdTime; }
        public void setCreatedTime(String createdTime) { this.createdTime = createdTime; }

        public Long getSize() { return size; }
        public void setSize(Long size) { this.size = size; }

        public List<String> getParents() { return parents; }
        public void setParents(List<String> parents) { this.parents = parents; }
    }

    public static class StorageInfo {
        private Long limit;
        private Long usage;
        private Long usageInDrive;
        private Integer spreadsheetCount;
        private Long spreadsheetSize;

        public Long getLimit() { return limit; }
        public void setLimit(Long limit) { this.limit = limit; }

        public Long getUsage() { return usage; }
        public void setUsage(Long usage) { this.usage = usage; }

        public Long getUsageInDrive() { return usageInDrive; }
        public void setUsageInDrive(Long usageInDrive) { this.usageInDrive = usageInDrive; }

        public Integer getSpreadsheetCount() { return spreadsheetCount; }
        public void setSpreadsheetCount(Integer count) { this.spreadsheetCount = count; }

        public Long getSpreadsheetSize() { return spreadsheetSize; }
        public void setSpreadsheetSize(Long size) { this.spreadsheetSize = size; }

        public String getUsageFormatted() {
            if (usage == null) return "Unknown";
            return (usage / 1024 / 1024) + " MB / " + (limit / 1024 / 1024) + " MB";
        }

        public double getUsagePercent() {
            if (usage == null || limit == null || limit == 0) return 0;
            return (usage.doubleValue() / limit.doubleValue()) * 100;
        }
    }
}
