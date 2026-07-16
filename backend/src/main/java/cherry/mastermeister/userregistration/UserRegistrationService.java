/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cherry.mastermeister.userregistration;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.audit.EventCategory;
import cherry.mastermeister.audit.EventType;
import cherry.mastermeister.audit.Result;
import cherry.mastermeister.common.exception.EntityNotFoundException;
import cherry.mastermeister.mail.MailNotificationType;
import cherry.mastermeister.mail.MailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationService {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final UserRepository userRepository;
    private final RegistrationTokenRepository registrationTokenRepository;
    private final RegistrationTokenService registrationTokenService;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AuditLogService auditLogService;
    private final Duration tokenExpiry;
    private final String frontendBaseUrl;

    public UserRegistrationService(
            UserRepository userRepository,
            RegistrationTokenRepository registrationTokenRepository,
            RegistrationTokenService registrationTokenService,
            PasswordEncoder passwordEncoder,
            MailService mailService,
            AuditLogService auditLogService,
            @Value("${mm.app.user-registration.token-expiry:3h}") Duration tokenExpiry,
            @Value("${mm.app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        this.userRepository = userRepository;
        this.registrationTokenRepository = registrationTokenRepository;
        this.registrationTokenService = registrationTokenService;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
        this.tokenExpiry = tokenExpiry;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public void requestRegistration(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }
        Instant now = Instant.now();
        registrationTokenRepository
                .findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull(email)
                .ifPresent(existing -> existing.invalidate(now));

        String plainToken = registrationTokenService.issueToken(email, tokenExpiry);
        mailService.send(MailNotificationType.REGISTRATION_CONFIRMATION, email, Map.of(
                "recipientName", email,
                "linkUrl", frontendBaseUrl + "/register/complete?token=" + plainToken,
                "expiryDateTime", EXPIRY_FORMAT.format(now.plus(tokenExpiry))
        ));
    }

    @Transactional
    public void completeRegistration(String token, String rawPassword) {
        RegistrationToken registrationToken = registrationTokenService.resolveValid(token);
        Instant now = Instant.now();
        User user = new User(
                registrationToken.getEmail(),
                passwordEncoder.encode(rawPassword),
                Role.USER,
                UserStatus.PENDING_APPROVAL,
                now,
                null
        );
        userRepository.save(user);
        registrationToken.consume(now);
    }

    @Transactional
    public void approveUser(Long adminUserId, Long targetUserId) {
        User user = requirePendingUser(targetUserId);
        user.approve(Instant.now());
        mailService.send(MailNotificationType.REGISTRATION_APPROVED, user.getEmail(), Map.of(
                "recipientName", user.getEmail(),
                "linkUrl", frontendBaseUrl + "/login"
        ));
        auditLogService.record(
                EventCategory.ADMIN_OPERATION, EventType.USER_REGISTRATION_APPROVED, adminUserId, null,
                Result.SUCCESS, user.getEmail(), "User registration approved: userId=" + targetUserId);
    }

    @Transactional
    public void rejectUser(Long adminUserId, Long targetUserId) {
        User user = requirePendingUser(targetUserId);
        user.reject(Instant.now());
        mailService.send(MailNotificationType.REGISTRATION_REJECTED, user.getEmail(), Map.of(
                "recipientName", user.getEmail()
        ));
        auditLogService.record(
                EventCategory.ADMIN_OPERATION, EventType.USER_REGISTRATION_REJECTED, adminUserId, null,
                Result.SUCCESS, user.getEmail(), "User registration rejected: userId=" + targetUserId);
    }

    public List<PendingUserSummary> listPendingUsers() {
        return userRepository.findByStatusOrderByCreatedAtAsc(UserStatus.PENDING_APPROVAL).stream()
                .map(user -> new PendingUserSummary(user.getId(), user.getEmail(), user.getCreatedAt()))
                .toList();
    }

    public List<UserAccountSummary> listApprovedUsers() {
        return userRepository.findByStatusOrderByEmailAsc(UserStatus.APPROVED).stream()
                .map(user -> new UserAccountSummary(user.getId(), user.getEmail()))
                .toList();
    }

    private User requirePendingUser(Long targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: id=" + targetUserId));
        if (user.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new InvalidUserStateException("User is not pending approval: id=" + targetUserId);
        }
        return user;
    }

}