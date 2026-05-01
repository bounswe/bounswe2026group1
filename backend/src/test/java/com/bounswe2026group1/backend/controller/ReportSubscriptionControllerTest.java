package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.SubscriptionStatusResponse;
import com.bounswe2026group1.backend.model.ReportSubscription;
import com.bounswe2026group1.backend.service.ReportSubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportSubscriptionControllerTest {

    @Mock
    private ReportSubscriptionService subscriptionService;

    @InjectMocks
    private ReportSubscriptionController controller;

    private static final String EMAIL = "alice@example.com";

    @Test
    void subscribe_returns201WithSubscribedTrue() {
        when(subscriptionService.subscribe(100L, EMAIL)).thenReturn(new ReportSubscription());

        ResponseEntity<SubscriptionStatusResponse> response = controller.subscribe(100L, EMAIL);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().subscribed());
    }

    @Test
    void unsubscribe_returns200WithSubscribedFalse() {
        ResponseEntity<SubscriptionStatusResponse> response = controller.unsubscribe(100L, EMAIL);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().subscribed());
        verify(subscriptionService).unsubscribe(100L, EMAIL);
    }

    @Test
    void isSubscribed_reflectsServiceAnswer() {
        when(subscriptionService.isSubscribed(100L, EMAIL)).thenReturn(true);

        ResponseEntity<SubscriptionStatusResponse> response = controller.isSubscribed(100L, EMAIL);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().subscribed());
    }

    @Test
    void subscribe_returns401WhenPrincipalMissing() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.subscribe(100L, null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void unsubscribe_returns401WhenPrincipalMissing() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.unsubscribe(100L, ""));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
