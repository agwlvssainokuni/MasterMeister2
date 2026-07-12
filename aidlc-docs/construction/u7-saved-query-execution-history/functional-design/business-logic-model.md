# business-logic-model.md — U7: Saved Query / Execution / History

`domain-entities.md`・`business-rules.md`で定義したモデル・ルールに基づく業務フロー。
技術非依存（実装方式はNFR Design/Code Generationで確定）。

---

## フロー1: 保存クエリの保存・一覧・詳細・編集・廃止（GEN-10〜12）

**関与コンポーネント**: フロントエンド`savedQuery/`（`SavedQuerySaveForm`/`SavedQueryListPage`/
`SavedQueryDetailPage`） → `SavedQueryService`

1. `SavedQuerySaveForm`が名前・SQL・可視性（`Visibility`）を入力し`saveQuery`を呼び出す
   （GEN-10）。SQLはU6の`GeneratedSqlPanel`の「保存」ボタン、またはU7自身の実行画面から
   引き継いだ`rawSql`（`business-rules.md` 6節）が初期値となる。
2. `SavedQueryListPage`は`listQueries(userId, connectionId, includeRetired)`を呼び出し、
   Public全件＋自分のPrivateのみを取得する（`business-rules.md` 1.1）。
3. `SavedQueryDetailPage`は`getQuery`を呼び出す。Privateかつ作成者以外の場合は
   `PermissionDeniedException`（`business-rules.md` 1.1）。
4. 作成者は`SavedQueryDetailPage`から`updateQuery`（名前・SQL・可視性の変更、GEN-12）または
   `retireQuery`（廃止、`business-rules.md` 1.3）を実行できる。`retired=true`のクエリへの
   `updateQuery`は作成者本人であっても`EntityNotFoundException`となる。

---

## フロー2: SQLの実行（GEN-11, GEN-13, GEN-14）

**関与コンポーネント**: フロントエンド`queryExecution/`（`QueryExecutionPage`） →
`QueryExecutionService` → `SavedQueryService`（保存クエリ実行時のみ） →
`rdbmsconnection.ConnectionPoolRegistry`（U3） → `QueryHistoryService` → `AuditLogService`（U1）

1. `QueryExecutionPage`は手入力SQL（`executeAdhocSql`、GEN-13）または保存クエリID指定
   （`executeSavedQuery`、GEN-11）のいずれかで実行を要求する。保存クエリ実行時はSQL入力欄を
   読み取り専用にする（`business-rules.md` 1.4）。
2. `executeSavedQuery`は内部で`SavedQueryService`相当の可視性・`retired`チェックを行い
   （`business-rules.md` 1.4）、対象の`sql`を取得する。
3. JSqlParserで対象SQLをパースし、`Select`型でない場合、またはパースに失敗した場合は
   `ValidationException`とする（`business-rules.md` 2.1）。U6の`SqlParsingService`は経由しない。
4. SQL文字列から`:param`形式のパラメータを検出し（`business-rules.md` 3節）、リクエストで
   渡された`Map<String, Object>`と突き合わせて`NamedParameterJdbcTemplate`にバインドする。
5. ページングが「あり」の場合、対象SQLをサブクエリラップし`DialectStrategy.buildPagingClause`
   でLIMIT/OFFSET句を付与する（`business-rules.md` 4節）。「なし」の場合は
   `mm.app.query-execution.max-result-rows`（`business-rules.md` 7節）を適用する。
6. `ConnectionPoolRegistry.getJdbcTemplate`（U3既存）経由で対象RDBMSに対しSQLを実行し、
   `QueryResult`（`domain-entities.md`）を構築する。
7. `executeSavedQuery`の場合、`SavedQuery.executionCount`をアトミックにインクリメントする
   （`domain-entities.md`、Q3）。
8. `QueryHistoryService.recordExecution`と`AuditLogService.record`
   （`EventCategory.DATA_ACCESS` / `EventType.QUERY_EXECUTED`、U1既存の予約済みイベント種別）を
   明示的に呼び出す（同一トランザクション外・順不同、`services.md`の設計方針どおり）。
9. `QueryExecutionPage`が`QueryResult`を表形式で表示する（GEN-14）。

---

## フロー3: 実行履歴の一覧・絞り込み・マスキング（GEN-15）

**関与コンポーネント**: フロントエンド`queryHistory/`（`QueryHistoryListPage`） →
`QueryHistoryService` → `SavedQueryService`（可視性判定のみ）

1. `QueryHistoryListPage`が`HistoryFilterCriteria`（日時範囲・`executorScope`・
   `sqlTextSearch`）を指定し`listHistory`を呼び出す（`business-rules.md` 5.1）。
2. `QueryHistoryService`は`QueryHistory`単体に対する単純なクエリでページング・絞り込みを行う
   （`SavedQueryService`への問い合わせはこの時点では行わない）。
3. 取得したページ内の行のうち、`savedQueryId`が非nullかつ`row.userId != 閲覧者`の行について
   のみ、`SavedQueryService.getStatuses`（`domain-entities.md`の`SavedQueryStatus`）を1回
   呼び出す（`business-rules.md` 5.2）。
4. `visibleToViewer=false`の行は`sql`/`savedQueryName`/`params`をプレースホルダに差し替える。
   `retired=true`の行は可視性とは独立に「廃止済み」バッジを付与する（`business-rules.md` 5.3
   の可視性マトリクス）。
