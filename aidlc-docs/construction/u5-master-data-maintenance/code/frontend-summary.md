# U5 Master Data Maintenance - フロントエンドサマリ

Step 11（フロントエンド生成）・Step 12（Vitest+RTLテスト）で生成したコンポーネント・
API・ルーティングの一覧。設計は`functional-design/frontend-components.md`に準拠する。

## 新規: `features/masterData/`

| ファイル | 内容 |
|---|---|
| `types.ts` | `RdbmsType`/`ConnectionSummary`/`TableType`/`Permission`/`TableSummary`/`ColumnMetadata`/`RecordListResult`/`FilterMode`/`Operator`/`UiCondition`/`SortDirection`/`UiSort`/`FilterCriteria`/`RecordCreate`/`RecordUpdate`/`RecordDelete`/`MutationRequest`/`MutationResult`。`ConnectionSummary`/`RdbmsType`/`TableType`/`Permission`は`rdbmsConnection`/`schema`/`permission`各featureの型と同一shapeで本feature内にローカル再定義（他feature非依存の方針）。`PageResult`のみ共通`src/types/api.ts`からimport |
| `api.ts` | `listAccessibleConnections()` → `GET /api/master-data/connections`、`listAccessibleSchemas(connectionId)` → `GET /api/master-data/{connectionId}/schemas`、`listAccessibleTables(connectionId, schema)` → `GET /api/master-data/{connectionId}/schemas/{schema}/tables`、`listRecords(connectionId, schema, table, criteria, page)` → `POST .../records:search`、`applyChanges(connectionId, schema, table, request)` → `POST .../records:apply`。U1の`apiClient`（`apiFetch`）を再利用 |
| `SchemaTableListPage.tsx` | 接続選択→スキーマ選択→`DataTable`（U1既存）による`TableSummary`一覧表示の3段階UI。行選択で`useNavigate`により`/master-data/:connectionId/:schema/:table`へ遷移する |
| `FilterPanel.tsx` | UI/RAWモードトグル。UIモードは`columns`のうち`effectivePermission`が`PERMISSION_ORDER`（NONE=0/READ=1/UPDATE=2）で`READ`以上のカラムのみを対象に`UiCondition`（カラム・`Operator`・値、`IS_NULL`/`IS_NOT_NULL`時は値欄非表示）と`UiSort`（カラム・昇順/降順）の追加・編集・削除フォームを提供する。RAWモードは`rawWhere`/`rawOrderBy`のテキスト入力欄を提供する。モード切り替えは排他的で`criteria.mode`を都度更新して`onChange`に通知する |
| `RecordListPage.tsx` | `RecordListResult`を`DataTable`拡張版（`components/DataTable`本体は変更せず、`DataTableColumn.render`で列ごとに拡張）で表示する。`effectivePermission === 'UPDATE'`のセルのみ`<input>`を描画し、編集内容を主キー（`primaryKeySequence`が設定された列から構築した`primaryKeyValues`をソート済みキー文字列化した`pkKey`）で`pendingChanges.updates`へupsertする。テーブルの`canDelete`が`true`の場合のみ削除チェックボックス列を先頭に追加し`pendingChanges.deletes`へ反映、`canCreate`が`true`の場合のみ「新規行を追加」ボタンと別テーブルの新規行編集UIを表示し`pendingChanges.creates`へ反映する。`canCreate`/`canDelete`は`RecordListPage`のURLパラメータに含まれないため`listAccessibleTables(connectionId, schema)`から対象テーブル名で検索して取得する。フィルタ条件変更時はページを0へリセットして再取得し、「反映」ボタンで`applyChanges`を呼び出し、成功時は`pendingChanges`をクリアして`reloadKey`をインクリメントし一覧を再取得、失敗時は`pendingChanges`を保持する |
| `MutationResultDialog.tsx` | `result`が`null`の間何も描画しない。成功時は作成/更新/削除件数、失敗時は`errorMessage`を表示するモーダル |

## ルーティング一覧（追加分）

| パス | 種別 | コンポーネント |
|---|---|---|
| `/master-data` | 保護（全認証ユーザ、ロール制約なし） | `SchemaTableListPage` |
| `/master-data/:connectionId/:schema/:table` | 保護（全認証ユーザ、ロール制約なし） | `RecordListPage` |

`AppLayout.tsx`には`isAuthenticated`のみを条件とする「マスタデータ」ナビゲーションリンク
（`data-testid="app-layout-nav-master-data"`）を、管理者専用リンク群より前の
ロール非依存の位置に追加した。本ユニットはU2〜U4の管理系機能と異なり全認証ユーザ向け機能の
ため、他ユニットの`requiredRole="ADMIN"`ルートとは条件が異なる。未認証で保護ルートに
アクセスした場合は`ProtectedRoute`（既存、変更なし）により`/login`へリダイレクトする。

## data-testid一覧（新規分）

