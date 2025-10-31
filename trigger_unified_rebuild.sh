#!/bin/bash
# Trigger manual unified_transactions rebuild
# Use this if automatic rebuild isn't working yet

echo "Triggering unified_transactions rebuild..."

# Option A: Production (Render)
curl -X POST https://spoutproperty-hub.onrender.com/api/unified-transactions/rebuild/full

# Option B: Local dev
# curl -X POST http://localhost:8080/api/unified-transactions/rebuild/full

echo ""
echo "Rebuild triggered! Check logs for completion."
echo ""
echo "To verify, run this SQL:"
echo "SELECT source_system, payprop_data_source, COUNT(*) FROM unified_transactions GROUP BY source_system, payprop_data_source;"
