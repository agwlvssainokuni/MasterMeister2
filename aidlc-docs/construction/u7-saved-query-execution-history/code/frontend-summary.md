# U7 Saved Query / Execution / History - フロントエンドサマリ

Step 11（フロントエンド生成）・Step 12（Vitest+RTLテスト）で生成したコンポーネント・
API・ルーティングの一覧。設計は`functional-design/frontend-components.md`に準拠する。

## 新規: `features/savedQuery/`

| ファイル | 内容 |
|---|---|
| `types.ts` | `Visibility`/`SavedQuerySummary`/`SavedQueryDetail`。バックエンドDTO（`SavedQuerySummary`/`SavedQueryDetail`）と1:1対応 |
| `api.ts` | `listQueries(connectionId, includeRetired)` → `GET /api/saved-queries`、`saveQuery(connectionId, name, sql, visibility)` → `POST /api/saved-queries`（作成IDを返す）、`getQuery(savedQueryId)` → `GET /api/saved-queries/{id}`、`updateQuery(savedQueryId, name, sql, visibility)` → `PUT /api/saved-queries/{id}`、`retireQuery(savedQueryId)` → `POST /api/saved-queries/{id}/retire` |
| `SavedQueryListPage.tsx` | `/saved-queries`。URLクエリパラメータ`connectionId`必須（未指定時はメッセージ表示のみ、API呼び出しなし）。「廃止済みも表示」トグルで`includeRetired`を切替。各行に「実行」（`/query-execution`へ`savedQueryId`付きで遷移）「詳細」（`/saved-queries/{id}`へ遷移）ボタン |
| `SavedQuerySaveForm.tsx` | `/saved-queries/new`。URLクエリパラメータ`connectionId`（必須）・`rawSql`（SQL欄の初期値、編集可能）を読み取る。名前・SQL・公開範囲を入力し`saveQuery`呼び出し後、作成された`SavedQueryDetailPage`へ遷移 |
| `SavedQueryDetailPage.tsx` | `/saved-queries/:id`。`getQuery`で詳細取得。作成者（`ownerId === currentUser.id`、`useAuth`経由）かつ`retired=false`の場合のみ「編集」「廃止」ボタンを活性化。編集モードでは`updateQuery`、廃止は`ConfirmDialog`（U1既存）確認後`retireQuery`。「実行」ボタンは`connectionId`・`savedQueryId`付きで`/query-execution`へ遷移 |

## 新規: `features/queryExecution/`

| ファイル | 内容 |
|---|---|
| `types.ts` | `ResultColumn`/`QueryResult`/`DetectedParam`/`PagingOption`。バックエンドDTOと1:1対応 |
| `api.ts` | `detectParams(sql)`（API呼び出しなし、バックエンド`SqlParamDetector.java`と同一の1文字ずつスキャンアルゴリズムをTypeScriptで再実装。文字列リテラルの`''`エスケープ・PostgreSQLの`::`キャスト演算子を誤検知しない）、`executeAdhocSql(connectionId, sql, params, paging)` → `POST /api/query-execution/adhoc`、`executeSavedQuery(connectionId, savedQueryId, params, paging)` → `POST /api/query-execution/saved/{id}` |
| `QueryExecutionPage.tsx` | `/query-execution`。URLクエリパラメータ`connectionId`（必須）・`rawSql`（手入力初期値）または`savedQueryId`のいずれかを受け取る。`savedQueryId`指定時は`savedQuery/api.getQuery`でSQLを取得しSQL欄を読み取り専用にする（`queryexecution → savedquery`の依存方向は`logical-components.md`確定どおり）。SQL変更のたびに`detectParams`を実行し検出パラメータごとに値入力欄を表示。ページング切替・実行ボタンを提供し、結果は`DataTable`（U5既存コンポーネント再利用）で表示、ページングあり時は前後ページ送りボタンを提供 |

## 新規: `features/queryHistory/`

| ファイル | 内容 |
|---|---|
| `types.ts` | `ExecutorScope`/`HistoryEntry`/`HistoryFilterCriteria`。バックエンドDTOと1:1対応 |
| `api.ts` | `listHistory(connectionId, criteria, page)` → `GET /api/query-history`（`connectionId`必須、`executedAtFrom`/`executedAtTo`/`executorScope`/`sqlTextSearch`/`page`/`pageSize`をクエリパラメータとして送信） |
| `QueryHistoryListPage.tsx` | `/query-history`。URLクエリパラメータ`connectionId`必須。日時範囲・実行者スコープ（`ALL`/`SELF`）・SQLテキスト検索の絞り込みフォーム（`AuditLogFilterPanel`と同様の構成）と`Pagination`（U1既存共有コンポーネント、`mm.app.query-history.default-page-size`/`page-size-options`と揃えた既定値）を提供。マスキングされた行はバックエンドが既に置換済みの値をそのまま表示（フロントエンド側での追加判定は不要）。`retired=true`の行に「廃止済み」バッジを表示。各行に「再実行」「保存」「ビルダーで編集」ボタンを配置し、`rawSql`（＋`connectionId`）付きでそれぞれ`/query-execution`・`/saved-queries/new`・`/query-builder`へ遷移 |

