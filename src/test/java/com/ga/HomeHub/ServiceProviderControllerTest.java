package com.ga.HomeHub;

import com.ga.HomeHub.controller.ProviderController;
import com.ga.HomeHub.controller.AdminController;
import com.ga.HomeHub.controller.ServiceOfferingController;
import com.ga.HomeHub.dto.ProviderResponse;
import com.ga.HomeHub.dto.ServiceRequest;
import com.ga.HomeHub.exception.ApiExceptionHandler;
import com.ga.HomeHub.service.ProviderService;
import com.ga.HomeHub.service.ServiceOfferingService;
import com.ga.HomeHub.service.UserService;
import com.ga.HomeHub.model.ProviderProfile;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.ProviderStatus;
import org.springframework.data.domain.PageImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

//Exercise real controller validation and Spring method security without connecting to development data.
@SpringJUnitConfig(ServiceProviderControllerTest.Config.class)
class ServiceProviderControllerTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        ServiceOfferingService offerings(){
            return mock(ServiceOfferingService.class);
        }

        @Bean
        ProviderService providers(){
            return mock(ProviderService.class);
        }

        @Bean
        ServiceOfferingController serviceController(ServiceOfferingService offerings){
            return new ServiceOfferingController(offerings);
        }

        @Bean
        ProviderController providerController(ProviderService providers){
            return new ProviderController(providers);
        }

        @Bean
        UserService users(){
            return mock(UserService.class);
        }

        @Bean
        AdminController adminController(UserService users,ProviderService providers){
            return new AdminController(users,providers);
        }
    }

    @Autowired private ServiceOfferingController serviceController;
    @Autowired private ProviderController providerController;
    @Autowired private AdminController adminController;
    @Autowired private ServiceOfferingService offerings;
    @Autowired private ProviderService providers;
    private MockMvc mvc;

    @BeforeEach
    void setUp(){
        reset(offerings,providers);
        mvc=MockMvcBuilders.standaloneSetup(serviceController,providerController,adminController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @AfterEach
    void clearAuthentication(){
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownServicesRequireProviderRole() throws Exception{
        authenticate("HOMEOWNER");
        mvc.perform(get("/api/services/mine")).andExpect(status().isForbidden());
        authenticate("ADMIN");
        mvc.perform(get("/api/services/mine")).andExpect(status().isForbidden());
        verifyNoInteractions(offerings);
        authenticate("PROVIDER");
        when(offerings.getCurrentProviderServices(any())).thenReturn(Page.empty(PageRequest.of(0,20)));
        mvc.perform(get("/api/services/mine")).andExpect(status().isOk());
    }

    @Test
    void profileUpdateRejectsOtherRolesAndRetainsRequiredNameValidation() throws Exception{
        authenticate("HOMEOWNER");
        mvc.perform(put("/api/providers/profile").contentType("application/json")
                .content("{\"businessName\":\"Business\",\"description\":\"Repairs\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(providers);
        authenticate("PROVIDER");
        mvc.perform(put("/api/providers/profile").contentType("application/json")
                .content("{\"businessName\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(providers);
        mvc.perform(put("/api/providers/profile").contentType("application/json")
                .content("{\"businessName\":\"Updated business\",\"description\":\"Repairs\"}"))
                .andExpect(status().isOk());
        verify(providers).updateProviderProfile(any());
    }

    @Test
    void existingServiceJsonWithoutEmergencyFieldRemainsAccepted() throws Exception{
        authenticate("PROVIDER");
        mvc.perform(post("/api/services").contentType("application/json")
                .content("{\"categoryId\":3,\"name\":\"Repair\",\"price\":50,\"durationMinutes\":60}"))
                .andExpect(status().isCreated());
        ArgumentCaptor<ServiceRequest> request=ArgumentCaptor.forClass(ServiceRequest.class);
        verify(offerings).createServiceOffering(request.capture());
        assertNull(request.getValue().emergencyEnabled());
        assertEquals(3L,request.getValue().categoryId());
    }

    @Test
    void serviceJsonSupportsEmergencyOptInAndRejectsInvalidExistingFields() throws Exception{
        authenticate("PROVIDER");
        mvc.perform(post("/api/services").contentType("application/json")
                .content("{\"categoryId\":3,\"name\":\"Repair\",\"price\":50,\"durationMinutes\":60,\"emergencyEnabled\":true}"))
                .andExpect(status().isCreated());
        ArgumentCaptor<ServiceRequest> request=ArgumentCaptor.forClass(ServiceRequest.class);
        verify(offerings).createServiceOffering(request.capture());
        assertEquals(Boolean.TRUE,request.getValue().emergencyEnabled());
        reset(offerings);
        mvc.perform(post("/api/services").contentType("application/json")
                .content("{\"categoryId\":3,\"name\":\"\",\"price\":0,\"durationMinutes\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(offerings);
    }

    @Test
    void providerDetailsSerializeOnlySafeBusinessFields() throws Exception{
        when(providers.getProviderDetails(11L)).thenReturn(new ProviderResponse(11L,"Repair business","Repairs","APPROVED","Portfolio",true));
        mvc.perform(get("/api/providers/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Repair business"))
                .andExpect(jsonPath("$.portfolioDescription").value("Portfolio"))
                .andExpect(jsonPath("$.emergencyEnabled").value(true))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void serviceListAcceptsOptionalProviderFilterAndExistingRequests() throws Exception{
        when(offerings.getAllServiceOffering(any(),any(),any())).thenReturn(Page.empty(PageRequest.of(0,10)));
        mvc.perform(get("/api/services").param("categoryId","3").param("providerId","11").param("size","10"))
                .andExpect(status().isOk());
        verify(offerings).getAllServiceOffering(3L,11L,PageRequest.of(0,10));
        mvc.perform(get("/api/services")).andExpect(status().isOk());
        verify(offerings).getAllServiceOffering(null,null,PageRequest.of(0,20));
    }

    @Test
    void applicationListingAndDetailsAreAdminOnly() throws Exception{
        for(String role:List.of("HOMEOWNER","PROVIDER")){
            authenticate(role);
            mvc.perform(get("/api/admin/providers")).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/providers/11")).andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/providers/11/approve")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(providers);
    }

    @Test
    void adminCanInspectPendingApplicationWithSafeApplicantFieldsAndPaging() throws Exception{
        authenticate("ADMIN");
        User applicant=new User();
        applicant.setId(7L);
        applicant.setFirstName("Applicant");
        applicant.setLastName("Name");
        applicant.setEmailAddress("applicant@example.test");
        applicant.setPasswordHash("must-not-be-exposed");
        ProviderProfile provider=new ProviderProfile();
        provider.setId(11L);
        provider.setUser(applicant);
        provider.setProviderStatus(ProviderStatus.PENDING);
        PageRequest page=PageRequest.of(1,1);
        when(providers.getProviderApplications(ProviderStatus.PENDING,page)).thenReturn(new PageImpl<>(List.of(provider),page,3));
        when(providers.getProviderApplication(11L)).thenReturn(provider);
        when(providers.toResponse(provider)).thenReturn(new ProviderResponse(11L,"Business","Description","PENDING","Portfolio",false));
        mvc.perform(get("/api/admin/providers").param("status","PENDING").param("page","1").param("size","1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].provider.providerStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[0].applicant.id").value(7))
                .andExpect(jsonPath("$.content[0].applicant.email").value("applicant@example.test"))
                .andExpect(jsonPath("$.content[0].applicant.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].provider.user").doesNotExist());
        mvc.perform(get("/api/admin/providers/11")).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider.id").value(11))
                .andExpect(jsonPath("$.applicant.password").doesNotExist());
        verify(providers).getProviderApplications(ProviderStatus.PENDING,page);
    }

    @Test
    void invalidApplicationStatusReturns400AndExistingApprovalStillWorks() throws Exception{
        authenticate("ADMIN");
        mvc.perform(get("/api/admin/providers").param("status","REJECTED"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(providers);
        ProviderProfile provider=new ProviderProfile();
        provider.setId(11L);
        when(providers.updateProviderStatus(11L)).thenReturn(provider);
        when(providers.toResponse(provider)).thenReturn(new ProviderResponse(11L,"Business",null,"APPROVED",null,false));
        mvc.perform(put("/api/admin/providers/11/approve")).andExpect(status().isOk())
                .andExpect(jsonPath("$.providerStatus").value("APPROVED"));
        verify(providers).updateProviderStatus(11L);
    }

    private void authenticate(String role){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-account","unused",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
}
