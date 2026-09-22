package com.ooaf.eventticketing.config;

import com.ooaf.eventticketing.entity.Event;
import com.ooaf.eventticketing.entity.OrganiserType;
import com.ooaf.eventticketing.entity.User;
import com.ooaf.eventticketing.entity.UserRole;
import com.ooaf.eventticketing.repository.EventRepository;
import com.ooaf.eventticketing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private static final String DEMO_ORGANISER_EMAIL = "committee.demo@uni.sydney.edu.au";
    private static final String DEMO_ORGANISER_SOCIETY = "USYD Coding Society";

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Demo-only organiser login for the Sprint 1 organiser dashboard, since
     * public registration only creates STUDENT accounts. Override the
     * password with DEMO_ORGANISER_PASSWORD; remove this seed before any
     * real deployment.
     */
    @Value("${DEMO_ORGANISER_PASSWORD:OrganiserDemo1!}")
    private String demoOrganiserPassword;

    public DataSeeder(EventRepository eventRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedDemoOrganiser();
        if (eventRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String usyd = "USYD";
        String sydney = "Sydney";
        List<Event> events = List.of(
                new Event("USYD Vietnamese Society Lunar New Year Ball", "A night of food, performances and games celebrating Lunar New Year.",
                        "https://picsum.photos/seed/vsoc-lny/640/360", now.plusDays(9).withHour(19).withMinute(0),
                        "Wentworth Ballroom", sydney, usyd, "USYD Vietnamese Society", OrganiserType.SOCIETY,
                        "Cultural", List.of("ball", "food", "performance")),
                new Event("Sydney University Dramatic Society: Spring One-Acts", "Three student-written one-act plays performed live.",
                        "https://picsum.photos/seed/suds-plays/640/360", now.plusDays(4).withHour(19).withMinute(30),
                        "Cellar Theatre", sydney, usyd, "SUDS", OrganiserType.SOCIETY,
                        "Arts", List.of("theatre", "student-written")),
                new Event("USYD Coding Society Hackathon Kickoff", "Team formation and problem statement reveal for the 24-hour hackathon.",
                        "https://picsum.photos/seed/codesoc-hack/640/360", now.plusDays(12).withHour(17).withMinute(0),
                        "Susan Wakil Building", sydney, usyd, "USYD Coding Society", OrganiserType.SOCIETY,
                        "Technology", List.of("hackathon", "coding", "networking")),
                new Event("Filmmakers Society Screening Night", "Student short films followed by a Q&A with the directors.",
                        "https://picsum.photos/seed/filmsoc-screening/640/360", now.plusDays(6).withHour(18).withMinute(30),
                        "New Law School Annexe", sydney, usyd, "USYD Filmmakers Society", OrganiserType.SOCIETY,
                        "Arts", List.of("film", "screening")),
                new Event("Chess Society Weekly Blitz Night", "Casual blitz tournament, all skill levels welcome.",
                        "https://picsum.photos/seed/chess-blitz/640/360", now.plusDays(2).withHour(17).withMinute(0),
                        "Holme Building", sydney, usyd, "USYD Chess Society", OrganiserType.SOCIETY,
                        "Games", List.of("chess", "casual")),
                new Event("Photography Society Golden Hour Walk", "A guided photo walk around campus and Victoria Park.",
                        "https://picsum.photos/seed/photosoc-walk/640/360", now.plusDays(3).withHour(17).withMinute(0),
                        "Victoria Park", sydney, usyd, "USYD Photography Society", OrganiserType.SOCIETY,
                        "Arts", List.of("photography", "outdoors")),
                new Event("Wine and Cheese Society Tasting Night", "Guided tasting of Australian wines paired with cheese.",
                        "https://picsum.photos/seed/wine-cheese/640/360", now.plusDays(14).withHour(19).withMinute(0),
                        "Manning Bar", sydney, usyd, "USYD Wine and Cheese Society", OrganiserType.SOCIETY,
                        "Food & Drink", List.of("tasting", "social")),
                new Event("Debating Society Novice Grand Final", "The final round of the semester's novice debating competition.",
                        "https://picsum.photos/seed/debate-final/640/360", now.plusDays(8).withHour(18).withMinute(0),
                        "Merewether Building", sydney, usyd, "Sydney University Debating Society", OrganiserType.SOCIETY,
                        "Academic", List.of("debating", "competition")),
                new Event("K-Pop Dance Society Showcase", "End-of-semester dance cover showcase.",
                        "https://picsum.photos/seed/kpop-showcase/640/360", now.plusDays(11).withHour(18).withMinute(0),
                        "Seymour Centre", sydney, usyd, "USYD K-Pop Dance Society", OrganiserType.SOCIETY,
                        "Performance", List.of("dance", "kpop", "showcase")),
                new Event("Environment Collective Clothes Swap", "Bring an item, take an item — free sustainable fashion swap.",
                        "https://picsum.photos/seed/env-swap/640/360", now.plusDays(5).withHour(11).withMinute(0),
                        "Eastern Avenue", sydney, usyd, "USYD Environment Collective", OrganiserType.SOCIETY,
                        "Community", List.of("sustainability", "free")),
                new Event("Engineering Faculty Industry Networking Night", "Meet graduate recruiters from top engineering firms.",
                        "https://picsum.photos/seed/eng-networking/640/360", now.plusDays(7).withHour(18).withMinute(0),
                        "Chau Chak Wing Building", sydney, usyd, "Faculty of Engineering", OrganiserType.FACULTY,
                        "Careers", List.of("networking", "engineering")),
                new Event("Business School Alumni Panel: Careers in Finance", "Panel discussion with USYD Business School alumni working in finance.",
                        "https://picsum.photos/seed/biz-panel/640/360", now.plusDays(10).withHour(17).withMinute(30),
                        "Abercrombie Building", sydney, usyd, "University of Sydney Business School", OrganiserType.FACULTY,
                        "Careers", List.of("finance", "alumni", "panel")),
                new Event("Faculty of Science Open Research Day", "Undergraduates showcase semester research projects.",
                        "https://picsum.photos/seed/sci-research/640/360", now.plusDays(13).withHour(10).withMinute(0),
                        "Sydney Nanoscience Hub", sydney, usyd, "Faculty of Science", OrganiserType.FACULTY,
                        "Academic", List.of("research", "science")),
                new Event("Law School Careers Fair", "Meet clerkship and graduate program recruiters from major law firms.",
                        "https://picsum.photos/seed/law-fair/640/360", now.plusDays(15).withHour(11).withMinute(0),
                        "New Law School", sydney, usyd, "Sydney Law School", OrganiserType.FACULTY,
                        "Careers", List.of("law", "clerkship")),
                new Event("Conservatorium Lunchtime Concert Series", "Free lunchtime concert performed by Conservatorium students.",
                        "https://picsum.photos/seed/con-lunchtime/640/360", now.plusDays(1).withHour(13).withMinute(0),
                        "Sydney Conservatorium of Music", sydney, usyd, "Sydney Conservatorium of Music", OrganiserType.FACULTY,
                        "Performance", List.of("music", "free", "classical")),
                new Event("Medicine Faculty Wellbeing Workshop", "Mindfulness and stress-management workshop ahead of exam block.",
                        "https://picsum.photos/seed/med-wellbeing/640/360", now.plusDays(16).withHour(12).withMinute(0),
                        "Susan Wakil Health Building", sydney, usyd, "Faculty of Medicine and Health", OrganiserType.FACULTY,
                        "Wellbeing", List.of("workshop", "wellbeing")),
                new Event("Architecture Faculty End-of-Year Exhibition", "Final year architecture and design student showcase.",
                        "https://picsum.photos/seed/arch-exhibition/640/360", now.plusDays(18).withHour(17).withMinute(0),
                        "Wilkinson Building", sydney, usyd, "Faculty of Architecture, Design and Planning", OrganiserType.FACULTY,
                        "Arts", List.of("exhibition", "design")),
                new Event("Arts and Social Sciences Guest Lecture: Media and Democracy", "Public lecture from a visiting political science scholar.",
                        "https://picsum.photos/seed/fass-lecture/640/360", now.plusDays(20).withHour(18).withMinute(0),
                        "Social Sciences Building", sydney, usyd, "Faculty of Arts and Social Sciences", OrganiserType.FACULTY,
                        "Academic", List.of("lecture", "public")),
                new Event("Sydney Night Noodle Markets", "Pop-up street food market featuring Sydney's best noodle vendors.",
                        "https://picsum.photos/seed/noodle-markets/640/360", now.plusDays(4).withHour(17).withMinute(0),
                        "Hyde Park", sydney, null, "Time Out Sydney", OrganiserType.OTHER,
                        "Food & Drink", List.of("market", "food", "outdoors")),
                new Event("Sydney Fringe Comedy Showcase", "A rotating lineup of Sydney's rising stand-up comedians.",
                        "https://picsum.photos/seed/fringe-comedy/640/360", now.plusDays(6).withHour(20).withMinute(0),
                        "Giant Dwarf", sydney, null, "Sydney Fringe Festival", OrganiserType.OTHER,
                        "Comedy", List.of("standup", "fringe")),
                new Event("Vivid Sydney Light Walk Preview", "Early preview walk through this year's Vivid light installations.",
                        "https://picsum.photos/seed/vivid-preview/640/360", now.plusDays(22).withHour(19).withMinute(0),
                        "Circular Quay", sydney, null, "Destination NSW", OrganiserType.OTHER,
                        "Festival", List.of("lights", "outdoors", "festival")),
                new Event("Bondi Beach Volleyball Social", "Casual social volleyball, no experience needed.",
                        "https://picsum.photos/seed/bondi-volleyball/640/360", now.plusDays(3).withHour(9).withMinute(0),
                        "Bondi Beach", sydney, null, "Sydney Beach Sports", OrganiserType.OTHER,
                        "Sports", List.of("volleyball", "beach", "social")),
                new Event("Sydney Startup Founders Meetup", "Monthly meetup for early-stage founders and operators.",
                        "https://picsum.photos/seed/startup-meetup/640/360", now.plusDays(9).withHour(18).withMinute(30),
                        "Fishburners", sydney, null, "Sydney Startup Community", OrganiserType.OTHER,
                        "Networking", List.of("startups", "networking")),
                new Event("Carriageworks Farmers Market", "Weekly farmers market with fresh produce and live music.",
                        "https://picsum.photos/seed/carriageworks-market/640/360", now.plusDays(2).withHour(8).withMinute(0),
                        "Carriageworks", sydney, null, "Carriageworks", OrganiserType.OTHER,
                        "Community", List.of("market", "food")),
                new Event("Sydney Opera House Jazz Night", "An evening of live jazz on the harbour.",
                        "https://picsum.photos/seed/sydney-jazz/640/360", now.plusDays(17).withHour(19).withMinute(30),
                        "Sydney Opera House", sydney, null, "Sydney Opera House", OrganiserType.OTHER,
                        "Music", List.of("jazz", "live-music")),
                new Event("City2Surf Training Fun Run", "A community training run along the City2Surf route.",
                        "https://picsum.photos/seed/city2surf-training/640/360", now.plusDays(19).withHour(7).withMinute(0),
                        "Hyde Park", sydney, null, "Sydney Running Club", OrganiserType.OTHER,
                        "Sports", List.of("running", "training")),
                new Event("Newtown Street Art Walking Tour", "A guided tour of Newtown and Enmore's best street art.",
                        "https://picsum.photos/seed/newtown-artwalk/640/360", now.plusDays(5).withHour(14).withMinute(0),
                        "Newtown", sydney, null, "Sydney Culture Walks", OrganiserType.OTHER,
                        "Arts", List.of("street-art", "walking-tour")),
                new Event("Darling Harbour Fireworks Night", "Weekly Saturday fireworks display over Darling Harbour.",
                        "https://picsum.photos/seed/darling-fireworks/640/360", now.plusDays(1).withHour(21).withMinute(0),
                        "Darling Harbour", sydney, null, "Darling Harbour Precinct", OrganiserType.OTHER,
                        "Festival", List.of("fireworks", "family")),
                new Event("USYD Welcome Week Bazaar", "Semester welcome bazaar with club sign-ups and giveaways.",
                        "https://picsum.photos/seed/welcome-bazaar/640/360", now.minusDays(20).withHour(11).withMinute(0),
                        "Eastern Avenue", sydney, usyd, "USU", OrganiserType.SOCIETY,
                        "Community", List.of("welcome-week", "clubs")),
                new Event("Sydney Harbour New Year Fireworks", "Sydney's flagship New Year's Eve fireworks display.",
                        "https://picsum.photos/seed/nye-fireworks/640/360", now.minusDays(60).withHour(21).withMinute(0),
                        "Sydney Harbour", sydney, null, "City of Sydney", OrganiserType.OTHER,
                        "Festival", List.of("fireworks", "nye"))
        );
        eventRepository.saveAll(events);
    }

    private void seedDemoOrganiser() {
        userRepository.findByEmail(DEMO_ORGANISER_EMAIL)
                .ifPresentOrElse(this::ensureDemoOrganiserSociety, () -> userRepository.save(new User(
                    DEMO_ORGANISER_EMAIL,
                    passwordEncoder.encode(demoOrganiserPassword),
                    UserRole.COMMITTEE_MEMBER,
                    DEMO_ORGANISER_SOCIETY
            )));
    }

    private void ensureDemoOrganiserSociety(User user) {
        if (user.getSocietyName() == null || user.getSocietyName().isBlank()) {
            user.setSocietyName(DEMO_ORGANISER_SOCIETY);
            userRepository.save(user);
        }
    }
}
