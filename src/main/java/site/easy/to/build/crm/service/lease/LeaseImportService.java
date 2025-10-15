package site.easy.to.build.crm.service.lease;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import site.easy.to.build.crm.entity.Invoice;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.repository.InvoiceRepository;
import site.easy.to.build.crm.repository.CustomerRepository;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.service.property.PropertyService;
import site.easy.to.build.crm.service.customer.CustomerService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lease Import Service - Handles bulk import of lease records from CSV
 *
 * This service enables importing lease agreements (Invoice entities) in bulk,
 * which must be done BEFORE importing historical transactions.
 *
 * CSV Format:
 * property_reference,customer_reference,lease_start_date,lease_end_date,rent_amount,payment_day,lease_reference
 *
 * Example:
 * FLAT 1 - 3 WEST GATE,MS O SMOLIARENKO,2024-04-27,,795.00,27,LEASE-FLAT1-2024
 */
@Service
@Transactional
public class LeaseImportService {

    private static final Logger log = LoggerFactory.getLogger(LeaseImportService.class);

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private CustomerService customerService;

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    /**
     * Import leases from CSV file
     */
    @Transactional
    public ImportResult importFromCsvFile(MultipartFile file, User createdByUser) {
        log.info("📋 Starting lease import from file: {}", file.getOriginalFilename());

        try {
            String csvContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            return importFromCsvString(csvContent, createdByUser);
        } catch (Exception e) {
            log.error("❌ Failed to read CSV file: {}", e.getMessage(), e);
            return ImportResult.failure("Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Import leases from CSV string (paste functionality)
     */
    @Transactional
    public ImportResult importFromCsvString(String csvData, User createdByUser) {
        log.info("📋 Starting lease import from CSV string ({} characters)", csvData.length());

        ImportResult result = new ImportResult();
        result.setStartTime(LocalDateTime.now());

        try {
            // Parse CSV
            List<LeaseRow> rows = parseCsvData(csvData);
            result.setTotalRows(rows.size());

            log.info("📊 Parsed {} lease rows from CSV", rows.size());

            // Validate and import each row
            for (int i = 0; i < rows.size(); i++) {
                LeaseRow row = rows.get(i);
                int lineNumber = i + 2; // +2 for header and 0-indexing

                try {
                    // Validate row
                    validateLeaseRow(row, lineNumber);

                    // Check for duplicate lease_reference
                    if (invoiceRepository.existsByLeaseReference(row.leaseReference)) {
                        result.addSkipped("Line " + lineNumber + ": Lease reference '" + row.leaseReference + "' already exists");
                        continue;
                    }

                    // Match property
                    Property property = matchProperty(row.propertyReference);
                    if (property == null) {
                        result.addError("Line " + lineNumber + ": Property '" + row.propertyReference + "' not found");
                        continue;
                    }

                    // Match customer
                    Customer customer = matchCustomer(row.customerReference);
                    if (customer == null) {
                        result.addError("Line " + lineNumber + ": Customer '" + row.customerReference + "' not found");
                        continue;
                    }

                    // Create lease (Invoice entity)
                    Invoice lease = createLease(row, property, customer, createdByUser);
                    invoiceRepository.save(lease);

                    result.incrementSuccessful();
                    log.info("✅ Line {}: Created lease {} for {} at {}",
                            lineNumber, row.leaseReference, customer.getName(), property.getPropertyName());

                } catch (Exception e) {
                    result.addError("Line " + lineNumber + ": " + e.getMessage());
                    log.error("❌ Line {}: Failed to import lease - {}", lineNumber, e.getMessage());
                }
            }

            result.setEndTime(LocalDateTime.now());
            result.setSuccess(result.getFailedImports() == 0);

            log.info("✅ Lease import complete: {} successful, {} failed, {} skipped",
                    result.getSuccessfulImports(), result.getFailedImports(), result.getSkippedRows());

            return result;

        } catch (Exception e) {
            log.error("❌ Lease import failed: {}", e.getMessage(), e);
            result.setEndTime(LocalDateTime.now());
            result.setSuccess(false);
            result.setErrorMessage("Import failed: " + e.getMessage());
            return result;
        }
    }

    /**
     * Parse CSV data into LeaseRow objects
     */
    private List<LeaseRow> parseCsvData(String csvData) throws Exception {
        List<LeaseRow> rows = new ArrayList<>();
        String[] lines = csvData.split("\n");

        if (lines.length < 2) {
            throw new IllegalArgumentException("CSV must have at least a header row and one data row");
        }

        // Parse header
        String headerLine = lines[0].trim();
        String[] headers = parseCsvLine(headerLine);
        Map<String, Integer> columnMap = buildColumnMap(headers);

        // Validate required columns
        validateRequiredColumns(columnMap);

        // Parse data rows
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] values = parseCsvLine(line);
            if (values.length < headers.length) {
                log.warn("⚠️ Line {} has fewer columns than header, skipping", i + 1);
                continue;
            }

            LeaseRow row = parseLeaseRow(values, columnMap);
            rows.add(row);
        }

        return rows;
    }

    /**
     * Parse a single CSV line handling quoted fields
     */
    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());

