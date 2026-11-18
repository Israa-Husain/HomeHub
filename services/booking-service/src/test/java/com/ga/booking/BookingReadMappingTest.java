package com.ga.booking;

import com.ga.HomeHub.booking.client.AccountDirectory;
import com.ga.HomeHub.booking.config.BookingPolicyProperties;
import com.ga.HomeHub.booking.controller.BookingController;
import com.ga.HomeHub.booking.dto.BookingResponse;
import com.ga.HomeHub.booking.exception.InformationNotFoundException;
import com.ga.HomeHub.booking.exception.UnauthorizedException;
import com.ga.HomeHub.booking.model.Booking;
import com.ga.HomeHub.booking.model.Category;
import com.ga.HomeHub.booking.model.Home;
import com.ga.HomeHub.booking.model.ProviderProfile;
import com.ga.HomeHub.booking.model.ServiceOffering;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.model.enums.BookingStatus;
import com.ga.HomeHub.booking.model.enums.BookingType;
import com.ga.HomeHub.booking.model.enums.Role;
import com.ga.HomeHub.booking.model.enums.ServiceStatus;
import com.ga.HomeHub.booking.repository.AvailabilityRepository;
import com.ga.HomeHub.booking.repository.BookingRepository;
import com.ga.HomeHub.booking.repository.BookingWaitlistRepository;
import com.ga.HomeHub.booking.repository.ServiceOfferingRepository;
import com.ga.HomeHub.booking.service.BookingService;
import com.ga.HomeHub.booking.service.CurrentUserService;
import com.ga.HomeHub.booking.validation.ProviderScheduleValidator;
import com.ga.HomeHub.booking.validation.ServiceEligibilityValidator;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Isolated read/mapping regression tests: no application context, database or scheduler.
@ExtendWith(MockitoExtension.class)
class BookingReadMappingTest {
    @Mock private AccountDirectory fixtureAccounts;

    @Mock private EntityManager fixtureEntityManager;

    @Mock private BookingWaitlistRepository fixtureWaiting;

    @Mock private AvailabilityRepository fixtureAvailability;

    @Mock private BookingRepository repository;
    @Mock private CurrentUserService current;
    @InjectMocks private BookingService service;
    private BookingController controller;
    private User homeowner;
    private User providerUser;
    private ServiceOffering offering;
    private Home home;

    @BeforeEach
    void setUp() {
        controller = new BookingController(service);
        homeowner = account(11L, Role.HOMEOWNER);
        providerUser = account(22L, Role.PROVIDER);
        ProviderProfile provider = new ProviderProfile();
        provider.setId(33L);
        provider.setUser(providerUser);
        provider.setBusinessName("Existing provider");
        offering = new ServiceOffering();
        offering.setId(44L);
        offering.setName("Existing service");
        offering.setPrice(999.0); // Must never replace an unknown historical final price.
        offering.setProvider(provider);
        home = new Home();
        home.setId(55L);
        home.setOwner(homeowner);
    }

    @Test
    void legacyMappingPreservesUnknownTypeAndPriceWithoutChangingEntity() {
        Booking legacy = booking(1L, null, null);
        BookingResponse response = service.toResponse(legacy);
        assertNull(response.bookingType());
        assertNull(response.finalPrice());
        assertNull(legacy.getBookingType());
        assertNull(legacy.getFinalPrice());
        assertEquals(BookingStatus.CANCELLED, legacy.getStatus());
        assertEquals(offering.getId(), response.serviceId());
        assertEquals(home.getId(), response.homeId());
        verifyNoInteractions(repository, current);
    }

    @Test
    void recordedTypesAndFinalPricesArePreserved() {
        for (BookingType type : List.of(BookingType.NORMAL, BookingType.EMERGENCY)) {
            BookingResponse response = service.toResponse(booking(2L, type, 15.125));
            assertEquals(type.name(), response.bookingType());
            assertEquals(15.125, response.finalPrice());
        }
    }

    @Test
    void mixedHistoricalHomeownerPageMapsEveryRowWithoutDroppingRecords() {
        PageRequest page = PageRequest.of(0, 100);
        when(current.getCurrentUser()).thenReturn(homeowner);
        when(repository.findByCustomerId(homeowner.getId(), page)).thenReturn(
                new PageImpl<>(List.of(booking(1L, null, null), booking(2L, BookingType.NORMAL, 15.0)), page, 2));
        Page<BookingResponse> response = controller.getCurrentUserBookings(null, page);
        assertEquals(2, response.getTotalElements());
        assertNull(response.getContent().get(0).bookingType());
        assertNull(response.getContent().get(0).finalPrice());
        assertEquals("NORMAL", response.getContent().get(1).bookingType());
        verify(repository).findByCustomerId(homeowner.getId(), page);
        verify(repository, never()).save(any());
    }

    @Test
    void homeownerWithNoBookingsReceivesAnEmptyPage() {
        PageRequest page = PageRequest.of(0, 100);
        when(current.getCurrentUser()).thenReturn(homeowner);
        when(repository.findByCustomerId(homeowner.getId(), page)).thenReturn(Page.empty(page));
        assertTrue(controller.getCurrentUserBookings(null, page).isEmpty());
        verify(repository, never()).save(any());
    }

