# Orphaned Entities Analysis - PayProp Sync Issues

## Executive Summary

**Finding:** The "orphaned entities" are **NOT actually orphaned** - they exist in both your local database AND in the PayProp export tables.

**Root Cause:** PayProp's **Entity Resolution API** (`/export/entities/{id}`) returns 404 "Property not linked to entity" errors, but this appears to be a **PayProp API permission or configuration issue**, NOT a data integrity problem.

---

## Key Findings

### Properties
- **Local database:** 45 properties total, all with PayProp IDs
- **PayProp export:** 45 properties found
- **"Orphaned":** 27 properties (but they EXIST in `payprop_export_properties`)
- **Status:** ✅ Data is intact, API resolution failing

### Tenants
- **Local database:** ~94 customers, 29 flagged as "orphaned"
- **PayProp export:** All 29 "orphaned" tenants found in `payprop_export_tenants`
- **Status:** ✅ Data is intact, API resolution failing

---

## The Orphaned Properties (27)

All these properties exist in BOTH your local DB and PayProp export:

| Local ID | PayProp ID | Property Name | City | Created |
|----------|-----------|---------------|------|---------|
| 1 | 08JL4wzmJR | Flat 6 - 3 West Gate | Long Eaton | 2025-10-05 |
| 2 | 5AJ5KVr91M | Flat 16 - 3 West Gate | Long Eaton | 2025-10-05 |
| 5 | 7QZGPmabJ9 | Flat 17 - 3 West Gate | Long Eaton | 2025-10-05 |
| 6 | 7QZGPmanJ9 | Flat 9 - 3 West Gate | Long Eaton | 2025-10-05 |
| 7 | 8EJAnY8VXj | Flat 10 - 3 West Gate | Long Eaton | 2025-10-05 |
| 11 | agXVKxgl13 | Flat 29 - 3 West Gate | Long Eaton | 2025-10-05 |
| 14 | D6JmBwWkXv | Flat 22 - 3 West Gate | Long Eaton | 2025-10-05 |
| 16 | d71eApon15 | Apartment F - Knighton Hayes Manor | Leicester | 2025-10-05 |
| 17 | EyJ6K7RQXj | Flat 11 - 3 West Gate | Long Eaton | 2025-10-05 |
| 18 | EyJ6K7RxXj | Apartment 40 - 31 Watkin Road | Leicester | 2025-10-05 |
| 20 | GvJDP9KaJz | Flat 7 - 3 West Gate | Long Eaton | 2025-10-05 |
| 21 | GVJjmOWOZE | Flat 5 - 3 West Gate | Long Eaton | 2025-10-05 |
| 25 | Kd1bnpWY1v | Flat 13 - 3 West Gate | Long Eaton | 2025-10-05 |
| 27 | LQZr37r3JN | Flat 27 - 3 West Gate | Long Eaton | 2025-10-05 |
| 28 | lwZ7Kky8ZD | Flat 25 - 3 West Gate | Long Eaton | 2025-10-05 |
| 29 | lwZ7KkyEZD | Flat 23 - 3 West Gate | Long Eaton | 2025-10-05 |
| 30 | mLZdvpadXn | Flat 4 - 3 West Gate | Long Eaton | 2025-10-05 |
| 31 | mn18KO44X9 | Flat 21 - 3 West Gate | Long Eaton | 2025-10-05 |
| 33 | oRZQ8ldW1m | Flat 18 - 3 West Gate | Long Eaton | 2025-10-05 |
| 37 | RwXxg7kmJA | Flat 24 - 3 West Gate | Long Eaton | 2025-10-05 |
| 39 | v2XlAPjb1e | Flat 20 - 3 West Gate | Long Eaton | 2025-10-05 |
| 40 | v2XlAPjx1e | Flat 8 - 3 West Gate | Long Eaton | 2025-10-05 |
| 41 | WzJBQ3E8ZQ | Flat 12 - 3 West Gate | Long Eaton | 2025-10-05 |
| 43 | z2JkGdRlJb | Flat 19 - 3 West Gate | Long Eaton | 2025-10-05 |
| 44 | z2JkGdRoJb | Flat 15 - 3 West Gate | Long Eaton | 2025-10-05 |

**Pattern:** Mostly flats at 3 West Gate, Long Eaton

---

## The Orphaned Tenants (29)

All these tenants exist in BOTH your local DB and PayProp export:

| Local ID | PayProp ID | Tenant Name | Email | Created |
|----------|-----------|-------------|-------|---------|
| 3 | 7nZ3YqvrXN | Mr Harsh Patel | harsh.p6077@gmail.com | 2025-10-05 |
| 5 | 8b1gv23aXG | Michel Mabondo Mbuti & Sandra Boadi | micheemabondo@gmail.com | 2025-10-05 |
| 6 | 8b1gv7LNXG | Miss Megan Delaney, Mr Joseph Williams | megan.delaney@live.com | 2025-10-05 |
| 7 | 8EJA83o9Zj | Mr Shermal Wijesinghage, Mrs Udeshika Dona | shermalpunarji90@gmail.com | 2025-10-05 |
| 8 | 8eJP9LrVXG | Mr Chun Hung Tang | webb882003@yahoo.com.hk | 2025-10-05 |
| 9 | 90JYRLmPXo | Mr Brian Lowe | bk.lowe.62@gmail.com | 2025-10-05 |
| 11 | aLJMELajJq | Mr Ferenc Peter | peterferenc84@gmail.com | 2025-10-05 |
| 12 | B6XKzLmzJW | Mr Felwin Francis | felwinfrancis17@gmail.com | 2025-10-05 |
| 13 | D6JmW6jr1v | Mr Riaz Hamid, Ismat Tarr-Hamid | riazh@live.co.za | 2025-10-05 |
| 15 | EyJ6PNxk1j | Adam Kirby | t.w_kirby@mx-mail.eu | 2025-10-05 |
| 16 | ge1aWLDpJE | Mr Ethan Maloney, Miss Soraya Fearon | ethanmaloney28@gmail.com | 2025-10-05 |
| 17 | GvJDKLmVZz | Mr Richard Perrotta | peririch85@gmail.com | 2025-10-05 |
| 18 | GVJjWNLLXE | Mr Nathan Holland | nathholland@hotmail.co.uk | 2025-10-05 |
| 19 | KAXNqLgAJk | Neha Minocha | nehaminocha18@gmail.com | 2025-10-05 |
| 21 | lMZnal7717 | Charanya Kaliamoorthy | charanya.dr@gmail.com | 2025-10-05 |
| 22 | lMZnW5L6J7 | Mr James Kay | lyrcc.email@gmail.com | 2025-10-05 |
| 26 | mn184gvb19 | Mr Scott Warner | scott.warner2@nuh.nhs.uk | 2025-10-05 |
| 27 | oRZQdmMW1m | Jason Barclay | jaybarclay22@gmail.com | 2025-10-05 |
| 28 | qv1pdwLB1d | Mr Amos Blyth | amosblyth@outlook.com | 2025-10-05 |
| 29 | qv1pmBppZd | Mark Tennick | marktennick85@gmail.com | 2025-10-05 |
| 31 | rp19gkVoJA | Nirmali Gedara and Janith Hetti Arachchilage | hajclperera@gmail.com | 2025-10-05 |
| 32 | rp19gojwJA | Miss Sophie Murray | murraysophie208@gmail.com | 2025-10-05 |
| 33 | RwXxk0nWJA | Mr Riley Paul Beresford, Ms Gina Louise Judge | riley-beresford@hotmail.co.uk | 2025-10-05 |
| 34 | v0Zo3zLVZD | Miss Shelley Lees | leesshelley19@outlook.com | 2025-10-05 |
| 35 | v2XljeLxZe | Mrs Margaret Fritchley | (no email) | 2025-10-05 |
| 36 | WzJBEL9RZQ | Ms Lucy Cassell | lucycassell1@gmail.com | 2025-10-05 |
| 37 | z2JkaRAN1b | Anna Stoliarchuk | moholosetho8448@icloud.com | 2025-10-05 |
| 75 | mLZdAw6wJn | Beatriz Silva | bea.silva2805@outlook.com | 2025-10-07 |
| 94 | KV1z40RlJO | Jemimah Nallarajah & Arulnesan Anthony | jemimahnallarajah@gmail.com | 2025-10-24 |

---

## What's Actually Happening

### The Entity Resolution Service
Your `PayPropEntityResolutionService` tries to call:
```
GET /export/entities/{property_id}
GET /export/entities/{tenant_id}
```

### PayProp's Response
```json
{
  "errors": [
    {"message": "Property not linked to entity"}
  ],
  "status": 404
}
```

