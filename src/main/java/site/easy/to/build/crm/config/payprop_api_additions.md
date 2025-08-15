# PayProp API Additional Features

## Credit Check System
POST /entity/tenant-credit-check

### South African ID Check
```json
{
  "identity_type": "south_african_id",
  "tenant_id": "string",
  "property_id": "string", 
  "first_name": "string",
  "last_name": "string",
  "id_number": "1234567890123",
  "monthly_rent": 10000.00,
  "net_monthly_income": 50000.00,
  "email": "tenant@email.com",
  "mobile_number": "27123456789"
}
```

### Passport Check
```json
{
  "identity_type": "passport",
  "tenant_id": "string",
  "property_id": "string",
  "first_name": "string", 
  "last_name": "string",
  "passport_number": "ABC123456",
  "passport_country": "GB",
  "gender": "male|female",
  "date_of_birth": "1990-01-01",
  "monthly_rent": 10000.00,
  "net_monthly_income": 50000.00
}
```

### Business Check
```json
{
  "identity_type": "business",
  "tenant_id": "string",
  "property_id": "string",
  "business_name": "Company Ltd",
  "registration_number": "99/123456/07",
  "monthly_rent": 10000.00,
  "net_monthly_income": 50000.00
}
```

## Validation Rules
- Property names: max 255 chars
- SA ID numbers: exactly 13 digits
- Passport numbers: 3-17 chars
- Mobile numbers: must start with non-zero digit
- Business reg pattern: `^\d{2,}/\d{4,}/\d{2,}$`
- Monthly rent: R10-R1,000,000
- Email: max 50 chars

## Additional Endpoints

### Maintenance Tickets
- GET /maintenance/tickets
- POST /maintenance/tickets
- PUT /maintenance/tickets/{external_id}
- GET /maintenance/categories

### File Attachments
- GET /attachments/{entity}/{external_id}
- POST /attachments/{entity}/{external_id}
- GET /attachments/{external_id}

### Invoices & Payments
- POST /entity/invoice
- POST /entity/payment
- POST /entity/adhoc-invoice
- POST /entity/adhoc-payment

### Reports
- GET /report/tenant/balances
- GET /report/tenant/statement
- GET /report/agency/income
- GET /report/processing-summary

### Tags
- GET /tags
- POST /tags
- POST /tags/entities/{entity_type}/{entity_id}

### Transactions
- POST /transactions/credit-note
- POST /transactions/debit-note
- GET /transactions/frequencies

## Webhook Events
Events sent to webhook URL:
- `create|update|delete|restore` for `beneficiary|property|tenant|payment|invoice_rule|maintenance_ticket`
- Event payload includes agency info and data changes

## Error Responses
All endpoints return standard error format:
```json
{
  "errors": [{"message": "Error description"}],
  "status": 400|401|403|404|500|501
}
```

## Rate Limiting
- Max 5 requests per second
- Exceeding limit blocks all requests for 30 seconds
- Returns HTTP 429 with "Too many requests" message

## Pagination
Standard pagination for list endpoints:
```json
{
  "pagination": {
    "page": 1,
    "rows": 10,
    "total_rows": 100,
    "total_pages": 10
  }
}
```

## ID Types Reference
GET /id-types returns available ID types for entities

## Global Beneficiaries
GET /global-beneficiaries returns pre-configured beneficiaries like utilities, councils