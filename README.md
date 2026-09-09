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
GRANT SELECT, INSERT, UPDATE ON training_management.* TO 'training_app'@'localhost';
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
src/main/resources/static/index.html, manage.html, programme-participants.html and their
JavaScript files, with a shared style.css. Open the pages through
Spring Boot rather than directly as a local file so fetch requests share the API origin.

Use the shared navigation to open /manage.html and create departments and training programmes.
Both tables refresh immediately after creation. Nomination dropdowns fetch the saved records
from /api/departments and /api/trainings each time the nomination page loads.
Department codes are unique and stored in uppercase.
The Training Programme Participants page loads capacity totals and nominations for
the selected programme. It preserves backend ordering and displays submission times
in the browser's local time zone. Confirmed rows provide a cancellation button with
a browser confirmation dialog; cancelling refreshes both the summary and table to
show any automatic waiting-list promotion. No manual promotion control is provided.
The nomination form shows the saved status returned by the server and informational
capacity figures. A full programme still accepts nominations onto its waiting list.
Officer Name is an optional reference field and is not sent or saved; the table
uses registered names returned by the backend. Only Officer ID identifies an officer.
The frontend submits nominatedByDepartmentId; the API also continues to accept
departmentId. Nomination responses include officerName, trainingName
and departmentName from the related database tables.

To demonstrate duplicates, submit officer 1001 for programme 101 from Finance,
then submit the same pair from Administration. The second request shows a red
duplicate warning and does not create another row.

### Upgrading an existing database

Use migrations/002_management.sql with an account allowed to alter the schema, then
restart Spring Boot. This adds department codes, generated master-data IDs, officer
details and training details without deleting existing records. Existing departments
receive codes such as DEPT-10; existing training dates, venues and capacities may be
empty and are displayed as "Not set". Newly created training programmes require all fields.
Run the migration during a maintenance window because MySQL requires foreign keys
to be temporarily dropped and recreated when changing ID columns to AUTO_INCREMENT.
The nomination unique constraint remains in place throughout.

For a fresh installation, schema.sql already includes these changes.
SQL columns department_name, officer_name and training_name retain their existing
names; the domain/API exposes name or title as appropriate.

## API reference

| Method | Path | Result |
| --- | --- | --- |
| GET | /api/departments | List departments: departmentId, name, code |
| POST | /api/departments | Create from name and code; 201, or 409 for duplicate code |
| GET | /api/trainings | List programmes: trainingId, title, trainingDate, venue, maxParticipants |
| POST | /api/trainings | Create from title, trainingDate, venue, maxParticipants; 201 |
| POST | /api/nominations | Create; 201 with Location header |
| PUT | /api/nominations/{id}/cancel | Cancel and promote the oldest waiting nomination atomically |
| GET | /api/trainings/{id}/nominations | Confirmed, waiting, cancelled; then timestamp and ID |
| GET | /api/trainings/{id}/capacity-summary | Capacity, status counts and available seats |
| PUT | /api/trainings/{id}/capacity | Update from maxParticipants and promote waiting officers |
| GET | /api/nominations/{id} | Retrieve; 404 if missing |
| GET | /api/nominations?trainingId=101&limit=50&offset=0 | List, optionally filtered by training |

List defaults: limit 50, offset 0; maximum limit 200. Results are ordered by nomination ID.

```powershell
$body = @{ trainingId = 101; officerId = 1001; nominatedByDepartmentId = 10 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/nominations -ContentType 'application/json' -Body $body
Invoke-RestMethod -Uri http://localhost:8080/api/nominations/1
Invoke-RestMethod -Uri 'http://localhost:8080/api/nominations?trainingId=101'
```

The server assigns the nomination ID, nominationDateTime (UTC ISO-8601) and
CONFIRMED or WAITING_LIST status. The database stores nomination_datetime with
microsecond precision and a CURRENT_TIMESTAMP(6) default. Clients cannot select a
status or backdate submissions; those fields are not part of the request DTO.
Repeating the POST with department 20 still returns HTTP 409:

```json
{
  "timestamp": "2026-09-09T00:00:00Z",
  "status": 409,
  "code": "DUPLICATE_NOMINATION",
  "message": "Officer is already nominated for this training programme."
}
```

Nonpositive IDs, malformed input and invalid department references return 400.
Missing training programmes, officers and nominations return 404.
Duplicate nominations, repeat cancellations and invalid status transitions return 409.

## Duplicate protection

The service first checks training_id and officer_id only. The MySQL constraint
`UNIQUE KEY uq_training_officer (training_id, officer_id)` is the final protection
when concurrent requests both pass the check. Spring translates a rejected insert
into DuplicateKeyException; the service converts that to the same 409 response.
Department, officer name and status never participate in duplicate detection.
Even cancelled nominations retain their unique pair.

## Task 2: limited capacity

NominationService.create and cancel use READ_COMMITTED transactions and acquire
SELECT ... FOR UPDATE on the same training_programme row before changing any seats.
The lock is held through commit. Each creation counts CONFIRMED records only after
acquiring the lock, so it sees prior committed allocations and cannot overbook.
Different training programmes have independent locks. Direct SQL writers must follow
the same locking protocol; the unique constraint separately protects duplicate pairs.

Submission order is the durable database insertion order: nomination_datetime, then
nomination_id. Concurrent HTTP requests are serialized when they acquire the training
lock; MySQL does not promise FIFO ordering by network arrival time.

Cancelling a confirmed nomination promotes exactly the oldest WAITING_LIST record
within the same transaction and preserves its original timestamp. Cancelling a waiting
nomination removes it from the queue without promoting anyone. Repeating cancellation
returns 409. No endpoint permits arbitrary status updates.

Capacity summaries take the same training lock and use one aggregate query to return
consistent confirmedCount, waitingListCount, cancelledCount, and availableSeats.
availableSeats is clamped to zero. Legacy programmes with no positive capacity return
409 CAPACITY_NOT_CONFIGURED for creation and summary; configure their actual limits.

The management page provides a capacity input and Save button for each programme.
Capacity updates acquire the same training lock, reject a reduction below the confirmed
count, and promote waiting officers in order when seats become available.

### Existing database migration

Stop the old application and back up the database, then run
migrations/003_training_capacity.sql once after migration 002. Restart with the new JAR.
This renames the existing timestamp column, changes the status check and adds a queue
index; it never recreates the nomination table or removes uq_training_officer.
Legacy NOMINATED/APPROVED records are allocated by timestamp and ID up to the stored
capacity; remaining records become WAITING_LIST. REJECTED becomes CANCELLED.
Existing timestamps and IDs are preserved. Configure missing capacities before migration;
otherwise those programmes receive zero confirmed allocations. New installations use schema.sql.

```powershell
Invoke-RestMethod -Method Put -Uri http://localhost:8080/api/nominations/1/cancel
Invoke-RestMethod -Uri http://localhost:8080/api/trainings/101/nominations
Invoke-RestMethod -Uri http://localhost:8080/api/trainings/101/capacity-summary
```

## Verification

```powershell
mvn test
mvn package
```

The default 19 tests cover duplicate protection, capacity allocation, capacity edits, last-seat races,
concurrent cancellations, cancellation versus creation, chronological promotion,
timestamp ties, rollback on promotion failure, summaries and validation. They use H2
in MySQL mode with the table definitions in schema.sql.

An additional opt-in MySqlCapacityIntegrationTest exercises concurrent last-seat
allocation and cancellation/promotion against a migrated MySQL database. Configure
DB_URL, DB_USERNAME and DB_PASSWORD, then run:

```powershell
$env:RUN_MYSQL_CAPACITY_TESTS = 'true'
mvn test
```

The MySQL test creates a uniquely named department, training and two officers and
deletes only those fixtures afterwards. Its account needs DELETE permission as well
as the runtime permissions. Do not enable it for routine production startup.

The project uses controller, service, repository, model and dto Java packages.
Static pages use plain HTML, CSS and fetch without a frontend framework.
Authentication and department authorization are not implemented.
