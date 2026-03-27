package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RegisteredUserService {

    private final RegisteredUserRepository registeredUserRepository;

    public List<RegisteredUser> getAll() {
        return registeredUserRepository.findAll();
    }

    public Optional<RegisteredUser> getById(Long id) {
        return registeredUserRepository.findById(id);
    }

    public RegisteredUser create(RegisteredUser user) {
        return registeredUserRepository.save(user);
    }

    public boolean delete(Long id) {
        if (!registeredUserRepository.existsById(id)) return false;
        registeredUserRepository.deleteById(id);
        return true;
    }
}
