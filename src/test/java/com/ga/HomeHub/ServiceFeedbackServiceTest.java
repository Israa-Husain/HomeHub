package com.ga.HomeHub;

import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.*;
import com.ga.HomeHub.model.enums.*;
import com.ga.HomeHub.repository.*;
import com.ga.HomeHub.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

//Reports, reviews, and private assessments share the same post-service booking fixtures.
@ExtendWith(MockitoExtension.class)
class ServiceFeedbackServiceTest{
    @Mock private BookingRepository bookings;
    @Mock private CurrentUserService current;
    @Mock private ServiceReportRepository reports;
    @Mock private ServiceReviewRepository reviews;
    @Mock private HomeownerAssessmentRepository assessments;
    @InjectMocks private ServiceReportService reportService;
    @InjectMocks private ServiceReviewService reviewService;
    @InjectMocks private HomeownerAssessmentService assessmentService;
    private User homeowner;
    private User providerUser;
    private Booking booking;

    @BeforeEach
    void setUp(){
        homeowner=new User();
        homeowner.setId(1L);
        providerUser=new User();
        providerUser.setId(2L);
        providerUser.setRole(Role.PROVIDER);
        ProviderProfile provider=new ProviderProfile();
        provider.setId(3L);
        provider.setUser(providerUser);
        ServiceOffering offering=new ServiceOffering();
        offering.setId(4L);
        offering.setProvider(provider);
        booking=new Booking();
        booking.setId(6L);
        booking.setCustomer(homeowner);
        booking.setService(offering);
        booking.setBookingDate(LocalDate.now().minusDays(1));
        booking.setEndTime(LocalTime.of(11,0));
        booking.setStatus(BookingStatus.COMPLETED);
    }

    @Test
    void reportReadAllowsBookingParticipantsWithoutAnAdminOverride(){
        ServiceReport report=new ServiceReport();
        report.setBooking(booking);
        when(reports.findByBooking_Id(6L)).thenReturn(Optional.of(report));
        for(User user:List.of(homeowner,providerUser)){
            when(current.getCurrentUser()).thenReturn(user);
            assertSame(report,reportService.getServiceReport(6L));
        }
        User unrelated=new User();
        unrelated.setId(99L);
        for(Role role:Role.values()){
            unrelated.setRole(role);
            when(current.getCurrentUser()).thenReturn(unrelated);
            assertThrows(UnauthorizedException.class,()->reportService.getServiceReport(6L));
        }
    }

