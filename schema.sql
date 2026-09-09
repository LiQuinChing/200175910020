CREATE DATABASE IF NOT EXISTS training_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE training_management;

CREATE TABLE department (
    department_id BIGINT NOT NULL PRIMARY KEY,
    department_name VARCHAR(200) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE officer (
    officer_id BIGINT NOT NULL PRIMARY KEY,
    officer_name VARCHAR(200) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE training_programme (
    training_id BIGINT NOT NULL PRIMARY KEY,
    training_name VARCHAR(200) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE training_nomination (
    nomination_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    training_id BIGINT NOT NULL,
    officer_id BIGINT NOT NULL,
    nominated_by_department_id BIGINT NOT NULL,
    nomination_date TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    status VARCHAR(20) NOT NULL DEFAULT 'NOMINATED',
    UNIQUE KEY uq_training_officer (training_id, officer_id),
    CONSTRAINT fk_nomination_training FOREIGN KEY (training_id) REFERENCES training_programme(training_id),
    CONSTRAINT fk_nomination_officer FOREIGN KEY (officer_id) REFERENCES officer(officer_id),
    CONSTRAINT fk_nomination_department FOREIGN KEY (nominated_by_department_id) REFERENCES department(department_id),
    CONSTRAINT chk_nomination_status CHECK (status IN ('NOMINATED', 'APPROVED', 'REJECTED', 'CANCELLED'))
) ENGINE=InnoDB;

