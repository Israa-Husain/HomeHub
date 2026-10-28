package com.ga.HomeHub;

import com.ga.HomeHub.controller.BookingRescheduleController;
import com.ga.HomeHub.controller.BookingWaitlistController;
import com.ga.HomeHub.exception.ApiExceptionHandler;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.*;
import com.ga.HomeHub.model.enums.*;
import com.ga.HomeHub.service.BookingRescheduleService;
import com.ga.HomeHub.service.BookingService;
import com.ga.HomeHub.service.BookingWaitlistService;
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
import com.jayway.jsonpath.JsonPath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

//Phase 3 HTTP/security fixtures remain separate from service and database behavior tests.
@SpringJUnitConfig(RescheduleWaitlistControllerTest.Config.class)
class RescheduleWaitlistControllerTest{
    @Configuration
    @EnableMethodSecurity
    static class Config{
        @Bean
        BookingRescheduleService reschedules(){
            return mock(BookingRescheduleService.class);
        }

        @Bean
        BookingWaitlistService waitlist(){
            return mock(BookingWaitlistService.class);
        }

        @Bean
        BookingService bookings(){
            return mock(BookingService.class);
        }

        @Bean
        BookingRescheduleController rescheduleController(BookingRescheduleService reschedules){
            return new BookingRescheduleController(reschedules);
        }

        @Bean
        BookingWaitlistController waitlistController(BookingWaitlistService waitlist,BookingService bookings){
            return new BookingWaitlistController(waitlist,bookings);
        }
    }

    @Autowired private BookingRescheduleService reschedules;
    @Autowired private BookingWaitlistService waitlist;
    @Autowired private BookingService bookings;
    @Autowired private BookingRescheduleController rescheduleController;
    @Autowired private BookingWaitlistController waitlistController;
    private MockMvc mvc;
    private BookingRescheduleRequest request;
    private BookingWaitlist entry;

    @BeforeEach
    void setUp(){
        reset(reschedules,waitlist,bookings);
        mvc=MockMvcBuilders.standaloneSetup(rescheduleController,waitlistController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new ApiExceptionHandler()).build();
        ProviderProfile provider=new ProviderProfile();
        provider.setId(3L);
        provider.setBusinessName("Repair business");
        ServiceOffering service=new ServiceOffering();
        service.setId(4L);
        service.setName("Repair");
        service.setProvider(provider);
        Home home=new Home();
        home.setId(5L);
        Booking booking=new Booking();
        booking.setId(6L);
        booking.setService(service);
        booking.setHome(home);
        booking.setBookingDate(LocalDate.now().plusDays(3));
        booking.setStartTime(LocalTime.of(10,0));
        booking.setEndTime(LocalTime.of(11,0));
        request=new BookingRescheduleRequest();
        request.setId(7L);
        request.setBooking(booking);
        request.setRequestedDate(LocalDate.now().plusDays(4));
        request.setRequestedStartTime(LocalTime.of(11,0));
        request.setCreatedAt(LocalDateTime.now());
        entry=new BookingWaitlist();
        entry.setId(8L);
        entry.setService(service);
        entry.setHome(home);
        entry.setRequestedDate(booking.getBookingDate());
        entry.setRequestedStartTime(booking.getStartTime());
    }

    @AfterEach
    void clearAuthentication(){
        SecurityContextHolder.clearContext();
    }

