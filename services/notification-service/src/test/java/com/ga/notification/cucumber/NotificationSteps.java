package com.ga.notification.cucumber;

import com.ga.HomeHub.notification.dto.NotificationEvent;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class NotificationSteps{
    private final ScenarioState state;
    private final NotificationTestData data;
    private final JavaMailSender sender;

    public NotificationSteps(
            ScenarioState state,
            NotificationTestData data,
            JavaMailSender sender
    ){
        this.state=state;
        this.data=data;
        this.sender=sender;
    }

    @Given("a {string} event")
    public void event(String type){
        data.newEvent(type);
    }

    @Given("SMTP fails once then recovers")
    public void smtpTemporary(){
        doThrow(new MailSendException("Fixture temporary outage"))
                .doNothing()
                .when(sender)
                .send(any(SimpleMailMessage.class));
    }

    @Given("SMTP keeps failing")
    public void smtpPermanent(){
        doThrow(new MailSendException("Fixture permanent outage"))
                .when(sender)
                .send(any(SimpleMailMessage.class));
    }

    @When("I publish the event")
    public void publish(){
        data.publish();
    }

    @When("the event is consumed")
    public void consumed(){
        data.awaitConsumed();
    }

    @When("I publish malformed JSON")
    public void malformed(){
        state.setPayload("invalid-json-"+UUID.randomUUID());
        data.publish();
    }

    @When("I use an unsupported schema version")
    public void badVersion(){
        NotificationEvent event=state.getEvent();

        state.setEvent(new NotificationEvent(
                event.eventId(),
                99,
                event.eventType(),
                event.occurredAt(),
                event.recipientId(),
                event.email(),
                event.subject(),
                event.message()
        ));

        data.encode();
    }

    @When("I use an unknown notification recipient")
    public void unknownRecipient(){
        NotificationEvent event=state.getEvent();

        state.setEvent(new NotificationEvent(
                event.eventId(),
                1,
                event.eventType(),
                event.occurredAt(),
                999L,
                event.email(),
                event.subject(),
                event.message()
        ));

        data.encode();
    }

    @When("I reuse the event ID with different content")
    public void different(){
        NotificationEvent event=state.getEvent();

        state.setEvent(new NotificationEvent(
                event.eventId(),
                1,
                event.eventType(),
                event.occurredAt(),
                event.recipientId(),
                event.email(),
                event.subject(),
                "Different event content"
        ));

        data.encode();
        data.publish();
    }

    @Then("there are {int} notification rows and {int} receipt rows")
    public void rows(int notifications,int receipts){
        assertEquals(
                notifications,
                data.count("notification")
        );

        assertEquals(
                receipts,
                data.count("outbox_event")
        );
    }

    @Then("the receipt is already marked published")
    public void receipt(){
        data.assertReceiptPublished();
    }

    @Then("the event reaches the dead-letter topic")
    public void deadLetter(){
        data.awaitDlt();
    }

    @Then("SMTP was called {int} times")
    public void mailAttempts(int count){
        verify(sender,times(count))
                .send(any(SimpleMailMessage.class));
    }

    @Then("SMTP received the event email fields")
    public void mailFields(){
        ArgumentCaptor<SimpleMailMessage> capture=
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(sender).send(capture.capture());

        SimpleMailMessage mail=capture.getValue();

        assertEquals(
                "cucumber-sender@homehub.example",
                mail.getFrom()
        );

        assertEquals(
                state.getEvent().email(),
                mail.getTo()[0]
        );

        assertEquals(
                state.getEvent().subject(),
                mail.getSubject()
        );

        assertEquals(
                state.getEvent().message(),
                mail.getText()
        );
    }
}