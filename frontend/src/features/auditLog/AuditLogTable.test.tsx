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

import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AuditLogTable } from './AuditLogTable'
import type { AuditLog } from './types'

const rows: AuditLog[] = [
  {
    id: 1,
    occurredAt: '2026-01-01T00:00:00Z',
    userId: 1,
    connectionId: null,
    eventCategory: 'AUTHENTICATION',
    eventType: 'LOGIN_SUCCESS',
    result: 'SUCCESS',
    targetDescription: 'user@example.com',
    summaryMessage: null,
  },
]

describe('AuditLogTable', () => {
  it('shows a loading message while loading', () => {
    render(<AuditLogTable rows={[]} loading={true} />)

    expect(screen.getByText('読み込み中...')).toBeInTheDocument()
  })

  it('renders a row for each audit log with a category badge', () => {
    render(<AuditLogTable rows={rows} loading={false} />)

    expect(screen.getByText('user@example.com')).toBeInTheDocument()
    expect(screen.getByText('AUTHENTICATION')).toHaveClass('audit-log-badge-authentication')
  })

  it('renders a placeholder for null optional fields', () => {
    render(<AuditLogTable rows={rows} loading={false} />)

    expect(screen.getAllByText('-').length).toBeGreaterThan(0)
  })
})