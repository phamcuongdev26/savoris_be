-- B1.3: Remove inventory, purchasing, shifts, promotions, loyalty, customer modules
-- and CMS tables that are no longer used (offer, accolade, testimonial, menu_file).

-- Step 1: drop columns from tables we keep
ALTER TABLE orders
    DROP COLUMN IF EXISTS discount_amount,
    DROP COLUMN IF EXISTS points_used,
    DROP COLUMN IF EXISTS customer_id,
    DROP COLUMN IF EXISTS promotion_id;

ALTER TABLE reservations
    DROP COLUMN IF EXISTS customer_id;

-- Step 2: drop removed module tables (CASCADE removes any remaining FK references)
DROP TABLE IF EXISTS point_transactions CASCADE;
DROP TABLE IF EXISTS promotion_branches CASCADE;
DROP TABLE IF EXISTS promotions CASCADE;
DROP TABLE IF EXISTS customers CASCADE;
DROP TABLE IF EXISTS stock_count_items CASCADE;
DROP TABLE IF EXISTS stock_counts CASCADE;
DROP TABLE IF EXISTS stock_movements CASCADE;
DROP TABLE IF EXISTS purchase_order_items CASCADE;
DROP TABLE IF EXISTS purchase_orders CASCADE;
DROP TABLE IF EXISTS recipes CASCADE;
DROP TABLE IF EXISTS branch_inventory CASCADE;
DROP TABLE IF EXISTS ingredients CASCADE;
DROP TABLE IF EXISTS staff_shifts CASCADE;
DROP TABLE IF EXISTS shifts CASCADE;
DROP TABLE IF EXISTS suppliers CASCADE;
DROP TABLE IF EXISTS offer CASCADE;
DROP TABLE IF EXISTS accolade CASCADE;
DROP TABLE IF EXISTS testimonial CASCADE;
DROP TABLE IF EXISTS menu_file CASCADE;
