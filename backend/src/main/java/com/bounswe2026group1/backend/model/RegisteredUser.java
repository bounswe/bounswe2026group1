package com.bounswe2026group1.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "registered_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredUser {
    // Will be filled by others
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
