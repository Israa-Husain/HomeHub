package com.ga.notification.cucumber;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.Mockito.reset;

public class NotificationHooks{
    private final NotificationTestData data;
    private final JavaMailSender sender;

    public NotificationHooks(
            NotificationTestData data,
            JavaMailSender sender
    ){
        this.data=data;
        this.sender=sender;
    }

    @Before
    public void beforeScenario(){
        data.clean();
        data.users();
        reset(sender);
    }

    @After
    public void afterScenario(){
        try{
            //Wait for processing before removing scenario fixtures.
            data.awaitConsumed();
        }finally{
            data.clean();
        }
    }
}