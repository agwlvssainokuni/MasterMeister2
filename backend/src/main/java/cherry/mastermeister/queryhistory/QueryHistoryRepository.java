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

package cherry.mastermeister.queryhistory;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {

    @Query("""
            SELECT h FROM QueryHistory h
            WHERE h.connectionId = :connectionId
              AND (:executedAtFrom IS NULL OR h.executedAt >= :executedAtFrom)
              AND (:executedAtTo IS NULL OR h.executedAt <= :executedAtTo)
              AND (:userId IS NULL OR h.userId = :userId)
              AND (:sqlTextSearch IS NULL OR LOWER(CAST(h.sql AS string)) LIKE LOWER(CONCAT('%', :sqlTextSearch, '%')))
            """)
    Page<QueryHistory> search(
            @Param("connectionId") Long connectionId,
            @Param("executedAtFrom") Instant executedAtFrom,
            @Param("executedAtTo") Instant executedAtTo,
            @Param("userId") Long userId,
            @Param("sqlTextSearch") String sqlTextSearch,
            Pageable pageable
    );

}