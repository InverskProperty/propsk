# PayProp Integration Code Review
## Complete Table Structures with Sample Data

---

## 📊 Core Entity Tables - Detailed Field Analysis

### 1. **`payprop_export_properties`** (352 records)
**Complete property master data with address and settings**

| Field | Type | Sample Data |
|-------|------|------------|
| `payprop_id` | varchar(50) | `08JLaK0qXR` |
| `name` | text | `NULL` |
| `description` | text | `NULL` |
| `create_date` | timestamp | `NULL` |
| `modify_date` | timestamp | `NULL` |
| `start_date` | timestamp | `NULL` |
| `end_date` | timestamp | `NULL` |
| `property_image` | text | `NULL` |
| `address_id` | varchar(50) | `08JL4Yg0JR` |
| `address_first_line` | varchar(100) | `10 Eldon Grove` |
| `address_second_line` | varchar(100) | `` |
| `address_third_line` | varchar(100) | `` |
| `address_city` | varchar(50) | `Wandsworth` |
| `address_state` | varchar(50) | `Greater London` |
| `address_country_code` | varchar(2) | `UK` |
| `address_postal_code` | varchar(10) | `SW15 8LH` |
| `address_zip_code` | varchar(10) | `SW15 8LH` |
| `address_latitude` | decimal(10,8) | `NULL` |
| `address_longitude` | decimal(11,8) | `NULL` |
| `address_phone` | varchar(20) | `1805 407437` |
| `address_fax` | varchar(20) | `1805 438815` |
| `address_email` | varchar(100) | `oht86fw@aol.com` |
| `address_created` | timestamp | `2020-05-27 00:00:00` |
| `address_modified` | timestamp | `2020-06-23 00:00:00` |
| `settings_monthly_payment` | decimal(10,2) | `NULL` |
| `settings_enable_payments` | tinyint(1) | `NULL` |
| `settings_hold_owner_funds` | tinyint(1) | `NULL` |
| `settings_verify_payments` | tinyint(1) | `NULL` |
| `settings_minimum_balance` | decimal(10,2) | `NULL` |
| `settings_listing_from` | date | `NULL` |
| `settings_approval_required` | tinyint(1) | `NULL` |
| `commission_percentage` | decimal(5,2) | `NULL` |
| `commission_amount` | decimal(10,2) | `NULL` |
| `commission_id` | varchar(50) | `NULL` |
| `imported_at` | timestamp | `2025-08-21 23:53:57` |
| `last_modified_at` | timestamp | `NULL` |
| `sync_status` | enum | `active` |
| `is_archived` | tinyint(1) | `1` |
| `contract_amount` | decimal(10,2) | `NULL` |
| `balance_amount` | decimal(10,2) | `NULL` |

---

### 2. **`payprop_export_tenants_complete`** (541 records)
**Complete tenant profiles with address, tenancy, and nested property data**

| Field | Type | Sample Data |
|-------|------|------------|
| `payprop_id` | varchar(50) | `08JL8aWnJR` |
| `first_name` | varchar(100) | `Chris` |
| `last_name` | varchar(100) | `Ferguson` |
| `business_name` | varchar(255) | `Ferguson Chris` |
| `display_name` | varchar(255) | `Ferguson Chris` |
| `email` | varchar(255) | `22p6ypvg@hotmail.com` |
| `email_cc` | varchar(255) | `slimsicily@icloud.com` |
| `phone` | varchar(50) | `NULL` |
| `mobile` | varchar(50) | `447975991091` |
| `date_of_birth` | date | `NULL` |
| `id_number` | varchar(100) | `845016798765` |
| `id_type` | varchar(50) | `NULL` |
| `nationality` | varchar(50) | `NULL` |
| `occupation` | varchar(100) | `NULL` |
| `employer` | varchar(255) | `NULL` |
| `address_id` | varchar(50) | `oRZQMVzdZm` |
| `address_first_line` | varchar(255) | `Unit 23` |
| `address_second_line` | varchar(255) | `67 Chester Cottages` |
| `address_third_line` | varchar(255) | `` |
| `address_city` | varchar(100) | `Whitfield` |
| `address_state` | varchar(100) | `Kent` |
| `address_country_code` | varchar(2) | `UK` |
| `address_postal_code` | varchar(10) | `CT15 1RH` |
| `address_zip_code` | varchar(10) | `CT15 1RH` |
| `address_phone` | varchar(20) | `1285 857938` |
| `address_email` | varchar(100) | `55femnun@york.ac.uk` |
| `emergency_contact_name` | varchar(255) | `NULL` |
| `emergency_contact_phone` | varchar(50) | `NULL` |
| `emergency_contact_relationship` | varchar(100) | `NULL` |
| `current_property_id` | varchar(50) | `w7Z4kyV41P` |
| `current_deposit_id` | varchar(50) | `CDD422` |
| `tenancy_start_date` | date | `2024-08-09` |
| `tenancy_end_date` | date | `NULL` |
| `monthly_rent_amount` | decimal(10,2) | `995.00` |
| `deposit_amount` | decimal(10,2) | `NULL` |
| `notify_email` | tinyint(1) | `1` |
| `notify_sms` | tinyint(1) | `1` |
| `preferred_contact_method` | varchar(20) | `email` |
| `tenant_status` | varchar(50) | `Active` |
| `is_active` | tinyint(1) | `1` |
| `credit_score` | int | `NULL` |
| `reference` | text | `` |
| `comment` | text | `` |
| `properties_json` | json | Complex nested property data |
| `imported_at` | timestamp | `2025-08-21 23:55:23` |
| `sync_status` | enum | `active` |

