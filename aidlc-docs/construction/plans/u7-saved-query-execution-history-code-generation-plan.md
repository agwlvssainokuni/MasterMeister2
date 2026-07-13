# u7-saved-query-execution-history-code-generation-plan.md

U7（Saved Query / Execution / History）の Code Generation 計画。本ドキュメントが Code
Generation の単一の真実源（single source of truth）であり、Part 2（Generation）はこの計画の
ステップを順に実行する。ワークスペースルート: `~/Documents/project/git/MasterMeister2`
（`aidlc-state.md` Workspace Root）。アプリケーションコードはワークスペースルート配下
（`backend/`, `frontend/`）にのみ生成し、`aidlc-docs/` にはドキュメント成果物のみ生成する。

---

## ユニットコンテキスト（code-generation.md Step 3）

### 対応ストーリー

GEN-10〜GEN-16（`unit-of-work-story-map.md`）:

| ID | タイトル |
|---|---|
| GEN-10 | クエリの保存（名前・可視性指定） |
| GEN-11 | 保存済みクエリの一覧・実行 |
| GEN-12 | 保存済みクエリの編集・削除（廃止） |
| GEN-13 | 手入力SQLの実行（読み取り専用検証） |
| GEN-14 | クエリ実行結果の表示・ページング |
| GEN-15 | 実行履歴の一覧・絞り込み |
| GEN-16 | 実行履歴からの再実行・保存・ビルダー編集への遷移 |

### 他ユニットへの依存

`component-dependency.md`確定済み: `savedquery → common`、`queryexecution → common, audit,
rdbmsconnection, queryhistory, savedquery`、`queryhistory → common, savedquery`。

- `common`（U1既存）: `common.exception`配下の`EntityNotFoundException`/`ValidationException`/
  `PermissionDeniedException`（いずれも既存、新規例外なし——`domain-entities.md`確定）、
  `common.PageRequest`/`PageResult`、`common.dialect.DialectStrategy`/
  `DialectStrategyFactory`（`PagingSqlBuilder`が利用）。
- `audit`（U1）: `AuditLogService.record`（`EventCategory.DATA_ACCESS` /
  `EventType.QUERY_EXECUTED`、既存の予約済みイベント種別）。
- `rdbmsconnection`（U3）: `RdbmsConnectionRepository.findById`（`DialectStrategy`解決用）、
  `ConnectionPoolRegistry.getJdbcTemplate`（対象RDBMSへのSELECT実行）。
- `queryhistory`/`savedquery`は`queryexecution`から呼び出される（同一ユニット内の3パッケージ
  間の依存）。
- `querybuilder`（U6）: バックエンド依存なし。フロントエンドのURLクエリパラメータ経由の連携
  のみ（`business-rules.md` 6節）——U6の`GeneratedSqlPanel.onNavigateToSave`/
  `onNavigateToExecute`props実装を本ユニットのStep 11で差し込む（ブラウンフィールド修正）。
- `permission`（U4）: 依存しない（`business-rules.md` 2.2、意図的な設計判断）。

### 提供インタフェース・契約（他ユニットが依存する公開API）

- なし。`unit-of-work-dependency.md`上、U7に依存する後続ユニットは存在しない（最終ユニット）。

### 本ユニットが所有するデータエンティティ（内部DB/JPA）

`domain-entities.md`確定（Q1 = A）のとおり、`SavedQuery`（`savedquery`パッケージ）・
`QueryHistory`（`queryhistory`パッケージ）の2エンティティのみ。`queryexecution`は専用の
内部DBエンティティを持たない（ステートレスな実行ロジックのみ）。

### パッケージ設計判断（`nfr-design-patterns.md`/`logical-components.md`からの継承）

- `SavedQueryService`（`savedquery`）・`QueryExecutionService`＋`ReadOnlySqlValidator`/
  `SqlParamDetector`/`PagingSqlBuilder`（`queryexecution`）・`QueryHistoryService`
  （`queryhistory`）は、それぞれのパッケージに配置する。`common`への切り出しは行わない
  （`nfr-design-patterns.md` 1.1）。
- `QueryHistory`に`(connectionId, executedAt)`・`savedQueryId`の明示的インデックスを追加。
  `SavedQuery`は追加インデックスなし（`nfr-design-patterns.md` 2）。
