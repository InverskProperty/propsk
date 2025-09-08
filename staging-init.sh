#!/bin/bash

# Staging Environment Initialization Script
# Run this after deploying to staging environment

set -e  # Exit on any error

STAGING_URL="${STAGING_URL:-https://crm-staging.onrender.com}"
ADMIN_TOKEN="${ADMIN_TOKEN:-staging-token}"

echo "🚀 Initializing Staging Environment"
echo "   Staging URL: $STAGING_URL"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to make API calls with error handling
make_api_call() {
    local method=$1
    local endpoint=$2
    local description=$3
    
    echo -e "${BLUE}[INFO]${NC} $description..."
    
    if curl -s -X "$method" "$STAGING_URL$endpoint" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $ADMIN_TOKEN" > /tmp/api_response.json; then
        
        if grep -q '"status":"success"' /tmp/api_response.json; then
            echo -e "${GREEN}[SUCCESS]${NC} $description completed"
            return 0
        else
            echo -e "${RED}[ERROR]${NC} $description failed"
            cat /tmp/api_response.json
            return 1
        fi
    else
        echo -e "${RED}[ERROR]${NC} Failed to connect to $endpoint"
        return 1
    fi
}

# Function to check staging health
check_health() {
    echo -e "${BLUE}[HEALTH CHECK]${NC} Checking staging environment..."
    
    if curl -s "$STAGING_URL/admin/staging/health" > /tmp/health.json; then
        if grep -q '"status":"healthy"' /tmp/health.json; then
            echo -e "${GREEN}[HEALTHY]${NC} Staging environment is ready"
            
            # Display current data counts
            echo -e "${YELLOW}[DATA COUNTS]${NC}"
            cat /tmp/health.json | grep -o '"counts":{[^}]*}' | \
            sed 's/.*"counts":{\([^}]*\)}.*/\1/' | \
            sed 's/,/\n/g' | \
            sed 's/"//g' | \
            sed 's/:/: /'
            
            return 0
        else
            echo -e "${RED}[UNHEALTHY]${NC} Staging environment has issues"
            cat /tmp/health.json
            return 1
        fi
    else
        echo -e "${RED}[ERROR]${NC} Cannot connect to staging environment"
        return 1
    fi
}

# Function to setup initial data
setup_initial_data() {
    echo -e "${YELLOW}[SETUP]${NC} Setting up initial staging data..."
    
    # Reset database
    make_api_call "POST" "/admin/staging/database/reset" "Database reset"
    
    # Wait for reset to complete
    sleep 5
    
    # Create test users
    make_api_call "POST" "/admin/staging/users/create-test-users" "Test users creation"
    
    # Execute full PayProp sync
    echo -e "${BLUE}[SYNC]${NC} Starting full PayProp data sync (this may take several minutes)..."
    if curl -s -X POST "$STAGING_URL/admin/staging/sync/full" \
            -H "Authorization: Bearer $ADMIN_TOKEN" > /tmp/sync_response.json; then
        
        if grep -q '"status":"success"' /tmp/sync_response.json; then
            echo -e "${GREEN}[SUCCESS]${NC} PayProp sync completed"
            
            # Show sync statistics
            echo -e "${YELLOW}[STATISTICS]${NC}"
            cat /tmp/sync_response.json | grep -o '"statistics":{[^}]*}' | \
            sed 's/.*"statistics":{\([^}]*\)}.*/\1/' | \
            sed 's/,/\n/g' | \
            sed 's/"//g' | \
            sed 's/:/: /'
        else
            echo -e "${RED}[ERROR]${NC} PayProp sync failed"
            cat /tmp/sync_response.json
            return 1
        fi
    else
        echo -e "${RED}[ERROR]${NC} Failed to start PayProp sync"
        return 1
    fi
}

# Function to display staging info
display_staging_info() {
    echo -e "${GREEN}[STAGING READY]${NC} Your staging environment is ready!"
    echo ""
    echo -e "${BLUE}Staging URLs:${NC}"
    echo "  App:           $STAGING_URL"
    echo "  Admin Panel:   $STAGING_URL/admin/staging/health"
    echo "  API Docs:      $STAGING_URL/swagger-ui.html"
    echo ""
    echo -e "${BLUE}Test Accounts:${NC}"
    echo "  Admin:         admin@staging.local"
    echo "  Property Owner: owner@staging.local"  
    echo "  Tenant:        tenant@staging.local"
    echo ""
    echo -e "${BLUE}Development Commands:${NC}"
    echo "  Health Check:       curl $STAGING_URL/admin/staging/health"
    echo "  Data Stats:         curl $STAGING_URL/admin/staging/stats"
    echo "  Incremental Sync:   curl -X POST $STAGING_URL/admin/staging/sync/incremental"
    echo "  Reset Database:     curl -X POST $STAGING_URL/admin/staging/database/reset"
    echo ""
    echo -e "${YELLOW}[TIP]${NC} Save these commands in your development workflow!"
}

# Main execution flow
main() {
    echo "=================================================="
    echo "🏗️  CRM Staging Environment Initialization"
    echo "=================================================="
    
    # Step 1: Health check
    if ! check_health; then
        echo -e "${RED}[ABORT]${NC} Staging environment is not healthy. Please check deployment."
        exit 1
    fi
    
    # Step 2: Ask user if they want to setup initial data
    echo ""
    read -p "Do you want to setup initial data (reset DB + PayProp sync)? (y/N): " -n 1 -r
    echo ""
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        setup_initial_data
    else
        echo -e "${YELLOW}[SKIP]${NC} Skipping initial data setup"
    fi
    
    # Step 3: Final health check and display info
    echo ""
    if check_health; then
        display_staging_info
    else
        echo -e "${RED}[WARNING]${NC} Staging setup completed but health check failed"
        exit 1
    fi
    
    echo ""
    echo -e "${GREEN}[COMPLETE]${NC} Staging environment initialization finished!"
}

# Run main function
main "$@"