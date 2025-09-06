# Local Entity to PayProp API Mapping
## Complete Field Mapping and Sync Service Design

*Based on successful PayProp API testing results*

---

## 🎯 **OVERVIEW**

This document maps our local entity creation system to the tested PayProp API endpoints, enabling bi-directional sync between local entities and PayProp.

**Core Integration Pattern:**
1. **Create Locally First** - All entities created in local system
2. **Sync to PayProp** - Push local entities to PayProp when needed
3. **Store PayProp IDs** - Link local entities to PayProp entities
4. **Winner Logic Respected** - PayProp data wins for synced entities

---

## 🏠 **PROPERTY MAPPING**

### **Local Property → PayProp Property**

| Local Field | PayProp API Field | Required | Example | Notes |
|-------------|------------------|----------|---------|-------|
| `propertyName` | `name` | ✅ | `"123 Oak Street"` | Property display name |
| `customerId` | `customer_id` | ✅ | `"PROP-001"` | Internal reference ID |
| `customerReference` | `customer_reference` | ❌ | `"REF-123"` | External reference |
| `addressLine1` | `address.address_line_1` | ✅ | `"123 Oak Street"` | Primary address |
| `addressLine2` | `address.address_line_2` | ❌ | `"Unit 4B"` | Secondary address |
| `city` | `address.city` | ✅ | `"London"` | City name |
| `state` | `address.state` | ❌ | `"Greater London"` | State/county |
| `postcode` | `address.postal_code` | ✅ | `"SW1A 1AA"` | Postal code |
| `countryCode` | `address.country_code` | ✅ | `"UK"` | Country code |
| `monthlyPayment` | `settings.monthly_payment` | ✅ | `2200.00` | **Required by PayProp** |
| `enablePayments` | `settings.enable_payments` | ❌ | `true` | Enable payments |
| `holdOwnerFunds` | `settings.hold_owner_funds` | ❌ | `false` | Hold funds |
| `verifyPayments` | `settings.verify_payments` | ❌ | `true` | Verify payments |
| `propertyAccountMinimumBalance` | `settings.minimum_balance` | ❌ | `300.00` | Min balance |

### **PayProp Property Creation Service**

```java
public String syncPropertyToPayProp(Property localProperty) {
    Map<String, Object> payPropData = new HashMap<>();
    
    // Required fields
    payPropData.put("name", localProperty.getPropertyName());
    payPropData.put("customer_id", localProperty.getCustomerId());
    
    // Address object
    Map<String, Object> address = new HashMap<>();
    address.put("address_line_1", localProperty.getAddressLine1());
    if (localProperty.getAddressLine2() != null) {
        address.put("address_line_2", localProperty.getAddressLine2());
    }
    address.put("city", localProperty.getCity());
    address.put("postal_code", localProperty.getPostcode());
    address.put("country_code", localProperty.getCountryCode() != null ? 
                localProperty.getCountryCode() : "UK");
    payPropData.put("address", address);
    
    // Settings object (required if monthly_payment provided)
    if (localProperty.getMonthlyPayment() != null) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("monthly_payment", localProperty.getMonthlyPayment());
        settings.put("enable_payments", "Y".equals(localProperty.getEnablePayments()));
        settings.put("hold_owner_funds", "Y".equals(localProperty.getHoldOwnerFunds()));
        settings.put("verify_payments", "Y".equals(localProperty.getVerifyPayments()));
        if (localProperty.getPropertyAccountMinimumBalance() != null) {
            settings.put("minimum_balance", localProperty.getPropertyAccountMinimumBalance());
        }
        payPropData.put("settings", settings);
    }
    
    // Call PayProp API
    String payPropId = payPropApiClient.createProperty(payPropData);
    
    // Update local property with PayProp ID
    localProperty.setPayPropId(payPropId);
    propertyService.save(localProperty);
    
    return payPropId;
}
```

---

## 👥 **CUSTOMER (BENEFICIARY/TENANT) MAPPING**

### **Local Customer → PayProp Beneficiary (Property Owners)**

