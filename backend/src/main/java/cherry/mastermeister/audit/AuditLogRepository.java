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

package cherry.mastermeister.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:#{#criteria.dateFrom} IS NULL OR a.occurredAt >= :#{#criteria.dateFrom})
              AND (:#{#criteria.dateTo} IS NULL OR a.occurredAt <= :#{#criteria.dateTo})
              AND (:#{#criteria.userId} IS NULL OR a.userId = :#{#criteria.userId})
              AND (:#{#criteria.eventCategory} IS NULL OR a.eventCategory = :#{#criteria.eventCategory})
              AND (:#{#criteria.eventType} IS NULL OR a.eventType = :#{#criteria.eventType})
            """)
    Page<AuditLog> search(@Param("criteria") AuditLogFilterCriteria criteria, Pageable pageable);

}