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
    void searchUsersByName_includesAdminsAndExcludesBannedUsers() {
        RegisteredUser regular = persist("Search Regular", UserRole.USER, UserStatus.ACTIVE);
        RegisteredUser admin = persist("Search Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        RegisteredUser banned = persist("Search Banned", UserRole.USER, UserStatus.BANNED);

        Page<RegisteredUser> page = repository.searchUsersByName("search", PageRequest.of(0, 20));

        List<Long> ids = page.getContent().stream().map(RegisteredUser::getId).toList();
        assertTrue(ids.contains(regular.getId()));
        assertTrue(ids.contains(admin.getId()), "admin accounts must surface in user search");
        assertFalse(ids.contains(banned.getId()), "banned accounts must not surface");
        assertEquals(2L, page.getTotalElements());
    }

    @Test
    void searchUsersByName_caseInsensitiveSubstringMatch() {
        RegisteredUser hit = persist("Grace Hopper", UserRole.USER, UserStatus.ACTIVE);
        persist("Unrelated Person", UserRole.USER, UserStatus.ACTIVE);

        Page<RegisteredUser> page = repository.searchUsersByName("GRACE", PageRequest.of(0, 20));

        assertEquals(1L, page.getTotalElements());
        assertEquals(hit.getId(), page.getContent().get(0).getId());
    }

    private RegisteredUser persist(String name, UserRole role, UserStatus status) {
        RegisteredUser u = new RegisteredUser();
        u.setName(name);
        u.setEmail(name.toLowerCase().replace(' ', '.') + "@search.test");
        u.setPassword("hashedPassword");
        u.setRole(role);
        u.setStatus(status);
        return repository.save(u);
    }
}
