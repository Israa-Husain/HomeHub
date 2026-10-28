package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.AvailabilityRequest;
import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.Availability;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.repository.AvailabilityRepository;
import com.ga.HomeHub.repository.BookingRepository;
import com.ga.HomeHub.repository.ProviderProfileRepository;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@AllArgsConstructor
public class AvailabilityService {
    private final AvailabilityRepository repository;
    private final ProviderService providers;
    ProviderProfileRepository providerLocks;
    BookingRepository bookings;
    private final EntityManager entityManager;
    private final BookingWaitlistService waitlist;

    @Transactional
    public Availability createAvailability(AvailabilityRequest request){
        validateTimeRange(request,"Invalid availability time");
        ProviderProfile provider = providers.getCurrentProviderProfile();
        providerLocks.lockById(provider.getId());
        Availability availability = new Availability();
        availability.setProvider(provider);
        availability.setDate(request.date());
        availability.setStartTime(request.startTime());
        availability.setEndTime(request.endTime());
        return repository.save(availability);
    }

    public List<Availability> getProviderAvailabilityByDate(Long providerId, LocalDate date,boolean timeOff){
        if(timeOff){
            if(!providerId.equals(providers.getCurrentProviderProfile().getId())){
                throw new UnauthorizedException("Not your time off");
            }
            return repository.findByProviderIdAndDateAndTimeOffTrue(providerId,date);
        }
        return repository.findByProviderIdAndDateAndIsAvailableTrue(providerId,date);
    }

    //Reuse the existing request and entity for edits; keep the original period type.
    @Transactional
    public Availability updateAvailability(Long id,AvailabilityRequest request){
        validateTimeRange(request,"Invalid availability time");
        Availability availability=repository.findById(id).orElseThrow(()->new InformationNotFoundException("Availability not found"));
        if(!availability.getProvider().getId().equals(providers.getCurrentProviderProfile().getId())){
            throw new UnauthorizedException("Not your availability");
        }
        Long providerId=availability.getProvider().getId();
        providerLocks.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));
        entityManager.refresh(availability);
        if(!providerId.equals(availability.getProvider().getId())){
            throw new UnauthorizedException("Not your availability");
        }
        if(!availability.isAvailable() && !availability.isTimeOff()){
            throw new IllegalArgumentException("Availability is inactive");
        }
        if(hasBookingConflict(providerId,availability.getDate(),availability.getStartTime(),availability.getEndTime())
                || (availability.isTimeOff() && hasBookingConflict(providerId,request.date(),request.startTime(),request.endTime()))){
            throw new BookingConflictException("Existing bookings overlap this availability change");
        }
        LocalDate oldDate=availability.getDate();
        availability.setDate(request.date());
        availability.setStartTime(request.startTime());
        availability.setEndTime(request.endTime());
        Availability saved=repository.save(availability);
        if(saved.isTimeOff()){
            waitlist.offerNextAvailableProviderSlot(providerId,oldDate);
        }
        return saved;
    }

    @Transactional
    public void deleteAvailability(Long id){
        Availability availability = repository.findById(id).orElseThrow(()-> new InformationNotFoundException("Availability not found"));
        if(!availability.getProvider().getId().equals(providers.getCurrentProviderProfile().getId())){
            throw new UnauthorizedException("Not your availability");
        }
        Long providerId=availability.getProvider().getId();
        providerLocks.lockById(providerId).orElseThrow(()->new InformationNotFoundException("Provider not found"));
        entityManager.refresh(availability);
        if(!providerId.equals(availability.getProvider().getId())){
            throw new UnauthorizedException("Not your availability");
        }
        if(hasBookingConflict(availability.getProvider().getId(),availability.getDate(),availability.getStartTime(),availability.getEndTime())){
            throw new BookingConflictException("Existing bookings overlap this availability");
        }
        boolean releasedTimeOff=availability.isTimeOff();
        availability.setAvailable(false);
        availability.setTimeOff(false);
        repository.save(availability);
        if(releasedTimeOff){
            waitlist.offerNextAvailableProviderSlot(availability.getProvider().getId(),availability.getDate());
        }
    }

    //Block provider time off without creating a duplicate entity.
    @Transactional
    public Availability blockTimeOff(AvailabilityRequest request){
        validateTimeRange(request,"Invalid time off range");
        ProviderProfile provider=providers.getCurrentProviderProfile();
        providerLocks.lockById(provider.getId());
        if(hasBookingConflict(provider.getId(),request.date(),request.startTime(),request.endTime())){
            throw new BookingConflictException("Existing bookings overlap time off");
        }
        Availability block=new Availability();
        block.setProvider(provider);
        block.setDate(request.date());
        block.setStartTime(request.startTime());
        block.setEndTime(request.endTime());
        block.setAvailable(false);block.setTimeOff(true);
        return repository.save(block);
    }

    //Keep the existing validation and booking-overlap predicate shared by all period changes.
    private void validateTimeRange(AvailabilityRequest request,String message){
        if(request.date().isBefore(LocalDate.now()) || !request.endTime().isAfter(request.startTime())){
            throw new IllegalArgumentException(message);
        }
    }

    private boolean hasBookingConflict(Long providerId,LocalDate date,LocalTime start,LocalTime end){
        return bookings.findByServiceProviderIdAndBookingDateAndStatusIn(providerId,date,
                List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT))
                .stream().anyMatch(b->start.isBefore(b.getEndTime()) && end.isAfter(b.getStartTime()));
    }
}
