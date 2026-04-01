package com.bounswe2026group1.backend.config;

import com.bounswe2026group1.backend.exception.RoutingException;
import com.bounswe2026group1.backend.service.OrsHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class OpenRouteServiceConfig {

    private static final String ORS_BASE_URL = "https://api.openrouteservice.org";

    @Bean
    public OrsHttpClient orsHttpClient(@Value("${ors.api.key}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(30_000);

        RestClient restClient = RestClient.builder()
                .baseUrl(ORS_BASE_URL)
                .requestFactory(requestFactory)
                .build();

        return (profile, requestBody) -> restClient.post()
                .uri("/v2/directions/{profile}/json", profile)
                .header(HttpHeaders.AUTHORIZATION, apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    String snippet = "";
                    try {
                        var stream = response.getBody();
                        if (stream != null) {
                            byte[] body = stream.readAllBytes();
                            if (body.length > 0) {
                                snippet = new String(body, StandardCharsets.UTF_8);
                            }
                        }
                    } catch (IOException e) {
                        throw new RoutingException("Failed to read OpenRouteService error response", e);
                    }
                    throw new RoutingException(
                            "OpenRouteService HTTP %s: %s".formatted(response.getStatusCode(), snippet));
                })
                .body(String.class);
    }
}
