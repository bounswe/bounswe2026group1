package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.LoginRequest;
import com.bounswe2026group1.backend.dto.LoginResponse;
import com.bounswe2026group1.backend.dto.RegisterRequest;
import com.bounswe2026group1.backend.dto.RegisterResponse;
import com.bounswe2026group1.backend.dto.UpdateProfileRequest;
import com.bounswe2026group1.backend.dto.UserProfileDTO;
import com.bounswe2026group1.backend.model.Badge;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.model.UserStatus;
import com.bounswe2026group1.backend.dto.PointEventResponse;
import com.bounswe2026group1.backend.repository.PointEventRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.repository.RouteRepository;
import com.bounswe2026group1.backend.repository.UserBadgeRepository;
import com.bounswe2026group1.backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RegisteredUserService {


    private final RegisteredUserRepository registeredUserRepository;
    private final ReportRepository reportRepository;
    private final RouteRepository routeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final PointEventRepository pointEventRepository;
    private final LeaderboardService leaderboardService;
    private final S3MediaService s3MediaService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RegisteredUserService.class);

    // Password strength regex pattern: Minimum 8 characters, at least one uppercase letter, one lowercase letter, one digit and one special character
    private static final String PASSWORD_PATTERN = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$";


    public RegisterResponse registerUser(RegisterRequest request) {
        // 1. Check Uniqueness
        if (registeredUserRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use.");
        }

        // 2. Password strength validation
        if (!request.getPassword().matches(PASSWORD_PATTERN)) {
            throw new IllegalArgumentException("Password must be at least 8 characters long, and include an uppercase letter, a lowercase letter, a digit, and a special character.");
        }

        // 3. User Entity Conversion & Password Hashing
        RegisteredUser user = new RegisteredUser();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.USER);
        user.setStatus(com.bounswe2026group1.backend.model.UserStatus.ACTIVE);

        // 4. Save to Database
        RegisteredUser savedUser = registeredUserRepository.save(user);

        // 5. Return Response (excluding password)
        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole().name()
        );
    }

    public LoginResponse loginUser(LoginRequest request) {
        // 1. Find user by email
        RegisteredUser user = registeredUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // 2. Check password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // 3. Generate JWT token
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        // 4. Return token + profile in response
        return new LoginResponse(token, toProfileDTO(user, true));
    }

    public List<RegisteredUser> getAll() {
        return registeredUserRepository.findAll();
    }

    public Optional<RegisteredUser> getById(Long id) {
        return registeredUserRepository.findById(id);
    }

    public RegisteredUser create(RegisteredUser user) {
        return registeredUserRepository.save(user);
    }

    public boolean delete(Long id) {
        if (!registeredUserRepository.existsById(id)) return false;
        registeredUserRepository.deleteById(id);
        return true;
    }

    /**
     * Anonymizes a user in place so FK references from their reports/comments/votes
     * stay valid while PII is erased. Shared by self-delete (1.1.1.2.12) and admin-delete (1.1.1.3.8).
     *
     * Clears: name, email, password, bio, avatarUrl (and deletes the underlying S3 object
     * when present); sets status to BANNED. S3 errors are logged but don't abort the
     * anonymization — once the user-facing fields are cleared we never want to roll back
     * the deletion just because the photo was missing or unreachable.
     */
    @Transactional
    public void anonymizeUser(RegisteredUser target) {
        String oldAvatarUrl = target.getAvatarUrl();
        target.setName("Deleted User");
        target.setEmail("deleted_" + target.getId() + "@deleted.invalid");
        target.setPassword("$DELETED$");
        target.setBio(null);
        target.setAvatarUrl(null);
        target.setStatus(UserStatus.BANNED);
        registeredUserRepository.save(target);

        if (oldAvatarUrl != null && !oldAvatarUrl.isBlank()) {
            try {
                s3MediaService.deleteFile(oldAvatarUrl);
            } catch (Exception e) {
                log.warn("Failed to delete avatar S3 object during anonymization of user {}: {}",
                        target.getId(), e.getMessage());
            }
        }
    }

    /**
     * Self-delete (1.1.1.2.12). Looks up the caller by email and anonymizes them.
     * If the caller is the only remaining admin, refuses with 409.
     */
    @Transactional
    public void deleteSelf(String callerEmail) {
        RegisteredUser target = registeredUserRepository.findByEmail(callerEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found with email: " + callerEmail));

        if (target.getRole() == UserRole.ADMIN
                && registeredUserRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete the last admin");
        }
        anonymizeUser(target);
    }

    // ───── Profile (issue #302) ─────────────────────────────────────────────

    /** Public listing — uses batched count queries so this stays at a small
     *  fixed query count regardless of user size, instead of N+1. Rank is
     *  intentionally null for list views: filling it in would require a
     *  count-above query per user, and admin/listing UIs only need points. */
    public List<UserProfileDTO> getAllProfiles() {
        List<RegisteredUser> users = registeredUserRepository.findAll();
        if (users.isEmpty()) return List.of();

        List<Long> ids = users.stream().map(RegisteredUser::getId).toList();
        Map<Long, Long> reportCounts = toCountMap(reportRepository.countByCreatedByIdIn(ids));
        Map<Long, Long> routeCounts = toCountMap(routeRepository.countByCreatedByIdIn(ids));
        Map<Long, List<Badge>> badgesByUser = toBadgeMap(userBadgeRepository.findBadgesByUserIds(ids));

        return users.stream()
                .map(u -> buildDTO(u, false,
                        reportCounts.getOrDefault(u.getId(), 0L),
                        routeCounts.getOrDefault(u.getId(), 0L),
                        badgesByUser.getOrDefault(u.getId(), List.of()),
                        null))
                .toList();
    }

    /** User search (#306 / #501): paginated, case-insensitive substring match
     *  across name OR email. Empty / blank query is rejected with
     *  IllegalArgumentException so the controller can return 400.
     *  Email is omitted in the DTO (privacy) — search results are public. */
    public Page<UserProfileDTO> searchUsers(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query must not be empty.");
        }
        String trimmed = query.trim();
        Page<RegisteredUser> page = registeredUserRepository
                .searchRegularUsersByNameOrEmail(trimmed, pageable);
        if (page.isEmpty()) return page.map(u -> buildDTO(u, false, 0L, 0L, List.of(), null));

        // Batch the contribution-stats queries so we stay flat at 3 queries
        // total regardless of page size — same pattern as getAllProfiles.
        List<Long> ids = page.getContent().stream().map(RegisteredUser::getId).toList();
        Map<Long, Long> reportCounts = toCountMap(reportRepository.countByCreatedByIdIn(ids));
        Map<Long, Long> routeCounts = toCountMap(routeRepository.countByCreatedByIdIn(ids));
        Map<Long, List<Badge>> badgesByUser = toBadgeMap(userBadgeRepository.findBadgesByUserIds(ids));
        return page.map(u -> buildDTO(u, false,
                reportCounts.getOrDefault(u.getId(), 0L),
                routeCounts.getOrDefault(u.getId(), 0L),
                badgesByUser.getOrDefault(u.getId(), List.of()),
                null));
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>(rows.size());
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    private static Map<Long, List<Badge>> toBadgeMap(List<Object[]> rows) {
        Map<Long, List<Badge>> map = new HashMap<>();
        for (Object[] row : rows) {
            map.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((Badge) row[1]);
        }
        return map;
    }

    public UserProfileDTO getProfileById(Long id) {
        RegisteredUser user = registeredUserRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));
        return toProfileDTO(user, false);
    }

    public UserProfileDTO getProfileByEmail(String email) {
        RegisteredUser user = registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found with email: " + email));
        return toProfileDTO(user, true);
    }

    public UserProfileDTO updateProfile(Long id, UpdateProfileRequest request) {
        RegisteredUser user = registeredUserRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));

        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        RegisteredUser saved = registeredUserRepository.save(user);
        return toProfileDTO(saved, true);
    }

    public UserProfileDTO setAvatar(Long id, String avatarUrl) {
        RegisteredUser user = registeredUserRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));
        user.setAvatarUrl(avatarUrl);
        RegisteredUser saved = registeredUserRepository.save(user);
        return toProfileDTO(saved, true);
    }

    /** Toggles the user's leaderboard opt-out flag. Their accumulated points
     *  stay intact — they re-enter the ranking instantly when this flips back. */
    public UserProfileDTO setLeaderboardHidden(Long id, boolean hidden) {
        RegisteredUser user = registeredUserRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));
        user.setLeaderboardHidden(hidden);
        RegisteredUser saved = registeredUserRepository.save(user);
        return toProfileDTO(saved, true);
    }

    /** Toggles the user's voice-command accessibility mode (1.1.3.8). Persisted
     *  per user so the web app can restore the choice on every login. */
    public UserProfileDTO setVoiceCommandsEnabled(Long id, boolean enabled) {
        RegisteredUser user = registeredUserRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + id));
        user.setVoiceCommandsEnabled(enabled);
        RegisteredUser saved = registeredUserRepository.save(user);
        return toProfileDTO(saved, true);
    }

    /** All badges currently held by a user — exposed as its own endpoint so
     *  the public profile page can fetch badges without going through the
     *  full profile DTO. */
    public List<Badge> getBadges(Long id) {
        if (!registeredUserRepository.existsById(id)) {
            throw new NoSuchElementException("User not found with id: " + id);
        }
        return userBadgeRepository.findByUserId(id).stream()
                .map(ub -> ub.getBadge())
                .toList();
    }

    /** Paginated points-history view (most recent first). Surfaced through
     *  the points history endpoint, gated to the user themselves and admins. */
    public Page<PointEventResponse> listPointEvents(Long userId, Pageable pageable) {
        if (!registeredUserRepository.existsById(userId)) {
            throw new NoSuchElementException("User not found with id: " + userId);
        }
        return pointEventRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(PointEventResponse::fromEntity);
    }

    // includeEmail=true is reserved for self-views (/me, login, owner-only mutations).
    // Public lookups must pass false to avoid leaking another user's email.
    private UserProfileDTO toProfileDTO(RegisteredUser user, boolean includeEmail) {
        long reports = reportRepository.countByCreatedById(user.getId());
        long routes = routeRepository.countByCreatedById(user.getId());
        List<Badge> badges = userBadgeRepository.findByUserId(user.getId()).stream()
                .map(ub -> ub.getBadge())
                .toList();
        Integer rank = leaderboardService.getRankFor(user.getId())
                .map(RankInfo::rank)
                .orElse(null);
        return buildDTO(user, includeEmail, reports, routes, badges, rank);
    }

    private UserProfileDTO buildDTO(RegisteredUser user,
                                    boolean includeEmail,
                                    long reports,
                                    long routes,
                                    List<Badge> badges,
                                    Integer rank) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(includeEmail ? user.getEmail() : null)
                .bio(user.getBio())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name())
                .contributionStats(UserProfileDTO.ContributionStatsDTO.builder()
                        .reportsSubmitted(reports)
                        .routesPlanned(routes)
                        .build())
                .points(user.getPoints())
                .rank(rank)
                .badges(badges)
                .topBadge(Badge.pickHighestTier(badges))
                .leaderboardHidden(user.isLeaderboardHidden())
                .voiceCommandsEnabled(user.isVoiceCommandsEnabled())
                .build();
    }
}
