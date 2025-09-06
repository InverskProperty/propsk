# PayProp API Integration Findings

## Overview
This document tracks findings from testing PayProp's staging API to help set up the production system.

## API Base URL
- **Staging**: `https://ukapi.staging.payprop.com/api/agency/v1.1`
- **Production**: `https://uk.payprop.com/api/agency/v1.1` (assumed)

## Authentication
- OAuth 2.0 required
- Must be logged in to access categories endpoints
- Headers handled automatically by backend `PayPropOAuth2Controller`

---

## Entity Creation Tests

### 1. Property Creation ✅
**Endpoint**: `POST /entity/property`

**Working Request**:
```json
{
  "name": "Test Workflow Property - 789 Elm Avenue",
  "address": {
    "address_line_1": "789 Elm Avenue",
    "address_line_2": "Unit 3B", 
    "city": "London",
    "state": "Greater London",
    "country_code": "UK",
    "postal_code": "SE1 8XY"
  },
  "settings": {
    "monthly_payment": 2200.00,
    "enable_payments": true,
    "hold_owner_funds": false,
    "verify_payments": true,
    "minimum_balance": 300.00
  },
  "customer_id": "WORKFLOW-TEST-PROP-001"
}
```

**Key Findings**:
- ❌ `description` field not allowed (returns 400 error)
- ✅ `settings.monthly_payment` is required
- ✅ Returns Property ID: `z2JkGdowJb`
- ✅ Auto-sets `listing_from` to current date, `listing_to` to `9999-12-31`

---

## Categories Available

### Invoice Categories
Retrieved from: `GET /invoices/categories`

| ID | Name | System | Active |
|---|---|---|---|
| `Vv2XlY1ema` | **Rent** | ✅ | ✅ |
| `vagXVvX3RP` | Maintenance | ❌ | ✅ |
| `W5AJ5Oa1Mk` | Other | ❌ | ✅ |
| `woRZQl1mA4` | Deposit | ❌ | ✅ |
| `6EyJ6RJjbv` | Holding deposit | ✅ | ✅ |

### Payment Categories  
Retrieved from: `GET /payments/categories`

| ID | Name | System | Active |
|---|---|---|---|
| `Vv2XlY1ema` | **Owner** | ✅ | ✅ |
| `woRZQl1mA4` | Agent | ❌ | ✅ |
| `DWzJBaZQBp` | Contractor | ❌ | ✅ |
| `Kd71e915Ma` | Commission | ✅ | ✅ |
| `zKd1b21vGg` | Deposit | ✅ | ✅ |

---

### 2. Beneficiary Creation ✅
**Endpoint**: `POST /entity/beneficiary`

**Working Request**:
```json
{
  "account_type": "individual",
  "first_name": "Sarah",
  "last_name": "Johnson",
  "email_address": "sarah.johnson@example.com",
  "mobile": "447700123456",
  "phone": "442071234567",
  "customer_id": "WORKFLOW-TEST-BEN-001",
  "customer_reference": "OWNER-789-ELM",
  "address": {
    "address_line_1": "12 Victoria Gardens",
    "city": "London",
    "postal_code": "SW1W 0ET",
    "country_code": "UK"
  },
  "bank_account": {
    "account_name": "Sarah Johnson",
    "account_number": "12345678",
    "branch_code": "209876"
  },
  "payment_method": "local"
}
```

**Key Findings**:
- ✅ All fields accepted successfully
- ✅ Auto-sets `communication_preferences.email.enabled: true`
- ✅ Auto-sets `notify_email: true`, `notify_sms: true`
- ✅ Bank account number masked in response: `....5678`
- ✅ Returns Beneficiary ID: `EyJ6KBmQXj`

### 3. Tenant Creation ✅
**Endpoint**: `POST /entity/tenant`

**Working Request**:
```json
{
  "account_type": "individual",
  "first_name": "Michael",
  "last_name": "Thompson",
  "email_address": "michael.thompson@example.com",
  "mobile_number": "447987654321",
  "phone": "442089876543",
  "customer_id": "WORKFLOW-TEST-TEN-001",
  "customer_reference": "TENANT-789-ELM",
  "date_of_birth": "1990-03-15",
  "address": {
    "address_line_1": "789 Elm Avenue",
    "address_line_2": "Unit 3B",
    "city": "London", 
    "state": "Greater London",
    "country_code": "UK",
    "postal_code": "SE1 8XY"
  },
  "has_bank_account": true,
  "bank_account": {
    "account_name": "Michael Thompson",
    "account_number": "87654321",
    "branch_code": "304050"
  }
}
```

