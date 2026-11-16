package com.ga.HomeHub.booking.seed;

import com.ga.HomeHub.booking.model.AuditLog;
import com.ga.HomeHub.booking.model.Availability;
import com.ga.HomeHub.booking.model.Booking;
import com.ga.HomeHub.booking.model.BookingRescheduleRequest;
import com.ga.HomeHub.booking.model.BookingWaitlist;
import com.ga.HomeHub.booking.model.Category;
import com.ga.HomeHub.booking.model.Home;
import com.ga.HomeHub.booking.model.HomeownerAssessment;
import com.ga.HomeHub.booking.model.ProviderProfile;
import com.ga.HomeHub.booking.model.ServiceFavorite;
import com.ga.HomeHub.booking.model.ServiceOffering;
import com.ga.HomeHub.booking.model.ServiceReport;
import com.ga.HomeHub.booking.model.ServiceReview;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.model.enums.BookingStatus;
import com.ga.HomeHub.booking.model.enums.BookingType;
import com.ga.HomeHub.booking.model.enums.CategoryStatus;
import com.ga.HomeHub.booking.model.enums.ProviderStatus;
import com.ga.HomeHub.booking.model.enums.RescheduleStatus;
import com.ga.HomeHub.booking.model.enums.Role;
import com.ga.HomeHub.booking.model.enums.ServiceStatus;
import com.ga.HomeHub.booking.model.enums.UserStatus;
import com.ga.HomeHub.booking.model.enums.WaitlistStatus;
import com.ga.HomeHub.booking.repository.ProviderProfileRepository;
import com.ga.HomeHub.booking.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="homehub.seed.enabled",havingValue="true")
public class BookingDataSeed implements CommandLineRunner{
    private static final Logger logger=LoggerFactory.getLogger(BookingDataSeed.class);
    private static final String CATEGORY_NAME="HomeHub Demo Cleaning";

    private final UserRepository users;
    private final ProviderProfileRepository providers;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;

    @Value("${homehub.seed.homeowner-email:}")
    private String homeownerEmail;

    @Value("${homehub.seed.second-homeowner-email:}")
    private String secondHomeownerEmail;

    @Value("${homehub.seed.provider-email:}")
    private String providerEmail;

