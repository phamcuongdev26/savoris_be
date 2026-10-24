-- Issue 1: Ensure opening_hour rows exist for every active branch × weekday.
-- V24 seeded these, but running again is safe (ON CONFLICT DO NOTHING).
INSERT INTO opening_hour (branch_id, weekday, opens_at, closes_at)
SELECT b.id, d, '10:00'::time, '22:00'::time
FROM branches b CROSS JOIN generate_series(1, 7) d
WHERE b.status = 'ACTIVE'
ON CONFLICT (branch_id, weekday) DO NOTHING;

-- Issue 3: Use human-readable slugs for the four main branches.
UPDATE branches
SET slug = CASE code
    WHEN 'CN01' THEN 'hoan-kiem'
    WHEN 'CN02' THEN 'cau-giay'
    WHEN 'CN03' THEN 'tay-ho'
    WHEN 'CN04' THEN 'long-bien'
END
WHERE code IN ('CN01', 'CN02', 'CN03', 'CN04');

-- Sync the slug stored inside the branch_page CMS payload.
UPDATE branch_page bp
SET payload = jsonb_set(bp.payload, '{slug}', to_jsonb(
    CASE b.code
        WHEN 'CN01' THEN 'hoan-kiem'
        WHEN 'CN02' THEN 'cau-giay'
        WHEN 'CN03' THEN 'tay-ho'
        WHEN 'CN04' THEN 'long-bien'
    END
))
FROM branches b
WHERE bp.branch_id = b.id
  AND b.code IN ('CN01', 'CN02', 'CN03', 'CN04');

-- Issue 2 / #ve-chung-toi: Seed one default story section if the CMS has none yet.
INSERT INTO site_config (config_key, content, updated_at)
VALUES (
    'sections',
    '[{"id":1,"image_url":"/images/restaurant-interior.webp","public_id":"","image_alt":"Không gian nhà hàng Savoris","title":"Ẩm thực Việt\nđương đại","body":"Savoris ra đời từ tình yêu với ẩm thực Việt và khát vọng mang đến trải nghiệm tinh tế, thân quen. Chúng tôi tin rằng mỗi bữa ăn là một câu chuyện — và chúng tôi kể câu chuyện đó bằng từng nguyên liệu tươi, từng hương vị cân bằng.","link_text":"Đặt bàn ngay","link_url":"/dat-ban","image_side":"right","order":1,"visible":true}]',
    now()
)
ON CONFLICT (config_key) DO NOTHING;