`schema-table-list-page`, `schema-table-list-page-connection-select`,
`schema-table-list-page-schema-select`, `schema-table-list-page-row`, `filter-panel`,
`filter-panel-ui-mode`, `filter-panel-raw-mode`, `filter-panel-raw-where`,
`filter-panel-raw-order-by`, `record-list-page`, `record-list-page-delete-checkbox`,
`record-list-page-new-rows`, `record-list-page-add-row`, `record-list-page-apply`,
`mutation-result-dialog`, `mutation-result-dialog-close`, `app-layout-nav-master-data`

## 実装時判断事項（設計未規定・自律的に決定した内容）

- **`DataTable`本体を変更しない拡張方針**: `components/DataTable.tsx`は他ユニット共通の
  シンプルなテーブルコンポーネントのため変更せず、インライン編集セル・削除チェックボックス・
  新規行編集用の各UIは`RecordListPage.tsx`側の`DataTableColumn<RecordRow>.render`クロージャ
  のみで実現した。
- **行データが位置配列（`unknown[]`）であることへの対応**: バックエンドの`RecordListResult`は
  レコード行を`ColumnMetadata[]`と対応する`unknown[]`（キー付きオブジェクトではない）で
  返すため、`pkKey`（主キーのMapをソート済みキー文字列化）・`buildPrimaryKeyValues`
  （`primaryKeySequence`が設定された列から主キーMapを構築）・`rowKeyOf`（主キーがあれば
  `pkKey`、なければ`JSON.stringify(row)`）の3ヘルパーで、`pendingChanges`へのupsertと
  Reactの`key`の双方に使う行識別子を導出した。
- **テーブル単位の`canCreate`/`canDelete`取得手段の不在**: `RecordListPage`のルートは
  `connectionId`/`schema`/`table`の3パラメータのみを持ち、`SchemaTableListPage`で取得した
  `TableSummary`（`canCreate`/`canDelete`を含む）を引き継ぐ手段がない。単一テーブルの
  権限取得専用APIも存在しないため、`RecordListPage`は独自に`listAccessibleTables(connectionId,
  schema)`を再呼び出しし、対象テーブル名で該当行を検索して`canCreate`/`canDelete`を得る
  （API呼び出しが1回余分に発生するが、専用エンドポイント追加は本Stepのスコープ外と判断）。
- **`useEffect`内での再取得トリガー**: 「反映」成功後に一覧を再取得する際、抽出した
  `fetchRecords()`関数を`useEffect`から呼ぶ実装は`react-hooks/exhaustive-deps`警告
  （関数が依存配列に含まれない）を招くため、フェッチ処理を`useEffect`本体へ直接インライン化し、
  数値の`reloadKey`状態を依存配列に加えて`setReloadKey(k => k + 1)`で再取得を発火する方式とした
  （本リポジトリに`eslint-disable`コメントを使う先例がないため、警告を実際に解消する実装で
  対応）。

## テストカバレッジ（Step 12）

| テストファイル | 件数 | 検証内容 |
|---|---|---|
| `features/masterData/SchemaTableListPage.test.tsx` | 4 | 接続一覧表示とスキーマ選択前の非表示、接続選択時のスキーマロード、スキーマ選択時のテーブル一覧表示、行選択による`/master-data/:connectionId/:schema/:table`への遷移（`MemoryRouter`+`Routes`+遷移先スタブ`Route`） |
| `features/masterData/FilterPanel.test.tsx` | 8 | UI/RAWモード切替、`effectivePermission=NONE`列のプルダウン除外、`IS_NULL`演算子選択時の値入力欄非表示、条件の追加/削除、ソート追加時の既定ASC、RAWモードの`rawWhere`/`rawOrderBy`入力反映、読み取り可能列0件時の追加ボタン非活性、`onChange`呼び出し引数の直接検証 |
| `features/masterData/RecordListPage.test.tsx` | 12 | 一覧描画、READ列の非編集表示とUPDATE列の`<input>`編集、`canDelete`/`canCreate`に応じた削除チェックボックス列・新規行UIの表示切替、セル編集→反映→`applyChanges`への`primaryKeyValues`/`changedValues`引数と成功時の`pendingChanges`クリア・再取得（`reloadKey`）、失敗時の`pendingChanges`保持、削除チェック→反映の`applyChanges`引数、新規行追加・編集→反映の`applyChanges`引数、ページングボタンの活性/非活性 |
| `features/masterData/MutationResultDialog.test.tsx` | 3 | `result=null`時の非描画、成功時の件数表示、失敗時の`errorMessage`表示、閉じるボタンの`onClose`呼び出し |
| `routes/AppRouter.test.tsx`・`components/AppLayout.test.tsx`（ブラウンフィールド修正） | 計12件（既存、変更なし） | Step 11-6の`/master-data`ルート・「マスタデータ」ナビリンク追加後も全件成功することを回帰確認（新規テストケース追加なし） |

上表の新規4ファイル・27件を合わせ、U5で新規追加したフロントエンドテストは**27件**。
U1〜U4既存分と合わせ、フロントエンド全体は**44ファイル・183件、全テスト成功**
（`npx vitest run`）。`npx tsc -b`（型チェック）・`npm run lint`（oxlint）も
エラー・警告なしで完了している（詳細は`testing-summary.md`参照）。