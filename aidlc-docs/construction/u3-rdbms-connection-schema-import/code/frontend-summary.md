# U3 RDBMS Connection & Schema Import - フロントエンドサマリ

Step 11（フロントエンド生成）・Step 12（Vitest+RTLテスト）で生成したコンポーネント・
API・ルーティングの一覧。設計は`functional-design/frontend-components.md`に準拠する。

## 新規: `features/rdbmsConnection/`

| ファイル | 内容 |
|---|---|
| `types.ts` | `RdbmsType`（`MYSQL`\|`MARIADB`\|`POSTGRESQL`\|`H2`）、`ConnectionSummary`、`ConnectionDetail`、`ConnectionConfig`、`ConnectionTestResult` |
| `api/connectionApi.ts` | `createConnection(config)` → `POST /api/rdbms-connections`、`updateConnection(id, config)` → `PUT /api/rdbms-connections/{id}`、`listConnections()` → `GET /api/rdbms-connections`、`getConnection(id)` → `GET /api/rdbms-connections/{id}`、`testConnection(config \| connectionId)`（オーバーロード） → `POST /api/rdbms-connections/test` または `POST /api/rdbms-connections/{id}/test` |
| `ConnectionListPage.tsx` | マウント時`listConnections()`。`ConnectionTable`のコンテナ。接続テスト結果は`ToastNotification`（U1）で通知。「スキーマ取り込み」操作で`SchemaImportPanel`（`features/schema`）をインライン表示する状態を保持 |
| `ConnectionTable.tsx` | `DataTable`（U1）利用。行ごとに編集リンク・接続テストボタン・スキーマ取り込みボタンを表示 |
| `ConnectionFormPage.tsx` | 新規登録/編集共用（`mode`プロパティ）。編集時はマウント時`getConnection(id)`で初期値を取得するが、パスワード欄は復号値を返さないAPI仕様のため常に空欄で初期化（`updateConnection`は空欄のまま送信すれば既存の暗号化済みパスワードを変更しない） |

## 新規: `features/schema/`

| ファイル | 内容 |
|---|---|
| `types.ts` | `TableType`（`TABLE`\|`VIEW`）、`SchemaImportResult`、`TableMetadata`、`ColumnDetail`、`TableDetail` |
| `api/schemaApi.ts` | `importSchema(connectionId)` → `POST /api/rdbms-connections/{connectionId}/schema-import`、`listSchemas(connectionId)` → `GET /api/rdbms-connections/{connectionId}/schemas`、`listTables(connectionId, schema)` → `GET .../schemas/{schema}/tables`、`getTableDetail(connectionId, schema, table)` → `GET .../schemas/{schema}/tables/{table}`。スキーマ名・テーブル名はいずれも`encodeURIComponent`でエスケープしてURLに埋め込む |
| `SchemaImportPanel.tsx` | `ConnectionListPage`からインライン起動するパネル（独立ルートを持たない）。取り込み実行→成功時「取り込みに成功しました（N件）」、失敗時（APIレスポンス`success: false`または例外）「取り込みに失敗しました: ...」を表示 |
| `SchemaBrowserPage.tsx` | マウント時`listSchemas(connectionId)`。スキーマ選択時`listTables`、テーブル選択時`getTableDetail`を呼び出し、`SchemaSelector`・`TableList`・`TableDetailPanel`を組み合わせて表示 |
| `SchemaSelector.tsx` | プレーンな`<select>`ラッパー。スキーマ一覧からの選択を`onSelect`で通知 |
| `TableList.tsx` | `DataTable`（U1）利用。行ごとにテーブル選択ボタンを表示 |
| `TableDetailPanel.tsx` | 選択中テーブルのカラム一覧・主キー構成を表示。主キーは`primaryKeySequence`昇順にソートして列名を連結表示。`tableType === 'VIEW'`の場合は主キー保有列があっても常に「なし」と表示（`business-rules.md` 2.1準拠） |

## ルーティング一覧（追加分）

| パス | 種別 | コンポーネント |
|---|---|---|
| `/admin/rdbms-connections` | 保護（ADMIN限定） | `ConnectionListPage` |
| `/admin/rdbms-connections/new` | 保護（ADMIN限定） | `ConnectionFormPage mode="create"` |
| `/admin/rdbms-connections/:id` | 保護（ADMIN限定） | `ConnectionFormPage mode="edit"` |
| `/admin/schema/:connectionId` | 保護（ADMIN限定） | `SchemaBrowserPage` |

`SchemaImportPanel`は独立ルートを持たず、`ConnectionListPage`内から起動するパネルとして
実装されている（`frontend-components.md`ルーティング表の4パスと1:1対応）。
`AppLayout.tsx`には「RDBMS接続管理」（`/admin/rdbms-connections`）へのナビゲーションリンクを
既存の「監査ログ」リンクと同一条件（`isAuthenticated && currentUser?.role === 'ADMIN'`）で
追加した。`/admin/schema/:connectionId`への直接のナビリンクは設計に規定がないため未追加
（`ConnectionTable`の「スキーマ取り込み」操作は`SchemaImportPanel`インライン起動のみ）。
未認証で保護ルートにアクセスした場合は`ProtectedRoute`（既存、変更なし）により`/login`へ
リダイレクトする。

## data-testid一覧（新規分）

