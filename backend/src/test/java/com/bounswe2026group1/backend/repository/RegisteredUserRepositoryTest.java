package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.RegisteredUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    @DisplayName("searchByName: partial match across all rows")
    void partialMatch_returnsAllContaining() {
        Page<RegisteredUser> page = registeredUserRepository
                .searchByName("ada", PageRequest.of(0, 20));

        assertEquals(2, page.getTotalElements(), "both Ada rows must match");
        assertTrue(page.getContent().stream().allMatch(u -> u.getName().toLowerCase().contains("ada")));
    }

    @Test
    @DisplayName("searchByName: case-insensitive — uppercase query matches lowercase data")
    void caseInsensitive_uppercaseMatchesLowercase() {
        Page<RegisteredUser> page = registeredUserRepository
                .searchByName("ADA", PageRequest.of(0, 20));

        assertEquals(2, page.getTotalElements());
    }

    @Test
    @DisplayName("searchByName: empty query matches every user, alphabetically by name")
    void emptyQuery_matchesAllAlphabetically() {
        Page<RegisteredUser> page = registeredUserRepository
                .searchByName("", PageRequest.of(0, 20));

        assertEquals(4, page.getTotalElements());
        // Empty query → every row ranks 0, so ORDER BY LOWER(name) takes over.
        // Don't assert exact ordering between "Ada Lovelace" and "ada (lower)" —
        // they collide on the "ada" prefix and the rest is collation-dependent.
        // We can still pin the positions of the two non-"a" names.
        List<String> names = page.getContent().stream().map(RegisteredUser::getName).toList();
        assertTrue(names.get(0).toLowerCase().startsWith("ada"), "slot 0 must be one of the ada* rows: " + names);
        assertTrue(names.get(1).toLowerCase().startsWith("ada"), "slot 1 must be one of the ada* rows: " + names);
        assertEquals("Alan Turing",  names.get(2));
        assertEquals("Grace Hopper", names.get(3));
    }

    @Test
    @DisplayName("searchByName: prefix matches rank above mere-contains")
    void prefixMatchesRankAboveContains() {
        // "Suzan User" CONTAINS 'a' but does not START with it.
        // "carol" CONTAINS 'a' but does not start with it.
        // "Ada Lovelace" / "ada (lower)" / "Alan Turing" all START with 'a' (case-insensitive).
        persist("Suzan User", "suzan@test.com");
        persist("carol",      "carol@test.com");

        Page<RegisteredUser> page = registeredUserRepository
                .searchByName("a", PageRequest.of(0, 20));

        List<String> names = page.getContent().stream().map(RegisteredUser::getName).toList();

        // The first three slots must all be prefix matches; the contains-only
        // names must be pushed to the tail of the list.
        assertEquals(6, names.size());
        assertTrue(names.get(0).toLowerCase().startsWith("a"), "slot 0 must be a prefix match: " + names);
        assertTrue(names.get(1).toLowerCase().startsWith("a"), "slot 1 must be a prefix match: " + names);
        assertTrue(names.get(2).toLowerCase().startsWith("a"), "slot 2 must be a prefix match: " + names);
        assertFalse(names.get(4).toLowerCase().startsWith("a"), "slot 4 must be a contains-only: " + names);
        assertFalse(names.get(5).toLowerCase().startsWith("a"), "slot 5 must be a contains-only: " + names);

        // Within the prefix group, alphabetical (case-insensitive) by name.
        // The two "ada*" rows are tied on "ada" and the rest of the ordering
        // depends on the DB's collation (space vs punctuation), so don't pin
        // their relative positions. "Alan Turing" must come last in the
        // prefix group regardless.
        assertTrue(names.get(0).toLowerCase().startsWith("ada"), "slot 0: " + names);
        assertTrue(names.get(1).toLowerCase().startsWith("ada"), "slot 1: " + names);
        assertEquals("Alan Turing", names.get(2));
    }

    @Test
    @DisplayName("searchByName: pagination splits results across pages")
    void pagination_splitsResults() {
        // seed 25 more so we have a clean 29 rows total (4 seeded + 25 bulk)
        for (int i = 0; i < 25; i++) {
            persist("BulkUser-" + String.format("%02d", i), "bulk-" + i + "@test.com");
        }
        long total = registeredUserRepository.count();
        assertEquals(29, total, "test starts from 4 seeded rows + 25 bulk = 29");

        Page<RegisteredUser> page0 = registeredUserRepository
                .searchByName("", PageRequest.of(0, 10));
        assertEquals(10, page0.getNumberOfElements());
        assertEquals(3, page0.getTotalPages(), "29 rows / 10 per page = 3 pages");
        assertEquals(29L, page0.getTotalElements());
        assertTrue(page0.isFirst());
        assertFalse(page0.isLast());

        Page<RegisteredUser> page2 = registeredUserRepository
                .searchByName("", PageRequest.of(2, 10));
        assertEquals(9, page2.getNumberOfElements(), "last page holds the remainder");
        assertTrue(page2.isLast());
    }

    @Test
    @DisplayName("searchByName: no match returns an empty page, not null")
    void noMatch_returnsEmptyPage() {
        Page<RegisteredUser> page = registeredUserRepository
                .searchByName("zzz_no_such_name_zzz", PageRequest.of(0, 20));

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