    @Override
    @Transactional
    public void run(String... args){
        //Serialize seed execution when multiple Booking instances start together.
        jdbc.execute("select pg_advisory_xact_lock(70448423003)");

        Long existing=entityManager.createQuery(
                        "select count(c) from Category c where c.name=:name",Long.class)
                .setParameter("name",CATEGORY_NAME)
                .getSingleResult();

        if(existing>0){
            logger.info("Booking demo data already exists; skipping seed");
            return;
        }

        User homeowner=findAccount(homeownerEmail,Role.HOMEOWNER);
        User secondHomeowner=findAccount(secondHomeownerEmail,Role.HOMEOWNER);
        User providerUser=findAccount(providerEmail,Role.PROVIDER);

        if(homeowner.getId().equals(secondHomeowner.getId())){
            throw new IllegalArgumentException("Configure two different homeowner accounts");
        }

        ProviderProfile provider=providers.findByUserId(providerUser.getId())
                .orElse(null);

        if(provider==null){
            provider=new ProviderProfile();
            provider.setUser(providerUser);
            provider.setBusinessName("HomeHub Demo Cleaning Team");
            provider.setDescription("Fictional provider profile for local testing");
            provider.setPortfolioDescription("Demo cleaning and home-care services");
            provider.setProviderStatus(ProviderStatus.APPROVED);
            provider.setEmergencyEnabled(true);
            entityManager.persist(provider);
        }else if(provider.getProviderStatus()!=ProviderStatus.APPROVED){
            throw new IllegalArgumentException(
                    "Approve the existing provider profile before running the Booking seed");
        }

        Category category=new Category();
        category.setName(CATEGORY_NAME);
        category.setDescription("Demo category created by BookingDataSeed");
        category.setStatus(CategoryStatus.ACTIVE);
        entityManager.persist(category);

        Home firstHome=createHome(homeowner,"HomeHub Demo Saar Home","Saar");
        Home secondHome=createHome(secondHomeowner,"HomeHub Demo Manama Home","Manama");

        ServiceOffering service=new ServiceOffering();
        service.setProvider(provider);
        service.setCategory(category);
        service.setName("HomeHub Demo Standard Cleaning");
        service.setDescription("One-hour fictional cleaning appointment");
        service.setDurationMinutes(60);
        service.setPrice(10.0);
        service.setEmergencyEnabled(true);
        service.setStatus(ServiceStatus.ACTIVE);
        entityManager.persist(service);

        LocalDate today=LocalDate.now();

        //Create future working hours for booking, waitlist and reschedule testing.
        for(int day=7;day<=14;day++){
            createAvailability(provider,today.plusDays(day),
                    LocalTime.of(9,0),LocalTime.of(17,0),false);
        }

        //This period is unavailable even though it falls inside working hours.
        createAvailability(provider,today.plusDays(8),
                LocalTime.of(13,0),LocalTime.of(14,0),true);

        Booking pending=createBooking(homeowner,firstHome,service,
                today.plusDays(7),LocalTime.of(10,0),BookingStatus.PENDING);

        Booking confirmed=createBooking(homeowner,firstHome,service,
                today.plusDays(8),LocalTime.of(10,0),BookingStatus.CONFIRMED);

        createBooking(homeowner,firstHome,service,
                today.plusDays(9),LocalTime.of(10,0),BookingStatus.CANCELLED);

        createBooking(homeowner,firstHome,service,
                today.plusDays(10),LocalTime.of(10,0),BookingStatus.REJECTED);

        createBooking(homeowner,firstHome,service,
                today.minusDays(3),LocalTime.of(10,0),BookingStatus.EXPIRED);

        Booking completed=createBooking(homeowner,firstHome,service,
                today.minusDays(2),LocalTime.of(10,0),BookingStatus.COMPLETED);

        Booking awaitingReport=createBooking(homeowner,firstHome,service,
                today.minusDays(1),LocalTime.of(10,0),BookingStatus.AWAITING_REPORT);

        awaitingReport.setReportDeadline(LocalDateTime.now().minusHours(1));

        //The second homeowner waits for the occupied confirmed appointment.
        createWaitlist(secondHomeowner,secondHome,service,
                confirmed.getBookingDate(),confirmed.getStartTime(),
                WaitlistStatus.WAITING,null);

        //An expired offer lets the scheduled job demonstrate handoff.
        createWaitlist(homeowner,firstHome,service,
                today.plusDays(12),LocalTime.of(10,0),
                WaitlistStatus.OFFERED,LocalDateTime.now().minusMinutes(1));

        createWaitlist(secondHomeowner,secondHome,service,
                today.plusDays(12),LocalTime.of(10,0),
                WaitlistStatus.WAITING,null);

        BookingRescheduleRequest reschedule=new BookingRescheduleRequest();
        reschedule.setBooking(pending);
        reschedule.setRequester(homeowner);
        reschedule.setRequestedDate(today.plusDays(14));
        reschedule.setRequestedStartTime(LocalTime.of(11,0));
        reschedule.setStatus(RescheduleStatus.PENDING);
        entityManager.persist(reschedule);

        ServiceReport report=new ServiceReport();
        report.setBooking(completed);
        report.setProvider(provider);
        report.setWorkPerformed("Completed the fictional cleaning appointment");
        report.setMaterialsUsed("Demo cleaning materials");
        report.setRecommendations("Schedule regular cleaning");
        entityManager.persist(report);

        ServiceReview review=new ServiceReview();
        review.setBooking(completed);
        review.setHomeowner(homeowner);
        review.setRating(5);
        review.setComment("Demo review: appointment completed successfully");
        entityManager.persist(review);

        HomeownerAssessment assessment=new HomeownerAssessment();
        assessment.setBooking(completed);
        assessment.setHomeowner(homeowner);
        assessment.setAuthor(provider);
        assessment.setRating(5);
        assessment.setComment("Demo assessment: clear instructions and access");
        entityManager.persist(assessment);

        ServiceFavorite favorite=new ServiceFavorite();
        favorite.setHomeowner(secondHomeowner);
        favorite.setService(service);
        entityManager.persist(favorite);

        entityManager.flush();

        logger.info("Booking demo data created: serviceId={}, pendingBookingId={}, confirmedBookingId={}",
                service.getId(),pending.getId(),confirmed.getId());
    }

