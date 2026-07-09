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

import { useState } from 'react'
import { EVENT_CATEGORY_OPTIONS, EVENT_TYPES_BY_CATEGORY } from './types'
import type { AuditLogFilter, EventCategory, EventType } from './types'

interface AuditLogFilterPanelProps {
  filter: AuditLogFilter
  onFilterChange: (filter: AuditLogFilter) => void
}

export function AuditLogFilterPanel({ filter, onFilterChange }: AuditLogFilterPanelProps) {
  const [dateFrom, setDateFrom] = useState(filter.dateFrom ?? '')
  const [dateTo, setDateTo] = useState(filter.dateTo ?? '')
  const [userId, setUserId] = useState(filter.userId?.toString() ?? '')
  const [eventCategory, setEventCategory] = useState<EventCategory | ''>(filter.eventCategory ?? '')
  const [eventType, setEventType] = useState<EventType | ''>(filter.eventType ?? '')
  const [error, setError] = useState<string | null>(null)

  const eventTypeOptions = eventCategory ? EVENT_TYPES_BY_CATEGORY[eventCategory] : []

  const handleCategoryChange = (next: EventCategory | '') => {
    setEventCategory(next)
    setEventType('')
  }

  const handleSearch = () => {
    if (dateFrom && dateTo && dateFrom > dateTo) {
      setError('開始日時は終了日時より前を指定してください')
      return
    }
    setError(null)
    onFilterChange({
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
      userId: userId ? Number(userId) : undefined,
      eventCategory: eventCategory || undefined,
      eventType: eventType || undefined,
    })
  }

  return (
    <div className="audit-log-filter-panel">
      <label>
        開始日時
        <input
          type="datetime-local"
          data-testid="audit-log-filter-date-from-input"
          value={dateFrom}
          onChange={(event) => setDateFrom(event.target.value)}
        />
      </label>
      <label>
        終了日時
        <input
          type="datetime-local"
          data-testid="audit-log-filter-date-to-input"
          value={dateTo}
          onChange={(event) => setDateTo(event.target.value)}
        />
      </label>
      <label>
        ユーザID
        <input
          type="number"
          data-testid="audit-log-filter-user-select"
          value={userId}
          onChange={(event) => setUserId(event.target.value)}
        />
      </label>
      <label>
        操作カテゴリ
        <select
          data-testid="audit-log-filter-category-select"
          value={eventCategory}
          onChange={(event) => handleCategoryChange(event.target.value as EventCategory | '')}
        >
          <option value="">すべて</option>
          {EVENT_CATEGORY_OPTIONS.map((category) => (
            <option key={category} value={category}>
              {category}
            </option>
          ))}
        </select>
      </label>
      <label>
        操作種別
        <select
          data-testid="audit-log-filter-type-select"
          value={eventType}
          onChange={(event) => setEventType(event.target.value as EventType | '')}
          disabled={!eventCategory}
        >
          <option value="">すべて</option>
          {eventTypeOptions.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
      </label>
      {error && (
        <p data-testid="audit-log-filter-error" role="alert">
          {error}
        </p>
      )}
      <button type="button" data-testid="audit-log-filter-search-button" onClick={handleSearch}>
        検索
      </button>
    </div>
  )
}