-- One-time repair for the older, empty schema found in the local demonstration database.
-- For a new installation use schema.sql instead. Do not run both scripts.
USE training_management;

CREATE TABLE department (
    department_id BIGINT NOT NULL PRIMARY KEY,
    department_name VARCHAR(200) NOT NULL
) ENGINE=InnoDB;

ALTER TABLE training_programme RENAME COLUMN title TO training_name;

ALTER TABLE training_nomination
    MODIFY nominated_by_department_id BIGINT NOT NULL,
    MODIFY nomination_date TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY status VARCHAR(20) NOT NULL DEFAULT 'NOMINATED',
    ADD CONSTRAINT fk_nomination_department FOREIGN KEY (nominated_by_department_id)
        REFERENCES department(department_id),
    ADD CONSTRAINT chk_nomination_status CHECK (status IN ('NOMINATED', 'APPROVED', 'REJECTED', 'CANCELLED'));

INSERT INTO department (department_id, department_name) VALUES (10, 'Finance'), (20, 'Administration');
INSERT INTO officer (officer_id, officer_name) VALUES (1001, 'Example Officer'), (1002, 'Example Officer');
INSERT INTO training_programme (training_id, training_name) VALUES (101, 'Public Administration'), (102, 'Procurement');
