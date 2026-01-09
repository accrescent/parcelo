CREATE TABLE rate_limit_buckets(
    id text PRIMARY KEY,
    state bytea,
    expires_at bigint
);
