# frontend-components.md — U7: Saved Query / Execution / History

`u7-saved-query-execution-history-functional-design-plan.md`の回答（Q9, Q10）に基づく
フロントエンド構成。U1既存の共有コンポーネント（`ConfirmDialog`・`ToastNotification`、
`routes/ProtectedRoute`）を再利用する。一般ユーザ・管理者いずれも利用可能な機能
（GEN-10〜16のACに管理者限定の記載なし）。ルーティングプレフィックスは`/saved-queries`・
`/query-execution`・`/query-history`の3系統。

---

## features/savedQuery/

### コンポーネント階層

```
SavedQueryListPage
SavedQuerySaveForm
SavedQueryDetailPage
```

（U6の`queryBuilder/`のようなタブ切り替えコンテナ構造ではなく、3つの独立したページとして
構成する——GEN-10〜12はそれぞれ独立した操作であり、共有state管理を要する連続フローではない
ため）

### SavedQueryListPage

- **状態**: `savedQueries: SavedQuerySummary[]`, `includeRetired: boolean`
  （**2026-07-15変更要求**: `connectionId`はページ内stateではなくU1の`useConnection()`から
  グローバル接続コンテキストとして取得する）
- **責務**: `connectionId`（グローバルコンテキスト）が`null`の場合は「接続が指定されていません。」
  を表示し、ナビゲーションから直接アクセスしても行き止まりにならない（CHG-3）。`connectionId`
  が確立していれば`listQueries(connectionId, includeRetired)`（フロー1手順2）を呼び出し、
  Public全件＋自分のPrivateを一覧表示する（`/saved-queries`）。「廃止済みも表示」トグルで
  `includeRetired`を切り替える。各行に「実行」「詳細」リンクを配置する。**（2026-07-15
  変更要求・訂正）** 「実行」リンクは`savedQueryId`のみを付与して`/query-execution`へ遷移する
  （`connectionId`は付与しない——`QueryExecutionPage`が`getQuery(savedQueryId)`のレスポンスから
  取得するため。下記`QueryExecutionPage`参照）。

### SavedQuerySaveForm

- **状態**: `name: string`, `visibility: Visibility`
- **Props**: なし（URLクエリパラメータ`rawSql`から初期値を取得。**2026-07-15変更要求**:
  `connectionId`はU1の`useConnection()`から取得し、URLクエリパラメータでの受け取りは廃止する）
- **責務**: 名前・SQL（`rawSql`プリセット、`business-rules.md` 6節）・可視性を入力し
  `saveQuery`を呼び出す（GEN-10、フロー1手順1、`/saved-queries/new`）。保存成功後は
  `SavedQueryDetailPage`へ遷移する。`connectionId`が`null`の場合は「接続が指定されていません。」
  を表示し保存操作を行わせない。

### SavedQueryDetailPage

- **状態**: `savedQuery: SavedQueryDetail | null`, `editing: boolean`
- **責務**: `getQuery`（フロー1手順3）で詳細を取得し表示する（`/saved-queries/{id}`）。
  作成者のみ「編集」「廃止」ボタンを活性化する（GEN-12）。編集モードでは`updateQuery`、
  廃止ボタン押下時は`ConfirmDialog`（U1既存）で確認後`retireQuery`を呼び出す
  （`business-rules.md` 1.3）。「実行」ボタンから`queryExecution/`の実行画面へ
  `savedQueryId`のみを付与して遷移する（**2026-07-15変更要求・訂正**: `connectionId`は
  付与しない。本ページはグローバル接続コンテキストとは独立に、直接リンク経由でも
  `savedQuery.connectionId`を表示・保持しているため、この値を確実に使う必要がある
  ——グローバルコンテキストで代替すると、閲覧中の保存クエリの実際の接続と、たまたま現在
  選択中のグローバル接続が一致しない場合に誤動作する。`QueryExecutionPage`側が
  `getQuery(savedQueryId)`を再度呼び出し`connectionId`を取得する設計に統一した）。

### api.ts（`features/savedQuery/`）

| 関数 | 対応API | 責務 |
|---|---|---|
| `listQueries(connectionId, includeRetired)` | `GET /api/saved-queries` | フロー1手順2 |
| `saveQuery(connectionId, name, sql, visibility)` | `POST /api/saved-queries` | フロー1手順1 |
| `getQuery(savedQueryId)` | `GET /api/saved-queries/{savedQueryId}` | フロー1手順3 |
| `updateQuery(savedQueryId, name, sql, visibility)` | `PUT /api/saved-queries/{savedQueryId}` | フロー1手順4 |
| `retireQuery(savedQueryId)` | `POST /api/saved-queries/{savedQueryId}/retire` | フロー1手順4 |

