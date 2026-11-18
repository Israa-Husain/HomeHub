package com.ga.HomeHub.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ga.HomeHub.booking.cache.CatalogCache;
import com.ga.HomeHub.booking.dto.CategoryRequest;
import com.ga.HomeHub.booking.exception.InformationNotFoundException;
import com.ga.HomeHub.booking.model.Category;
import com.ga.HomeHub.booking.model.enums.CategoryStatus;
import com.ga.HomeHub.booking.repository.CategoryRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;

@Service
@AllArgsConstructor
@Transactional
public class CategoryService{
    private static final Logger logger=LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository repository;
    private final CurrentUserService current;
    private final AuditLogService audit;
    private final CatalogCache cache;

    //Register Java time support for the category timestamps.
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();

    @PreAuthorize("hasRole('ADMIN')")
    public Category createCategory(CategoryRequest request){
        Category category=new Category();
        category.setName(request.name());
        category.setDescription(request.description());

        Category savedCategory=repository.save(category);
        audit.record(current.getCurrentUser(),"Create Category","Category",
                savedCategory.getId(),"Admin Created Category "+savedCategory.getName());

        cache.invalidateAfterCommit();
        return savedCategory;
    }

    @Transactional(readOnly=true)
    public Page<Category> getAllCategories(Pageable p){
        return repository.findByStatus(CategoryStatus.ACTIVE,p);
    }

    //Cache category reads while keeping booking validation in PostgreSQL.
    @Transactional(readOnly=true)
    public Category getCategoryById(Long id){
        try{
            String json=cache.read("category:"+id,()->{
                Category category=findActiveCategory(id);
                try{
                    return mapper.writeValueAsString(category);
                }catch(JsonProcessingException e){
                    throw new UncheckedIOException(e);
                }
            });

            Category category=mapper.readValue(json,Category.class);

            //Ignore malformed or unsuitable cached entries.
            if(category!=null && id.equals(category.getId())
                    && category.getStatus()==CategoryStatus.ACTIVE){
                return category;
            }
        }catch(JsonProcessingException | UncheckedIOException e){
            logger.warn("Could not process cached category; reading from database");
        }

        return findActiveCategory(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Category updateCategory(Long id,CategoryRequest request){
        //Load the managed database entity rather than a detached cached copy.
        Category category=repository.findById(id)
                .orElseThrow(()->new InformationNotFoundException("Category not found"));

        category.setName(request.name());
        category.setDescription(request.description());

        Category savedCategory=repository.save(category);
        audit.record(current.getCurrentUser(),"Update Category","Category",
                savedCategory.getId(),"Admin Updated Category "+savedCategory.getName());

        cache.invalidateAfterCommit();
        return savedCategory;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteCategory(Long id){
        Category category=repository.findById(id)
                .orElseThrow(()->new InformationNotFoundException("Category not found"));

        category.setStatus(CategoryStatus.INACTIVE);
        repository.save(category);
        audit.record(current.getCurrentUser(),"Deactivate Category","Category",
                category.getId(),"Admin Deactivated Category "+category.getName());

        cache.invalidateAfterCommit();
    }

    private Category findActiveCategory(Long id){
        return repository.findById(id)
                .filter(category->category.getStatus()==CategoryStatus.ACTIVE)
                .orElseThrow(()->new InformationNotFoundException("Category not found"));
    }
}