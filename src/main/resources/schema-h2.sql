create table if not exists todo (
    id varchar(36) primary key,
    title varchar(30),
    finished boolean,
    created_at timestamp
);