---

## features/queryExecution/

### コンポーネント階層

```
QueryExecutionPage
```

### QueryExecutionPage

- **状態**: `sql: string`, `readOnly: boolean`（`savedQueryId`指定時true）,
  `detectedParams: DetectedParam[]`, `paramValues: Record<string, string>`,
  `paging: PagingOption`, `result: QueryResult | null`。**（2026-07-15変更要求、訂正版）**
  `connectionId`の取得元は`savedQueryId`の有無で分岐する: `savedQueryId`指定時（保存クエリ
  実行）は`getQuery(savedQueryId)`のレスポンス（`SavedQueryDetail.connectionId`、保存クエリに
  固定された値）を用いる（グローバルコンテキストは使わない——バックエンドの
  `executeSavedQuery`が渡された`connectionId`と保存クエリ自身の`connectionId`の一致を検証する
  ため、`business-rules.md` 1.4、誤った接続での実行を防ぐ）。`savedQueryId`未指定時（手入力
  SQL実行）はU1の`useConnection()`からグローバル接続コンテキストとして取得する（手入力SQLは
  特定の接続に固定されないため）。いずれの場合もURLクエリパラメータでの`connectionId`受け取りは
  廃止する。新規に`schemas: string[]`, `schema: string | null`を保持する
- **責務**: 実効`connectionId`（上記のいずれか）が`null`の場合は「接続が指定されていません。」
  を表示する（CHG-3）。確立時、URLクエリパラメータで`rawSql`（手入力実行、
  GEN-13）または`savedQueryId`（保存クエリ実行、GEN-11）のいずれかを受け取る
  （`/query-execution`）。`savedQueryId`指定時はSQL入力欄を読み取り専用にする
  （`business-rules.md` 1.4）。**（2026-07-15変更要求）** マウント時に
  `listAccessibleSchemas(connectionId)`（新規API、`business-rules.md` 2.3追記事項）でスキーマ
  選択肢を取得し、常設のスキーマ選択`<select>`を表示する（CHG-4）。選択肢が1件のみでも
  自動選択は行わない（`masterData`/`queryBuilder`と同じ流儀）。URLクエリパラメータ`schema`が
  あれば初期値としてプリフィルする（画面内で上書き可能、`business-rules.md` 6節）。`schema`が
  未選択の間は実行操作を行えない。SQL入力/変更のたびにパラメータ自動検出（フロー2手順4）を
  行い、検出された`:param`ごとに値入力欄を表示する。ページング切替（あり/なし）・実行ボタンを
  提供し、`executeAdhocSql`/`executeSavedQuery`（いずれも`schema`を必須パラメータとして渡す）を
  呼び出す。結果は`DataTable`（U5既存コンポーネントの再利用、`business-rules.md` 5.4）で
  表形式表示し、ページングあり時はページ送りUIを提供する（GEN-14）。読み取り専用検証エラー
  （`ValidationException`）・スキーマ許可リスト検証エラー（`PermissionDeniedException`）は
  エラーメッセージとして表示する。

### api.ts（`features/queryExecution/`）

| 関数 | 対応API | 責務 |
|---|---|---|
| `detectParams(sql)` | フロントエンド側の正規表現処理（`business-rules.md` 3節）、API呼び出しなし | パラメータ自動検出UI |
| `listAccessibleSchemas(connectionId)`（2026-07-15変更要求で新規追加） | `GET /api/query-execution/{connectionId}/schemas` | スキーマ選択UI（CHG-4） |
| `executeAdhocSql(connectionId, schema, sql, params, paging)` | `POST /api/query-execution/adhoc` | フロー2手順1〜9（手入力）。`schema`追加（2026-07-15変更要求） |
| `executeSavedQuery(connectionId, schema, savedQueryId, params, paging)` | `POST /api/query-execution/saved/{savedQueryId}` | フロー2手順1〜9（保存クエリ）。`schema`追加（2026-07-15変更要求） |

---

## features/queryHistory/

### コンポーネント階層

```
QueryHistoryListPage
```

### QueryHistoryListPage