`connection-list-page`, `connection-list-page-new-button`, `connection-table-edit-button`,
`connection-table-test-button`, `connection-table-import-schema-button`,
`connection-form-page`, `connection-form-page-name-input`,
`connection-form-page-rdbms-type-select`, `connection-form-page-host-input`,
`connection-form-page-port-input`, `connection-form-page-database-name-input`,
`connection-form-page-username-input`, `connection-form-page-password-input`,
`connection-form-page-additional-params-input`, `connection-form-page-test-button`,
`connection-form-page-test-result-message`, `connection-form-page-submit-button`,
`schema-import-panel`, `schema-import-panel-import-button`,
`schema-import-panel-result-message`, `schema-import-panel-close-button`,
`schema-browser-page`, `schema-selector-select`, `table-list-row`, `table-detail-panel`,
`table-detail-panel-column-row`, `table-detail-panel-primary-key`,
`app-layout-nav-rdbms-connections`

## 実装時判断事項（設計未規定・自律的に決定した内容）

- **編集時のパスワード欄の扱い**: `getConnection`のレスポンスは復号値を含まない
  （`ConnectionDetail`に`password`フィールド自体が存在しない）ため、編集フォームは
  マウント時に常にパスワード欄を空欄で初期化する。空欄のまま保存した場合、バックエンド側
  `RdbmsConnectionService.updateConnection`が既存の暗号化済みパスワードを変更しない前提の
  実装とした（`frontend-components.md`記載のフロー）。
- **`/admin/schema/:connectionId`への導線**: `AppRouter.tsx`にはルートを追加したが、
  `frontend-components.md`に本画面への具体的なナビゲーション方法（一覧からのリンク等）の
  規定がなかったため、本Stepでは直接のリンクを追加していない。`ConnectionTable`の
  「スキーマ取り込み」ボタンは`SchemaImportPanel`をインライン表示するのみで、
  スキーマ参照画面へは遷移しない。将来のUnitまたはレビューで導線を追加する可能性がある。
- **`testConnection`のオーバーロード**: 保存前の入力値によるテスト（`ConnectionFormPage`の
  接続テストボタン）と、保存済み接続IDによるテスト（`ConnectionTable`の接続テストボタン）を
  単一のAPI関数名に関数オーバーロードで束ねた（`frontend-components.md`のconnectionApi.ts表）。

## テストカバレッジ（Step 12）

| テストファイル | 件数 | 検証内容 |
|---|---|---|
| `features/rdbmsConnection/api/connectionApi.test.ts` | 6 | `createConnection`/`updateConnection`/`listConnections`/`getConnection`/`testConnection`（2オーバーロード）のリクエストURL・メソッド・ボディ |
| `features/rdbmsConnection/ConnectionTable.test.tsx` | 4 | 行描画、編集リンクのhref、接続テスト/スキーマ取り込みボタンのコールバック発火（接続ID指定） |
| `features/rdbmsConnection/ConnectionFormPage.test.tsx` | 5 | 新規登録送信、編集時マウント（パスワード欄空欄初期化）、編集送信（空欄パスワードでの`updateConnection`呼び出し）、接続テスト成功/失敗メッセージ表示 |
| `features/rdbmsConnection/ConnectionListPage.test.tsx` | 6 | マウント時一覧表示、新規登録リンクのhref、接続テスト成功/失敗トースト、スキーマ取り込みパネルの表示/クローズ |
| `features/schema/api/schemaApi.test.ts` | 4 | `importSchema`/`listSchemas`/`listTables`/`getTableDetail`のリクエストURL・メソッド、スキーマ名・テーブル名の`encodeURIComponent`エスケープ |
| `features/schema/SchemaImportPanel.test.tsx` | 4 | 取り込み成功、APIレスポンス失敗、例外発生時、`onClose`コールバック |
| `features/schema/SchemaBrowserPage.test.tsx` | 3 | マウント時`listSchemas`呼び出し、スキーマ選択時`listTables`呼び出し、テーブル選択時`getTableDetail`呼び出しと詳細表示 |
| `features/schema/SchemaSelector.test.tsx` | 3 | スキーマ選択肢の描画、選択変更時の`onSelect`呼び出し、`selected`プロパティの反映 |
| `features/schema/TableList.test.tsx` | 2 | テーブル行の描画、行選択時の`onSelect`呼び出し |
| `features/schema/TableDetailPanel.test.tsx` | 4 | `detail={null}`時の非表示、複合主キーの`primaryKeySequence`昇順表示、主キー無しテーブルの「なし」表示、ビューでの「なし」強制表示 |
| `routes/AppRouter.test.tsx`（拡張） | 2件追加 | `/admin/rdbms-connections`・`/admin/schema/:connectionId`への未認証アクセス時の`/login`リダイレクト |
| `components/AppLayout.test.tsx`（拡張） | 既存3件を拡張 | `app-layout-nav-rdbms-connections`リンクの表示（管理者）／非表示（非管理者・未認証） |

上表の新規10ファイル・41件に加え、既存`AppRouter.test.tsx`への2件追加（既存4件→6件）、
`AppLayout.test.tsx`の既存3件拡張（新規テストケース追加なし）を合わせ、U3で新規/拡張した
テストは合計**43件**。U1・U2既存分と合わせ、フロントエンド全体は**30ファイル・114件、
全テスト成功**（`npx vitest run`）。`npx tsc -b`（型チェック）・`npx oxlint`（Lint）も
エラーなしで完了している（詳細は`testing-summary.md`参照）。