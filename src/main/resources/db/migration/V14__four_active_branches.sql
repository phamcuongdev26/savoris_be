-- Keep the chain to four active branches. Historical rows remain intact, but CN05+ are no longer exposed by
-- active-branch APIs.
update branches
set status = 'INACTIVE',
    updated_at = now()
where code in ('CN05', 'CN06', 'CN07', 'CN08', 'CN09')
  and status <> 'INACTIVE';

-- Existing deployments used chain-wide bank settings. Move that data to CN01 only; CN02-CN04 must be configured
-- separately before they can accept bank transfers.
insert into settings (branch_id, scope_key, updated_by, updated_at, setting_key, description, setting_value)
select b.id,
       b.id,
       s.updated_by,
       now(),
       s.setting_key,
       s.description,
       s.setting_value
from settings s
join branches b on b.code = 'CN01'
where s.scope_key = 0
  and s.setting_key in ('BANK_CODE', 'BANK_ACCOUNT', 'BANK_ACCOUNT_NAME', 'TRANSFER_QR_URL')
on conflict (scope_key, setting_key) do update
set branch_id = excluded.branch_id,
    updated_by = excluded.updated_by,
    updated_at = excluded.updated_at,
    description = excluded.description,
    setting_value = excluded.setting_value;
