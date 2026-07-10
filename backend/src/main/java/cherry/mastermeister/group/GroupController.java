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

package cherry.mastermeister.group;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Long createGroup(@RequestBody GroupCreateRequest request, Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        return groupService.createGroup(adminUserId, request.name());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> renameGroup(
            @PathVariable Long id, @RequestBody GroupRenameRequest request, Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        groupService.renameGroup(adminUserId, id, request.name());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id, Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        groupService.deleteGroup(adminUserId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<GroupSummary> listGroups() {
        return groupService.listGroups();
    }

    @GetMapping("/{id}/members")
    public List<UserSummary> listGroupMembers(@PathVariable Long id) {
        return groupService.listGroupMembers(id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public void addUserToGroup(
            @PathVariable Long id, @RequestBody GroupMemberAddRequest request, Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        groupService.addUserToGroup(adminUserId, id, request.userId());
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeUserFromGroup(
            @PathVariable Long id, @PathVariable Long userId, Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        groupService.removeUserFromGroup(adminUserId, id, userId);
        return ResponseEntity.noContent().build();
    }

}