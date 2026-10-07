package com.ga.HomeHub.seed;

import com.ga.HomeHub.model.Category;
import com.ga.HomeHub.repository.CategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeed implements CommandLineRunner {

    private final CategoryRepository categoryRepository;

    public DataSeed(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(String... args) {

        if (categoryRepository.count() == 0) {
            addCategory("Plumbing", "Plumbing repair and installation services");
            addCategory("Electrical", "Electrical repair and installation services");
            addCategory("Cleaning", "Home cleaning services");
            addCategory("AC Maintenance", "Air conditioning maintenance and repair");
            addCategory("Painting", "Indoor and outdoor painting services");
        }
    }

    private void addCategory(String name, String description) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);

        categoryRepository.save(category);
    }
}