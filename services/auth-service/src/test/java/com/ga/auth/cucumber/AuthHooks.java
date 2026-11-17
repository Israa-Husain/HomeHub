package com.ga.auth.cucumber;

import com.ga.HomeHub.auth.config.RateLimit;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class AuthHooks {
    private final ScenarioState state;
    private final AuthTestData data;
    private final RateLimit rateLimit;
    private final KafkaTemplate<String, String> kafka;

    @LocalServerPort
    private int port;

    public AuthHooks(ScenarioState state, AuthTestData data, RateLimit rateLimit, KafkaTemplate<String, String> kafka) {
        this.state = state;
        this.data = data;
        this.rateLimit = rateLimit;
        this.kafka = kafka;
    }

    @Before
    public void beforeScenario() {
        data.clean();
        state.setPort(port);

        //Reset the IP rate-limit window between scenarios. A separate scenario checks rate limiting.
        Object windows = ReflectionTestUtils.getField(rateLimit, "windows");
        if (!(windows instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Review RateLimit test fixture");
        }
        map.clear();
    }

    @After
    public void afterScenario() {
        try {
            verify(kafka, never()).send(anyString(), anyString(), anyString());
        } finally {
            data.clean();
        }
    }
}
