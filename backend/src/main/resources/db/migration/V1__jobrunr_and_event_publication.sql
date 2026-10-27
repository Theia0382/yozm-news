-- JobRunr 작업 테이블과 Spring Modulith 이벤트 발행 레지스트리.
--
-- 두 라이브러리가 제공하는 스키마 원문을 손대지 않고 담는다. 라이브러리의 스키마 자동 생성은 끄고
-- (jobrunr.database.skip-create, spring.modulith.events.jdbc.schema-initialization.enabled) 이 파일을 정본으로 둔다.
--
-- 구획: 줄 맨 앞이 아래 표시인 줄이 구획을 열고, 구획 본문은 다음 표시 줄 또는 파일 끝까지다.
--   jobrunr-migration: {스크립트}   JobRunr 가 PostgreSQL 에 적용하는 스크립트 원문(파일 이름순, PostgreSQL 전용본 우선)
--   jobrunr-migration-record        JobRunr 가 스스로 남기는 적용 기록(jobrunr_migrations) — 같은 스크립트를 다시 돌리지 않게 한다
--   modulith-schema: {경로}         spring-modulith-events-jdbc 의 schemas/{경로} 원문
-- FrameworkSchemaMigrationTests 가 구획 본문과 라이브러리 원문을 대조한다. 라이브러리를 올려 새 스크립트가 생기면
-- 새 버전 파일에 같은 규칙으로 옮긴다.

-- jobrunr-migration: v000__create_migrations_table.sql
CREATE TABLE jobrunr_migrations
(
    id          nchar(36) PRIMARY KEY,
    script      varchar(64) NOT NULL,
    installedOn varchar(29) NOT NULL
);

-- jobrunr-migration: v001__create_job_table.sql
CREATE TABLE jobrunr_jobs
(
    id           NCHAR(36) PRIMARY KEY,
    version      int          NOT NULL,
    jobAsJson    text         NOT NULL,
    jobSignature VARCHAR(512) NOT NULL,
    state        VARCHAR(36)  NOT NULL,
    createdAt    TIMESTAMP    NOT NULL,
    updatedAt    TIMESTAMP    NOT NULL,
    scheduledAt  TIMESTAMP
);
CREATE INDEX jobrunr_state_idx ON jobrunr_jobs (state);
CREATE INDEX jobrunr_job_signature_idx ON jobrunr_jobs (jobSignature);
CREATE INDEX jobrunr_job_created_at_idx ON jobrunr_jobs (createdAt);
CREATE INDEX jobrunr_job_updated_at_idx ON jobrunr_jobs (updatedAt);
CREATE INDEX jobrunr_job_scheduled_at_idx ON jobrunr_jobs (scheduledAt);

-- jobrunr-migration: v002__create_recurring_job_table.sql
CREATE TABLE jobrunr_recurring_jobs
(
    id        NCHAR(128) PRIMARY KEY,
    version   int  NOT NULL,
    jobAsJson text NOT NULL
);

-- jobrunr-migration: v003__create_background_job_server_table.sql
CREATE TABLE jobrunr_backgroundjobservers
(
    id                     NCHAR(36) PRIMARY KEY,
    workerPoolSize         int           NOT NULL,
    pollIntervalInSeconds  int           NOT NULL,
    firstHeartbeat         TIMESTAMP(6)  NOT NULL,
    lastHeartbeat          TIMESTAMP(6)  NOT NULL,
    running                int           NOT NULL,
    systemTotalMemory      BIGINT        NOT NULL,
    systemFreeMemory       BIGINT        NOT NULL,
    systemCpuLoad          NUMERIC(3, 2) NOT NULL,
    processMaxMemory       BIGINT        NOT NULL,
    processFreeMemory      BIGINT        NOT NULL,
    processAllocatedMemory BIGINT        NOT NULL,
    processCpuLoad         NUMERIC(3, 2) NOT NULL
);
CREATE INDEX jobrunr_bgjobsrvrs_fsthb_idx ON jobrunr_backgroundjobservers (firstHeartbeat);
CREATE INDEX jobrunr_bgjobsrvrs_lsthb_idx ON jobrunr_backgroundjobservers (lastHeartbeat);

-- jobrunr-migration: v004__create_job_stats_view.sql
CREATE TABLE jobrunr_job_counters
(
    name   NCHAR(36) PRIMARY KEY,
    amount int NOT NULL
);

INSERT INTO jobrunr_job_counters (name, amount)
VALUES ('AWAITING', 0);
INSERT INTO jobrunr_job_counters (name, amount)
VALUES ('SCHEDULED', 0);
INSERT INTO jobrunr_job_counters (name, amount)
VALUES ('ENQUEUED', 0);
INSERT INTO jobrunr_job_counters (name, amount)
VALUES ('PROCESSING', 0);
INSERT INTO jobrunr_job_counters (name, amount)
VALUES ('FAILED', 0);
INSERT INTO jobrunr_job_counters (name, amount)
VALUES ('SUCCEEDED', 0);

