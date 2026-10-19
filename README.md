# Tixora

> **Sprint 3 review branch (12 October 2026):** responsive attendee experience, mock checkout, organiser lifecycle, persisted seating/allocation, private seat-map tickets and anonymous export are now integrated. Start with [Sprint 3 handoff and run commands](docs/SPRINT3_TEJAS.md), [checkout API](docs/MOCK_CHECKOUT_API.md), and [seating API](docs/SEATING_API.md). Campus layouts remain explicitly unverified demos. The Sprint 0/1 descriptions below are historical, not the current implementation contract.

### Campus ticketing made simpler, fairer and more accessible

**ELEC5619 – Object Oriented Application Frameworks**  
**Lab 4 | Group 10 | Sprint 0**

> **Sprint 1 status:** the repo now contains a working slice — registration/login
> (`app/`, Spring Boot + MySQL), an event-discovery feed with likes and a
> personalised For You tab, and an organiser dashboard for posting events
> (`frontend/`, React + Vite). Seating, payments, QR tickets and check-in below
> are the Sprint 0 proposal direction and are **not built yet** (proposal not
> final until 13 Sep 2026). See [`frontend/README.md`](frontend/README.md) to
> run it and [`INTEGRATION_NOTES.md`](INTEGRATION_NOTES.md) for the current
> API contract and gaps.

See [Authentication API](AUTH_API.md) for registration, login, current-user endpoints, request/response examples, and authentication errors.

## Run with Docker

The complete application can run with Docker Compose, including MySQL:

```bash
cp .env.example .env       # optional: customise ports and credentials
docker compose up --build
```

Open <http://localhost:5173>. The frontend proxies `/api` requests to the
backend inside the Compose network. The backend is also available directly at
<http://localhost:8080> for API development.

Stop the containers with `docker compose down`. Add `--volumes` only when you
also want to delete the local MySQL data volume.

## Overview

Tixora is a campus-first event ticketing and venue-entry platform built for university societies. It allows society committees to publish events, offer member and non-member tickets, manage allocated seating, take payments, issue QR e-tickets and monitor attendance through one system.

The platform supports the complete event journey: a society publishes an event, a student verifies their eligibility, finds suitable seats, completes payment, receives a QR ticket and checks in at the venue. Although initially designed for university societies, the platform could later support community organisations and other small event organisers.

## Problem Statement

University societies regularly organise balls, performances, conferences, competitions and networking events. However, general ticketing platforms do not always reflect the way campus events operate. Committees may need to manage society-member discounts, student eligibility, group seating, accessibility requirements and volunteer-run entry while using separate tools for ticket sales and attendance.

This fragmented process can create inaccurate seating information, unclear ticket eligibility, unexpected fees, duplicated tickets and slow check-in. Students may also struggle to find adjacent seats for their group or identify accessible seating without contacting the organiser separately.

Tixora addresses these problems through student and membership verification, transparent ticket categories, group-aware seat recommendations, temporary seat holds, accessible booking, hosted payments, single-use QR tickets and a society dashboard.

## Target Users

- **Students and guests:** Discover society events, verify eligibility, find seats and purchase tickets.
- **Society committee members:** Create events, manage pricing and seating, and monitor sales.
- **Event volunteers:** Validate tickets and check attendees into the event.

## Key Requirements

| Identified problem | Tixora requirement |
|---|---|
| Societies offer different prices to members, students and guests | Events support member, student and general-admission ticket categories |
| Committees need to confirm ticket eligibility | Students verify their university email, while societies can maintain a simple member list or code |
| Groups struggle to find adjacent seats | The Smart Seat Finder recommends suitable contiguous seats |
| Customers may lose seats during checkout | Selected seats receive a time-limited hold before payment |
| Two customers may attempt to purchase the same seat | Short database transactions prevent confirmed double-bookings |
| Visual seat maps can be inaccessible | A keyboard-operable seat list and accessibility filters accompany the visual map |
| Extra fees may appear late in checkout | The complete payable price is displayed before payment |
| QR codes can be copied or reused | Tickets are validated against the server and can only be checked in once |
| Committees use separate sales and entry records | A society dashboard combines sales, capacity and attendance information |