**Key Findings**:
- ✅ All fields accepted successfully
- ✅ Returns Tenant ID: `08JLzxl61R`
- ✅ Bank account number masked: `....4321`
- ❌ `notify_email: false`, `notify_sms: false` (different from beneficiary)
- ✅ `lead_days: 0` set automatically

### 4. Business Tenant Creation ✅
**Endpoint**: `POST /entity/tenant`

**Working Request**:
```json
{
  "account_type": "business",
  "business_name": "Thompson Consulting Ltd",
  "first_name": "Emma",
  "last_name": "Thompson", 
  "email_address": "emma@thompsonconsulting.co.uk",
  "mobile_number": "447912345678",
  "phone": "442071234890",
  "customer_id": "WORKFLOW-TEST-BUS-TEN-001",
  "customer_reference": "BUSINESS-TENANT-001",
  "vat_number": "GB456789123",
  "address": {
    "address_line_1": "789 Elm Avenue",
    "address_line_2": "Unit 3B",
    "city": "London",
    "state": "Greater London",
    "country_code": "UK",
    "postal_code": "SE1 8XY"
  },
  "has_bank_account": true,
  "bank_account": {
    "account_name": "Thompson Consulting Ltd",
    "account_number": "11223344",
    "branch_code": "205060"
  }
}
```

**Key Findings**:
- ✅ Business account type works perfectly
- ✅ Returns Business Tenant ID: `LQZrrPMRZN`
- ✅ `business_name` field populated
- ✅ `vat_number` field accepted: `GB456789123`
- ✅ `date_of_birth: null` (not applicable for business)
- ✅ Bank account in company name works
- ❌ Same notification defaults: `notify_email: false`, `notify_sms: false`

---

## Test Entities Created

### Test Property
- **PayProp ID**: `z2JkGdowJb`
- **Name**: "Test Workflow Property - 789 Elm Avenue"
- **Customer ID**: "WORKFLOW-TEST-PROP-001"
- **Monthly Payment**: £2,200
- **Status**: Active ✅

### Test Beneficiary (Property Owner)
- **PayProp ID**: `EyJ6KBmQXj`
- **Name**: "Sarah Johnson"
- **Customer ID**: "WORKFLOW-TEST-BEN-001"
- **Account**: Individual, UK Local Payment
- **Status**: Active ✅

### Test Tenant
- **PayProp ID**: `08JLzxl61R`
- **Name**: "Michael Thompson"
- **Customer ID**: "WORKFLOW-TEST-TEN-001"
- **DOB**: 1990-03-15
- **Address**: Same as property (789 Elm Avenue, Unit 3B)
- **Status**: Active ✅

### Test Recurring Invoice (Rent)
- **PayProp ID**: `rp19vo6GZA`
- **Tenant**: `08JLzxl61R` (Michael Thompson)
- **Property**: `z2JkGdowJb` (789 Elm Avenue)
- **Amount**: £2,200/month
- **Frequency**: Monthly on 1st
- **Start Date**: 2025-10-01
- **Status**: Active ✅

### Test Recurring Payment (To Owner)
- **PayProp ID**: `0JYep2aeJo`
- **Property**: `z2JkGdowJb` (789 Elm Avenue)
- **Beneficiary**: `EyJ6KBmQXj` (Sarah Johnson)
- **Amount**: £1,980/month (after 10% management fee)
- **Frequency**: Monthly on 25th
- **Start Date**: 2025-10-25
- **Status**: Active ✅

---

## Complete Workflow Summary ✅
1. **Tenant** (`08JLzxl61R`) pays **£2,200** rent on 1st → **Property** (`z2JkGdowJb`)
2. **Property** pays **£1,980** (net) on 25th → **Owner** (`EyJ6KBmQXj`)
3. **Agency** keeps **£220** management fee (10%)

### Global Contractor Beneficiary ✅
- **PayProp ID**: `5AJ5Klm91M`
- **Business Name**: "ABC Plumbing Services Ltd"
- **Account Type**: Business (not individual)
- **VAT Number**: GB987654321
- **Use Case**: Can work on ANY property (not linked to specific property)

