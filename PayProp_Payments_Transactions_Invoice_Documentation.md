# PayProp API - Payments, Transactions & Invoices Documentation

## Overview
This document provides a comprehensive analysis of PayProp's API specification focusing on payment processing, transaction handling, and invoice management systems. Based on the API specification version 1.1.

## Payment System Architecture

### Payment Types

#### 1. Adhoc Payments (`/entity/adhoc-payment`)
**Purpose**: One-time payments with no recurring schedule
- **Method**: POST
- **Frequency**: "O" (One-time only)
- **Required Fields**: 
  - `property_id`
  - `beneficiary_type` 
  - `frequency`
  - `amount`

**Data Structure**:
```yaml
amount: number (minimum: 0.01, multipleOf: 0.01)
beneficiary_id: string (10-32 chars, alphanumeric)
beneficiary_type: enum [agency, beneficiary, global_beneficiary, property_account, deposit_account]
category_id: string (Payment category reference)
customer_id: string (1-50 chars, optional, experimental)
description: string (max 255 chars)
enabled: boolean
maintenance_ticket_id: string (optional association)
no_commission_amount: number (minimum: 0.01)
property_id: string (required)
reference: string (max 50 chars, required if beneficiary_type is "beneficiary")
start_date: date (defaults to today)
tax_amount: number/string (minimum: 0)
tenant_id: string
use_money_from: enum [any_tenant, tenant, property_account]
has_tax: boolean
```

#### 2. Recurring Payments (`/entity/payment`)
**Purpose**: Scheduled recurring payments
- **Method**: POST, GET, PUT
- **Frequency Options**: 
  - "O" (One-time)
  - "W" (Weekly)
  - "2W" (Bi-weekly) 
  - "4W" (Every 4 weeks)
  - "M" (Monthly)
  - "2M" (Bi-monthly)
  - "Q" (Quarterly)
  - "6M" (Semi-annually)
  - "A" (Annually)

**Additional Fields for Recurring Payments**:
```yaml
end_date: date (optional)
payment_day: integer (0-31)
percentage: number (0.01-100)
```

#### 3. Secondary Payments (`/entity/secondary-payment`)
**Purpose**: Split portions of primary payments to different beneficiaries
- **Method**: POST, GET, PUT
- **References**: `api_definitions.yaml#/definitions/SecondaryPaymentCreate`

#### 4. Posted Payments (`/posted-payments`)
**Purpose**: Capture incoming payments before bank reconciliation
- **Method**: POST
- **Description**: Allows capturing incoming payments that still need to reach the bank
- **References**: `api_definitions.yaml#/definitions/PostedPayment`

### Payment Categories (`/payments/categories`)
- **GET**: Retrieve payment categories
- **POST**: Create new payment category
- **PUT**: Update existing payment category (`/payments/categories/{external_id}`)
- **Filtering**: Support for inactive categories via `include_inactive` parameter

### Payment Export (`/export/payments`)
**Purpose**: Bulk export of payment data
**Parameters**:
- `rows`: Limit returned results
- `page`: Pagination
- `modified_from_time`: Filter by modification timestamp
- `modified_from_timezone`: Timezone specification
- `external_id`: Filter by specific payment ID
- `property_id`: Filter by property
- `include_beneficiary_info`: Include beneficiary details
- `category_id`: Filter by payment category
- `maintenance_ticket_id`: Filter by maintenance association

## Invoice System

### Invoice Types

#### 1. Adhoc Invoices (`/entity/adhoc-invoice`)
**Purpose**: One-time invoices
- **Method**: POST
- **Frequency**: "O" (One-time only)
- **Required Fields**:
  - `tenant_id`
  - `property_id` 
  - `amount`
  - `category_id`

**Data Structure**:
```yaml
amount: number (minimum: 0.01, multipleOf: 0.01)
category_id: string (Invoice category reference)
customer_id: string (experimental feature)
description: string (max 255 chars)
frequency: "O"
has_tax: boolean
is_direct_debit: boolean
property_id: string (required)
start_date: date (defaults to today)
tax_amount: number/string (minimum: 0)
tenant_id: string (required)
deposit_id: string (Tenant payment reference, auto-generated)
```

#### 2. Recurring Invoices (`/entity/invoice`)
**Purpose**: Scheduled recurring invoices
- **Method**: POST, GET, PUT
- **Additional Features**: Support for recurring schedules

### Invoice Categories (`/invoices/categories`)
- **GET**: Retrieve invoice categories
- **POST**: Create new invoice category  
- **PUT**: Update existing invoice category (`/invoices/categories/{external_id}`)

### Invoice Export (`/export/invoices`)
**Purpose**: Bulk export of invoice data
**Additional Export**: `/export/invoice-instructions` for invoice instruction data