- `QueryHistoryService.recordExecution`は`AuditLogService.record`と同じ`REQUIRES_NEW`＋
  非伝播パターン（`nfr-design-patterns.md` 1.3）。
- GEN-13入力SQLのJSqlParserタイムアウト制御は共有`ExecutorService`＋`Future.get`方式、
  超過時は`ValidationException`（`nfr-design-patterns.md` 1.2）。

### サービス境界・責務

- `savedquery`: `SavedQueryService`（`saveQuery`/`getQuery`/`updateQuery`/`retireQuery`/
  `listQueries`/`getStatuses`、`business-rules.md` 1節）、`SavedQueryController`（REST API、
  8節）。
- `queryexecution`: `QueryExecutionService`（`executeAdhocSql`/`executeSavedQuery`、
  オーケストレーション）、`ReadOnlySqlValidator`（読み取り専用検証、2節）、
  `SqlParamDetector`（パラメータ検出、3節）、`PagingSqlBuilder`（ページングSQL組み立て、
  4節）、`QueryExecutionController`（REST API、8節）。
- `queryhistory`: `QueryHistoryService`（`recordExecution`/`listHistory`、5節）、
  `QueryHistoryController`（REST API、8節）。
- `security`（U1、ブラウンフィールド拡張）: `SecurityConfig`に3パッケージのエンドポイント用
  `authenticated()`マッチャを追記する。
- フロントエンド: `features/savedQuery/`・`features/queryExecution/`・`features/queryHistory/`
  （`frontend-components.md`）。U6の`GeneratedSqlPanel`を修正し`onNavigateToSave`/
  `onNavigateToExecute`を実装する。

### テスト可能な性質（PBT-01、`business-logic-model.md`で識別済み）

P1〜P10（`business-logic-model.md`「テスト可能な性質」表）。Step 3で対応する`@Property`テストを
生成する。

### Code Generation時点で確定する事項（Functional Design/NFR Designで「Code Generationで確定」と
留保されていた論点）

1. **REST APIパスの確定**: `business-rules.md` 8節記載のパスをそのまま採用する（U6と異なり
   `{connectionId}`をクラスレベルパスに含めない——`SavedQuery`/`QueryHistory`エンティティが
   `connectionId`を自身のフィールドとして持つため、リソースパスに含める必然性がない）。
   `connectionId`はリクエストボディ（POST/PUT）またはクエリパラメータ（GET）で渡す。
   `executeSavedQuery`のリクエストボディにも`connectionId`を含め、`SavedQuery.connectionId`
   との一致を`QueryExecutionService`内で検証する（防御的二重チェック、不一致時は
   `ValidationException`）。
2. **`QueryHistory.params`（`Map<String, Object>`）の永続化方式**: `rdbmsconnection`パッケージの
   `EncryptedStringConverter`（`AttributeConverter<String, String>`）と同じ`@Convert`パターンを
   踏襲し、`JsonMapConverter`（`AttributeConverter<Map<String, Object>, String>`、Jackson
   `ObjectMapper`によるJSON文字列化）を`queryhistory`パッケージに新規実装する。DBカラムは
   `@Lob`（`nfr-requirements.md` 3.1）。
3. **`SavedQueryService.getQuery`と`executeSavedQuery`用の内部メソッドの分離**:
   `business-rules.md` 1.1（`getQuery`は`retired`を無視）と1.4（`executeSavedQuery`は`retired`
   を`EntityNotFoundException`で拒否）は異なる検証を要求するため、`SavedQueryService`に
   `getQuery`（公開API用）とは別に、パッケージプライベートではなく`queryexecution`から呼ばれる
   ため`public`な`SavedQueryDetail getExecutableQuery(Long userId, Long savedQueryId)`
   （`retired=true`は`EntityNotFoundException`、可視性チェックは`getQuery`と共通ロジックを
   private メソッドへ切り出して再利用）を追加する。実行成功後の`executionCount`加算は
   `int incrementExecutionCount(Long savedQueryId)`（`@Modifying @Query`によるアトミック
   UPDATE、`nfr-requirements.md` 2.2）を`SavedQueryService`に追加し、更新後の値を返す
   （`QueryHistory.executionCount`スナップショットに利用）。
