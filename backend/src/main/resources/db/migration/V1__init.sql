-- V1: Initial schema

CREATE TABLE users (
    id              UUID         PRIMARY KEY,
    email           TEXT         NOT NULL,
    password_hash   TEXT         NOT NULL,
    is_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_suspended    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_admin        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT users_email_format_chk      CHECK (length(email) <= 254 AND email = lower(email)),
    CONSTRAINT users_deleted_at_consistent CHECK ((is_deleted = TRUE) = (deleted_at IS NOT NULL))
);

-- Login is the hot lookup (keyed by email, must ignore soft-deleted rows). The partial unique gives
-- both the uniqueness constraint (one active account per email) and the lookup index in a single
-- object. Re-registration of a soft-deleted email is permitted because deleted rows are not in the
-- index.
CREATE UNIQUE INDEX users_email_active_uidx ON users (email) WHERE is_deleted = FALSE;

CREATE TABLE sessions (
    id                  UUID         PRIMARY KEY,
    user_id             UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    session_token_hash  TEXT         NOT NULL,
    ip_address          TEXT,
    user_agent          TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ  NOT NULL,
    last_activity_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Every authenticated request hashes the cookie token and resolves the session by hash.
-- Uniqueness + lookup in one object.
CREATE UNIQUE INDEX sessions_token_hash_uidx ON sessions (session_token_hash);
-- Used by (a) password-reset / account-deletion flows that invalidate all sessions for a user,
-- and (b) the periodic sweep that purges expired rows by user.
CREATE INDEX        sessions_user_id_idx     ON sessions (user_id);

CREATE TABLE email_verifications (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    token_hash   TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ  NOT NULL,
    verified_at  TIMESTAMPTZ
);

-- Single hot path: user clicks email link, server SHA-256s the token and resolves by hash.
-- Uniqueness prevents collisions in randomly-generated tokens.
CREATE UNIQUE INDEX email_verifications_token_hash_uidx ON email_verifications (token_hash);

CREATE TABLE password_resets (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    token_hash  TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    claimed_at  TIMESTAMPTZ
);

-- Single hot path: SHA-256 the token from the email link, resolve by hash.
CREATE UNIQUE INDEX password_resets_token_hash_uidx  ON password_resets (token_hash);
-- Serves the NFR-Sec7 "≤ 3 / h / email" rate-limit check, which counts recent resets for a user.
CREATE        INDEX password_resets_user_created_idx ON password_resets (user_id, created_at DESC);

CREATE TABLE symbols (
    ticker             TEXT         PRIMARY KEY,
    exchange           TEXT         NOT NULL,
    name               TEXT,
    is_delisted        BOOLEAN      NOT NULL DEFAULT FALSE,
    last_vendor_check  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT symbols_ticker_format_chk CHECK (ticker ~ '^[A-Z.]{1,10}$'),
    CONSTRAINT symbols_exchange_chk      CHECK (exchange IN ('NYSE', 'NASDAQ'))
);

CREATE TABLE price_history (
    ticker             TEXT             NOT NULL REFERENCES symbols (ticker) ON DELETE RESTRICT,
    trade_date         DATE             NOT NULL,
    close_price        NUMERIC(20, 6)   NOT NULL,
    is_split_adjusted  BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (ticker, trade_date),
    CONSTRAINT price_history_close_positive_chk CHECK (close_price > 0)
);

-- The portfolio module assembles holdings by joining the latest close per ticker (a LATERAL
-- "most recent row" lookup). Descending trade_date on the leaf makes this an index seek instead
-- of a scan.
CREATE INDEX price_history_ticker_date_desc_idx ON price_history (ticker, trade_date DESC);

CREATE TABLE backfill_jobs (
    id             UUID         PRIMARY KEY,
    ticker         TEXT         NOT NULL REFERENCES symbols (ticker) ON DELETE RESTRICT,
    user_id        UUID         NOT NULL REFERENCES users (id)       ON DELETE RESTRICT,
    status         TEXT         NOT NULL DEFAULT 'pending',
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,
    price_count    INTEGER,
    error_message  TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    CONSTRAINT backfill_jobs_status_chk      CHECK (status IN ('pending', 'in_progress', 'completed', 'failed')),
    CONSTRAINT backfill_jobs_date_range_chk  CHECK (end_date >= start_date),
    CONSTRAINT backfill_jobs_price_count_chk CHECK (price_count IS NULL OR price_count >= 0)
);

-- Worker polls "what's next?". A partial index on the small active set keeps the lookup cheap
-- as completed/failed rows accumulate.
CREATE        INDEX backfill_jobs_pending_idx        ON backfill_jobs (created_at) WHERE status = 'pending';
-- Structurally prevents two concurrent transaction handlers from inserting duplicate jobs for the
-- same ticker. Insert collision → catch in RecordTransaction as a no-op (the position is already
-- "pending").
CREATE UNIQUE INDEX backfill_jobs_ticker_active_uidx ON backfill_jobs (ticker)     WHERE status IN ('pending', 'in_progress');

CREATE TABLE transactions (
    id          UUID            PRIMARY KEY,
    user_id     UUID            NOT NULL REFERENCES users (id)       ON DELETE RESTRICT,
    ticker      TEXT            NOT NULL REFERENCES symbols (ticker) ON DELETE RESTRICT,
    operation   TEXT            NOT NULL,
    quantity    NUMERIC(20, 6)  NOT NULL,
    trade_date  DATE            NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT transactions_operation_chk         CHECK (operation IN ('BUY', 'SELL')),
    CONSTRAINT transactions_quantity_positive_chk CHECK (quantity > 0),
    CONSTRAINT transactions_trade_date_chk        CHECK (trade_date <= CURRENT_DATE)
);

-- Sell-as-of-trade-date validation aggregates signed quantity for (user, ticker) where
-- trade_date ≤ candidate. The index leaf order matches the predicate.
CREATE INDEX transactions_user_ticker_date_idx     ON transactions (user_id, ticker, trade_date);
-- The "list my transactions" endpoint orders by trade_date DESC filtered by user. Index-only
-- scan friendly.
CREATE INDEX transactions_user_trade_date_desc_idx ON transactions (user_id, trade_date DESC);

CREATE TABLE holdings (
    user_id     UUID            NOT NULL REFERENCES users (id)       ON DELETE RESTRICT,
    ticker      TEXT            NOT NULL REFERENCES symbols (ticker) ON DELETE RESTRICT,
    quantity    NUMERIC(20, 6)  NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, ticker),
    CONSTRAINT holdings_quantity_positive_chk CHECK (quantity > 0)
);

