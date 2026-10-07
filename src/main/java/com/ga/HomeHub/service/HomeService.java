package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.HomeRequest;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.exception.UnauthorizedException;
import com.ga.HomeHub.model.Home;
import com.ga.HomeHub.repository.HomeRopository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class HomeService {
    private final HomeRopository repository;
    private final CurrentUserService current;

    public Home createHome(HomeRequest request){
        Home home = new Home();
        home.setOwner(current.getCurrentUser());
        home.setName(request.name());
        home.setCity(request.city());
        home.setAddress(request.address());
        return repository.save(home);
    }

    public Page<Home> getCurrentUserHomes(Pageable p){
        return repository.findByOwnerIdAndActiveTrue(current.getCurrentUser().getId(), p);
    }

    public Home getHomeById(Long id){
        Home home = repository.findById(id).orElseThrow(()-> new InformationNotFoundException("Home not found"));
        if(!home.isActive()){
            throw new InformationNotFoundException("Home not found");
        }
        if(!home.getOwner().getId().equals(current.getCurrentUser().getId())){
            throw new UnauthorizedException("This home does not belong to you");
        }
        return home;
    }

    public Home updateHome(Long id, HomeRequest request){
        Home home = getHomeById(id);
        home.setName(request.name());
        home.setCity(request.city());
        home.setAddress(request.address());
        return repository.save(home);
    }

    public void deleteHome(Long id){
        Home home = getHomeById(id);
        home.setActive(false);
        repository.save(home);
    }
}
