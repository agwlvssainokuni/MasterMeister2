# frontend-components.md — U3: RDBMS Connection & Schema Import

`u3-rdbms-connection-schema-import-functional-design-plan.md`（Question 8 = A）に基づく
`features/rdbmsConnection/`・`features/schema/`のコンポーネント設計。U1の共通基盤
（`components/DataTable`・`ConfirmDialog`・`ToastNotification`、`routes/ProtectedRoute`、
`/admin`プレフィクス規約）を再利用する。本ユニットの全画面は管理者専用
（MVP-7, MVP-8, ADM-3のペルソナはいずれも「管理者」）。

---

## features/rdbmsConnection/

### コンポーネント階層

```
ConnectionListPage（ProtectedRoute requiredRole="ADMIN"配下）
└── ConnectionTable（DataTableを利用、行内に編集/接続テスト/スキーマ取り込み導線）

ConnectionFormPage（ProtectedRoute requiredRole="ADMIN"配下、新規登録/編集共用）
└── （フォームのみ、子コンポーネントなし。接続テスト結果はページ内に表示）
```

### ConnectionListPage
- **状態**: `connections: ConnectionSummary[]`, `loading: boolean`
- **責務**: マウント時に`connectionApi.listConnections()`を呼び出し、`ConnectionTable`に
  結果を渡す（`business-logic-model.md` フロー1手順5、ADM-3 AC: 複数接続の管理）。

### ConnectionTable
- **Props**: `connections: ConnectionSummary[]`, `onTest(connectionId)`,
  `onImportSchema(connectionId)`
- **責務**: `DataTable`（U1）を用いて接続名・種別・ホスト/DB名を一覧表示し、各行に
  「編集」（`ConnectionFormPage`への遷移）、「接続テスト」（`business-logic-model.md`
  フロー2パターン2）、「スキーマ取り込み」（`SchemaImportPanel`を開く、フロー4）の導線を
  提供する。接続テスト結果は`ToastNotification`（U1）で通知する。

### ConnectionFormPage
- **状態**: `form: ConnectionConfig`（`name`, `rdbmsType`, `host`, `port`, `databaseName`,
  `username`, `password`, `additionalParams`）, `mode: 'create' | 'edit'`,
  `testResult: ConnectionTestResult | null`, `testing: boolean`, `submitting: boolean`,
  `error: string | null`
- **責務**: 接続情報の入力フォームを表示する。「接続テスト」ボタン押下時は
  `connectionApi.testConnection(form)`（保存前、`business-logic-model.md` フロー2
  パターン1）を呼び出し、`testResult`をページ内に表示する（成功/失敗のみで保存を強制
  しない）。保存操作で`connectionApi.createConnection(form)`または
  `connectionApi.updateConnection(connectionId, form)`を呼び出し、成功時は
  `ConnectionListPage`へ遷移する。
- **パスワード入力の扱い**: 新規登録時は必須入力。編集時は既存パスワードを画面に表示せず
  マスクされた状態（例: プレースホルダのみ）とし、変更する場合のみ再入力を必須とする
  （空欄のまま保存した場合は既存の暗号化済みパスワードを変更しない、という挙動をCode
  Generationで確定する）。
- **バリデーション**: 必須項目（`name`/`rdbmsType`/`host`/`port`/`databaseName`/`username`、
  新規登録時は`password`も）のクライアント側チェックのみ。`additionalParams`は
  バリデーションしない（`business-rules.md` 1.3）。

### connectionApi.ts（`features/rdbmsConnection/api/`）
| 関数 | 対応API | 責務 |
|---|---|---|
| `createConnection(config)` | `POST /api/rdbms-connections` | フロー1 |
| `updateConnection(connectionId, config)` | `PUT /api/rdbms-connections/{id}` | フロー1 |
| `listConnections()` | `GET /api/rdbms-connections` | `ConnectionListPage`初期表示 |
| `getConnection(connectionId)` | `GET /api/rdbms-connections/{id}` | `ConnectionFormPage`編集時の初期値取得（パスワードは復号値を返さずマスク用の空/プレースホルダ値とする） |
| `testConnection(config)` | `POST /api/rdbms-connections/test` | フロー2パターン1 |
| `testConnection(connectionId)` | `POST /api/rdbms-connections/{id}/test` | フロー2パターン2 |

（正確なエンドポイントパス・パラメータ名はCode Generation段階で確定する。
`component-methods.md`の`RdbmsConnectionService`シグネチャに準拠する。）

---

## features/schema/

### コンポーネント階層