    @Test
    void authorizedAdminCanReadLegacyBooking() {
        when(current.getCurrentUser()).thenReturn(account(66L, Role.ADMIN));
        when(repository.findById(1L)).thenReturn(Optional.of(booking(1L, null, null)));
        BookingResponse response = controller.getBookingById(1L);
        assertEquals(1L, response.id());
        assertNull(response.bookingType());
        assertNull(response.finalPrice());
        verify(repository, never()).save(any());
    }

    @Test
    void owningHomeownerAndAssignedProviderCanReadLegacyBooking() {
        when(repository.findById(1L)).thenReturn(Optional.of(booking(1L, null, null)));
        when(current.getCurrentUser()).thenReturn(homeowner, providerUser);
        assertNull(controller.getBookingById(1L).bookingType());
        assertNull(controller.getBookingById(1L).bookingType());
    }

    @Test
    void unrelatedHomeownerAndProviderRemainDenied() {
        when(repository.findById(1L)).thenReturn(Optional.of(booking(1L, null, null)));
        when(current.getCurrentUser()).thenReturn(account(77L, Role.HOMEOWNER), account(88L, Role.PROVIDER));
        assertThrows(UnauthorizedException.class, () -> controller.getBookingById(1L));
        assertThrows(UnauthorizedException.class, () -> controller.getBookingById(1L));
    }

    @Test
    void missingBookingRemainsNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(InformationNotFoundException.class, () -> controller.getBookingById(999L));
        verifyNoInteractions(current);
    }

    @Test
    void nullableServiceSearchNameIsExplicitlyCastInGeneratedPostgresSql() throws Exception {
        // Exercise real Hibernate/PostgreSQL SQL generation with JDBC mocks, never a database.
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try {
            var metadata = new MetadataSources(registry)
                    .addAnnotatedClass(ServiceOffering.class)
                    .addAnnotatedClass(ProviderProfile.class)
                    .addAnnotatedClass(User.class)
                    .addAnnotatedClass(Category.class)
                    .buildMetadata();
            try (var factory = metadata.buildSessionFactory()) {
                var jdbc = mock(Connection.class);
                var statement = mock(PreparedStatement.class);
                var rows = mock(ResultSet.class);
                var sqlStatements = new ArrayList<String>();
                when(jdbc.prepareStatement(anyString(), anyInt(), anyInt())).thenAnswer(invocation -> {
                    sqlStatements.add(invocation.getArgument(0));
                    return statement;
                });
                when(statement.executeQuery()).thenReturn(rows);
                // A cursor with no rows avoids hydration; SQL generation and binding are real.
                var method = ServiceOfferingRepository.class.getMethod(
                        "searchServices", String.class, Long.class, Long.class, Double.class,
                        Double.class, ServiceStatus.class,
                        Pageable.class);
                String hql = method.getAnnotation(Query.class).value();
                try (var session = factory.withOptions().connection(jdbc).openSession()) {
                    session.setDefaultReadOnly(true);
                    for (String name : new String[]{null, "Cleaning"}) {
                        session.createSelectionQuery(hql, ServiceOffering.class)
                                .setParameter("name", name)
                                .setParameter("categoryId", null)
                                .setParameter("providerId", null)
                                .setParameter("minPrice", null)
                                .setParameter("maxPrice", null)
                                .setParameter("status", ServiceStatus.ACTIVE)
                                .setReadOnly(true)
                                .getResultList();
                    }
                }
                assertEquals(2, sqlStatements.size());
                for (String sql : sqlStatements) {
                    String normalized = sql.toLowerCase(Locale.ROOT);
                    assertTrue(normalized.contains("cast(? as varchar"), sql);
                    assertTrue(normalized.replace("_", "").contains("providerstatus='approved'"), sql);
                    assertTrue(normalized.contains("status='active'"), sql);
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private User account(long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private Booking booking(long id, BookingType type, Double price) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setCustomer(homeowner);
        booking.setHome(home);
        booking.setService(offering);
        booking.setBookingDate(LocalDate.of(2026, 10, 1));
        booking.setStartTime(LocalTime.of(9, 0));
        booking.setEndTime(LocalTime.of(10, 0));
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setBookingType(type);
        booking.setFinalPrice(price);
        return booking;
    }

    @BeforeEach
    void wireCurrentProductionDependencies(){
        Mockito.lenient().when(fixtureAccounts.isActive(ArgumentMatchers.any())).thenReturn(true);
        ReflectionTestUtils.setField(service,"schedule",new ProviderScheduleValidator(fixtureAvailability,repository,fixtureWaiting));
        ReflectionTestUtils.setField(service,"policy",new BookingPolicyProperties());
        ReflectionTestUtils.setField(service,"eligibility",new ServiceEligibilityValidator(fixtureEntityManager,fixtureAccounts));
    }
}
