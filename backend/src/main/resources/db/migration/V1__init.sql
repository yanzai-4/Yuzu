-- v0.0.2 🍊 Initial Yuzu schema (all tables).
--
-- Conventions
--   * Agent-scoped tables: PRIMARY KEY (agent_id, seq) clusters each agent's rows together, so per-agent
--     "latest N" and time-range reads are clustered range scans; KEY(seq) satisfies AUTO_INCREMENT and
--     UNIQUE(id) holds the business id "<name>-<agentHex>-<10hex>".
--   * Room-scoped tables: PRIMARY KEY (room_id, seq) with the same pattern.
--   * ids are ASCII/binary; text is utf8mb4_0900_ai_ci; times are DATETIME(3) in UTC; money is DECIMAL.
--   * ngram FULLTEXT drops tokens that CONTAIN stopwords ('a', 'i', ...), so stopwords are disabled
--     before any FULLTEXT DDL (the local instance also disables them globally).
SET SESSION innodb_ft_enable_stopword = OFF;

-- ---------------------------------------------------------------------------------------------------
-- Global
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE app_setting (
    k          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    v          JSON        NOT NULL,
    version    INT         NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (k)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE room (
    id         CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name       VARCHAR(80) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE human_user (
    id           CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    room_id      CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    username     VARCHAR(40) NOT NULL,
    color        VARCHAR(16) NOT NULL,
    created_at   DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_username (room_id, username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE agent (
    agent_id    CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    room_id     CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    citrus_name VARCHAR(40) NOT NULL,
    avatar_key  VARCHAR(40) NOT NULL,
    color       VARCHAR(16) NOT NULL,
    role        VARCHAR(32) NOT NULL,
    title       VARCHAR(80) NOT NULL,
    scope_text  TEXT        NOT NULL,
    persona     TEXT        NOT NULL,
    permissions JSON        NOT NULL,
    limits      JSON        NOT NULL,
    state       ENUM ('ACTIVE','PAUSED','RETIRED') NOT NULL DEFAULT 'ACTIVE',
    version     INT         NOT NULL DEFAULT 0,
    created_at  DATETIME(3) NOT NULL,
    updated_at  DATETIME(3) NOT NULL,
    PRIMARY KEY (agent_id),
    UNIQUE KEY uk_room_name (room_id, citrus_name),
    KEY k_room_state (room_id, state)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------------------------------
-- Group chat (room-scoped)
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE chat_message (
    room_id      CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id           VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    author_kind  ENUM ('HUMAN','AGENT','SYSTEM') NOT NULL,
    author_id    VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    author_name  VARCHAR(64) NOT NULL,
    kind         ENUM ('TEXT','WARNING','QUESTION_CARD','APPROVAL_CARD','REPORT','SYSTEM') NOT NULL,
    content      MEDIUMTEXT  NOT NULL,
    mentions     JSON        NOT NULL,
    mention_all  TINYINT(1)  NOT NULL DEFAULT 0,
    causal_depth SMALLINT    NOT NULL DEFAULT 0,
    closure      TINYINT(1)  NOT NULL DEFAULT 0,
    reply_to     VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    card_id      VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    fanout       TINYINT(1)  NOT NULL DEFAULT 1,
    stream_state ENUM ('NONE','STREAMING','DONE','STOPPED') NOT NULL DEFAULT 'NONE',
    trace_id     VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at   DATETIME(3) NOT NULL,
    PRIMARY KEY (room_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_room_created (room_id, created_at),
    FULLTEXT KEY ft_content (content) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE question_card (
    agent_id        CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id              VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    room_id         CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    kind            ENUM ('QUESTION','APPROVAL') NOT NULL,
    message_id      VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    batch_id        VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    tool_call_id    VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    prompt          TEXT        NOT NULL,
    options         JSON        NOT NULL,
    allow_other     TINYINT(1)  NOT NULL DEFAULT 1,
    status          ENUM ('OPEN','ANSWERED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'OPEN',
    answer          JSON        NULL,
    answered_by     VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    answered_by_name VARCHAR(64) NULL,
    created_at      DATETIME(3) NOT NULL,
    answered_at     DATETIME(3) NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_status (agent_id, status),
    KEY k_room_status (room_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------------------------------
-- Consciousness
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE pool_message (
    agent_id        CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id              VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    origin          ENUM ('EXTERNAL','SELF','SUBCONSCIOUS','REVIEW') NOT NULL,
    attribution     VARCHAR(500) NOT NULL,
    text            MEDIUMTEXT   NOT NULL,
    trace_id        VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    causal_depth    SMALLINT     NOT NULL DEFAULT 0,
    emitted_by_main TINYINT(1)   NOT NULL DEFAULT 0,
    run_id          VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    consumed_at     DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_run (agent_id, run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE main_run (
    agent_id     CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id           VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mode         ENUM ('ACT','THINK','END','FAILED','CANCELLED') NOT NULL,
    thought      MEDIUMTEXT  NOT NULL,
    actions      JSON        NOT NULL,
    next_thought TEXT        NULL,
    input_ids    JSON        NOT NULL,
    trace_id     VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    started_at   DATETIME(3) NOT NULL,
    ended_at     DATETIME(3) NOT NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE action_batch (
    agent_id   CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id         VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    run_id     VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    lineage_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actions    JSON        NOT NULL,
    status     ENUM ('REVIEWING','REJECTED','DISPATCHING','WAITING','DONE','FAILED','CANCELLED') NOT NULL,
    review     JSON        NULL,
    warning    TEXT        NULL,
    rejections SMALLINT    NOT NULL DEFAULT 0,
    trace_id   VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_run (agent_id, run_id),
    KEY k_status (agent_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------------------------------
-- Memory
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE working_memory_entry (
    agent_id   CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id         VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    run_id     VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    direction  ENUM ('IN','OUT') NOT NULL,
    origin     VARCHAR(16) NOT NULL,
    origin_ref VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    text       MEDIUMTEXT  NOT NULL,
    tokens     INT         NOT NULL DEFAULT 0,
    compacted  TINYINT(1)  NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    UNIQUE KEY uk_origin (agent_id, origin_ref),
    KEY k_compacted (agent_id, compacted, seq),
    KEY k_created (agent_id, created_at),
    FULLTEXT KEY ft_text (text) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE working_memory_digest (
    agent_id           CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    id                 VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    summary            MEDIUMTEXT      NOT NULL,
    covers_through_seq BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version            INT             NOT NULL DEFAULT 0,
    updated_at         DATETIME(3)     NOT NULL,
    PRIMARY KEY (agent_id),
    UNIQUE KEY uk_id (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE habit_memory (
    agent_id     CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id           VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name         VARCHAR(200) NOT NULL,
    scenario     TEXT         NOT NULL,
    technique    MEDIUMTEXT   NOT NULL,
    status       ENUM ('ACTIVE','SUPERSEDED','MERGED') NOT NULL DEFAULT 'ACTIVE',
    uses         INT          NOT NULL DEFAULT 0,
    last_used_at DATETIME(3)  NULL,
    content_hash BINARY(32)   NOT NULL,
    created_at   DATETIME(3)  NOT NULL,
    updated_at   DATETIME(3)  NOT NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    UNIQUE KEY uk_hash (agent_id, content_hash),
    KEY k_status (agent_id, status),
    FULLTEXT KEY ft_habit (name, scenario, technique) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE deep_memory (
    agent_id     CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id           VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title        VARCHAR(200) NOT NULL,
    content      MEDIUMTEXT   NOT NULL,
    keywords     VARCHAR(500) NOT NULL DEFAULT '',
    body_path    VARCHAR(255) NULL,
    about_from   DATETIME(3)  NULL,
    about_to     DATETIME(3)  NULL,
    source       ENUM ('SUBCONSCIOUS','MANUAL','SYSTEM') NOT NULL,
    status       ENUM ('ACTIVE','SUPERSEDED','MERGED') NOT NULL DEFAULT 'ACTIVE',
    content_hash BINARY(32)   NOT NULL,
    created_at   DATETIME(3)  NOT NULL,
    updated_at   DATETIME(3)  NOT NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    UNIQUE KEY uk_hash (agent_id, content_hash),
    KEY k_created (agent_id, created_at),
    KEY k_about (agent_id, about_from),
    FULLTEXT KEY ft_mem (title, content, keywords) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE memory_conflict (
    agent_id    CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id          VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    kind        ENUM ('HABIT','DEEP') NOT NULL,
    target_id   VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    old_copy    JSON        NOT NULL,
    new_copy    JSON        NOT NULL,
    reason      TEXT        NOT NULL,
    rounds_left SMALLINT    NOT NULL,
    status      ENUM ('OPEN','RESOLVED','EXPIRED') NOT NULL DEFAULT 'OPEN',
    resolution  TEXT        NULL,
    created_at  DATETIME(3) NOT NULL,
    updated_at  DATETIME(3) NOT NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_status (agent_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------------------------------
-- Tickets and task lists
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE ticket (
    room_id          CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id               VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title            VARCHAR(200) NOT NULL,
    detail           TEXT         NOT NULL,
    status           ENUM ('OPEN','ASSIGNED','IN_PROGRESS','DONE','APPROVED','CANCELLED') NOT NULL DEFAULT 'OPEN',
    creator_kind     ENUM ('HUMAN','AGENT') NOT NULL,
    creator_id       VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    creator_name     VARCHAR(64) NOT NULL,
    assignee_id      CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NULL,
    requester_id     VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    requester_name   VARCHAR(64) NULL,
    source_msg_id    VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    list_id          VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    version          INT         NOT NULL DEFAULT 0,
    created_at       DATETIME(3) NOT NULL,
    updated_at       DATETIME(3) NOT NULL,
    PRIMARY KEY (room_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_room_status (room_id, status),
    KEY k_assignee (assignee_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE task_list (
    agent_id       CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id             VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    goal           TEXT        NOT NULL,
    status         ENUM ('ACTIVE','AWAITING_APPROVAL','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
    publisher_kind ENUM ('HUMAN','AGENT') NOT NULL,
    publisher_id   VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    publisher_name VARCHAR(64) NOT NULL,
    ticket_id      VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    outcome        TEXT        NULL,
    approved_by    VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    version        INT         NOT NULL DEFAULT 0,
    created_at     DATETIME(3) NOT NULL,
    updated_at     DATETIME(3) NOT NULL,
    completed_at   DATETIME(3) NULL,
    approved_at    DATETIME(3) NULL,
    archived_at    DATETIME(3) NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_status (agent_id, status, archived_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE task_item (
    agent_id      CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id            VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    list_id       VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ord           INT         NOT NULL,
    text          TEXT        NOT NULL,
    state         ENUM ('TODO','DOING','DONE','STRUCK') NOT NULL DEFAULT 'TODO',
    note          TEXT        NULL,
    struck_reason TEXT        NULL,
    created_at    DATETIME(3) NOT NULL,
    updated_at    DATETIME(3) NOT NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_list (agent_id, list_id, ord)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------------------------------
-- Actions and tracing
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE tool_call (
    agent_id       CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id             VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    batch_id       VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action_index   SMALLINT    NOT NULL,
    tool           VARCHAR(40) NOT NULL,
    instruction    TEXT        NOT NULL,
    args           JSON        NOT NULL,
    status         ENUM ('PENDING','RUNNING','OK','ERROR','DENIED','CANCELLED','WAITING') NOT NULL,
    trusted        TINYINT(1)  NOT NULL DEFAULT 0,
    result_preview TEXT        NULL,
    result_path    VARCHAR(255) NULL,
    mask_reasons   JSON        NULL,
    error          TEXT        NULL,
    trace_id       VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    started_at     DATETIME(3) NOT NULL,
    completed_at   DATETIME(3) NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_batch (agent_id, batch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE llm_call (
    agent_id           CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id                 VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    module             VARCHAR(32) NOT NULL,
    tier               ENUM ('IMPORTANT','DEFAULT','LIGHT') NOT NULL,
    model              VARCHAR(120) NOT NULL,
    strategy           VARCHAR(24) NOT NULL,
    attempt            SMALLINT    NOT NULL,
    prompt_tokens      INT         NOT NULL DEFAULT 0,
    cached_tokens      INT         NOT NULL DEFAULT 0,
    cache_write_tokens INT         NOT NULL DEFAULT 0,
    completion_tokens  INT         NOT NULL DEFAULT 0,
    reasoning_tokens   INT         NOT NULL DEFAULT 0,
    estimated          TINYINT(1)  NOT NULL DEFAULT 0,
    latency_ms         INT         NOT NULL DEFAULT 0,
    ttft_ms            INT         NULL,
    status             VARCHAR(24) NOT NULL,
    error              VARCHAR(500) NULL,
    trace_id           VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    payload_path       VARCHAR(255) NULL,
    created_at         DATETIME(3) NOT NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_created (created_at),
    KEY k_module (agent_id, module, seq),
    KEY k_trace (trace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE module_event (
    agent_id       CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id             VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    module         VARCHAR(32) NOT NULL,
    phase          VARCHAR(16) NOT NULL,
    text           VARCHAR(1000) NOT NULL,
    detail         JSON        NULL,
    trace_id       VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    span_id        VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    parent_span_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at     DATETIME(3) NOT NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_trace (trace_id),
    KEY k_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE security_incident (
    agent_id   CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id         VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    stage      ENUM ('INBOUND','OUTBOUND','BEHAVIOR','GUARD','HIGH_RISK') NOT NULL,
    verdict    VARCHAR(24) NOT NULL,
    reasons    JSON        NOT NULL,
    excerpt    TEXT        NOT NULL,
    trace_id   VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------------------------------
-- Simulated world (fake email, fake trading)
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE fake_email (
    agent_id     CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id           VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    direction    ENUM ('IN','OUT') NOT NULL,
    from_addr    VARCHAR(200) NOT NULL,
    to_addr      VARCHAR(500) NOT NULL,
    subject      VARCHAR(300) NOT NULL,
    body         MEDIUMTEXT   NOT NULL,
    status       ENUM ('RECEIVED','SENT','BLOCKED') NOT NULL,
    dedupe_hash  BINARY(32)   NULL,
    trace_id     VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at   DATETIME(3)  NOT NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_created (agent_id, created_at),
    KEY k_dedupe (agent_id, dedupe_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE fake_trade (
    agent_id   CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    seq        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id         VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    symbol     VARCHAR(16)    NOT NULL,
    side       ENUM ('BUY','SELL') NOT NULL,
    qty        DECIMAL(18, 4) NOT NULL,
    price      DECIMAL(18, 4) NOT NULL,
    notional   DECIMAL(18, 2) NOT NULL,
    status     ENUM ('PENDING_APPROVAL','EXECUTED','REJECTED','BLOCKED') NOT NULL,
    card_id    VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    reason     TEXT           NULL,
    trace_id   VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(3)    NOT NULL,
    updated_at DATETIME(3)    NOT NULL,
    PRIMARY KEY (agent_id, seq),
    KEY k_seq (seq),
    UNIQUE KEY uk_id (id),
    KEY k_created (agent_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE fake_portfolio (
    agent_id   CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cash       DECIMAL(18, 2) NOT NULL,
    positions  JSON           NOT NULL,
    version    INT            NOT NULL DEFAULT 0,
    updated_at DATETIME(3)    NOT NULL,
    PRIMARY KEY (agent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------------------------------
-- Seed: the default workgroup room.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO room (id, name, created_at) VALUES ('room-0001', 'Citrus HQ', UTC_TIMESTAMP(3));
