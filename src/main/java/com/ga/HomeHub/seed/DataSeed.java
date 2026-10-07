package com.ga.HomeHub.seed;

import com.ga.HomeHub.model.Category;
import com.ga.HomeHub.model.User;
import com.ga.HomeHub.model.enums.Role;
import com.ga.HomeHub.repository.CategoryRepository;
import com.ga.HomeHub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSeed implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${MAIL_USERNAME}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;



    @Override
    public void run(String... args) {

        if (categoryRepository.count() == 0) {
            addCategory("Plumbing", "Plumbing repair and installation services");
            addCategory("Electrical", "Electrical repair and installation services");
            addCategory("Cleaning", "Home cleaning services");
            addCategory("AC Maintenance", "Air conditioning maintenance and repair");
            addCategory("Painting", "Indoor and outdoor painting services");
        }

        if (!userRepository.existsByEmailAddress(adminEmail)) {
            addAdmin();
        }
    }

    private void addCategory(String name, String description) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        categoryRepository.save(category);
    }

    private void addAdmin() {
        User admin = new User();

        admin.setFirstName("HomeHub");
        admin.setLastName("Admin");
        admin.setEmailAddress(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setPhoneNumber("33170000");
        admin.setRole(Role.ADMIN);
        admin.setEmailVerified(true);

        userRepository.save(admin);
    }
}