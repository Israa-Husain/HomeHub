package com.ga.HomeHub.booking.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@Component
@ConfigurationProperties(prefix = "homehub.booking")
@Getter
@Setter
public class BookingPolicyProperties {
    @Min(24)
    private int normalNoticeHours = 24;

    @Min(1)
    private int emergencyNoticeHours = 1;

    @Min(24)
    private int rescheduleNoticeHours = 24;

    @Min(1)
    private int responseDeadlineHours = 24;

    @Min(1)
    private int reportDeadlineHours = 24;

    @Min(1)
    private int cancellationNoticeHours = 24;

    @Min(1)
    private int waitlistOfferMinutes = 15;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double emergencySurcharge = 0.20;
}