# PayProp Sync Failure - Complete Diagnosis
**Date:** October 30, 2025, 23:37
**Status:** ROOT CAUSE IDENTIFIED

---

## Summary

Your PayProp import is failing with **two separate issues**:

1. **❌ IMMEDIATE ISSUE:** Sync fails with "No Google OAuth connection found"
   - **FALSE ERROR:** PayProp sync doesn't need Google OAuth at all!
   - **REAL CAUSE:** Controller checking wrong OAuth table

2. **❌ SECONDARY ISSUE:** Maintenance API 403 FORBIDDEN errors
   - **CAUSE:** PayProp account lacks maintenance permissions
   - **IMPACT:** Causes entire sync transaction to rollback
   - **STATUS:** Fixed in local code (not yet deployed)

---

## Issue #1: Wrong OAuth Check (Critical - Blocking Sync)

### What You See
```
[23:37:02] Unified sync failed: No Google OAuth connection found.
Please connect your Google account first.
```

### The Problem

**PayPropMaintenanceController.java:294-300** incorrectly requires Google OAuth:

```java
if (oAuthUser == null) {
    return ResponseEntity.badRequest().body(Map.of(
        "success", false,
        "error", "No Google OAuth connection found. Please connect your Google account first.",
        "timestamp", LocalDateTime.now()
    ));
}
```

**This check is WRONG because:**
- PayProp sync uses **`payprop_oauth_tokens` table**
- Google OAuth is in **`oauth_users` table** (different system!)
- The controller looks for Google OAuth and rejects the request
- PayProp has its own separate OAuth system

### What You Actually Have

**Database shows:**

```
✅ User: sajidkazmi (ID: 62)
✅ Google OAuth: Yes (id: 38) - but expired and not needed
✅ PayProp Token: Yes (id: 33) - ACTIVE and VALID!
   - Expires: 2025-10-31 01:55:51 (valid for 25 more hours)
   - is_active: true
   - Last refreshed: 2025-10-30 01:55:51
```

**You have everything needed for PayProp sync!**

### The Fix