4. **例外ハンドリング**: `GlobalExceptionHandler`（U1既存、`config`パッケージ）が
   `EntityNotFoundException`→404、`PermissionDeniedException`→403、`ValidationException`→400
   を既にマッピング済みのため、本ユニットは新規のExceptionHandlerを追加しない（既存の
   マッピングにそのまま乗る）。
5. **`SqlParamDetector`の正規表現**: `business-rules.md` 3節の方針（`:paramName`検出、文字列
   リテラル内・PostgreSQL`::type`キャストの誤検知回避）を実装する簡易ステートマシン（1文字
   ずつスキャンし、シングルクォート文字列リテラル区間をスキップ、`::`直後の識別子は
   パラメータとして扱わない）を`SqlParamDetector`に実装する。正規表現一発では文字列リテラル
   スキップの実現が困難なため、スキャナ方式を採用する（`business-rules.md`の「正規表現で」
   という記載は概念的な説明であり、実装は文字単位スキャンで同等の結果を得る）。
6. **`PagingSqlBuilder`とページなし時の`max-result-rows`適用方式**: ページングありは
   `business-rules.md` 4節のサブクエリラップ方式。ページングなしは`NamedParameterJdbcTemplate.
   query`のコールバックで結果行数が`max-result-rows`を超えた時点で読み取りを打ち切り
   （`RowCallbackHandler`または`ResultSetExtractor`で件数カウントし上限到達で終了）、
   `QueryResult.truncated = true`を設定する。SQL自体にLIMITを追加しない（元のSQLが既に
   ORDER BY等を含む場合の意味変更を避けるため、JDBC側の読み取り件数制御で対応する）。

---

## ステップ一覧

### Step 1: プロジェクト構造セットアップ
- [x] 1-1. **該当なし（N/A）**: JSqlParserはU6で`backend/build.gradle.kts`に導入済み
      （`tech-stack-decisions.md`確定）。新規依存追加はない。

### Step 2: ビジネスロジック生成
- [x] 2-1. `backend/src/main/java/cherry/mastermeister/savedquery/Visibility.java`（enum:
      `PUBLIC, PRIVATE`）を生成する（`domain-entities.md`確定）。
- [x] 2-2. `backend/src/main/java/cherry/mastermeister/savedquery/SavedQuery.java`
      （`@Entity @Table(name = "saved_query")`）: `id, ownerId, connectionId, name,
      sql（@Lob）, visibility（@Enumerated(EnumType.STRING)）, retired（既定false）,
      executionCount（既定0）, createdAt, updatedAt`を生成する（`domain-entities.md`確定、
      `@ManyToOne`等の関係アノテーションは使用せず素の`Long`型FKフィールド、U1〜U6踏襲の
      JPA規約）。
- [x] 2-3. `backend/src/main/java/cherry/mastermeister/savedquery/`にAPI境界DTOを生成する
      （`domain-entities.md`確定）: `SavedQuerySummary`（record: `id, name, visibility,
      retired, ownerId`）、`SavedQueryDetail`（record: `id, ownerId, connectionId, name, sql,
      visibility, retired, executionCount, createdAt, updatedAt`）、`SavedQueryStatus`
      （record: `visibleToViewer, retired`）。
- [x] 2-4. `backend/src/main/java/cherry/mastermeister/savedquery/SavedQueryRepository.java`
      （`JpaRepository<SavedQuery, Long>`）: `listQueries`（`business-rules.md` 1.1）に必要な
      検索メソッド（`connectionId`＋可視性/所有者条件）、`getStatuses`（5.2）に必要な
      `findAllById`相当、`incrementExecutionCount`用の`@Modifying @Query("UPDATE SavedQuery s
      SET s.executionCount = s.executionCount + 1 WHERE s.id = :id")`
      （`clearAutomatically = true`、`nfr-requirements.md` 2.2）を定義する。具体的なクエリ
      メソッドシグネチャはStep 2-5実装時の呼び出し箇所に合わせて確定する（U3/U4 Step 8と
      同様、Service先行実装との整合を優先）。
