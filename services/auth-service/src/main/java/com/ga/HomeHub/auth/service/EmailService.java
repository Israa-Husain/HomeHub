
package com.ga.HomeHub.auth.service;

import com.ga.HomeHub.auth.messaging.AuthOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
//Email is now durable work for Notification Service rather than a local SMTP side effect.
public class EmailService {
    private final AuthOutboxService outbox;

    public void sendEmail(String to, String subject, String body) {
        outbox.enqueue("EMAIL", null, to, subject, body);
    }
}
