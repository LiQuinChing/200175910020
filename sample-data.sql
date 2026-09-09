USE training_management;
INSERT INTO department (department_id, department_name, code) VALUES (10, 'Finance', 'FIN'), (20, 'Administration', 'ADM');
INSERT INTO officer (officer_id, officer_name, department_id) VALUES (1001, 'Example Officer', 10), (1002, 'Example Officer', 20);
INSERT INTO training_programme (training_id, training_name, training_date, venue, max_participants)
VALUES (101, 'Public Administration', '2026-10-15', 'Main Auditorium', 40),
       (102, 'Procurement', '2026-10-20', 'Conference Room', 40);