- [x] 2-5. `backend/src/main/java/cherry/mastermeister/savedquery/SavedQueryService.java`
      （`@Service`）: `Long saveQuery(Long userId, Long connectionId, String name, String sql,
      Visibility visibility)`、`SavedQueryDetail getQuery(Long userId, Long savedQueryId)`
      （`business-rules.md` 1.1、`retired`は無視）、`SavedQueryDetail
      getExecutableQuery(Long userId, Long savedQueryId)`（1.4、`retired=true`は
      `EntityNotFoundException`、上記「Code Generation時点で確定する事項」3）、
      `void updateQuery(Long userId, Long savedQueryId, String name, String sql, Visibility
      visibility)`（1.2、`retired=true`は作成者含め全員`EntityNotFoundException`）、
      `void retireQuery(Long userId, Long savedQueryId)`（1.3、作成者のみ、一方向）、
      `List<SavedQuerySummary> listQueries(Long userId, Long connectionId, boolean
      includeRetired)`（1.1）、`Map<Long, SavedQueryStatus> getStatuses(Long viewerId,
      Set<Long> savedQueryIds)`（5.2、`queryhistory`から呼ばれる）、
      `int incrementExecutionCount(Long savedQueryId)`（`queryexecution`から呼ばれる）を
      実装する。可視性チェック（Public/Private＋所有者判定）はprivateメソッドへ切り出し
      `getQuery`/`getExecutableQuery`/`updateQuery`/`retireQuery`間で共通化する。
- [x] 2-6. `backend/src/main/java/cherry/mastermeister/queryexecution/`にAPI境界DTOを生成する
      （`domain-entities.md`確定）: `QueryResult`（record: `columns, rows, totalRows,
      truncated`）、`ResultColumn`（record: `columnName, dataType`）、`DetectedParam`
      （record: `name`）、`PagingOption`（record: `enabled, page, pageSize`）。
- [x] 2-7. `backend/src/main/java/cherry/mastermeister/queryexecution/ReadOnlySqlValidator.java`
      （`nfr-design-patterns.md` 1.1、1.2）: `void validate(String sql)`
      （`business-rules.md` 2.1、失敗時`ValidationException`）を実装する。固定サイズ共有
      `ExecutorService`（`@PreDestroy`で`shutdown`、`mm.app.query-execution.
      parse-executor-pool-size`）に`CCJSqlParserUtil.parse(sql)`を`submit`し、
      `Future.get(parseTimeoutSeconds, TimeUnit.SECONDS)`でタイムアウト制御
      （`mm.app.query-execution.parse-timeout`）。`mm.app.query-execution.sql-max-length`
      超過は呼び出し前に`ValidationException`。パース失敗・タイムアウト・`Select`型でない
      場合はいずれも`ValidationException`（U6と異なりnotice応答はない、
      `nfr-design-patterns.md` 1.2）。
- [x] 2-8. `backend/src/main/java/cherry/mastermeister/queryexecution/SqlParamDetector.java`
      （`nfr-design-patterns.md` 1.1）: `List<DetectedParam> detect(String sql)`
      （`business-rules.md` 3節、上記「Code Generation時点で確定する事項」5のスキャナ方式）を
      実装する。
- [x] 2-9. `backend/src/main/java/cherry/mastermeister/queryexecution/PagingSqlBuilder.java`
      （`nfr-design-patterns.md` 1.1）: `String wrapWithPaging(String sql, DialectStrategy
      dialect, int limit, int offset)`（`business-rules.md` 4節、`SELECT * FROM (<sql>) AS
      subquery`＋`DialectStrategy.buildPagingClause`）を実装する。
