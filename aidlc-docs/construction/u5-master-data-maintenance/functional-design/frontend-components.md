# frontend-components.md — U5: Master Data Maintenance

`u5-master-data-maintenance-functional-design-plan.md`（Q8）に基づく`features/masterData/`の
コンポーネント設計。U1の共通基盤（`components/DataTable`・`ConfirmDialog`・
`ToastNotification`、`routes/ProtectedRoute`）を再利用する。本ユニットの全画面は一般ユーザ
向け（MVP-10, MVP-11, GEN-1〜5のペルソナはいずれも「一般ユーザ」、管理者ロール制約は
課さない）。`/master-data`配下にルーティングする。

---

## features/masterData/

### コンポーネント階層

```
SchemaTableListPage（ProtectedRoute配下、認証済みユーザ全員アクセス可）
└── DataTable（U1既存を再利用、TableSummary一覧を表示）

RecordListPage（ProtectedRoute配下）
├── FilterPanel（UIモード/RAWモードのトグル、絞り込み・ソート条件の入力）
├── DataTable（U1既存を拡張、RecordListResult.columns/recordsを描画。インライン編集セル・
│   行選択チェックボックス・新規行追加ボタンを追加）
└── MutationResultDialog（「反映」実行後の結果表示）
```

### SchemaTableListPage
- **状態**: `schema: string | null`, `tables: TableSummary[]`, `loading: boolean`
  （**2026-07-15変更要求**: `connectionId`はページ内state ではなく、U1の`useConnection()`
  フックからグローバル接続コンテキストとして取得する。ページ内に接続選択UIは持たない）
- **責務**: `connectionId`（グローバルコンテキスト）が変化するたびに`schema`/`tables`を
  リセットし、`masterDataApi.listAccessibleSchemas(connectionId)`を呼び出す。スキーマ選択後
  `masterDataApi.listAccessibleTables(connectionId, schema)`を呼び出し、結果を`DataTable`で
  表示する（`business-logic-model.md` フロー1、MVP-10 AC）。行選択で
  `/master-data/:connectionId/:schema/:table`（`RecordListPage`）へ遷移する。`connectionId`が
  `null`（グローバル接続未選択）の場合は「接続が指定されていません。」を表示し、スキーマ取得を
  行わない。

### FilterPanel
- **Props**: `columns: ColumnMetadata[]`, `criteria: FilterCriteria`,
  `onChange(criteria: FilterCriteria)`
- **状態**: `mode: 'UI' | 'RAW'`
- **責務**: UIモード時は`columns`のうち`effectivePermission >= READ`のカラムのみを対象に
  `UiCondition`（カラム選択・`Operator`プルダウン・値入力）・`UiSort`（カラム選択・昇順/
  降順）の組み立てUIを提供する（`business-logic-model.md` フロー2手順1、GEN-1 AC）。RAW
  モード時は`rawWhere`/`rawOrderBy`のテキスト入力欄を提供する（GEN-2 AC）。モード切り替えは
  排他的（`domain-entities.md`の`FilterCriteria`構造に対応）。

### RecordListPage
- **状態**: `result: RecordListResult | null`, `criteria: FilterCriteria`, `page: PageRequest`,
  `pendingChanges: { creates: RecordCreate[], updates: RecordUpdate[], deletes: RecordDelete[]
  }`, `loading: boolean`, `mutationResult: MutationResult | null`
- **責務**: マウント時・`FilterPanel`の条件変更時に`masterDataApi.listRecords(connectionId,
  schema, table, criteria, page)`を呼び出し、`RecordListResult`を保持する
  （`business-logic-model.md` フロー2手順2〜3）。`DataTable`拡張版に`result.columns`/
  `result.records`を渡して描画する。編集・新規行追加・削除選択の差分は`pendingChanges`に
  蓄積し（対象RDBMSへは未送信）、「反映」ボタン押下で
  `masterDataApi.applyChanges(connectionId, schema, table, { creates, updates, deletes })`を
  呼び出す（フロー3手順2）。レスポンスの`MutationResult`を`mutationResult`へ設定し
  `MutationResultDialog`を表示する。成功時は`pendingChanges`をクリアして一覧を再取得し、
  失敗時は`pendingChanges`を保持する（ユーザが修正して再送信できるようにする）。

