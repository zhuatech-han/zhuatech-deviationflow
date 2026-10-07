[中文](README.md) | [English](README.en.md)

# DeviationFlow · Temporary Process Deviation Authorization and Usage Ledger

<img src="frontend/public/brand/logo.jpg" alt="ZhiHua Technology logo" width="200">

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/).

**Public source for learning 0.1.0 / non-commercial use.** Java 21, Spring Boot, Vue 3, MySQL and Flyway implement application → technical review → independent approval → date/quantity-limited usage → independent reversal, revocation or closure.

Own source is for personal learning, technical research and non-commercial exchange. Commercial use requires written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. [LICENSE](LICENSE) is the existing ZhuaTech Non-Commercial Source License 1.0, not an OSI open-source license. [Third-party notices](THIRD_PARTY_NOTICES.md) retain separate permissions.

## Purpose and implemented workflow

When normal tooling is temporarily unavailable or a process needs a temporary adjustment, a request should state its scope, controls and external authorization basis before technical review and independent approval. Usage must match the item, revision and work-order scope, and remain within approved dates and integer quantities. This application lets readers study the responsibilities and ledger implementation; it does not make on-site safety decisions, certify compliance or validate the legal effect of external/customer approval.

| Module | Implemented behavior |
| --- | --- |
| Identity and administration | Session login/logout/password change; accounts, roles, registered permissions, menus, departments, dictionaries, settings and audit |
| Draft/application | Category, designated people, item/revision, work-order scope, baseline, deviation, controls and external authorization basis; only the author edits a draft |
| Independent approval | Author, technical reviewer and approver must be different; assigned personnel perform their actions, not an unrelated administrator |
| Frozen authorization | Approved boundaries freeze; Shanghai dates derive scheduled/valid/expired/exhausted status, with at most 365 inclusive calendar days |
| Usage | Only designated executor records usage; server checks matching facts, date, integer quantity, current permissions and unique evidence references |
| Reversal/termination | Designated approver independently voids an incorrect entry, retaining original quantity/person/time/reason; terminal permits prohibit usage/reversal |
| Workbench/statistics | Personal pending work, search/state filters, database pagination, per-unit quantities and approaching expiry |
| Export/history | Scope-checked permit JSON with usages/events, retained audit, persistence and versions |

Nonconforming-product disposition, rework, scrap and corrective actions are normally a QMS concern. Permanent product/BOM changes belong to PLM/ECN; this application does not change a formal version or automatically make a deviation permanent. It rejects invalid registration requests but has no MES/ERP/device connection and cannot stop actual production. Responsible staff enter an external authorization reference or not-applicable basis; the system does not verify its authenticity.

No attachments, electronic signatures, email/SMS, ERP/MES integration, online customer approval, equipment interlocks, multiple-company tenants or automatic permanent change are implemented. There is no AI, payment, external online-service dependency or seeded business example. TEST records in screenshots come from isolated acceptance tests, not empty-database startup.

## Users, responsibilities and data scopes

Administrators create departments and separate applicant, technical-reviewer, approver and executor accounts. Three approval-stage people must differ by account identity. An executor can also hold another role, but cannot reverse their own usage. Even full-permission administrators must be explicitly designated for a business action. Multiple accounts held by one person cannot be detected; account ownership must be managed responsibly.

`ALL` reads all departments; `DEPARTMENT` reads the current department; `SELF` reads permits the account created or is designated to review, approve or execute. SELF cannot apply/edit or approve. Executors moved to another department may still read their own history but cannot use the original department's permit. Details, lists, statistics and exports enforce the same server-side scope; hidden navigation is not authorization.

Draft → technical review → approval → active. A return from review/approval restores draft while retaining reasons/events; a previously submitted draft cannot be deleted. Approval freezes quantity, dates and designated identities. Create a new application for new scope. ACTIVE derives scheduled/valid/exhausted/expired state; terminal REVOKED/CLOSED takes precedence and cannot reopen.

The server uses Shanghai calendar dates, including the final day. Usage records preserve original facts and server timestamps. Item/revision/scope must match; quantities are integer pieces, batches or sets, summarized separately by unit. References trim whitespace and normalize to uppercase, unique within a permit. Usage evidence must contain at least ten characters.

