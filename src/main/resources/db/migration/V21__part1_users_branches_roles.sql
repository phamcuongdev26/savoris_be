-- Keep existing staff identifiers: operational tables have foreign keys to them.
alter table staffs drop constraint if exists staffs_role_check;
alter table staffs drop constraint if exists staffs_status_check;
alter table staffs rename to app_user;

update app_user
set role = case role
    when 'BRANCH_MANAGER' then 'MANAGER'
    when 'WAITER' then 'STAFF'
    when 'CASHIER' then 'STAFF'
    when 'WAREHOUSE' then 'STAFF'
    else role
end,
status = case when role = 'WAREHOUSE' then 'LOCKED' else status end;

-- Some older databases may still contain the merged cashier role.
update app_user set role = 'STAFF' where role = 'CASHIER';

update app_user
set email = username || '@legacy.savoris.invalid'
where email is null or btrim(email) = '';
alter table app_user alter column email set not null;
alter table app_user add constraint app_user_role_check
    check (role in ('ADMIN', 'MANAGER', 'STAFF', 'CHEF'));
alter table app_user add constraint app_user_status_check
    check (status in ('ACTIVE', 'LOCKED', 'ON_LEAVE', 'RESIGNED'));
alter table app_user add constraint app_user_branch_role_check
    check ((role = 'ADMIN' and branch_id is null) or (role <> 'ADMIN' and branch_id is not null));

alter table branches add column email varchar(255);
create unique index branches_email_unique on branches (email) where email is not null;

