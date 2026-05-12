package com.bounswe2026group1.backend;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers {@link BackendApplication#main(String[])} without actually starting the
 * Spring context, by stubbing the static {@code SpringApplication.run(...)} call.
 */
class BackendApplicationMainTest {

    @Test
    void main_delegatesToSpringApplicationRun() {
        String[] args = {"--spring.profiles.active=test"};

        try (MockedStatic<SpringApplication> mocked = mockStatic(SpringApplication.class)) {
            mocked.when(() -> SpringApplication.run(any(Class.class), any(String[].class)))
                    .thenReturn(null);

            BackendApplication.main(args);

            mocked.verify(() -> SpringApplication.run(eq(BackendApplication.class), eq(args)));
        }
    }
}
