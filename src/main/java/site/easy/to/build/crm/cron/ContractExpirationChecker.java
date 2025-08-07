package site.easy.to.build.crm.cron;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.easy.to.build.crm.entity.Contract;
import site.easy.to.build.crm.service.contract.ContractService;

import java.time.LocalDate;
import java.util.List;

@Component
public class ContractExpirationChecker {

    private final ContractService contractService;

    @Autowired
    public ContractExpirationChecker(ContractService contractService) {
        this.contractService = contractService;
    }

    @Scheduled(cron = "0 0 0 * * *") // Daily at midnight
    public void scheduleContractExpirationCheck() { // ✅ No parameters
        System.out.println("🔍 Starting daily contract expiration check...");
        
        LocalDate currentDate = LocalDate.now();
        List<Contract> allContracts = contractService.findAll();
        
        int checkedCount = 0;
        int expiredCount = 0;
        int validCount = 0;
        
        for (Contract contract : allContracts) {
            // Only check active contracts
            if (!"active".equals(contract.getStatus())) {
                continue;
            }
            
            checkedCount++;
            LocalDate endDate = LocalDate.parse(contract.getEndDate());
            
            if (currentDate.isAfter(endDate)) {
                // Contract has expired - update status
                contract.setStatus("expired");
                contractService.save(contract);
                expiredCount++;
                
                System.out.println("❌ Contract expired: " + contract.getContractId() + 
                    " (Subject: " + contract.getSubject() + ", End Date: " + endDate + ")");
            } else {
                validCount++;
                System.out.println("✅ Contract still valid: " + contract.getContractId() + 
                    " (Expires: " + endDate + ")");
            }
        }
        
        System.out.println("📊 Contract expiration check completed:");
        System.out.println("   Total active contracts checked: " + checkedCount);
        System.out.println("   Contracts expired: " + expiredCount);
        System.out.println("   Contracts still valid: " + validCount);
    }
}