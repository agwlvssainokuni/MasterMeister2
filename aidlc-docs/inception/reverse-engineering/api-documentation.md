# API Documentation

## REST APIs

**None exist yet.** No `@RestController` / `@Controller` classes are present anywhere in `backend/src`. `spring-boot-starter-web` is on the classpath but unused beyond auto-configuration.

Per `docs/REQUIREMENTS.md`, the following REST surface is planned (not yet designed at the endpoint level):
- User registration (email submission, token-based password-set completion) and admin approval/rejection
- Target RDBMS connection configuration and schema import
- Table/column permission configuration (with YAML import/export)
- Login/session (or JWT) authentication
- Master data list/filter/edit — with **all create/update/delete operations unified into a single transactional endpoint** (explicit design decision, not an oversight)
- Query builder SQL generation / reverse-parse
- Saved query CRUD + execution
- Ad-hoc query execution (read-only, parameterized `:param` support, optional pagination)
- Query execution history list/filter
- Audit log viewing (admin only)

## Internal APIs

**None exist yet.** No service interfaces, repository interfaces, or other internal contracts have been written beyond the generated `MasterMeisterApplication` entry point.

## Data Models

**None exist yet.** No JPA entities, DTOs, or `NamedParameterJdbcTemplate`-mapped row classes are present. Planned entity domains (per `docs/PROJECT_STRUCTURE.md`) include: users, RDBMS connection configs, imported schema metadata, table/column permissions, saved queries, query execution history, and audit log entries — all to live in the internal H2 database accessed via JPA.
