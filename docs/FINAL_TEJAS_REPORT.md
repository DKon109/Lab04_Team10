# Section 1 — Introduction (Tejas Singh, 530623218)

Prepared 2 November 2026. Content review by Aadi is pending; this document does not record a review on Aadi's behalf.

## 1.1 Overview

Tixora is a campus-first event discovery, booking and group-aware seating application developed for ELEC5619. It addresses the difficulty of coordinating seats for friends who register independently. Without a shared workflow, organisers must reconcile individual reservations, group requests, room capacity and accessibility needs, while attendees may arrive without knowing whether they can sit together. Tixora connects these activities through a React interface, Spring Boot REST services and MySQL persistence.

The central journey combines individual booking with a private, event-specific group code. Each attendee completes their own mock checkout. Once registration closes, the organiser runs allocation over confirmed bookings, and attendees receive their own ticket and seat-map view. Organisers can inspect allocation results and produce anonymous CSV and printable plans. The project's contribution is this coordinated workflow and its constraint enforcement, rather than a claim that event discovery or automatic seat allocation is a new concept.

## 1.2 Aims

The project aims to make campus events easier to discover and manage, preserve independent registration while allowing friends to request seating together, and give organisers repeatable allocation results. The allocation process seeks contiguous seats, records explanations when groups split, and respects capacity, blocked positions, accessibility requirements and aisle boundaries. Deterministic tie-breaking makes outcomes reproducible, although the heuristic does not establish a globally optimal placement for every layout.

Further aims are to protect attendee information in shared seating outputs, distinguish confirmed bookings from failed checkout attempts, prevent duplicate reservations and assignments, and present understandable customer and organiser states. The proposal sets a performance target of less than ten seconds for supported, feasible allocation datasets of up to 300 attendees in a documented environment. Accessibility is a design target for keyboard use, labelled controls, visible focus and textual seat information; it requires evidence from the final walkthrough as well as automated checks.

## 1.3 Project Scope

The implemented scope includes account registration and login, event discovery with search and filters, likes and a rules-based For You feed, organiser draft creation/editing/publication/closure, individual and private-group bookings, simulated payment outcomes, deferred allocation, private tickets, seating maps, allocation metrics and anonymous exports. Two venue paths feed the seating workflow: a catalogue of three Seymour Centre campus plans and configurable front-stage, fan and central-stage external layouts. The campus plans are derived from published seating plans; schematic geometry and demonstration accessibility settings require physical verification before use at a real event.

Exact seat allocation is enabled only when the organiser confirms physical identifiers matching the stored plan. Otherwise, the event remains general admission and the interface does not promise a seat number. Real payments and refunds, university or USU identity integration, automatic floor-plan recognition, occupancy sensing and a universal venue editor remain outside the MVP. Tickets are private booking references and seat information; the proposal's conceptual QR/check-in and separate-entity details are not evidence of an implemented door-scanning system. These implementation differences must remain explicit in the final report.

## 1.4 Project Scenarios

In a campus event scenario, a society organiser creates a draft, selects a saved room map, checks its capacity and confirms the relevant physical identifiers before publication. A student discovers the event, reviews its details, creates a private group and completes simulated checkout. Friends enter that event's code and book individually. After closure, the organiser runs allocation. Each attendee opens their own ticket to locate the assigned seat; any group split has an explanation. The organiser downloads a seating plan using references rather than attendee names or emails.

In an external-event scenario, the organiser configures one of the three supported layout families, including dimensions, aisles, blocked positions and accessible seats. The same booking and allocation workflow follows. If physical seat identifiers cannot be provided, the organiser chooses general admission instead. Declined or cancelled mock checkout does not create a confirmed booking or consume capacity, and repeating a successful request returns the existing result. These failure and retry paths are part of the intended journey, not exceptional manual workarounds.

## 1.5 Audiences and Stakeholders

Primary audiences are students attending university events and society, faculty or selected Sydney organisers managing those events. Attendees need clear discovery, independent booking, understandable group membership and reliable arrival information. Organisers need usable venue configuration, controlled publication, accurate registration counts and explainable allocation outputs. Venue operators and accessibility stakeholders influence the correctness of physical layouts, accessible positions and installed identifiers. Door staff are secondary readers of tickets and anonymous plans; a dedicated check-in application is not part of the delivered workflow. The development team and teaching staff are stakeholders in maintainability, integration quality and evidence against the submitted proposal.

## Evidence and review handoff

- Proposal: `Tixora Project Proposal 2026.pdf`, sections 1, 3–6 and 8–10. This is the submitted scope source, rather than the older Sprint 0 README roadmap.
- PR #12: attendee experience, checkout and persisted seating integration. PRs #22–23: later event/venue and frontend integration. PRs #24–25: allocation coverage, stage ordering and printable outputs.
- Attendee source: `frontend/src/pages/{AuthPage,EventsPage,BookingPage,MyBookingsPage,TicketAndSeatMapView}.jsx`, `frontend/src/components/events/EventDetailsModal.jsx`, `frontend/src/context/AuthContext.jsx`.
- Organiser source: `frontend/src/pages/{EventCreationWizard,EventSeatingPage,OrganiserDashboard}.jsx`, `frontend/src/components/VenuePicker.jsx`.
- Backend evidence: `service/{EventService,MockCheckoutService,SeatingService,SeatingMapManagementService,CampusVenueTemplateService}.java`, `allocation/SeatAllocationService.java` beneath `app/src/main/java/com/ooaf/eventticketing/`.
- Related Jira references found in the repository: SCRUM-45 (contract review), SCRUM-64 (campus demonstration boundary), SCRUM-67 (integrated journeys) and SCRUM-68 (handoff). These are shared context, not a claim that Tejas owns or has closed those issues. See `docs/BACKEND_HANDOFF.md`.
- Current test results and unresolved acceptance items belong in `docs/FINAL_ACCEPTANCE.md`. Screenshots must come from the final live application; older Sprint 2 images must not be relabelled as current evidence.
- Cover identity for Tejas: **Tejas Singh | 530623218**, matching the submitted proposal. Other members must confirm their own details; the report and proposal use different forms of Rafly's name.

## Tejas's review of Aadi's Section 3

The linked report contained only the headings “3.1 Project Functionalities” and “3.2 Non Functional Requirements Achieved” when inspected on 2 November. There is no authored section to approve yet. Review status: **awaiting Aadi's draft**, not approved.

When supplied, check the following against the integrated application:

- Describe general admission separately from pending assigned seating, and include decline/cancel/retry paths.
- Describe the saved managed map and external-template limits, with the physical identifier condition.
- Explain changes from the conceptual proposal: scalar group codes, checkout attempts and computed tickets replace several separate conceptual entities; QR scanning/check-in is not implemented.
- Distinguish measured performance from a universal guarantee, and accessibility design support from audited WCAG conformance.
- Link each claimed feature to the relevant PR, source and current test or screenshot. Do not treat a heading, endpoint or retained old test report as completion evidence.
