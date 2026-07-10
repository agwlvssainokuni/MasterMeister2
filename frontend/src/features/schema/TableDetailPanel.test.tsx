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
import { TableDetailPanel } from './TableDetailPanel'
import type { TableDetail } from './types'

describe('TableDetailPanel', () => {
  it('renders nothing when no table is selected', () => {
    const { container } = render(<TableDetailPanel detail={null} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('lists the columns and orders composite primary key columns by sequence', () => {
    const detail: TableDetail = {
      schemaName: 'public',
      tableName: 'order_items',
      tableType: 'TABLE',
      comment: '注文明細',
      columns: [
        { columnName: 'item_no', dataType: 'INTEGER', nullable: false, comment: null, ordinalPosition: 2, primaryKeySequence: 2 },
        { columnName: 'order_id', dataType: 'INTEGER', nullable: false, comment: null, ordinalPosition: 1, primaryKeySequence: 1 },
        { columnName: 'quantity', dataType: 'INTEGER', nullable: true, comment: null, ordinalPosition: 3, primaryKeySequence: null },
      ],
    }

    render(<TableDetailPanel detail={detail} />)

    expect(screen.getByText('order_items')).toBeInTheDocument()
    expect(screen.getAllByTestId('table-detail-panel-column-row')).toHaveLength(3)
    expect(screen.getByTestId('table-detail-panel-primary-key')).toHaveTextContent('order_id, item_no')
  })

  it('shows "なし" when the table has no primary key columns', () => {
    const detail: TableDetail = {
      schemaName: 'public',
      tableName: 'logs',
      tableType: 'TABLE',
      comment: null,
      columns: [
        { columnName: 'message', dataType: 'VARCHAR', nullable: true, comment: null, ordinalPosition: 1, primaryKeySequence: null },
      ],
    }

    render(<TableDetailPanel detail={detail} />)

    expect(screen.getByTestId('table-detail-panel-primary-key')).toHaveTextContent('なし')
  })

  it('shows "なし" for a view even if a primary-key-like column is present', () => {
    const detail: TableDetail = {
      schemaName: 'public',
      tableName: 'user_view',
      tableType: 'VIEW',
      comment: null,
      columns: [
        { columnName: 'id', dataType: 'INTEGER', nullable: false, comment: null, ordinalPosition: 1, primaryKeySequence: 1 },
      ],
    }

    render(<TableDetailPanel detail={detail} />)

    expect(screen.getByTestId('table-detail-panel-primary-key')).toHaveTextContent('なし')
  })
})
