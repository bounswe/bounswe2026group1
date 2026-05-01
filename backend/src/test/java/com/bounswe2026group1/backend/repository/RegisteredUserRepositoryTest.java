package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.RegisteredUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class RegisteredUserRepositoryTest {

    @Autowired
    private RegisteredUserRepository registeredUserRepository;

    @BeforeEach
    void seed() {
        persist("Ada Lovelace", "ada@test.com");
        persist("Alan Turing",  "alan@test.com");
        persist("Grace Hopper", "grace@test.com");
        persist("ada (lower)",  "lower@test.com"); // duplicate-ish to test case-insensitivity
    }

    @Test
    @DisplayName("findByNameContainingIgnoreCase: partial match across all rows")
    void partialMatch_returnsAllContaining() {
        Page<RegisteredUser> page = registeredUserRepository
                .findByNameContainingIgnoreCase("ada", PageRequest.of(0, 20));

        assertEquals(2, page.getTotalElements(), "both Ada rows must match");
        assertTrue(page.getContent().stream().allMatch(u -> u.getName().toLowerCase().contains("ada")));
    }

    @Test
    @DisplayName("findByNameContainingIgnoreCase: case-insensitive — uppercase query matches lowercase data")
    void caseInsensitive_uppercaseMatchesLowercase() {
        Page<RegisteredUser> page = registeredUserRepository
                .findByNameContainingIgnoreCase("ADA", PageRequest.of(0, 20));

        assertEquals(2, page.getTotalElements());
    }

    @Test
    @DisplayName("findByNameContainingIgnoreCase: empty query matches every user")
    void emptyQuery_matchesAll() {
        Page<RegisteredUser> page = registeredUserRepository
                .findByNameContainingIgnoreCase("", PageRequest.of(0, 20));

        assertEquals(4, page.getTotalElements());
    }

    @Test
    @DisplayName("findByNameContainingIgnoreCase: pagination splits results across pages")
    void pagination_splitsResults() {
        // seed 25 more so we have a clean 30 rows total
        for (int i = 0; i < 25; i++) {
            persist("BulkUser-" + i, "bulk-" + i + "@test.com");
        }
        long total = registeredUserRepository.count();
        assertEquals(29, total, "test starts from 4 seeded rows + 25 bulk = 29");

        Page<RegisteredUser> page0 = registeredUserRepository
                .findByNameContainingIgnoreCase("", PageRequest.of(0, 10, Sort.by("id")));
        assertEquals(10, page0.getNumberOfElements());
        assertEquals(3, page0.getTotalPages(), "29 rows / 10 per page = 3 pages");
        assertEquals(29L, page0.getTotalElements());
        assertTrue(page0.isFirst());
        assertFalse(page0.isLast());

        Page<RegisteredUser> page2 = registeredUserRepository
                .findByNameContainingIgnoreCase("", PageRequest.of(2, 10, Sort.by("id")));
        assertEquals(9, page2.getNumberOfElements(), "last page holds the remainder");
        assertTrue(page2.isLast());
    }

    @Test
    @DisplayName("findByNameContainingIgnoreCase: no match returns an empty page, not null")
    void noMatch_returnsEmptyPage() {
        Page<RegisteredUser> page = registeredUserRepository
                .findByNameContainingIgnoreCase("zzz_no_such_name_zzz", PageRequest.of(0, 20));

        assertNotNull(page);
        assertEquals(0, page.getTotalElements());
        assertEquals(List.of(), page.getContent());
    }

    private RegisteredUser persist(String name, String email) {
        RegisteredUser u = new RegisteredUser();
        u.setName(name);
        u.setEmail(email);
        u.setPassword("hashedPassword");
        u.setRole("USER");
        return registeredUserRepository.save(u);
    }
}