| Local Field | PayProp API Field | Required | Example | Notes |
|-------------|------------------|----------|---------|-------|
| `accountType` | `account_type` | ✅ | `"individual"/"business"` | Account type |
| `firstName` | `first_name` | ✅* | `"John"` | *Required for individual |
| `lastName` | `last_name` | ✅* | `"Smith"` | *Required for individual |
| `businessName` | `business_name` | ✅* | `"Smith Properties Ltd"` | *Required for business |
| `email` | `email_address` | ✅ | `"john@example.com"` | Email address |
| `mobileNumber` | `mobile` | ❌ | `"447700123456"` | Mobile number |
| `phone` | `phone` | ❌ | `"442071234567"` | Phone number |
| `customerId` | `customer_id` | ✅ | `"CUST-001"` | Internal reference |
| `customerReference` | `customer_reference` | ❌ | `"REF-OWNER-001"` | External reference |
| `addressLine1` | `address.address_line_1` | ✅ | `"12 Victoria Gardens"` | Primary address |
| `city` | `address.city` | ✅ | `"London"` | City |
| `postcode` | `address.postal_code` | ✅ | `"SW1W 0ET"` | Postal code |
| `countryCode` | `address.country_code` | ✅ | `"UK"` | Country |
| `bankAccountName` | `bank_account.account_name` | ❌ | `"John Smith"` | Bank account name |
| `bankAccountNumber` | `bank_account.account_number` | ❌ | `"12345678"` | Account number |
| `bankSortCode` | `bank_account.branch_code` | ❌ | `"209876"` | Sort code |

### **Local Customer → PayProp Tenant**

| Local Field | PayProp API Field | Required | Example | Notes |
|-------------|------------------|----------|---------|-------|
| `accountType` | `account_type` | ✅ | `"individual"/"business"` | Account type |
| `firstName` | `first_name` | ✅* | `"Michael"` | *Required for individual |
| `lastName` | `last_name` | ✅* | `"Thompson"` | *Required for individual |
| `businessName` | `business_name` | ✅* | `"Thompson Consulting Ltd"` | *Required for business |
| `email` | `email_address` | ✅ | `"michael@example.com"` | Email address |
| `mobileNumber` | `mobile_number` | ❌ | `"447987654321"` | Mobile (tenant uses mobile_number) |
| `phone` | `phone` | ❌ | `"442089876543"` | Phone number |
| `customerId` | `customer_id` | ✅ | `"TEN-001"` | Internal reference |
| `customerReference` | `customer_reference` | ❌ | `"TENANT-123"` | External reference |
| `dateOfBirth` | `date_of_birth` | ❌ | `"1990-03-15"` | DOB (individual only) |
| `vatNumber` | `vat_number` | ❌ | `"GB456789123"` | VAT number (business only) |
| `hasBankAccount` | `has_bank_account` | ❌ | `true` | Whether has bank account |
| `bankAccountName` | `bank_account.account_name` | ❌ | `"Michael Thompson"` | Bank account name |
| `bankAccountNumber` | `bank_account.account_number` | ❌ | `"87654321"` | Account number |
| `bankSortCode` | `bank_account.branch_code` | ❌ | `"304050"` | Sort code |

### **Customer Sync Service**

