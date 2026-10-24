DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.oid = to_regclass('staffs')
          AND n.nspname = current_schema()
          AND c.relkind = 'v'
    ) THEN
        DROP VIEW staffs;
    END IF;
END
$$;
