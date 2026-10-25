package com.ga.HomeHub.service;

import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.Booking;
import com.ga.HomeHub.model.ServiceReport;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.BookingStatus;
import com.ga.HomeHub.repository.BookingRepository;
import com.ga.HomeHub.repository.ServiceReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ServiceReportService {
    private final ServiceReportRepository reports;
    private final BookingRepository bookings;
    private final CurrentUserService current;

    //Allow the assigned provider to submit a service report after the appointment ends.
    @Transactional
    public ServiceReport submitServiceReport(Long bookingId, String workDone, String materialsUsed, String recommendation){
        User user = current.getCurrentUser();

        //Ensure only the provider assigned to the booking can submit its report.
        Booking booking = bookings.findById(bookingId).orElseThrow(()-> new InformationNotFoundException("Booking not found"));
        if(!booking.getService().getProvider().getUser().getId().equals(user.getId())){
            throw new UnauthorizedException("Not the assigned provider");
        }

        //Allow reports only for confirmed bookings or bookings awaiting a report.
        if(booking.getStatus()!= BookingStatus.CONFIRMED && booking.getStatus()!=BookingStatus.AWAITING_REPORT){
            throw new BookingConflictException("Booking is not ready for a report yet");
        }

        //Prevent providers from submitting reports before the scheduled service ends.
        LocalDateTime appointmentEnd=LocalDateTime.of(booking.getBookingDate(), booking.getEndTime());
        if(LocalDateTime.now().isBefore(appointmentEnd)){
            throw new BookingConflictException("Service report can only be submitted after the appointment ends");
        }

        //Require a description of the work performed.
        if(workDone==null || workDone.isBlank()){
            throw new BookingConflictException("Work details are required");
        }

        //Prevent multiple service reports for the same booking.
        if(reports.existsByBookingId(bookingId)){
            throw new BookingConflictException("Report already submitted");
        }

        ServiceReport report = new ServiceReport();
        report.setBooking(booking);
        report.setProvider(booking.getService().getProvider());
        report.setWorkPerformed(workDone);
        report.setMaterialsUsed(materialsUsed);
        report.setRecommendations(recommendation);
        return reports.save(report);
    }

    //Allow only the homeowner and assigned provider to view the service report.
    @Transactional(readOnly=true)
    public ServiceReport getServiceReport(Long bookingId){
        User user = current.getCurrentUser();
        ServiceReport report = reports.findByBooking_Id(bookingId).orElseThrow(()-> new InformationNotFoundException("Report not found"));
        Booking booking = report.getBooking();
        if(!booking.getCustomer().getId().equals(user.getId()) && !booking.getService().getProvider().getUser().getId().equals(user.getId())){
            throw new UnauthorizedException("Report is private");
        }

        return report;
    }
}
