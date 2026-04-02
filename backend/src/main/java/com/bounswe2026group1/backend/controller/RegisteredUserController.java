package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.service.RegisteredUserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class RegisteredUserController {

    private static final Logger logger = LoggerFactory.getLogger(RegisteredUserController.class);

    private final RegisteredUserService registeredUserService;

    @GetMapping
    public List<RegisteredUser> getAll() {
        logger.info("GET /api/users - fetching all users");
        return registeredUserService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegisteredUser> getById(@PathVariable Long id) {
        logger.info("GET /api/users/{} - fetching user by id", id);
        return registeredUserService.getById(id)
                .map(u -> {
                    logger.info("User found with id: {} - 200 OK", id);
                    return ResponseEntity.ok(u);
                })
                .orElseGet(() -> {
                    logger.warn("User not found with id: {} - 404 NOT FOUND", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PostMapping
    public RegisteredUser create(@RequestBody RegisteredUser user) {
        logger.info("POST /api/users - creating new user");
        RegisteredUser created = registeredUserService.create(user);
        logger.info("User created with id: {} - 200 OK", created.getId());
        return created;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        logger.info("DELETE /api/users/{} - deleting user", id);
        if (registeredUserService.delete(id)) {
            logger.info("User deleted with id: {} - 204 NO CONTENT", id);
            return ResponseEntity.noContent().build();
        }
        logger.warn("User not found for deletion with id: {} - 404 NOT FOUND", id);
        return ResponseEntity.notFound().build();
    }
}
