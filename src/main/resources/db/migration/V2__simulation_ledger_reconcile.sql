-- V2: reconcile the ledger schema with the entities.
-- Safe on the existing Hibernate-created schema and on a fresh V1 baseline.
-- The ledger tables are empty on the current database; no rows are rewritten.

-- tome_ledger: money to numeric(12,2), APR to numeric(7,4)
ALTER TABLE tome_ledger
    ADD COLUMN IF NOT EXISTS current_payment numeric(12,2);

ALTER TABLE tome_ledger
    ALTER COLUMN current_balance      TYPE numeric(12,2) USING current_balance::numeric(12,2),
    ALTER COLUMN minimum_debt_payment TYPE numeric(12,2) USING minimum_debt_payment::numeric(12,2),
    ALTER COLUMN credit_limit         TYPE numeric(12,2) USING credit_limit::numeric(12,2),
    ALTER COLUMN current_payment      TYPE numeric(12,2) USING current_payment::numeric(12,2),
    ALTER COLUMN apr                  TYPE numeric(7,4)  USING apr::numeric(7,4),
    ALTER COLUMN estimated_apr        TYPE numeric(7,4)  USING estimated_apr::numeric(7,4);

-- simulation_run: metrics to numeric(12,2); peak utilization to numeric(6,2)
ALTER TABLE simulation_run
    ALTER COLUMN extra_payment      TYPE numeric(12,2) USING extra_payment::numeric(12,2),
    ALTER COLUMN lump_sum_payment   TYPE numeric(12,2) USING lump_sum_payment::numeric(12,2),
    ALTER COLUMN total_interest     TYPE numeric(12,2) USING total_interest::numeric(12,2),
    ALTER COLUMN total_paid         TYPE numeric(12,2) USING total_paid::numeric(12,2),
    ALTER COLUMN final_balance      TYPE numeric(12,2) USING final_balance::numeric(12,2),
    ALTER COLUMN peak_utilization   TYPE numeric(6,2)  USING peak_utilization::numeric(6,2);

-- simulation_run_debt
ALTER TABLE simulation_run_debt
    ALTER COLUMN starting_balance TYPE numeric(12,2) USING starting_balance::numeric(12,2),
    ALTER COLUMN minimum_payment  TYPE numeric(12,2) USING minimum_payment::numeric(12,2),
    ALTER COLUMN current_payment  TYPE numeric(12,2) USING current_payment::numeric(12,2),
    ALTER COLUMN credit_limit     TYPE numeric(12,2) USING credit_limit::numeric(12,2),
    ALTER COLUMN apr              TYPE numeric(7,4)  USING apr::numeric(7,4);

-- simulation_month
ALTER TABLE simulation_month
    ALTER COLUMN starting_balance         TYPE numeric(12,2) USING starting_balance::numeric(12,2),
    ALTER COLUMN monthly_interest         TYPE numeric(12,2) USING monthly_interest::numeric(12,2),
    ALTER COLUMN minimum_due              TYPE numeric(12,2) USING minimum_due::numeric(12,2),
    ALTER COLUMN actual_payment           TYPE numeric(12,2) USING actual_payment::numeric(12,2),
    ALTER COLUMN extra_payment            TYPE numeric(12,2) USING extra_payment::numeric(12,2),
    ALTER COLUMN lump_sum_applied         TYPE numeric(12,2) USING lump_sum_applied::numeric(12,2),
    ALTER COLUMN remaining_balance        TYPE numeric(12,2) USING remaining_balance::numeric(12,2),
    ALTER COLUMN interest_paid            TYPE numeric(12,2) USING interest_paid::numeric(12,2),
    ALTER COLUMN amount_to_cover_interest TYPE numeric(12,2) USING amount_to_cover_interest::numeric(12,2),
    ALTER COLUMN utilization_pct          TYPE numeric(6,2)  USING utilization_pct::numeric(6,2);

-- simulation_month_debt
ALTER TABLE simulation_month_debt
    ALTER COLUMN starting_balance         TYPE numeric(12,2) USING starting_balance::numeric(12,2),
    ALTER COLUMN monthly_interest         TYPE numeric(12,2) USING monthly_interest::numeric(12,2),
    ALTER COLUMN minimum_due              TYPE numeric(12,2) USING minimum_due::numeric(12,2),
    ALTER COLUMN actual_payment           TYPE numeric(12,2) USING actual_payment::numeric(12,2),
    ALTER COLUMN extra_payment            TYPE numeric(12,2) USING extra_payment::numeric(12,2),
    ALTER COLUMN lump_sum_applied         TYPE numeric(12,2) USING lump_sum_applied::numeric(12,2),
    ALTER COLUMN remaining_balance        TYPE numeric(12,2) USING remaining_balance::numeric(12,2),
    ALTER COLUMN interest_paid            TYPE numeric(12,2) USING interest_paid::numeric(12,2),
    ALTER COLUMN amount_to_cover_interest TYPE numeric(12,2) USING amount_to_cover_interest::numeric(12,2),
    ALTER COLUMN utilization_pct          TYPE numeric(6,2)  USING utilization_pct::numeric(6,2);

-- FK/CASCADE constraints (missing because Hibernate created the tables first)
ALTER TABLE simulation_run_debt DROP CONSTRAINT IF EXISTS fk_simulation_run_debt_run;
ALTER TABLE simulation_run_debt
    ADD CONSTRAINT fk_simulation_run_debt_run
    FOREIGN KEY (run_id) REFERENCES simulation_run (id) ON DELETE CASCADE;

ALTER TABLE simulation_month DROP CONSTRAINT IF EXISTS fk_simulation_month_run;
ALTER TABLE simulation_month
    ADD CONSTRAINT fk_simulation_month_run
    FOREIGN KEY (run_id) REFERENCES simulation_run (id) ON DELETE CASCADE;

ALTER TABLE simulation_month_debt DROP CONSTRAINT IF EXISTS fk_simulation_month_debt_run;
ALTER TABLE simulation_month_debt
    ADD CONSTRAINT fk_simulation_month_debt_run
    FOREIGN KEY (run_id) REFERENCES simulation_run (id) ON DELETE CASCADE;

-- Supporting indexes
CREATE INDEX IF NOT EXISTS idx_simulation_run_user
    ON simulation_run (user_id);
CREATE INDEX IF NOT EXISTS idx_simulation_run_group
    ON simulation_run (comparison_group_id);
CREATE INDEX IF NOT EXISTS idx_simulation_month_run
    ON simulation_month (run_id, month_index);
CREATE INDEX IF NOT EXISTS idx_simulation_month_debt_run
    ON simulation_month_debt (run_id, month_index);