- [x] 2-10. `backend/src/main/java/cherry/mastermeister/queryexecution/QueryExecutionService.java`
      （`@Service`）: `QueryResult executeAdhocSql(Long userId, Long connectionId, String sql,
      Map<String, Object> params, PagingOption paging)`、`QueryResult
      executeSavedQuery(Long userId, Long connectionId, Long savedQueryId, Map<String,
      Object> params, PagingOption paging)`を実装する（`business-logic-model.md`フロー2）。
      `executeSavedQuery`は`SavedQueryService.getExecutableQuery`でSQL取得＋`connectionId`
      一致検証（上記確定事項1）。共通処理: `ReadOnlySqlValidator.validate`→
      `RdbmsConnectionRepository`+`DialectStrategyFactory.resolve`でDialectStrategy解決→
      ページングあり:`PagingSqlBuilder.wrapWithPaging`／なし:`mm.app.query-execution.
      max-result-rows`件で打ち切り（上記確定事項6）→`ConnectionPoolRegistry.getJdbcTemplate`
      （U3）で`setQueryTimeout`（`mm.app.query-execution.query-timeout`）を設定し
      `NamedParameterJdbcTemplate.query`実行→`QueryResult`構築→（`executeSavedQuery`のみ）
      `SavedQueryService.incrementExecutionCount`→`QueryHistoryService.recordExecution`・
      `AuditLogService.record`（`EventType.QUERY_EXECUTED`）を呼び出し（いずれも失敗非伝播、
      `nfr-design-patterns.md` 1.3）→`QueryResult`を返す。
- [x] 2-11. `backend/src/main/java/cherry/mastermeister/queryhistory/JsonMapConverter.java`
      （`AttributeConverter<Map<String, Object>, String>` `@Converter`、上記「Code
      Generation時点で確定する事項」2）を実装する。
- [x] 2-12. `backend/src/main/java/cherry/mastermeister/queryhistory/QueryHistory.java`
      （`@Entity @Table(name = "query_history", indexes = {@Index(columnList = "connectionId,
      executedAt"), @Index(columnList = "savedQueryId")})`）: `id, userId, connectionId,
      sql（@Lob）, params（@Convert(converter = JsonMapConverter.class) @Lob）, resultCount,
      elapsedMillis, executedAt, savedQueryId（nullable）, savedQueryName（nullable）,
      executionCount（nullable Integer）`を生成する（`domain-entities.md`確定、
      `nfr-design-patterns.md` 2）。
- [x] 2-13. `backend/src/main/java/cherry/mastermeister/queryhistory/`にDTO・enumを生成する
      （`domain-entities.md`確定）: `ExecutorScope`（enum: `ALL, SELF`）、
      `HistoryFilterCriteria`（record: `executedAtFrom, executedAtTo, executorScope,
      sqlTextSearch`）、`HistoryEntry`（record: `id, userId, connectionId, sql, params,
      resultCount, elapsedMillis, executedAt, savedQueryId, savedQueryName, executionCount,
      retired, masked`——`masked`は`business-rules.md` 5.2のマスキング済みフラグ、
      `domain-entities.md`にない追加フィールドだがフロントエンド側でバッジ・プレースホルダ
      表示分岐に必要なため、5.3可視性マトリクスに基づき追加する）、`ExecutionRecord`
      （record: `userId, connectionId, sql, params, resultCount, elapsedMillis, executedAt,
      savedQueryId, savedQueryName, executionCount`——`recordExecution`の引数型、
      `component-methods.md`草案を踏襲）。
- [x] 2-14. `backend/src/main/java/cherry/mastermeister/queryhistory/QueryHistoryRepository.java`
      （`JpaRepository<QueryHistory, Long>`）: `listHistory`の絞り込み・ページングに必要な
      `@Query`（`connectionId`＋日時範囲＋`executorScope`（`userId`条件）＋`sqlTextSearch`
      （LIKE））を定義する。具体的なクエリメソッドシグネチャはStep 2-15実装時に確定する。
- [x] 2-15. `backend/src/main/java/cherry/mastermeister/queryhistory/QueryHistoryService.java`
      （`@Service`）: `void recordExecution(ExecutionRecord record)`
      （`@Transactional(propagation = Propagation.REQUIRES_NEW)`＋`try-catch`でログ出力のみ、
      `nfr-design-patterns.md` 1.3）、`PageResult<HistoryEntry> listHistory(Long userId, Long
      connectionId, HistoryFilterCriteria criteria, PageRequest page)`
      （`business-rules.md` 5.1-5.3: `QueryHistoryRepository`から単純ページング取得→ページ内の
      `savedQueryId != null && row.userId != userId`行についてのみ`SavedQueryService.
      getStatuses`を1回呼び出し→`visibleToViewer=false`は`sql`/`savedQueryName`/`params`を
      プレースホルダ化し`masked=true`、`retired=true`は独立に`retired`フラグを立てて返す）を
      実装する。設定キー`mm.app.query-history.default-page-size`/`page-size-options`
      （`AuditLogService.resolvePageSize`と同じ検証ロジック）を適用する。

