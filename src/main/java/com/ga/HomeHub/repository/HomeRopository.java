package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.Home;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface HomeRopository extends JpaRepository<Home,Long> {
    Page<Home> findByOwnerIdAndActiveTrue(Long ownerId, Pageable pageable);
}
