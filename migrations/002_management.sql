-- Upgrade an existing installation without deleting records.
-- Run with mysql from the Task1 directory: SOURCE migrations/002_management.sql;
USE training_management;

SET @management_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'department' AND column_name = 'code'),
 'SELECT 1', 'ALTER TABLE department ADD COLUMN code VARCHAR(30) NULL');
PREPARE management_stmt FROM @management_ddl;
EXECUTE management_stmt;
DEALLOCATE PREPARE management_stmt;

SET @management_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'training_programme' AND column_name = 'training_date'),
 'SELECT 1', 'ALTER TABLE training_programme ADD COLUMN training_date DATE NULL');
PREPARE management_stmt FROM @management_ddl;
EXECUTE management_stmt;
DEALLOCATE PREPARE management_stmt;

SET @management_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'training_programme' AND column_name = 'venue'),
 'SELECT 1', 'ALTER TABLE training_programme ADD COLUMN venue VARCHAR(200) NULL');
PREPARE management_stmt FROM @management_ddl;
EXECUTE management_stmt;
DEALLOCATE PREPARE management_stmt;

SET @management_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'training_programme' AND column_name = 'max_participants'),
 'SELECT 1', 'ALTER TABLE training_programme ADD COLUMN max_participants INT NULL');
PREPARE management_stmt FROM @management_ddl;
EXECUTE management_stmt;
DEALLOCATE PREPARE management_stmt;

SET @management_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'officer' AND column_name = 'department_id'),
 'SELECT 1', 'ALTER TABLE officer ADD COLUMN department_id BIGINT NULL');
PREPARE management_stmt FROM @management_ddl;
EXECUTE management_stmt;
DEALLOCATE PREPARE management_stmt;

SET @management_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'officer' AND column_name = 'designation'),
 'SELECT 1', 'ALTER TABLE officer ADD COLUMN designation VARCHAR(200) NULL');
PREPARE management_stmt FROM @management_ddl;
EXECUTE management_stmt;
DEALLOCATE PREPARE management_stmt;

SET @management_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'officer' AND column_name = 'email'),
 'SELECT 1', 'ALTER TABLE officer ADD COLUMN email VARCHAR(254) NULL');
PREPARE management_stmt FROM @management_ddl;
EXECUTE management_stmt;
DEALLOCATE PREPARE management_stmt;

UPDATE department SET code = CONCAT('DEPT-', department_id) WHERE code IS NULL;
ALTER TABLE department MODIFY code VARCHAR(30) NOT NULL;
-- MySQL requires referencing foreign keys to be removed before changing identity attributes.
-- Recreate them immediately; no nomination rows or unique keys are changed.
ALTER TABLE training_nomination DROP FOREIGN KEY fk_nomination_department;
SET @management_ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints
 WHERE constraint_schema = DATABASE() AND table_name = 'officer' AND constraint_name = 'fk_officer_department'),
 'ALTER TABLE officer DROP FOREIGN KEY fk_officer_department', 'SELECT 1');
PREPARE management_stmt FROM @management_ddl;
EXECUTE management_stmt;
DEALLOCATE PREPARE management_stmt;
ALTER TABLE department MODIFY department_id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE training_nomination ADD CONSTRAINT fk_nomination_department
 FOREIGN KEY (nominated_by_department_id) REFERENCES department(department_id);
ALTER TABLE officer ADD CONSTRAINT fk_officer_department
 FOREIGN KEY (department_id) REFERENCES department(department_id);

ALTER TABLE training_nomination DROP FOREIGN KEY fk_nomination_training;
ALTER TABLE training_programme MODIFY training_id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE training_nomination ADD CONSTRAINT fk_nomination_training
 FOREIGN KEY (training_id) REFERENCES training_programme(training_id);

SET @management_ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints
 WHERE constraint_schema = DATABASE() AND table_name = 'department' AND constraint_name = 'uq_department_code'),
 'SELECT 1', 'ALTER TABLE department ADD CONSTRAINT uq_department_code UNIQUE (code)');
PREPARE management_stmt FROM @management_ddl;
EXECUTE management_stmt;
DEALLOCATE PREPARE management_stmt;

SET @management_ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints
 WHERE constraint_schema = DATABASE() AND table_name = 'officer' AND constraint_name = 'fk_officer_department'),
 'SELECT 1', 'ALTER TABLE officer ADD CONSTRAINT fk_officer_department FOREIGN KEY (department_id) REFERENCES department(department_id)');
PREPARE management_stmt FROM @management_ddl;
EXECUTE management_stmt;
DEALLOCATE PREPARE management_stmt;

SET @management_ddl = IF(EXISTS(SELECT 1 FROM information_schema.table_constraints
 WHERE constraint_schema = DATABASE() AND table_name = 'training_programme' AND constraint_name = 'chk_training_capacity'),
 'SELECT 1', 'ALTER TABLE training_programme ADD CONSTRAINT chk_training_capacity CHECK (max_participants > 0)');
PREPARE management_stmt FROM @management_ddl;
EXECUTE management_stmt;
DEALLOCATE PREPARE management_stmt;
