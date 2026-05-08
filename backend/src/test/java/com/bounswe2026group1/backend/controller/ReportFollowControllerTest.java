package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.FollowStatusResponse;
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
class ReportFollowControllerTest {

    @Mock
    private ReportSubscriptionService subscriptionService;

    @InjectMocks
    private ReportFollowController controller;

    private static final String EMAIL = "alice@example.com";

    @Test
    void follow_returns201WithFollowingTrue() {
        when(subscriptionService.subscribe(100L, EMAIL)).thenReturn(new ReportSubscription());

        ResponseEntity<FollowStatusResponse> response = controller.follow(100L, EMAIL);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().following());
    }

    @Test
    void unfollow_returns200WithFollowingFalse() {
        ResponseEntity<FollowStatusResponse> response = controller.unfollow(100L, EMAIL);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().following());
        verify(subscriptionService).unsubscribe(100L, EMAIL);
    }

    @Test
    void isFollowing_reflectsServiceAnswer() {
        when(subscriptionService.isSubscribed(100L, EMAIL)).thenReturn(true);

        ResponseEntity<FollowStatusResponse> response = controller.isFollowing(100L, EMAIL);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().following());
    }

    @Test
    void follow_returns401WhenPrincipalMissing() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.follow(100L, null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void unfollow_returns401WhenPrincipalMissing() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.unfollow(100L, ""));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
