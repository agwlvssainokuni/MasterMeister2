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
- **責務**: `listQueries(connectionId, includeRetired)`（フロー1手順2）を呼び出し、Public全件＋
  自分のPrivateを一覧表示する（`/saved-queries`）。「廃止済みも表示」トグルで`includeRetired`
  を切り替える。各行に「実行」「詳細」リンクを配置する。

### SavedQuerySaveForm

- **状態**: `name: string`, `visibility: Visibility`
- **Props**: なし（URLクエリパラメータ`rawSql`/`connectionId`から初期値を取得）
- **責務**: 名前・SQL（`rawSql`プリセット、`business-rules.md` 6節）・可視性を入力し
  `saveQuery`を呼び出す（GEN-10、フロー1手順1、`/saved-queries/new`）。保存成功後は
  `SavedQueryDetailPage`へ遷移する。

### SavedQueryDetailPage

- **状態**: `savedQuery: SavedQueryDetail | null`, `editing: boolean`
- **責務**: `getQuery`（フロー1手順3）で詳細を取得し表示する（`/saved-queries/{id}`）。
  作成者のみ「編集」「廃止」ボタンを活性化する（GEN-12）。編集モードでは`updateQuery`、
  廃止ボタン押下時は`ConfirmDialog`（U1既存）で確認後`retireQuery`を呼び出す
  （`business-rules.md` 1.3）。「実行」ボタンから`queryExecution/`の実行画面へ
  `savedQueryId`付きで遷移する。

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
  `paging: PagingOption`, `result: QueryResult | null`
- **責務**: URLクエリパラメータで`rawSql`（手入力実行、GEN-13）または`savedQueryId`
  （保存クエリ実行、GEN-11）のいずれかを受け取る（`/query-execution`）。`savedQueryId`
  指定時はSQL入力欄を読み取り専用にする（`business-rules.md` 1.4）。SQL入力/変更のたびに
  パラメータ自動検出（フロー2手順4）を行い、検出された`:param`ごとに値入力欄を表示する。
  ページング切替（あり/なし）・実行ボタンを提供し、`executeAdhocSql`/`executeSavedQuery`を
  呼び出す。結果は`DataTable`（U5既存コンポーネントの再利用、`business-rules.md` 5.4）で
  表形式表示し、ページングあり時はページ送りUIを提供する（GEN-14）。読み取り専用検証エラー
  （`ValidationException`）はエラーメッセージとして表示する。

### api.ts（`features/queryExecution/`）

| 関数 | 対応API | 責務 |
|---|---|---|
| `detectParams(sql)` | フロントエンド側の正規表現処理（`business-rules.md` 3節）、API呼び出しなし | パラメータ自動検出UI |
| `executeAdhocSql(connectionId, sql, params, paging)` | `POST /api/query-execution/adhoc` | フロー2手順1〜9（手入力） |
| `executeSavedQuery(connectionId, savedQueryId, params, paging)` | `POST /api/query-execution/saved/{savedQueryId}` | フロー2手順1〜9（保存クエリ） |

---

## features/queryHistory/

### コンポーネント階層

```
QueryHistoryListPage
```

### QueryHistoryListPage

- **状態**: `entries: HistoryEntry[]`, `filter: HistoryFilterCriteria`, `page: PageRequest`
- **責務**: 日時範囲・実行者スコープ（`ALL`/`SELF`）・SQLテキスト検索の絞り込みフォームを
  提供し、`listHistory`（フロー3手順1）を呼び出す（`/query-history`）。保存クエリ実行と
  直接入力実行を区別して表示する（`savedQueryId`の有無、GEN-15 AC）。マスキングされた行は
  `sql`/`savedQueryName`をプレースホルダ文字列としてそのまま表示する（バックエンドが既に
  置換済みの値を返すため、フロントエンド側での追加判定は不要）。`retired=true`の行には
  「廃止済み」バッジを表示する（`business-rules.md` 5.3）。各行に「再実行」「保存」
  「ビルダーで編集」ボタンを配置し、`business-rules.md` 6節の遷移表に従いURLクエリ
  パラメータ（`rawSql`、「ビルダーで編集」時は`connectionId`も）を付与して`navigate`する
  （GEN-16、フロー4）。

### api.ts（`features/queryHistory/`）

| 関数 | 対応API | 責務 |
|---|---|---|
| `listHistory(connectionId, criteria, page)` | `GET /api/query-history` | フロー3手順1 |

---

## U6との連携（`business-rules.md` 6節）

U6の`GeneratedSqlPanel`の`onNavigateToSave`/`onNavigateToExecute` props（U6時点では未実装、
ボタンdisabled）に、本ユニットのCode Generationで以下の実装を差し込む。

- `onNavigateToSave(generatedSql)`: `generatedSql.sql`を`rawSql`クエリパラメータとして
  `/saved-queries/new`へ`navigate`する。
- `onNavigateToExecute(generatedSql)`: `generatedSql.sql`を`rawSql`クエリパラメータとして
  `/query-execution`へ`navigate`する。

`querybuilder`（U6）の`QueryBuilderPage`は`rawSql`/`connectionId`クエリパラメータを既に
受け付ける実装を持つため、`queryHistory/`からの「ビルダーで編集」遷移（GEN-16）は追加実装
不要——U6側の既存実装をそのまま利用する。

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