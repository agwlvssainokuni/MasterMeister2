# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

This repository is currently in the planning phase — no application code exists yet (no `backend/`, `frontend/`, or `devenv/` directories, no build files). Only planning documents are present under `docs/`. There are no build/lint/test commands to run yet.

Before scaffolding code, read `docs/REQUIREMENTS.md` (full requirements) and `docs/PROJECT_STRUCTURE.md` (agreed directory layout) — both are authoritative and should be kept in sync with any structural or scope decisions made during implementation.

## What This Project Is

A web application (SPA) for maintaining master data stored in an external RDBMS, with a Spring Boot backend and React frontend. Key workflow: two-step email-first user registration with admin approval → admin configures a target RDBMS connection and imports its schema → admin sets table/column-level access permissions → users browse/filter/edit master data and build/save/execute SQL queries against the target RDBMS, all under permission and audit-log controls. See `docs/REQUIREMENTS.md` for the full spec (registration flow, permission model, query builder, query history, audit logging requirements, etc.).

## Technical Stack

- Java 25 (latest LTS), Node.js 24 (latest LTS) — migrate to next LTS when released
- Backend: Spring Boot 4.1, built with Gradle 9.6
- Frontend: React 19, built with Vite
- Internal (operational) DB: H2, accessed via JPA
- Target RDBMS access (the data being maintained): `NamedParameterJdbcTemplate`, not JPA — must support MySQL, MariaDB, PostgreSQL, and H2 as target databases, using a connection pool (never `DriverManager.getConnection()`) and `java.time` for date/time
- Deployment target: executable WAR (12-factor, env-var configuration), with Docker packaging as a secondary target

## Architecture

### Two-database model
The application talks to two distinct databases with different access patterns:
- **Internal DB** (H2, JPA): application's own operational data — users, RDBMS connection configs, imported schema metadata, permissions, saved queries, query execution history, audit logs.
- **Target RDBMS** (MySQL/MariaDB/PostgreSQL/H2, JdbcTemplate): the external master data being maintained. Never accessed via JPA; all access goes through `NamedParameterJdbcTemplate` over a connection pool, and must work generically across the supported target database dialects.

### Backend package layout (planned)
Root package: `cherry.mastermeister`. Organized by feature/domain (not by technical layer), because the feature set is broad and independent (registration, RDBMS connection, schema import, permissions, master data CRUD, query builder, saved queries, query execution, query history, audit). See `docs/PROJECT_STRUCTURE.md` for the full package list and rationale. Within each feature package, split by layer (`controller/service/repository/entity/dto`).

Access-control model to keep in mind everywhere master data is touched: permissions are table-level (allow/deny) and column-level (none / read / read+update / full CRUD when all columns are updatable). Query builder, master data editing, and ad-hoc SQL execution must all respect this model except where the requirements explicitly carve out an exception (e.g., manual WHERE/ORDER BY input bypasses column-level read-permission filtering by design).

### Frontend structure (planned)
Feature-aligned with the backend: one directory per feature under `src/features/`, mirroring the backend's domain packages, each with its own components/hooks/api/types. See `docs/PROJECT_STRUCTURE.md` for the full layout.

### Unified mutation API
Master data create/update/delete are all submitted through a single API endpoint as one transaction (not separate endpoints per operation) — this is a deliberate design choice from the requirements, not an oversight, when extending the master-data feature.