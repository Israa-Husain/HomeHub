package com.ga.HomeHub;

import com.ga.HomeHub.controller.*;
import com.ga.HomeHub.exception.ApiExceptionHandler;
import com.ga.HomeHub.model.*;
import com.ga.HomeHub.service.*;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(ServiceFeedbackControllerTest.Config.class)
class ServiceFeedbackControllerTest{
    @Configuration
    @EnableMethodSecurity
    static class Config{
        @Bean
        ServiceReportService reports(){ return mock(ServiceReportService.class); }
        @Bean
        ServiceReviewService reviews(){ return mock(ServiceReviewService.class); }
        @Bean
        HomeownerAssessmentService assessments(){ return mock(HomeownerAssessmentService.class); }
        @Bean
        ServiceReportController reportController(ServiceReportService reports){ return new ServiceReportController(reports); }
        @Bean
        ServiceReviewController reviewController(ServiceReviewService reviews){ return new ServiceReviewController(reviews); }
        @Bean
        HomeownerAssessmentController assessmentController(HomeownerAssessmentService assessments){ return new HomeownerAssessmentController(assessments); }
    }

    @Autowired private ServiceReportService reports;
    @Autowired private ServiceReviewService reviews;
    @Autowired private HomeownerAssessmentService assessments;
    @Autowired private ServiceReportController reportController;
    @Autowired private ServiceReviewController reviewController;
    @Autowired private HomeownerAssessmentController assessmentController;
    private MockMvc mvc;
    private Booking booking;
    private ServiceReview review;

    @BeforeEach
    void setUp(){
        reset(reports,reviews,assessments);
        mvc=MockMvcBuilders.standaloneSetup(reportController,reviewController,assessmentController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new ApiExceptionHandler()).build();
        ProviderProfile provider=new ProviderProfile();
        provider.setId(3L);
        ServiceOffering offering=new ServiceOffering();
        offering.setId(4L);
        offering.setName("Repair");
        offering.setProvider(provider);
        booking=new Booking();
        booking.setId(6L);
        booking.setService(offering);
        review=new ServiceReview();
        review.setId(7L);
        review.setBooking(booking);
        review.setRating(5);
        review.setComment("Good repair");
    }

    @AfterEach
    void clearAuthentication(){ SecurityContextHolder.clearContext(); }

    @Test
    void homeownerReviewPageIncludesBookingLinkAndPreservesPagination() throws Exception{
        authenticate("HOMEOWNER");
        PageRequest page=PageRequest.of(1,1);
        when(reviews.getMyReviews(page)).thenReturn(new PageImpl<>(List.of(review),page,3));
        mvc.perform(get("/api/reviews/mine").param("page","1").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].bookingId").value(6))
                .andExpect(jsonPath("$.content[0].serviceName").value("Repair"))
                .andExpect(jsonPath("$.content[0].homeowner").doesNotExist());
    }

    @Test
    void publicProviderReviewsExcludePrivateBookingAndCustomerInformation() throws Exception{
        when(reviews.providerReviews(3L)).thenReturn(List.of(review));
        mvc.perform(get("/api/reviews/provider/3")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].comment").value("Good repair"))
                .andExpect(jsonPath("$[0].providerId").value(3))
                .andExpect(jsonPath("$[0].bookingId").doesNotExist())
                .andExpect(jsonPath("$[0].booking").doesNotExist())
                .andExpect(jsonPath("$[0].homeowner").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].home").doesNotExist());
    }

    @Test
    void ownReviewReadsAndWritesRemainHomeownerOnly() throws Exception{
        for(String role:List.of("PROVIDER","ADMIN")){
            authenticate(role);
            mvc.perform(get("/api/reviews/mine")).andExpect(status().isForbidden());
            mvc.perform(post("/api/reviews").contentType("application/json").content("{\"bookingId\":6,\"rating\":5}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(reviews);
    }

    @Test
    void reportReadsSerializeOnlyReportFieldsAndSubmissionUsesExistingContract() throws Exception{
        ServiceReport report=new ServiceReport();
        report.setId(8L);
        report.setBooking(booking);
        report.setWorkPerformed("Repaired pipe");
        when(reports.getServiceReport(6L)).thenReturn(report);
        mvc.perform(get("/api/reports/booking/6")).andExpect(status().isOk())
                .andExpect(jsonPath("$.workPerformed").value("Repaired pipe"))
                .andExpect(jsonPath("$.bookingId").value(6))
                .andExpect(jsonPath("$.booking").doesNotExist())
                .andExpect(jsonPath("$.provider").doesNotExist());
        authenticate("PROVIDER");
        when(reports.submitServiceReport(6L,"Repaired pipe",null,null)).thenReturn(report);
        mvc.perform(post("/api/reports/booking/6").contentType("application/json").content("{\"workDone\":\"Repaired pipe\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(8));
    }

    @Test
    void reportsAndAssessmentsKeepProviderOnlySubmissionAndAssessmentReads() throws Exception{
        for(String role:List.of("HOMEOWNER","ADMIN")){
            authenticate(role);
            mvc.perform(post("/api/reports/booking/6").contentType("application/json").content("{\"workDone\":\"Work\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/assessments/booking/6")).andExpect(status().isForbidden());
            mvc.perform(post("/api/assessments/booking/6").contentType("application/json").content("{\"rating\":5}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(reports,assessments);
    }

    @Test
    void feedbackLengthValidationMatchesExistingStorageLimits() throws Exception{
        String longText="x".repeat(3001);
        authenticate("PROVIDER");
        mvc.perform(post("/api/reports/booking/6").contentType("application/json").content("{\"workDone\":\""+longText+"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/reports/booking/6").contentType("application/json").content("{\"workDone\":\"Work\",\"materialsUsed\":\""+longText+"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/reports/booking/6").contentType("application/json").content("{\"workDone\":\"Work\",\"recommendation\":\""+longText+"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/assessments/booking/6").contentType("application/json").content("{\"comment\":\""+longText+"\"}"))
                .andExpect(status().isBadRequest());
        authenticate("HOMEOWNER");
        mvc.perform(post("/api/reviews").contentType("application/json").content("{\"bookingId\":6,\"rating\":5,\"comment\":\""+longText+"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reports,reviews,assessments);
    }

    @Test
    void assessmentResponseDoesNotExposeHomeownerAuthorOrBooking() throws Exception{
        authenticate("PROVIDER");
        HomeownerAssessment assessment=new HomeownerAssessment();
        assessment.setId(9L);
        assessment.setComment("Clear instructions");
        when(assessments.getHomeownerAssessments(6L)).thenReturn(List.of(assessment));
        mvc.perform(get("/api/assessments/booking/6")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].comment").value("Clear instructions"))
                .andExpect(jsonPath("$[0].homeowner").doesNotExist())
                .andExpect(jsonPath("$[0].author").doesNotExist())
                .andExpect(jsonPath("$[0].booking").doesNotExist());
    }

    private void authenticate(String role){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-account","unused",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
}
