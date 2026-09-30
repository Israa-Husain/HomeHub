package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    //for registration
    boolean existsByEmailAddress(String emailAddress);
    //for login
    User findUserByEmailAddress(String emailAddress);
}
