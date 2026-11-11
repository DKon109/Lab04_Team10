# Tixora — Event Management & Seating

**From an organiser's first draft to an attendee's assigned seat.**

[**Open the interactive demo →**](https://dkon109.github.io/Lab04_Team10/) · [My contribution](#my-contribution--ryoji-kondo) · [Run the full application](#run-the-full-application) · [Verification](#verification)

Tixora is a University of Sydney team project built with **Java 17, Spring Boot, React and MySQL**. It connects event discovery, owner-managed publishing, simulated checkout and deferred group seating in one workflow.

My work focused on the rules behind that experience: **who can change an event, when it can be published, which venue data is valid, and whether bookings and seating survive transactions correctly.**

## Try it in two minutes

The [free public preview](https://dkon109.github.io/Lab04_Team10/) opens without a backend cold start or account:

1. In **Organiser**, try publishing the draft before providing a venue. Inspect the validation message.
2. Apply the demo venue and publish. Switch to **Attendee** to simulate successful or declined checkout.
3. Create a few fictional bookings, optionally requesting an accessible position.
4. Return to **Organiser**, close registration and run the illustrative assignment. A new booking attempt is now rejected.
5. Open **Tickets** to inspect seat highlights and download an anonymous CSV. Reset the demo to start again.

**Preview boundaries:** this is a browser-only, interactive demonstration with fictional records stored in localStorage. It does not run Spring Security, MySQL, real authentication or the team's allocation algorithm. Its simple accessible-first assignment illustrates the flow, not backend group optimisation. All fictional tickets can be inspected in the preview; the full application enforces ticket ownership. No money is charged. GitHub Pages provides static hosting without a sleeping API; availability remains subject to the hosting service.

## My contribution — Ryoji Kondo

This is a five-person team project. The table distinguishes my implementation and verification from the system as a whole.

| My responsibility | What I implemented or verified | Code / evidence |
| --- | --- | --- |
| Event ownership and lifecycle | Derive ownership from the authenticated organiser; restrict editing and status changes; separate draft creation from publication and closure | [EventService](app/src/main/java/com/ooaf/eventticketing/service/EventService.java), [OrganiserEventController](app/src/main/java/com/ooaf/eventticketing/controller/OrganiserEventController.java) |
| Publication and public access rules | Reject invalid venue/capacity data; limit public discovery and likes to published events; validate seating prerequisites | [EventService](app/src/main/java/com/ooaf/eventticketing/service/EventService.java), [event API tests](app/src/test/java/com/ooaf/eventticketing/controller/LikesAndEventCreationApiTest.java) |
| Campus venue templates | Everest, Reginald and York template generation, explicit demo assumptions and validation | [CampusVenueTemplateService](app/src/main/java/com/ooaf/eventticketing/service/CampusVenueTemplateService.java), [template tests](app/src/test/java/com/ooaf/eventticketing/service/CampusVenueTemplateServiceTest.java) |
| Configurable external layouts | Front-stage generator and external layout families integrated with the shared seating-map contract | [FrontStageTemplateService](app/src/main/java/com/ooaf/eventticketing/service/FrontStageTemplateService.java), [ExternalVenueTemplateService](app/src/main/java/com/ooaf/eventticketing/service/ExternalVenueTemplateService.java) |
| Transaction and concurrency verification | Test closure-first versus checkout-first outcomes, preserve successful bookings, and verify saved venue data across transactions | [RegistrationClosureConcurrencyTest](app/src/test/java/com/ooaf/eventticketing/controller/RegistrationClosureConcurrencyTest.java), [VenueTemplatePersistenceTest](app/src/test/java/com/ooaf/eventticketing/controller/VenueTemplatePersistenceTest.java), [GeneralAdmissionPersistenceTest](app/src/test/java/com/ooaf/eventticketing/controller/GeneralAdmissionPersistenceTest.java) |
| Integration and handoff | Document API contracts; verify campus journeys, tickets, CSV/print consistency and invalid-update preservation | [SeatingIntegrationTest](app/src/test/java/com/ooaf/eventticketing/controller/SeatingIntegrationTest.java), [backend handoff](docs/BACKEND_HANDOFF.md) |
| Personal portfolio edition | This README and the always-available browser preview | [PortfolioDemo](frontend/src/PortfolioDemo.jsx) |

**Team attribution:** Rafly owned allocation algorithms; Aadi owned organiser/seating interfaces; Tejas owned attendee interfaces and overall integration; Sathish owned bookings, mock payments and database coordination. Their contributions and history are retained. The public preview reuses the team's visual styles and seat-map component. The team's complete test totals below are not a claim that I authored every test or feature.

## The full application

```mermaid
flowchart LR
    O[Organiser] --> UI[React interface]
    A[Attendee] --> UI
    UI --> API[Spring Boot REST API]
    API --> RULES[Security, ownership and lifecycle rules]
    RULES --> VENUE[Venue maps and validation]
    RULES --> BOOK[Bookings and mock checkout]
    BOOK --> ALLOC[Deferred seat allocation]
    VENUE --> ALLOC
    ALLOC --> OUT[Private tickets, metrics, CSV and printable plans]
    RULES --> DB[(MySQL / JPA)]
    BOOK --> DB
    ALLOC --> DB
```

- **Discover:** browse Sydney/USYD events, search, filter, like and see rule-based recommendations.
- **Organise:** create and edit drafts, configure venues, publish and close registration.
- **Book:** reserve an individual place or join a private event group through simulated checkout.
- **Assign:** allocate after closure, considering groups, accessibility, blocked positions and layout constraints.
- **Inspect:** view private tickets, allocation metrics, anonymous CSV and printable seating plans.

### Engineering decisions I can explain

**Ownership comes from the session.** A caller cannot select another organiser as the owner by supplying an ID. Role and ownership checks protect state-changing operations.

**Publication is a state transition with prerequisites.** Incomplete drafts can exist, while public events must satisfy server-side venue, capacity and seating-mode requirements. Unnumbered venues require general admission unless temporary physical identifiers are confirmed.

**Closing registration must cooperate with checkout.** Integration tests exercise both transaction orderings. A booking committed before closure is preserved; an identical successful checkout retry retrieves the existing outcome. Closure prevents new successful checkout.

**Persistence deserves a separate test.** Tests flush/reload state and use separate transactions to verify saved maps and tickets, rather than relying solely on objects already held in memory.

## Verification

Recorded full-application baseline, **2 November 2026**:

| Check | Result |
| --- | --- |
| Backend full suite | 311 tests; zero failures, errors or skips |
| Frontend suite | 95 tests across 15 files; all passed |
| Frontend production build | Passed |
| Proposal-scale allocation benchmarks | 20 cases passed the ten-second threshold in the documented environment |

See [final acceptance](docs/FINAL_ACCEPTANCE.md) for environments, commands, timing boundaries and limitations. These are dated baseline results, not a claim of continuous verification on every later revision. Larger campus simulations are outside the 300-attendee timing guarantee. The portfolio preview has its own checks and is separate from those backend results.

## Run the full application

With Docker Desktop running, from the repository root:

```bash
git clone https://github.com/DKon109/Lab04_Team10.git
cd Lab04_Team10
docker compose up --build -d
```

Open **http://localhost:5173**. The API runs on port 8080. Compose starts MySQL and retains application data in a named volume.

For the complete seeded acceptance journey, use the documented [PowerShell smoke script](scripts/smoke-final.ps1) and [final acceptance instructions](docs/FINAL_ACCEPTANCE.md). That script creates labelled fictional fixtures and prints ticket URLs.

To stop: `docker compose down`.

### Run checks

Frontend (Node 22.12+):

```bash
cd frontend
npm ci
npm test
npm run build
```

Backend (Java 17+ and a **dedicated disposable MySQL test database**):

```bash
cd app
TEST_DB_URL='jdbc:mysql://localhost:3306/tixora_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
TEST_DB_USERNAME=root TEST_DB_PASSWORD='your-test-password' ./mvnw test
```

Test configuration may recreate tables. Use a test database, not the application database.

Browser preview locally:

```bash
cd frontend
npm ci
VITE_PORTFOLIO_DEMO=true npm run dev
```

The normal build continues to use the real API. The Pages workflow explicitly enables the portfolio preview at build time.

## Scope and limitations

- Payments are simulated; there are no real charges, refunds or payment-provider integration.
- Campus templates derive from published-plan information and agreed demonstration assumptions. Physical geometry, installed identifiers and accessible positions are not verified for real event operation.
- Allocation is a constrained heuristic, not a claim of global optimality.
- There is no implemented QR door-scanning/check-in or university identity integration.
- Team project history is preserved; this personal repository and portfolio edition are maintained separately from the university repository.

## Further technical reading

[Backend handoff](docs/BACKEND_HANDOFF.md) · [Authentication](AUTH_API.md) · [Event integration](INTEGRATION_NOTES.md) · [Managed seating maps](docs/SEATING_MAP_MANAGEMENT_API.md) · [Allocation and outputs](docs/SEAT_ALLOCATION_AND_OUTPUTS.md) · [Final acceptance](docs/FINAL_ACCEPTANCE.md)
