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

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final UserRegistrationService userRegistrationService;

    public RegistrationController(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @PostMapping
    public ResponseEntity<Void> requestRegistration(@RequestBody RequestRegistrationRequest request) {
        userRegistrationService.requestRegistration(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> completeRegistration(@RequestBody CompleteRegistrationRequest request) {
        userRegistrationService.completeRegistration(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pending")
    public List<PendingUserSummary> listPendingUsers() {
        return userRegistrationService.listPendingUsers();
    }

    @GetMapping("/approved")
    public List<UserAccountSummary> listApprovedUsers() {
        return userRegistrationService.listApprovedUsers();
    }

    @PostMapping("/{userId}/approve")
    public ResponseEntity<Void> approveUser(@PathVariable Long userId, Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        userRegistrationService.approveUser(adminUserId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/reject")
    public ResponseEntity<Void> rejectUser(@PathVariable Long userId, Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        userRegistrationService.rejectUser(adminUserId, userId);
        return ResponseEntity.noContent().build();
    }

}