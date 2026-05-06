package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Notification;
import com.bounswe2026group1.backend.model.NotificationType;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private RegisteredUserRepository registeredUserRepository;

    private RegisteredUser alice;
    private RegisteredUser bob;

    @BeforeEach
    void setUp() {
        alice = persistUser("alice@example.com", "Alice");
        bob = persistUser("bob@example.com", "Bob");
    }

    @Test
    void findByRecipientIdOrderByCreatedAtDesc_returnsOnlyRecipientNotificationsNewestFirst() throws InterruptedException {
        Notification first = persistNotification(alice, "first", NotificationType.NEW_COMMENT);
        Thread.sleep(5);
        Notification second = persistNotification(alice, "second", NotificationType.STATUS_CHANGE);
        persistNotification(bob, "bob's", NotificationType.NEW_COMMENT);

        List<Notification> aliceList = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(alice.getId());

        assertEquals(2, aliceList.size());
        assertEquals(second.getId(), aliceList.get(0).getId());
        assertEquals(first.getId(), aliceList.get(1).getId());
    }

    @Test
    void countByRecipientIdAndIsReadFalse_countsOnlyUnreadNotifications() {
        Notification unread = persistNotification(alice, "unread", NotificationType.NEW_COMMENT);
        Notification read = persistNotification(alice, "read", NotificationType.STATUS_CHANGE);
        read.setRead(true);
        notificationRepository.save(read);

        long unreadCount = notificationRepository.countByRecipientIdAndIsReadFalse(alice.getId());

        assertEquals(1, unreadCount);
        assertEquals(unread.getId(), notificationRepository.findById(unread.getId()).orElseThrow().getId());
    }

    private RegisteredUser persistUser(String email, String name) {
        RegisteredUser user = new RegisteredUser();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("StrongP@ss1");
        user.setRole(UserRole.USER);
        return registeredUserRepository.save(user);
    }

    private Notification persistNotification(RegisteredUser recipient, String message, NotificationType type) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setMessage(message);
        n.setType(type);
        return notificationRepository.save(n);
    }
}
