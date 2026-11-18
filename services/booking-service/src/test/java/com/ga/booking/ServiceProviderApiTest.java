package com.ga.booking;

import com.ga.HomeHub.booking.dto.ProviderRequest;
import com.ga.HomeHub.booking.dto.ProviderResponse;
import com.ga.HomeHub.booking.dto.ServiceRequest;
import com.ga.HomeHub.booking.dto.ServiceResponse;
import com.ga.HomeHub.booking.exception.InformationNotFoundException;
import com.ga.HomeHub.booking.exception.UnauthorizedException;
import com.ga.HomeHub.booking.model.Category;
import com.ga.HomeHub.booking.model.ProviderProfile;
import com.ga.HomeHub.booking.model.ServiceOffering;
import com.ga.HomeHub.booking.model.User;
import com.ga.HomeHub.booking.model.enums.CategoryStatus;
import com.ga.HomeHub.booking.model.enums.ProviderStatus;
import com.ga.HomeHub.booking.model.enums.Role;
import com.ga.HomeHub.booking.model.enums.ServiceStatus;
import com.ga.HomeHub.booking.model.enums.UserStatus;
import com.ga.HomeHub.booking.repository.CategoryRepository;
import com.ga.HomeHub.booking.repository.ProviderProfileRepository;
import com.ga.HomeHub.booking.repository.ServiceOfferingRepository;
import com.ga.HomeHub.booking.service.AuditLogService;
import com.ga.HomeHub.booking.service.CurrentUserService;
import com.ga.HomeHub.booking.service.ProviderService;
import com.ga.HomeHub.booking.service.ServiceOfferingService;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceProviderApiTest {
    @Mock private EntityManager entityManager;

    @Mock private ServiceOfferingRepository services;
    @Mock private CategoryRepository categories;
    @Mock private ProviderProfileRepository providerRepository;
    @Mock private CurrentUserService current;
    @Mock private AuditLogService audit;

    private ProviderService providers;
    private ServiceOfferingService offerings;
    private User owner;
    private ProviderProfile provider;
    private Category category;
    private ServiceOffering offering;
    private final PageRequest page=PageRequest.of(0,10);

    @BeforeEach
    void setUp(){
        Mockito.lenient().when(providerRepository.lockById(ArgumentMatchers.anyLong())).thenAnswer(call->Optional.of(provider));
        providers=new ProviderService(providerRepository,current,audit,entityManager);
        offerings=new ServiceOfferingService(services,categories,providers,audit,current,providerRepository,entityManager);
        owner=new User();
        owner.setId(7L);
        owner.setRole(Role.PROVIDER);
        provider=new ProviderProfile();
        provider.setId(11L);
        provider.setUser(owner);
        provider.setProviderStatus(ProviderStatus.APPROVED);
        provider.setBusinessName("Repair business");
        provider.setEmergencyEnabled(true);
        provider.setPortfolioDescription("Repairs and installations");
        category=new Category();
        category.setId(3L);
        category.setName("Plumbing");
        offering=new ServiceOffering();
        offering.setId(21L);
        offering.setProvider(provider);
        offering.setCategory(category);
        offering.setName("Leak repair");
        offering.setPrice(50.0);
        offering.setDurationMinutes(60);
        offering.setEmergencyEnabled(true);
    }

    @Test
    void responseIncludesIdentifiersStatusesAndCombinedEmergencyEligibility(){
        ServiceResponse result=offerings.toResponse(offering);
        assertEquals(3L,result.categoryId());
        assertEquals(11L,result.providerId());
        assertEquals("Plumbing",result.categoryName());
        assertEquals("Repair business",result.providerName());
        assertEquals("ACTIVE",result.status());
        assertEquals("ACTIVE",result.categoryStatus());
        assertEquals("APPROVED",result.providerStatus());
        assertTrue(result.emergencyEnabled());
        assertTrue(result.providerEmergencyEnabled());
        assertTrue(result.emergencyEligible());
        provider.setEmergencyEnabled(false);
        assertFalse(offerings.toResponse(offering).emergencyEligible());
    }

    @Test
    void emergencyEligibilityRequiresBothOptInsAndDiscoverableOffering(){
        offering.setEmergencyEnabled(false);
        assertFalse(offerings.toResponse(offering).emergencyEligible());
        offering.setEmergencyEnabled(true);
        category.setStatus(CategoryStatus.INACTIVE);
        assertFalse(offerings.toResponse(offering).emergencyEligible());
        category.setStatus(CategoryStatus.ACTIVE);
        provider.setProviderStatus(ProviderStatus.SUSPENDED);
        assertFalse(offerings.toResponse(offering).emergencyEligible());
        provider.setProviderStatus(ProviderStatus.APPROVED);
        owner.setStatus(UserStatus.INACTIVE);
        assertFalse(offerings.toResponse(offering).emergencyEligible());
    }

    @Test
    void createSavesEmergencyOptInAndOlderRequestsDefaultToFalse(){
        ownProfile();
        when(categories.findById(3L)).thenReturn(Optional.of(category));
        when(services.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        assertTrue(offerings.createServiceOffering(request(true)).isEmergencyEnabled());
        assertFalse(offerings.createServiceOffering(request(null)).isEmergencyEnabled());
    }

    @Test
    void unapprovedProviderCannotCreateService(){
        ownProfile();
        provider.setProviderStatus(ProviderStatus.PENDING);
        assertThrows(UnauthorizedException.class,()->offerings.createServiceOffering(request(true)));
        verify(services,never()).save(any());
    }

    @Test
    void updatePreservesOmittedEmergencySettingAndAcceptsExplicitChanges(){
        ownProfile();
        when(services.findById(21L)).thenReturn(Optional.of(offering));
        when(categories.findById(3L)).thenReturn(Optional.of(category));
        when(services.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        assertTrue(offerings.updateServiceOffering(21L,request(null)).isEmergencyEnabled());
        assertFalse(offerings.updateServiceOffering(21L,request(false)).isEmergencyEnabled());
        assertTrue(offerings.updateServiceOffering(21L,request(true)).isEmergencyEnabled());
    }

    @Test
    void providerCannotEditAnotherProvidersService(){
        ownProfile();
        ProviderProfile other=new ProviderProfile();
        other.setId(99L);
        offering.setProvider(other);
        when(services.findById(21L)).thenReturn(Optional.of(offering));
        assertThrows(UnauthorizedException.class,()->offerings.updateServiceOffering(21L,request(false)));
        assertThrows(UnauthorizedException.class,()->offerings.deactivateServiceOffering(21L));
        verify(services,never()).save(any());
    }

    @Test
    void ownListingUsesAuthenticatedProviderAndIncludesInactiveServices(){
        ownProfile();
        offering.setStatus(ServiceStatus.INACTIVE);
        when(services.findByProviderId(11L,page)).thenReturn(new PageImpl<>(List.of(offering)));
        assertEquals("INACTIVE",offerings.getCurrentProviderServices(page).getContent().get(0).status());
        verify(services).findByProviderId(11L,page);
    }

    @Test
    void browsingAndSearchingShareEligibilityQueryAndProviderFilter(){
        when(services.searchServices(null,3L,11L,null,null,ServiceStatus.ACTIVE,page)).thenReturn(new PageImpl<>(List.of(offering)));
        assertEquals(1,offerings.getAllServiceOffering(3L,11L,page).getTotalElements());
        assertEquals(1,offerings.searchServices(null,3L,11L,null,null,page).getTotalElements());
        verify(services,times(2)).searchServices(null,3L,11L,null,null,ServiceStatus.ACTIVE,page);
    }

    @Test
    void invalidSearchPriceRangeStillFailsBeforeRepositoryAccess(){
        assertThrows(IllegalArgumentException.class,()->offerings.searchServices(null,null,null,50.0,10.0,page));
        assertThrows(IllegalArgumentException.class,()->offerings.searchServices(null,null,null,-1.0,null,page));
        verifyNoInteractions(services);
    }

    @Test
    void hiddenServiceIsUnavailableToOtherUsersButReadableByOwnerAndAdmin(){
        offering.setStatus(ServiceStatus.INACTIVE);
        when(services.findById(21L)).thenReturn(Optional.of(offering));
        User visitor=new User();
        visitor.setId(42L);
        visitor.setRole(Role.HOMEOWNER);
        when(current.getCurrentUser()).thenReturn(visitor);
        assertThrows(InformationNotFoundException.class,()->offerings.getServiceResponseById(21L));
        when(current.getCurrentUser()).thenReturn(owner);
        assertEquals(21L,offerings.getServiceResponseById(21L).id());
        visitor.setRole(Role.ADMIN);
        when(current.getCurrentUser()).thenReturn(visitor);
        assertEquals(21L,offerings.getServiceResponseById(21L).id());
    }

    @Test
    void inactiveCategoryUnapprovedProviderAndInactiveOwnerAreHiddenFromDetails(){
        User visitor=new User();
        visitor.setId(42L);
        when(current.getCurrentUser()).thenReturn(visitor);
        when(services.findById(21L)).thenReturn(Optional.of(offering));
        category.setStatus(CategoryStatus.INACTIVE);
        assertThrows(InformationNotFoundException.class,()->offerings.getServiceResponseById(21L));
        category.setStatus(CategoryStatus.ACTIVE);
        provider.setProviderStatus(ProviderStatus.PENDING);
        assertThrows(InformationNotFoundException.class,()->offerings.getServiceResponseById(21L));
        provider.setProviderStatus(ProviderStatus.APPROVED);
        owner.setStatus(UserStatus.INACTIVE);
        assertThrows(InformationNotFoundException.class,()->offerings.getServiceResponseById(21L));
    }

    @Test
    void providerResponseReturnsStoredPortfolioAndPreferences(){
        when(providerRepository.findById(11L)).thenReturn(Optional.of(provider));
        ProviderResponse result=providers.getProviderDetails(11L);
        assertEquals("Repairs and installations",result.portfolioDescription());
        assertTrue(result.emergencyEnabled());
        assertEquals("APPROVED",result.providerStatus());
    }

    @Test
    void providerDetailsHideUnapprovedAndInactiveAccounts(){
        when(providerRepository.findById(11L)).thenReturn(Optional.of(provider));
        provider.setProviderStatus(ProviderStatus.PENDING);
        assertThrows(InformationNotFoundException.class,()->providers.getProviderDetails(11L));
        provider.setProviderStatus(ProviderStatus.APPROVED);
        owner.setStatus(UserStatus.INACTIVE);
        assertThrows(InformationNotFoundException.class,()->providers.getProviderDetails(11L));
    }

    @Test
    void profileAndPortfolioUpdatesPreserveApprovalAndReuseExistingEntity(){
        ownProfile();
        provider.setProviderStatus(ProviderStatus.PENDING);
        when(providerRepository.save(provider)).thenReturn(provider);
        assertSame(provider,providers.updateProviderProfile(new ProviderRequest("Updated business","Updated description")));
        assertEquals("Updated business",provider.getBusinessName());
        assertSame(provider,providers.updatePortfolio("Updated portfolio",false));
        assertEquals("Updated portfolio",provider.getPortfolioDescription());
        assertFalse(provider.isEmergencyEnabled());
        assertEquals(ProviderStatus.PENDING,provider.getProviderStatus());
        assertThrows(IllegalArgumentException.class,()->providers.updatePortfolio("x".repeat(3001),true));
        verify(providerRepository,times(2)).save(provider);
    }

    @Test
    void existingRequestValidationRemainsInPlace(){
        try(ValidatorFactory factory=Validation.buildDefaultValidatorFactory()){
            assertTrue(factory.getValidator().validate(request(null)).isEmpty());
            assertFalse(factory.getValidator().validate(new ServiceRequest(null,"",null,0.0,0,true)).isEmpty());
            assertFalse(factory.getValidator().validate(new ProviderRequest("",null)).isEmpty());
        }
    }

    @Test
    void adminApplicationReadsReuseStatusFinderAndInheritedLookups(){
        PageRequest requestedPage=PageRequest.of(1,1);
        provider.setProviderStatus(ProviderStatus.PENDING);
        when(providerRepository.findByProviderStatus(ProviderStatus.PENDING,requestedPage))
                .thenReturn(new PageImpl<>(List.of(provider),requestedPage,3));
        when(providerRepository.findAll(requestedPage)).thenReturn(new PageImpl<>(List.of(provider)));
        when(providerRepository.findById(11L)).thenReturn(Optional.of(provider));
        assertEquals(3,providers.getProviderApplications(ProviderStatus.PENDING,requestedPage).getTotalElements());
        assertEquals(1,providers.getProviderApplications(null,requestedPage).getTotalElements());
        assertSame(provider,providers.getProviderApplication(11L));
        assertThrows(InformationNotFoundException.class,()->providers.getProviderApplication(99L));
        verify(providerRepository).findByProviderStatus(ProviderStatus.PENDING,requestedPage);
        verify(providerRepository).findAll(requestedPage);
    }

    @Test
    void existingAdminApprovalStillUpdatesExistingStatusAndAudit(){
        provider.setProviderStatus(ProviderStatus.PENDING);
        User admin=new User();
        admin.setId(99L);
        admin.setRole(Role.ADMIN);
        when(current.getCurrentUser()).thenReturn(admin);
        when(providerRepository.lockById(11L)).thenReturn(Optional.of(provider));
        when(providerRepository.save(provider)).thenReturn(provider);
        assertSame(provider,providers.updateProviderStatus(11L));
        assertEquals(ProviderStatus.APPROVED,provider.getProviderStatus());
        verify(audit).record(admin,"Approve Provider","ProviderProfile",11L,"Admin approved provider");
    }

    private void ownProfile(){
        when(current.getCurrentUser()).thenReturn(owner);
        when(providerRepository.findByUserId(7L)).thenReturn(Optional.of(provider));
    }

    private ServiceRequest request(Boolean emergencyEnabled){
        return new ServiceRequest(3L,"Leak repair","Repair a leak",50.0,60,emergencyEnabled);
    }
}
