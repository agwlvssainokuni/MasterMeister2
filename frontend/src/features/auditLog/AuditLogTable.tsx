import { DataTable } from '../../components/DataTable'
import type { DataTableColumn } from '../../components/DataTable'
import './auditLogTable.css'
import type { AuditLog, EventCategory } from './types'

const CATEGORY_BADGE_CLASS: Record<EventCategory, string> = {
  AUTHENTICATION: 'audit-log-badge-authentication',
  ADMIN_OPERATION: 'audit-log-badge-admin-operation',
  DATA_ACCESS: 'audit-log-badge-data-access',
}

interface AuditLogTableProps {
  rows: AuditLog[]
  loading: boolean
}

export function AuditLogTable({ rows, loading }: AuditLogTableProps) {
  const columns: DataTableColumn<AuditLog>[] = [
    {
      key: 'occurredAt',
      header: '発生日時',
      render: (row) => new Date(row.occurredAt).toLocaleString(),
    },
    { key: 'userId', header: 'ユーザID', render: (row) => row.userId ?? '-' },
    {
      key: 'eventCategory',
      header: '操作カテゴリ',
      render: (row) => (
        <span className={`audit-log-badge ${CATEGORY_BADGE_CLASS[row.eventCategory]}`}>{row.eventCategory}</span>
      ),
    },
    { key: 'eventType', header: '操作種別' },
    { key: 'result', header: '結果' },
    { key: 'targetDescription', header: '対象', render: (row) => row.targetDescription ?? '-' },
    { key: 'summaryMessage', header: '概要', render: (row) => row.summaryMessage ?? '-' },
  ]

  if (loading) {
    return <p>読み込み中...</p>
  }

  return <DataTable columns={columns} rows={rows} getRowKey={(row) => row.id} />
}