-- V6: Add display_order to property_block_assignments table
-- Purpose: Enable ordering of properties within blocks for better organization

-- Add display_order column to property_block_assignments
ALTER TABLE property_block_assignments
ADD COLUMN display_order INT DEFAULT 0
AFTER is_active;

-- Create index for efficient ordering queries
CREATE INDEX idx_block_assignment_order
ON property_block_assignments(block_id, is_active, display_order);

-- Update existing records to have sequential display_order based on property_id
-- This gives a sensible default order for existing assignments
SET @row_number = 0;
SET @current_block = NULL;

UPDATE property_block_assignments pba
JOIN (
    SELECT
        id,
        block_id,
        @row_number := IF(@current_block = block_id, @row_number + 1, 1) AS new_order,
        @current_block := block_id
    FROM property_block_assignments
    ORDER BY block_id, property_id
) AS numbered ON pba.id = numbered.id
SET pba.display_order = numbered.new_order;
