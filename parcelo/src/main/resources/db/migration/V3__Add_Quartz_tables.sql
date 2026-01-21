-- This file was modified from the original found in the Quartz project at
-- https://github.com/quartz-scheduler/quartz/blob/v2.5.2/quartz/src/main/resources/org/quartz/impl/jdbcjobstore/tables_postgres.sql.
--
-- Below are all original attribution notices in the file:
--
--     Thanks to Patrick Lightbody for submitting this...

CREATE TABLE quartz_job_details (
    sched_name varchar(120) NOT NULL,
    job_name varchar(200) NOT NULL,
    job_group varchar(200) NOT NULL,
    description varchar(250) NULL,
    job_class_name varchar(250) NOT NULL,
    is_durable bool NOT NULL,
    is_nonconcurrent bool NOT NULL,
    is_update_data bool NOT NULL,
    requests_recovery bool NOT NULL,
    job_data bytea NULL,
    PRIMARY KEY (sched_name, job_name, job_group)
);

CREATE TABLE quartz_triggers (
    sched_name varchar(120) NOT NULL,
    trigger_name varchar(200) NOT NULL,
    trigger_group varchar(200) NOT NULL,
    job_name varchar(200) NOT NULL,
    job_group varchar(200) NOT NULL,
    description varchar(250) NULL,
    next_fire_time bigint NULL,
    prev_fire_time bigint NULL,
    priority integer NULL,
    trigger_state varchar(16) NOT NULL,
    trigger_type varchar(8) NOT NULL,
    start_time bigint NOT NULL,
    end_time bigint NULL,
    calendar_name varchar(200) NULL,
    misfire_instr smallint NULL,
    job_data bytea NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, job_name, job_group)
    REFERENCES quartz_job_details (sched_name, job_name, job_group)
);

CREATE TABLE quartz_simple_triggers (
    sched_name varchar(120) NOT NULL,
    trigger_name varchar(200) NOT NULL,
    trigger_group varchar(200) NOT NULL,
    repeat_count bigint NOT NULL,
    repeat_interval bigint NOT NULL,
    times_triggered bigint NOT NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
    REFERENCES quartz_triggers (sched_name, trigger_name, trigger_group)
);

CREATE TABLE quartz_cron_triggers (
    sched_name varchar(120) NOT NULL,
    trigger_name varchar(200) NOT NULL,
    trigger_group varchar(200) NOT NULL,
    cron_expression varchar(120) NOT NULL,
    time_zone_id varchar(80),
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
    REFERENCES quartz_triggers (sched_name, trigger_name, trigger_group)
);

CREATE TABLE quartz_simprop_triggers (
    sched_name varchar(120) NOT NULL,
    trigger_name varchar(200) NOT NULL,
    trigger_group varchar(200) NOT NULL,
    str_prop_1 varchar(512) NULL,
    str_prop_2 varchar(512) NULL,
    str_prop_3 varchar(512) NULL,
    int_prop_1 INT NULL,
    int_prop_2 INT NULL,
    long_prop_1 bigint NULL,
    long_prop_2 bigint NULL,
    dec_prop_1 NUMERIC(13, 4) NULL,
    dec_prop_2 NUMERIC(13, 4) NULL,
    bool_prop_1 bool NULL,
    bool_prop_2 bool NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
    REFERENCES quartz_triggers (sched_name, trigger_name, trigger_group)
);

CREATE TABLE quartz_blob_triggers (
    sched_name varchar(120) NOT NULL,
    trigger_name varchar(200) NOT NULL,
    trigger_group varchar(200) NOT NULL,
    blob_data bytea NULL,
    PRIMARY KEY (sched_name, trigger_name, trigger_group),
    FOREIGN KEY (sched_name, trigger_name, trigger_group)
    REFERENCES quartz_triggers (sched_name, trigger_name, trigger_group)
);

CREATE TABLE quartz_calendars (
    sched_name varchar(120) NOT NULL,
    calendar_name varchar(200) NOT NULL,
    calendar bytea NOT NULL,
    PRIMARY KEY (sched_name, calendar_name)
);