5. `QueryHistoryListPage`は各行に「再実行」「保存」「ビルダーで編集」ボタンを表示する
   （GEN-16、マスキングされた行でもボタン自体は表示するが、遷移時に引き継ぐ`rawSql`が
   プレースホルダ文字列であるため実質的に無意味——UI側でボタンを無効化するかは
   Code Generation時点で確定する）。

---

## フロー4: 履歴からの画面遷移（GEN-16）

**関与コンポーネント**: フロントエンド`queryHistory/`（`QueryHistoryListPage`） →
`queryExecution/`・`savedQuery/`・`querybuilder/`（U6）

1. `QueryHistoryListPage`の各行から「再実行」「保存」「ビルダーで編集」いずれかのボタンが
   押下されると、`business-rules.md` 6節の遷移表に従いURLクエリパラメータ（`rawSql`、
   「ビルダーで編集」の場合は`connectionId`も）を付与して`navigate`する。
2. 遷移先画面（`QueryExecutionPage`/`SavedQuerySaveForm`/U6の`QueryBuilderPage`）は、URL
   クエリパラメータから`rawSql`を読み取りSQL入力欄の初期値とする（`QueryBuilderPage`は
   U6で実装済みの`rawSql`受け入れをそのまま利用）。
3. パラメータ**値**は引き継がず、遷移先でパラメータ自動検出（フロー2手順4）により再度値入力を
   求める（`business-rules.md` 6節）。

---

## テスト可能な性質（Testable Properties, PBT-01）

`property-based-testing`拡張（enabled）のRule PBT-01に基づき、本ユニットの業務ロジック
（フロー1〜4）が持つ性質をカテゴリ別に識別する。実際のPBTケース設計・生成器定義はCode
Generation計画時に確定する。

| # | 対象 | カテゴリ | 性質 | 備考 |
|---|---|---|---|---|
| P1 | `SavedQueryService.listQueries`のフィルタ（フロー1） | Invariant | 返される`SavedQuery`一覧は常に「`visibility=PUBLIC`」または「`ownerId==呼び出しユーザ`」のいずれかを満たすもののみ——他ユーザのPrivateなクエリが含まれることは一切ない | `business-rules.md` 1.1 |
| P2 | `SavedQueryService`の`retired`状態遷移（フロー1） | Invariant（状態遷移） | `retired=true`の`SavedQuery`に対する`updateQuery`/`executeSavedQuery`は常に`EntityNotFoundException`となり、`getQuery`は可視性条件のみで成否が決まる（`retired`の値に影響されない） | `business-rules.md` 1.3 |
| P3 | `retireQuery`の一方向性（フロー1） | Invariant | 本ユニットのいかなる操作を経ても、一度`retired=true`になった`SavedQuery`が`retired=false`に戻ることは一切ない（復元APIが存在しない） | `business-rules.md` 1.3 |
| P4 | `QueryExecutionService`の読み取り専用検証（フロー2） | Invariant | JSqlParserでパースした結果が`Select`型でない、またはパース自体に失敗したSQLを`executeAdhocSql`/`executeSavedQuery`に渡した場合、常に`ValidationException`となり対象RDBMSへの実行は一切発生しない | `business-rules.md` 2.1 |
| P5 | パラメータ自動検出（フロー2） | Invariant | 文字列リテラル外に出現する`:paramName`形式の識別子集合と、検出される`DetectedParam`名の集合は常に一致する | `business-rules.md` 3節 |
| P6 | ページング適用時の件数上限（フロー2） | Invariant（境界値） | `PagingOption.enabled=true`の場合、`QueryResult.rows`の件数は常に`pageSize`以下。`enabled=false`の場合は常に`max-result-rows`以下であり、実件数がこれを超える場合に限り`QueryResult.truncated=true`となる | `business-rules.md` 4節, 7節 |
| P7 | 実行のたびの二重記録（フロー2） | Invariant | `executeAdhocSql`/`executeSavedQuery`が成功するたびに、`QueryHistory`が常に1件追加され、かつ`AuditLog`（`eventType=QUERY_EXECUTED`）が常に1件追加される（一方のみが記録されることはない） | `business-rules.md` 8節、`services.md` |
| P8 | `executionCount`のインクリメント整合性（フロー2） | Invariant | `executeSavedQuery`が成功するたびに、対象`SavedQuery.executionCount`は呼び出し前の値からちょうど1増加し、生成される`QueryHistory.executionCount`はインクリメント後の値と常に一致する | `business-rules.md` 1.4, `domain-entities.md` Q3 |
| P9 | 実行履歴のマスキング（フロー3） | Invariant | `listHistory`が返す行のうち、`savedQueryId`が非nullかつ`row.userId != 閲覧者`の行について、`sql`/`savedQueryName`/`params`がプレースホルダ化されるのは、参照先`SavedQuery`が「`retired=false`かつ（`visibility=PUBLIC`または`ownerId==閲覧者`）」を満たさない場合に限る（それ以外の行は常にフル表示） | `business-rules.md` 5.2, 5.3 |
| P10 | 「廃止済み」バッジの独立性（フロー3） | Invariant | `listHistory`が返す行の「廃止済み」バッジ付与は、同じ行のマスキング有無（P9）とは独立に、参照先`SavedQuery.retired`の値のみで決まる | `business-rules.md` 5.2, 5.3 |