### Step 3: ビジネスロジック単体テスト（PBT-01〜PBT-08, PBT-10）
`business-logic-model.md`のP1〜P10に対応する`@Property`テストをjqwikで生成する。
`queryexecution`は対象RDBMSへの実アクセスを伴うため、U3〜U6と同じ手法
（`org.h2.tools.Server`によるH2 TCPサーバを対象RDBMS役として起動、`@SpringBootTest`
`@JqwikSpringSupport`）を用いる。`savedquery`/`queryhistory`は内部DB（JPA）のみで完結するため
`@SpringBootTest`（組み込みH2内部DB）で足りる。
- [x] 3-1. **P1**（`listQueries`の可視性フィルタInvariant）・**P2**（`retired`状態遷移
      Invariant）・**P3**（`retireQuery`一方向性Invariant）: `SavedQueryServiceTest`に
      `@Property`テストを生成する。
- [x] 3-2. **P4**（読み取り専用検証Invariant）: `ReadOnlySqlValidatorTest`に`@Property`テストを
      生成する。
- [x] 3-3. **P5**（パラメータ検出round-tripInvariant）: `SqlParamDetectorTest`に`@Property`
      テストを生成する。
- [x] 3-4. **P6**（ページング件数上限Invariant）・**P7**（QueryHistory・AuditLog二重記録
      Invariant）・**P8**（`executionCount`インクリメント整合性Invariant）:
      `QueryExecutionServiceTest`（H2 TCPサーバ経由）に`@Property`テストを生成する。
- [x] 3-5. **P9**（実行履歴マスキングInvariant）・**P10**（「廃止済み」バッジ独立性
      Invariant）: `QueryHistoryServiceTest`に`@Property`テストを生成する。

### Step 4: ビジネスロジックサマリ
- [x] 4-1. `aidlc-docs/construction/u7-saved-query-execution-history/code/
      business-logic-summary.md`を生成し、Step 2・Step 3で生成したクラス一覧とP1〜P10の
      対応関係を表形式で記載する（U1〜U6の`business-logic-summary.md`と同一構成）。

### Step 5: APIレイヤ生成
- [x] 5-1. `backend/src/main/java/cherry/mastermeister/savedquery/SavedQueryController.java`
      （`@RestController @RequestMapping("/api/saved-queries")`）: `GET ""`
      （`connectionId`/`includeRetired`クエリパラメータ、`listQueries`）、`POST ""`
      （`saveQuery`）、`GET "/{savedQueryId}"`（`getQuery`）、`PUT "/{savedQueryId}"`
      （`updateQuery`）、`POST "/{savedQueryId}/retire"`（`retireQuery`）を生成する
      （`business-rules.md` 8節、上記「Code Generation時点で確定する事項」1）。`userId`は
      `Authentication#getPrincipal()`キャスト取得（U2〜U6のコントローラと同一パターン）。
- [x] 5-2. `backend/src/main/java/cherry/mastermeister/queryexecution/QueryExecutionController.java`
      （`@RestController @RequestMapping("/api/query-execution")`）: `POST "/adhoc"`
      （`executeAdhocSql`）、`POST "/saved/{savedQueryId}"`（`executeSavedQuery`）を生成する
      （`business-rules.md` 8節）。
- [x] 5-3. `backend/src/main/java/cherry/mastermeister/queryhistory/QueryHistoryController.java`
      （`@RestController @RequestMapping("/api/query-history")`）: `GET ""`
      （`connectionId`/日時範囲/`executorScope`/`sqlTextSearch`/ページングクエリパラメータ、
      `listHistory`）を生成する（`business-rules.md` 8節）。
- [x] 5-4. `backend/src/main/java/cherry/mastermeister/security/SecurityConfig.java`
      （既存、ブラウンフィールド修正）に`.requestMatchers("/api/saved-queries/**")
      .authenticated()`、`.requestMatchers("/api/query-execution/**").authenticated()`、
      `.requestMatchers("/api/query-history/**").authenticated()`を、`/api/query-builder/**`
      と同様の場所に追記する（`business-rules.md` 8節「認証済みユーザ全員」）。

