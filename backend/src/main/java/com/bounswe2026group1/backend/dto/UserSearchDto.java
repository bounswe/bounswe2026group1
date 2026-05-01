package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.RegisteredUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchDto {
    private Long id;
    private String name;
    private String avatarUrl;
    private long contributionCount;

    public static UserSearchDto fromEntity(RegisteredUser user, long contributionCount) {
        return new UserSearchDto(
                user.getId(),
                user.getName(),
                user.getAvatarUrl(),
                contributionCount
        );
    }
}
