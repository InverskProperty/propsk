package site.easy.to.build.crm.service.payprop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.Customer;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.CustomerType;
import site.easy.to.build.crm.entity.DataSource;
import site.easy.to.build.crm.service.customer.CustomerService;
import site.easy.to.build.crm.service.property.PropertyService;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Service to import local financial data and make it work identically to PayProp data
 * This creates the same relationship structure as PayProp sync
 */
@Service
public class LocalPayPropDataService {

    private static final Logger logger = Logger.getLogger(LocalPayPropDataService.class.getName());

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private PropertyService propertyService;

    /**
     * Create local PayProp payments table to store financial data in PayProp format
     */
    public void createLocalPayPropTable() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS local_payprop_payments (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                property_reference VARCHAR(255),
                category VARCHAR(255),
                amount DECIMAL(19,2),
                payment_date DATE,
                description TEXT,
                payer_email VARCHAR(255),
                payer_first_name VARCHAR(255),
                payer_last_name VARCHAR(255),
                relationship_type VARCHAR(50),
                data_source VARCHAR(50) DEFAULT 'UPLOADED',
                upload_batch_id VARCHAR(255),
                created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                processed BOOLEAN DEFAULT FALSE,
                UNIQUE KEY unique_payment (property_reference, amount, payment_date, payer_email)
            )
        """;

        jdbcTemplate.execute(createTableSql);
        logger.info("Created local_payprop_payments table");
    }

    /**
     * Export financial data to CSV format compatible with PayProp structure
     */
    public void exportFinancialDataToCsv(String outputPath) throws IOException {
        String sql = """
            SELECT
                'Payment' as type,
                COALESCE(p.property_reference, CONCAT('PROP_', p.property_id)) as property_reference,
                CASE
                    WHEN ft.transaction_type = 'payment_to_landlord' THEN 'Rent Payment'
                    WHEN ft.transaction_type = 'tenant_payment' THEN 'Tenant Payment'
                    WHEN ft.transaction_type = 'maintenance_cost' THEN 'Maintenance'
                    WHEN ft.transaction_type = 'insurance' THEN 'Insurance'
                    WHEN ft.transaction_type = 'council_tax' THEN 'Council Tax'
                    WHEN ft.transaction_type = 'agency_fee' THEN 'Management Fee'
                    WHEN ft.transaction_type = 'payment_to_agency' THEN 'Agency Payment'
                    WHEN ft.transaction_type = 'invoice' THEN 'Invoice Payment'
                    ELSE ft.transaction_type
                END as category,
                ft.amount,
                ft.transaction_date as payment_date,
                ft.description,
                COALESCE(c.email, 'unknown@example.com') as payer_email,
                COALESCE(c.first_name, '') as payer_first_name,
                COALESCE(c.last_name, '') as payer_last_name,
                CASE
                    WHEN ft.transaction_type IN ('payment_to_landlord', 'agency_fee') THEN 'OWNER'
                    WHEN ft.transaction_type IN ('tenant_payment') THEN 'TENANT'
                    ELSE 'OTHER'
                END as relationship_type
            FROM financial_transactions ft
            LEFT JOIN properties p ON ft.property_id = p.property_id
            LEFT JOIN customers c ON ft.customer_id = c.customer_id
            WHERE ft.amount > 0
            AND ft.transaction_date IS NOT NULL
            ORDER BY ft.transaction_date DESC
        """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        try (var writer = new java.io.FileWriter(outputPath)) {
            // Write CSV header
            writer.write("type,property_reference,category,amount,payment_date,description,payer_email,payer_first_name,payer_last_name,relationship_type\n");

            // Write data rows
            for (Map<String, Object> row : results) {
                writer.write(String.format("%s,%s,%s,%s,%s,\"%s\",%s,%s,%s,%s\n",
                    row.get("type"),
                    row.get("property_reference"),
                    row.get("category"),
                    row.get("amount"),
                    row.get("payment_date"),
                    row.get("description").toString().replace("\"", "\"\""), // Escape quotes
                    row.get("payer_email"),
                    row.get("payer_first_name"),
                    row.get("payer_last_name"),
                    row.get("relationship_type")
                ));
            }
        }

        logger.info("Exported " + results.size() + " financial records to " + outputPath);
    }

    /**
     * Import CSV data into local PayProp format and create relationships
     * This works identically to the PayProp sync process
     */
    @Transactional
    public void importLocalPayPropData(String csvPath) throws IOException {
        createLocalPayPropTable();

        String batchId = "BATCH_" + System.currentTimeMillis();
        int importedCount = 0;
        int relationshipsCreated = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line = reader.readLine(); // Skip header

            while ((line = reader.readLine()) != null) {
                String[] fields = parseCsvLine(line);
                if (fields.length >= 10) {
                    // Insert into local PayProp table
                    String insertSql = """
                        INSERT IGNORE INTO local_payprop_payments
                        (property_reference, category, amount, payment_date, description,
                         payer_email, payer_first_name, payer_last_name, relationship_type, upload_batch_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

                    jdbcTemplate.update(insertSql,
                        fields[1], // property_reference
                        fields[2], // category
                        new BigDecimal(fields[3]), // amount
                        LocalDate.parse(fields[4]), // payment_date
                        fields[5], // description
                        fields[6], // payer_email
                        fields[7], // payer_first_name
                        fields[8], // payer_last_name
                        fields[9], // relationship_type
                        batchId
                    );

                    importedCount++;
                }
            }
        }

        // Create relationships based on imported data (same logic as PayProp sync)
        relationshipsCreated = createRelationshipsFromLocalData(batchId);

        logger.info("Imported " + importedCount + " payments and created " + relationshipsCreated + " relationships");
    }

    /**
     * Create customer-property relationships from local PayProp data
     * Uses the same logic as PayProp sync to ensure identical behavior
     */
    private int createRelationshipsFromLocalData(String batchId) {
        // Get distinct property-customer relationships from payments
        String sql = """
            SELECT DISTINCT
                lpp.property_reference,
                lpp.payer_email,
                lpp.payer_first_name,
                lpp.payer_last_name,
                lpp.relationship_type
            FROM local_payprop_payments lpp
            WHERE lpp.upload_batch_id = ?
            AND lpp.relationship_type IN ('OWNER', 'TENANT')
            AND lpp.payer_email IS NOT NULL
            AND lpp.property_reference IS NOT NULL
        """;

        List<Map<String, Object>> relationships = jdbcTemplate.queryForList(sql, batchId);
        int created = 0;

        for (Map<String, Object> rel : relationships) {
            String propertyRef = (String) rel.get("property_reference");
            String email = (String) rel.get("payer_email");
            String firstName = (String) rel.get("payer_first_name");
            String lastName = (String) rel.get("payer_last_name");
            String relType = (String) rel.get("relationship_type");

            // Find or create property
            Property property = findOrCreateProperty(propertyRef);

            // Find or create customer
            Customer customer = findOrCreateCustomer(email, firstName, lastName, relType);

            // Create assignment (same as PayProp sync)
            if (property != null && customer != null) {
                createCustomerPropertyAssignment(customer.getCustomerId(), property.getPropertyId(), relType);
                created++;
            }
        }

        return created;
    }

    private Property findOrCreateProperty(String propertyReference) {
        // Try to find existing property by reference
        List<Property> properties = jdbcTemplate.query(
            "SELECT * FROM properties WHERE property_reference = ?",
            (rs, rowNum) -> {
                Property p = new Property();
                p.setPropertyId(rs.getLong("property_id"));
                p.setPropertyReference(rs.getString("property_reference"));
                p.setDataSource(DataSource.UPLOADED);
                return p;
            },
            propertyReference
        );

        if (!properties.isEmpty()) {
            return properties.get(0);
        }

        // Create new property if not found
        Property newProperty = new Property();
        newProperty.setPropertyReference(propertyReference);
        newProperty.setDataSource(DataSource.UPLOADED);
        newProperty.setExternalReference(propertyReference);

        return propertyService.save(newProperty);
    }

    private Customer findOrCreateCustomer(String email, String firstName, String lastName, String relationshipType) {
        Customer existing = customerService.findByEmail(email);
        if (existing != null) {
            return existing;
        }

        // Create new customer
        Customer newCustomer = new Customer();
        newCustomer.setEmail(email);
        newCustomer.setFirstName(firstName);
        newCustomer.setLastName(lastName);
        newCustomer.setDataSource(DataSource.UPLOADED);
        newCustomer.setExternalReference(email);

        // Set customer type based on relationship
        if ("OWNER".equals(relationshipType)) {
            newCustomer.setCustomerType(CustomerType.PROPERTY_OWNER);
            newCustomer.setIsPropertyOwner(true);
        } else if ("TENANT".equals(relationshipType)) {
            newCustomer.setCustomerType(CustomerType.TENANT);
            newCustomer.setIsTenant(true);
        }

        return customerService.save(newCustomer);
    }

    private void createCustomerPropertyAssignment(Long customerId, Long propertyId, String assignmentType) {
        // Check if assignment already exists
        String checkSql = """
            SELECT COUNT(*) FROM customer_property_assignments
            WHERE customer_id = ? AND property_id = ? AND assignment_type = ?
        """;

        int count = jdbcTemplate.queryForObject(checkSql, Integer.class, customerId, propertyId, assignmentType);

        if (count == 0) {
            String insertSql = """
                INSERT INTO customer_property_assignments
                (customer_id, property_id, assignment_type, created_date, data_source)
                VALUES (?, ?, ?, NOW(), 'UPLOADED')
            """;

            jdbcTemplate.update(insertSql, customerId, propertyId, assignmentType);
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }

        fields.add(currentField.toString());
        return fields.toArray(new String[0]);
    }
}