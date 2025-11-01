#!/bin/bash

# Update insertFromHistoricalTransactions
sed -i '/private int insertFromHistoricalTransactions(String batchId)/,/return jdbcTemplate.update(sql, batchId);/{
/rent_amount_at_transaction, property_name,/a\
                transaction_type, flow_direction,
/p\.property_name,/a\
                CASE\
                    WHEN ht.category LIKE '"'"'%rent%'"'"' OR ht.category LIKE '"'"'%Rent%'"'"' THEN '"'"'rent_received'"'"'\
                    WHEN ht.category LIKE '"'"'%expense%'"'"' OR ht.category LIKE '"'"'%Expense%'"'"' THEN '"'"'expense'"'"'\
                    ELSE '"'"'other'"'"'\
                END as transaction_type,\
                CASE\
                    WHEN ht.category LIKE '"'"'%rent%'"'"' OR ht.category LIKE '"'"'%Rent%'"'"' THEN '"'"'INCOMING'"'"'\
                    ELSE '"'"'OUTGOING'"'"'\
                END as flow_direction,
}' src/main/java/site/easy/to/build/crm/service/transaction/UnifiedTransactionRebuildService.java

echo "Rebuild service updated"
