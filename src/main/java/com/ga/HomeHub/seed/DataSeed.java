
package com.ga.HomeHub.seed;

import com.ga.HomeHub.model.AuditLog;
import com.ga.HomeHub.model.Availability;
import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.BookingRescheduleRequest;
import com.ga.HomeHub.model.BookingWaitlist;
import com.ga.HomeHub.model.Category;
import com.ga.HomeHub.model.Home;
import com.ga.HomeHub.model.HomeownerAssessment;
import com.ga.HomeHub.model.Notification;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.model.ServiceFavorite;
import com.ga.HomeHub.model.ServiceOffering;
import com.ga.HomeHub.model.ServiceReport;
import com.ga.HomeHub.model.ServiceReview;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.model.enums.BookingType;
import com.ga.HomeHub.model.enums.CategoryStatus;
import com.ga.HomeHub.model.enums.ProviderStatus;
import com.ga.HomeHub.model.enums.RescheduleStatus;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.model.enums.ServiceStatus;
import com.ga.HomeHub.model.enums.UserStatus;
import com.ga.HomeHub.model.enums.WaitlistStatus;
import com.ga.HomeHub.repository.AuditLogRepository;
import com.ga.HomeHub.repository.AvailabilityRepository;
import com.ga.HomeHub.repository.BookingRepository;
import com.ga.HomeHub.repository.BookingRescheduleRequestRepository;
import com.ga.HomeHub.repository.BookingWaitlistRepository;
import com.ga.HomeHub.repository.CategoryRepository;
import com.ga.HomeHub.repository.HomeRepository;
import com.ga.HomeHub.repository.HomeownerAssessmentRepository;
import com.ga.HomeHub.repository.NotificationRepository;
import com.ga.HomeHub.repository.ProviderProfileRepository;
import com.ga.HomeHub.repository.ServiceFavoriteRepository;
import com.ga.HomeHub.repository.ServiceOfferingRepository;
import com.ga.HomeHub.repository.ServiceReportRepository;
import com.ga.HomeHub.repository.ServiceReviewRepository;
import com.ga.HomeHub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ga.HomeHub.repository.EmailVerificationTokenRepository;
import com.ga.HomeHub.repository.PasswordResetTokenRepository;

@Component
@RequiredArgsConstructor
public class DataSeed implements CommandLineRunner{

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    //Repositories used to create Project 3 demo workflows.
    private final ProviderProfileRepository providerRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final HomeRepository homeRepository;
    private final AvailabilityRepository availabilityRepository;
    private final BookingRepository bookingRepository;
    private final ServiceReportRepository reportRepository;
    private final ServiceReviewRepository reviewRepository;
    private final ServiceFavoriteRepository favoriteRepository;
    private final BookingWaitlistRepository waitlistRepository;
    private final BookingRescheduleRequestRepository rescheduleRepository;
    private final NotificationRepository notificationRepository;
    private final HomeownerAssessmentRepository assessmentRepository;
    private final AuditLogRepository auditRepository;

    //Check whether the demo profile is active.
    private final Environment environment;


    private final JdbcTemplate jdbc;
    private final EmailVerificationTokenRepository verificationRepository;
    private final PasswordResetTokenRepository resetRepository;
    private Clock demoClock=Clock.system(ZoneId.of("Asia/Bahrain"));
    private static final String DEMO_ACTION="Initialize Demo Data";
    private static final String DEMO_VERSION="homehub-demo-v1";
    private static final String DEMO_ADMIN="demo.admin@homehub.example";

    @Value("${MAIL_USERNAME:}")
    private String adminEmail;
    @Value("${admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args){
        boolean profile=Arrays.asList(environment.getActiveProfiles()).contains("demo");
        boolean enabled=environment.getProperty("homehub.demo.enabled",Boolean.class,false);
        if(profile || enabled){
            if(!profile || !enabled){
                throw new IllegalStateException("Demo seeding requires the demo profile and homehub.demo.enabled=true");
            }
            runDemo();
            return;
        }
        //Keep the existing operational bootstrap separate from opt-in demo fixtures.
        if(adminEmail==null || adminEmail.isBlank() || adminPassword==null || adminPassword.isBlank()){
            throw new IllegalStateException("Configure the existing bootstrap administrator credentials");
        }
        if(categoryRepository.count()==0){
            addCategory("Plumbing","Plumbing repair and installation services");
            addCategory("Electrical","Electrical repair and installation services");
            addCategory("Cleaning","Home cleaning services");
            addCategory("AC Maintenance","Air conditioning maintenance and repair");
            addCategory("Painting","Indoor and outdoor painting services");
        }
        if(!userRepository.existsByEmailAddress(adminEmail)){
            addAdmin();
        }
    }

