# Component Inventory

## Application Packages
- `backend` (`cherry.mastermeister`) - Spring Boot backend application skeleton; no feature packages yet
- `frontend` - React + TypeScript SPA client (unmodified Vite template)

## Infrastructure Packages
- `devenv` - Docker Compose local dev environment (MailPit, MySQL, MariaDB, PostgreSQL)

## Shared Packages
- None yet (no `common/`, `config/`, or shared utility code has been created in the backend; frontend has no `components/`, `hooks/`, `api/`, `store/`, or `types/` directories yet)

## Test Packages
- `backend/src/test/java/cherry/mastermeister` - single Spring context-load test (`MasterMeisterApplicationTests`)
- No frontend test package (no test runner configured)

## Total Count
- **Total Packages**: 3 (backend, frontend, devenv)
- **Application**: 2 (backend, frontend)
- **Infrastructure**: 1 (devenv)
- **Shared**: 0
- **Test**: 1 (backend unit/integration test source set; frontend has none)
