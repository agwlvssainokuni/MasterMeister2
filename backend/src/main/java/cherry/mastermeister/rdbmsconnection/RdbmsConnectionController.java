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

package cherry.mastermeister.rdbmsconnection;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rdbms-connections")
public class RdbmsConnectionController {

    private final RdbmsConnectionService rdbmsConnectionService;
    private final ConnectionAccessService connectionAccessService;

    public RdbmsConnectionController(
            RdbmsConnectionService rdbmsConnectionService, ConnectionAccessService connectionAccessService) {
        this.rdbmsConnectionService = rdbmsConnectionService;
        this.connectionAccessService = connectionAccessService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Long createConnection(@RequestBody ConnectionConfig config, Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        return rdbmsConnectionService.createConnection(adminUserId, config);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateConnection(
            @PathVariable Long id, @RequestBody ConnectionConfig config, Authentication authentication) {
        Long adminUserId = (Long) authentication.getPrincipal();
        rdbmsConnectionService.updateConnection(adminUserId, id, config);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<ConnectionSummary> listConnections() {
        return rdbmsConnectionService.listConnections();
    }

    @GetMapping("/{id}")
    public ConnectionDetail getConnection(@PathVariable Long id) {
        return rdbmsConnectionService.getConnection(id);
    }

    @PostMapping("/test")
    public ConnectionTestResult testConnection(@RequestBody ConnectionConfig config) {
        return rdbmsConnectionService.testConnection(config);
    }

    @PostMapping("/{id}/test")
    public ConnectionTestResult testConnection(@PathVariable Long id) {
        return rdbmsConnectionService.testConnection(id);
    }

    @GetMapping("/accessible")
    public List<ConnectionSummary> listAccessibleConnections(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return connectionAccessService.listAccessibleConnections(userId);
    }

}