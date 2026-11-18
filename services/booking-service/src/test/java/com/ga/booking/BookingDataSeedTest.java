package com.ga.booking;

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
import com.ga.HomeHub.booking.model.enums.ProviderStatus;
import com.ga.HomeHub.booking.model.enums.Role;
import com.ga.HomeHub.booking.model.enums.UserStatus;
import com.ga.HomeHub.booking.model.enums.WaitlistStatus;
import com.ga.HomeHub.booking.repository.ProviderProfileRepository;
import com.ga.HomeHub.booking.repository.UserRepository;
import com.ga.HomeHub.booking.seed.BookingDataSeed;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingDataSeedTest{
    private final UserRepository users=mock(UserRepository.class);
    private final ProviderProfileRepository providers=mock(ProviderProfileRepository.class);
    private final EntityManager em=mock(EntityManager.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final TypedQuery<Long> count=mock(TypedQuery.class);
    private final List<Object> rows=new ArrayList<>();
    private final AtomicBoolean initialized=new AtomicBoolean();
    private final AtomicLong ids=new AtomicLong(100);
    private User homeowner;
    private User second;
    private User providerUser;
    private BookingDataSeed seed;

    @BeforeEach
    void setUp(){
        homeowner=account(1L,Role.HOMEOWNER);
        second=account(3L,Role.HOMEOWNER);
        providerUser=account(2L,Role.PROVIDER);
        when(users.findUserByEmailAddress("user1@homehub.example")).thenReturn(homeowner);
        when(users.findUserByEmailAddress("user3@homehub.example")).thenReturn(second);
        when(users.findUserByEmailAddress("user2@homehub.example")).thenReturn(providerUser);
        when(em.createQuery(anyString(),eq(Long.class))).thenReturn(count);
        when(count.setParameter(anyString(),any())).thenReturn(count);
        when(count.getSingleResult()).thenAnswer(call->initialized.get()?1L:0L);
        doAnswer(call->{
            Object row=call.getArgument(0);
            BeanWrapperImpl bean=new BeanWrapperImpl(row);
            assertNull(bean.getPropertyValue("id"));
            bean.setPropertyValue("id",ids.incrementAndGet());
            rows.add(row);
            if(row instanceof Category){initialized.set(true);}
            return null;
        }).when(em).persist(any());
        seed=new BookingDataSeed(users,providers,em,jdbc);
        ReflectionTestUtils.setField(seed,"homeownerEmail","user1@homehub.example");
        ReflectionTestUtils.setField(seed,"secondHomeownerEmail","user3@homehub.example");
        ReflectionTestUtils.setField(seed,"providerEmail","user2@homehub.example");
    }

    @Test
    void seedRequiresExplicitOptIn(){
        ConditionalOnProperty condition=BookingDataSeed.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals(List.of("homehub.seed.enabled"),List.of(condition.name()));
        assertEquals("true",condition.havingValue());
        assertFalse(condition.matchIfMissing());
    }

    @Test
    void existingActiveVerifiedAccountsAreRequiredBeforeSeedWrites(){
        for(UserStatus status:List.of(UserStatus.INACTIVE)){
            homeowner.setStatus(status);
            assertThrows(IllegalArgumentException.class,()->seed.run());
            assertTrue(rows.isEmpty());
        }
        homeowner.setStatus(UserStatus.ACTIVE);
        homeowner.setEmailVerified(false);
        assertThrows(IllegalArgumentException.class,()->seed.run());
        homeowner.setEmailVerified(true);
        homeowner.setRole(Role.PROVIDER);
        assertThrows(IllegalArgumentException.class,()->seed.run());
        homeowner.setRole(Role.HOMEOWNER);
        when(users.findUserByEmailAddress("user1@homehub.example")).thenReturn(null);
        assertThrows(IllegalArgumentException.class,()->seed.run());
        assertTrue(rows.isEmpty());
        when(users.findUserByEmailAddress("user1@homehub.example")).thenReturn(homeowner);
        ReflectionTestUtils.setField(seed,"homeownerEmail","");
        assertThrows(IllegalArgumentException.class,()->seed.run());
        assertTrue(rows.isEmpty());
    }

    @Test
    void duplicateHomeownersAndUnapprovedExistingProvidersAreRejected(){
        when(users.findUserByEmailAddress("user3@homehub.example")).thenReturn(homeowner);
        assertThrows(IllegalArgumentException.class,()->seed.run());
        when(users.findUserByEmailAddress("user3@homehub.example")).thenReturn(second);
        ProviderProfile pending=new ProviderProfile();
        pending.setProviderStatus(ProviderStatus.PENDING);
        when(providers.findByUserId(2L)).thenReturn(Optional.of(pending));
        assertThrows(IllegalArgumentException.class,()->seed.run());
        assertTrue(rows.isEmpty());
    }

    @Test
    void initializationLocksBeforeReadingOrInserting(){
        seed.run();
        var order=inOrder(jdbc,em,count,users);
        order.verify(jdbc).execute("select pg_advisory_xact_lock(70448423003)");
        order.verify(em).createQuery("select count(c) from Category c where c.name=:name",Long.class);
        order.verify(count).setParameter("name","HomeHub Demo Cleaning");
        order.verify(count).getSingleResult();
        order.verify(users).findUserByEmailAddress("user1@homehub.example");
        order.verify(em).persist(any(ProviderProfile.class));
        verify(em).flush();
    }

    @Test
    void relationshipsOwnersUniqueAssociationsAndRequiredContentAreConsistent(){
        seed.run();
        assertEquals(2,all(Home.class).size());
        assertEquals(1,all(ServiceOffering.class).size());
        assertEquals(1,all(ProviderProfile.class).size());
        for(Booking booking:all(Booking.class)){
            assertSame(booking.getCustomer(),booking.getHome().getOwner());
            assertTrue(booking.getHome().isActive());
            assertTrue(List.of(homeowner,second).contains(booking.getCustomer()));
            assertTrue(all(ServiceOffering.class).contains(booking.getService()));
            assertTrue(booking.getNote().length()<=255);
        }
        Set<Long> reports=new HashSet<>(),reviews=new HashSet<>(),assessments=new HashSet<>();
        for(ServiceReport report:all(ServiceReport.class)){
            assertTrue(reports.add(report.getBooking().getId()));
            assertSame(report.getBooking().getService().getProvider(),report.getProvider());
            assertFalse(report.getWorkPerformed().isBlank());
            assertTrue(report.getWorkPerformed().length()<=3000);
        }
        for(ServiceReview review:all(ServiceReview.class)){
            assertTrue(reviews.add(review.getBooking().getId()));
            assertSame(review.getBooking().getCustomer(),review.getHomeowner());
            assertEquals(BookingStatus.COMPLETED,review.getBooking().getStatus());
            assertTrue(review.getRating()>=1&&review.getRating()<=5);
        }
        for(HomeownerAssessment assessment:all(HomeownerAssessment.class)){
            assertTrue(assessments.add(assessment.getBooking().getId()));
            assertSame(assessment.getBooking().getCustomer(),assessment.getHomeowner());
            assertSame(assessment.getBooking().getService().getProvider(),assessment.getAuthor());
            assertEquals(BookingStatus.COMPLETED,assessment.getBooking().getStatus());
            assertTrue(assessment.getRating()!=null||!assessment.getComment().isBlank());
        }
        assertSame(second,all(ServiceFavorite.class).get(0).getHomeowner());
        Set<String> favoritePairs=new HashSet<>();
        for(ServiceFavorite favorite:all(ServiceFavorite.class)){
            assertTrue(favoritePairs.add(favorite.getHomeowner().getId()+":"+favorite.getService().getId()));
        }
        for(ProviderProfile provider:all(ProviderProfile.class)){
            assertEquals(Role.PROVIDER,provider.getUser().getRole());
            assertFalse(provider.getPortfolioDescription().isBlank());
            assertTrue(provider.getPortfolioDescription().length()<=3000);
        }
    }

    @Test
    void allBookingStatesAndNormalPricesAreAvailable(){
        seed.run();
        assertEquals(7,all(Booking.class).size());
        assertEquals(EnumSet.allOf(BookingStatus.class),all(Booking.class).stream().map(Booking::getStatus).collect(Collectors.toSet()));
        for(Booking booking:all(Booking.class)){
            assertEquals(BookingType.NORMAL,booking.getBookingType());
            assertEquals(booking.getService().getPrice(),booking.getFinalPrice());
            assertEquals(booking.getStartTime().plusMinutes(booking.getService().getDurationMinutes()),booking.getEndTime());
            assertFalse(booking.getResponseDeadline().isAfter(LocalDateTime.of(booking.getBookingDate(),booking.getStartTime())));
            if(booking.getStatus()==BookingStatus.PENDING||booking.getStatus()==BookingStatus.CONFIRMED){
                assertTrue(LocalDateTime.of(booking.getBookingDate(),booking.getStartTime()).isAfter(LocalDateTime.now().plusHours(24)));
            }
            if(booking.getStatus()==BookingStatus.AWAITING_REPORT){assertTrue(booking.getReportDeadline().isBefore(LocalDateTime.now()));}
        }
        assertEquals(1,all(ServiceReport.class).size());
        assertEquals(1,all(ServiceReview.class).size());
        assertEquals(1,all(HomeownerAssessment.class).size());
    }

    @Test
    void futureBookingsDoNotOverlapEachOtherOrTimeOff(){
        seed.run();
        List<BookingStatus> occupied=List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT);
        for(Booking booking:all(Booking.class)){
            if(booking.getBookingDate().isBefore(LocalDate.now())){continue;}
            assertTrue(all(Availability.class).stream().anyMatch(a->a.getProvider()==booking.getService().getProvider()
                    &&a.getDate().equals(booking.getBookingDate())&&a.contains(booking.getStartTime(),booking.getEndTime())));
            if(!occupied.contains(booking.getStatus())){continue;}
            assertFalse(all(Availability.class).stream().anyMatch(a->a.isTimeOff()
                    &&a.getProvider()==booking.getService().getProvider()&&a.getDate().equals(booking.getBookingDate())
                    &&overlaps(booking.getStartTime(),booking.getEndTime(),a.getStartTime(),a.getEndTime())));
            assertFalse(all(Booking.class).stream().anyMatch(other->other!=booking&&occupied.contains(other.getStatus())
                    &&other.getService().getProvider()==booking.getService().getProvider()
                    &&other.getBookingDate().equals(booking.getBookingDate())
                    &&overlaps(booking.getStartTime(),booking.getEndTime(),other.getStartTime(),other.getEndTime())));
        }
    }

    @Test
    void waitlistAndRescheduleRelationshipsSupportRealActions(){
        seed.run();
        assertEquals(3,all(BookingWaitlist.class).size());
        Set<String> activePairs=new HashSet<>();
        for(BookingWaitlist entry:all(BookingWaitlist.class)){
            assertSame(entry.getHomeowner(),entry.getHome().getOwner());
            assertTrue(activePairs.add(entry.getHomeowner().getId()+":"+entry.getHome().getId()+":"+entry.getService().getId()+":"+entry.getRequestedDate()+":"+entry.getRequestedStartTime()));
            if(entry.getStatus()==WaitlistStatus.OFFERED){assertTrue(entry.getOfferExpiresAt().isBefore(LocalDateTime.now()));}
        }
        assertEquals(1,all(BookingRescheduleRequest.class).size());
        for(BookingRescheduleRequest request:all(BookingRescheduleRequest.class)){
            assertSame(request.getRequester(),request.getBooking().getCustomer());
            assertTrue(LocalDateTime.of(request.getRequestedDate(),request.getRequestedStartTime()).isAfter(LocalDateTime.now().plusHours(24)));
            assertTrue(all(Availability.class).stream().anyMatch(a->a.getProvider()==request.getBooking().getService().getProvider()
                    &&a.getDate().equals(request.getRequestedDate())
                    &&a.contains(request.getRequestedStartTime(),request.getRequestedStartTime().plusMinutes(request.getBooking().getService().getDurationMinutes()))));
        }
    }

    @Test
    void seedAuditReferencesExistingBookingsWithoutFutureDecisions(){
        LocalDateTime before=LocalDateTime.now();
        seed.run();
        LocalDateTime after=LocalDateTime.now();
        assertEquals(7,all(AuditLog.class).size());
        for(AuditLog event:all(AuditLog.class)){
            assertEquals("Seed Booking",event.getAction());
            assertEquals("Booking",event.getEntityType());
            assertFalse(event.getCreatedAt().isBefore(before));
            assertFalse(event.getCreatedAt().isAfter(after));
            assertTrue(event.getDescription().length()<=255);
            assertTrue(all(Booking.class).stream().anyMatch(b->b.getId().equals(event.getEntityId())));
        }
    }

    @Test
    void requiredEntityFieldsAndTextValuesFitDeclaredMappings()throws Exception{
        seed.run();
        for(Object row:rows){
            for(var field:row.getClass().getDeclaredFields()){
                field.setAccessible(true);
                Object value=field.get(row);
                Column column=field.getAnnotation(Column.class);
                if(column!=null&&!column.nullable()){assertNotNull(value,row.getClass().getSimpleName()+"."+field.getName());}
                if(value instanceof String text){assertTrue(text.length()<=(column==null?255:column.length()),field.getName());}
                var many=field.getAnnotation(jakarta.persistence.ManyToOne.class);
                var one=field.getAnnotation(jakarta.persistence.OneToOne.class);
                if((many!=null&&!many.optional())||(one!=null&&!one.optional())){
                    assertNotNull(value,field.getName());
                    assertTrue(rows.contains(value)||List.of(homeowner,second,providerUser).contains(value));
                }
            }
        }
        assertTrue(all(ServiceOffering.class).stream().allMatch(s->s.getPrice()>0&&s.getDurationMinutes()>0&&s.getDurationMinutes()<1440));
    }

    @Test
    void lateNightSeedKeepsFutureRequestsBeyondNotice(){
        LocalDate date=LocalDate.of(2035,1,20);
        LocalDateTime late=date.atTime(23,59);
        try(var dates=mockStatic(LocalDate.class,CALLS_REAL_METHODS);
            var times=mockStatic(LocalDateTime.class,CALLS_REAL_METHODS)){
            dates.when(LocalDate::now).thenReturn(date);
            times.when(LocalDateTime::now).thenReturn(late);
            seed.run();
            for(Booking booking:all(Booking.class)){
                if(booking.getStatus()==BookingStatus.PENDING||booking.getStatus()==BookingStatus.CONFIRMED){
                    assertTrue(LocalDateTime.of(booking.getBookingDate(),booking.getStartTime()).isAfter(late.plusHours(24)));
                    assertTrue(booking.getResponseDeadline().isAfter(late));
                }
            }
            assertTrue(all(BookingRescheduleRequest.class).stream().allMatch(r->
                    LocalDateTime.of(r.getRequestedDate(),r.getRequestedStartTime()).isAfter(late.plusHours(24))));
        }
    }

    @Test
    void restartDoesNotDuplicateMoveDatesOrRepairManuallyChangedFixtures(){
        seed.run();
        List<LocalDate> dates=all(Booking.class).stream().map(Booking::getBookingDate).toList();
        homeowner.setStatus(UserStatus.INACTIVE);
        rows.removeIf(ServiceFavorite.class::isInstance);
        int size=rows.size();
        seed.run();
        assertEquals(size,rows.size());
        assertEquals(dates,all(Booking.class).stream().map(Booking::getBookingDate).toList());
        assertEquals(UserStatus.INACTIVE,homeowner.getStatus());
        assertTrue(all(ServiceFavorite.class).isEmpty());
    }

    @Test
    void failedPersistenceRollsBackAtTransactionalBoundary()throws Exception{
        PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
        SimpleTransactionStatus status=new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        doThrow(new IllegalStateException("Fixture persistence failure")).when(em).flush();
        ProxyFactory factory=new ProxyFactory(seed);
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        CommandLineRunner transactional=(CommandLineRunner)factory.getProxy();
        assertThrows(IllegalStateException.class,()->transactional.run());
        verify(manager).rollback(status);
        verify(manager,never()).commit(any());
    }

    private User account(long id,Role role){
        User user=new User();
        user.setId(id);user.setRole(role);user.setStatus(UserStatus.ACTIVE);user.setEmailVerified(true);
        user.setFirstName("JUnit");user.setLastName("Fixture");user.setEmailAddress("user"+id+"@homehub.example");
        return user;
    }
    private <T> List<T> all(Class<T> type){return rows.stream().filter(type::isInstance).map(type::cast).toList();}
    private boolean overlaps(LocalTime start,LocalTime end,LocalTime other,LocalTime otherEnd){
        return start.isBefore(otherEnd)&&end.isAfter(other);
    }
}