```java
public String syncCustomerToPayProp(Customer localCustomer, String entityType) {
    Map<String, Object> payPropData = new HashMap<>();
    
    // Account type and basic info
    payPropData.put("account_type", localCustomer.getAccountType().name());
    payPropData.put("customer_id", "LOCAL-" + localCustomer.getCustomerId());
    payPropData.put("email_address", localCustomer.getEmail());
    
    // Name fields based on account type
    if (localCustomer.getAccountType() == AccountType.individual) {
        payPropData.put("first_name", localCustomer.getFirstName());
        payPropData.put("last_name", localCustomer.getLastName());
        if (localCustomer.getDateOfBirth() != null && "tenant".equals(entityType)) {
            payPropData.put("date_of_birth", localCustomer.getDateOfBirth().toString());
        }
    } else {
        payPropData.put("business_name", localCustomer.getBusinessName());
        payPropData.put("first_name", localCustomer.getFirstName()); // Contact person
        payPropData.put("last_name", localCustomer.getLastName());   // Contact person
        if (localCustomer.getVatNumber() != null) {
            payPropData.put("vat_number", localCustomer.getVatNumber());
        }
    }
    
    // Contact info
    if (localCustomer.getMobileNumber() != null) {
        if ("tenant".equals(entityType)) {
            payPropData.put("mobile_number", localCustomer.getMobileNumber());
        } else {
            payPropData.put("mobile", localCustomer.getMobileNumber());
        }
    }
    if (localCustomer.getPhone() != null) {
        payPropData.put("phone", localCustomer.getPhone());
    }
    
    // Address
    Map<String, Object> address = new HashMap<>();
    address.put("address_line_1", localCustomer.getAddressLine1());
    if (localCustomer.getAddressLine2() != null) {
        address.put("address_line_2", localCustomer.getAddressLine2());
    }
    address.put("city", localCustomer.getCity());
    address.put("postal_code", localCustomer.getPostcode());
    address.put("country_code", localCustomer.getCountryCode() != null ? 
                localCustomer.getCountryCode() : "UK");
    payPropData.put("address", address);
    
    // Bank account (if provided)
    if (localCustomer.getBankAccountNumber() != null) {
        Map<String, Object> bankAccount = new HashMap<>();
        bankAccount.put("account_name", localCustomer.getBankAccountName() != null ? 
                       localCustomer.getBankAccountName() : localCustomer.getName());
        bankAccount.put("account_number", localCustomer.getBankAccountNumber());
        bankAccount.put("branch_code", localCustomer.getBankSortCode());
        payPropData.put("bank_account", bankAccount);
        
        if ("tenant".equals(entityType)) {
            payPropData.put("has_bank_account", true);
        }
    }
    
    // Payment method for beneficiaries
    if ("beneficiary".equals(entityType)) {
        payPropData.put("payment_method", "local"); // Default to local payments
    }
    
    // Call appropriate PayProp API
    String payPropId;
    if ("beneficiary".equals(entityType)) {
        payPropId = payPropApiClient.createBeneficiary(payPropData);
    } else {
        payPropId = payPropApiClient.createTenant(payPropData);
    }
    
    // Update local customer with PayProp ID
    localCustomer.setPayPropEntityId(payPropId);
    localCustomer.setPayPropSynced(true);
    localCustomer.setPayPropLastSync(LocalDateTime.now());
    customerService.save(localCustomer);
    
    return payPropId;
}
```

---

## 📋 **INVOICE MAPPING**

### **Local Invoice → PayProp Invoice**

From our API testing, we know PayProp invoice creation requires these tested patterns:

| Local Field | PayProp API Field | Required | Example | Notes |
|-------------|------------------|----------|---------|-------|
| `amount` | `gross_amount` | ✅ | `1250.00` | Invoice amount |
| `description` | `description` | ✅ | `"Monthly rent"` | Description |
| `frequency` | `frequency_code` | ✅ | `"M"` | M/Q/Y/W/O |
| `paymentDay` | `payment_day` | ✅* | `1` | *Required for M/Q/Y |
| `startDate` | `start_date` | ✅ | `"2025-01-01"` | Start date |
| `endDate` | `end_date` | ❌ | `"2025-12-31"` | End date |
| `categoryId` | `category_id` | ✅ | `"Vv2XlY1ema"` | PayProp category ID |
| `customer.paypropEntityId` | `tenant_id` | ✅ | `"08JLzxl61R"` | Tenant PayProp ID |
| `property.payPropId` | `property_id` | ✅ | `"z2JkGdowJb"` | Property PayProp ID |
| `accountType` | `account_type` | ✅ | `"individual"` | From customer |
| `isDebitOrder` | `debit_order` | ❌ | `true` | Debit order flag |
| `vatIncluded` | `vat` | ❌ | `false` | VAT applicable |
| `vatAmount` | `vat_amount` | ❌ | `0.00` | VAT amount |

**PayProp Invoice Categories (from testing):**
- Rent: `Vv2XlY1ema` ✅ (System category)
- Maintenance: `vagXVvX3RP`
- Other: `W5AJ5Oa1Mk`
- Deposit: `woRZQl1mA4`
- Holding deposit: `6EyJ6RJjbv` ✅ (System category)

### **Invoice Sync Service**

