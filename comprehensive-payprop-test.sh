#!/bin/bash

# Comprehensive PayProp Emergency Fix Testing Script
echo "🚨 PayProp Emergency Fix - Comprehensive Testing Suite"
echo "====================================================="

# Base URL - adjust if needed
BASE_URL="http://localhost:8080"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to log test results
log_test() {
    echo -e "${BLUE}📋 $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️ $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Function to check HTTP status
check_status() {
    if [[ "$1" == "200" ]]; then
        log_success "HTTP Status: $1 (Success)"
        return 0
    elif [[ "$1" == "500" ]]; then
        log_error "HTTP Status: $1 (Server Error)"
        return 1
    elif [[ "$1" == "401" ]]; then
        log_error "HTTP Status: $1 (Unauthorized - Check authentication)"
        return 1
    elif [[ "$1" == "403" ]]; then
        log_error "HTTP Status: $1 (Forbidden - Check user permissions)"
        return 1
    else
        log_warning "HTTP Status: $1 (Unexpected)"
        return 1
    fi
}

# Function to extract JSON field
extract_json_field() {
    echo "$1" | jq -r ".$2 // empty"
}

echo ""
log_test "Phase 1: System Health Check"
echo "-----------------------------"

# Test 1: Health check
log_test "Running comprehensive health check..."
HEALTH_RESPONSE=$(curl -s -w "HTTPSTATUS:%{http_code}" "$BASE_URL/admin/payprop/health")
HEALTH_STATUS=$(echo "$HEALTH_RESPONSE" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
HEALTH_BODY=$(echo "$HEALTH_RESPONSE" | sed 's/HTTPSTATUS:[0-9]*$//')

echo "Health Response:"
echo "$HEALTH_BODY" | jq . 2>/dev/null || echo "$HEALTH_BODY"
check_status "$HEALTH_STATUS"

if [[ "$HEALTH_STATUS" == "200" ]]; then
    HEALTH_STATUS_VALUE=$(extract_json_field "$HEALTH_BODY" "status")
    NEEDS_MIGRATION=$(extract_json_field "$HEALTH_BODY" "needs_migration")
    BROKEN_COUNT=$(extract_json_field "$HEALTH_BODY" "broken_portfolios")
    
    echo ""
    log_test "Health Check Results:"
    echo "  • Overall Status: $HEALTH_STATUS_VALUE"
    echo "  • Needs Migration: $NEEDS_MIGRATION"
    echo "  • Broken Portfolios: $BROKEN_COUNT"
    
    if [[ "$HEALTH_STATUS_VALUE" == "HEALTHY" ]]; then
        log_success "System is healthy - no migration needed"
    else
        log_warning "System needs attention - proceeding with detailed tests"
    fi
fi

echo ""
echo "=================================="
log_test "Phase 2: Migration Analysis"
echo "-----------------------------"

# Test 2: Migration summary
log_test "Getting migration summary..."
MIGRATION_RESPONSE=$(curl -s -w "HTTPSTATUS:%{http_code}" "$BASE_URL/portfolio/internal/payprop/migration/summary")
MIGRATION_STATUS=$(echo "$MIGRATION_RESPONSE" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
MIGRATION_BODY=$(echo "$MIGRATION_RESPONSE" | sed 's/HTTPSTATUS:[0-9]*$//')

echo "Migration Summary:"
echo "$MIGRATION_BODY" | jq . 2>/dev/null || echo "$MIGRATION_BODY"
check_status "$MIGRATION_STATUS"

NEEDS_MIGRATION_DETAILED=""
if [[ "$MIGRATION_STATUS" == "200" ]]; then
    NEEDS_MIGRATION_DETAILED=$(extract_json_field "$MIGRATION_BODY" "needsMigration")
    BROKEN_PORTFOLIOS=$(extract_json_field "$MIGRATION_BODY" "brokenPortfoliosCount")
    PENDING_ASSIGNMENTS=$(extract_json_field "$MIGRATION_BODY" "pendingAssignmentsCount")
    
    echo ""
    log_test "Migration Analysis Results:"
    echo "  • Needs Migration: $NEEDS_MIGRATION_DETAILED"
    echo "  • Broken Portfolios: $BROKEN_PORTFOLIOS"
    echo "  • Pending Assignments: $PENDING_ASSIGNMENTS"
fi

echo ""
echo "=================================="
log_test "Phase 3: Tag Creation Testing"
echo "-----------------------------"

# Test 3: Tag creation
TEST_TAG_NAME="DEBUG-TEST-TAG-$(date +%s)"
log_test "Testing tag creation with name: $TEST_TAG_NAME"

TAG_RESPONSE=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST "$BASE_URL/portfolio/internal/payprop/debug/test-tag-creation" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "tagName=$TEST_TAG_NAME")
TAG_STATUS=$(echo "$TAG_RESPONSE" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
TAG_BODY=$(echo "$TAG_RESPONSE" | sed 's/HTTPSTATUS:[0-9]*$//')

echo "Tag Creation Response:"
echo "$TAG_BODY" | jq . 2>/dev/null || echo "$TAG_BODY"
check_status "$TAG_STATUS"

if [[ "$TAG_STATUS" == "200" ]]; then
    TAG_SUCCESS=$(extract_json_field "$TAG_BODY" "success")
    TAG_ID=$(extract_json_field "$TAG_BODY" "tagId")
    
    if [[ "$TAG_SUCCESS" == "true" && -n "$TAG_ID" && "$TAG_ID" != "null" ]]; then
        log_success "Tag creation successful - Tag ID: $TAG_ID"
        TAG_CREATION_WORKS=true
    else
        log_error "Tag creation failed - No valid tag ID returned"
        TAG_CREATION_WORKS=false
    fi
else
    log_error "Tag creation API call failed"
    TAG_CREATION_WORKS=false
fi

echo ""
echo "=================================="
log_test "Phase 4: Portfolio Creation Testing"
echo "------------------------------------"

# Test 4: Portfolio creation
TEST_PORTFOLIO_NAME="DEBUG-PORTFOLIO-$(date +%s)"
log_test "Testing portfolio creation with name: $TEST_PORTFOLIO_NAME"

PORTFOLIO_RESPONSE=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST "$BASE_URL/portfolio/internal/payprop/debug/test-portfolio-creation" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "portfolioName=$TEST_PORTFOLIO_NAME")
PORTFOLIO_STATUS=$(echo "$PORTFOLIO_RESPONSE" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
PORTFOLIO_BODY=$(echo "$PORTFOLIO_RESPONSE" | sed 's/HTTPSTATUS:[0-9]*$//')

echo "Portfolio Creation Response:"
echo "$PORTFOLIO_BODY" | jq . 2>/dev/null || echo "$PORTFOLIO_BODY"
check_status "$PORTFOLIO_STATUS"

PORTFOLIO_CREATION_WORKS=false
if [[ "$PORTFOLIO_STATUS" == "200" ]]; then
    PORTFOLIO_SUCCESS=$(extract_json_field "$PORTFOLIO_BODY" "success")
    PORTFOLIO_PAY_PROP_TAGS=$(extract_json_field "$PORTFOLIO_BODY" "payPropTags")
    PORTFOLIO_SYNC_STATUS=$(extract_json_field "$PORTFOLIO_BODY" "syncStatus")
    
    echo ""
    log_test "Portfolio Creation Results:"
    echo "  • Success: $PORTFOLIO_SUCCESS"
    echo "  • PayProp Tags: $PORTFOLIO_PAY_PROP_TAGS"
    echo "  • Sync Status: $PORTFOLIO_SYNC_STATUS"
    
    if [[ "$PORTFOLIO_SUCCESS" == "true" && -n "$PORTFOLIO_PAY_PROP_TAGS" && "$PORTFOLIO_PAY_PROP_TAGS" != "null" && "$PORTFOLIO_SYNC_STATUS" == "synced" ]]; then
        log_success "Portfolio creation successful with PayProp sync"
        PORTFOLIO_CREATION_WORKS=true
    else
        log_error "Portfolio creation failed - Missing PayProp external ID or sync failed"
    fi
else
    log_error "Portfolio creation API call failed"
fi

echo ""
echo "=================================="
log_test "Phase 5: Migration Execution (if needed)"
echo "---------------------------------------"

# Test 5: Run migration if needed
if [[ "$NEEDS_MIGRATION_DETAILED" == "true" ]]; then
    log_test "Migration needed - executing migration..."
    
    MIGRATION_EXEC_RESPONSE=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST "$BASE_URL/portfolio/internal/payprop/migration/fix-broken-portfolios")
    MIGRATION_EXEC_STATUS=$(echo "$MIGRATION_EXEC_RESPONSE" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
    MIGRATION_EXEC_BODY=$(echo "$MIGRATION_EXEC_RESPONSE" | sed 's/HTTPSTATUS:[0-9]*$//')
    
    echo "Migration Execution Response:"
    echo "$MIGRATION_EXEC_BODY" | jq . 2>/dev/null || echo "$MIGRATION_EXEC_BODY"
    check_status "$MIGRATION_EXEC_STATUS"
    
    if [[ "$MIGRATION_EXEC_STATUS" == "200" ]]; then
        MIGRATION_SUCCESS=$(extract_json_field "$MIGRATION_EXEC_BODY" "success")
        FIXED_COUNT=$(extract_json_field "$MIGRATION_EXEC_BODY" "fixedCount")
        FAILED_COUNT=$(extract_json_field "$MIGRATION_EXEC_BODY" "failedCount")
        
        echo ""
        log_test "Migration Results:"
        echo "  • Success: $MIGRATION_SUCCESS"
        echo "  • Fixed Count: $FIXED_COUNT"
        echo "  • Failed Count: $FAILED_COUNT"
        
        if [[ "$MIGRATION_SUCCESS" == "true" && "$FAILED_COUNT" == "0" ]]; then
            log_success "Migration completed successfully"
        else
            log_warning "Migration completed with some failures"
        fi
    else
        log_error "Migration execution failed"
    fi
else
    log_success "No migration needed - skipping migration execution"
fi

echo ""
echo "=================================="
log_test "Final Health Check"
echo "----------------"

# Test 6: Final health check
log_test "Running final health check to verify fixes..."
FINAL_HEALTH_RESPONSE=$(curl -s -w "HTTPSTATUS:%{http_code}" "$BASE_URL/admin/payprop/health")
FINAL_HEALTH_STATUS=$(echo "$FINAL_HEALTH_RESPONSE" | grep -o "HTTPSTATUS:[0-9]*" | cut -d: -f2)
FINAL_HEALTH_BODY=$(echo "$FINAL_HEALTH_RESPONSE" | sed 's/HTTPSTATUS:[0-9]*$//')

echo "Final Health Response:"
echo "$FINAL_HEALTH_BODY" | jq . 2>/dev/null || echo "$FINAL_HEALTH_BODY"
check_status "$FINAL_HEALTH_STATUS"

if [[ "$FINAL_HEALTH_STATUS" == "200" ]]; then
    FINAL_STATUS=$(extract_json_field "$FINAL_HEALTH_BODY" "status")
    FINAL_BROKEN_COUNT=$(extract_json_field "$FINAL_HEALTH_BODY" "broken_portfolios")
    
    echo ""
    log_test "Final Health Results:"
    echo "  • Status: $FINAL_STATUS"
    echo "  • Broken Portfolios: $FINAL_BROKEN_COUNT"
    
    if [[ "$FINAL_STATUS" == "HEALTHY" ]]; then
        log_success "System is now healthy!"
    else
        log_warning "System still needs attention"
    fi
fi

echo ""
echo "=================================="
echo "🎯 COMPREHENSIVE TEST SUMMARY"
echo "=================================="

echo ""
log_test "Test Results Summary:"

# Tag Creation Test
if [[ "$TAG_CREATION_WORKS" == "true" ]]; then
    log_success "✅ Tag Creation: WORKING"
else
    log_error "❌ Tag Creation: FAILED"
fi

# Portfolio Creation Test  
if [[ "$PORTFOLIO_CREATION_WORKS" == "true" ]]; then
    log_success "✅ Portfolio Creation: WORKING"
else
    log_error "❌ Portfolio Creation: FAILED"
fi

# Overall Assessment
echo ""
log_test "📋 Root Cause Fix Assessment:"

if [[ "$TAG_CREATION_WORKS" == "true" && "$PORTFOLIO_CREATION_WORKS" == "true" ]]; then
    log_success "🎉 SUCCESS: The root cause has been fixed!"
    log_success "    • PayProp tag creation is working"
    log_success "    • Portfolio creation stores external IDs properly"
    log_success "    • Property assignments should now sync correctly"
    echo ""
    log_test "📝 Next Steps:"
    echo "  1. ✅ Monitor production for successful property assignments"
    echo "  2. ✅ Run migration on any remaining broken portfolios"
    echo "  3. ✅ Implement the portfolio-block system on this stable foundation"
elif [[ "$TAG_CREATION_WORKS" == "true" ]]; then
    log_warning "⚠️ PARTIAL SUCCESS: Tag creation works but portfolio creation failed"
    echo ""
    log_test "📝 Next Steps:"
    echo "  1. 🔍 Investigate portfolio creation transaction issues"
    echo "  2. 🔍 Check database constraints and rollback scenarios"
    echo "  3. 🔧 Fix portfolio creation logic before proceeding"
else
    log_error "❌ FAILURE: Root cause still present"
    echo ""
    log_test "📝 Next Steps:"
    echo "  1. 🔍 Check application logs for specific error details"
    echo "  2. 🔍 Verify PayProp API authentication and connectivity"
    echo "  3. 🔍 Validate API response format and field extraction"
    echo "  4. 🔧 Fix identified issues and re-test"
fi

echo ""
log_test "📊 Database Validation Commands:"
echo ""
echo "# Check for remaining broken portfolios:"
echo "SELECT COUNT(*) FROM portfolios WHERE payprop_tag_names IS NOT NULL AND payprop_tags IS NULL;"
echo ""
echo "# Check for pending assignments:"
echo "SELECT COUNT(*) FROM property_portfolio_assignments WHERE sync_status = 'pending' AND is_active = 1;"
echo ""
echo "# View test portfolios created:"
echo "SELECT id, name, payprop_tags, payprop_tag_names, sync_status FROM portfolios WHERE name LIKE 'DEBUG-%' ORDER BY created_at DESC LIMIT 5;"

echo ""
log_test "🔚 Test completed at $(date)"
echo ""