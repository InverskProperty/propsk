#!/bin/bash

# Test script for PayProp tag creation debugging
echo "🧪 Testing PayProp Tag Creation Fix"
echo "=================================="

# Base URL - adjust if needed
BASE_URL="http://localhost:8080"

# Test 1: Simple tag creation test
echo "📝 Test 1: Testing basic tag creation..."
curl -X POST "$BASE_URL/portfolio/internal/payprop/debug/test-tag-creation" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "tagName=DEBUG-TEST-TAG-$(date +%s)" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s | jq .

echo ""
echo "=================================="

# Test 2: Portfolio creation test
echo "📝 Test 2: Testing portfolio creation with PayProp sync..."
curl -X POST "$BASE_URL/portfolio/internal/payprop/debug/test-portfolio-creation" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "portfolioName=DEBUG-PORTFOLIO-$(date +%s)" \
  -w "\nHTTP Status: %{http_code}\n" \
  -s | jq .

echo ""
echo "✅ Testing complete!"
echo ""
echo "📋 Next steps:"
echo "1. Check the application logs for detailed debug output"
echo "2. Look for any authentication or API errors"
echo "3. Verify PayProp external IDs are being stored properly"