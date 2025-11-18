package site.easy.to.build.crm.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import site.easy.to.build.crm.service.drive.SharedDriveFileService;

import java.util.List;
import java.util.Map;

//@Component  // Uncomment to run
public class TestPropertyFolderCreationRunner implements CommandLineRunner {

    @Autowired
    private SharedDriveFileService sharedDriveFileService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n==============================================");
        System.out.println("🔧 TESTING PROPERTY FOLDER CREATION");
        System.out.println("==============================================\n");

        Long testPropertyId = 3L; // Flat 3

        System.out.println("📂 Testing property subfolder creation for Property ID: " + testPropertyId);
        System.out.println();

        try {
            // This should create EICR, EPC, Insurance, Miscellaneous folders in Google Drive
            List<Map<String, Object>> subfolders = sharedDriveFileService.listPropertySubfolders(testPropertyId);

            System.out.println("✅ SUCCESS! Property subfolders retrieved/created:");
            System.out.println("   Total subfolders: " + subfolders.size());
            System.out.println();

            for (Map<String, Object> folder : subfolders) {
                System.out.println("   📁 " + folder.get("name") + " (ID: " + folder.get("folderId") + ")");
                System.out.println("      Description: " + folder.get("description"));
            }

            System.out.println();
            System.out.println("✅ If the folders were created, they should now appear in Google Drive");
            System.out.println("   under: Property Documents → Property 3 - [address]");
            System.out.println();
            System.out.println("🔍 Please check your Google Drive Shared Drive to verify the folders exist.");

        } catch (Exception e) {
            System.err.println("❌ ERROR creating/retrieving property subfolders:");
            System.err.println("   " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n==============================================");
        System.out.println("✅ Test complete!");
        System.out.println("==============================================\n");

        System.exit(0);
    }
}
