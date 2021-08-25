DROP TABLE todo;

CREATE TABLE todo(id STRING(36), title STRING(30), finished BOOL, created_at TIMESTAMP) PRIMARY KEY (id);
