package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.NotificationResponse;
import com.bounswe2026group1.backend.model.NotificationType;
import com.bounswe2026group1.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/** Unit-level tests for the notification controller. The controller is a thin
 *  passthrough to {@link NotificationService}, so we exercise its branching
 *  directly rather than wiring up a full WebMvcTest with Spring Security. */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController controller;

    private static final String EMAIL = "alice@example.com";

    @Test
    void list_delegatesToServiceWithAuthenticatedEmail() {
        NotificationResponse n = new NotificationResponse(
                7L, 1L, NotificationType.NEW_COMMENT, "Bob commented", 100L, false, LocalDateTime.now());
        when(notificationService.listForUser(EMAIL)).thenReturn(List.of(n));

        ResponseEntity<List<NotificationResponse>> response = controller.list(EMAIL);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(7L, response.getBody().get(0).getId());
    }

    @Test
    void list_returns401WhenPrincipalIsMissing() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.list(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void list_returns401WhenPrincipalIsBlank() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.list("   "));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void markRead_delegatesAndReturnsUpdatedDto() {
        NotificationResponse n = new NotificationResponse(
                9L, 1L, NotificationType.STATUS_CHANGE, "verified", 100L, true, LocalDateTime.now());
        when(notificationService.markAsRead(9L, EMAIL)).thenReturn(n);

        ResponseEntity<NotificationResponse> response = controller.markRead(9L, EMAIL);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isRead());
    }

    @Test
    void markRead_propagatesForbidden() {
        when(notificationService.markAsRead(9L, EMAIL))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your notification"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.markRead(9L, EMAIL));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void markRead_propagatesNotFound() {
        when(notificationService.markAsRead(9L, EMAIL))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.markRead(9L, EMAIL));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void markRead_returns401WhenPrincipalMissing() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.markRead(9L, null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
