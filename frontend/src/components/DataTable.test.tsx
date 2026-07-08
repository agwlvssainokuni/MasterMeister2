import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { DataTable } from './DataTable'
import type { DataTableColumn } from './DataTable'

interface Row {
  id: number
  name: string
}

const rows: Row[] = [
  { id: 1, name: 'Alice' },
  { id: 2, name: 'Bob' },
]

describe('DataTable', () => {
  it('renders a header for each column and a cell for each row', () => {
    const columns: DataTableColumn<Row>[] = [
      { key: 'name', header: 'Name' },
    ]

    render(<DataTable columns={columns} rows={rows} getRowKey={(row) => row.id} />)

    expect(screen.getByTestId('data-table-name-header')).toHaveTextContent('Name')
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('Bob')).toBeInTheDocument()
  })

  it('renders a sort button for sortable columns and calls onSort with the column key', () => {
    const onSort = vi.fn()
    const columns: DataTableColumn<Row>[] = [
      { key: 'name', header: 'Name', sortable: true },
    ]

    render(<DataTable columns={columns} rows={rows} getRowKey={(row) => row.id} onSort={onSort} />)

    fireEvent.click(screen.getByTestId('data-table-sort-button'))

    expect(onSort).toHaveBeenCalledWith('name')
  })

  it('uses the column render function when provided', () => {
    const columns: DataTableColumn<Row>[] = [
      { key: 'name', header: 'Name', render: (row) => `#${row.id} ${row.name}` },
    ]

    render(<DataTable columns={columns} rows={rows} getRowKey={(row) => row.id} />)

    expect(screen.getByText('#1 Alice')).toBeInTheDocument()
  })
})