### Invoice Documents (`/documents/pdf/agency-invoice`)
**Purpose**: Generate PDF documents for agency invoices
**Parameters**:
- `year`: Required (minimum: 2000)
- `month`: Required (1-12)
**Response**: Binary PDF data

## Transaction Processing

### Beneficiary Management (`/entity/beneficiary`)
**Payment Methods Supported**:
1. **Local Payments** (`payment_method: "local"`)
   - Bank details formatted for local country
   - Account number and sort code

2. **International Payments** (`payment_method: "international"`)
   - IBAN and SWIFT code required
   - Cross-border payment support

3. **Cheque Payments** (`payment_method: "cheque"`)
   - Physical address required for delivery
   - Postal code validation

**Communication Preferences**:
```yaml
communication_preferences:
  email_notifications: boolean (default: true)
  payment_advice: boolean (default: true)
```

### Webhook System for Real-time Updates

**Supported Webhook Events**:
- `payment` - Reconciled incoming payment (Actions: create, update)
- `payment_instruction` - Payment instruction changes (Actions: create, update, delete)
- `posted_payment` - Unreconciled posted payment (Actions: create, delete)
- `secondary_payment` - Secondary payment changes (Actions: create, update, delete)
- `outgoing_payment_batch` - Batch payment notifications (Actions: update)
- `invoice_rule` - Recurring invoice rules (Actions: create, update, delete)
- `invoice_instruction` - Invoice instructions (Actions: create, update, delete)

**Webhook Trigger**: Add `?send_webhook=true` parameter to API calls

## Data Flow & Transaction Lifecycle

### Incoming Payment Flow
1. **Posted Payment Creation** → 2. **Bank Reconciliation** → 3. **Payment Entity** → 4. **Webhook Notification**

### Outgoing Payment Flow  
1. **Payment Instruction** → 2. **Payment Batch** → 3. **Beneficiary Transfer** → 4. **Payment Advice**

### Invoice Flow
1. **Invoice Creation** → 2. **Tenant Notification** → 3. **Payment Collection** → 4. **Reconciliation**

## Key Data Types & Formats

### Identifiers
- **External ID**: 10-32 character alphanumeric string (`^[a-zA-Z0-9]+$`)
- **Customer ID**: 1-50 character string (`^[a-zA-Z0-9_-]+$`) - Experimental feature

### Monetary Values
- **Amount**: Minimum 0.01, multiple of 0.01 (2 decimal precision)
- **Tax Amount**: Minimum 0, accepts number or string
- **No Commission Amount**: Minimum 0.01

### Dates
- **Format**: ISO date format (YYYY-MM-DD)
- **Examples**: "2021-08-24"
- **Timezone Support**: Available for export filtering

### Status Values
- **Payment Status**: "new", "processed", "failed"
- **Frequencies**: O, W, 2W, 4W, M, 2M, Q, 6M, A

## API Integration Best Practices

### Authentication
- API keys required for all endpoints
- Service charges apply as per Customer Agreement
- 30-day notice for service charge changes

### Development Environment
- Temporary credentials available (90-day limit)
- Development sandbox with dummy data
- Integration testing support

### Error Handling
- HTTP status codes: 400, 401, 403, 404, 500, 501
- Structured error responses via `api_definitions.yaml`

### Pagination
- `rows` parameter for limiting results
- `page` parameter for navigation
- Pagination metadata included in responses

## Report Endpoints

### All Payments Report (`/report/all-payments`)
**Purpose**: Comprehensive payment reporting
**Response**: `api_definitions.yaml#/definitions/AllPaymentsReport`

### Owner Statement PDF (`/documents/pdf/owner-statement`)
**Purpose**: Generate owner financial statements
**Response**: Binary PDF data

## Maintenance Integration

### Maintenance Ticket Association
- Payments can be associated with maintenance tickets via `maintenance_ticket_id`
- Supports linking costs to specific property maintenance activities
- Enables tracking of maintenance-related financial flows

## Summary

The PayProp API provides a comprehensive payment and invoice management system with:

- **4 Payment Types**: Adhoc, Recurring, Secondary, Posted
- **2 Invoice Types**: Adhoc, Recurring  
- **3 Payment Methods**: Local, International, Cheque
- **9 Frequency Options**: From one-time to annual recurring
- **Real-time Webhooks**: For payment and invoice events
- **Export Capabilities**: Bulk data extraction with filtering
- **PDF Generation**: For invoices and statements
- **Maintenance Integration**: Link payments to property maintenance

The system supports both inbound payment reconciliation and outbound payment processing with comprehensive categorization, tax handling, and multi-beneficiary support.