    @Test
    void rescheduleListPreservesPageFiltersAndSafeExistingFields() throws Exception{
        authenticate("HOMEOWNER");
        PageRequest page=PageRequest.of(1,1);
        when(reschedules.getCurrentUserRequests(RescheduleStatus.PENDING,6L,page))
                .thenReturn(new PageImpl<>(List.of(request),page,3));
        mvc.perform(get("/api/reschedules").param("status","PENDING").param("bookingId","6").param("page","1").param("size","1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].bookingId").value(6))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].date").value(request.getRequestedDate().toString()))
                .andExpect(jsonPath("$.content[0].start").value("11:00"))
                .andExpect(jsonPath("$.content[0].providerName").value("Repair business"))
                .andExpect(jsonPath("$.content[0].booking").doesNotExist())
                .andExpect(jsonPath("$.content[0].requester").doesNotExist());
        verify(reschedules).getCurrentUserRequests(RescheduleStatus.PENDING,6L,page);
    }

    @Test
    void detailReadIsAvailableToBothParticipantRolesButNotAdmin() throws Exception{
        when(reschedules.getRequestById(7L)).thenReturn(request);
        for(String role:List.of("HOMEOWNER","PROVIDER")){
            authenticate(role);
            mvc.perform(get("/api/reschedules/7")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.serviceId").value(4))
                    .andExpect(jsonPath("$.bookingDate").exists());
        }
        reset(reschedules);
        authenticate("ADMIN");
        mvc.perform(get("/api/reschedules/7")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reschedules")).andExpect(status().isForbidden());
        verifyNoInteractions(reschedules);
    }

    @Test
    void unrelatedParticipantDenialRemainsForbidden() throws Exception{
        authenticate("HOMEOWNER");
        when(reschedules.getRequestById(7L)).thenThrow(new UnauthorizedException("You can not view this reschedule request"));
        mvc.perform(get("/api/reschedules/7")).andExpect(status().isForbidden());
    }

    @Test
    void requestAndDecisionRetainRoleRestrictionsAndRequireExplicitDecision() throws Exception{
        authenticate("HOMEOWNER");
        mvc.perform(put("/api/reschedules/7/decision").contentType("application/json").content("{\"approve\":true}"))
                .andExpect(status().isForbidden());
        authenticate("PROVIDER");
        mvc.perform(post("/api/reschedules/booking/6").contentType("application/json")
                .content("{\"date\":\""+request.getRequestedDate()+"\",\"start\":\"11:00\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/reschedules/7/decision").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reschedules);
        when(reschedules.decide(7L,false)).thenReturn(request);
        mvc.perform(put("/api/reschedules/7/decision").contentType("application/json").content("{\"approve\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookingId").value(6));
        verify(reschedules).decide(7L,false);
    }

    @Test
    void homeownerCanSubmitExistingRescheduleAndWaitlistRequestBodies() throws Exception{
        authenticate("HOMEOWNER");
        when(reschedules.request(6L,request.getRequestedDate(),LocalTime.of(11,0))).thenReturn(request);
        mvc.perform(post("/api/reschedules/booking/6").contentType("application/json")
                .content("{\"date\":\""+request.getRequestedDate()+"\",\"start\":\"11:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7));
        when(waitlist.joinWaitlist(4L,5L,entry.getRequestedDate(),LocalTime.of(10,0))).thenReturn(entry);
        mvc.perform(post("/api/waitlist").contentType("application/json")
                .content("{\"serviceId\":4,\"homeId\":5,\"date\":\""+entry.getRequestedDate()+"\",\"start\":\"10:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(8));
    }

    @Test
    void invalidRescheduleFilterUsesCorrectedBadRequestHandler() throws Exception{
        authenticate("PROVIDER");
        mvc.perform(get("/api/reschedules").param("status","not-a-status"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: status"));
        verifyNoInteractions(reschedules);
    }

    @Test
    void waitlistResponseIncludesExactPersistedExpiryAndHandlesNullForWaitingEntry() throws Exception{
        authenticate("HOMEOWNER");
        LocalDateTime expiry=LocalDateTime.now().plusMinutes(15).withNano(0);
        entry.setOfferExpiresAt(expiry);
        entry.setStatus(WaitlistStatus.OFFERED);
        when(waitlist.getMyWaitlist()).thenReturn(List.of(entry));
        var result=mvc.perform(get("/api/waitlist")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(8))
                .andExpect(jsonPath("$[0].serviceId").value(4))
                .andExpect(jsonPath("$[0].homeId").value(5))
                .andExpect(jsonPath("$[0].status").value("OFFERED"))
                .andExpect(jsonPath("$[0].homeowner").doesNotExist()).andReturn();
        String returnedExpiry=JsonPath.read(result.getResponse().getContentAsString(),"$[0].offerExpiresAt");
        assertEquals(expiry,LocalDateTime.parse(returnedExpiry));
        entry.setStatus(WaitlistStatus.WAITING);
        entry.setOfferExpiresAt(null);
        mvc.perform(get("/api/waitlist")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("WAITING"))
                .andExpect(jsonPath("$[0].offerExpiresAt").doesNotExist());
    }

    @Test
    void waitlistExistingOperationsRemainHomeownerOnly() throws Exception{
        for(String role:List.of("PROVIDER","ADMIN")){
            authenticate(role);
            mvc.perform(get("/api/waitlist")).andExpect(status().isForbidden());
            mvc.perform(post("/api/waitlist/8/claim")).andExpect(status().isForbidden());
            mvc.perform(delete("/api/waitlist/8")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(waitlist,bookings);
    }

    private void authenticate(String role){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-account","unused",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
}
