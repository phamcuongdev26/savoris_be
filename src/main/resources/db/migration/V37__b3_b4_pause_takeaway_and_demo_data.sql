-- B3: manual takeaway pause flag per branch
ALTER TABLE branches ADD COLUMN IF NOT EXISTS takeaway_paused BOOLEAN NOT NULL DEFAULT FALSE;

-- B4: rename existing test categories (no-op on fresh DBs)
UPDATE menu_categories SET name = 'Khai vị',    sort_order = 1 WHERE id = 1;
UPDATE menu_categories SET name = 'Đồ uống',    sort_order = 3 WHERE id = 2;

-- Ensure all 4 categories exist (inserts only what is missing; safe on both fresh and existing DBs)
INSERT INTO menu_categories (name, sort_order, is_active)
  SELECT 'Khai vị',    1, true WHERE NOT EXISTS (SELECT 1 FROM menu_categories WHERE name = 'Khai vị');
INSERT INTO menu_categories (name, sort_order, is_active)
  SELECT 'Đồ uống',    3, true WHERE NOT EXISTS (SELECT 1 FROM menu_categories WHERE name = 'Đồ uống');
INSERT INTO menu_categories (name, sort_order, is_active)
  SELECT 'Món chính',  2, true WHERE NOT EXISTS (SELECT 1 FROM menu_categories WHERE name = 'Món chính');
INSERT INTO menu_categories (name, sort_order, is_active)
  SELECT 'Tráng miệng',4, true WHERE NOT EXISTS (SELECT 1 FROM menu_categories WHERE name = 'Tráng miệng');

-- B4: update test item + add real menu items
UPDATE menu_items SET
  code        = 'FOOD-001',
  name        = 'Phở bò tái',
  description = 'Nước dùng ninh xương bò 12 tiếng, thịt tái hồng, bánh phở mềm, hành lá và rau thơm.',
  base_price  = 85000,
  unit        = 'tô',
  updated_at  = NOW(),
  category_id = (SELECT id FROM menu_categories WHERE name = 'Món chính')
WHERE id = 1;

INSERT INTO menu_items (category_id, code, name, description, base_price, unit, needs_kitchen, takeaway_available, is_active, created_at, updated_at)
SELECT mc.id, i.code, i.name, i.description, i.base_price, i.unit, i.needs_kitchen, i.takeaway_available, true, NOW(), NOW()
FROM (VALUES
  ('Khai vị',    'FOOD-002','Gỏi cuốn tôm thịt',     'Rau sống tươi, tôm luộc, thịt ba chỉ, bún, chấm tương đậu phộng.',         55000,'phần',true, true),
  ('Khai vị',    'FOOD-003','Chả giò chiên',           'Vàng giòn nhân tôm thịt mộc nhĩ, chấm tương chua ngọt.',                   60000,'phần',true, true),
  ('Món chính',  'FOOD-004','Bún bò Huế',              'Nước dùng cay nồng đặc trưng, giò heo, chả Huế, bún to, rau sống.',        80000,'tô',  true, true),
  ('Món chính',  'FOOD-005','Cơm tấm sườn bì chả',    'Sườn nướng than hoa, bì tơi, chả hấp, cơm tấm thơm, mỡ hành, nước mắm.',  75000,'phần',true, true),
  ('Món chính',  'FOOD-006','Bánh mì kẹp thịt nướng', 'Bánh mì giòn, thịt nướng mật ong, rau thơm, đồ chua, pate.',               45000,'ổ',   true, true),
  ('Đồ uống',    'DRINK-001','Trà đá',                 'Trà xanh ướp hương sen, phục vụ kèm đá lạnh.',                             15000,'ly',  false,true),
  ('Đồ uống',    'DRINK-002','Cà phê sữa đá',          'Cà phê phin truyền thống, sữa đặc, đá viên.',                              30000,'ly',  false,true),
  ('Đồ uống',    'DRINK-003','Nước ép cam',             'Cam vắt tươi nguyên chất, không đường.',                                   35000,'ly',  false,true),
  ('Đồ uống',    'DRINK-004','Sinh tố xoài',            'Xoài chín ngọt xay kem, không phụ gia.',                                   40000,'ly',  false,true),
  ('Tráng miệng','DES-001', 'Bánh flan',                'Bánh flan trứng mềm mịn, caramel đắng nhẹ, ướp lạnh.',                    40000,'phần',false,true),
  ('Tráng miệng','DES-002', 'Chè khúc bạch',            'Thạch sữa, vải thiều, nước đường thơm, đá bào.',                          45000,'ly',  false,true)
) AS i(cat_name, code, name, description, base_price, unit, needs_kitchen, takeaway_available)
JOIN menu_categories mc ON mc.name = i.cat_name
ON CONFLICT (code) DO NOTHING;

