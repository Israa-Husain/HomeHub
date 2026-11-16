package com.ga.HomeHub.booking.repository;

import com.ga.HomeHub.booking.model.User;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

//Booking reads existing accounts. Auth manages account changes.
@Transactional(readOnly=true)
public interface UserRepository extends Repository<User,Long>{
    Optional<User> findById(Long id);
    User findUserByEmailAddress(String emailAddress);
    long count();
}