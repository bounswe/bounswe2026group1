package com.bounswe2026group1.backend.dto.admin;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportVerification;
import com.bounswe2026group1.backend.model.VoteType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminValidationResponseTest {

    @Test
    void fromEntity_copiesIdsNameAndVoteType() {
        RegisteredUser user = new RegisteredUser();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setName("Alice Example");

        Report report = new Report();
        ReflectionTestUtils.setField(report, "reportId", 1024L);

        ReportVerification rv = new ReportVerification(user, report, VoteType.AGREE);
        ReflectionTestUtils.setField(rv, "id", 7001L);

        AdminValidationResponse dto = AdminValidationResponse.fromEntity(rv);

        assertEquals(7001L, dto.getId());
        assertEquals(42L, dto.getUserId());
        assertEquals("Alice Example", dto.getUserName());
        assertEquals(1024L, dto.getReportId());
        assertEquals(VoteType.AGREE, dto.getVoteType());
    }
}
