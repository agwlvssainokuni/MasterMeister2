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

import cherry.mastermeister.security.OpaqueTokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationTokenService {

    private final RegistrationTokenRepository registrationTokenRepository;
    private final OpaqueTokenGenerator opaqueTokenGenerator;

    public RegistrationTokenService(
            RegistrationTokenRepository registrationTokenRepository,
            OpaqueTokenGenerator opaqueTokenGenerator
    ) {
        this.registrationTokenRepository = registrationTokenRepository;
        this.opaqueTokenGenerator = opaqueTokenGenerator;
    }

    @Transactional
    public String issueToken(String email, Duration expiry) {
        String plainToken = opaqueTokenGenerator.generate();
        String tokenHash = opaqueTokenGenerator.hash(plainToken);
        Instant now = Instant.now();
        RegistrationToken registrationToken = new RegistrationToken(
                email, tokenHash, now.plus(expiry), null, null, now);
        registrationTokenRepository.save(registrationToken);
        return plainToken;
    }

    @Transactional
    public RegistrationTokenStatus validate(String token) {
        return registrationTokenRepository.findByTokenHash(opaqueTokenGenerator.hash(token))
                .map(this::resolveStatus)
                .orElse(RegistrationTokenStatus.NOT_FOUND);
    }

    @Transactional
    public RegistrationToken resolveValid(String token) {
        RegistrationToken registrationToken = registrationTokenRepository
                .findByTokenHash(opaqueTokenGenerator.hash(token))
                .orElseThrow(() -> new TokenNotFoundException("Registration token not found"));
        if (resolveStatus(registrationToken) != RegistrationTokenStatus.VALID) {
            throw new TokenExpiredException("Registration token is expired or already used");
        }
        return registrationToken;
    }

    private RegistrationTokenStatus resolveStatus(RegistrationToken registrationToken) {
        if (registrationToken.getInvalidatedAt() != null
                || registrationToken.getConsumedAt() != null
                || registrationToken.getExpiresAt().isBefore(Instant.now())) {
            return RegistrationTokenStatus.EXPIRED;
        }
        return RegistrationTokenStatus.VALID;
    }

}