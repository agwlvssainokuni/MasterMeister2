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

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "registration_token")
public class RegistrationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Column(unique = true, nullable = false)
    private String tokenHash;

    private Instant expiresAt;

    private Instant invalidatedAt;

    private Instant consumedAt;

    private Instant createdAt;

    protected RegistrationToken() {
    }

    public RegistrationToken(
            String email,
            String tokenHash,
            Instant expiresAt,
            Instant invalidatedAt,
            Instant consumedAt,
            Instant createdAt
    ) {
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.invalidatedAt = invalidatedAt;
        this.consumedAt = consumedAt;
        this.createdAt = createdAt;
    }

    public void invalidate(Instant invalidatedAt) {
        this.invalidatedAt = invalidatedAt;
    }

    public void consume(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}