## ブラウンフィールド修正: `features/queryBuilder/QueryBuilderPage.tsx`（U6既存）

U6時点では未実装だった`GeneratedSqlPanel`の`onNavigateToSave`/`onNavigateToExecute`propsに、
`useNavigate`による実装を配線した（item 11-7）。`GeneratedSql.sql`を`rawSql`クエリパラメータ
として、`onNavigateToSave`は`/saved-queries/new`へ、`onNavigateToExecute`は`/query-execution`
へ遷移する。`GeneratedSql.params`は値ではなく構造の引き継ぎ方針（`business-rules.md` 6節）の
ため渡さない。

## ルーティング一覧（追加分）

| パス | 種別 | コンポーネント |
|---|---|---|
| `/saved-queries` | 保護（全認証ユーザ、ロール制約なし） | `SavedQueryListPage` |
| `/saved-queries/new` | 保護（同上） | `SavedQuerySaveForm` |
| `/saved-queries/:id` | 保護（同上） | `SavedQueryDetailPage` |
| `/query-execution` | 保護（同上） | `QueryExecutionPage` |
| `/query-history` | 保護（同上） | `QueryHistoryListPage` |

`AppLayout.tsx`には`isAuthenticated`のみを条件とする「保存クエリ」「クエリ実行」「クエリ履歴」
ナビゲーションリンク（`data-testid="app-layout-nav-{saved-queries|query-execution|query-history}"`）
を、「クエリビルダー」リンクの直後、管理者専用リンク群より前のロール非依存の位置に追加した。

## data-testid一覧（新規分）

`saved-query-list-page`, `saved-query-list-page-include-retired-checkbox`,
`saved-query-list-page-execute-button`, `saved-query-list-page-detail-button`,
`saved-query-save-form`, `saved-query-save-form-name-input`, `saved-query-save-form-sql-textarea`,
`saved-query-save-form-visibility-select`, `saved-query-save-form-error`,
`saved-query-save-form-submit-button`, `saved-query-detail-page`, `saved-query-detail-page-sql`,
`saved-query-detail-page-name-input`, `saved-query-detail-page-sql-textarea`,
`saved-query-detail-page-visibility-select`, `saved-query-detail-page-save-button`,
`saved-query-detail-page-cancel-button`, `saved-query-detail-page-execute-button`,
`saved-query-detail-page-edit-button`, `saved-query-detail-page-retire-button`,
`saved-query-detail-page-retired-badge`, `saved-query-detail-page-error`,
`query-execution-page`, `query-execution-page-sql-textarea`, `query-execution-page-params`,
`query-execution-page-param-{name}`, `query-execution-page-paging-checkbox`,
`query-execution-page-paging-page-size-input`, `query-execution-page-execute-button`,
`query-execution-page-error`, `query-execution-page-result`, `query-execution-page-prev-button`,
`query-execution-page-next-button`, `query-history-list-page`,
`query-history-list-page-executed-at-from-input`, `query-history-list-page-executed-at-to-input`,
`query-history-list-page-executor-scope-select`, `query-history-list-page-sql-text-search-input`,
`query-history-list-page-search-button`, `query-history-list-page-rerun-button`,
`query-history-list-page-save-button`, `query-history-list-page-edit-in-builder-button`,
`app-layout-nav-saved-queries`, `app-layout-nav-query-execution`, `app-layout-nav-query-history`

## 実装時判断事項（設計未規定・自律的に決定した内容）

- **`connectionId`の取得方法**: `savedQuery`/`queryExecution`/`queryHistory`はいずれも
  `component-dependency.md`により他featureのAPIに依存できない（`savedquery → common`のみ、
  `queryhistory → common, savedquery`のみ）ため、`rdbmsConnection`や`queryBuilder`のような
  接続選択UI・接続一覧APIを持たない。`frontend-components.md`にも接続選択UIの記載がないため、
  3feature全ての一覧・実行・履歴ページで`connectionId`を常にURLクエリパラメータ経由（他画面
  からの遷移、または直接URL指定）で受け取る方式とし、未指定時は「接続が指定されていません」
  というメッセージのみを表示する（API呼び出しは行わない）実装とした。