CREATE VIEW jobrunr_jobs_stats
AS
SELECT count(*)                                                                           AS total,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'AWAITING')             AS awaiting,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SCHEDULED')            AS scheduled,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'ENQUEUED')             AS enqueued,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'PROCESSING')           AS processing,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'FAILED')               AS failed,
       (SELECT((SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SUCCEEDED') +
               (SELECT amount FROM jobrunr_job_counters jc WHERE jc.name = 'SUCCEEDED'))) AS succeeded,
       (SELECT count(*) FROM jobrunr_backgroundjobservers)                                AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM jobrunr_recurring_jobs)                                      AS nbrOfRecurringJobs
FROM jobrunr_jobs j;

-- jobrunr-migration: v005__update_job_stats_view.sql
DROP VIEW jobrunr_jobs_stats;

CREATE VIEW jobrunr_jobs_stats
AS
SELECT count(*)                                                                           AS total,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'AWAITING')             AS awaiting,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SCHEDULED')            AS scheduled,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'ENQUEUED')             AS enqueued,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'PROCESSING')           AS processing,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'FAILED')               AS failed,
       (SELECT((SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SUCCEEDED') +
               (SELECT amount FROM jobrunr_job_counters jc WHERE jc.name = 'SUCCEEDED'))) AS succeeded,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'DELETED')              AS deleted,
       (SELECT count(*) FROM jobrunr_backgroundjobservers)                                AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM jobrunr_recurring_jobs)                                      AS nbrOfRecurringJobs
FROM jobrunr_jobs j;

-- jobrunr-migration: v006__alter_table_jobs_add_recurringjob.sql
ALTER TABLE jobrunr_jobs
    ADD recurringJobId VARCHAR(128);
CREATE INDEX jobrunr_job_rci_idx ON jobrunr_jobs (recurringJobId);

-- jobrunr-migration: v007__alter_table_backgroundjobserver_add_delete_config.sql
ALTER TABLE jobrunr_backgroundjobservers
    ADD deleteSucceededJobsAfter VARCHAR(32);
ALTER TABLE jobrunr_backgroundjobservers
    ADD permanentlyDeleteJobsAfter VARCHAR(32);

-- jobrunr-migration: v008__alter_table_jobs_increase_jobAsJson_size.sql
-- Empty migration so all databases follow the same numbering

-- jobrunr-migration: v009__change_jobrunr_job_counters_to_jobrunr_metadata.sql
CREATE TABLE jobrunr_metadata
(
    id        varchar(156) PRIMARY KEY,
    name      varchar(92) NOT NULL,
    owner     varchar(64) NOT NULL,
    value     text        NOT NULL,
    createdAt TIMESTAMP   NOT NULL,
    updatedAt TIMESTAMP   NOT NULL
);

INSERT INTO jobrunr_metadata (id, name, owner, value, createdAt, updatedAt)
VALUES ('succeeded-jobs-counter-cluster', 'succeeded-jobs-counter', 'cluster',
        cast((SELECT amount FROM jobrunr_job_counters WHERE name = 'SUCCEEDED') AS char(10)), CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

DROP VIEW jobrunr_jobs_stats;
DROP TABLE jobrunr_job_counters;

CREATE VIEW jobrunr_jobs_stats
AS
SELECT count(*)                                                                 AS total,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'AWAITING')   AS awaiting,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SCHEDULED')  AS scheduled,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'ENQUEUED')   AS enqueued,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'PROCESSING') AS processing,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'FAILED')     AS failed,
       (SELECT((SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SUCCEEDED') +
               (SELECT cast(cast(value AS char(10)) AS decimal(10, 0))
                FROM jobrunr_metadata jm
                WHERE jm.id = 'succeeded-jobs-counter-cluster')))               AS succeeded,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'DELETED')    AS deleted,
       (SELECT count(*) FROM jobrunr_backgroundjobservers)                      AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM jobrunr_recurring_jobs)                            AS nbrOfRecurringJobs
FROM jobrunr_jobs j;

-- jobrunr-migration: v010__change_job_stats.sql
DROP VIEW jobrunr_jobs_stats;

CREATE VIEW jobrunr_jobs_stats
AS
SELECT count(*)                                                                 AS total,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'AWAITING')   AS awaiting,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SCHEDULED')  AS scheduled,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'ENQUEUED')   AS enqueued,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'PROCESSING') AS processing,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'FAILED')     AS failed,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'SUCCEEDED')  AS succeeded,
       (SELECT cast(cast(value AS char(10)) AS decimal(10, 0))
        FROM jobrunr_metadata jm
        WHERE jm.id = 'succeeded-jobs-counter-cluster')                         AS allTimeSucceeded,
       (SELECT count(*) FROM jobrunr_jobs jobs WHERE jobs.state = 'DELETED')    AS deleted,
       (SELECT count(*) FROM jobrunr_backgroundjobservers)                      AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM jobrunr_recurring_jobs)                            AS nbrOfRecurringJobs