**Sample `properties_json` structure:**
```json
[{
  "id": "w7Z4kyV41P",
  "tenant": {
    "end_date": null,
    "deposit_id": "CDD422", 
    "start_date": "2024-08-09"
  },
  "address": {
    "id": "Kd1bmRK0Xv",
    "city": "Manston",
    "first_line": "8 Brunehild Street",
    "postal_code": "CT12 0LC"
  },
  "property_name": "Brunehild Street 8, Manston",
  "monthly_payment_required": "995.00",
  "responsible_agent": "Stacey Thomas"
}]
```

---

### 3. **`payprop_export_beneficiaries_complete`** (173 records)
**Payment recipients with address and property relationships**

| Field | Type | Sample Data |
|-------|------|------------|
| `payprop_id` | varchar(50) | `08JL49oqJR` |
| `business_name` | varchar(255) | `` |
| `display_name` | varchar(255) | `NULL` |
| `first_name` | varchar(100) | `Josh` |
| `last_name` | varchar(100) | `Morley` |
| `email_address` | varchar(255) | `w15dppuh3@hotmail.co.uk` |
| `email_cc_address` | varchar(255) | `` |
| `mobile_number` | varchar(50) | `447576488062` |
| `reference` | varchar(100) | `NULL` |
| `comment` | text | `NULL` |
| `customer_id` | varchar(50) | `NULL` |
| `status` | varchar(50) | `NULL` |
| `date_of_birth` | date | `NULL` |
| `id_reg_no` | varchar(100) | `NULL` |
| `id_type_id` | varchar(50) | `` |
| `vat_number` | varchar(50) | `882291925` |
| `notify_email` | tinyint(1) | `1` |
| `notify_sms` | tinyint(1) | `1` |
| `invoice_lead_days` | int | `NULL` |
| `address_id` | varchar(50) | `NULL` |
| `address_first_line` | varchar(255) | `NULL` |
| `address_second_line` | varchar(255) | `NULL` |
| `address_third_line` | varchar(255) | `NULL` |
| `address_city` | varchar(100) | `NULL` |
| `address_state` | varchar(100) | `NULL` |
| `address_country_code` | varchar(2) | `NULL` |
| `address_postal_code` | varchar(10) | `NULL` |
| `address_zip_code` | varchar(10) | `NULL` |
| `address_latitude` | decimal(10,8) | `NULL` |
| `address_longitude` | decimal(11,8) | `NULL` |
| `address_phone` | varchar(20) | `NULL` |
| `address_fax` | varchar(20) | `NULL` |
| `address_email` | varchar(100) | `NULL` |
| `address_created` | timestamp | `NULL` |
| `address_modified` | timestamp | `NULL` |
| `total_properties` | int | `1` |
| `total_account_balance` | decimal(10,2) | `0.00` |
| `properties_json` | json | Complex nested property data |
| `imported_at` | timestamp | `2025-08-22 00:02:01` |
| `sync_status` | enum | `active` |

---

### 4. **`payprop_export_invoices`** (244 records) ⭐
**Active rent instructions - KEY FOR OCCUPANCY LOGIC**

