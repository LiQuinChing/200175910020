# Government Training Management API

Java 17+, Maven 3.6.3+, MySQL 8.0.16+; Spring Boot 3.5 with JdbcTemplate (no JPA).
Spring Boot requirements: https://docs.spring.io/spring-boot/3.5/system-requirements.html

## Setup

Run the following in the MySQL client as a database administrator for a fresh database:

```sql
SOURCE C:/Users/Vihara Ching/Desktop/financeTask1/Task1/schema.sql;
-- Optional demonstration records:
SOURCE C:/Users/Vihara Ching/Desktop/financeTask1/Task1/sample-data.sql;
CREATE USER 'training_app'@'localhost' IDENTIFIED BY 'replace-with-your-password';
GRANT SELECT, INSERT ON training_management.* TO 'training_app'@'localhost';
```

The schema is intended for initial installation; it does not drop existing data or modify an existing schema.
Officer, training and department records must exist before nominations are created.
Their primary keys are globally unique IDs; officer names need not be unique.

From this project directory in PowerShell:

```powershell
$env:DB_PASSWORD = 'replace-with-your-password'
$env:DB_USERNAME = 'training_app'
$env:DB_URL = 'jdbc:mysql://localhost:3306/training_management'
mvn spring-boot:run
```

Database credentials are supplied through environment variables. Automatic schema initialization is disabled.

## Simple frontend

After database setup (including sample-data.sql) and starting Spring Boot, open
http://localhost:8080/ in your browser. Spring Boot serves the frontend from
src/main/resources/static/index.html, style.css and script.js. Open it through
Spring Boot rather than directly as a local file so fetch requests share the API origin.

The dropdowns use the sample programme IDs 101/102 and department IDs 10/20.
Update their options in index.html if you change the demonstration records.
Officer Name is an optional reference field and is not sent or saved; the table
uses registered names returned by the backend. Only Officer ID identifies an officer.
The frontend submits departmentId; the API also continues to accept
nominatedByDepartmentId. Nomination responses include officerName, trainingName
and departmentName from the related database tables.

To demonstrate duplicates, submit officer 1001 for programme 101 from Finance,
then submit the same pair from Administration. The second request shows a red
duplicate warning and does not create another row.

## API reference

| Method | Path | Result |
| --- | --- | --- |
| POST | /api/nominations | Create; 201 with Location header |
| GET | /api/nominations/{id} | Retrieve; 404 if missing |
| GET | /api/nominations?trainingId=101&limit=50&offset=0 | List, optionally filtered by training |

List defaults: limit 50, offset 0; maximum limit 200. Results are ordered by nomination ID.

```powershell
$body = @{ trainingId = 101; officerId = 1001; nominatedByDepartmentId = 10 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/nominations -ContentType 'application/json' -Body $body
Invoke-RestMethod -Uri http://localhost:8080/api/nominations/1
Invoke-RestMethod -Uri 'http://localhost:8080/api/nominations?trainingId=101'
```

The server assigns the nomination ID, date and initial NOMINATED status.
Repeating the POST with department 20 still returns HTTP 409:

```json
{
  "timestamp": "2026-09-09T00:00:00Z",
  "status": 409,
  "code": "DUPLICATE_NOMINATION",
  "message": "Officer is already nominated for this training programme."
}
```

Missing/nonpositive IDs, malformed input and missing foreign-key references return 400.

## Duplicate protection

The service first checks training_id and officer_id only. The MySQL constraint
`UNIQUE KEY uq_training_officer (training_id, officer_id)` is the final protection
when concurrent requests both pass the check. Spring translates a rejected insert
into DuplicateKeyException; the service converts that to the same 409 response.
Department, officer name and status never participate in duplicate detection.
Even cancelled or rejected nominations retain their unique pair.

## Verification

```powershell
mvn test
mvn package
```

Tests exercise create/retrieve, cross-department duplicates, officers sharing names,
different trainings, validation, foreign keys, concurrent inserts after both prechecks
pass, and service translation of a database duplicate. They run against H2 in MySQL
mode using the table definitions in schema.sql. This is not a live MySQL concurrency
test; validate against your MySQL instance before deployment.

The requested scope covers nomination creation and retrieval. Authentication,
department authorization and master-data administration are not implemented.