The designated approver may reverse a historic entry independently, including after expiry while the permit remains nonterminal. Original quantity/evidence/executor remain; reversal adds actor/reason/time and reduces net usage. Revocation stops use early; closure ends administration. Neither permits further usage/reversal.

## Actual running screens

These real application screenshots show synthetic TEST acceptance records.

Login authenticates a server-side session. The applicant list shows authorization state, item revision, scope, dates and quantity boundaries.

![Login](docs/screenshots/login.jpg)

![Applicant permit list](docs/screenshots/permits.jpg)

The designated reviewer's workbench lists personal pending work and review responsibilities. Executor detail shows net consumption and retained original/voided entries.

![Technical review workbench](docs/screenshots/workbench.jpg)

![Usage ledger](docs/screenshots/usage.jpg)

Statistics aggregate the authorized scope by unit. Administrators maintain staff roles/departments, while business approvals still require assigned people.

![Statistics](docs/screenshots/statistics.jpg)

![Account administration](docs/screenshots/accounts.jpg)

Registered permissions and ALL/DEPARTMENT/SELF scopes are configured independently of backend enforcement.

![Roles and data scopes](docs/screenshots/roles.jpg)

## Architecture, directories and environment

Browser → Nginx → Spring Boot → MySQL. Permit changes, quota consumption, events and UUID retry records commit in one transaction. A pessimistic headquarters-department lock serializes writes with READ_COMMITTED, and identity is refreshed after locking. Entity versions reject stale edits. This coarse lock favors bounded consistency; large-load performance is unverified.

```text
backend/      Java API, identity, transactions, unit and HTTP integration tests
  src/main/resources/db/migration/    V1 identity and V2 permits/usages
frontend/     Vue workflows, request helpers and frontend tests
  public/brand/           Official logo
  public/third-party/     Third-party license text
scripts/      Local environment generation, HTTP acceptance and release checks
compose.yaml  Isolated MySQL/backend/frontend
.env.example  Configuration names without passwords
LICENSE       Existing own-source non-commercial license
```

| Layer | Requirement |
| --- | --- |
| Backend | Java 21, Maven 3.9, Spring Boot 4.0.7 |
| Frontend | Node.js 24.19+, npm 11, Vue 3.5.40, Vite 8.1.5 |
| Data | MySQL 8.4; Flyway version managed by Spring Boot |
| Deployment/scripts | Docker Engine/BuildKit, Compose v2 and Python 3.11+ |

Exact dependencies are in `backend/pom.xml` and the frontend lock file. Applications run as non-root. Flyway validates migrations; runtime schema auto-creation is disabled.

## First startup and database initialization

From the repository root:

```sh
python3 scripts/init-env.py
docker compose -p deviationflow-local config --quiet
docker compose -p deviationflow-local up --build -d --wait
```