## Minimum Viable Product

### Society and Event Management

- Committee members can create a society profile and publish events.
- Events contain a title, description, date, time, venue, entry conditions and ticket categories.
- Organisers select a predefined seating layout and label standard, accessible, companion, restricted-view or unavailable seats.
- Role-based access restricts event management and check-in functions.

### Student Eligibility and Ticket Categories

- Customers can create an account and verify a university email address.
- Organisers can offer separate society-member, university-student and guest tickets.
- Society membership can be confirmed through an organiser-managed member list or event membership code.
- The applicable ticket price and eligibility conditions are shown before seat selection.

The MVP will not connect directly to a university identity system. This avoids depending on restricted university infrastructure while still demonstrating eligibility management.

### Accessible Seat Selection and Smart Seat Finder

- Customers can select seats through a visual map or an equivalent keyboard-operable list.
- Filters identify accessible, companion, restricted-view and price-appropriate seats.
- The Smart Seat Finder accepts group size, budget, preferred section and accessibility needs.
- It ranks available adjacent seats according to group fit, accessibility match, price and section preference while avoiding unnecessary isolated single seats.

The Smart Seat Finder uses a transparent rules-based algorithm rather than artificial intelligence, keeping the feature explainable and feasible within the semester.

### Temporary Seat Holds and Live Availability

- Selecting seats creates an approximately five-minute hold and checkout timer.
- Held seats become unavailable to other customers.
- Expired holds and failed checkouts automatically release their seats.
- Server-Sent Events update connected seat maps when seats are held, released or sold.

The central seat lifecycle is:

`AVAILABLE → HELD → SOLD → CHECKED_IN`

### Transparent Payment and QR Tickets

- The complete ticket price, including any required fee, remains visible throughout checkout.
- Payment uses a payment provider’s hosted sandbox checkout, so Tixora does not store complete card information.
- The backend confirms payment before creating a unique QR ticket for each purchased seat.
- Repeated payment notifications cannot create duplicate orders or tickets.

### Volunteer Check-In and Society Dashboard

- Authorised volunteers can scan a QR code or enter a ticket number manually.
- The server identifies valid, already-used, cancelled, wrong-event and invalid tickets.
- A successfully checked-in ticket cannot be reused.
- Committee members can view ticket sales, remaining capacity, ticket categories and attendance.

## High-Level Goals

1. Give university societies one platform for ticket sales, seating and event entry.
2. Support member, student and guest ticketing without requiring university-system integration.
3. Make group and accessible seat selection easier for students.
4. Prevent confirmed double-bookings and repeated ticket use.
5. Provide transparent pricing and a reliable purchase-to-check-in experience.
6. Build a maintainable full-stack application using object-oriented design.

## Scope

The MVP will use predefined seating layouts, hosted sandbox payments and online check-in. Direct university single sign-on, advanced bot detection, virtual waiting rooms, automated refunds, ticket resale, a drag-and-drop venue editor, dynamic pricing and offline check-in synchronisation are outside the initial scope.

Possible future features include an automatic waitlist, controlled ticket transfers, email or SMS delivery and support for community organisations.

## Technology Stack

- **Front end:** React
- **Back end:** Java and Spring Boot
- **Database:** MySQL with Spring Data JPA
- **Security:** Spring Security
- **Live updates:** Server-Sent Events
- **Payment:** Hosted checkout in sandbox mode
- **E-tickets:** QR generation and server-side validation
- **Collaboration:** GitHub and Jira

## MVP Success Criteria

The MVP will be successful when:

1. A society can publish an event with ticket categories and a predefined seating layout.
2. A student can verify eligibility and purchase the correct ticket type.
3. A customer can find suitable seats manually or through the Smart Seat Finder.
4. Selected seats are temporarily held without confirmed double-booking.
5. Successful sandbox payment creates each QR ticket exactly once.
6. Volunteers can validate tickets and reject repeated use.
7. The society dashboard accurately displays sales, capacity and attendance.