`DataTable`拡張部分（`RecordListPage`固有、`components/DataTable`本体は変更しない想定・
詳細はCode Generationで確定）:
- インライン編集セル: `result.columns[i].effectivePermission == UPDATE`のセルのみ編集可能
  （GEN-3 AC、Q6背景のとおり主キーなしテーブルは更新操作自体を無効化——
  `business-rules.md` 3.2）。編集内容は`pendingChanges.updates`へ反映する。
- 行選択チェックボックス: `table.canDelete`が`true`の場合のみ表示し、選択行を
  `pendingChanges.deletes`へ反映する（GEN-5 AC）。
- 新規行追加ボタン: `table.canDelete`ではなく`table.canCreate`が`true`の場合のみ表示し、
  空行を`pendingChanges.creates`へ追加する（GEN-5 AC）。

### MutationResultDialog
- **Props**: `result: MutationResult | null`, `onClose()`
- **責務**: 「反映」実行後の結果を表示する。`result.success`時は反映件数
  （`createdCount`/`updatedCount`/`deletedCount`）、失敗時は`result.errorMessage`を表示する
  （`business-rules.md` 3.4）。

### api.ts（`features/masterData/`）
| 関数 | 対応API | 責務 |
|---|---|---|
| `listAccessibleSchemas(connectionId)` | `GET /api/rdbms-connections/{connectionId}/schemas` | `SchemaTableListPage`初期表示、フロー1手順1 |
| `listAccessibleTables(connectionId, schema)` | `GET /api/rdbms-connections/{connectionId}/schemas/{schema}/tables` | `SchemaTableListPage`、フロー1手順2 |
| `listRecords(connectionId, schema, table, criteria, page)` | `GET /api/rdbms-connections/{connectionId}/schemas/{schema}/tables/{table}/records` | `RecordListPage`初期表示・絞り込み、フロー2手順2 |
| `applyChanges(connectionId, schema, table, request)` | `POST /api/rdbms-connections/{connectionId}/schemas/{schema}/tables/{table}/records:apply` | 「反映」操作、フロー3手順2 |

（正確なエンドポイントパス・クエリ/ボディの受け渡し方式はCode Generation段階で確定する。
`component-methods.md`の`MasterDataQueryService`/`MasterDataMutationService`シグネチャ＋
Q2の`RecordListResult`詳細化に準拠する。）

`features/masterData/`は他featureのAPIを直接参照しない（`unit-of-work.md`のU5依存は
バックエンド側`masterdata → common, audit, rdbmsconnection, schema, permission`のみで、
これらはいずれもフロントエンドではAPIレスポンスの形として間接的に反映されるのみ。U4の
`features/permission/` → `features/group/`のような機能間直接参照はU5には存在しない）。

---

## U7（クエリ実行、未着手）との将来的な共通化に関する申し送り事項

`RecordListResult`（`columns` + `records`）はJDBCの`ResultSet`/`ResultSetMetaData`の構造を
踏襲しており（Q2）、U7の任意SQL実行結果も同じ「カラムメタデータ＋結果行」の形で返却される
想定である。そのため`DataTable`拡張部分のうち「`columns`定義から`DataTableColumn<T>`を
組み立て、`records`の各行を描画する」というアダプタ的なロジックは、U5とU7で共通化できる
可能性がある（インライン編集・行選択等のU5固有の操作は共通化対象外）。共通化の要否・
実装方式はU7のFunctional Designで改めて判断する。

---

## AppRouter.tsxへの追加

| パス | コンポーネント | 認可 |
|---|---|---|
| `/master-data` | `SchemaTableListPage`（**2026-07-15変更要求**: 接続はU1のグローバル接続コンテキストを参照、ページ内接続選択UIは廃止） | `ProtectedRoute`（認証済みユーザ全員、管理者ロール制約なし） |
| `/master-data/:connectionId/:schema/:table` | `RecordListPage` | 同上 |

`AppLayout`（U1）のナビゲーションに「マスタデータ」への遷移リンクを追加する（全ユーザに
表示、U1`frontend-components.md`の出し分け機構——管理者専用リンクとは異なり常時表示）。