    //Auth must create and verify these accounts before Booking seeds its fixtures.
    private User findAccount(String address,Role expectedRole){
        if(address==null || address.isBlank()){
            throw new IllegalArgumentException("Configure all three Booking seed account emails");
        }

        User user=users.findUserByEmailAddress(address.trim().toLowerCase(Locale.ROOT));

        if(user==null || user.getRole()!=expectedRole
                || user.getStatus()!=UserStatus.ACTIVE || !user.isEmailVerified()){
            throw new IllegalArgumentException(
                    "Each seed account must already exist, be active and verified, and have the required role");
        }

        return user;
    }

    private Home createHome(User owner,String name,String city){
        Home home=new Home();
        home.setOwner(owner);
        home.setName(name);
        home.setCity(city);
        home.setAddress("Fictional address for local testing");
        home.setActive(true);
        entityManager.persist(home);
        return home;
    }

    private void createAvailability(ProviderProfile provider,LocalDate date,
                                    LocalTime start,LocalTime end,boolean timeOff){
        Availability availability=new Availability();
        availability.setProvider(provider);
        availability.setDate(date);
        availability.setStartTime(start);
        availability.setEndTime(end);
        availability.setAvailable(!timeOff);
        availability.setTimeOff(timeOff);
        entityManager.persist(availability);
    }

    private Booking createBooking(User customer,Home home,ServiceOffering service,
                                  LocalDate date,LocalTime start,BookingStatus status){
        Booking booking=new Booking();
        booking.setCustomer(customer);
        booking.setHome(home);
        booking.setService(service);
        booking.setBookingDate(date);
        booking.setStartTime(start);
        booking.setEndTime(start.plusMinutes(service.getDurationMinutes()));
        booking.setBookingType(BookingType.NORMAL);
        booking.setFinalPrice(service.getPrice());
        booking.setStatus(status);
        booking.setNote("Created by BookingDataSeed");
        booking.setLateCancellation(status==BookingStatus.CANCELLED);

        LocalDateTime appointment=LocalDateTime.of(date,start);
        LocalDateTime deadline=LocalDateTime.now().plusHours(24);
        booking.setResponseDeadline(deadline.isBefore(appointment)?deadline:appointment);

        entityManager.persist(booking);

        AuditLog audit=new AuditLog();
        audit.setUser(customer);
        audit.setAction("Seed Booking");
        audit.setEntityType("Booking");
        audit.setEntityId(booking.getId());
        audit.setDescription("Demo fixture with status "+status);
        entityManager.persist(audit);

        return booking;
    }

    private void createWaitlist(User homeowner,Home home,ServiceOffering service,
                                LocalDate date,LocalTime start,WaitlistStatus status,
                                LocalDateTime expiresAt){
        BookingWaitlist entry=new BookingWaitlist();
        entry.setHomeowner(homeowner);
        entry.setHome(home);
        entry.setService(service);
        entry.setRequestedDate(date);
        entry.setRequestedStartTime(start);
        entry.setStatus(status);
        entry.setOfferExpiresAt(expiresAt);
        entityManager.persist(entry);
    }
}