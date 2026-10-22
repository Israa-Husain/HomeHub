
package com.ga.HomeHub.service;

import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.HomeownerAssessment;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.repository.BookingRepository;
import com.ga.HomeHub.repository.HomeownerAssessmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeownerAssessmentService {
    private final HomeownerAssessmentRepository assessments;
    private final BookingRepository bookings;
    private final CurrentUserService current;

    //Allow the assigned provider to submit an optional assessment after completing a booking.
    @Transactional
    public HomeownerAssessment submitHomeownerAssessment(Long bookingId, Integer rating, String comment){
        Booking booking=bookings.findById(bookingId).orElseThrow(()->new InformationNotFoundException("Booking not found"));
        User user=current.getCurrentUser();

        if(!booking.getService().getProvider().getUser().getId().equals(user.getId())){
            throw new UnauthorizedException("Not assigned provider");
        }

        if(booking.getStatus()!=BookingStatus.COMPLETED){
            throw new BookingConflictException("Booking must be completed");
        }

        //Prevent multiple assessments for the same booking.
        if(assessments.existsByBookingId(bookingId)){
            throw new BookingConflictException("Already assessed");
        }

        //Validate the optional rating when provided.
        if(rating!=null && (rating<1 || rating>5)){
            throw new BookingConflictException("Rating must be 1 to 5");
        }

        //Require at least a rating or a comment.
        if(rating==null && (comment==null || comment.isBlank())){
            throw new BookingConflictException("Provide a rating or comment");
        }

        HomeownerAssessment assessment=new HomeownerAssessment();
        assessment.setBooking(booking);
        assessment.setHomeowner(booking.getCustomer());
        assessment.setAuthor(booking.getService().getProvider());
        assessment.setRating(rating);
        assessment.setComment(comment);

        return assessments.save(assessment);
    }

    //Allow providers to view homeowner assessments only when reviewing a pending booking.
    @Transactional(readOnly=true)
    public List<HomeownerAssessment> getHomeownerAssessments(Long bookingId){
        Booking booking=bookings.findById(bookingId).orElseThrow(()->new InformationNotFoundException("Booking not found"));
        User user=current.getCurrentUser();

        if(!booking.getService().getProvider().getUser().getId().equals(user.getId()) || booking.getStatus()!=BookingStatus.PENDING){
            throw new UnauthorizedException("Assessments are available only to the provider reviewing a pending booking");
        }

        return assessments.findByHomeownerId(booking.getCustomer().getId());
    }
}