```java
public String syncInvoiceToPayProp(Invoice localInvoice) {
    // Validate prerequisites
    if (localInvoice.getCustomer().getPayPropEntityId() == null) {
        throw new IllegalStateException("Customer must be synced to PayProp first");
    }
    if (localInvoice.getProperty().getPayPropId() == null) {
        throw new IllegalStateException("Property must be synced to PayProp first");
    }
    
    Map<String, Object> payPropData = new HashMap<>();
    
    // Required fields
    payPropData.put("gross_amount", localInvoice.getAmount());
    payPropData.put("description", localInvoice.getDescription());
    payPropData.put("frequency_code", localInvoice.getFrequency().name());
    payPropData.put("start_date", localInvoice.getStartDate().toString());
    payPropData.put("category_id", localInvoice.getCategoryId());
    payPropData.put("tenant_id", localInvoice.getCustomer().getPayPropEntityId());
    payPropData.put("property_id", localInvoice.getProperty().getPayPropId());
    payPropData.put("account_type", localInvoice.getAccountType().name());
    
    // Payment day for recurring invoices
    if (localInvoice.getFrequency().requiresPaymentDay()) {
        payPropData.put("payment_day", localInvoice.getPaymentDay());
    }
    
    // Optional fields
    if (localInvoice.getEndDate() != null) {
        payPropData.put("end_date", localInvoice.getEndDate().toString());
    }
    if (localInvoice.getIsDebitOrder() != null) {
        payPropData.put("debit_order", localInvoice.getIsDebitOrder());
    }
    if (localInvoice.getVatIncluded() != null) {
        payPropData.put("vat", localInvoice.getVatIncluded());
    }
    if (localInvoice.getVatAmount() != null) {
        payPropData.put("vat_amount", localInvoice.getVatAmount());
    }
    
    // Call PayProp API
    String payPropId = payPropApiClient.createInvoice(payPropData);
    
    // Update local invoice
    localInvoice.setPaypropId(payPropId);
    localInvoice.setSyncStatus(SyncStatus.synced);
    localInvoice.setPaypropLastSync(LocalDateTime.now());
    invoiceService.save(localInvoice);
    
    return payPropId;
}
```

---

## 💰 **PAYMENT INSTRUCTION MAPPING**

### **Local Payment → PayProp Payment**

From our testing, we know PayProp payment creation patterns:

| Local Field | PayProp API Field | Required | Example | Notes |
|-------------|------------------|----------|---------|-------|
| `amount` | `gross_amount` | ✅ | `225.00` | Payment amount |
| `percentage` | `gross_percentage` | ✅ | `9.00` | Commission percentage |
| `description` | `description` | ✅ | `"Management commission"` | Description |
| `frequency` | `frequency_code` | ✅ | `"M"` | M/Q/Y/W/O |
| `paymentDay` | `payment_day` | ✅* | `15` | *Required for M/Q/Y |
| `startDate` | `start_date` | ✅ | `"2025-01-01"` | Start date |
| `categoryId` | `category_id` | ✅ | `"Kd71e915Ma"` | Commission category |
| `beneficiaryId` | `beneficiary_id` | ✅ | `"EyJ6KBmQXj"` | Beneficiary PayProp ID |
| `property.payPropId` | `property_id` | ✅ | `"z2JkGdowJb"` | Property PayProp ID |

**PayProp Payment Categories (from testing):**
- Owner: `Vv2XlY1ema` ✅ (System category)
- Agent: `woRZQl1mA4`
- Contractor: `DWzJBaZQBp`
- Commission: `Kd71e915Ma` ✅ (System category)
- Deposit: `zKd1b21vGg` ✅ (System category)

---

## 🔄 **COMPREHENSIVE SYNC ORCHESTRATOR**

### **Enhanced PayProp Sync Service**

Based on our API testing, here's how to integrate with the existing `PayPropSyncOrchestrator`:

```java
/**
 * Sync local entity to PayProp using tested API patterns
 */
@Service
public class LocalToPayPropSyncService {
    
    @Autowired
    private PayPropApiClient payPropApiClient;
    
    /**
     * Sync a complete property ecosystem to PayProp
     */
    @Transactional
    public PropertyEcosystemSyncResult syncCompletePropertyToPayProp(Property localProperty) {
        PropertyEcosystemSyncResult result = new PropertyEcosystemSyncResult();
        
        try {
            // Step 1: Sync property first
            if (localProperty.getPayPropId() == null) {
                String propertyPayPropId = syncPropertyToPayProp(localProperty);
                result.setPropertySynced(true);
                result.setPropertyPayPropId(propertyPayPropId);
            }
            
            // Step 2: Sync all property owners as beneficiaries
            List<Customer> owners = assignmentService.getCustomersForProperty(
                localProperty.getId(), AssignmentType.OWNER);
            for (Customer owner : owners) {
                if (owner.getPayPropEntityId() == null) {
                    String beneficiaryId = syncCustomerToPayProp(owner, "beneficiary");
                    result.addSyncedBeneficiary(beneficiaryId);
                }
            }
            
            // Step 3: Sync all tenants
            List<Customer> tenants = assignmentService.getCustomersForProperty(
                localProperty.getId(), AssignmentType.TENANT);
            for (Customer tenant : tenants) {
                if (tenant.getPayPropEntityId() == null) {
                    String tenantId = syncCustomerToPayProp(tenant, "tenant");
                    result.addSyncedTenant(tenantId);
                }
            }
            
            // Step 4: Sync local invoices
            List<Invoice> localInvoices = invoiceService.findByProperty(localProperty);
            for (Invoice invoice : localInvoices) {
                if (invoice.needsPayPropSync()) {
                    String invoiceId = syncInvoiceToPayProp(invoice);
                    result.addSyncedInvoice(invoiceId);
                }
            }
            
            // Step 5: Create payment instructions based on assignments
            createPaymentInstructionsFromAssignments(localProperty, result);
            
            result.setSuccess(true);
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.error("Failed to sync property ecosystem: {}", e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * Create payment instructions based on property assignments
     */
    private void createPaymentInstructionsFromAssignments(Property property, 
                                                        PropertyEcosystemSyncResult result) {
        // Get property owners and their percentages
        List<CustomerPropertyAssignment> ownerAssignments = assignmentService
            .findByPropertyAndAssignmentType(property, AssignmentType.OWNER);
        
        for (CustomerPropertyAssignment assignment : ownerAssignments) {
            Customer owner = assignment.getCustomer();
            if (owner.getPayPropEntityId() != null) {
                
                // Create owner payment instruction (91% to owner)
                Map<String, Object> ownerPayment = new HashMap<>();
                ownerPayment.put("beneficiary_id", owner.getPayPropEntityId());
                ownerPayment.put("property_id", property.getPayPropId());
                ownerPayment.put("category_id", "Vv2XlY1ema"); // Owner category
                ownerPayment.put("gross_percentage", assignment.getOwnershipPercentage() 
                                                   != null ? assignment.getOwnershipPercentage() : 91.00);
                ownerPayment.put("frequency_code", "M");
                ownerPayment.put("payment_day", 15);
                ownerPayment.put("start_date", LocalDate.now().toString());
                ownerPayment.put("description", "Owner payment for " + property.getPropertyName());
                
                String ownerPaymentId = payPropApiClient.createPayment(ownerPayment);
                result.addSyncedPayment(ownerPaymentId);
                
                // Create agency commission payment (9% to agency)
                Map<String, Object> commissionPayment = new HashMap<>();
                commissionPayment.put("beneficiary_id", getAgencyBeneficiaryId()); // Your agency beneficiary
                commissionPayment.put("property_id", property.getPayPropId());
                commissionPayment.put("category_id", "Kd71e915Ma"); // Commission category
                commissionPayment.put("gross_percentage", 9.00);
                commissionPayment.put("frequency_code", "M");
                commissionPayment.put("payment_day", 15);
                commissionPayment.put("start_date", LocalDate.now().toString());
                commissionPayment.put("description", "Management commission for " + property.getPropertyName());
                
                String commissionPaymentId = payPropApiClient.createPayment(commissionPayment);
                result.addSyncedPayment(commissionPaymentId);
            }
        }
    }
}
```

---

## 🎯 **SYNC WORKFLOW EXAMPLES**

### **Example 1: New Property with Tenant**

