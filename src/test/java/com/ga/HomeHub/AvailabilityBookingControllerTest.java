package com.ga.HomeHub;

import com.ga.HomeHub.controller.AvailabilityController;
import com.ga.HomeHub.controller.BookingController;
import com.ga.HomeHub.exception.ApiExceptionHandler;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.model.AuditLog;
import com.ga.HomeHub.model.Availability;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.BookingType;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.service.AvailabilityService;
import com.ga.HomeHub.service.BookingService;
import com.ga.HomeHub.service.AuditLogService;
import com.ga.HomeHub.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

//Keep Phase 1 tests unchanged; these fixtures target only Phase 2 HTTP contracts and permissions.
@SpringJUnitConfig(AvailabilityBookingControllerTest.Config.class)
class AvailabilityBookingControllerTest{
    @Configuration
    @EnableMethodSecurity
    static class Config{
        @Bean
        AvailabilityService availability(){
            return mock(AvailabilityService.class);
        }

        @Bean
        BookingService bookings(){
            return mock(BookingService.class);
        }

        @Bean
        AvailabilityController availabilityController(AvailabilityService availability){
            return new AvailabilityController(availability);
        }

        @Bean
        BookingController bookingController(BookingService bookings){
            return new BookingController(bookings);
        }
    }

    @Autowired private AvailabilityService availability;
    @Autowired private BookingService bookings;
    @Autowired private AvailabilityController availabilityController;
    @Autowired private BookingController bookingController;
    private MockMvc mvc;
    private final LocalDate date=LocalDate.now().plusDays(3);

