package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.LoginRequest;
import com.bounswe2026group1.backend.dto.LoginResponse;
import com.bounswe2026group1.backend.dto.RegisterRequest;
import com.bounswe2026group1.backend.dto.RegisterResponse;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RegisteredUserService {


    private final RegisteredUserRepository registeredUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // Password strength regex pattern: Minimum 8 characters, at least one uppercase letter, one lowercase letter, one digit and one special character
    private static final String PASSWORD_PATTERN = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$";


    public RegisterResponse registerUser(RegisterRequest request) {
        // 1. Check Uniqueness
        if (registeredUserRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use.");
        }

        // 2. Password strength validation
        if (!request.getPassword().matches(PASSWORD_PATTERN)) {
            throw new IllegalArgumentException("Password must be at least 8 characters long, and include an uppercase letter, a lowercase letter, a digit, and a special character.");
        }

        // 3. User Entity Conversion & Password Hashing
        RegisteredUser user = new RegisteredUser();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER"); // Default role assignment

        // 4. Save to Database
        RegisteredUser savedUser = registeredUserRepository.save(user);

        // 5. Return Response (excluding password)
        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    public LoginResponse loginUser(LoginRequest request) {
        // 1. Find user by email
        RegisteredUser user = registeredUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // 2. Check password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // 3. Generate JWT token
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());

        // 4. Return token in response
        return new LoginResponse(token);
    }

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
