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

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import cherry.mastermeister.common.dialect.DialectStrategyFactory;
import cherry.mastermeister.common.exception.EntityNotFoundException;

@Component
public class ConnectionPoolRegistry {

    private final RdbmsConnectionRepository rdbmsConnectionRepository;
    private final DialectStrategyFactory dialectStrategyFactory;
    private final int maximumPoolSize;
    private final int minimumIdle;
    private final Duration connectionTimeout;

    private final ConcurrentHashMap<Long, HikariDataSource> dataSources = new ConcurrentHashMap<>();

    public ConnectionPoolRegistry(
            RdbmsConnectionRepository rdbmsConnectionRepository,
            DialectStrategyFactory dialectStrategyFactory,
            @Value("${mm.app.rdbms-connection.pool.maximum-pool-size:5}") int maximumPoolSize,
            @Value("${mm.app.rdbms-connection.pool.minimum-idle:0}") int minimumIdle,
            @Value("${mm.app.rdbms-connection.pool.connection-timeout:5s}") Duration connectionTimeout
    ) {
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
        this.dialectStrategyFactory = dialectStrategyFactory;
        this.maximumPoolSize = maximumPoolSize;
        this.minimumIdle = minimumIdle;
        this.connectionTimeout = connectionTimeout;
    }

    public DataSource getDataSource(Long connectionId) {
        return dataSources.computeIfAbsent(connectionId, this::createDataSource);
    }

    public NamedParameterJdbcTemplate getJdbcTemplate(Long connectionId) {
        return new NamedParameterJdbcTemplate(getDataSource(connectionId));
    }

    public void invalidate(Long connectionId) {
        HikariDataSource dataSource = dataSources.remove(connectionId);
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private HikariDataSource createDataSource(Long connectionId) {
        RdbmsConnection connection = rdbmsConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new EntityNotFoundException("RdbmsConnection not found: " + connectionId));

        String baseUrl = dialectStrategyFactory.resolve(connection.getRdbmsType())
                .buildJdbcUrl(connection.getHost(), connection.getPort(), connection.getDatabaseName());
        String additionalParams = connection.getAdditionalParams();
        String jdbcUrl = (additionalParams == null || additionalParams.isBlank())
                ? baseUrl
                : baseUrl + "?" + additionalParams;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(connection.getUsername());
        config.setPassword(connection.getPassword());
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout.toMillis());
        return new HikariDataSource(config);
    }

}