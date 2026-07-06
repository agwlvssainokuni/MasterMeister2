# System Architecture

## System Overview

MasterMeister is planned as a single Spring Boot backend serving a React SPA (built and embedded as static resources for an executable WAR deployment), talking to two distinct databases: an internal H2 database for its own operational data (via JPA) and one of several supported target RDBMSes for the master data being maintained (via `NamedParameterJdbcTemplate` over a connection pool). As of this analysis, only the skeleton of each piece exists — no controllers, entities, or frontend feature code have been written yet.

## Architecture Diagram

```mermaid
flowchart TB
    subgraph Client["Browser"]
        SPA["React SPA<br/>(Vite build output, planned)"]
    end

    subgraph Backend["backend/ (Spring Boot 4.1, planned)"]
        Ctrl["Controllers<br/>(not yet implemented)"]
        Svc["Services<br/>(not yet implemented)"]
        JPA["JPA Repositories<br/>(not yet implemented)"]
        Jdbc["NamedParameterJdbcTemplate<br/>(not yet implemented)"]
    end

    InternalDB[("Internal DB: H2<br/>users, permissions,<br/>saved queries, audit log")]
    TargetDB[("Target RDBMS<br/>MySQL / MariaDB / PostgreSQL / H2")]
    Mail[("SMTP<br/>MailPit in devenv/")]

    SPA -->|"HTTP/JSON (planned)"| Ctrl
    Ctrl --> Svc
    Svc --> JPA
    Svc --> Jdbc
    JPA --> InternalDB
    Jdbc --> TargetDB
    Svc -.->|"registration / approval emails"| Mail
```

## Component Descriptions

### backend/
- **Purpose**: Server-side application; will implement every business transaction in `docs/REQUIREMENTS.md`.
- **Responsibilities**: currently none beyond booting a Spring context.
- **Dependencies**: `spring-boot-starter-web`, `spring-boot-starter-test` (test scope); Java 25 toolchain; Gradle 9.6 (Kotlin DSL) with Spring Boot BOM imported explicitly via `dependencyManagement`.
- **Type**: Application

### frontend/
- **Purpose**: SPA client; will implement the UI for every feature.
- **Responsibilities**: currently none — unmodified Vite `react-ts` template.
- **Dependencies**: React 19, ReactDOM 19, TypeScript ~6.0, Vite ^8.1, oxlint ^1.71 (lint only, no test runner configured).
- **Type**: Application (client)

### devenv/
- **Purpose**: Local dev infrastructure via Docker Compose.
- **Responsibilities**: runs MailPit (SMTP + web UI), MySQL, MariaDB, PostgreSQL containers with seeded `mastermeister` db/user/password. H2 needs no container (in-process).
- **Dependencies**: none from application code; supports manual/dev-time verification only.
- **Type**: Infrastructure (not yet runtime-verified in this environment — no Docker daemon available here)

## Data Flow

No end-to-end business workflow exists yet to diagram (no controllers or persistence code). The sequence below shows the *planned* shape for one representative transaction (master data edit) per `docs/REQUIREMENTS.md` §5.4, for forward reference only — it is not yet implemented.

```mermaid
sequenceDiagram
    participant U as User (browser)
    participant C as Controller (planned)
    participant S as Service (planned)
    participant J as NamedParameterJdbcTemplate (planned)
    participant T as Target RDBMS

    U->>C: submit edited rows (single "commit" action)
    C->>S: unified create/update/delete request
    S->>S: check table/column permissions
    S->>J: execute all changes in one transaction
    J->>T: INSERT/UPDATE/DELETE
    T-->>J: result
    J-->>S: result
    S-->>C: outcome
    C-->>U: success/failure response
```

## Integration Points

- **External APIs**: none yet.
- **Databases**:
  - Internal: H2 (JPA) — not yet configured (no `application.yml` datasource entries, no entities).
  - Target: MySQL / MariaDB / PostgreSQL / H2 — not yet configured; dev instances defined in `devenv/docker-compose.yml`.
- **Third-party Services**: SMTP via MailPit in dev (`devenv/docker-compose.yml`); production SMTP to be environment-configured per `docs/REQUIREMENTS.md` §7.3.

## Infrastructure Components

- **CDK Stacks**: none (not an AWS CDK project).
- **Deployment Model**: planned executable WAR (12-factor, env-var configuration) per `docs/REQUIREMENTS.md` §7.2, with Docker packaging as a secondary target; Tomcat WAR deploy as a future option. Not yet implemented.
- **Networking**: none defined yet; `devenv/docker-compose.yml` maps MailPit (1025/8025), MySQL (3306), MariaDB (3307→3306), PostgreSQL (5432) to localhost for development only.
