-- Front-of-house is one role now: former cashier accounts become waiter accounts with the same permissions.
update staffs
set role = 'WAITER'
where role = 'CASHIER';

alter table staffs drop constraint if exists staffs_role_check;
alter table staffs add constraint staffs_role_check
    check (role in ('ADMIN', 'BRANCH_MANAGER', 'WAITER', 'CHEF', 'WAREHOUSE'));
