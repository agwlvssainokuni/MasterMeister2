# Technology Stack

## Programming Languages
- Java - 25 (latest LTS, via Gradle toolchain) - backend application code
- Kotlin - (Gradle DSL only, via `.gradle.kts` build scripts) - build scripting, not application logic
- TypeScript - ~6.0.2 - frontend application code

## Frameworks
- Spring Boot - 4.1.0 (Spring Framework 7-based) - backend application framework (currently only `spring-boot-starter-web` + `spring-boot-starter-test` in use)
- React - ^19.2.7 - frontend UI framework (template default; no app-specific code yet)

## Infrastructure
- H2 Database - planned internal operational DB (JPA); not yet wired into the app
- MySQL - `mysql:lts` container in devenv - one of four supported target RDBMS dialects
- MariaDB - `mariadb:lts` container in devenv (port 3307→3306) - target RDBMS dialect
- PostgreSQL - `postgres:18` container in devenv - target RDBMS dialect
- MailPit - `axllent/mailpit:latest` container in devenv - SMTP catcher + web UI for dev email verification

## Build Tools
- Gradle - 9.6.1 (Kotlin DSL, wrapper committed) - backend build
- `io.spring.dependency-management` - 1.1.7 - explicit Spring Boot BOM import
- Vite - ^8.1.1 - frontend build/dev server
- npm - (version not pinned in repo) - frontend package management
- Docker Compose - (version not pinned; CLI v29.6.1 / compose v5.3.0 observed locally) - devenv orchestration

## Testing Tools
- JUnit 5 (via `spring-boot-starter-test`, JUnit Platform) - backend testing; only a context-load smoke test exists
- oxlint - ^1.71.0 - frontend linting only (not a test runner; no frontend test runner configured)