| Field | Type | Sample Data |
|-------|------|------------|
| `payprop_id` | varchar(50) | `08JL64W01R` |
| `account_type` | varchar(50) | `direct deposit` |
| `debit_order` | tinyint(1) | `0` |
| `description` | text | `` |
| `frequency` | varchar(20) | `Monthly` |
| `frequency_code` | varchar(1) | `M` |
| `from_date` | date | `2025-03-01` |
| `to_date` | date | `NULL` |
| `gross_amount` | decimal(10,2) | `1100.00` |
| `payment_day` | int | `26` |
| `invoice_type` | varchar(50) | `Rent` |
| `reference` | varchar(100) | `` |
| `vat` | tinyint(1) | `0` |
| `vat_amount` | decimal(10,2) | `0.00` |
| `property_payprop_id` | varchar(50) | `oRZQgRxXmA` |
| `tenant_payprop_id` | varchar(50) | `08JLzm921R` |
| `category_payprop_id` | varchar(50) | `Vv2XlY1ema` |
| `property_name` | varchar(255) | `Grace Street 39, Hackney` |
| `tenant_display_name` | varchar(255) | `Mackenzie Jessica` |
| `tenant_email` | varchar(255) | `fqscubyimf@gmail.com` |
| `tenant_business_name` | varchar(255) | `Mackenzie Jessica` |
| `tenant_first_name` | varchar(100) | `Jessica` |
| `tenant_last_name` | varchar(100) | `Mackenzie` |
| `category_name` | varchar(100) | `Rent` |
| `imported_at` | timestamp | `2025-08-25 16:39:13` |
| `last_modified_at` | timestamp | `NULL` |
| `sync_status` | enum | `active` |
| `is_active_instruction` | tinyint(1) | `NULL` |

---

## 🚀 Key Insights

### **Occupancy Logic Implementation:**
```sql
-- OCCUPIED = Properties with active rent instructions  
SELECT COUNT(DISTINCT property_payprop_id) 
FROM payprop_export_invoices 
WHERE invoice_type = 'Rent' AND sync_status = 'active'
-- Result: 241 occupied properties

-- VACANT = Active properties WITHOUT rent instructions
SELECT COUNT(*) - 241 
FROM payprop_export_properties 
WHERE is_archived = 0
-- Result: 22 vacant properties (263 - 241)
```

### **Data Relationships:**
- **Properties** ↔ **Tenants**: Via `current_property_id` and `properties_json`
- **Properties** ↔ **Invoices**: Via `property_payprop_id` ⭐ **CRITICAL FOR OCCUPANCY**
- **Beneficiaries** ↔ **Properties**: Via `properties_json` nested data
- **Complete Tables**: Include full nested JSON relationships for complex queries

---

## 🚨 **Legacy Data Usage Found - TODO List**

### **Java Files Using Legacy Occupancy Logic:**
1. **`PortfolioController.java`** - Line 1081: `property.isOccupied()` ❌
2. **`Property.java`** - `isOccupied()` method uses legacy status field ❌
3. **`PropertyRepository.java`** - `findOccupiedProperties()` uses `customer_property_assignments` ❌
4. **`PropertyRepository.java`** - `findVacantProperties()` uses `customer_property_assignments` ❌
5. **`HomePageController.java`** - May use legacy property statistics ❌
6. **`PropertyOwnerController.java`** - May use legacy occupancy data ❌
7. **`PayPropSyncController.java`** - Check for legacy references ❌

### **HTML Templates Using Legacy Statistics:**
8. **`property/all-properties.html`** - Property count displays ❌
9. **`property/vacant-properties.html`** - Vacancy statistics ❌
10. **`property/portfolio-overview.html`** - Portfolio metrics ❌
11. **`general/left-sidebar.html`** - Navigation counters ❌
12. **`portfolio/portfolio-details.html`** - Portfolio statistics ❌
13. **`portfolio/all-portfolios.html`** - Portfolio listings ❌
14. **`index.html`** - Dashboard statistics ❌
15. **`property-owner/properties.html`** - Owner property counts ❌
16. **`property-owner/financials.html`** - Owner financial stats ❌

### **Repository Layer Issues:**
17. **`PropertyRepository.java`** - Replace junction table queries with PayProp logic ❌
18. **`CustomerPropertyAssignmentRepository.java`** - May be used incorrectly for occupancy ❌

### **Service Layer Updates Needed:**
19. **`PropertyService.java`** interface - Update method signatures if needed ❌
20. **`TenantService.java`** / **`TenantServiceImpl.java`** - Check for legacy tenant counting ❌

### **Entity Method Updates:**
21. **`Property.java`** - Update `isOccupied()` method to use PayProp data ❌
22. **`Portfolio.java`** - Check for property counting methods ❌

---

## ✅ **Already Fixed:**
- **`PropertyController.java`** - PayProp ID routing and portfolio statistics ✅
- **`PropertyServiceImpl.java`** - `findOccupiedProperties()` and `findVacantProperties()` methods ✅  
- **`FinancialController.java`** - PayProp-based financial calculations ✅

---

## 🎯 **Action Plan:**
1. **Phase 1**: Fix core entity methods (`Property.java`, `PropertyRepository.java`)
2. **Phase 2**: Update controllers using legacy methods (`PortfolioController.java`, etc.)
3. **Phase 3**: Update HTML templates with correct statistics
4. **Phase 4**: Test all property listing and statistics pages
5. **Phase 5**: Remove unused legacy methods and clean up code