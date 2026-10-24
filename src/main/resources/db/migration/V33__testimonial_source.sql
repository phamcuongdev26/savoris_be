ALTER TABLE testimonial ADD COLUMN IF NOT EXISTS source varchar(180) NOT NULL DEFAULT '';

-- Existing seeded quotes are demo content without a verifiable review source.
UPDATE testimonial SET visible = false WHERE source = '';
