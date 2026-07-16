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

package cherry.mastermeister.permission;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rdbms-connections/{connectionId}/permissions")
public class PermissionController {

    private final PermissionAssignmentService permissionAssignmentService;

    public PermissionController(PermissionAssignmentService permissionAssignmentService) {
        this.permissionAssignmentService = permissionAssignmentService;
    }

    @GetMapping
    public PermissionLookupResponse lookupPermission(
            @PathVariable Long connectionId,
            @RequestParam PrincipalType principalType,
            @RequestParam Long principalId,
            @RequestParam String schema,
            @RequestParam Optional<String> table,
            @RequestParam Optional<String> column) {
        PrincipalRef principal = new PrincipalRef(principalType, principalId);
        return permissionAssignmentService.lookupPermission(principal, connectionId, schema, table, column);
    }

    @PutMapping
    public ResponseEntity<Void> updatePermission(
            @PathVariable Long connectionId, @RequestBody PermissionUpdateRequest request,
            Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        if (request.permission().isPresent()) {
            permissionAssignmentService.setPermission(
                    adminUserId, request.principal(), connectionId, request.schema(),
                    request.table(), request.column(), request.permission().get());
        } else {
            permissionAssignmentService.setAuxPermission(
                    adminUserId, request.principal(), connectionId, request.schema(),
                    request.table(), request.auxType().get(), request.granted().get());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportPermissions(
            @PathVariable Long connectionId, Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        byte[] content = permissionAssignmentService.exportPermissionsAsYaml(adminUserId, connectionId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=permissions-" + connectionId + ".yaml")
                .body(content);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importPermissions(
            @PathVariable Long connectionId, @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return permissionAssignmentService.importPermissionsFromYaml(adminUserId, connectionId, content);
    }

}