    @Test
    void assignedProviderCanSubmitReportOnlyForReadyEndedAppointment(){
        providerBooking();
        booking.setStatus(BookingStatus.AWAITING_REPORT);
        when(reports.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        ServiceReport result=reportService.submitServiceReport(6L,"Repaired pipe","Sealant","Annual check");
        assertSame(booking,result.getBooking());
        assertEquals("Repaired pipe",result.getWorkPerformed());
        assertEquals("Sealant",result.getMaterialsUsed());
        assertEquals("Annual check",result.getRecommendations());
        assertEquals(BookingStatus.AWAITING_REPORT,booking.getStatus());
    }

    @Test
    void reportSubmissionPreservesTimeStatusRequiredWorkAndDuplicateRules(){
        providerBooking();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setBookingDate(LocalDate.now().plusDays(1));
        assertThrows(BookingConflictException.class,()->reportService.submitServiceReport(6L,"Work",null,null));
        booking.setBookingDate(LocalDate.now().minusDays(1));
        assertThrows(BookingConflictException.class,()->reportService.submitServiceReport(6L," ",null,null));
        when(reports.existsByBookingId(6L)).thenReturn(true);
        assertThrows(BookingConflictException.class,()->reportService.submitServiceReport(6L,"Work",null,null));
        booking.setStatus(BookingStatus.COMPLETED);
        assertThrows(BookingConflictException.class,()->reportService.submitServiceReport(6L,"Work",null,null));
        verify(reports,never()).save(any());
    }

    @Test
    void homeownerCannotSubmitProviderReport(){
        ownerBooking();
        assertThrows(UnauthorizedException.class,()->reportService.submitServiceReport(6L,"Work",null,null));
        verify(reports,never()).save(any());
    }

    @Test
    void ownReviewReadUsesCurrentAccountAndPreservesPagination(){
        PageRequest page=PageRequest.of(1,2);
        when(current.getCurrentUser()).thenReturn(homeowner);
        when(reviews.findByHomeownerId(1L,page)).thenReturn(new PageImpl<>(List.of(new ServiceReview()),page,3));
        assertEquals(3,reviewService.getMyReviews(page).getTotalElements());
        verify(reviews).findByHomeownerId(1L,page);
    }

    @Test
    void completedOwnedBookingCanBeReviewedWithOptionalComment(){
        ownerBooking();
        when(reviews.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        ServiceReview result=reviewService.createServiceReviw(6L,5,null);
        assertSame(homeowner,result.getHomeowner());
        assertEquals(5,result.getRating());
        assertNull(result.getComment());
    }

    @Test
    void reviewSubmissionPreservesEligibilityRatingCommentAndDuplicateChecks(){
        ownerBooking();
        booking.setStatus(BookingStatus.CONFIRMED);
        assertThrows(BookingConflictException.class,()->reviewService.createServiceReviw(6L,5,"Good"));
        booking.setStatus(BookingStatus.COMPLETED);
        assertThrows(BookingConflictException.class,()->reviewService.createServiceReviw(6L,0,"Good"));
        assertThrows(BookingConflictException.class,()->reviewService.createServiceReviw(6L,6,"Good"));
        assertThrows(BookingConflictException.class,()->reviewService.createServiceReviw(6L,5," "));
        when(reviews.existsByBookingId(6L)).thenReturn(true);
        assertThrows(BookingConflictException.class,()->reviewService.createServiceReviw(6L,5,"Good"));
        verify(reviews,never()).save(any());
    }

    @Test
    void unrelatedHomeownerCannotReviewBooking(){
        when(bookings.findById(6L)).thenReturn(Optional.of(booking));
        User other=new User();
        other.setId(99L);
        when(current.getCurrentUser()).thenReturn(other);
        assertThrows(UnauthorizedException.class,()->reviewService.createServiceReviw(6L,5,"Good"));
        verify(reviews,never()).save(any());
    }

    @Test
    void providerReviewReadReusesExistingRepositoryQuery(){
        when(reviews.findByBookingServiceProviderId(3L)).thenReturn(List.of(new ServiceReview()));
        assertEquals(1,reviewService.providerReviews(3L).size());
        verify(reviews).findByBookingServiceProviderId(3L);
    }

    @Test
    void onlyAssignedProviderReviewingPendingBookingCanReadAssessmentHistory(){
        providerBooking();
        booking.setStatus(BookingStatus.PENDING);
        when(assessments.findByHomeownerId(1L)).thenReturn(List.of(new HomeownerAssessment()));
        assertEquals(1,assessmentService.getHomeownerAssessments(6L).size());
        booking.setStatus(BookingStatus.COMPLETED);
        assertThrows(UnauthorizedException.class,()->assessmentService.getHomeownerAssessments(6L));
        booking.setStatus(BookingStatus.PENDING);
        when(current.getCurrentUser()).thenReturn(homeowner);
        assertThrows(UnauthorizedException.class,()->assessmentService.getHomeownerAssessments(6L));
        User otherProvider=new User();
        otherProvider.setId(99L);
        otherProvider.setRole(Role.PROVIDER);
        when(current.getCurrentUser()).thenReturn(otherProvider);
        assertThrows(UnauthorizedException.class,()->assessmentService.getHomeownerAssessments(6L));
        verify(assessments,times(1)).findByHomeownerId(1L);
    }

    @Test
    void assignedProviderCanAssessCompletedBookingWithoutMakingAssessmentPublic(){
        providerBooking();
        when(assessments.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        HomeownerAssessment result=assessmentService.submitHomeownerAssessment(6L,null,"Clear access instructions");
        assertSame(homeowner,result.getHomeowner());
        assertSame(booking.getService().getProvider(),result.getAuthor());
        assertNull(result.getRating());
    }

    @Test
    void assessmentSubmissionPreservesStatusRatingContentAndDuplicateRules(){
        providerBooking();
        booking.setStatus(BookingStatus.PENDING);
        assertThrows(BookingConflictException.class,()->assessmentService.submitHomeownerAssessment(6L,5,null));
        booking.setStatus(BookingStatus.COMPLETED);
        assertThrows(BookingConflictException.class,()->assessmentService.submitHomeownerAssessment(6L,0,null));
        assertThrows(BookingConflictException.class,()->assessmentService.submitHomeownerAssessment(6L,6,null));
        assertThrows(BookingConflictException.class,()->assessmentService.submitHomeownerAssessment(6L,null," "));
        when(assessments.existsByBookingId(6L)).thenReturn(true);
        assertThrows(BookingConflictException.class,()->assessmentService.submitHomeownerAssessment(6L,5,null));
        verify(assessments,never()).save(any());
    }

    private void providerBooking(){
        when(bookings.findById(6L)).thenReturn(Optional.of(booking));
        when(current.getCurrentUser()).thenReturn(providerUser);
    }

    private void ownerBooking(){
        when(bookings.findById(6L)).thenReturn(Optional.of(booking));
        when(current.getCurrentUser()).thenReturn(homeowner);
    }
}
