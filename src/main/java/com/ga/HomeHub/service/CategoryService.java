package com.ga.HomeHub.service;

import com.ga.HomeHub.dto.CategoryRequest;
import com.ga.HomeHub.exception.InformationNotFoundException;
import com.ga.HomeHub.model.Category;
import com.ga.HomeHub.model.enums.CategoryStatus;
import com.ga.HomeHub.repository.CategoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class CategoryService {
    private final CategoryRepository repository;

    public Category createCategory(CategoryRequest request){
        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        return repository.save(category);
    }

    public Page<Category> getAllCategories(Pageable p){
        return repository.findAll(p);
    }

    public Category getCategoryById(Long id){
        return repository.findById(id).orElseThrow(()-> new InformationNotFoundException("Category not found"));
    }

    public Category updateCategory(Long id, CategoryRequest request){
        Category category = getCategoryById(id);
        category.setName(request.name());
        category.setDescription(request.description());
        return repository.save(category);
    }

    public void deleteCategory(Long id){
        Category category = getCategoryById(id);
        category.setStatus(CategoryStatus.INACTIVE);
        repository.save(category);
    }
}
