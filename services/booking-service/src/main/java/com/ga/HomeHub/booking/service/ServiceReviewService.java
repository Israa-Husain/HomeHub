package com.ga.HomeHub.booking.service;


import com.ga.HomeHub.booking.exception.BookingConflictException;
import com.ga.HomeHub.booking.exception.InformationNotFoundException;
import com.ga.HomeHub.booking.exception.UnauthorizedException;
import com.ga.HomeHub.booking.model.Booking;
import com.ga.HomeHub.booking.model.ServiceReview;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.model.enums.BookingStatus;
import com.ga.HomeHub.booking.repository.BookingRepository;
import com.ga.HomeHub.booking.repository.ServiceReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ga.HomeHub.booking.repository.ProviderProfileRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ServiceReviewService {
    private final ServiceReviewRepository reviews;
    private final BookingRepository bookings;
    private final CurrentUserService current;
    private final ProviderProfileRepository providers;
    private final EntityManager entityManager;

    //Allow homeowners to review a provider after their booking is completed.
    @Transactional
    public ServiceReview createServiceReviw(Long bookingId, int rate, String comment){
        Booking booking = bookings.findById(bookingId).orElseThrow(()-> new InformationNotFoundException("Booking not found"));
        //Serialize validation and duplicate checks with other changes to this provider's bookings.
        providers.lockById(booking.getService().getProvider().getId()).orElseThrow(()->new InformationNotFoundException("Provider not found"));
        entityManager.refresh(booking);
        User user = current.getCurrentUser();

        //Ensure the review belongs to the homeowner who made the booking.
        if(!booking.getCustomer().getId().equals(user.getId())){
            throw new UnauthorizedException("Not your booking");
        }

        //Allow reviews only after the booking has been completed.
        if(booking.getStatus() != BookingStatus.COMPLETED){
            throw new BookingConflictException("Booking must be completed before reviewing");
        }

        //Require a rating between 1 and 5.
        if(rate<1 || rate>5){
            throw new IllegalArgumentException("Rating must be 1 to 5");
        }

        //Prevent multiple reviews for the same booking.
        if(reviews.existsByBookingId(bookingId)){
            throw new BookingConflictException("Booking already got reviewed");
        }

        //Reject comments containing only spaces.
        if(comment!=null && comment.isBlank()){
            throw new IllegalArgumentException("Comment can not be blank");
        }

        ServiceReview review = new ServiceReview();
        review.setBooking(booking);
        review.setHomeowner(user);
        review.setRating(rate);
        review.setComment(comment);
        return reviews.save(review);
    }

    //Retrieve all public reviews for a specific provider.
    @Transactional(readOnly=true)
    public List<ServiceReview> providerReviews(Long providerId){
        return reviews.findByBookingServiceProviderId(providerId);
    }

    //Resolve ownership from authentication, never from a caller-supplied homeowner ID.
    @Transactional(readOnly=true)
    public Page<ServiceReview> getMyReviews(Pageable pageable){
        return reviews.findByHomeownerId(current.getCurrentUser().getId(),pageable);
    }
}