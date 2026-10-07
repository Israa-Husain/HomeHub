package com.ga.HomeHub.controller;

import com.ga.HomeHub.dto.CategoryRequest;
import com.ga.HomeHub.model.Category;
import com.ga.HomeHub.service.CategoryService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public Page<Category> getAllCategories(Pageable pageable){
        return categoryService.getAllCategories(pageable);
    }

    @GetMapping("/{categoryId}")
    public Category getCategoryById(@PathVariable Long categoryId){
        return categoryService.getCategoryById(categoryId);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Category> createCategory(@Valid @RequestBody CategoryRequest request){
        return ResponseEntity.status(201).body(categoryService.createCategory(request));
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Category updateCategory(@PathVariable Long categoryId, @Valid @RequestBody CategoryRequest request){
        return categoryService.updateCategory(categoryId,request);
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long categoryId){
        categoryService.deleteCategory(categoryId);
    }
}
