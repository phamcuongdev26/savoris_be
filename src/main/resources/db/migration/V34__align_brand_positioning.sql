UPDATE site_config
SET content = replace(
        replace(
            content,
            'ra mắt thực đơn dim sum cuối tuần được đông đảo thực khách đón nhận.',
            'giới thiệu những món Việt quen thuộc qua cách chế biến và trình bày đương đại.'
        ),
        'Được bình chọn "Nhà hàng Á Đông được yêu thích nhất Hà Nội" dù năm đó toàn ngành F&B đầy thách thức.',
        'Được thực khách ghi nhận nhờ cách tiếp cận đương đại với ẩm thực Việt.'
    ),
    updated_at = now()
WHERE config_key = 'milestones'
  AND (content LIKE '%dim sum%' OR content LIKE '%Nhà hàng Á Đông%');

UPDATE site_setting
SET payload = jsonb_set(
        jsonb_set(payload, '{brandLine}', '"Ẩm thực Việt đương đại, tinh tế trong từng khoảnh khắc."'::jsonb),
        '{seoTitle}', '"Savoris | Ẩm thực Việt đương đại"'::jsonb
    )
WHERE id = 1
  AND payload->>'brandLine' = 'Ẩm thực Việt, tinh tế trong từng khoảnh khắc.';

UPDATE branch_page
SET payload = jsonb_set(payload, '{brandLine}', '"Món Việt thân quen, trải nghiệm đương đại."'::jsonb)
WHERE payload->>'brandLine' = 'Hương vị thân quen, trải nghiệm luôn mới.';