### Test Business Tenant ✅
- **PayProp ID**: `LQZrrPMRZN`
- **Business Name**: "Thompson Consulting Ltd"
- **Contact**: "Emma Thompson"
- **Customer ID**: "WORKFLOW-TEST-BUS-TEN-001"
- **VAT Number**: GB456789123
- **Account Type**: Business (matches your setup)
- **Status**: Active ✅

---

## Key Findings Summary

### Entity Types Tested ✅
1. **Properties**: Basic with required `settings.monthly_payment`
2. **Individual Beneficiaries**: Property owners with auto-notifications enabled
3. **Business Beneficiaries**: Global contractors with VAT numbers
4. **Individual Tenants**: Personal renters with notifications disabled by default
5. **Business Tenants**: Company renters (matches your setup) with VAT support
6. **Recurring Invoices**: Monthly rent with date validation (future dates only)
7. **Recurring Payments**: Owner payments with category linking
8. **Ad-hoc Invoices**: One-time charges (maintenance, etc.)

### Your System Compatibility ✅
- ✅ **Business Tenants**: PayProp fully supports your business tenant setup
- ✅ **Global Contractors**: Can serve multiple properties without duplication
- ✅ **Complete Cash Flow**: Tenant → Property → Owner workflows working
- ✅ **Category System**: Invoice/Payment categories properly linked

---

## Next Steps - Production Setup
1. ~~Test all entity creation patterns~~ ✅
2. ~~Validate business tenant support~~ ✅
3. ~~Test global contractor model~~ ✅
4. **Ready for production integration!** 🚀

---

## Commission System Deep Dive ✅

### Real Property Example: "88a Satchwell Road, Canterbury"

**Payment Instructions Setup:**
1. **Commission Payment**: "TAKEN: Propsk" → 9.00% → Agency
2. **Owner Payment**: "Natalie Turner" → 100.00% → Owner (of remaining amount)

**Actual Transaction Flow** (from real data):
```
Tenant pays £1,075.00 rent
├── Commission: £96.75 → "TAKEN: Propsk" (9% agency)  
└── Net to Owner: £978.25 → "Natalie Turner" (91%)
```

**Transaction Fees Applied:**
- Incoming payment fee: £0.18 + £4.64 service fee
- Owner payment fee: £0.36

### Commission Payment Configuration Details:

**PayProp Commission Payment Setup:**
```
Payment Category: Commission
Beneficiary Type: Agency ("TAKEN: Propsk")
Payment Type: Percentage
Percentage: 9.00%
VAT Handling: 
  - Percentage excluding VAT: 7.5%
  - VAT Rate: 20.00%
Use Money From: Any tenant
Date Range: 2019-05-20 to ongoing
```

### Commission API Implementation:

**1. Commission Payment (Agency)**:
```json
{
  "beneficiary_type": "agency",
  "category_id": "Kd71e915Ma",  // Commission category
  "percentage": 9.0,
  "has_tax": true,
  "tax_amount": 1.5,  // VAT calculation (7.5% base + 1.5% VAT = 9%)
  "reference": "TAKEN: Propsk",
  "description": "Management commission",
  "use_money_from": "any_tenant"
}
```

**2. Owner Payment (Net Amount)**:
```json
{
  "beneficiary_type": "beneficiary", 
  "beneficiary_id": "{{owner_id}}",
  "category_id": "Vv2XlY1ema",  // Owner category
  "percentage": 91.0,  // Remaining after 9% commission
  "has_tax": false,
  "reference": "Natalie Turner"
}
```

### Key Insights:
- ✅ **Commission percentage is applied FIRST** (9% to agency)
- ✅ **VAT is handled within commission** (7.5% + 1.5% VAT = 9% total)
- ✅ **"Use Money From: Any tenant"** allows flexibility in payment source
- ✅ **Two separate payment instructions** handle the split
- ✅ **Transaction fees are separate** from commission calculations
- ✅ **"TAKEN: Propsk"** is your agency identifier
- ✅ **Payment type: Percentage** (not fixed amount)
- ✅ **Long-running payment** (2019-05-20 to ongoing)

---

## Field Validation Rules
- Property `description` field: **NOT ALLOWED** ❌
- Property `settings.monthly_payment`: **REQUIRED** ✅
- Category authentication: **LOGIN REQUIRED** ✅
- Commission structure: **TWO PAYMENT INSTRUCTIONS** (Agency + Owner) ✅
- Commission timing: **APPLIED FIRST** before owner payment ✅

---

*Last Updated: 2025-09-05 20:45*