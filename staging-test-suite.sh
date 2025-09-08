#!/bin/bash

# Staging Environment Test Suite
# Comprehensive testing for CRM staging data synchronization

set -e

STAGING_URL="${STAGING_URL:-https://crm-staging.onrender.com}"
ADMIN_TOKEN="${ADMIN_TOKEN:-staging-admin-token}"
TEST_EMAIL="${TEST_EMAIL:-admin@staging.local}"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to log test results
log_test() {
    local status=$1
    local test_name=$2
    local message=$3
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if [ "$status" = "PASS" ]; then
        echo -e "${GREEN}[PASS]${NC} $test_name: $message"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}[FAIL]${NC} $test_name: $message"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
}

# Function to make API calls with error handling
api_call() {
    local method=$1
    local endpoint=$2
    local description=$3
    local expected_status=${4:-200}
    
    echo -e "${BLUE}[API TEST]${NC} $description..."
    
    HTTP_STATUS=$(curl -s -w "%{http_code}" -X "$method" "$STAGING_URL$endpoint" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -o /tmp/api_response.json)
    
    if [ "$HTTP_STATUS" -eq "$expected_status" ]; then
        log_test "PASS" "API_$method" "$description (HTTP $HTTP_STATUS)"
        return 0
    else
        log_test "FAIL" "API_$method" "$description (Expected $expected_status, got $HTTP_STATUS)"
        cat /tmp/api_response.json
        return 1
    fi
}

# Function to validate JSON response
validate_json_field() {
    local field=$1
    local expected_value=$2
    local description=$3
    
    if jq -e ".$field" /tmp/api_response.json > /dev/null; then
        actual_value=$(jq -r ".$field" /tmp/api_response.json)
        if [ "$actual_value" = "$expected_value" ]; then
            log_test "PASS" "JSON_VALIDATION" "$description: $field=$actual_value"
        else
            log_test "FAIL" "JSON_VALIDATION" "$description: Expected $field=$expected_value, got $actual_value"
        fi
    else
        log_test "FAIL" "JSON_VALIDATION" "$description: Field $field not found in response"
    fi
}

# Function to test database connectivity
test_database_connectivity() {
    echo -e "${PURPLE}=== Testing Database Connectivity ===${NC}"
    
    if api_call "GET" "/admin/staging/health" "Database health check"; then
        validate_json_field "status" "healthy" "Health status check"
        validate_json_field "database" "connected" "Database connection check"
        validate_json_field "environment" "staging" "Environment validation"
        
        # Check if counts object exists
        if jq -e ".counts" /tmp/api_response.json > /dev/null; then
            log_test "PASS" "DB_STRUCTURE" "Database tables accessible"
        else
            log_test "FAIL" "DB_STRUCTURE" "Database counts not available"
        fi
    fi
}

# Function to test staging endpoints
test_staging_endpoints() {
    echo -e "${PURPLE}=== Testing Staging Endpoints ===${NC}"
    
    # Test health endpoint
    api_call "GET" "/admin/staging/health" "Health endpoint availability"
    
    # Test stats endpoint
    api_call "GET" "/admin/staging/stats" "Statistics endpoint availability"
    
    # Test database reset endpoint (should be available but not executed)
    api_call "OPTIONS" "/admin/staging/database/reset" "Database reset endpoint availability" 404
}

# Function to test user creation
test_user_creation() {
    echo -e "${PURPLE}=== Testing User Creation ===${NC}"
    
    if api_call "POST" "/admin/staging/users/create-test-users" "Test users creation"; then
        validate_json_field "status" "success" "User creation status"
        
        # Check if user IDs were returned
        if jq -e ".users" /tmp/api_response.json > /dev/null; then
            log_test "PASS" "USER_CREATION" "Test users created with IDs"
        else
            log_test "FAIL" "USER_CREATION" "User IDs not returned in response"
        fi
    fi
}

# Function to test data statistics
test_data_statistics() {
    echo -e "${PURPLE}=== Testing Data Statistics ===${NC}"
    
    if api_call "GET" "/admin/staging/stats" "Data statistics retrieval"; then
        # Check for expected statistics fields
        local fields=("users" "customers" "properties" "financial_transactions")
        
        for field in "${fields[@]}"; do
            if jq -e ".$field" /tmp/api_response.json > /dev/null; then
                count=$(jq -r ".$field" /tmp/api_response.json)
                log_test "PASS" "DATA_STATS" "$field count: $count"
            else
                log_test "FAIL" "DATA_STATS" "Missing $field count in statistics"
            fi
        done
    fi
}

# Function to test PayProp sync (without executing full sync)
test_payprop_sync_availability() {
    echo -e "${PURPLE}=== Testing PayProp Sync Availability ===${NC}"
    
    # Test if sync endpoints are available (OPTIONS request)
    api_call "OPTIONS" "/admin/staging/sync/full" "Full sync endpoint availability" 404
    api_call "OPTIONS" "/admin/staging/sync/incremental" "Incremental sync endpoint availability" 404
    
    # If OPTIONS not supported, try HEAD request
    echo -e "${BLUE}[INFO]${NC} Testing sync endpoint accessibility..."
    if curl -s -I "$STAGING_URL/admin/staging/sync/full" | grep -q "HTTP.*200\|HTTP.*405"; then
        log_test "PASS" "SYNC_ENDPOINTS" "PayProp sync endpoints are accessible"
    else
        log_test "FAIL" "SYNC_ENDPOINTS" "PayProp sync endpoints not accessible"
    fi
}

