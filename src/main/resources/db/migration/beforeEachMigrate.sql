DO $$
BEGIN
    IF to_regclass('staffs') IS NULL AND to_regclass('app_user') IS NOT NULL THEN
        EXECUTE 'CREATE VIEW staffs AS SELECT * FROM app_user';
    END IF;
END
$$;