The controller should:
1. Remove the Google OAuth check entirely (it's not needed)
2. Let `PayPropApiClient` handle PayProp authentication automatically
3. `PayPropApiClient` already uses `payprop_oauth_tokens` table correctly

**Quick Fix Option:**
Remove lines 273-300 from `PayPropMaintenanceController.java` and just call the sync directly:

```java
// Run the sync directly - PayPropApiClient handles auth
PayPropSyncOrchestrator.UnifiedSyncResult result =
    syncOrchestrator.performEnhancedUnifiedSyncWithWorkingFinancials(null, userId);
```

---

## Issue #2: Maintenance API Permissions (Causes Rollback)

### What Happened (from Render logs at 22:47)

```
22:47:11.435Z ERROR - PayProp API error: 403 FORBIDDEN
  {"errors":[{"message":"You do not have the necessary permission(s)"}],"status":403}

22:47:11.435Z ERROR - Failed to fetch page 1 from /maintenance/categories
22:47:12.424Z ERROR - Failed to fetch page 1 from /maintenance/tickets
22:47:12.435Z INFO  - Financial Sync: Financial sync failed: Transaction silently rolled back
```

### The Problem

1. PayProp sync calls `/maintenance/categories` and `/maintenance/tickets`
2. Your PayProp account doesn't have maintenance module permissions
3. PayProp returns 403 FORBIDDEN
4. The error causes Spring transaction to mark as "rollback-only"
5. **Entire financial sync fails and rolls back**
6. No data saved, no event published, no automatic rebuild

### Impact Chain
```
Maintenance 403 Error
  ↓
Transaction marked rollback-only
  ↓
Financial sync fails
  ↓
No PayPropDataSyncedEvent published
  ↓
No automatic unified_transactions rebuild
  ↓
Data stays stale (163 ICDN, 106 missing payments)
```

### The Fix (Already Applied Locally)

Modified **`PayPropMaintenanceSyncService.java`** to gracefully handle 403:

```java
try {
    categories = apiClient.fetchAllPages("/maintenance/categories", ...);
} catch (Exception apiError) {
    if (apiError.getMessage().contains("403") || apiError.getMessage().contains("FORBIDDEN")) {
        log.warn("⚠️ Maintenance categories API access denied - skipping");
        return SyncResult.partial("Maintenance skipped - insufficient API permissions",
            Map.of("warning", "403 FORBIDDEN - PayProp account lacks maintenance permissions"));
    }
    throw apiError; // Re-throw other errors
}
```

**Same fix applied to** `importMaintenanceTickets()` method.

**Result:** Maintenance sync fails gracefully without breaking the entire transaction.

---

## Current Status Summary

| Component | Status | Notes |
|-----------|--------|-------|
| **PayProp Token** | ✅ Valid | Expires tomorrow, auto-refreshes |
| **Google OAuth** | ❌ Not needed | Wrong check in controller |
| **Maintenance Fix** | ✅ Fixed locally | Not yet deployed to Render |
| **Event System** | ✅ Deployed | Ready to work once sync succeeds |
| **Unified Data** | ❌ Stale | 163 ICDN, 106 missing payments |

---

## What Needs to Happen

### Immediate (Fixes the "No OAuth" error)

**Option A: Quick Controller Fix**
Remove the Google OAuth check from `PayPropMaintenanceController.java` lines 273-300.

**Option B: Update Controller Logic**
Check for `payprop_oauth_tokens` instead of `oauth_users`:

```java
// Check for active PayProp token
String checkToken = "SELECT COUNT(*) FROM payprop_oauth_tokens WHERE user_id = ? AND is_active = true";
int tokenCount = jdbcTemplate.queryForObject(checkToken, Integer.class, userId);

if (tokenCount == 0) {
    return ResponseEntity.badRequest().body(Map.of(
        "success", false,
        "error", "No active PayProp OAuth connection found. Please authorize PayProp access.",
        "timestamp", LocalDateTime.now()
    ));
}

// Continue with sync - PayPropApiClient will use the token automatically
```

### Next (Deploy the maintenance fix)

1. Compile the maintenance fix changes
2. Commit and push to Render
3. Wait for deploy to complete

### Then (Test the sync)

1. Trigger PayProp sync from admin UI
2. Should complete successfully (maintenance skipped with warning)
3. Event fires → Automatic rebuild triggers
4. Unified data updates correctly

---

## Expected Outcome After Fix

**Sync logs should show:**
```
✅ Properties synced successfully
✅ Owners synced successfully
✅ Tenants synced successfully
✅ Financial sync completed
⚠️ Maintenance categories skipped - insufficient API permissions
⚠️ Maintenance tickets skipped - insufficient API permissions
✅ Comprehensive financial sync completed successfully
📢 Published PayPropDataSyncedEvent for automatic unified rebuild
📥 PayProp data sync detected: COMPREHENSIVE_FINANCIAL_SYNC - X records
🔄 Triggering automatic unified_transactions incremental rebuild
✅ Automatic unified rebuild completed: X records processed
```

**Unified data after rebuild:**
```
SOURCE      DATA_SOURCE         COUNT    TOTAL
--------------------------------------------------
HISTORICAL  N/A                  138     £106,288
PAYPROP     BATCH_PAYMENT        152     £63,594
PAYPROP     COMMISSION_PAYMENT   118     £12,432
PAYPROP     INCOMING_PAYMENT     106     £88,560  ← FIXED (was 0)
--------------------------------------------------
TOTAL                            514     £270,874

ICDN records: 0 (was 163) ← FIXED
```

---

## Files Modified (Not Yet Deployed)

1. **`PayPropMaintenanceSyncService.java`**
   - Added 403 handling to `syncMaintenanceCategories()`
   - Added 403 handling to `importMaintenanceTickets()`
   - Status: Compiled locally, not pushed to Render

2. **`PayPropMaintenanceController.java`** (needs fix)
   - Current: Checks Google OAuth (wrong!)
   - Should: Either remove check or check PayProp tokens
   - Status: Not yet modified

---

## Questions Answered

**Q: "If I run a fresh payprop data import will it just fix things anyway?"**

**A NOW (before fixes deployed):**
- ❌ No - will fail with "No Google OAuth connection found"
- Even if OAuth check removed, would fail on maintenance 403
- No data saved, unified stays stale

**A AFTER fixes deployed:**
- ✅ Yes - sync completes (maintenance skipped with warning)
- Financial data saves successfully
- Event fires → Automatic rebuild
- Unified data becomes current

---

## Recommendation

**Fastest path to working sync:**

1. **Now:** Remove Google OAuth check from controller (5 min fix)
2. **Now:** Compile, commit, push maintenance fix
3. **Wait:** Render deploy (~5 min)
4. **Then:** Run PayProp sync from admin UI
5. **Verify:** Check logs for success + automatic rebuild

**Total time:** ~15-20 minutes to fully working automatic sync

---

**Status:** 🔴 BLOCKED - Needs controller OAuth fix
**Priority:** HIGH - Prevents all PayProp syncs
**Next Step:** Fix PayPropMaintenanceController OAuth check
