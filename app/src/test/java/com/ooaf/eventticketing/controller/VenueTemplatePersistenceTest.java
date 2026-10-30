package com.ooaf.eventticketing.controller;

import com.ooaf.eventticketing.dto.SeatMapRequest;
import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.EventRepository;
import com.ooaf.eventticketing.repository.UserRepository;
import com.ooaf.eventticketing.service.CampusVenueTemplateService;
import com.ooaf.eventticketing.service.ExternalVenueTemplateService;
import com.ooaf.eventticketing.service.SeatingMapManagementService;
import com.ooaf.eventticketing.dto.ExternalVenueTemplateRequest;
import com.ooaf.eventticketing.dto.SeatingLayoutRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

@SpringBootTest
@ActiveProfiles("test")
class VenueTemplatePersistenceTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CampusVenueTemplateService campusTemplates;

    @Autowired
    private ExternalVenueTemplateService externalTemplates;

    @Autowired
    private SeatingMapManagementService managedSeating;

    @Autowired
    private EventRepository events;

    @Autowired
    private UserRepository users;

    private record TestData(
            Long ownerId,
            Long eventId,
            String ownerEmail,
            SeatingMapManagementService.SeatMapView savedMap
    ) {}

    private TransactionTemplate newTransaction() {
        var transaction =  new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        transaction.setIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED
        );
        return transaction;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "usyd-seymour-everest",
            "usyd-seymour-reginald",
            "usyd-seymour-york"
    })
    void campusTemplateSurvivesCommitAndReload(String templateId) {
        var template = campusTemplates.listTemplates().stream().filter(
                item -> item.id().equals(templateId))
                        .findFirst()
                        .orElseThrow()
                        .map();

                TestData data = newTransaction().execute(
                        status -> createSavedTemplate(template)
                );
                assertNotNull(data);

                try {
                    newTransaction().executeWithoutResult(status -> {
                        var reloaded = managedSeating.organiserMap(
                                data.eventId(), data.ownerEmail()
                        );

                        assertEquals(data.savedMap(), reloaded);

                        var event = events.findById(data.eventId()).orElseThrow();

                        assertEquals(Math.toIntExact(reloaded.publicCapacity()),
                                event.getCapacity().intValue()
                        );
                        assertFalse(event.isSeatIdentifiersConfirmed());
                    });
                } finally {
                    deleteTestData(data);
                }
    }

    @ParameterizedTest
    @EnumSource(SeatingLayoutRequest.Template.class)
    void setExternalTemplateSurvivesCommitAndReload(
            SeatingLayoutRequest.Template family) {
        var template = externalTemplates.generate(
                new ExternalVenueTemplateRequest(
                        "External persistence demo",
                        family,
                        3, 4, 2,
                        List.of("C4"),
                        List.of("A1")
                )
        );

        TestData data = newTransaction().execute(
                status -> createSavedTemplate(template)
        );
        assertNotNull(data);

        try {
            newTransaction().executeWithoutResult(status -> {
                var reloaded = managedSeating.organiserMap(
                        data.eventId(), data.ownerEmail()
                );

                assertEquals(data.savedMap(), reloaded);
                assertEquals(12L, reloaded.totalSeats());
                assertEquals(11L, reloaded.publicCapacity());

                var seats = reloaded.elements().stream().flatMap(element -> element.seats().stream())
                        .toList();

                assertTrue(seats.stream().filter(seat -> seat.code().equals("A1"))
                        .findFirst().orElseThrow().accessible());

                assertEquals(
                        SeatingMapManagementService.SeatStatus.BLOCKED,
                        seats.stream()
                                .filter(seat -> seat.code().equals("C4"))
                                .findFirst().orElseThrow().status()
                );

                var event = events.findById(data.eventId()).orElseThrow();
                assertEquals(11, event.getCapacity().intValue());
                assertFalse(event.isSeatIdentifiersConfirmed());
            });
        } finally {
            deleteTestData(data);
        }
    }

    private TestData createSavedTemplate(SeatMapRequest template) {
        String suffix = UUID.randomUUID().toString();

        var owner = users.saveAndFlush(new User(
                "venue-owner-" + suffix + "@test.local",
                "unused",
                UserRole.COMMITTEE_MEMBER,
                "Demo society"
        ));

        var event = new Event(
                "Venue template persistence test",
                "Test event",
                null,
                LocalDateTime.now().plusDays(2),
                "Test venue",
                "Sydney",
                "USYD",
                "Demo society",
                OrganiserType.SOCIETY,
                "Arts",
                List.of()
        );
        event.setOwner(owner);
        Long eventId = events.saveAndFlush(event).getId();

        var saveMap = managedSeating.saveMap(
                eventId, owner.getEmail(), template
        );

        return new TestData(
                owner.getId(),
                eventId,
                owner.getEmail(),
                saveMap
        );
    }

    private void deleteTestData(TestData data) {
        newTransaction().executeWithoutResult(status -> {
            managedSeating.deleteMap(data.eventId(), data.ownerEmail());

            // Flush map deletions before removing the event and owner.
            events.flush();

            events.deleteById(data.eventId());
            events.flush();

            users.deleteById(data.ownerId());
            users.flush();
        });
    }
}