Open [http://127.0.0.1:8116/](http://127.0.0.1:8116/). Health: [http://127.0.0.1:8116/actuator/health](http://127.0.0.1:8116/actuator/health). Frontend binds localhost; backend/database have no host ports.

An empty database creates administrator `admin` and role directories only. Read your locally generated `ADMIN_PASSWORD`; there is no common default password. The generator creates independent random credentials in ignored `.env` with mode 0600 and refuses to overwrite it. Existing data/passwords survive restart; changing initialization variables does not reset existing accounts.

MySQL database `zhuatech_deviationflow`, internal application account `deviationflow`, persistent volume `mysql-data`. [V1](backend/src/main/resources/db/migration/V1__identity.sql) creates identity/administration tables; [V2](backend/src/main/resources/db/migration/V2__deviations.sql) creates permits, usages, events and command records with foreign keys/unique constraints. Flyway applies migrations then validates; do not edit applied versions or delete volumes to upgrade. See [database](docs/DATABASE.md).

## Configuration and development

| Name | Use |
| --- | --- |
| `DATABASE_PASSWORD` | Application database password, matching MySQL |
| `MYSQL_ROOT_PASSWORD` | MySQL initialization administrator, only in its container |
| `ADMIN_PASSWORD` | First empty-database administrator password |
| `WEB_PORT` / `BIND_ADDRESS` | Default `8116` / `127.0.0.1` |
| `COOKIE_SECURE` | Local HTTP `false`; trusted HTTPS deployment `true` |

Passwords need at least 12 characters, uppercase/lowercase/digits, and at most 72 UTF-8 bytes. Keep configuration, backups and acceptance credentials private. No paid third-party API configuration is required.

For directly running the backend, prepare a separate reachable test MySQL and supply `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` and `ADMIN_PASSWORD`. Default JDBC uses Compose host `mysql`; it is not reachable directly from the host without a separate restricted development arrangement. In one terminal from the root:

```sh
cd backend
mvn spotless:check test package
mvn spring-boot:run
```

In another terminal from the root:

```sh
cd frontend
npm ci
npm run dev
```

Vite binds localhost and proxies the backend; see `frontend/vite.config.js`. Do not publicly expose MySQL.

## Tests and isolated acceptance

From the repository root:

```sh
cd backend
mvn spotless:check test package
cd ../frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose -p deviationflow-check config --quiet
docker compose -p deviationflow-check up --build -d --wait
python3 scripts/smoke.py
python3 scripts/smoke.py --verify
python3 scripts/release-check.py
git diff --check
```

Prepare an independent disposable database/configuration/port before acceptance. `smoke.py` defaults to `http://127.0.0.1:8116`; set `--base` for another port. It creates TEST accounts and permits, and stores random passwords in ignored `.smoke-state.json`; do not run against business/customer data or share that file. `--verify` checks the persisted state after restart or independent recovery.

Backend tests cover three-account independence, designated execution, frozen boundaries, retained returns, versions, exact retries, duplicate-reference rollback, quantities, dates including Shanghai midnight/365-day limits, concurrency, department/SELF scopes, export, permission changes and CSRF. Frontend tests cover states/display/request handling. H2 compatibility tests do not replace real MySQL migration/workflow validation. Docker builds execute format/tests/build without skipping Maven tests. See [testing](docs/TESTING.md).

## Deployment, upgrade and recovery

Use separate database accounts, trusted HTTPS, `COOKIE_SECURE=true`, controlled access, offline backups and independent recovery verification. Single-instance sessions/rate limits do not provide tenant isolation, distributed sessions or high availability. Stop with `docker compose -p deviationflow-local down` to retain data. `down -v` is only for confirmed disposable tests.

Before upgrading, back up the logical database snapshot, migration version and protected configuration. Restore into a new isolated database with a matching application version, verify original accounts, permits, usages and audit, then decide whether to switch. Add migration versions rather than editing V1/V2. For restarts, wait for MySQL health, then backend health, then restart frontend to resolve the current backend address. [Deployment](docs/DEPLOYMENT.md), [operations](docs/OPERATIONS.md) and [security](docs/SECURITY.md) provide detailed Chinese instructions.

Password hashes use BCrypt cost 12. Sessions are HttpOnly/SameSite Strict with a 30-minute timeout; writes require CSRF. Repeated failed logins are limited. Enablement, password version, role, permission and department are checked server-side on each request and after write locking.

Database administrators can directly change data; audit is not cryptographically signed or tamper-proof. Audit lists the recent 1,000 entries, not full history; statistics reject scopes exceeding 10,000 permits. Long-term archival and unbounded reports are unimplemented.

401: sign in again and check account status. 403: check role/scope/designated person/CSRF. 409: refresh state/version and inspect quotas/references; reuse a UUID only with the original payload. For rejected usage check Shanghai date, item/revision/scope, quantity, current department and role. Terminal permits cannot reopen. For startup failure inspect this project's redacted status/logs, credentials and migrations without deleting business volumes.

## Feedback, limits and source license

Report reproducible issues, tests and small fixes through the repository using synthetic/anonymized data. Preserve branding, copyrights and licenses; add database migrations and document behavior/validation changes. Contributions must have compatible rights. Send security reports privately via the company contact, without public credentials or identifiable business records.

The software does not assess deviation safety, validate external approvals, replace a quality/regulatory/customer authorization process, issue an electronic-signature certificate or stop physical production. There is no production-readiness claim. The existing [LICENSE](LICENSE) governs own source with written authorization required for commercial use; third-party licenses remain intact, without free-commercial-use MIT/Apache terms.

## Contact ZhiHua Technology

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [https://www.zhuatech.cn/](https://www.zhuatech.cn/).

For commercial licensing, customization, private deployment and system integration:

- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)

Contact information does not change the source license or third-party permissions.
