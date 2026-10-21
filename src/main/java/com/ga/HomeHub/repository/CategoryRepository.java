package com.ga.HomeHub.repository;

import com.ga.HomeHub.model.Category;
import com.ga.HomeHub.model.enums.CategoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category,Long> {
    Page<Category> findByStatus(CategoryStatus status, Pageable pageable);
}