FROM jobrunr_jobs j;

-- jobrunr-migration: v011__change_sqlserver_text_to_varchar.sql
-- Empty migration so all databases follow the same numbering

-- jobrunr-migration: v012__change_oracle_alter_jobrunr_metadata_column_size.sql
-- Empty migration so all databases follow the same numbering

-- jobrunr-migration: v013__alter_table_recurring_job_add_createdAt.sql
ALTER TABLE jobrunr_recurring_jobs
    ADD createdAt BIGINT NOT NULL DEFAULT '0';
CREATE INDEX jobrunr_recurring_job_created_at_idx ON jobrunr_recurring_jobs (createdAt);

-- jobrunr-migration: v014__improve_job_stats.sql
DROP VIEW jobrunr_jobs_stats;
CREATE VIEW jobrunr_jobs_stats
AS
with job_stat_results AS (SELECT state, count(*) AS count
    FROM jobrunr_jobs
    GROUP BY ROLLUP (state
)
)
SELECT coalesce((SELECT count FROM job_stat_results WHERE state IS NULL), 0)        AS total,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'SCHEDULED'), 0)  AS scheduled,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'ENQUEUED'), 0)   AS enqueued,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'PROCESSING'), 0) AS processing,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'FAILED'), 0)     AS failed,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'SUCCEEDED'), 0)  AS succeeded,
       coalesce((SELECT cast(cast(value AS char(10)) AS decimal(10, 0))
                 FROM jobrunr_metadata jm
                 WHERE jm.id = 'succeeded-jobs-counter-cluster'), 0)                AS allTimeSucceeded,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'DELETED'), 0)    AS deleted,
       (SELECT count(*) FROM jobrunr_backgroundjobservers)                          AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM jobrunr_recurring_jobs)                                AS nbrOfRecurringJobs;

DROP INDEX jobrunr_job_updated_at_idx;
CREATE INDEX jobrunr_jobs_state_updated_idx ON jobrunr_jobs (state ASC, updatedAt ASC);

-- jobrunr-migration: v015__alter_table_backgroundjobserver_add_name.sql
ALTER TABLE jobrunr_backgroundjobservers
    ADD name VARCHAR(128);

-- jobrunr-migration: v016__alter_jobs_stats_add_awaiting_jobs.sql
DROP VIEW jobrunr_jobs_stats;
CREATE VIEW jobrunr_jobs_stats
AS
with job_stat_results AS (SELECT state, count(*) AS count
    FROM jobrunr_jobs
    GROUP BY state
)
SELECT coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results), 0)                            AS total,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'AWAITING'), 0)   AS awaiting,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'SCHEDULED'), 0)  AS scheduled,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'ENQUEUED'), 0)   AS enqueued,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'PROCESSING'), 0) AS processing,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'PROCESSED'), 0)  AS processed,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'FAILED'), 0)     AS failed,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'SUCCEEDED'), 0)  AS succeeded,
       coalesce((SELECT cASt(cASt(value AS char(10)) AS decimal(10, 0))
                 FROM jobrunr_metadata jm
                 WHERE jm.id = 'succeeded-jobs-counter-cluster'),
                0)                                                                                        AS allTimeSucceeded,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'DELETED'), 0)    AS deleted,
       (SELECT count(*) FROM jobrunr_backgroundjobservers)                                                AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM jobrunr_recurring_jobs)                                                      AS nbrOfRecurringJobs;

-- jobrunr-migration: v017__change_job_stats_view_to_sql_security_invoker.sql
-- Empty migration so all databases follow the same numbering

-- jobrunr-migration-record
INSERT INTO jobrunr_migrations (id, script, installedOn)
VALUES
  (gen_random_uuid()::text, 'v000__create_migrations_table.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v001__create_job_table.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v002__create_recurring_job_table.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v003__create_background_job_server_table.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v004__create_job_stats_view.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v005__update_job_stats_view.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v006__alter_table_jobs_add_recurringjob.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v007__alter_table_backgroundjobserver_add_delete_config.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v008__alter_table_jobs_increase_jobAsJson_size.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v009__change_jobrunr_job_counters_to_jobrunr_metadata.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v010__change_job_stats.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v011__change_sqlserver_text_to_varchar.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v012__change_oracle_alter_jobrunr_metadata_column_size.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v013__alter_table_recurring_job_add_createdAt.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v014__improve_job_stats.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v015__alter_table_backgroundjobserver_add_name.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v016__alter_jobs_stats_add_awaiting_jobs.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')),
  (gen_random_uuid()::text, 'v017__change_job_stats_view_to_sql_security_invoker.sql', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'));

-- modulith-schema: v2/schema-postgresql.sql
CREATE TABLE IF NOT EXISTS event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);
