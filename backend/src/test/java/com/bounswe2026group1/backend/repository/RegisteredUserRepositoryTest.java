package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import com.bounswe2026group1.backend.support.AbstractPostgisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class RegisteredUserRepositoryTest extends AbstractPostgisIntegrationTest {

    @Autowired
    private RegisteredUserRepository repository;

    @Test
    void searchUsersByNameOrEmail_includesAdminsAndExcludesBannedUsers() {
        RegisteredUser regular = persist("Ada Lovelace", "ada@search.test", UserRole.USER, UserStatus.ACTIVE);
        RegisteredUser admin = persist("Alan Turing", "alan@search.test", UserRole.ADMIN, UserStatus.ACTIVE);
        RegisteredUser banned = persist("Albert Banned", "albert@search.test", UserRole.USER, UserStatus.BANNED);

        Page<RegisteredUser> page = repository.searchUsersByNameOrEmail("search.test", PageRequest.of(0, 20));

        List<Long> ids = page.getContent().stream().map(RegisteredUser::getId).toList();
        assertTrue(ids.contains(regular.getId()));
        assertTrue(ids.contains(admin.getId()), "admin accounts must surface in user search");
        assertFalse(ids.contains(banned.getId()), "banned accounts must not surface");
        assertEquals(2L, page.getTotalElements());
    }

    @Test
    void searchUsersByNameOrEmail_caseInsensitiveSubstringMatchAcrossNameAndEmail() {
        RegisteredUser byName = persist("Grace Hopper", "grace@case.test", UserRole.USER, UserStatus.ACTIVE);
        RegisteredUser byEmail = persist("Other Person", "alan@case.test", UserRole.USER, UserStatus.ACTIVE);
        persist("Unrelated", "u@elsewhere.test", UserRole.USER, UserStatus.ACTIVE);

        Page<RegisteredUser> matchByName = repository.searchUsersByNameOrEmail("GRACE", PageRequest.of(0, 20));
        Page<RegisteredUser> matchByEmail = repository.searchUsersByNameOrEmail("alan@", PageRequest.of(0, 20));

        assertEquals(1L, matchByName.getTotalElements());
        assertEquals(byName.getId(), matchByName.getContent().get(0).getId());

        assertEquals(1L, matchByEmail.getTotalElements());
        assertEquals(byEmail.getId(), matchByEmail.getContent().get(0).getId());
    }

    private RegisteredUser persist(String name, String email, UserRole role, UserStatus status) {
        RegisteredUser u = new RegisteredUser();
        u.setName(name);
        u.setEmail(email);
        u.setPassword("hashedPassword");
        u.setRole(role);
        u.setStatus(status);
        return repository.save(u);
    }
}
