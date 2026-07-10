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

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import cherry.mastermeister.common.dialect.RdbmsType;

@Entity
@Table(name = "rdbms_connection")
public class RdbmsConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RdbmsType rdbmsType;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(nullable = false)
    private String databaseName;

    @Column(nullable = false)
    private String username;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false)
    private String password;

    private String additionalParams;

    private Instant createdAt;

    private Instant updatedAt;

    protected RdbmsConnection() {
    }

    public RdbmsConnection(
            String name,
            RdbmsType rdbmsType,
            String host,
            Integer port,
            String databaseName,
            String username,
            String password,
            String additionalParams,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.name = name;
        this.rdbmsType = rdbmsType;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
        this.additionalParams = additionalParams;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void update(
            String name,
            RdbmsType rdbmsType,
            String host,
            Integer port,
            String databaseName,
            String username,
            String password,
            String additionalParams,
            Instant updatedAt
    ) {
        this.name = name;
        this.rdbmsType = rdbmsType;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
        this.additionalParams = additionalParams;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RdbmsType getRdbmsType() {
        return rdbmsType;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getAdditionalParams() {
        return additionalParams;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}