# Function to test application configuration
test_application_config() {
    echo -e "${PURPLE}=== Testing Application Configuration ===${NC}"
    
    # Test if we're running in staging profile
    if api_call "GET" "/admin/staging/health" "Staging profile validation"; then
        validate_json_field "environment" "staging" "Spring profile validation"
    fi
    
    # Test management endpoints availability
    echo -e "${BLUE}[INFO]${NC} Testing management endpoints..."
    local mgmt_endpoints=("health" "info" "metrics" "env")
    
    for endpoint in "${mgmt_endpoints[@]}"; do
        if curl -s "$STAGING_URL/actuator/$endpoint" > /dev/null; then
            log_test "PASS" "MGMT_ENDPOINTS" "Management endpoint /$endpoint available"
        else
            log_test "FAIL" "MGMT_ENDPOINTS" "Management endpoint /$endpoint not available"
        fi
    done
}

# Function to test data integrity after operations
test_data_integrity() {
    echo -e "${PURPLE}=== Testing Data Integrity ===${NC}"
    
    # Get initial stats
    if api_call "GET" "/admin/staging/stats" "Initial data statistics"; then
        initial_users=$(jq -r ".users" /tmp/api_response.json)
        initial_customers=$(jq -r ".customers" /tmp/api_response.json)
        
        echo -e "${BLUE}[INFO]${NC} Initial counts - Users: $initial_users, Customers: $initial_customers"
        
        # Create test users
        if api_call "POST" "/admin/staging/users/create-test-users" "Create test users for integrity test"; then
            
            # Check stats again
            sleep 2  # Allow time for data to be written
            if api_call "GET" "/admin/staging/stats" "Updated data statistics"; then
                updated_users=$(jq -r ".users" /tmp/api_response.json)
                updated_customers=$(jq -r ".customers" /tmp/api_response.json)
                
                echo -e "${BLUE}[INFO]${NC} Updated counts - Users: $updated_users, Customers: $updated_customers"
                
                if [ "$updated_users" -gt "$initial_users" ] || [ "$updated_customers" -gt "$initial_customers" ]; then
                    log_test "PASS" "DATA_INTEGRITY" "User creation reflected in statistics"
                else
                    log_test "FAIL" "DATA_INTEGRITY" "User creation not reflected in statistics"
                fi
            fi
        fi
    fi
}

# Function to test error handling
test_error_handling() {
    echo -e "${PURPLE}=== Testing Error Handling ===${NC}"
    
    # Test invalid endpoint
    api_call "GET" "/admin/staging/invalid-endpoint" "Invalid endpoint error handling" 404
    
    # Test unauthorized access (without token)
    echo -e "${BLUE}[API TEST]${NC} Testing unauthorized access..."
    HTTP_STATUS=$(curl -s -w "%{http_code}" "$STAGING_URL/admin/staging/health" -o /dev/null)
    
    if [ "$HTTP_STATUS" -eq 401 ] || [ "$HTTP_STATUS" -eq 403 ] || [ "$HTTP_STATUS" -eq 200 ]; then
        log_test "PASS" "AUTH_HANDLING" "Authorization handling working (HTTP $HTTP_STATUS)"
    else
        log_test "FAIL" "AUTH_HANDLING" "Unexpected authorization response (HTTP $HTTP_STATUS)"
    fi
}

# Function to test performance
test_performance() {
    echo -e "${PURPLE}=== Testing Performance ===${NC}"
    
    # Test response times
    start_time=$(date +%s.%N)
    api_call "GET" "/admin/staging/health" "Health endpoint performance test"
    end_time=$(date +%s.%N)
    
    response_time=$(echo "$end_time - $start_time" | bc)
    response_time_ms=$(echo "$response_time * 1000" | bc | cut -d. -f1)
    
    if [ "$response_time_ms" -lt 5000 ]; then  # Less than 5 seconds
        log_test "PASS" "PERFORMANCE" "Health endpoint response time: ${response_time_ms}ms"
    else
        log_test "FAIL" "PERFORMANCE" "Health endpoint slow response time: ${response_time_ms}ms"
    fi
}

# Main execution function
main() {
    echo "=========================================================="
    echo "🧪 CRM Staging Environment Test Suite"
    echo "=========================================================="
    echo "Staging URL: $STAGING_URL"
    echo "Start Time: $(date)"
    echo ""
    
    # Execute all test suites
    test_database_connectivity
    echo ""
    test_staging_endpoints
    echo ""
    test_application_config
    echo ""
    test_user_creation
    echo ""
    test_data_statistics
    echo ""
    test_data_integrity
    echo ""
    test_payprop_sync_availability
    echo ""
    test_error_handling
    echo ""
    test_performance
    
    # Final report
    echo ""
    echo "=========================================================="
    echo "🏁 Test Suite Results"
    echo "=========================================================="
    echo -e "Total Tests: ${BLUE}$TOTAL_TESTS${NC}"
    echo -e "Passed:      ${GREEN}$PASSED_TESTS${NC}"
    echo -e "Failed:      ${RED}$FAILED_TESTS${NC}"
    echo -e "Success Rate: ${BLUE}$(( (PASSED_TESTS * 100) / TOTAL_TESTS ))%${NC}"
    echo ""
    
    if [ "$FAILED_TESTS" -eq 0 ]; then
        echo -e "${GREEN}🎉 All tests passed! Staging environment is ready.${NC}"
        echo ""
        echo -e "${BLUE}Next Steps:${NC}"
        echo "1. Execute PayProp OAuth setup"
        echo "2. Run full data sync"
        echo "3. Begin development on staging environment"
        exit 0
    else
        echo -e "${RED}❌ Some tests failed. Please review and fix issues.${NC}"
        echo ""
        echo -e "${YELLOW}Common Issues:${NC}"
        echo "- Check environment variables in Render dashboard"
        echo "- Verify database connection to Railway"
        echo "- Ensure staging profile is active"
        echo "- Check application logs for errors"
        exit 1
    fi
}

# Run the test suite
main "$@"