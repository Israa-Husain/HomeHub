package com.ga.notification.cucumber;

import com.ga.HomeHub.notification.dto.NotificationEvent;
import io.cucumber.spring.ScenarioScope;
import lombok.Getter;
import lombok.Setter;

@ScenarioScope
@Getter
@Setter
public class ScenarioState{
    private NotificationEvent event;
    private String payload;
    private int partition;
    private long offset=-1;
}