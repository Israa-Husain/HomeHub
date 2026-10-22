package com.ga.HomeHub.service;

import com.ga.HomeHub.exception.BookingConflictException;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.model.ServiceFavorite;
import com.ga.HomeHub.model.ServiceOffering;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.repository.ServiceFavoriteRepository;
import com.ga.HomeHub.repository.ServiceOfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceFavoriteService {
    private final ServiceFavoriteRepository favorites;
    private final ServiceOfferingRepository services;
    private final CurrentUserService current;

    //Allow homeowner to save services for future booking.
    @Transactional
    public ServiceFavorite addFavorite(Long serviceId){
        User user = current.getCurrentUser();
        ServiceOffering service = services.findById(serviceId).orElseThrow(()-> new InformationNotFoundException("Service not found"));

        //prevent the same homeowner from saving a service more than once.
        if(favorites.existsByHomeownerIdAndServiceId(user.getId(),serviceId)){
            throw new BookingConflictException("Service already saved");
        }

        ServiceFavorite favorite = new ServiceFavorite();
        favorite.setHomeowner(user);
        favorite.setService(service);
        return favorites.save(favorite);
    }

    //Retrieve all services saved by the current homeowner.
    @Transactional(readOnly = true)
    public List<ServiceFavorite> getMyFavorite(){
        return favorites.findByHomeownerId(current.getCurrentUser().getId());
    }

    //Remove a favorite
    @Transactional
    public void removeFavorite(Long favoriteId){
        ServiceFavorite favorite = favorites.findByIdAndHomeownerId(favoriteId, current.getCurrentUser().getId()).orElseThrow(()-> new InformationNotFoundException("Favorite not found"));
        favorites.delete(favorite);
    }
}
