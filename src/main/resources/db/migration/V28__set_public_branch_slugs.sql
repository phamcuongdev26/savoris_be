-- The public website uses /chi-nhanh/cn1 through /chi-nhanh/cn4.
UPDATE branches
SET slug = CASE code
    WHEN 'CN01' THEN 'cn1'
    WHEN 'CN02' THEN 'cn2'
    WHEN 'CN03' THEN 'cn3'
    WHEN 'CN04' THEN 'cn4'
END
WHERE status = 'ACTIVE'
  AND code IN ('CN01', 'CN02', 'CN03', 'CN04')
  AND slug IS DISTINCT FROM CASE code
      WHEN 'CN01' THEN 'cn1'
      WHEN 'CN02' THEN 'cn2'
      WHEN 'CN03' THEN 'cn3'
      WHEN 'CN04' THEN 'cn4'
  END;