### Step 6: APIレイヤ単体テスト
- [x] 6-1. `SavedQueryControllerTest`（`@WebMvcTest` + `spring-security-test`）: 5エンドポイント
      それぞれについて認証済みユーザ成功系・未認証401をexample-basedテストで検証する
      （U2〜U6のControllerTestパターンを踏襲、管理者ロール制約がないため403系テストは
      不要）。
- [x] 6-2. `QueryExecutionControllerTest`（同上）: 2エンドポイントの成功系・未認証401、
      読み取り専用違反SQLでの400（`ValidationException`）をテストする。
- [x] 6-3. `QueryHistoryControllerTest`（同上）: 1エンドポイントの成功系・未認証401をテストする。

### Step 7: APIレイヤサマリ
- [x] 7-1. `aidlc-docs/construction/u7-saved-query-execution-history/code/
      api-layer-summary.md`を生成し、8エンドポイント一覧（パス・メソッド・認可要件・
      リクエスト/レスポンス形状）を記載する。

### Step 8: リポジトリレイヤ生成
- [x] 8-1. （item 2-4の直後に暫定実装として先行実施、U3/U4と同じ実行順序変更）
      `SavedQueryRepository`の最終的なクエリメソッドシグネチャを確定する（`listQueries`用の
      `connectionId`＋可視性/所有者/`retired`条件、`incrementExecutionCount`の
      `@Modifying @Query`）。
- [x] 8-2. （item 2-14の直後に暫定実装として先行実施）`QueryHistoryRepository`の最終的な
      クエリメソッドシグネチャを確定する（`listHistory`用の`connectionId`＋日時範囲＋
      `executorScope`＋`sqlTextSearch`の複合`@Query`、ページング対応）。

### Step 9: リポジトリレイヤ単体テスト
- [x] 9-1. `SavedQueryRepositoryTest`（`@DataJpaTest`、組み込みH2）: 基本CRUD、
      `incrementExecutionCount`の並行実行時の整合性（複数スレッドからの同時呼び出しで
      加算漏れが発生しないこと）、`listQueries`相当のクエリメソッドをexample-basedテストで
      検証する。
- [x] 9-2. `QueryHistoryRepositoryTest`（同上）: 基本CRUD、`listHistory`相当のクエリメソッド
      （絞り込み・ページング・日時範囲）をexample-basedテストで検証する。

### Step 10: リポジトリレイヤサマリ
- [x] 10-1. `aidlc-docs/construction/u7-saved-query-execution-history/code/
      repository-layer-summary.md`を生成し、2リポジトリのクエリメソッド一覧とインデックス
      設計（`nfr-design-patterns.md` 2）を記載する。

### Step 11: フロントエンドコンポーネント生成
- [x] 11-1. `frontend/src/features/savedQuery/types.ts`・`api.ts`:
      `frontend-components.md`・`domain-entities.md`のDTOに対応するTypeScript型
      （`Visibility`/`SavedQuerySummary`/`SavedQueryDetail`）と`listQueries`/`saveQuery`/
      `getQuery`/`updateQuery`/`retireQuery`（Step 5-1確定の実パスに対応）を実装する。
- [x] 11-2. `frontend/src/features/savedQuery/SavedQueryListPage.tsx`・
      `SavedQuerySaveForm.tsx`・`SavedQueryDetailPage.tsx`: `frontend-components.md`確定の
      3ページを生成する（GEN-10〜12）。
- [x] 11-3. `frontend/src/features/queryExecution/types.ts`・`api.ts`:
      `QueryResult`/`ResultColumn`/`DetectedParam`/`PagingOption`型と`detectParams`
      （フロントエンド内正規表現処理）/`executeAdhocSql`/`executeSavedQuery`を実装する。
- [x] 11-4. `frontend/src/features/queryExecution/QueryExecutionPage.tsx`:
      `frontend-components.md`確定のSQL実行画面を生成する（GEN-11, GEN-13, GEN-14）。
- [x] 11-5. `frontend/src/features/queryHistory/types.ts`・`api.ts`: `HistoryEntry`/
      `HistoryFilterCriteria`/`ExecutorScope`型と`listHistory`を実装する。