- **状態**: `entries: HistoryEntry[]`, `filter: HistoryFilterCriteria`, `page: PageRequest`。
  （**2026-07-15変更要求**: `connectionId`はページ内stateではなくU1の`useConnection()`から
  グローバル接続コンテキストとして取得する）
- **責務**: `connectionId`（グローバルコンテキスト）が`null`の場合は「接続が指定されていません。」
  を表示する（CHG-3）。`connectionId`確立時、日時範囲・実行者スコープ（`ALL`/`SELF`）・SQL
  テキスト検索の絞り込みフォームを提供し、`listHistory`（フロー3手順1）を呼び出す
  （`/query-history`）。保存クエリ実行と直接入力実行を区別して表示する（`savedQueryId`の有無、
  GEN-15 AC）。**（2026-07-15変更要求）** 各行に実行時に対象としたスキーマ（`HistoryEntry.
  schema`）を列表示する（CHG-5）。マスキングされた行は`sql`/`savedQueryName`をプレースホルダ
  文字列としてそのまま表示する（バックエンドが既に置換済みの値を返すため、フロントエンド側での
  追加判定は不要）。`retired=true`の行には「廃止済み」バッジを表示する（`business-rules.md`
  5.3）。各行に「再実行」「保存」「ビルダーで編集」ボタンを配置し、`business-rules.md` 6節の
  遷移表に従いURLクエリパラメータ（`rawSql`、「再実行」「ビルダーで編集」時は`schema`も
  ——2026-07-15変更要求、CHG-5）を付与して`navigate`する（GEN-16、フロー4）。

### api.ts（`features/queryHistory/`）

| 関数 | 対応API | 責務 |
|---|---|---|
| `listHistory(connectionId, criteria, page)` | `GET /api/query-history` | フロー3手順1 |

---

## U6との連携（`business-rules.md` 6節）

U6の`GeneratedSqlPanel`の`onNavigateToSave`/`onNavigateToExecute` props（U6時点では未実装、
ボタンdisabled）は、U6のCode Generation（変更要求）時点で既に実装済み
（`QueryBuilderPage.tsx`の`handleNavigateToSave`/`handleNavigateToExecute`、U6
`frontend-summary.md`参照）。**2026-07-15変更要求時点の実装**は以下のとおり:

- `onNavigateToSave(generatedSql)`: `generatedSql.sql`を`rawSql`クエリパラメータとして
  `/saved-queries/new`へ`navigate`する（`connectionId`・`schema`は引き継がない、
  `SavedQuerySaveForm`は`connectionId`をグローバルコンテキストから取得し`schema`を保存対象と
  しないため）。
- `onNavigateToExecute(generatedSql)`: `generatedSql.sql`を`rawSql`、選択中の`schema`を
  `schema`クエリパラメータとして`/query-execution`へ`navigate`する（`connectionId`は
  グローバルコンテキストに従うため引き継がない）。

`querybuilder`（U6）の`QueryBuilderPage`は`rawSql`/`schema`クエリパラメータを受け付ける実装を
持つため（`connectionId`はグローバルコンテキスト参照に統一済み）、`queryHistory/`からの
「ビルダーで編集」遷移（GEN-16）で`rawSql`・`schema`を引き継げばよい——U6側の既存実装を
そのまま利用する。

`features/{savedQuery,queryExecution,queryHistory}/`は`masterData/`・`querybuilder/`等、
他featureのAPIを直接参照しない（`component-dependency.md`のとおり、`savedquery`が`common`
のみ、`queryhistory`が`common`＋新設の`savedquery`依存のみに限定されることに対応するフロント
エンド側の制約）。型定義（`ConnectionSummary`等）は他feature同様ローカルに再定義する
（`他feature非依存の方針`）。

---

## AppRouter.tsxへの追加

| パス | コンポーネント | 認可 |
|---|---|---|
| `/saved-queries` | `SavedQueryListPage` | `ProtectedRoute`（`requiredRole`指定なし） |
| `/saved-queries/new` | `SavedQuerySaveForm` | `ProtectedRoute`（同上） |
| `/saved-queries/{id}` | `SavedQueryDetailPage` | `ProtectedRoute`（同上） |
| `/query-execution` | `QueryExecutionPage` | `ProtectedRoute`（同上） |
| `/query-history` | `QueryHistoryListPage` | `ProtectedRoute`（同上） |

`AppLayout`（U1既存）のナビゲーションに「保存クエリ」「クエリ実行」「クエリ履歴」の3リンクを
追加する（全ユーザに表示、管理者限定ではない）。