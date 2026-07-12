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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cherry.mastermeister.common.exception.EntityNotFoundException;
import cherry.mastermeister.common.exception.PermissionDeniedException;

@Service
public class SavedQueryService {

    private final SavedQueryRepository savedQueryRepository;

    public SavedQueryService(SavedQueryRepository savedQueryRepository) {
        this.savedQueryRepository = savedQueryRepository;
    }

    @Transactional
    public Long saveQuery(Long userId, Long connectionId, String name, String sql, Visibility visibility) {
        Instant now = Instant.now();
        SavedQuery entity = new SavedQuery(userId, connectionId, name, sql, visibility, false, 0, now, now);
        return savedQueryRepository.save(entity).getId();
    }

    public SavedQueryDetail getQuery(Long userId, Long savedQueryId) {
        SavedQuery entity = findExisting(savedQueryId);
        checkVisible(userId, entity);
        return toDetail(entity);
    }

    public SavedQueryDetail getExecutableQuery(Long userId, Long savedQueryId) {
        SavedQuery entity = findExisting(savedQueryId);
        if (entity.isRetired()) {
            throw new EntityNotFoundException("SavedQuery not found: " + savedQueryId);
        }
        checkVisible(userId, entity);
        return toDetail(entity);
    }

    @Transactional
    public void updateQuery(Long userId, Long savedQueryId, String name, String sql, Visibility visibility) {
        SavedQuery entity = findExisting(savedQueryId);
        if (entity.isRetired()) {
            throw new EntityNotFoundException("SavedQuery not found: " + savedQueryId);
        }
        if (!entity.getOwnerId().equals(userId)) {
            throw new PermissionDeniedException("Not the owner of saved query: " + savedQueryId);
        }
        entity.update(name, sql, visibility, Instant.now());
    }

    @Transactional
    public void retireQuery(Long userId, Long savedQueryId) {
        SavedQuery entity = findExisting(savedQueryId);
        if (!entity.getOwnerId().equals(userId)) {
            throw new PermissionDeniedException("Not the owner of saved query: " + savedQueryId);
        }
        entity.retire(Instant.now());
    }

    public List<SavedQuerySummary> listQueries(Long userId, Long connectionId, boolean includeRetired) {
        return savedQueryRepository.findVisible(connectionId, userId, includeRetired).stream()
                .map(e -> new SavedQuerySummary(e.getId(), e.getName(), e.getVisibility(), e.isRetired(), e.getOwnerId()))
                .toList();
    }

    public Map<Long, SavedQueryStatus> getStatuses(Long viewerId, Set<Long> savedQueryIds) {
        if (savedQueryIds.isEmpty()) {
            return Map.of();
        }
        return savedQueryRepository.findAllById(savedQueryIds).stream()
                .collect(Collectors.toMap(
                        SavedQuery::getId,
                        e -> new SavedQueryStatus(
                                e.getVisibility() == Visibility.PUBLIC || e.getOwnerId().equals(viewerId),
                                e.isRetired())));
    }

    @Transactional
    public int incrementExecutionCount(Long savedQueryId) {
        int updated = savedQueryRepository.incrementExecutionCount(savedQueryId);
        if (updated == 0) {
            throw new EntityNotFoundException("SavedQuery not found: " + savedQueryId);
        }
        return findExisting(savedQueryId).getExecutionCount();
    }

    private SavedQuery findExisting(Long savedQueryId) {
        return savedQueryRepository.findById(savedQueryId)
                .orElseThrow(() -> new EntityNotFoundException("SavedQuery not found: " + savedQueryId));
    }

    private void checkVisible(Long userId, SavedQuery entity) {
        if (entity.getVisibility() == Visibility.PRIVATE && !entity.getOwnerId().equals(userId)) {
            throw new PermissionDeniedException("Not visible: " + entity.getId());
        }
    }

    private SavedQueryDetail toDetail(SavedQuery e) {
        return new SavedQueryDetail(
                e.getId(), e.getOwnerId(), e.getConnectionId(), e.getName(), e.getSql(), e.getVisibility(),
                e.isRetired(), e.getExecutionCount(), e.getCreatedAt(), e.getUpdatedAt());
    }

}