- [x] 11-6. `frontend/src/features/queryHistory/QueryHistoryListPage.tsx`:
      `frontend-components.md`確定の履歴一覧画面（絞り込みフォーム、マスキング表示、
      「廃止済み」バッジ、「再実行」「保存」「ビルダーで編集」ボタン）を生成する
      （GEN-15, GEN-16）。
- [x] 11-7. `frontend/src/features/queryBuilder/GeneratedSqlPanel.tsx`（既存、ブラウンフィールド
      修正）: `onNavigateToSave`/`onNavigateToExecute`propsのデフォルト実装（`useNavigate`で
      `rawSql`クエリパラメータ付き`/saved-queries/new`・`/query-execution`への遷移）を
      `QueryBuilderPage.tsx`（既存、ブラウンフィールド修正）から渡すよう配線する
      （`frontend-components.md`「U6との連携」節）。
- [x] 11-8. `frontend/src/routes/AppRouter.tsx`（既存、ブラウンフィールド修正）に
      `/saved-queries`・`/saved-queries/new`・`/saved-queries/{id}`・`/query-execution`・
      `/query-history`（いずれも`ProtectedRoute`・`requiredRole`指定なし）を追加する。
      `frontend/src/components/AppLayout.tsx`（既存、ブラウンフィールド修正）に「保存クエリ」
      「クエリ実行」「クエリ履歴」ナビゲーションリンクを全ユーザ表示で追加する
      （`frontend-components.md` AppRouter.tsxへの追加）。

### Step 12: フロントエンドコンポーネント単体テスト
- [x] 12-1. `SavedQueryListPage.test.tsx`・`SavedQuerySaveForm.test.tsx`・
      `SavedQueryDetailPage.test.tsx`・`QueryExecutionPage.test.tsx`・
      `QueryHistoryListPage.test.tsx`（vitest + Testing Library、U4〜U6の各featureテスト
      パターンを踏襲）を生成する。
- [x] 12-2. `GeneratedSqlPanel.test.tsx`（既存、ブラウンフィールド修正）に`onNavigateToSave`/
      `onNavigateToExecute`実装（item 11-7）のテストケースを追加する。

### Step 13: フロントエンドコンポーネントサマリ
- [x] 13-1. `aidlc-docs/construction/u7-saved-query-execution-history/code/
      frontend-summary.md`を生成する。

### Step 14: データベースマイグレーションスクリプト
- [x] 14-1. **該当なし（N/A）**: 本プロジェクトは`ddl-auto: update`方式（U1〜U6踏襲）のため、
      専用マイグレーションスクリプトは生成しない。`SavedQuery`/`QueryHistory`エンティティの
      追加により起動時に自動でテーブル・インデックスが作成される。

### Step 15: ドキュメント生成
- [x] 15-1. `aidlc-docs/construction/u7-saved-query-execution-history/code/
      testing-summary.md`（P1〜P10とテストクラスの対応表、example-basedテスト一覧、実行
      確認状況）を生成する。

### Step 16: デプロイ成果物生成
- [ ] 16-1. `backend/src/main/resources/application.yml`（既存、ブラウンフィールド修正）に
      `mm.app.query-history.default-page-size`（既定`50`）、
      `mm.app.query-history.page-size-options`（既定`50,100,200`）、
      `mm.app.query-execution.query-timeout`（既定`30`秒）、
      `mm.app.query-execution.sql-max-length`（既定`10000`）、
      `mm.app.query-execution.parse-timeout`（既定`5`秒）、
      `mm.app.query-execution.parse-executor-pool-size`（既定`4`）、
      `mm.app.query-execution.max-result-rows`（既定`1000`）を追記する
      （`nfr-requirements.md`・`nfr-design/logical-components.md` 7節確定）。

---

## 完了基準
- 上記全ステップの生成物がワークスペースルート配下に作成され、対応する単体テストが生成されて
  いること（実行・グリーン確認はBuild and Testステージで行う）。
- P1〜P10全ての性質にjqwik `@Property`テストが対応していること（PBT-02〜PBT-08準拠）。
- `aidlc-docs/construction/u7-saved-query-execution-history/code/`配下に4つのサマリドキュメント
  （business-logic-summary.md, api-layer-summary.md, repository-layer-summary.md,
  frontend-summary.md, testing-summary.md）が生成されていること。