    @BeforeEach
    void setUp(){
        reset(availability,bookings);
        mvc=MockMvcBuilders.standaloneSetup(availabilityController,bookingController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @AfterEach
    void clearAuthentication(){
        SecurityContextHolder.clearContext();
    }

    @Test
    void originalAvailabilityQueryStillWorksAndDoesNotExposeLinkedAccounts() throws Exception{
        authenticate("HOMEOWNER");
        when(availability.getProviderAvailabilityByDate(11L,date,false)).thenReturn(List.of(period()));
        mvc.perform(get("/api/availability").param("providerId","11").param("date",date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].providerId").value(11))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].timeOff").value(false))
                .andExpect(jsonPath("$[0].provider").doesNotExist())
                .andExpect(jsonPath("$[0].user").doesNotExist());
    }

    @Test
    void timeOffQueryRequiresProviderRoleBeforeServiceAccess() throws Exception{
        for(String role:List.of("HOMEOWNER","ADMIN")){
            authenticate(role);
            mvc.perform(get("/api/availability").param("providerId","11").param("date",date.toString()).param("timeOff","true"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(availability);
        authenticate("PROVIDER");
        when(availability.getProviderAvailabilityByDate(11L,date,true)).thenReturn(List.of());
        mvc.perform(get("/api/availability").param("providerId","11").param("date",date.toString()).param("timeOff","true"))
                .andExpect(status().isOk());
    }

    @Test
    void editingPeriodRequiresProviderRoleAndExistingRequestValidation() throws Exception{
        authenticate("HOMEOWNER");
        mvc.perform(put("/api/availability/5").contentType("application/json").content(body()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(availability);
        authenticate("PROVIDER");
        mvc.perform(put("/api/availability/5").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(availability);
        when(availability.updateAvailability(eq(5L),any())).thenReturn(period());
        mvc.perform(put("/api/availability/5").contentType("application/json").content(body()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.providerId").value(11));
    }

    @Test
    void createAndBlockRetainCreatedStatusAndDeleteRetainsNoContent() throws Exception{
        authenticate("PROVIDER");
        when(availability.createAvailability(any())).thenReturn(period());
        when(availability.blockTimeOff(any())).thenReturn(period());
        mvc.perform(post("/api/availability").contentType("application/json").content(body()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(5));
        mvc.perform(post("/api/availability/time-off").contentType("application/json").content(body()))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/availability/5")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(availability).deleteAvailability(5L);
    }

    @Test
    void appointmentDiscoveryHasNormalDefaultAndAcceptsEmergencyType() throws Exception{
        when(bookings.getAvailableAppointmentTimes(1L,date,BookingType.NORMAL)).thenReturn(List.of(LocalTime.of(9,0)));
        mvc.perform(get("/api/booking/available-times").param("serviceId","1").param("date",date.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        verify(bookings).getAvailableAppointmentTimes(1L,date,BookingType.NORMAL);
        mvc.perform(get("/api/booking/available-times").param("serviceId","1").param("date",date.toString()).param("bookingType","EMERGENCY"))
                .andExpect(status().isOk());
        verify(bookings).getAvailableAppointmentTimes(1L,date,BookingType.EMERGENCY);
        mvc.perform(get("/api/booking/available-times").param("serviceId","1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingRequiredParametersReturnSafeConsistentBadRequest() throws Exception{
        mvc.perform(get("/api/booking/available-times").param("serviceId","1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Required parameter is missing: date"))
                .andExpect(jsonPath("$.path").value("/api/booking/available-times"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
        mvc.perform(get("/api/booking/available-times").param("date",date.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter is missing: serviceId"));
        verifyNoInteractions(bookings);
    }

    @Test
    void malformedAndMissingJsonBodiesReturnSafeBadRequest() throws Exception{
        authenticate("HOMEOWNER");
        for(String body:List.of("{\"note\":\"private-request-text\"","")){
            mvc.perform(post("/api/booking").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Request body is missing or contains invalid JSON values"))
                    .andExpect(jsonPath("$.trace").doesNotExist())
                    .andExpect(jsonPath("$.stackTrace").doesNotExist());
        }
        verifyNoInteractions(bookings);
    }

    @Test
    void invalidJsonFieldTypesAndEnumsReturnBadRequestBeforeServiceAccess() throws Exception{
        authenticate("HOMEOWNER");
        for(String body:List.of("{\"homeId\":\"private-request-value\"}","{\"bookingType\":\"PRIVATE_INVALID_ENUM\"}")){
            mvc.perform(post("/api/booking").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Request body is missing or contains invalid JSON values"))
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
        verifyNoInteractions(bookings);
    }

    @Test
    void invalidParameterTypesDoNotEchoRejectedValuesOrInternalTypes() throws Exception{
        String rejected="private-input-must-not-be-echoed";
        mvc.perform(get("/api/booking/available-times").param("serviceId",rejected).param("date",date.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: serviceId"))
                .andExpect(jsonPath("$.exception").doesNotExist());
        mvc.perform(get("/api/booking/available-times").param("serviceId","1").param("date",rejected))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: date"));
        mvc.perform(get("/api/booking/available-times").param("serviceId","1").param("date",date.toString()).param("bookingType",rejected))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: bookingType"));
        mvc.perform(get("/api/availability").param("providerId","11").param("date",date.toString()).param("timeOff",rejected))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: timeOff"));
        mvc.perform(get("/api/booking/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: bookingId"));
        verifyNoInteractions(bookings,availability);
    }

    @Test
    void parameterHandlerCorrectionPreservesBusinessConflictResponse() throws Exception{
        when(bookings.getAvailableAppointmentTimes(1L,date,BookingType.NORMAL)).thenThrow(new BookingConflictException("Service unavailable"));
        mvc.perform(get("/api/booking/available-times").param("serviceId","1").param("date",date.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Service unavailable"));
    }

    @Test
    void lateApprovalReturnsMeaningful422ForProviderAndAdmin() throws Exception{
        for(String role:List.of("PROVIDER","ADMIN")){
            authenticate(role);
            doThrow(new com.ga.HomeHub.exception.InvalidBookingStatusException(
                    "Cannot approve a booking whose appointment has already started"))
                    .when(bookings).updateBookingStatus(5L,com.ga.HomeHub.model.enums.BookingStatus.CONFIRMED);
            mvc.perform(put("/api/booking/5/status").contentType("application/json")
                            .content("{\"status\":\"CONFIRMED\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Cannot approve a booking whose appointment has already started"));
        }
    }

    @Test
    void expiredApprovalDeadlineRemainsMeaningful422() throws Exception{
        authenticate("PROVIDER");
        when(bookings.updateBookingStatus(5L,com.ga.HomeHub.model.enums.BookingStatus.CONFIRMED))
                .thenThrow(new com.ga.HomeHub.exception.InvalidBookingStatusException("Provider response deadline has passed"));
        mvc.perform(put("/api/booking/5/status").contentType("application/json")
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Provider response deadline has passed"));
    }

    @Test
    void historySanitizesAuditRecordsAndExposesOnlyKnownRecordedStatus() throws Exception{
        AuditLog event=new AuditLog();
        event.setId(7L);
        event.setAction("Update Booking status");
        event.setDescription("Booking status changed to CONFIRMED");
        event.setCreatedAt(LocalDateTime.now());
        User actor=new User();
        actor.setId(9L);
        actor.setRole(Role.PROVIDER);
        actor.setEmailAddress("private@example.test");
        event.setUser(actor);
        AuditLog legacy=new AuditLog();
        legacy.setAction("Legacy event");
        legacy.setDescription("Internal notes must not be returned");
        when(bookings.getBookingHistory(5L,PageRequest.of(0,20))).thenReturn(new PageImpl<>(List.of(event,legacy),PageRequest.of(0,20),2));
        mvc.perform(get("/api/booking/5/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[0].actorRole").value("PROVIDER"))
                .andExpect(jsonPath("$.content[0].actorId").value(9))
                .andExpect(jsonPath("$.content[0].recordedAt").exists())
                .andExpect(jsonPath("$.content[0].description").doesNotExist())
                .andExpect(jsonPath("$.content[0].user").doesNotExist())
                .andExpect(jsonPath("$.content[0].emailAddress").doesNotExist())
                .andExpect(jsonPath("$.content[1].status").doesNotExist());
    }

    @Test
    void historyDenialReturnsForbidden() throws Exception{
        when(bookings.getBookingHistory(eq(5L),any())).thenThrow(new UnauthorizedException("You can not view this booking"));
        mvc.perform(get("/api/booking/5/history")).andExpect(status().isForbidden());
    }

    @Test
    void historyRepositoryReadIsScopedToBookingTypeAndId(){
        AuditLogRepository repository=mock(AuditLogRepository.class);
        AuditLogService audit=new AuditLogService(repository);
        PageRequest page=PageRequest.of(1,2);
        audit.getBookingHistory(5L,page);
        verify(repository).findByEntityTypeAndEntityIdOrderByCreatedAtAscIdAsc("Booking",5L,page);
    }

    @Test
    void historyPreservesRequestedPageAndTotalWithoutReturningOtherPages() throws Exception{
        PageRequest page=PageRequest.of(1,1);
        AuditLog event=new AuditLog();
        event.setId(8L);
        event.setAction("Cancel Booking");
        event.setCreatedAt(LocalDateTime.now());
        when(bookings.getBookingHistory(5L,page)).thenReturn(new PageImpl<>(List.of(event),page,3));
        mvc.perform(get("/api/booking/5/history").param("page","1").param("size","1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(8))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));
        verify(bookings).getBookingHistory(5L,page);
    }

    private Availability period(){
        Availability period=new Availability();
        period.setId(5L);
        ProviderProfile provider=new ProviderProfile();
        provider.setId(11L);
        period.setProvider(provider);
        period.setDate(date);
        period.setStartTime(LocalTime.of(9,0));
        period.setEndTime(LocalTime.of(10,0));
        return period;
    }

    private String body(){
        return "{\"date\":\""+date+"\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"}";
    }

    private void authenticate(String role){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-account","unused",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
}
