package com.ooaf.eventticketing.service;

import com.ooaf.eventticketing.dto.BookingExportResponse;
import com.ooaf.eventticketing.dto.BookingResponse;
import com.ooaf.eventticketing.dto.CloseRegistrationResponse;
import com.ooaf.eventticketing.dto.CreateBookingRequest;
import com.ooaf.eventticketing.entity.*;
import com.ooaf.eventticketing.repository.BookingRepository;
import com.ooaf.eventticketing.repository.EventRepository;
import com.ooaf.eventticketing.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class BookingService{
    private static final int REFERENCE_BYTES = 4;

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    public BookingService(BookingRepository bookingRepository, EventRepository eventRepository, UserRepository userRepository){
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }
    @Transactional
    public BookingResponse createBooking(Long eventId, String userEmail, CreateBookingRequest request){
        User user = requireUser(userEmail);
        return bookingRepository.findByIdempotencyKey(
                        request.idempotencyKey()
                ).map(existing -> handleIdempotentRetry(existing, eventId, user.getId()))
                .orElseGet(() -> createNewBooking(eventId, user, request));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listCurrentUserBookings(String userEmail){
        User user = requireUser(userEmail);
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(BookingResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listEventBookings(Long eventId, String userEmail){
        User user = requireUser(userEmail);
        Event event = requireEvent(eventId);
        requireCommitteeMemberForEvent(user, event);
        return bookingRepository.findByEventIdOrderByCreatedAtAsc(eventId)
                .stream()
                .map(BookingResponse::fromEntity)
                .toList();
    }

    @Transactional
    public CloseRegistrationResponse closeRegistration(Long eventId, String userEmail){
        User user = requireUser(userEmail);
        Event event = requireEventForUpdate(eventId);
        requireCommitteeMemberForEvent(user, event);
        event.setRegistrationStatus(EventRegistrationStatus.CLOSED);
        long confirmedBookings = countActiveBookings(eventId);
        long groups = bookingRepository.findByEventIdOrderByCreatedAtAsc(eventId)
                .stream()
                .map(Booking::getGroupCode)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        return new CloseRegistrationResponse(event.getId(), event.getRegistrationStatus(), confirmedBookings, groups, true);
    }

    @Transactional(readOnly = true)
    public List<BookingExportResponse> exportForAllocation(Long eventId, String userEmail){
        User user = requireUser(userEmail);
        Event event = requireEvent(eventId);
        requireCommitteeMemberForEvent(user, event);
        if(event.getRegistrationStatus() != EventRegistrationStatus.CLOSED){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Close registration before exporting bookings");
        }
        return bookingRepository.findByEventIdOrderByCreatedAtAsc(eventId)
                .stream()
                .filter(booking -> booking.getStatus() != BookingStatus.CANCELLED)
                .map(BookingExportResponse::fromEntity)
                .toList();
    }

    private BookingResponse createNewBooking(Long eventId, User user, CreateBookingRequest request){
        Event event = requireEventForUpdate(eventId);
        var existingBooking = bookingRepository.findByIdempotencyKey(request.idempotencyKey());
        if(existingBooking.isPresent()){
            return handleIdempotentRetry(existingBooking.get(), eventId, user.getId());
        }
        // Only published events can accept new bookings.
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Event not found: " + eventId);
        }
        if (event.getRegistrationStatus() != EventRegistrationStatus.OPEN){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Registration is closed for this event");
        }
        if(bookingRepository.existsByEventIdAndUserIdAndStatusNot(eventId, user.getId(), BookingStatus.CANCELLED)){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already has a booking for this event");
        }
        if(countActiveBookings(eventId) >= event.getCapacity()){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event capacity has been reached");
        }
        boolean createGroup = Boolean.TRUE.equals(request.createGroup());
        String joinGroupCode = normalizeGroupCode(request.joinGroupCode());
        if(createGroup && joinGroupCode != null){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use either createGroup or joinGroupCode");
        }
        String groupCode = resolveGroupCode(eventId, createGroup, joinGroupCode);
        Booking booking = new Booking(nextReference("BKG"), event, user, groupCode, Boolean.TRUE.equals(request.accessibilityRequired()), request.idempotencyKey());
        return BookingResponse.fromEntity(bookingRepository.saveAndFlush(booking));
    }

    private BookingResponse handleIdempotentRetry(Booking existing, Long eventId, Long userId){
        if(!existing.getEvent().getId().equals(eventId) || !existing.getUser().getId().equals(userId)){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key has already been used");
        }
        return BookingResponse.fromEntity(existing);
    }

    private String resolveGroupCode(Long eventId, boolean createGroup, String joinGroupCode){
        if(createGroup){
            return nextReference("GRP");
        }
        if(joinGroupCode == null){
            return null;
        }
        if(!bookingRepository.existsByEventIdAndGroupCodeAndStatusNot(eventId, joinGroupCode, BookingStatus.CANCELLED)){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group code does not exist for this event");
        }
        return joinGroupCode;
    }

    private long countActiveBookings(Long eventId){
        return bookingRepository.countByEventIdAndStatusNot(eventId, BookingStatus.CANCELLED);
    }

    private Event requireEvent(Long eventId){
        return eventRepository.findById(eventId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + eventId));
    }

    private Event requireEventForUpdate(Long eventId){
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + eventId));
    }

    private User requireUser(String email){
        return userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
    }

    private void requireCommitteeMember(User user){
        if (user.getRole() != UserRole.COMMITTEE_MEMBER){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Committee member access is required");
        }
    }

    private void requireCommitteeMemberForEvent(User user, Event event){
        requireCommitteeMember(user);
        if (!sameSociety(user.getSocietyName(), event.getOrganiserName())){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Committee member can only manage events for their society");
        }
    }

    private boolean sameSociety(String userSocietyName, String eventOrganiserName){
        return userSocietyName != null
                && eventOrganiserName != null
                && userSocietyName.trim().equalsIgnoreCase(eventOrganiserName.trim());
    }

    private String nextReference(String prefix){
        byte[] bytes = new byte[REFERENCE_BYTES];
        secureRandom.nextBytes(bytes);
        return prefix + "-" + HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
    }

    private String normalizeGroupCode(String groupCode){
        if (groupCode == null || groupCode.isBlank()){
            return null;
        }
        return groupCode.trim().toUpperCase(Locale.ROOT);
    }
}