    private void runDemo(){
        //All checks precede category/admin/demo writes. Never infer identity from a JDBC URL alone.
        String expected=environment.getRequiredProperty("homehub.demo.database");
        if(!expected.matches("(?i)[a-z][a-z0-9_]*_demo") || forbiddenDatabase(expected)){
            throw new IllegalStateException("Demo database must be an explicitly configured isolated *_Demo database");
        }
        if(!"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))){
            throw new IllegalStateException("Demo startup requires ddl-auto=validate and an existing schema");
        }
        if(!ZoneId.systemDefault().equals(ZoneId.of("Asia/Bahrain"))){
            throw new IllegalStateException("Start the demo JVM with -Duser.timezone=Asia/Bahrain");
        }
        String actual=jdbc.queryForObject("SELECT current_database()",String.class);
        if(actual==null || forbiddenDatabase(actual) || !expected.equals(actual)){
            throw new IllegalStateException("Connected database is not the approved isolated demo database");
        }
        String password=environment.getRequiredProperty("homehub.demo.password");
        if(password.isBlank() || password.length()<8){
            throw new IllegalStateException("Configure a demo password of at least 8 characters");
        }
        //JdbcTemplate shares the JPA transaction's connection. One initializer per database,
        //including across JVMs. The transaction-scoped lock is released on commit/rollback.
        jdbc.execute("SELECT pg_advisory_xact_lock(70448421001)");
        List<AuditLog> markers=auditRepository.findAll().stream()
                .filter(a->DEMO_ACTION.equals(a.getAction()) && "DemoSeed".equals(a.getEntityType())).toList();
        if(!markers.isEmpty()){
            if(markers.size()!=1 || markers.get(0).getUser()==null
                    || !DEMO_ADMIN.equals(markers.get(0).getUser().getEmailAddress())
                    || markers.get(0).getUser().getRole()!=Role.ADMIN
                    || markers.get(0).getDescription()==null
                    || !markers.get(0).getDescription().startsWith(DEMO_VERSION+";")){
                throw new IllegalStateException("Unrecognized demo marker; existing data will not be repaired");
            }
            return; //Never refresh dates, restore deleted fixtures, or reset testers' passwords/statuses.
        }
        requireEmptyDemoDatabase();
        LocalDateTime now=LocalDateTime.ofInstant(demoClock.instant(),ZoneId.of("Asia/Bahrain")).withNano(0);
        User administrator=addDemoData(now,password);
        audit(administrator,DEMO_ACTION,"DemoSeed",administrator.getId(),DEMO_VERSION+"; anchor="+now,now);
    }

    private boolean forbiddenDatabase(String name){
        return "HomeHub".equalsIgnoreCase(name) || "HomeHub_E2E".equalsIgnoreCase(name);
    }

    private void requireEmptyDemoDatabase(){
        //Do not adopt existing accounts/categories or merge an old/partial seed. Nothing is repaired.
        if(userRepository.count()!=0 || categoryRepository.count()!=0 || providerRepository.count()!=0
                || serviceRepository.count()!=0 || homeRepository.count()!=0 || availabilityRepository.count()!=0
                || bookingRepository.count()!=0 || reportRepository.count()!=0 || reviewRepository.count()!=0
                || favoriteRepository.count()!=0 || waitlistRepository.count()!=0 || rescheduleRepository.count()!=0
                || notificationRepository.count()!=0 || assessmentRepository.count()!=0 || auditRepository.count()!=0
                || verificationRepository.count()!=0 || resetRepository.count()!=0){
            throw new IllegalStateException("Demo initialization requires empty application tables; existing records are untouched");
        }
    }

    private void addCategory(String name,String description){
        category(name,description,CategoryStatus.ACTIVE);
    }

