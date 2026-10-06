# Guest Auth Module Context

## GitHub Ticket

Issue #70: `[BACKEND][AUTH-GUEST] Login de huéspedes con correo y contraseña real`

Branch: `feature/70-auth-guest-login`

Target PR base: `develop`

## Scope

Implemented persistent guest credentials and real guest authentication via email and password, establishing it as the primary guest authentication flow while cleanly deprecating the link code flow.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/api/v1/guest/auth/login` | Authenticate guest by email and password, returning JWT |
| POST | `/api/v1/guest/auth/link` | Deprecated link code authentication (retained for backward compatibility) |

## Files Added / Modified

- `src/main/resources/db/changelog/migrations/024-guest-credentials.sql`: Additive Liquibase migration creating `guest_credentials` table with BCrypt hashed passwords and seeds for 2 demo guests.
- `src/main/resources/db/changelog/db.changelog-master.yaml`: Registered migration 024.
- `src/main/java/com/aurora/pms/model/GuestCredential.java`: Persistent entity decoupled from staff `users`.
- `src/main/java/com/aurora/pms/repository/GuestCredentialRepository.java`: Repository for credentials.
- `src/main/java/com/aurora/pms/repository/BookingRepository.java`: Query methods for finding active bookings by guest.
- `src/main/java/com/aurora/pms/dto/request/GuestLoginRequest.java`: DTO with email and password validations.
- `src/main/java/com/aurora/pms/dto/response/GuestLoginResponse.java`: DTO with accessToken, tokenType, and expiresIn.
- `src/main/java/com/aurora/pms/service/GuestAccessService.java`: Added `login(GuestLoginRequest)`.
- `src/main/java/com/aurora/pms/service/impl/GuestAccessServiceImpl.java`: Implementation verifying BCrypt password hash, finding active checked-in booking, and generating `ROLE_GUEST` JWT.
- `src/main/java/com/aurora/pms/controller/GuestAccessController.java`: Added `POST /api/v1/guest/auth/login` and `@Deprecated` on `/api/v1/guest/auth/link`.
- `src/main/java/com/aurora/pms/config/SecurityConfig.java`: Allowed unauthenticated access to `/api/v1/guest/auth/login`.
- `src/test/java/com/aurora/pms/controller/GuestAuthControllerTest.java`: Complete test suite covering success, 401 handling, input validation, guest isolation, and security boundaries.
- `src/test/java/com/aurora/pms/DemoSeedDataTest.java`: Added verification for guest credentials seed.
- `src/test/java/com/aurora/pms/config/SecurityRoutesTest.java`: Added verification for guest login public routing.
- `docs/DEMO-DATA.md` and `docs/REGLAS-NEGOCIO.md`: Updated documentation with credentials and business rules.

## Demo Credentials

| Guest | Email | Password | Reservation | Link Code (Deprecated) |
| --- | --- | --- | --- | --- |
| Ana Morales | `ana.demo@aurora.test` | `huesped1` | `AUR-DEMO-001` | `HUESPED-DEMO-UNO` |
| Carlos Reyes | `carlos.demo@aurora.test` | `huesped2` | `AUR-DEMO-002` | `HUESPED-DEMO-DOS` |

## Important Decisions

- Staff users and guest credentials are strictly isolated: `guest_credentials` references `guests(id)` and does not use the staff `users` table or staff roles/permissions.
- Passwords are encrypted with BCrypt (`BCryptPasswordEncoder`).
- Returned JWT carries `ROLE_GUEST` authority and the active booking ID as subject, preserving compatibility with all existing guest portal endpoints.
- Cross-guest access is prevented by validating ownership in the service layer (`ensureOwn`), responding with `403 Forbidden` or `404 Not Found`.
- Invalid credentials return a controlled `401 Unauthorized` with `ApiErrorResponse`.
