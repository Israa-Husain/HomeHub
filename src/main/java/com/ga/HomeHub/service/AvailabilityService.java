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
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@AllArgsConstructor
public class AvailabilityService {
    private final AvailabilityRepository repository;
    private final ProviderService providers;
    ProviderProfileRepository providerLocks;
    BookingRepository bookings;

    public Availability createAvailability(AvailabilityRequest request){
        if(request.date().isBefore(LocalDate.now()) || !request.endTime().isAfter(request.startTime())){
            throw new IllegalArgumentException("Invalid availability time");
        }
        ProviderProfile provider = providers.getCurrentProviderProfile();
        Availability availability = new Availability();
        availability.setProvider(provider);
        availability.setDate(request.date());
        availability.setStartTime(request.startTime());
        availability.setEndTime(request.endTime());
        return repository.save(availability);
    }

    public List<Availability> getProviderAvailabilityByDate(Long providerId, LocalDate date){
        return repository.findByProviderIdAndDateAndIsAvailableTrue(providerId,date);
    }

    public void deleteAvailability(Long id){
        Availability availability = repository.findById(id).orElseThrow(()-> new InformationNotFoundException("Availability not found"));
        if(!availability.getProvider().getId().equals(providers.getCurrentProviderProfile().getId())){
            throw new UnauthorizedException("Not your availability");
        }
        availability.setAvailable(false);
        repository.save(availability);
    }

    //Block provider time off without creating a duplicate entity.
    @Transactional
    public Availability blockTimeOff(AvailabilityRequest request){
        if(request.date().isBefore(LocalDate.now()) || !request.endTime().isAfter(request.startTime())){
            throw new IllegalArgumentException("Invalid time off range");
        }
        ProviderProfile provider=providers.getCurrentProviderProfile();
        providerLocks.lockById(provider.getId());
        boolean booked=bookings.findByServiceProviderIdAndBookingDateAndStatusIn(provider.getId(),request.date(), List.of(BookingStatus.PENDING,BookingStatus.CONFIRMED,BookingStatus.AWAITING_REPORT)).stream().anyMatch(b->request.startTime().isBefore(b.getEndTime()) && request.endTime().isAfter(b.getStartTime()));
        if(booked){throw new BookingConflictException("Existing bookings overlap time off");}
        Availability block=new Availability();
        block.setProvider(provider);
        block.setDate(request.date());
        block.setStartTime(request.startTime());
        block.setEndTime(request.endTime());
        block.setAvailable(false);block.setTimeOff(true);
        return repository.save(block);
    }
}
