UPDATE areas
SET floor_name = 'Tầng 2', floor_sort_order = 2
WHERE branch_id = (SELECT id FROM branches WHERE code = 'CN02')
  AND name = 'Sân vườn';
