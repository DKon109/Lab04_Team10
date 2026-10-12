package com.ooaf.eventticketing.entity;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    private String imageUrl;

    @Column(nullable = false)
    private LocalDateTime dateTime;

    @Column(nullable = false)
    private String venue;

    @Column(nullable = false)
    private String city;

    private String university;

    @Column(nullable = false)
    private String organiserName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganiserType organiserType;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, columnDefinition = "integer default 100")
    private Integer capacity = 100;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32) default 'OPEN'")
    private EventRegistrationStatus registrationStatus = EventRegistrationStatus.OPEN;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_tags", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    public Event(String title, String description, String imageUrl, LocalDateTime dateTime,
                 String venue, String city, String university, String organiserName,
                 OrganiserType organiserType, String category, List<String> tags) {
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
        this.dateTime = dateTime;
        this.venue = venue;
        this.city = city;
        this.university = university;
        this.organiserName = organiserName;
        this.organiserType = organiserType;
        this.category = category;
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getUniversity() {
        return university;
    }

    public void setUniversity(String university) {
        this.university = university;
    }

    public String getOrganiserName() {
        return organiserName;
    }

    public void setOrganiserName(String organiserName) {
        this.organiserName = organiserName;
    }

    public OrganiserType getOrganiserType() {
        return organiserType;
    }

    public void setOrganiserType(OrganiserType organiserType) {
        this.organiserType = organiserType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    // Nullable for existing databases: unconfirmed legacy events are safely false.
    @Column
    private Boolean seatIdentifiersConfirmed = false;

    public boolean isSeatIdentifiersConfirmed() { return Boolean.TRUE.equals(seatIdentifiersConfirmed); }
    public void setSeatIdentifiersConfirmed(boolean confirmed) { this.seatIdentifiersConfirmed = confirmed; }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public EventRegistrationStatus getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(EventRegistrationStatus registrationStatus){
        this.registrationStatus = registrationStatus;
    }
}