CREATE TABLE quartz_paused_trigger_grps (
    sched_name varchar(120) NOT NULL,
    trigger_group varchar(200) NOT NULL,
    PRIMARY KEY (sched_name, trigger_group)
);

CREATE TABLE quartz_fired_triggers (
    sched_name varchar(120) NOT NULL,
    entry_id varchar(95) NOT NULL,
    trigger_name varchar(200) NOT NULL,
    trigger_group varchar(200) NOT NULL,
    instance_name varchar(200) NOT NULL,
    fired_time bigint NOT NULL,
    sched_time bigint NOT NULL,
    priority integer NOT NULL,
    state varchar(16) NOT NULL,
    job_name varchar(200) NULL,
    job_group varchar(200) NULL,
    is_nonconcurrent bool NULL,
    requests_recovery bool NULL,
    PRIMARY KEY (sched_name, entry_id)
);

CREATE TABLE quartz_scheduler_state (
    sched_name varchar(120) NOT NULL,
    instance_name varchar(200) NOT NULL,
    last_checkin_time bigint NOT NULL,
    checkin_interval bigint NOT NULL,
    PRIMARY KEY (sched_name, instance_name)
);

CREATE TABLE quartz_locks (
    sched_name varchar(120) NOT NULL,
    lock_name varchar(40) NOT NULL,
    PRIMARY KEY (sched_name, lock_name)
);

CREATE INDEX idx_quartz_j_req_recovery
    ON quartz_job_details (sched_name, requests_recovery);
CREATE INDEX idx_quartz_j_grp
    ON quartz_job_details (sched_name, job_group);

CREATE INDEX idx_quartz_t_j
    ON quartz_triggers (sched_name, job_name, job_group);
CREATE INDEX idx_quartz_t_jg
    ON quartz_triggers (sched_name, job_group);
CREATE INDEX idx_quartz_t_c
    ON quartz_triggers (sched_name, calendar_name);
CREATE INDEX idx_quartz_t_g
    ON quartz_triggers (sched_name, trigger_group);
CREATE INDEX idx_quartz_t_state
    ON quartz_triggers (sched_name, trigger_state);
CREATE INDEX idx_quartz_t_n_state
    ON quartz_triggers (sched_name, trigger_name, trigger_group, trigger_state);
CREATE INDEX idx_quartz_t_n_g_state
    ON quartz_triggers (sched_name, trigger_group, trigger_state);
CREATE INDEX idx_quartz_t_next_fire_time
    ON quartz_triggers (sched_name, next_fire_time);
CREATE INDEX idx_quartz_t_nft_st
    ON quartz_triggers (sched_name, trigger_state, next_fire_time);
CREATE INDEX idx_quartz_t_nft_misfire
    ON quartz_triggers (sched_name, misfire_instr, next_fire_time);
CREATE INDEX idx_quartz_t_nft_st_misfire
    ON quartz_triggers (sched_name, misfire_instr, next_fire_time, trigger_state);
CREATE INDEX idx_quartz_t_nft_st_misfire_grp
    ON quartz_triggers (sched_name, misfire_instr, next_fire_time, trigger_group, trigger_state);

CREATE INDEX idx_quartz_ft_trig_inst_name
    ON quartz_fired_triggers (sched_name, instance_name);
CREATE INDEX idx_quartz_ft_inst_job_req_rcvry
    ON quartz_fired_triggers (sched_name, instance_name, requests_recovery);
CREATE INDEX idx_quartz_ft_j_g
    ON quartz_fired_triggers (sched_name, job_name, job_group);
CREATE INDEX idx_quartz_ft_jg
    ON quartz_fired_triggers (sched_name, job_group);
CREATE INDEX idx_quartz_ft_t_g
    ON quartz_fired_triggers (sched_name, trigger_name, trigger_group);
CREATE INDEX idx_quartz_ft_tg
    ON quartz_fired_triggers (sched_name, trigger_group);
