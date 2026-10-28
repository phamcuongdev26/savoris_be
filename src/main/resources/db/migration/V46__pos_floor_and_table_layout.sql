ALTER TABLE areas
    ADD COLUMN floor_name varchar(100),
    ADD COLUMN floor_sort_order integer;

UPDATE areas
SET floor_name = CASE
        WHEN name ILIKE 'tầng %' THEN name
        WHEN name ILIKE '%sân vườn%' AND branch_id = (SELECT id FROM branches WHERE code = 'CN02') THEN 'Sân vườn'
        WHEN name ILIKE '%ngoài hiên%' AND branch_id = (SELECT id FROM branches WHERE code = 'CN04') THEN 'Tầng 2'
        WHEN name ILIKE '%trong nhà%' AND branch_id = (SELECT id FROM branches WHERE code = 'CN04') THEN 'Tầng 1'
        WHEN name ILIKE '%sân vườn%' THEN 'Sân vườn'
        ELSE 'Tầng 1'
    END,
    floor_sort_order = CASE
        WHEN name ~ '[0-9]+' THEN substring(name from '[0-9]+')::integer
        WHEN name ILIKE '%sân vườn%' THEN 3
        ELSE 1
    END;

ALTER TABLE areas ALTER COLUMN floor_name SET NOT NULL;
ALTER TABLE areas ALTER COLUMN floor_sort_order SET NOT NULL;
ALTER TABLE areas ALTER COLUMN floor_sort_order SET DEFAULT 1;

ALTER TABLE dining_tables
    ADD COLUMN position_x integer,
    ADD COLUMN position_y integer,
    ADD COLUMN sort_order integer;

WITH ordered AS (
    SELECT id, row_number() OVER (PARTITION BY area_id ORDER BY name, id) - 1 AS idx
    FROM dining_tables
)
UPDATE dining_tables t
SET position_x = ordered.idx % 4,
    position_y = ordered.idx / 4,
    sort_order = ordered.idx
FROM ordered
WHERE ordered.id = t.id;

ALTER TABLE dining_tables ALTER COLUMN position_x SET NOT NULL;
ALTER TABLE dining_tables ALTER COLUMN position_y SET NOT NULL;
ALTER TABLE dining_tables ALTER COLUMN sort_order SET NOT NULL;
ALTER TABLE dining_tables ALTER COLUMN position_x SET DEFAULT 0;
ALTER TABLE dining_tables ALTER COLUMN position_y SET DEFAULT 0;
ALTER TABLE dining_tables ALTER COLUMN sort_order SET DEFAULT 0;
