package com.ga.booking.cucumber;

import com.ga.HomeHub.booking.dto.user.UserResponse;
import com.ga.HomeHub.booking.model.Availability;
import com.ga.HomeHub.booking.model.Booking;
import com.ga.HomeHub.booking.model.Category;
import com.ga.HomeHub.booking.model.Home;
import com.ga.HomeHub.booking.model.ProviderProfile;
import com.ga.HomeHub.booking.model.ServiceOffering;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.model.enums.BookingStatus;
import com.ga.HomeHub.booking.model.enums.ProviderStatus;
import com.ga.HomeHub.booking.model.enums.Role;
import com.ga.HomeHub.booking.model.enums.UserStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BookingTestData{
    public static final String SECRET=
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final ScenarioState state;
    private final JdbcTemplate jdbc;
    private final DataSource source;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper=new ObjectMapper();

    @PersistenceContext
    private EntityManager em;

    public BookingTestData(
            ScenarioState state,
            JdbcTemplate jdbc,
            DataSource source,
            PlatformTransactionManager transactions
    ){
        this.state=state;
        this.jdbc=jdbc;
        this.source=source;
        this.transactions=new TransactionTemplate(transactions);
    }

    public void clean(){
        //Allow fixture cleanup only in the isolated H2 database.
        try(Connection connection=source.getConnection()){
            if(!connection.getMetaData().getURL()
                    .startsWith("jdbc:h2:mem:homehub-booking-cucumber")){
                throw new IllegalStateException(
                        "Only the isolated H2 Cucumber database may be cleaned"
                );
            }
        }catch(SQLException e){
            throw new IllegalStateException(
                    "Cannot validate test datasource",
                    e
            );
        }

        List<String> tables=jdbc.queryForList(
                "select table_name from information_schema.tables "
                        +"where table_schema='PUBLIC' "
                        +"and table_type='BASE TABLE'",
                String.class
        );

        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");

        try{
            for(String table:tables){
                if(!table.matches("[A-Z_]+")){
                    throw new IllegalStateException(
                            "Unexpected fixture table name"
                    );
                }

                jdbc.update("delete from "+table);
            }
        }finally{
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    public long userId(String actor){
        return switch(actor){
            case "homeowner"->1;
            case "provider"->2;
            case "other homeowner"->3;
            case "other provider"->4;
            case "admin"->5;
            default->throw new IllegalArgumentException(
                    "Unknown test actor"
            );
        };
    }

    public void actor(String actor){
        state.setActor(actor);

        String email="user"+userId(actor)+"@homehub.example";

        state.setToken(
                Jwts.builder()
                        .setSubject(email)
                        .setIssuedAt(new Date())
                        .setExpiration(
                                new Date(System.currentTimeMillis()+3600000)
                        )
                        .signWith(SignatureAlgorithm.HS256,SECRET)
                        .compact()
        );
    }

    public UserResponse accountByEmail(String email){
        Long id=jdbc.queryForObject(
                "select id from users where email_address=?",
                Long.class,
                email
        );

        return accountById(id);
    }

    public UserResponse accountById(Long id){
        return jdbc.queryForObject(
                "select * from users where id=?",
                (row,index)->new UserResponse(
                        id,
                        row.getString("first_name"),
                        row.getString("last_name"),
                        row.getString("email_address"),
                        row.getString("phone_number"),
                        null,
                        Role.valueOf(row.getString("role")),
                        UserStatus.valueOf(row.getString("status")),
                        row.getBoolean("email_verified"),
                        row.getTimestamp("created_at").toLocalDateTime(),
                        row.getTimestamp("updated_at").toLocalDateTime()
                ),
                id
        );
    }

    public void fixtures(){
        state.setDate(LocalDate.now().plusDays(3));

        for(long id=1;id<=5;id++){
            String role=id==2||id==4
                    ?"PROVIDER"
                    :id==5?"ADMIN":"HOMEOWNER";

            LocalDateTime now=LocalDateTime.now();

            //Insert fictional accounts only in the isolated test database.
            jdbc.update(
                    "insert into users("
                            +"id,first_name,last_name,email_address,"
                            +"phone_number,role,status,email_verified,"
                            +"created_at,updated_at"
                            +") values(?,?,?,?,?,?,'ACTIVE',true,?,?)",
                    id,
                    "Cucumber",
                    "Account",
                    "user"+id+"@homehub.example",
                    "33331234",
                    role,
                    now,
                    now
            );
        }

        transactions.executeWithoutResult(tx->{
            ProviderProfile provider=new ProviderProfile();
            provider.setUser(em.getReference(User.class,2L));
            provider.setBusinessName("Cucumber Provider");
            provider.setProviderStatus(ProviderStatus.APPROVED);

            em.persist(provider);
            state.setProviderId(provider.getId());

            Category category=new Category();
            category.setName("Cucumber category");

            em.persist(category);
            state.setCategoryId(category.getId());

            ServiceOffering service=new ServiceOffering();
            service.setProvider(provider);
            service.setCategory(category);
            service.setName("Cucumber cleaning");
            service.setDurationMinutes(60);
            service.setPrice(100.0);

            em.persist(service);
            state.setServiceId(service.getId());

            Home home=home(1L,"Main home");
            state.setHomeId(home.getId());

            Home other=home(3L,"Other home");
            state.setOtherHomeId(other.getId());

            availability(
                    state.getDate(),
                    LocalTime.of(8,0),
                    LocalTime.of(18,0),
                    false
            );
        });
    }

    private Home home(Long userId,String name){
        Home home=new Home();
        home.setOwner(em.getReference(User.class,userId));
        home.setName(name);
        home.setCity("Manama");
        home.setAddress("Fictional test address");

        em.persist(home);
        return home;
    }

    private void availability(
            LocalDate date,
            LocalTime start,
            LocalTime end,
            boolean timeOff
    ){
        Availability window=new Availability();
        window.setProvider(
                em.getReference(
                        ProviderProfile.class,
                        state.getProviderId()
                )
        );
        window.setDate(date);
        window.setStartTime(start);
        window.setEndTime(end);
        window.setTimeOff(timeOff);
        window.setAvailable(!timeOff);

        em.persist(window);
    }

    public void timeOff(){
        transactions.executeWithoutResult(tx->availability(
                state.getDate(),
                LocalTime.of(9,0),
                LocalTime.of(10,0),
                true
        ));
    }

    public void fixtureBooking(String status){
        transactions.executeWithoutResult(tx->{
            Booking booking=new Booking();
            booking.setCustomer(em.getReference(User.class,1L));
            booking.setHome(
                    em.getReference(Home.class,state.getHomeId())
            );
            booking.setService(
                    em.getReference(
                            ServiceOffering.class,
                            state.getServiceId()
                    )
            );
            booking.setBookingDate(state.getDate());
            booking.setStartTime(LocalTime.of(9,0));
            booking.setEndTime(LocalTime.of(10,0));
            booking.setStatus(BookingStatus.valueOf(status));
            booking.setFinalPrice(100.0);
            booking.setResponseDeadline(
                    LocalDateTime.now().plusHours(24)
            );

            em.persist(booking);
        });
    }

    public void changeHome(Consumer<Home> change){
        transactions.executeWithoutResult(tx->change.accept(
                em.find(Home.class,state.getHomeId())
        ));
    }

    public void noAvailability(){
        jdbc.update("delete from availability");
    }

    public void inactiveCategory(){
        jdbc.update(
                "update category set status='INACTIVE' where id=?",
                state.getCategoryId()
        );
    }

    public void unapprovedProvider(){
        jdbc.update(
                "update provider_profile set provider_status='PENDING' "
                        +"where id=?",
                state.getProviderId()
        );
    }

    public long count(String table){
        return jdbc.queryForObject(
                "select count(*) from "+table,
                Long.class
        );
    }

    public List<Map<String,Object>> events(){
        return jdbc.queryForList(
                "select payload,published_at from outbox_event"
        );
    }

    public Map<String,Object> payload(Object json){
        try{
            return mapper.readValue(
                    json.toString(),
                    new TypeReference<Map<String,Object>>(){}
            );
        }catch(JsonProcessingException e){
            throw new IllegalStateException(e);
        }
    }
}