#!/usr/bin/env python3
"""
Generate SQL import script from full_transactions.csv
"""
import csv
import sys

def escape_sql_string(value):
    """Escape single quotes in SQL strings"""
    if value is None:
        return 'NULL'
    return "'" + str(value).replace("'", "''") + "'"

def map_transaction_type(csv_type):
    """Map CSV transaction types to our system's types"""
    mapping = {
        'deposit': 'CREDIT',
        'fee': 'DEBIT',
        'payment': 'DEBIT',
        'expense': 'DEBIT'
    }
    return mapping.get(csv_type.lower(), csv_type.upper())

def generate_sql():
    csv_file = 'C:\\Users\\sajid\\crecrm\\full_transactions.csv'
    sql_file = 'C:\\Users\\sajid\\crecrm\\import_all_transactions.sql'

    with open(csv_file, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        next(reader)  # Skip header

        with open(sql_file, 'w', encoding='utf-8') as sql_out:
            sql_out.write("""-- Import full transaction dataset into financial_transactions table
USE crm_db;

-- Clear existing historical imports
DELETE FROM financial_transactions WHERE data_source = 'HISTORICAL_IMPORT';

-- Insert all transactions from full_transactions.csv
INSERT INTO financial_transactions (
    transaction_date, amount, description, transaction_type,
    property_name, tenant_name, category_name, reference,
    data_source, is_actual_transaction, created_at, updated_at
) VALUES
""")

            rows = list(reader)
            total_rows = len(rows)

            for i, row in enumerate(rows):
                if len(row) < 10:
                    continue

                transaction_date = row[0].strip()
                amount = row[1].strip()
                description = row[2].strip().strip('"')
                transaction_type = map_transaction_type(row[3].strip())
                category = row[4].strip()
                property_reference = row[5].strip().strip('"')
                customer_reference = row[6].strip().strip('"')
                bank_reference = row[7].strip().strip('"')
                payment_method = row[8].strip()
                notes = row[9].strip()

                # Handle empty fields
                property_name = escape_sql_string(property_reference if property_reference else None)
                tenant_name = escape_sql_string(customer_reference if customer_reference else None)

                sql_line = f"({escape_sql_string(transaction_date)}, {amount}, {escape_sql_string(description)}, {escape_sql_string(transaction_type)}, {property_name}, {tenant_name}, {escape_sql_string(category)}, {escape_sql_string(bank_reference)}, 'HISTORICAL_IMPORT', 1, NOW(), NOW())"

                if i < total_rows - 1:
                    sql_line += ","
                else:
                    sql_line += ";"

                sql_out.write(sql_line + "\n")

            sql_out.write("\n-- Verify import\nSELECT COUNT(*) as total_imported FROM financial_transactions WHERE data_source = 'HISTORICAL_IMPORT';\n")
            sql_out.write("SELECT transaction_date, COUNT(*) as count FROM financial_transactions WHERE data_source = 'HISTORICAL_IMPORT' GROUP BY transaction_date ORDER BY transaction_date;\n")

    print(f"✅ Generated SQL import script: {sql_file}")
    print(f"📊 Will import {total_rows} transactions")

if __name__ == '__main__':
    generate_sql()