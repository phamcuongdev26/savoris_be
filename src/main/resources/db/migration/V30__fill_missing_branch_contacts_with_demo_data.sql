-- Clearly marked, non-routable sample values requested for demo/testing only.
-- Preserve any existing contact fields and any address that already contains street-level detail.
UPDATE branches
SET address = CASE WHEN length(trim(coalesce(address, ''))) < 10 THEN CASE code
        WHEN 'CN01' THEN 'DỮ LIỆU DEMO · Số 00, Phố Mẫu CN01, Hoàn Kiếm'
        WHEN 'CN02' THEN 'DỮ LIỆU DEMO · Số 00, Phố Mẫu CN02, Cầu Giấy'
        WHEN 'CN03' THEN 'DỮ LIỆU DEMO · Số 00, Phố Mẫu CN03, Tây Hồ'
        WHEN 'CN04' THEN 'DỮ LIỆU DEMO · Số 00, Phố Mẫu CN04, Long Biên'
    END ELSE address END,
    phone = CASE WHEN nullif(trim(phone), '') IS NULL THEN CASE code
        WHEN 'CN01' THEN '0000000001'
        WHEN 'CN02' THEN '0000000002'
        WHEN 'CN03' THEN '0000000003'
        WHEN 'CN04' THEN '0000000004'
    END ELSE phone END,
    email = CASE WHEN nullif(trim(email), '') IS NULL THEN CASE code
        WHEN 'CN01' THEN 'cn01@savoris.invalid'
        WHEN 'CN02' THEN 'cn02@savoris.invalid'
        WHEN 'CN03' THEN 'cn03@savoris.invalid'
        WHEN 'CN04' THEN 'cn04@savoris.invalid'
    END ELSE email END,
    updated_at = now()
WHERE code IN ('CN01', 'CN02', 'CN03', 'CN04')
  AND (length(trim(coalesce(address, ''))) < 10
       OR nullif(trim(phone), '') IS NULL
       OR nullif(trim(email), '') IS NULL);
