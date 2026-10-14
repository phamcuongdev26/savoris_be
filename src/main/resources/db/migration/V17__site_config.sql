create table site_config (
    config_key varchar(100) not null,
    content text not null,
    updated_by integer,
    updated_at timestamp(6) not null default now(),
    primary key (config_key)
);