        return values.toArray(new String[0]);
    }

    /**
     * Build column map from headers
     */
    private Map<String, Integer> buildColumnMap(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].toLowerCase().trim(), i);
        }
        return map;
    }

    /**
     * Validate required columns are present
     */
    private void validateRequiredColumns(Map<String, Integer> columnMap) throws IllegalArgumentException {
        String[] required = {
            "property_reference", "customer_reference", "lease_start_date",
            "rent_amount", "payment_day", "lease_reference"
        };

        for (String col : required) {
            if (!columnMap.containsKey(col)) {
                throw new IllegalArgumentException("Missing required column: " + col);
            }
        }
    }

    /**
     * Parse a row into LeaseRow object
     */
    private LeaseRow parseLeaseRow(String[] values, Map<String, Integer> columnMap) {
        LeaseRow row = new LeaseRow();
        row.propertyReference = getValueFromMap(values, columnMap, "property_reference");
        row.customerReference = getValueFromMap(values, columnMap, "customer_reference");
        row.leaseStartDateStr = getValueFromMap(values, columnMap, "lease_start_date");
        row.leaseEndDateStr = getValueFromMap(values, columnMap, "lease_end_date");
        row.rentAmountStr = getValueFromMap(values, columnMap, "rent_amount");
        row.paymentDayStr = getValueFromMap(values, columnMap, "payment_day");
        row.leaseReference = getValueFromMap(values, columnMap, "lease_reference");
        return row;
    }

    private String getValueFromMap(String[] values, Map<String, Integer> columnMap, String column) {
        Integer index = columnMap.get(column);
        if (index == null || index >= values.length) return null;
        String value = values[index].trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Validate a lease row
     */
    private void validateLeaseRow(LeaseRow row, int lineNumber) {
        if (row.propertyReference == null || row.propertyReference.isEmpty()) {
            throw new IllegalArgumentException("property_reference is required");
        }
        if (row.customerReference == null || row.customerReference.isEmpty()) {
            throw new IllegalArgumentException("customer_reference is required");
        }
        if (row.leaseStartDateStr == null || row.leaseStartDateStr.isEmpty()) {
            throw new IllegalArgumentException("lease_start_date is required");
        }
        if (row.rentAmountStr == null || row.rentAmountStr.isEmpty()) {
            throw new IllegalArgumentException("rent_amount is required");
        }
        if (row.paymentDayStr == null || row.paymentDayStr.isEmpty()) {
            throw new IllegalArgumentException("payment_day is required");
        }
        if (row.leaseReference == null || row.leaseReference.isEmpty()) {
            throw new IllegalArgumentException("lease_reference is required");
        }

        // Validate date format
        LocalDate leaseStartDate = parseDate(row.leaseStartDateStr);
        if (leaseStartDate == null) {
            throw new IllegalArgumentException("Invalid lease_start_date format: " + row.leaseStartDateStr);
        }

        if (row.leaseEndDateStr != null && !row.leaseEndDateStr.isEmpty()) {
            LocalDate leaseEndDate = parseDate(row.leaseEndDateStr);
            if (leaseEndDate == null) {
                throw new IllegalArgumentException("Invalid lease_end_date format: " + row.leaseEndDateStr);
            }
            if (leaseEndDate.isBefore(leaseStartDate)) {
                throw new IllegalArgumentException("lease_end_date cannot be before lease_start_date");
            }
        }

        // Validate rent amount
        try {
            BigDecimal rentAmount = new BigDecimal(row.rentAmountStr);
            if (rentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("rent_amount must be positive");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid rent_amount: " + row.rentAmountStr);
        }

        // Validate payment day
        try {
            int paymentDay = Integer.parseInt(row.paymentDayStr);
            if (paymentDay < 1 || paymentDay > 31) {
                throw new IllegalArgumentException("payment_day must be between 1 and 31");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid payment_day: " + row.paymentDayStr);
        }
    }

    /**
     * Parse date from string with multiple format support
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }

        return null;
    }

    /**
     * Match property by reference (name or ID)
     */
    private Property matchProperty(String reference) {
        // Try exact match by name (case insensitive)
        Property property = propertyRepository.findByPropertyNameIgnoreCase(reference);
        if (property != null) return property;

        // Try fuzzy match
        List<Property> properties = propertyRepository.findAll();
        for (Property p : properties) {
            if (p.getPropertyName() != null &&
                p.getPropertyName().toLowerCase().contains(reference.toLowerCase())) {
                return p;
            }
        }

        return null;
    }

    /**
     * Match customer by reference (name or ID)
     */
    private Customer matchCustomer(String reference) {
        // Try exact match by name (case insensitive)
        List<Customer> exactMatches = customerRepository.findByNameContainingIgnoreCase(reference);
        if (!exactMatches.isEmpty()) {
            // Return exact match if found (first result)
            for (Customer c : exactMatches) {
                if (c.getName() != null && c.getName().equalsIgnoreCase(reference)) {
                    return c;
                }
            }
        }

        // Try fuzzy match
        List<Customer> customers = customerRepository.findAll();
        for (Customer c : customers) {
            if (c.getName() != null &&
                c.getName().toLowerCase().contains(reference.toLowerCase())) {
                return c;
            }
        }

        return null;
    }

    /**
     * Create Invoice (lease) entity from row
     */
    private Invoice createLease(LeaseRow row, Property property, Customer customer, User createdByUser) {
        Invoice lease = new Invoice();

        lease.setProperty(property);
        lease.setCustomer(customer);
        lease.setCreatedByUser(createdByUser);

        // Lease period
        lease.setStartDate(parseDate(row.leaseStartDateStr));
        if (row.leaseEndDateStr != null && !row.leaseEndDateStr.isEmpty()) {
            lease.setEndDate(parseDate(row.leaseEndDateStr));
        }

        // Financial details
        lease.setAmount(new BigDecimal(row.rentAmountStr));
        lease.setFrequency(Invoice.InvoiceFrequency.M); // Monthly
        lease.setPaymentDay(Integer.parseInt(row.paymentDayStr));

        // Lease reference
        lease.setLeaseReference(row.leaseReference);

        // Category
        lease.setCategoryId("rent");
        lease.setCategoryName("Rent");

        // Description
        lease.setDescription(String.format("Lease for %s - %s (£%s/month)",
                customer.getName(), property.getPropertyName(), row.rentAmountStr));

        // Status
        lease.setIsActive(true);
        lease.setSyncStatus(Invoice.SyncStatus.manual); // Don't sync historical leases to PayProp
        lease.setInvoiceType("lease");

        lease.setCreatedAt(LocalDateTime.now());

        return lease;
    }

    // ===== DATA CLASSES =====

    private static class LeaseRow {
        String propertyReference;
        String customerReference;
        String leaseStartDateStr;
        String leaseEndDateStr;
        String rentAmountStr;
        String paymentDayStr;
        String leaseReference;
    }

    public static class ImportResult {
        private boolean success;
        private int totalRows;
        private int successfulImports;
        private int failedImports;
        private int skippedRows;
        private List<String> errors = new ArrayList<>();
        private List<String> skipped = new ArrayList<>();
        private String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;

        public static ImportResult failure(String errorMessage) {
            ImportResult result = new ImportResult();
            result.success = false;
            result.errorMessage = errorMessage;
            result.startTime = LocalDateTime.now();
            result.endTime = LocalDateTime.now();
            return result;
        }

        public void incrementSuccessful() {
            this.successfulImports++;
        }

        public void addError(String error) {
            this.errors.add(error);
            this.failedImports++;
        }

        public void addSkipped(String reason) {
            this.skipped.add(reason);
            this.skippedRows++;
        }

        public String getSummary() {
            return String.format("Imported %d/%d leases (%d failed, %d skipped)",
                    successfulImports, totalRows, failedImports, skippedRows);
        }

        public long getDurationSeconds() {
            if (startTime == null || endTime == null) return 0;
            return java.time.Duration.between(startTime, endTime).getSeconds();
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public int getTotalRows() { return totalRows; }
        public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

        public int getSuccessfulImports() { return successfulImports; }
        public int getFailedImports() { return failedImports; }
        public int getSkippedRows() { return skippedRows; }

        public List<String> getErrors() { return errors; }
        public List<String> getSkipped() { return skipped; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    }
}
