-- Use the branch names guests see and make public URLs match those names.
UPDATE branches
SET name = CASE code
        WHEN 'CN03' THEN 'Hàng Giấy'
        WHEN 'CN04' THEN 'Hàng Mã'
        ELSE name
    END,
    slug = CASE code
        WHEN 'CN01' THEN 'hoan-kiem'
        WHEN 'CN02' THEN 'cau-giay'
        WHEN 'CN03' THEN 'hang-giay'
        WHEN 'CN04' THEN 'hang-ma'
        ELSE slug
    END,
    address = CASE code
        WHEN 'CN03' THEN 'Số 40, Phố Hàng Giấy, Hà Nội'
        WHEN 'CN04' THEN 'Số 43, Phố Hàng Mã, Hà Nội'
        ELSE address
    END,
    city = CASE WHEN code IN ('CN03', 'CN04') THEN 'Hà Nội' ELSE city END,
    phone = CASE code
        WHEN 'CN01' THEN CASE WHEN phone IS NULL OR btrim(phone) = '' OR phone LIKE '000000000%' THEN '02473028181' ELSE phone END
        WHEN 'CN02' THEN CASE WHEN phone IS NULL OR btrim(phone) = '' OR phone LIKE '000000000%' THEN '02473028182' ELSE phone END
        WHEN 'CN03' THEN CASE WHEN phone IS NULL OR btrim(phone) = '' OR phone LIKE '000000000%' THEN '04727352235' ELSE phone END
        WHEN 'CN04' THEN CASE WHEN phone IS NULL OR btrim(phone) = '' OR phone LIKE '000000000%' THEN '0384975244' ELSE phone END
        ELSE phone
    END
WHERE code IN ('CN01', 'CN02', 'CN03', 'CN04');

UPDATE branch_page bp
SET payload = jsonb_set(coalesce(bp.payload, '{}'::jsonb), '{slug}', to_jsonb(b.slug), true)
FROM branches b
WHERE bp.branch_id = b.id
  AND b.code IN ('CN01', 'CN02', 'CN03', 'CN04');
