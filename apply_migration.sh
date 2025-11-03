#!/bin/bash

# CRITICAL MIGRATION: Fix production login issue
# This script applies the database migration to fix the missing columns error

set -e  # Exit on error

echo "========================================"
echo "CRITICAL MIGRATION: Fixing Login Issue"
echo "========================================"
echo ""
echo "Issue: Login fails with 'Unknown column billing_period_start_day'"
echo "Fix: Adding missing columns to customers table"
echo ""

# Check if DATABASE_URL is set
if [ -z "$DATABASE_URL" ]; then
    echo "ERROR: DATABASE_URL environment variable is not set"
    echo "Please provide database connection details:"
    read -p "Database Host: " DB_HOST
    read -p "Database Port [3306]: " DB_PORT
    DB_PORT=${DB_PORT:-3306}
    read -p "Database Name: " DB_NAME
    read -p "Database User: " DB_USER
    read -sp "Database Password: " DB_PASSWORD
    echo ""

    MYSQL_CMD="mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASSWORD $DB_NAME"
else
    echo "Using DATABASE_URL from environment"
    # Parse DATABASE_URL (format: mysql://user:pass@host:port/dbname)
    MYSQL_CMD="mysql $DATABASE_URL"
fi

echo ""
echo "Applying migration..."
echo ""

# Run the migration
cat migration_fix_login.sql | $MYSQL_CMD

echo ""
echo "========================================"
echo "✓ MIGRATION COMPLETED SUCCESSFULLY!"
echo "========================================"
echo ""
echo "Login should now work. Test at:"
echo "https://spoutproperty-hub.onrender.com/customer-login"
echo ""