```
SchemaImportPanel（ConnectionListPage/ConnectionFormPageから起動するモーダル/パネル）
└── （取り込み実行ボタン + 結果表示、子コンポーネントなし）

SchemaBrowserPage（ProtectedRoute requiredRole="ADMIN"配下）
├── SchemaSelector（スキーマ一覧からの選択）
├── TableList（DataTableを利用、テーブル/ビュー一覧）
└── TableDetailPanel（選択中テーブルのカラム一覧・主キー構成）
```

### SchemaImportPanel
- **状態**: `importing: boolean`, `result: SchemaImportResult | null`
- **責務**: `ConnectionListPage`または接続詳細画面から起動される。取り込み実行ボタン押下時に
  `schemaApi.importSchema(connectionId)`を呼び出し（`business-logic-model.md` フロー4）、
  完了後に成功/失敗と取り込みテーブル数を`result`に基づき表示する。失敗時は概要メッセージを
  表示する。

### SchemaBrowserPage
- **状態**: `schemas: string[]`, `selectedSchema: string | null`,
  `tables: TableMetadata[]`, `selectedTable: TableDetail | null`, `loading: boolean`
- **責務**: 取り込み済みスキーマ/テーブル/カラムの**メタデータ**（物理名・コメント・型・
  主キー構成）のみを表示する参照専用ビュー。権限フィルタなしの生データを表示する、U4完成
  までは管理者が取り込み結果を確認するための暫定閲覧画面（`business-logic-model.md`
  フロー5）。**レコードデータ（実際の行データ）は表示しない**——`SchemaQueryService`のAPI
  はメタデータのみを返し、レコードデータを返すAPIを持たない。レコードデータの閲覧は権限
  フィルタ（`EffectivePermissionResolver`、U4）を経由する必要があるため、U5（Master Data
  Maintenance）の責務として明確に分離する。マウント時に`schemaApi.listSchemas(connectionId)`
  を呼び出す。

### SchemaSelector
- **Props**: `schemas: string[]`, `selected: string | null`, `onSelect(schema)`
- **責務**: スキーマ名のプルダウン/リスト選択。選択変更時に`schemaApi.listTables(connectionId,
  schema)`を呼び出す（`SchemaBrowserPage`が保持する）。

### TableList
- **Props**: `tables: TableMetadata[]`, `onSelect(table)`
- **責務**: `DataTable`（U1）を用いてテーブル/ビュー一覧（物理名・種別`TABLE`/`VIEW`・
  コメント）を表示する。選択時に`schemaApi.getTableDetail(connectionId, schema, table)`を
  呼び出す。

### TableDetailPanel
- **Props**: `detail: TableDetail | null`
- **責務**: 選択中テーブルのカラム一覧（物理名・型・NULL許容・コメント）と主キー構成
  （複合主キーは構成順に表示）を表示する。ビューの場合は主キー欄を「なし」と表示する
  （`business-rules.md` 2.1）。

### schemaApi.ts（`features/schema/api/`）
| 関数 | 対応API | 責務 |
|---|---|---|
| `importSchema(connectionId)` | `POST /api/rdbms-connections/{id}/schema-import` | フロー4 |
| `listSchemas(connectionId)` | `GET /api/rdbms-connections/{id}/schemas` | フロー5 |
| `listTables(connectionId, schema)` | `GET /api/rdbms-connections/{id}/schemas/{schema}/tables` | フロー5 |
| `getTableDetail(connectionId, schema, table)` | `GET /api/rdbms-connections/{id}/schemas/{schema}/tables/{table}` | フロー5 |

（正確なエンドポイントパス・パラメータ名はCode Generation段階で確定する。
`component-methods.md`の`SchemaImportService`/`SchemaQueryService`シグネチャに準拠する。）

---

## AppRouter.tsxへの追加

| パス | コンポーネント | 認可 |
|---|---|---|
| `/admin/rdbms-connections` | `ConnectionListPage` | `ProtectedRoute requiredRole="ADMIN"`（U1のルーティング規約） |
| `/admin/rdbms-connections/new` | `ConnectionFormPage`（`mode='create'`） | 同上 |
| `/admin/rdbms-connections/:id` | `ConnectionFormPage`（`mode='edit'`） | 同上 |
| `/admin/schema/:connectionId` | `SchemaBrowserPage` | 同上 |

`SchemaImportPanel`は独立ルートを持たず、`ConnectionListPage`/`ConnectionFormPage`内から
モーダル/パネルとして起動する。`AppLayout`（U1）のナビゲーションに「RDBMS接続管理」への
リンクを追加する（管理者ロールのみ表示、U1`frontend-components.md`の出し分け機構を再利用）。