    private void addAdmin(){
        User admin=new User();
        admin.setFirstName("HomeHub");
        admin.setLastName("Admin");
        admin.setEmailAddress(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setPhoneNumber("33170000");
        admin.setRole(Role.ADMIN);
        admin.setEmailVerified(true);
        userRepository.save(admin);
    }

    //Extend the original linked fixture graph and helpers, without invoking mail/HTTP workflows.
    private User addDemoData(LocalDateTime now,String password){
        LocalDate day=now.toLocalDate();
        Map<String,User> people=new LinkedHashMap<>();
        people.put("admin",user("Fatima","Al Noor","admin",Role.ADMIN,password,UserStatus.ACTIVE,true));
        String[][] homeowners={{"amina","Amina","Ahmed"},{"yusuf","Yusuf","Ali"},{"hessa","Hessa","Salman"},
                {"mariam","Mariam","Hasan"},{"inactive","Nasser","Salem"},{"unverified","Dana","Khalil"},{"ali","Ali","Jasim"}};
        for(String[] person:homeowners){
            people.put(person[0],user(person[1],person[2],person[0],Role.HOMEOWNER,password,
                    "inactive".equals(person[0])?UserStatus.INACTIVE:UserStatus.ACTIVE,!"unverified".equals(person[0])));
        }
        User hassan=user("Hassan","Abbas","hassan",Role.PROVIDER,password,UserStatus.ACTIVE,true);
        User lulwa=user("Lulwa","Mahdi","lulwa",Role.PROVIDER,password,UserStatus.ACTIVE,true);
        people.put("hassan",hassan);people.put("lulwa",lulwa);
        ProviderProfile maintenance=provider(hassan,"Dilmun Home Maintenance",ProviderStatus.APPROVED,true);
        ProviderProfile cleaning=provider(lulwa,"Pearl Home Cleaning",ProviderStatus.APPROVED,false);
        String[] first={"Noor","Fadhel","Zahra","Jasim","Layla","Ahmed","Huda","Salman","Reem","Khalil","Nadia","Majid","Safa"};
        String[] last={"Salman","Hassan","Ali","Mahdi","Abbas","Salem","Khalil","Ahmed","Jasim","Yusuf","Nasser","Kareem","Adel"};
        for(int i=0;i<13;i++){
            String key=i==0?"noor":String.format("pending%02d",i+1);
            User applicant=user(first[i],last[i],key,Role.PROVIDER,password,UserStatus.ACTIVE,true);
            people.put(key,applicant);
            provider(applicant,"Demo "+first[i]+" Home Services",ProviderStatus.PENDING,false);
        }
        User suspended=user("Sami","Adel","suspended",Role.PROVIDER,password,UserStatus.ACTIVE,true);
        User inactive=user("Rashid","Kareem","inactive.provider",Role.PROVIDER,password,UserStatus.INACTIVE,true);
        people.put("suspended",suspended);people.put("inactive.provider",inactive);
        people.put("onboarding",user("Basma","Yusuf","onboarding",Role.PROVIDER,password,UserStatus.ACTIVE,true));
        ProviderProfile suspendedProfile=provider(suspended,"Demo Suspended Maintenance",ProviderStatus.SUSPENDED,false);
        ProviderProfile inactiveProfile=provider(inactive,"Demo Inactive Maintenance",ProviderStatus.APPROVED,false);

        Category plumbing=category("Plumbing","Domestic water fittings and leak repairs",CategoryStatus.ACTIVE);
        Category electrical=category("Electrical","Lighting and domestic electrical fittings",CategoryStatus.ACTIVE);
        Category clean=category("Cleaning","Residential cleaning in Bahrain",CategoryStatus.ACTIVE);
        Category ac=category("AC Maintenance","Domestic air-conditioning maintenance",CategoryStatus.ACTIVE);
        Category painting=category("Painting","Interior wall preparation and painting",CategoryStatus.ACTIVE);
        Category archived=category("Demo Archived Category","Inactive category for visibility checks",CategoryStatus.INACTIVE);
        ServiceOffering pipe=service(maintenance,plumbing,"Leak Repair",25.000,60,true);
        service(maintenance,plumbing,"Tap Installation",18.500,60,false);
        service(maintenance,plumbing,"Drain Clearance",35.000,60,true);
        service(maintenance,electrical,"Socket Replacement",22.750,60,false);
        service(maintenance,electrical,"Ceiling Light Installation",15.000,60,false);
        service(maintenance,ac,"AC Filter Cleaning",22.500,90,true);
        service(maintenance,painting,"Interior Paint Touch-up",45.000,120,false);
        ServiceOffering deep=service(cleaning,clean,"Deep Cleaning",60.000,120,false);
        service(cleaning,clean,"Kitchen Cleaning",35.000,90,false);
        service(cleaning,clean,"Bathroom Cleaning",20.000,60,false);
        service(cleaning,clean,"Window Cleaning",30.750,90,false);
        service(cleaning,clean,"Balcony Cleaning",18.500,60,false);
        service(cleaning,clean,"Move-out Cleaning",90.000,180,false);
        ServiceOffering retired=service(maintenance,plumbing,"Retired Tap Inspection",12.000,60,false,ServiceStatus.INACTIVE);
        service(maintenance,archived,"Archived Maintenance Visit",15.000,60,false);
        service(suspendedProfile,plumbing,"Suspended Provider Inspection",15.000,60,false);
        service(inactiveProfile,plumbing,"Inactive Provider Inspection",15.000,60,false);

        User amina=people.get("amina"),yusuf=people.get("yusuf"),ali=people.get("ali");
        Home house=home(amina,"Amina Family House","Manama",true);
        home(amina,"Amina Apartment","Muharraq",true);
        home(amina,"Amina Archived House","Riffa",false);
        Home apartment=home(yusuf,"Yusuf Apartment","Muharraq",true);
        Home villa=home(ali,"Ali Villa","Hamad Town",true);
        home(people.get("hessa"),"Hessa Apartment","Riffa",true);
        for(int offset=-16;offset<=28;offset++){
            availability(maintenance,day.plusDays(offset),LocalTime.of(9,0),LocalTime.of(18,0),true,false);
            availability(cleaning,day.plusDays(offset),LocalTime.of(9,0),LocalTime.of(18,0),true,false);
        }
        availability(maintenance,day.plusDays(2),LocalTime.of(12,0),LocalTime.of(14,0),false,true);
        availability(cleaning,day.plusDays(5),LocalTime.of(9,0),LocalTime.of(12,0),false,true);

        //14 completed appointments, all with reports, but only eight reviews and six assessments.
        for(int i=0;i<14;i++){
            boolean firstOwner=i<12;
            Booking done=booking(firstOwner?amina:yusuf,firstOwner?house:apartment,i%2==0?pipe:deep,
                    day.minusDays(i+2),LocalTime.of(10,0),BookingStatus.COMPLETED,BookingType.NORMAL,now,"C"+String.format("%02d",i+1));
            report(done);
            if(i<8) review(done,i%3+3);
            if(i<6) assessment(done,i%2==0?5:null,i%2==0?"Clear access instructions and prepared work area.":"Homeowner arranged convenient access to the property.");
        }
        Booking p1=booking(amina,house,pipe,day.plusDays(3),LocalTime.of(10,0),BookingStatus.PENDING,BookingType.NORMAL,now,"P1");
        booking(yusuf,apartment,deep,day.plusDays(3),LocalTime.of(13,0),BookingStatus.PENDING,BookingType.NORMAL,now,"P2");
        booking(amina,house,pipe,day.plusDays(4),LocalTime.of(10,0),BookingStatus.PENDING,BookingType.NORMAL,now,"P3");
        booking(ali,villa,pipe,day.plusDays(5),LocalTime.of(10,0),BookingStatus.PENDING,BookingType.NORMAL,now,"P4-overdue");
        booking(amina,house,pipe,day.minusDays(1),LocalTime.of(9,0),BookingStatus.PENDING,BookingType.NORMAL,now,"P5-past-null-deadline");
        Booking claimed=booking(amina,house,pipe,day.plusDays(6),LocalTime.of(11,0),BookingStatus.PENDING,BookingType.NORMAL,now,"P6-claimed");
        Booking c1=booking(amina,house,pipe,day.plusDays(7),LocalTime.of(10,0),BookingStatus.CONFIRMED,BookingType.NORMAL,now,"F1-rescheduled");
        Booking c2=booking(yusuf,apartment,deep,day.plusDays(8),LocalTime.of(13,0),BookingStatus.CONFIRMED,BookingType.NORMAL,now,"F2");
        booking(amina,house,pipe,day.plusDays(1),LocalTime.of(10,0),BookingStatus.CONFIRMED,BookingType.EMERGENCY,now,"E1-emergency");
        booking(ali,villa,pipe,day.minusDays(1),LocalTime.of(14,0),BookingStatus.CONFIRMED,BookingType.NORMAL,now,"R1-report-ready");
        booking(yusuf,apartment,deep,day.minusDays(1),LocalTime.of(14,0),BookingStatus.CONFIRMED,BookingType.NORMAL,now,"R2-report-ready");
        booking(amina,house,pipe,day.plusDays(9),LocalTime.of(10,0),BookingStatus.CONFIRMED,BookingType.NORMAL,now,"F3");
        booking(amina,house,pipe,day.minusDays(1),LocalTime.of(10,0),BookingStatus.AWAITING_REPORT,BookingType.NORMAL,now,"A1-report-due");
        booking(yusuf,apartment,deep,day.minusDays(3),LocalTime.of(14,0),BookingStatus.AWAITING_REPORT,BookingType.NORMAL,now,"A2-report-overdue");
        Booking cancelled=booking(yusuf,apartment,pipe,day.plusDays(10),LocalTime.of(10,0),BookingStatus.CANCELLED,BookingType.NORMAL,now,"X1-cancelled");
        booking(amina,house,deep,day.minusDays(2),LocalTime.of(14,0),BookingStatus.CANCELLED,BookingType.NORMAL,now,"X2-late-cancelled");
        booking(ali,villa,pipe,day.plusDays(11),LocalTime.of(10,0),BookingStatus.REJECTED,BookingType.NORMAL,now,"J1-rejected");
        booking(yusuf,apartment,deep,day.plusDays(12),LocalTime.of(10,0),BookingStatus.EXPIRED,BookingType.NORMAL,now,"T1-expired");

        reschedule(p1,day.plusDays(17),LocalTime.of(10,0),RescheduleStatus.PENDING,now);
        reschedule(c1,c1.getBookingDate(),c1.getStartTime(),RescheduleStatus.APPROVED,now);
        reschedule(c2,day.plusDays(18),LocalTime.of(13,0),RescheduleStatus.REJECTED,now);
        reschedule(cancelled,day.plusDays(19),LocalTime.of(10,0),RescheduleStatus.CANCELLED,now);
        favorite(amina,deep);favorite(amina,retired);favorite(yusuf,pipe);favorite(ali,deep);
        waitlist(yusuf,apartment,pipe,p1.getBookingDate(),p1.getStartTime(),WaitlistStatus.WAITING,null);
        waitlist(ali,villa,pipe,p1.getBookingDate(),p1.getStartTime(),WaitlistStatus.WAITING,null);
        waitlist(amina,house,pipe,day.plusDays(14),LocalTime.of(10,0),WaitlistStatus.OFFERED,now.plusMinutes(15));
        waitlist(yusuf,apartment,pipe,day.plusDays(14),LocalTime.of(10,0),WaitlistStatus.WAITING,null);
        waitlist(yusuf,apartment,pipe,day.plusDays(15),LocalTime.of(10,0),WaitlistStatus.EXPIRED,now.minusMinutes(1));
        waitlist(amina,house,pipe,claimed.getBookingDate(),claimed.getStartTime(),WaitlistStatus.CLAIMED,now.minusMinutes(1));
        waitlist(ali,villa,pipe,day.plusDays(16),LocalTime.of(10,0),WaitlistStatus.CANCELLED,null);
        int index=0;
        for(User person:people.values()){
            notice(person,"Demo workspace ready. All identities and addresses are fictional.",index++%2==0);
        }
        notice(hassan,"Booking #"+p1.getId()+" awaits your decision; Amina's previous assessments are available.",false);
        notice(amina,"Waitlist offer available for Leak Repair on "+day.plusDays(14)+" at 10:00 Bahrain; expires "+now.plusMinutes(15)+".",false);
        notice(people.get("admin"),"13 fictional provider applications await review.",false);
        return people.get("admin");
    }

    private User user(String first,String last,String alias,Role role,String password,UserStatus status,boolean verified){
        User user=new User();
        user.setFirstName(first);user.setLastName(last);
        user.setEmailAddress("demo."+alias+"@homehub.example");
        user.setPasswordHash(passwordEncoder.encode(password));
        //No real contact number: this is fictional demo contact information.
        user.setPhoneNumber("+973 00000000");
        user.setRole(role);user.setStatus(status);user.setEmailVerified(verified);
        return userRepository.save(user);
    }

    private ProviderProfile provider(User user,String business,ProviderStatus status,boolean emergency){
        ProviderProfile provider=new ProviderProfile();
        provider.setUser(user);provider.setBusinessName(business);
        provider.setDescription("Fictional Bahrain-based home services for Manama, Muharraq, Riffa and Hamad Town.");
        provider.setPortfolioDescription("Demo portfolio: residential inspections, clearly explained work and tidy handover. No portfolio images are uploaded.");
        provider.setProviderStatus(status);provider.setEmergencyEnabled(emergency);
        return providerRepository.save(provider);
    }

    private Category category(String name,String description,CategoryStatus status){
        //Normal bootstrap still reuses existing categories; demo initialization requires empty tables.
        for(Category existing:categoryRepository.findAll()){
            if(existing.getName().equals(name)) return existing;
        }
        Category category=new Category();
        category.setName(name);category.setDescription(description);category.setStatus(status);
        return categoryRepository.save(category);
    }

    private ServiceOffering service(ProviderProfile provider,Category category,String name,double price,int duration,boolean emergency){
        return service(provider,category,name,price,duration,emergency,ServiceStatus.ACTIVE);
    }

    private ServiceOffering service(ProviderProfile provider,Category category,String name,double price,int duration,boolean emergency,ServiceStatus status){
        ServiceOffering service=new ServiceOffering();
        service.setProvider(provider);service.setCategory(category);service.setName(name);
        service.setDescription("Demo "+name.toLowerCase()+" for Bahrain homes. Scope and access are agreed with the homeowner.");
        service.setPrice(price);service.setDurationMinutes(duration);service.setEmergencyEnabled(emergency);service.setStatus(status);
        return serviceRepository.save(service);
    }

    private Home home(User owner,String name,String city,boolean active){
        Home home=new Home();home.setOwner(owner);home.setName(name);home.setCity(city);
        home.setAddress("Fictional demo address: Building 100, Road 200, Block 300, "+city+", Bahrain");
        home.setActive(active);return homeRepository.save(home);
    }

    private void availability(ProviderProfile provider,LocalDate date,LocalTime start,LocalTime end,boolean available,boolean timeOff){
        Availability availability=new Availability();availability.setProvider(provider);availability.setDate(date);
        availability.setStartTime(start);availability.setEndTime(end);availability.setAvailable(available);availability.setTimeOff(timeOff);
        availabilityRepository.save(availability);
    }

    private Booking booking(User homeowner,Home home,ServiceOffering service,LocalDate date,LocalTime start,
                            BookingStatus status,BookingType type,LocalDateTime now,String reference){
        Booking booking=new Booking();booking.setCustomer(homeowner);booking.setHome(home);booking.setService(service);
        booking.setBookingDate(date);booking.setStartTime(start);booking.setEndTime(start.plusMinutes(service.getDurationMinutes()));
        booking.setStatus(status);booking.setBookingType(type);
        booking.setFinalPrice(BigDecimal.valueOf(service.getPrice()).multiply(new BigDecimal(type==BookingType.EMERGENCY?"1.20":"1.00")).doubleValue());
        LocalDateTime begins=LocalDateTime.of(date,start),ends=LocalDateTime.of(date,booking.getEndTime());
        LocalDateTime submitted=begins.minusHours(type==BookingType.NORMAL?48:3);
        if(submitted.isAfter(now.minusMinutes(30))) submitted=now.minusMinutes(30);
        if(reference.equals("P4-overdue") || status==BookingStatus.EXPIRED) submitted=now.minusHours(25);
        LocalDateTime deadline=submitted.plusHours(24).isBefore(begins)?submitted.plusHours(24):begins;
        booking.setResponseDeadline(reference.equals("P5-past-null-deadline")?null:deadline);
        if(status==BookingStatus.AWAITING_REPORT || status==BookingStatus.COMPLETED) booking.setReportDeadline(ends.plusHours(24));
        booking.setLateCancellation(reference.equals("X2-late-cancelled"));
        booking.setNote("Demo "+reference+" - fictional fixture; dates use Bahrain time.");
        booking=bookingRepository.save(booking);
        audit(homeowner,"Create Booking","Booking",booking.getId(),"User created booking",submitted);
        LocalDateTime decision=submitted.plusMinutes(1);
        if(List.of(BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT,BookingStatus.COMPLETED).contains(status)){
            audit(service.getProvider().getUser(),"Update Booking status","Booking",booking.getId(),"Booking status changed to CONFIRMED",decision);
        }
        if(status==BookingStatus.AWAITING_REPORT || status==BookingStatus.COMPLETED){
            audit(service.getProvider().getUser(),"Update Booking status","Booking",booking.getId(),"Booking status changed to AWAITING_REPORT",ends.plusMinutes(1));
        }
        if(status==BookingStatus.COMPLETED || status==BookingStatus.REJECTED){
            audit(service.getProvider().getUser(),"Update Booking status","Booking",booking.getId(),"Booking status changed to "+status,
                    status==BookingStatus.COMPLETED?ends.plusHours(2):decision);
        }
        if(status==BookingStatus.CANCELLED){
            audit(homeowner,"Cancel Booking","Booking",booking.getId(),"Booking cancelled",
                    booking.isLateCancellation()?begins.minusHours(2):now.minusMinutes(1));
        }
        return booking;
    }

    private void report(Booking booking){
        ServiceReport report=new ServiceReport();report.setBooking(booking);report.setProvider(booking.getService().getProvider());
        report.setWorkPerformed("Completed the agreed "+booking.getService().getName().toLowerCase()+" and checked the work with the homeowner.");
        report.setMaterialsUsed("Suitable domestic maintenance or cleaning supplies.");
        report.setRecommendations("Arrange routine checks and report any recurring issue.");reportRepository.save(report);
    }
    private void review(Booking booking,int rating){
        ServiceReview review=new ServiceReview();review.setBooking(booking);review.setHomeowner(booking.getCustomer());
        review.setRating(rating);review.setComment("Clear communication and a tidy finish. Fictional demo review.");reviewRepository.save(review);
    }
    private void assessment(Booking booking,Integer rating,String comment){
        HomeownerAssessment assessment=new HomeownerAssessment();assessment.setBooking(booking);assessment.setHomeowner(booking.getCustomer());
        assessment.setAuthor(booking.getService().getProvider());assessment.setRating(rating);assessment.setComment(comment);assessmentRepository.save(assessment);
    }
    private void favorite(User owner,ServiceOffering service){
        ServiceFavorite favorite=new ServiceFavorite();favorite.setHomeowner(owner);favorite.setService(service);favoriteRepository.save(favorite);
    }
    private void reschedule(Booking booking,LocalDate date,LocalTime start,RescheduleStatus status,LocalDateTime now){
        BookingRescheduleRequest request=new BookingRescheduleRequest();request.setBooking(booking);request.setRequester(booking.getCustomer());
        request.setRequestedDate(date);request.setRequestedStartTime(start);request.setStatus(status);rescheduleRepository.save(request);
        audit(booking.getCustomer(),"Request Reschedule","Booking",booking.getId(),"Requested "+date+" "+start,now.minusMinutes(10));
        if(status==RescheduleStatus.APPROVED || status==RescheduleStatus.REJECTED){
            audit(booking.getService().getProvider().getUser(),"Reschedule Decision","Booking",booking.getId(),
                    status==RescheduleStatus.APPROVED?"Approved":"Rejected",now.minusMinutes(5));
        }
    }
    private void waitlist(User owner,Home home,ServiceOffering service,LocalDate date,LocalTime start,WaitlistStatus status,LocalDateTime expiry){
        BookingWaitlist entry=new BookingWaitlist();entry.setHomeowner(owner);entry.setHome(home);entry.setService(service);
        entry.setRequestedDate(date);entry.setRequestedStartTime(start);entry.setStatus(status);entry.setOfferExpiresAt(expiry);waitlistRepository.save(entry);
    }
    private void notice(User recipient,String message,boolean read){
        Notification notice=new Notification();notice.setRecipient(recipient);notice.setMessage(message);notice.setRead(read);
        notice.setEventType("booking");notificationRepository.save(notice);
    }
    private void audit(User actor,String action,String type,Long id,String description,LocalDateTime at){
        AuditLog audit=new AuditLog();audit.setUser(actor);audit.setAction(action);audit.setEntityType(type);audit.setEntityId(id);
        audit.setDescription(description);audit.setCreatedAt(at);auditRepository.save(audit);
    }
}