- **`connectionId`の引き継ぎ範囲の拡大**: `business-rules.md` 6節の遷移表は`queryHistory`起点
  の「再実行」「保存」に`connectionId`の引き継ぎを明記していない（「ビルダーで編集」のみ明記）
  が、遷移先（`QueryExecutionPage`/`SavedQuerySaveForm`）は上記のとおり`connectionId`が必須
  なため、`rawSql`とあわせて`connectionId`も常に引き継ぐ実装とした。同じ理由でU6
  `GeneratedSqlPanel`の`onNavigateToSave`/`onNavigateToExecute`（`QueryBuilderPage.tsx`から
  配線）でも、選択中の`connectionId`を付与している。
- **`SavedQuerySaveForm`のSQL欄**: `frontend-components.md`は「SQL（`rawSql`プリセット）」と
  記載するが、GEN-10 ACの「手入力またはクエリビルダーで作成したSQLに名前を付けて保存できる」
  との整合のため、`rawSql`は初期値としてプリセットしつつ編集可能なテキストエリアとした
  （読み取り専用にしていない）。
- **`QueryExecutionPage`のSQL取得元**: `savedQueryId`指定時にSQLを画面表示・パラメータ検出する
  ための取得元が`frontend-components.md`に明記されていなかったため、`queryexecution →
  savedquery`の依存方向（`logical-components.md`確定）を活かし`savedQuery/api.getQuery`を
  呼び出す実装とした。
- **ページング再実行時の状態遷移バグの回避**: `QueryExecutionPage`の前後ページ送りボタンで
  `setPaging`（非同期のstate更新）の直後に`paging`を参照して`executeAdhocSql`/
  `executeSavedQuery`を呼ぶと、Reactのstate更新が反映される前の古い`paging`を使ってしまう
  （stale closure）ため、`handleExecute`に`pagingOverride`引数を追加し、ページ送りボタンからは
  次ページの`PagingOption`を明示的に渡す実装とした。

## テストカバレッジ（Step 12）

| テストファイル | 件数 | 検証内容 |
|---|---|---|
| `SavedQueryListPage.test.tsx` | 5 | `connectionId`欠落時のメッセージ表示、`includeRetired=false`既定での一覧取得、トグルによる`includeRetired=true`再取得、「実行」「詳細」ボタンからの画面遷移 |
| `SavedQuerySaveForm.test.tsx` | 4 | `connectionId`欠落時のメッセージ表示、`rawSql`のプリフィル、保存成功時の詳細ページへの遷移と`saveQuery`呼び出し引数、保存失敗時のエラー表示 |
| `SavedQueryDetailPage.test.tsx` | 5 | 詳細表示、非所有者での編集/廃止ボタン非活性、所有者での編集保存（`updateQuery`呼び出し）、`ConfirmDialog`確認後の廃止（`retireQuery`呼び出しと廃止済みバッジ表示）、「実行」ボタンからの画面遷移 |
| `QueryExecutionPage.test.tsx` | 6 | `connectionId`欠落時のメッセージ表示、`:param`自動検出とSQL編集可能性（`rawSql`時）、パラメータ値付きの`executeAdhocSql`実行と結果表示、`savedQueryId`時のSQL読み取り専用表示と`executeSavedQuery`実行、`ValidationException`エラー表示、ページング有効時の次ページ実行（`pagingOverride`の検証） |
| `QueryHistoryListPage.test.tsx` | 8 | `connectionId`欠落時のメッセージ表示、既定条件での初回取得、保存クエリ名/廃止済みバッジ表示、検索ボタンでの絞り込み再取得、実行者スコープ変更での即時再取得、「再実行」「保存」「ビルダーで編集」ボタンからの画面遷移 |
| `QueryBuilderPage.test.tsx`（既存、ブラウンフィールド修正） | 8（既存6＋新規2） | 新規2件: 「保存」クリック時の`connectionId`・`rawSql`付き`/saved-queries/new`遷移、「実行」クリック時の`connectionId`・`rawSql`付き`/query-execution`遷移 |

上表6ファイル・**36件**（うち新規ファイル5件・28件、既存`QueryBuilderPage.test.tsx`への追加
2件）がU7で新規追加・変更したフロントエンドテスト。U1〜U6既存分と合わせ、フロントエンド全体は
**57ファイル・254件、全テスト成功**（`npx vitest run`）。`tsc -b`（型チェック）・`npm run lint`
（oxlint）もエラー・警告なしで完了している（詳細は`testing-summary.md`参照）。
