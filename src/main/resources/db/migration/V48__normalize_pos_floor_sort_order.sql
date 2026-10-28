UPDATE areas
SET floor_sort_order = CASE
    WHEN floor_name ~ '^Tầng [0-9]+' THEN substring(floor_name from '[0-9]+')::integer
    WHEN floor_name = 'Sân vườn' AND branch_id = (SELECT id FROM branches WHERE code = 'CN02') THEN 2
    WHEN floor_name = 'Sân vườn' AND branch_id = (SELECT id FROM branches WHERE code = 'CN03') THEN 3
    ELSE floor_sort_order
END;
