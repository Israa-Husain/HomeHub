package com.ga.booking.cucumber;

import com.ga.HomeHub.booking.client.AuthServiceClient;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.jsonwebtoken.Jwts;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BookingHooks{
    private static final TimeZone ORIGINAL_ZONE=TimeZone.getDefault();

    private final ScenarioState state;
    private final BookingTestData data;
    private final AuthServiceClient auth;
    private final KafkaTemplate<String,String> kafka;

    @LocalServerPort
    private int port;

    public BookingHooks(
            ScenarioState state,
            BookingTestData data,
            AuthServiceClient auth,
            KafkaTemplate<String,String> kafka
    ){
        this.state=state;
        this.data=data;
        this.auth=auth;
        this.kafka=kafka;
    }

    @BeforeAll
    public static void prepareTimeZone(){
        //Keep time-sensitive booking fixtures away from midnight.
        int utcHour=OffsetDateTime.now(ZoneOffset.UTC).getHour();

        TimeZone.setDefault(
                TimeZone.getTimeZone(ZoneOffset.ofHours(12-utcHour))
        );
    }

    @AfterAll
    public static void restoreTimeZone(){
        TimeZone.setDefault(ORIGINAL_ZONE);
    }

    @Before
    public void beforeScenario(){
        data.clean();
        reset(auth,kafka);

        state.setPort(port);
        data.fixtures();

        when(auth.current(anyString())).thenAnswer(call->{
            String bearer=call.getArgument(0);

            String email=Jwts.parserBuilder()
                    .setSigningKey(BookingTestData.SECRET)
                    .build()
                    .parseClaimsJws(bearer.substring(7))
                    .getBody()
                    .getSubject();

            return data.accountByEmail(email);
        });

        when(auth.byId(anyLong(),anyString()))
                .thenAnswer(call->data.accountById(call.getArgument(0)));

        data.actor("homeowner");
    }

    @After
    public void afterScenario(){
        try{
            verify(kafka,never()).send(
                    anyString(),
                    anyString(),
                    anyString()
            );
        }finally{
            data.clean();
        }
    }
}