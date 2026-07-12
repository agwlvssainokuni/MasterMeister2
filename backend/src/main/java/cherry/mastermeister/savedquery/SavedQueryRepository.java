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

package cherry.mastermeister.savedquery;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedQueryRepository extends JpaRepository<SavedQuery, Long> {

    @Query("""
            SELECT s FROM SavedQuery s
            WHERE s.connectionId = :connectionId
              AND (s.visibility = cherry.mastermeister.savedquery.Visibility.PUBLIC OR s.ownerId = :userId)
              AND (:includeRetired = true OR s.retired = false)
            ORDER BY s.name
            """)
    List<SavedQuery> findVisible(
            @Param("connectionId") Long connectionId,
            @Param("userId") Long userId,
            @Param("includeRetired") boolean includeRetired
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE SavedQuery s SET s.executionCount = s.executionCount + 1 WHERE s.id = :id")
    int incrementExecutionCount(@Param("id") Long id);

}