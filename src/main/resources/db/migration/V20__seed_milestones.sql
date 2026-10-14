INSERT INTO site_config (config_key, content, updated_at)
VALUES (
    'milestones',
    '[
        {"id":1,"year":"2016","title":"Khai trương","description":"Chi nhánh đầu tiên ra đời tại Hoàn Kiếm, Hà Nội — phục vụ hơn 300 lượt khách mỗi ngày chỉ sau hai tuần mở cửa.","order":1},
        {"id":2,"year":"2018","title":"Mở rộng","description":"Khai trương chi nhánh Tây Hồ, ra mắt thực đơn dim sum cuối tuần được đông đảo thực khách đón nhận.","order":2},
        {"id":3,"year":"2020","title":"Ghi nhận","description":"Được bình chọn \"Nhà hàng Á Đông được yêu thích nhất Hà Nội\" dù năm đó toàn ngành F&B đầy thách thức.","order":3},
        {"id":4,"year":"2022","title":"Toàn quốc","description":"Tiến vào TP.HCM với chi nhánh Quận 1 — Savoris chính thức trở thành chuỗi nhà hàng quy mô toàn quốc.","order":4},
        {"id":5,"year":"2024","title":"Biển Đông","description":"Khai trương chi nhánh Sơn Trà, Đà Nẵng, với sân thượng hướng biển — điểm đến ẩm thực của vùng Miền Trung.","order":5},
        {"id":6,"year":"2026","title":"Số hoá","description":"Savoris Platform hoàn chỉnh: gọi món QR, thanh toán online, quản lý bếp và kho tập trung toàn chuỗi.","order":6}
    ]',
    now()
)
ON CONFLICT (config_key) DO NOTHING;
