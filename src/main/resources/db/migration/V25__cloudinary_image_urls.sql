-- Replace local /images/*.webp placeholder paths with permanent Cloudinary URLs.
-- gallery_image
UPDATE gallery_image SET image_url = 'https://res.cloudinary.com/kizhftbe/image/upload/v1792812806/savoris/site/mbvwqmnkwm0qyzv9a4aj.webp' WHERE image_url = '/images/restaurant-interior.webp';
UPDATE gallery_image SET image_url = 'https://res.cloudinary.com/kizhftbe/image/upload/v1792812809/savoris/site/rpbrfnxhckcf7ucpgwjn.webp' WHERE image_url = '/images/gourmet-dishes.webp';
UPDATE gallery_image SET image_url = 'https://res.cloudinary.com/kizhftbe/image/upload/v1792812811/savoris/site/hbxhzowddgzbwcuzjjhc.webp' WHERE image_url = '/images/steak-plate.webp';
UPDATE gallery_image SET image_url = 'https://res.cloudinary.com/kizhftbe/image/upload/v1792812813/savoris/site/l070ytoijiy27o7qz79z.webp' WHERE image_url = '/images/bun-cha.webp';
UPDATE gallery_image SET image_url = 'https://res.cloudinary.com/kizhftbe/image/upload/v1792812815/savoris/site/kcq7dhwbl5oknp3mp4qv.webp' WHERE image_url = '/images/pho-top.webp';
UPDATE gallery_image SET image_url = 'https://res.cloudinary.com/kizhftbe/image/upload/v1792812817/savoris/site/hnncfivh8ppjourbsk5f.webp' WHERE image_url = '/images/sliced-steak.webp';

-- offer
UPDATE offer SET image_url = 'https://res.cloudinary.com/kizhftbe/image/upload/v1792812809/savoris/site/rpbrfnxhckcf7ucpgwjn.webp' WHERE image_url = '/images/gourmet-dishes.webp';
UPDATE offer SET image_url = 'https://res.cloudinary.com/kizhftbe/image/upload/v1792812811/savoris/site/hbxhzowddgzbwcuzjjhc.webp' WHERE image_url = '/images/steak-plate.webp';

-- branch_page heroImage
UPDATE branch_page
SET payload = jsonb_set(payload, '{heroImage}', '"https://res.cloudinary.com/kizhftbe/image/upload/v1792812806/savoris/site/mbvwqmnkwm0qyzv9a4aj.webp"')
WHERE payload->>'heroImage' = '/images/restaurant-interior.webp';

-- site_setting heroImage
UPDATE site_setting
SET payload = jsonb_set(payload, '{heroImage}', '"https://res.cloudinary.com/kizhftbe/image/upload/v1792812806/savoris/site/mbvwqmnkwm0qyzv9a4aj.webp"')
WHERE id = 1 AND payload->>'heroImage' = '/images/restaurant-interior.webp';
