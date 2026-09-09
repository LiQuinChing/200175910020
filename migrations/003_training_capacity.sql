-- Task 2: run ONCE after 002_management.sql, with the old application stopped.
-- Keep a backup before upgrading. MySQL DDL commits independently.
USE training_management;

ALTER TABLE training_nomination
    RENAME COLUMN nomination_date TO nomination_datetime,
    DROP CHECK chk_nomination_status,
    ALTER COLUMN status DROP DEFAULT;

-- Preserve existing timestamps and IDs. Allocate legacy active nominations in their
-- stored submission order. Missing capacity gives zero confirmed seats until configured.
UPDATE training_nomination n
JOIN (
    SELECT nomination_id,
           ROW_NUMBER() OVER (PARTITION BY training_id ORDER BY nomination_datetime, nomination_id) AS queue_position
    FROM training_nomination
    WHERE status IN ('NOMINATED', 'APPROVED')
) ranked ON ranked.nomination_id = n.nomination_id
JOIN training_programme t ON t.training_id = n.training_id
SET n.status = CASE WHEN ranked.queue_position <= COALESCE(t.max_participants, 0)
                    THEN 'CONFIRMED' ELSE 'WAITING_LIST' END;

UPDATE training_nomination SET status = 'CANCELLED' WHERE status = 'REJECTED';

ALTER TABLE training_nomination
    MODIFY nomination_datetime TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT chk_nomination_status CHECK (status IN ('CONFIRMED', 'WAITING_LIST', 'CANCELLED')),
    ADD INDEX ix_nomination_queue (training_id, status, nomination_datetime, nomination_id);

-- uq_training_officer is never dropped or changed.

