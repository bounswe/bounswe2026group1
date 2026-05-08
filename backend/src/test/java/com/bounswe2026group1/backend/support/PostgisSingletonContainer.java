package com.bounswe2026group1.backend.support;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One shared PostGIS instance for persistence tests ({@literal geometry(Point,4326)} is not usable on plain H2).
 */
public final class PostgisSingletonContainer {

    static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static {
        INSTANCE.start();
    }

    private PostgisSingletonContainer() {}
}
