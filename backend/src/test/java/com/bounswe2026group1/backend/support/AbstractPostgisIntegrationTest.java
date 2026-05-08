package com.bounswe2026group1.backend.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Wired datasource + Hibernate dialect so JPA can map {@code GEOMETRY(Point,4326)}.
 */
@ActiveProfiles("test")
public abstract class AbstractPostgisIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGIS = PostgisSingletonContainer.INSTANCE;

    @DynamicPropertySource
    static void postgisDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGIS::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGIS::getUsername);
        registry.add("spring.datasource.password", POSTGIS::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform",
                () -> "org.hibernate.spatial.dialect.postgis.PostgisPG10Dialect");
    }
}
