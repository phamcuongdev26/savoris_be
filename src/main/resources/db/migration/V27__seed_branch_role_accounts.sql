-- Create one manager, staff member, and chef account for each active Savoris branch.
-- The shared admin account is intentionally left unchanged.
-- Password hash matches the shared password used by V23__reset_passwords_savoris123.sql.
WITH branch_accounts AS (
    SELECT
        b.id AS branch_id,
        b.code AS branch_code,
        b.name AS branch_name,
        role_account.role,
        role_account.username_prefix,
        role_account.role_label
    FROM branches b
    CROSS JOIN (VALUES
        ('MANAGER', 'quanly', 'Quản lý'),
        ('STAFF', 'nhanvien', 'Nhân viên'),
        ('CHEF', 'daubep', 'Đầu bếp')
    ) AS role_account(role, username_prefix, role_label)
    WHERE b.code IN ('CN01', 'CN02', 'CN03', 'CN04')
), prepared_accounts AS (
    SELECT
        branch_id,
        upper(username_prefix || (substring(branch_code from 3)::integer)::text) AS code,
        role_label || ' - ' || branch_name AS full_name,
        username_prefix || (substring(branch_code from 3)::integer)::text AS username,
        username_prefix || (substring(branch_code from 3)::integer)::text || '@savoris.local' AS email,
        role
    FROM branch_accounts
)
INSERT INTO app_user (
    branch_id, code, full_name, email, username, password_hash, role, status, created_at, updated_at
)
SELECT
    branch_id,
    code,
    full_name,
    email,
    username,
    '$2b$10$Q.hyJr/iwUpfBuGNrd/HceOx7tQgscvMRZC33ZxdReCje6nISF0p.',
    role,
    'ACTIVE',
    now(),
    now()
FROM prepared_accounts
ON CONFLICT DO NOTHING;
