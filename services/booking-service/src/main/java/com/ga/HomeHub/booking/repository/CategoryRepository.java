package com.ga.HomeHub.booking.repository;

import com.ga.HomeHub.booking.model.Category;
import com.ga.HomeHub.booking.model.enums.CategoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category,Long> {
    Page<Category> findByStatus(CategoryStatus status, Pageable pageable);
}