```java
// 1. Create property locally
Property property = new Property("456 New Street");
property.setCustomerId("PROP-NEW-001");
property.setAddressLine1("456 New Street");
property.setCity("London");
property.setPostcode("E1 2AB");
property.setMonthlyPayment(new BigDecimal("1500.00"));
propertyService.save(property);

// 2. Create tenant locally
Customer tenant = new Customer();
tenant.setAccountType(AccountType.individual);
tenant.setFirstName("Alice");
tenant.setLastName("Johnson");
tenant.setEmail("alice.johnson@email.com");
tenant.setCustomerType(CustomerType.TENANT);
customerService.save(tenant);

// 3. Create assignment
assignmentService.createAssignment(tenant, property, AssignmentType.TENANT);

// 4. Create local invoice
Invoice invoice = invoiceService.createRentInvoice(
    tenant, property, new BigDecimal("1500.00"), 
    LocalDate.of(2025, 1, 1), 1);

// 5. Sync entire ecosystem to PayProp
PropertyEcosystemSyncResult result = localToPayPropSyncService
    .syncCompletePropertyToPayProp(property);

// Result: Property, tenant, and invoice now exist in PayProp with proper IDs
```

### **Example 2: Historical Data Integration**

```java
// 1. Import historical transactions
String csvData = """
    transaction_date,amount,description,transaction_type,property_reference
    2023-01-15,-1500.00,"Rent payment",payment,"456 New Street"
    2023-02-15,-1500.00,"Rent payment",payment,"456 New Street"
    """;

ImportResult result = historicalTransactionImportService
    .importFromJsonString(csvData, "Historical rent payments");

// 2. Now reports show:
// - PayProp invoices (future expectations)
// - Historical transactions (actual payments)
// - Complete financial picture
```

---

## 📊 **FIELD MAPPING REFERENCE**

### **Quick Reference Table**

| Entity | Local Table | PayProp Endpoint | Key Mapping |
|--------|------------|------------------|-------------|
| Property | `properties` | `POST /entity/property` | `propertyName` → `name` |
| Owner | `customers` | `POST /entity/beneficiary` | `customerType=PROPERTY_OWNER` |
| Tenant | `customers` | `POST /entity/tenant` | `customerType=TENANT` |
| Invoice | `invoices` | `POST /entity/invoice` | `amount` → `gross_amount` |
| Payment | `payments` | `POST /entity/payment` | `percentage` → `gross_percentage` |

### **Category ID Mappings**

```java
public class PayPropCategoryMappings {
    // Invoice Categories
    public static final String RENT_CATEGORY = "Vv2XlY1ema";
    public static final String MAINTENANCE_CATEGORY = "vagXVvX3RP";
    public static final String DEPOSIT_CATEGORY = "woRZQl1mA4";
    
    // Payment Categories  
    public static final String OWNER_CATEGORY = "Vv2XlY1ema";
    public static final String COMMISSION_CATEGORY = "Kd71e915Ma";
    public static final String CONTRACTOR_CATEGORY = "DWzJBaZQBp";
}
```

---

## ✅ **IMPLEMENTATION CHECKLIST**

### **Phase 1: Core Sync Services**
- [ ] Create `LocalToPayPropSyncService`
- [ ] Implement property sync method
- [ ] Implement customer sync method (beneficiary/tenant)
- [ ] Implement invoice sync method
- [ ] Add field mapping utilities

### **Phase 2: Integration**
- [ ] Enhance existing `PayPropSyncOrchestrator`
- [ ] Add sync triggers to local entity creation
- [ ] Implement error handling and retry logic
- [ ] Add sync status tracking

### **Phase 3: UI Integration**
- [ ] Add "Sync to PayProp" buttons to forms
- [ ] Show sync status in entity lists
- [ ] Create sync management interface
- [ ] Add bulk sync operations

### **Phase 4: Testing**
- [ ] Test property ecosystem sync
- [ ] Test individual entity sync
- [ ] Test error scenarios
- [ ] Validate data consistency

---

## 🎯 **SUCCESS METRICS**

With this mapping implemented, you'll have:

1. **Complete Bi-directional Sync** - Local → PayProp → Local
2. **Tested API Integration** - Based on actual successful API calls
3. **Field-level Mapping** - Every field mapped and validated
4. **Ecosystem Sync** - Properties, customers, invoices, payments all connected
5. **Error Handling** - Robust error handling based on testing experience

**Your system will support the complete workflow: Create locally → Sync to PayProp → Unified reporting with PayProp winner logic.** 🚀