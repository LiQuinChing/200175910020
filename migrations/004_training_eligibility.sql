-- Safe for installations where the Task 3 SQL was already applied manually.
-- No existing officers, rules or nominations are removed or replaced.
USE training_management;

SET @eligibility_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'officer' AND column_name = 'grade'),
 'SELECT 1', 'ALTER TABLE officer ADD COLUMN grade VARCHAR(100) NULL');
PREPARE eligibility_stmt FROM @eligibility_ddl;
EXECUTE eligibility_stmt;
DEALLOCATE PREPARE eligibility_stmt;

SET @eligibility_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'officer' AND column_name = 'date_of_joining'),
 'SELECT 1', 'ALTER TABLE officer ADD COLUMN date_of_joining DATE NULL');
PREPARE eligibility_stmt FROM @eligibility_ddl;
EXECUTE eligibility_stmt;
DEALLOCATE PREPARE eligibility_stmt;

CREATE TABLE IF NOT EXISTS training_eligibility_rule (
    rule_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    training_id BIGINT NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    `operator` VARCHAR(20) NOT NULL,
    rule_value VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_eligibility_training FOREIGN KEY (training_id) REFERENCES training_programme(training_id)
);

SET @eligibility_ddl = IF(EXISTS(SELECT 1 FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'training_eligibility_rule' AND column_name = 'training_id' AND seq_in_index = 1),
 'SELECT 1', 'CREATE INDEX idx_eligibility_training ON training_eligibility_rule(training_id)');
PREPARE eligibility_stmt FROM @eligibility_ddl;
EXECUTE eligibility_stmt;
DEALLOCATE PREPARE eligibility_stmt;

