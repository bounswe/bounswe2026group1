package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.service.RegisteredUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class RegisteredUserController {

    private final RegisteredUserService registeredUserService;

    @GetMapping
    public List<RegisteredUser> getAll() {
        return registeredUserService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegisteredUser> getById(@PathVariable Long id) {
        return registeredUserService.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public RegisteredUser create(@RequestBody RegisteredUser user) {
        return registeredUserService.create(user);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (registeredUserService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
