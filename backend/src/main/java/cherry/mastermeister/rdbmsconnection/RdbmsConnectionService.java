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

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cherry.mastermeister.audit.AuditLogService;
import cherry.mastermeister.audit.EventCategory;
import cherry.mastermeister.audit.EventType;
import cherry.mastermeister.audit.Result;
import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.exception.EntityNotFoundException;

@Service
public class RdbmsConnectionService {

    private final RdbmsConnectionRepository rdbmsConnectionRepository;
    private final DialectStrategyFactory dialectStrategyFactory;
    private final ConnectionPoolRegistry connectionPoolRegistry;
    private final AuditLogService auditLogService;
    private final Duration connectionTimeout;

    public RdbmsConnectionService(
            RdbmsConnectionRepository rdbmsConnectionRepository,
            DialectStrategyFactory dialectStrategyFactory,
            ConnectionPoolRegistry connectionPoolRegistry,
            AuditLogService auditLogService,
            @Value("${mm.app.rdbms-connection.pool.connection-timeout:5s}") Duration connectionTimeout
    ) {
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
        this.dialectStrategyFactory = dialectStrategyFactory;
        this.connectionPoolRegistry = connectionPoolRegistry;
        this.auditLogService = auditLogService;
        this.connectionTimeout = connectionTimeout;
    }

    @Transactional
    public Long createConnection(Long adminUserId, ConnectionConfig config) {
        Instant now = Instant.now();
        RdbmsConnection connection = new RdbmsConnection(
                config.name(), config.rdbmsType(), config.host(), config.port(),
                config.databaseName(), config.username(), config.password(),
                config.additionalParams(), now, now);
        rdbmsConnectionRepository.save(connection);
        auditLogService.record(
                EventCategory.ADMIN_OPERATION, EventType.RDBMS_CONNECTION_CHANGED, adminUserId,
                connection.getId(), Result.SUCCESS, config.name(),
                "RDBMS connection created: id=" + connection.getId());
        return connection.getId();
    }

    @Transactional
    public void updateConnection(Long adminUserId, Long connectionId, ConnectionConfig config) {
        RdbmsConnection connection = requireConnection(connectionId);
        String password = config.password() == null || config.password().isEmpty()
                ? connection.getPassword()
                : config.password();
        connection.update(
                config.name(), config.rdbmsType(), config.host(), config.port(),
                config.databaseName(), config.username(), password,
                config.additionalParams(), Instant.now());
        connectionPoolRegistry.invalidate(connectionId);
        auditLogService.record(
                EventCategory.ADMIN_OPERATION, EventType.RDBMS_CONNECTION_CHANGED, adminUserId,
                connectionId, Result.SUCCESS, config.name(),
                "RDBMS connection updated: id=" + connectionId);
    }

    public ConnectionTestResult testConnection(ConnectionConfig config) {
        String baseUrl = dialectStrategyFactory.resolve(config.rdbmsType())
                .buildJdbcUrl(config.host(), config.port(), config.databaseName());
        String additionalParams = config.additionalParams();
        String jdbcUrl = (additionalParams == null || additionalParams.isBlank())
                ? baseUrl
                : baseUrl + "?" + additionalParams;

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setConnectionTimeout(connectionTimeout.toMillis());

        try (HikariDataSource dataSource = new HikariDataSource(hikariConfig)) {
            try (Connection ignored = dataSource.getConnection()) {
                return new ConnectionTestResult(true, "Connection succeeded.");
            }
        } catch (SQLException e) {
            return new ConnectionTestResult(false, e.getMessage());
        }
    }

    public ConnectionTestResult testConnection(Long connectionId) {
        RdbmsConnection connection = requireConnection(connectionId);
        ConnectionConfig config = new ConnectionConfig(
                connection.getName(), connection.getRdbmsType(), connection.getHost(), connection.getPort(),
                connection.getDatabaseName(), connection.getUsername(), connection.getPassword(),
                connection.getAdditionalParams());
        return testConnection(config);
    }

    public List<ConnectionSummary> listConnections() {
        return rdbmsConnectionRepository.findAll().stream()
                .map(c -> new ConnectionSummary(c.getId(), c.getName(), c.getRdbmsType(), c.getHost(), c.getDatabaseName()))
                .toList();
    }

    public ConnectionDetail getConnection(Long connectionId) {
        RdbmsConnection connection = requireConnection(connectionId);
        return new ConnectionDetail(
                connection.getId(), connection.getName(), connection.getRdbmsType(), connection.getHost(),
                connection.getPort(), connection.getDatabaseName(), connection.getUsername(),
                connection.getAdditionalParams());
    }

    private RdbmsConnection requireConnection(Long connectionId) {
        return rdbmsConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new EntityNotFoundException("RdbmsConnection not found: id=" + connectionId));
    }

}