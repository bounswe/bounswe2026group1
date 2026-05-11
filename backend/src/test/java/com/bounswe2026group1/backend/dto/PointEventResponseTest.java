package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.PointEvent;
import com.bounswe2026group1.backend.model.PointReason;
import com.bounswe2026group1.backend.model.RegisteredUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PointEventResponseTest {

    @Test
    void fromEntity_copiesAllFields() {
        Instant when = Instant.parse("2026-01-15T10:00:00Z");
        PointEvent event = new PointEvent(new RegisteredUser(), -5, PointReason.VOTE_OPPOSED, 1024L);
        ReflectionTestUtils.setField(event, "id", 7001L);
        event.setCreatedAt(when);

        PointEventResponse dto = PointEventResponse.fromEntity(event);

        assertEquals(7001L, dto.id());
        assertEquals(-5, dto.delta());
        assertEquals(PointReason.VOTE_OPPOSED, dto.reason());
        assertEquals(1024L, dto.relatedEntityId());
        assertEquals(when, dto.createdAt());
    }

    @Test
    void fromEntity_passesThroughNullRelatedEntityId() {
        PointEvent event = new PointEvent(new RegisteredUser(), 10, PointReason.REPORT_SUBMIT, null);
        event.setCreatedAt(Instant.now());

        PointEventResponse dto = PointEventResponse.fromEntity(event);

        assertNull(dto.relatedEntityId());
        assertEquals(10, dto.delta());
        assertEquals(PointReason.REPORT_SUBMIT, dto.reason());
    }
}