CREATE TABLE portfolio_snapshots (
    user_id        UUID            NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    snapshot_date  DATE            NOT NULL,
    total_value    NUMERIC(20, 2)  NOT NULL,
    PRIMARY KEY (user_id, snapshot_date),
    CONSTRAINT portfolio_snapshots_total_value_chk CHECK (total_value >= 0)
);

CREATE TABLE alert_rules (
    id           UUID           PRIMARY KEY,
    user_id      UUID           NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    direction    TEXT           NOT NULL,
    threshold    NUMERIC(5, 1)  NOT NULL,
    window_days  INTEGER        NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT alert_rules_direction_chk   CHECK (direction IN ('UP', 'DOWN')),
    CONSTRAINT alert_rules_threshold_chk   CHECK (threshold BETWEEN 0.5 AND 100),
    CONSTRAINT alert_rules_window_days_chk CHECK (window_days IN (1, 7, 30, 90, 365, 1095, 1825))
);

-- Enforces BRD §2.6 "no exact-duplicate active rule" and serves the pre-insert duplicate check.
CREATE UNIQUE INDEX alert_rules_user_signature_uidx ON alert_rules (user_id, direction, threshold, window_days);
-- Used by the nightly evaluation fan-out ("every active rule grouped by user") and by the user's
-- "list active rules" endpoint.
CREATE        INDEX alert_rules_user_id_idx         ON alert_rules (user_id);

CREATE TABLE alert_firings (
    id                     UUID            PRIMARY KEY,
    user_id                UUID            NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    rule_id                UUID            NOT NULL,
    direction              TEXT            NOT NULL,
    threshold              NUMERIC(5, 1)   NOT NULL,
    window_days            INTEGER         NOT NULL,
    fired_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    portfolio_value_start  NUMERIC(20, 2)  NOT NULL,
    portfolio_value_end    NUMERIC(20, 2)  NOT NULL,
    percent_change         NUMERIC(10, 2)  NOT NULL,
    window_start_date      DATE            NOT NULL,
    window_end_date        DATE            NOT NULL,
    CONSTRAINT alert_firings_direction_chk    CHECK (direction IN ('UP', 'DOWN')),
    CONSTRAINT alert_firings_value_start_chk  CHECK (portfolio_value_start >= 0),
    CONSTRAINT alert_firings_value_end_chk    CHECK (portfolio_value_end   >= 0),
    CONSTRAINT alert_firings_window_range_chk CHECK (window_end_date >= window_start_date)
);