-- B4: enable all active items at all active branches
INSERT INTO branch_menu_items (branch_id, menu_item_id, is_available, price)
SELECT b.id, m.id, true, null
FROM   branches b CROSS JOIN menu_items m
WHERE  b.status = 'ACTIVE' AND m.is_active = true
ON CONFLICT (branch_id, menu_item_id) DO UPDATE SET is_available = true;

-- B4: areas for each branch (uses code lookup – safe on fresh DBs where branches don't exist yet)
INSERT INTO areas (branch_id, name)
SELECT b.id, t.aname
FROM branches b
JOIN (VALUES
  ('CN01','Tầng 1'),('CN01','Tầng 2'),
  ('CN02','Tầng 1'),('CN02','Sân vườn'),
  ('CN03','Tầng 1'),('CN03','Tầng 2'),
  ('CN04','Trong nhà'),('CN04','Ngoài hiên')
) t(bcode, aname) ON b.code = t.bcode
ON CONFLICT (branch_id, name) DO NOTHING;

-- B4: tables – CN01 (Hoàn Kiếm)
INSERT INTO dining_tables (area_id, name, capacity, status)
SELECT a.id, t.tname, t.cap, 'AVAILABLE'
FROM areas a
JOIN branches b ON b.id = a.branch_id AND b.code = 'CN01'
JOIN (VALUES
  ('Tầng 1','B01',4),('Tầng 1','B02',4),('Tầng 1','B03',6),
  ('Tầng 1','B04',2),('Tầng 1','B05',8),
  ('Tầng 2','B06',4),('Tầng 2','B07',4),('Tầng 2','B08',6),
  ('Tầng 2','B09',2),('Tầng 2','B10',8)
) t(aname, tname, cap) ON a.name = t.aname
ON CONFLICT (area_id, name) DO NOTHING;

-- B4: tables – CN02 (Cầu Giấy)
INSERT INTO dining_tables (area_id, name, capacity, status)
SELECT a.id, t.tname, t.cap, 'AVAILABLE'
FROM areas a
JOIN branches b ON b.id = a.branch_id AND b.code = 'CN02'
JOIN (VALUES
  ('Tầng 1','B01',4),('Tầng 1','B02',4),('Tầng 1','B03',6),
  ('Tầng 1','B04',2),('Tầng 1','B05',4),
  ('Sân vườn','SV01',6),('Sân vườn','SV02',6),
  ('Sân vườn','SV03',8),('Sân vườn','SV04',4),('Sân vườn','SV05',4)
) t(aname, tname, cap) ON a.name = t.aname
ON CONFLICT (area_id, name) DO NOTHING;

-- B4: tables – CN03 (Tây Hồ)
INSERT INTO dining_tables (area_id, name, capacity, status)
SELECT a.id, t.tname, t.cap, 'AVAILABLE'
FROM areas a
JOIN branches b ON b.id = a.branch_id AND b.code = 'CN03'
JOIN (VALUES
  ('Tầng 1','B01',4),('Tầng 1','B02',4),('Tầng 1','B03',6),
  ('Tầng 1','B04',2),('Tầng 1','B05',8),
  ('Tầng 2','B06',4),('Tầng 2','B07',4),('Tầng 2','B08',6),
  ('Tầng 2','B09',2),('Tầng 2','B10',4)
) t(aname, tname, cap) ON a.name = t.aname
ON CONFLICT (area_id, name) DO NOTHING;

-- B4: tables – CN04 (Long Biên)
INSERT INTO dining_tables (area_id, name, capacity, status)
SELECT a.id, t.tname, t.cap, 'AVAILABLE'
FROM areas a
JOIN branches b ON b.id = a.branch_id AND b.code = 'CN04'
JOIN (VALUES
  ('Trong nhà','B01',4),('Trong nhà','B02',4),('Trong nhà','B03',6),
  ('Trong nhà','B04',2),('Trong nhà','B05',4),
  ('Ngoài hiên','H01',6),('Ngoài hiên','H02',6),
  ('Ngoài hiên','H03',4),('Ngoài hiên','H04',4),('Ngoài hiên','H05',2)
) t(aname, tname, cap) ON a.name = t.aname
ON CONFLICT (area_id, name) DO NOTHING;

-- B4: opening hours Mon–Sun 10:00–22:00 for all active branches
INSERT INTO opening_hour (branch_id, weekday, opens_at, closes_at, closed)
SELECT b.id, w.d, '10:00:00'::time, '22:00:00'::time, false
FROM   branches b CROSS JOIN (VALUES (1),(2),(3),(4),(5),(6),(7)) w(d)
WHERE  b.status = 'ACTIVE'
ON CONFLICT (branch_id, weekday)
DO UPDATE SET opens_at = '10:00:00', closes_at = '22:00:00', closed = false;
