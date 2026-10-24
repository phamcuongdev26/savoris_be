UPDATE branches
SET name = CASE code
    WHEN 'CN01' THEN 'Hoàn Kiếm'
    WHEN 'CN02' THEN 'Cầu Giấy'
    WHEN 'CN03' THEN 'Tây Hồ'
    WHEN 'CN04' THEN 'Long Biên'
END,
updated_at = now()
WHERE code IN ('CN01', 'CN02', 'CN03', 'CN04');