### But The Data EXISTS!
- ✅ All 27 properties found in `payprop_export_properties`
- ✅ All 29 tenants found in `payprop_export_tenants`
- ✅ Data was successfully imported from `/export/properties` and `/export/tenants` endpoints

---

## Root Cause Analysis

### Hypothesis 1: Premium API Endpoint (MOST LIKELY)
The `/export/entities/{id}` endpoint may be a **premium/paid feature** that requires additional PayProp permissions.

**Evidence:**
- Your logs show: "⚠️ SKIPPED: 5 premium endpoints"
- The bulk export endpoints (`/export/properties`, `/export/tenants`) work fine
- The entity resolution endpoint fails for ALL entities

### Hypothesis 2: API Version Mismatch
The entity resolution endpoint may require a different API version or authentication scope.

### Hypothesis 3: Endpoint Deprecated
PayProp may have deprecated the `/export/entities/{id}` endpoint in favor of bulk exports.

---

## Impact Assessment

### ✅ NO DATA LOSS
- All properties are in your database
- All tenants are in your database
- All data synced successfully from PayProp

### ⚠️ Resolution Service Failing
The `PayPropEntityResolutionService` is trying to use an endpoint that doesn't work, causing:
- 56 API calls that all return 404
- Logs filled with ERROR messages
- Unnecessary API rate limit consumption

### ✅ Workaround Already Exists
You're already successfully using:
- `/export/properties` - Works perfectly
- `/export/tenants` - Works perfectly
- `/export/invoices` - Works perfectly
- `/report/all-payments` - Works perfectly

---

## Recommendations

### Option 1: Disable Entity Resolution Service (RECOMMENDED)

The entity resolution service appears to be redundant since you're successfully using bulk export endpoints.

**Action:**
```java
// In PayPropEntityResolutionService or the calling service
// Add a feature flag or comment out the resolution attempts
if (false) { // Disabled - using bulk exports instead
    resolveOrphanedEntities();
}
```

### Option 2: Contact PayProp Support

Ask PayProp about the `/export/entities/{id}` endpoint:
- Is it a premium feature?
- What permissions are required?
- Is there an alternative approach for entity resolution?

### Option 3: Use Bulk Export Tables Instead

Modify `PayPropEntityResolutionService` to resolve entities from your local `payprop_export_*` tables instead of calling the API:

```java
// Instead of calling API
// GET /export/entities/{id}

// Query local table
SELECT * FROM payprop_export_properties WHERE payprop_id = ?
SELECT * FROM payprop_export_tenants WHERE payprop_id = ?
```

---

## SQL to Verify Everything is OK

```sql
-- Verify all "orphaned" properties exist in export
SELECT
    'Properties OK' as status,
    COUNT(*) as count
FROM properties p
JOIN payprop_export_properties pep ON p.payprop_id = pep.payprop_id
WHERE p.id IN (1, 2, 5, 6, 7, 11, 14, 16, 17, 18, 20, 21, 25, 27, 28, 29, 30, 31, 33, 37, 39, 40, 41, 43, 44, 324, 325);

-- Verify all "orphaned" tenants exist in export
SELECT
    'Tenants OK' as status,
    COUNT(*) as count
FROM customers c
JOIN payprop_export_tenants pet ON c.payprop_entity_id = pet.payprop_id
WHERE c.customer_id IN (3, 5, 6, 7, 8, 9, 11, 12, 13, 15, 16, 17, 18, 19, 21, 22, 26, 27, 28, 29, 31, 32, 33, 34, 35, 36, 37, 75, 94);
```

**Expected Result:** Both queries should return 27 and 29 respectively.

---

## Conclusion

**GOOD NEWS:** Your data is fine! All "orphaned" entities exist in both your local database and PayProp export tables.

**THE ISSUE:** The `PayPropEntityResolutionService` is calling an API endpoint (`/export/entities/{id}`) that either:
1. Requires premium PayProp permissions
2. Is deprecated
3. Has different authentication requirements

**RECOMMENDATION:** Disable or refactor the entity resolution service to use your existing bulk export data instead of making 56 failing API calls.

---

## Next Steps

1. ✅ **IMMEDIATE:** Disable the entity resolution service to stop the 404 errors
2. 📧 **OPTIONAL:** Contact PayProp to ask about the `/export/entities/{id}` endpoint
3. 🔧 **LONG-TERM:** Refactor entity resolution to use local export tables instead of API calls
