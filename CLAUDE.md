# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Inji Verify is a web application for verifying QR code credentials. It's a **monorepo with three modules**:
- **verify-ui** — React 18 + TypeScript SPA (CRA, Redux Toolkit + Redux-Saga, MUI, Tailwind CSS)
- **verify-service** — Spring Boot 3.2.3 backend (Java 21, Maven, JPA)
- **inji-verify-sdk** — Reusable React component library (Webpack, published to NPM)

## Build & Development Commands

### Frontend (verify-ui)
```bash
cd verify-ui
npm install
npm start                # Dev server on localhost:3000
npm run build            # Production build
npm test                 # Jest tests with coverage + snapshot updates
```

### Backend (verify-service)
```bash
mvn clean                        # From repo root
mvn spring-boot:run              # Dev server on localhost:8080/v1/verify (uses H2 in-memory DB)
mvn test                         # JUnit 5 tests
mvn package                      # Build JAR
```

To run with the local profile (H2): Spring auto-detects `application-local.properties`.

### SDK (inji-verify-sdk)
```bash
cd inji-verify-sdk
npm install
npm run build            # Webpack + TypeScript declarations
npm test                 # Jest tests with coverage
```

### Docker
```bash
cd docker-compose && docker compose up -d   # Access at localhost:3000
```

## Architecture

### Frontend State Management
Redux Toolkit store with Redux-Saga for side effects. Key flow:
1. User uploads/scans QR → dispatches `verificationInit`
2. Saga intercepts → decodes QR (handles PixelPass compression, base64)
3. Parsed VC → `verify()` utility validates signature
4. Results dispatched → UI renders status

Slices: `verification`, `alerts`, `common` (language/theme), `application-state` (connectivity), `verify` (VP results).

### Backend API (context path: `/v1/verify`)
- `POST /vc-verification` — Verify a single VC
- `POST /vp-request` — Initiate OpenID4VP authorization request
- `GET /vp-request/{reqId}/status` — Poll VP request status (long-polling, default 55s timeout)
- `GET /vp-result/{txnId}` — Fetch VP verification result
- `GET /vp-definition` — Fetch presentation definitions
- `POST /vp-submission` — Submit verifiable presentation

Backend follows interface-based service pattern with implementations in `services/impl/`.

### Verification Flows
- **VC Verification (offline):** QR decode → parse VC JSON → cryptographic signature validation → status (VALID/INVALID/EXPIRED)
- **VP Verification (OpenID4VP online):** Backend creates auth request → frontend shows QR → wallet submits VP → backend validates → frontend polls result

### Database
- **Production:** PostgreSQL (schema: `verify`, tables: `authorization_request_details`, `presentation_definition`, `vp_submission`)
- **Development:** H2 in-memory with `ddl-auto=create`
- DDL scripts in `db_scripts/inji_verify/`

### Nginx (Production)
Frontend serves SPA on `/` with fallback routing. Backend API proxied at `/v1/verify`. Port 8000.

## Test Structure

### Frontend Tests
- Location: `verify-ui/src/__tests__/` (`.spec.ts` / `.spec.tsx` files)
- Config: `verify-ui/jest.config.js` — ts-jest preset, jsdom environment
- Setup: `verify-ui/src/setupTests.ts`
- SVG mock: `verify-ui/__mocks__/svgFileTransformer.js`

### Backend Tests
- Location: `verify-service/src/test/java/io/inji/verify/`
- 41 test files covering DTOs, services, and controllers

## Key Configuration

### Frontend Environment (verify-ui/.env)
- `VERIFY_SERVICE_API_URL` — Backend API path (default: `/v1/verify`)
- `OVP_QR_HEADER` — Prefix to identify OpenID4VP QR codes (`INJI_OVP://`)
- `INTERNET_CONNECTIVITY_CHECK_ENDPOINT` — Used for offline detection
- `VERIFIABLE_CLAIMS_CONFIG_URL` — Path to claims config

### Backend (application.properties)
- Database connection via env vars: `DATABASE_HOST`, `DATABASE_PORT`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `inji.vp-request.long-polling-timeout` — VP polling timeout (env: `INJI_VP_REQUEST_LONG_POLLING_TIMEOUT`, default 55000ms)

## i18n
Translation files in `verify-ui/src/locales/`. Uses i18next with RTL support for Arabic/Hebrew.
