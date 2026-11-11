# Personal full-stack hosting

This deployment runs the existing React application, Spring Boot controllers,
services, authentication and allocation code, and an external MySQL database.
It is separate from the university repository. The previous browser simulation
is not the application deployed by the root Dockerfile.

## Infrastructure

- Render free Docker web service, configured by `render.yaml`.
- Aiven free MySQL (or an existing compatible MySQL service), retained separately
  from the web container. Do not use an in-memory substitute for this deployment.
- React is compiled into Spring's static resources. All API requests use the same
  origin, including direct print downloads. React routes forward to index.html;
  `/api/**` retains the existing role, token and ownership checks.
- `SPRING_PROFILES_ACTIVE=public-demo`; Render supplies `PORT`.
- Configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` in the host's environment, never
  in Git. Use a dedicated demo database and the provider's TLS connection settings.
- `/health` checks actual database connectivity and returns only `{"status":"UP"}`.
- Free Render services sleep on inactivity and wake on request. No uptime guarantee
  is claimed; MySQL data survives web-service restarts.

## Accounts and data

Use fictional data only. Shared demo accounts are public fixtures and their data
can be changed by other visitors:

- Organiser: `committee.demo@uni.sydney.edu.au` / `OrganiserDemo1!`
- Attendee: `attendee.demo@uni.sydney.edu.au` / `TixoraDemo3!`

The existing seeder creates the organiser. The public-demo profile also creates
one fictional attendee. Passwords are hashed using the existing encoder. No real
payments are taken. The normal registration flow remains available.

## Deployment and verification

1. Sign in to the personal Render account and provision a free MySQL database in
   the personal Aiven account (new account terms must be accepted by the owner).
2. Create the Docker service from `DKon109/Lab04_Team10` and supply the dedicated
   database's connection settings. Select the free plan explicitly.
3. Verify `/health`, log in through `/auth`, load real event API data, create a
   draft, save a venue, publish, book as attendee, close and allocate as organiser,
   then inspect the attendee ticket and anonymous CSV/print exports.
4. Reload a React deep link and confirm it renders. Restart the service and confirm
   MySQL retains the created event and booking.
5. Only after verification, point the GitHub homepage and README demo link to the
   deployed service, and replace the old GitHub Pages preview with a link to it.

Local check (against a dedicated test database):

```bash
cd app
TEST_DB_URL='jdbc:mysql://localhost:3306/tixora_personal_test?useSSL=false&serverTimezone=UTC' \
TEST_DB_USERNAME=root TEST_DB_PASSWORD='' \
./mvnw -Dtest=PublicDemoHostingTest test
```

The test verifies React route forwards and unauthenticated API rejection, plus
health against MySQL. Existing backend suites cover the application behaviour.