-- The "GET /alert-firings" history endpoint orders by fired_at DESC filtered by user; supports
-- index-only scan for the common page size of 50.
CREATE INDEX alert_firings_user_fired_desc_idx ON alert_firings (user_id, fired_at DESC);

CREATE TABLE outbox (
    id                      UUID         PRIMARY KEY,
    aggregate_id            UUID         NOT NULL,
    event_type              TEXT         NOT NULL,
    payload                 JSONB        NOT NULL,
    idempotence_key         TEXT         NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at            TIMESTAMPTZ,
    error_count             INTEGER      NOT NULL DEFAULT 0,
    last_error              TEXT,
    published_by_worker_id  TEXT,
    CONSTRAINT outbox_event_type_chk  CHECK (event_type IN ('email.verification', 'email.password_reset', 'email.digest')),
    CONSTRAINT outbox_error_count_chk CHECK (error_count >= 0)
);

-- Prevents the vendor from sending the same message twice if the worker retries after a partial
-- failure.
CREATE UNIQUE INDEX outbox_idempotence_key_uidx ON outbox (idempotence_key);
-- The poller's hot query is "next unpublished, oldest first". A partial index keeps the working
-- set tiny.
CREATE        INDEX outbox_pending_idx          ON outbox (created_at) WHERE published_at IS NULL;
-- Ops queries: "what did we send this user lately?". Cheap insurance for support investigations.
CREATE        INDEX outbox_aggregate_id_idx     ON outbox (aggregate_id);

CREATE TABLE eod_pipeline_runs (
    id                    UUID         PRIMARY KEY,
    run_date              DATE         NOT NULL,
    trigger               TEXT         NOT NULL,
    status                TEXT         NOT NULL,
    started_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at           TIMESTAMPTZ,
    step_symbols_status   TEXT         NOT NULL DEFAULT 'pending',
    step_prices_status    TEXT         NOT NULL DEFAULT 'pending',
    step_evaluate_status  TEXT         NOT NULL DEFAULT 'pending',
    error_message         TEXT,
    CONSTRAINT eod_pipeline_runs_trigger_chk       CHECK (trigger IN ('cron', 'admin')),
    CONSTRAINT eod_pipeline_runs_status_chk        CHECK (status  IN ('pending', 'in_progress', 'succeeded', 'failed')),
    CONSTRAINT eod_pipeline_runs_step_symbols_chk  CHECK (step_symbols_status  IN ('pending', 'in_progress', 'succeeded', 'failed', 'skipped')),
    CONSTRAINT eod_pipeline_runs_step_prices_chk   CHECK (step_prices_status   IN ('pending', 'in_progress', 'succeeded', 'failed', 'skipped')),
    CONSTRAINT eod_pipeline_runs_step_evaluate_chk CHECK (step_evaluate_status IN ('pending', 'in_progress', 'succeeded', 'failed', 'skipped'))
);

-- The admin "list recent runs" view sorts strictly by started_at DESC.
CREATE        INDEX eod_pipeline_runs_started_desc_idx ON eod_pipeline_runs (started_at DESC);
-- Structurally prevents concurrent cron + admin triggers from creating two active runs for the
-- same run_date. Unique-violation on insert → 409 CONFLICT.
CREATE UNIQUE INDEX eod_pipeline_runs_in_progress_uidx ON eod_pipeline_runs (run_date)
    WHERE status IN ('pending', 'in_progress');

CREATE TABLE admin_audit_log (
    id              UUID         PRIMARY KEY,
    actor_id        UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    action          TEXT         NOT NULL,
    target_user_id  UUID                  REFERENCES users (id) ON DELETE RESTRICT,
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT admin_audit_log_action_chk CHECK (action IN ('SUSPEND', 'UNSUSPEND', 'DELETE', 'EOD_RUN', 'EOD_STEP_RERUN')),
    CONSTRAINT admin_audit_log_target_user_chk
        CHECK ((action IN ('EOD_RUN', 'EOD_STEP_RERUN')) OR (target_user_id IS NOT NULL))
);

-- Default sort of the admin audit-log UI.
CREATE INDEX admin_audit_log_created_desc_idx ON admin_audit_log (created_at DESC);
-- "Filter by admin actor" page.
CREATE INDEX admin_audit_log_actor_idx        ON admin_audit_log (actor_id,       created_at DESC);
-- "What actions targeted this user" page.
CREATE INDEX admin_audit_log_target_idx       ON admin_audit_log (target_user_id, created_at DESC);
