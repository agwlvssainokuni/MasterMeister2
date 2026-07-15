# business-logic-summary.md — U7: Saved Query / Execution / History

Step 2（ビジネスロジック生成）・Step 3（ビジネスロジック単体テスト）で生成したクラス一覧と、
`business-logic-model.md`のP1〜P10との対応関係。

## 生成クラス一覧（Step 2）

| パッケージ | クラス/インタフェース | 役割 |
|---|---|---|
| `savedquery` | `Visibility`（enum） | `PUBLIC`/`PRIVATE` |
| `savedquery` | `SavedQuery`（entity） | 保存済みクエリ本体（`ownerId`/`connectionId`/`name`/`sql`（`@Lob`）/`visibility`/`retired`/`executionCount`） |
| `savedquery` | `SavedQuerySummary`, `SavedQueryDetail`, `SavedQueryStatus`（record） | 一覧/詳細/状態（可視性・廃止有無）のDTO |
| `savedquery` | `SavedQueryRepository` | `findVisible`（可視性フィルタ付きJPQL）、`incrementExecutionCount`（`@Modifying`アトミックUPDATE） |
| `savedquery` | `SavedQueryService` | `saveQuery`/`getQuery`/`getExecutableQuery`/`updateQuery`/`retireQuery`/`listQueries`/`getStatuses`/`incrementExecutionCount`（フロー1） |
| `queryexecution` | `QueryResult`, `ResultColumn`, `DetectedParam`, `PagingOption`（record） | 実行結果・検出パラメータ・ページング指定のDTO |
| `queryexecution` | `ReadOnlySqlValidator` | GEN-13の読み取り専用検証。共有`ExecutorService`＋`Future.get`タイムアウト制御でJSqlParserを直接利用し、常にちょうど1文かつ`Select`型であることを検証（U6の`SqlParsingService`と同パターン） |
| `queryexecution` | `SqlParamDetector` | 文字列リテラル・`::`キャスト演算子を考慮した1文字スキャン方式で`:paramName`形式のプレースホルダを検出 |
| `queryexecution` | `PagingSqlBuilder` | 入力SQLをサブクエリとしてラップし`DialectStrategy`経由でLIMIT/OFFSET相当句を付与 |
| `queryexecution` | `QueryExecutionService` | `executeAdhocSql`/`executeSavedQuery`（検証→パラメータ事前検証→対象RDBMS実行→`executionCount`更新→`QueryHistory`/`AuditLog`記録、フロー2） |
| `queryhistory` | `ExecutorScope`（enum） | `ALL`/`SELF`（履歴一覧の実行者フィルタ） |
| `queryhistory` | `HistoryFilterCriteria`, `HistoryEntry`, `ExecutionRecord`（record） | 検索条件・一覧表示行（マスキング/バッジ反映後）・記録用入力のDTO |
| `queryhistory` | `JsonMapConverter` | `QueryHistory.params`（`Map<String, Object>`）のJSON永続化用`AttributeConverter`。`params`はJSONリクエストボディ由来で外部設定に依存しないため、`EncryptedStringConverter`と異なりSpring管理Beanにはせず、`ObjectMapper`をインスタンスフィールドとしてローカル生成する（Step 3で判明した理由は下記「Step 3で新規判明・修正した実装バグ」参照） |
| `queryhistory` | `QueryHistory`（entity） | 実行のたびのスナップショット（`sql`/`params`/`savedQueryName`は参照元`SavedQuery`の後続編集・廃止の影響を受けない）。`(connectionId, executedAt)`・`savedQueryId`に明示的インデックス |
| `queryhistory` | `QueryHistoryRepository` | `search`（`connectionId`必須、日時範囲・実行者・SQL文字列部分一致の任意フィルタ付きJPQL） |
| `queryhistory` | `QueryHistoryService` | `recordExecution`（`REQUIRES_NEW`＋非伝播、`AuditLogService.record`と同パターン）、`listHistory`（`SavedQueryService.getStatuses`によるバッチ判定でマスキング・バッジを付与、フロー3） |

## 生成テストクラス一覧（Step 3）

| テストクラス | 検証方式 |
|---|---|
| `savedquery.SavedQueryServiceTest` | jqwik `@Property`（`@JqwikSpringSupport @DataJpaTest`、`SavedQueryRepository`をAutowired・`SavedQueryService`は手動`new`。内部DBのみで完結するためH2 TCPサーバは不要） |
| `queryexecution.ReadOnlySqlValidatorTest` | jqwik `@Property`（プレーンクラス、`SqlParsingServiceTest`と同様に各試行後に`shutdown()`） |
| `queryexecution.SqlParamDetectorTest` | jqwik `@Property`（プレーンクラス、JDBC/DIとも無関係） |
| `queryexecution.QueryExecutionServiceTest` | jqwik `@Property`（`@JqwikSpringSupport @DataJpaTest`＋`org.h2.tools.Server`によるH2 TCPサーバ、`MasterDataQueryServiceTest`と同パターン。`SavedQueryRepository`はAutowired、`ReadOnlySqlValidator`は`@BeforeContainer`で1度だけ生成しコンテナ終了時に`shutdown()`） |
| `queryhistory.QueryHistoryServiceTest` | jqwik `@Property`（`@JqwikSpringSupport @DataJpaTest`、`QueryHistoryRepository`/`SavedQueryRepository`をAutowired。内部DBのみで完結するためH2 TCPサーバは不要） |

## P1〜P10対応表

| # | 対象 | 検証テストクラス | 状態 |
|---|---|---|---|
| P1 | `listQueries`の可視性フィルタInvariant（PUBLIC or 自分の所有のみ） | `SavedQueryServiceTest`（`listQueriesReturnsOnlyPublicOrOwnQueries`） | 実装済み（Step 3-1） |
| P2 | `retired`状態遷移Invariant（`getExecutableQuery`/`updateQuery`は常に拒否、`getQuery`は可視性のみで決まる） | `SavedQueryServiceTest`（`retiredQueryAlwaysRejectsExecutionAndUpdateRegardlessOfVisibility`） | 実装済み（Step 3-1） |
| P3 | `retireQuery`の一方向性Invariant | `SavedQueryServiceTest`（`retireQueryNeverReversesOnSubsequentOperations`） | 実装済み（Step 3-1） |
| P4 | `QueryExecutionService`の読み取り専用検証Invariant | `ReadOnlySqlValidatorTest`（`validateRejectsNonSelectOrUnparsableSql`/`validateAcceptsSelectSql`/`validateRejectsSqlExceedingMaxLength`） | 実装済み（Step 3-2） |
| P5 | パラメータ自動検出Invariant（文字列リテラル外の`:paramName`集合との一致） | `SqlParamDetectorTest`（`detectMatchesPlaceholdersOutsideStringLiterals`ほか） | 実装済み（Step 3-3） |
| P6 | ページング適用時の件数上限Invariant（有効時pageSize以下／無効時max-result-rows以下＋truncated境界） | `QueryExecutionServiceTest`（`executeAdhocSqlWithPagingNeverExceedsPageSize`/`executeAdhocSqlWithoutPagingTruncatesAtMaxResultRowsBoundary`） | 実装済み（Step 3-4） |
| P7 | 実行のたびの二重記録Invariant（QueryHistory・AuditLog各1件） | `QueryExecutionServiceTest`（`executeAdhocSqlAlwaysRecordsHistoryAndAuditExactlyOnce`） | 実装済み（Step 3-4） |
| P8 | `executionCount`のインクリメント整合性Invariant | `QueryExecutionServiceTest`（`executeSavedQueryIncrementsExecutionCountConsistently`） | 実装済み（Step 3-4） |
| P9 | 実行履歴のマスキングInvariant | `QueryHistoryServiceTest`（`listHistoryMasksAndBadgesIndependentlyPerRow`） | 実装済み（Step 3-5） |
| P10 | 「廃止済み」バッジの独立性Invariant | `QueryHistoryServiceTest`（`listHistoryMasksAndBadgesIndependentlyPerRow`、同一メソッド内でP9と独立に検証） | 実装済み（Step 3-5） |

**補足**: U7はU2〜U6と同様、P1〜P10すべてがStep 3で実装完了している。`queryexecution`は
`domain-entities.md`確定のとおり内部DBエンティティを一切持たないステートレスなオーケストレータ
パッケージであり、対象RDBMSへの実アクセスを伴うためU3〜U6と同じH2 TCPサーバ手法を用いる一方、
`savedquery`/`queryhistory`は内部DB（JPA）のみで完結するため`@DataJpaTest`（組み込みH2内部DB）
で足りる。

**Step 2完了時点で確定した実装時判断**: item 2-13（`QueryHistoryService.listHistory`）で、
承認済みプランは「`getStatuses`の呼び出し対象は`savedQueryId`非nullかつ`row.userId != 閲覧者`
の行のみ」としていたが、`business-rules.md` 5.3の可視性マトリクスは「閲覧者自身が実行」した
行にも`retired`次第のバッジ付与を要求しているため、バッジ判定用に`getStatuses`の対象を
`savedQueryId`が非nullの全行（自分の実行分も含む）へ拡張した（マスキング対象は従来どおり
非自分実行行のみに限定）。item 2-9（`QueryExecutionService.execute`）では、
`readOnlySqlValidator.validate`直後に`sqlParamDetector.detect(sql)`で検出したパラメータ名が
`params`に存在するか事前検証し、不足時は`ValidationException`（400）とする実装を追加した
（生のJDBC例外による500応答を避けるため）。いずれもP9/P10・P4〜P8のいずれの不変条件にも
抵触しない拡張であり、Step 3のプロパティテストで実際に検証している。

**Step 3で新規判明・修正した実装バグ**: 3件。(1) `JsonMapConverter`を`@Component`として
Spring管理Beanの`ObjectMapper`をコンストラクタ注入する設計にしていたが、`@DataJpaTest`
スライスにはJacksonの`ObjectMapper`Beanが含まれないため、`QueryHistory`エンティティを含む
全ての`@DataJpaTest`系テスト（U1の既存`AuditLogServiceTest`含む、`@DataJpaTest`はアプリ全体の
エンティティモデルを走査するため無関係なテストにも波及する）のコンテキスト起動が
`NoSuchBeanDefinitionException`で失敗することが判明した。`params`はJSONリクエストボディ由来
（String/Number/Boolean/null/List/Mapのみ）で外部設定に依存しないため、`EncryptedStringConverter`
と異なりSpring管理Beanにする実益がないと判断し、`@Component`を外して`ObjectMapper`を
インスタンスフィールドとしてローカル生成する形に変更した。
(2) `QueryHistoryRepository.search`の`LOWER(h.sql) LIKE ...`が、`h.sql`が`@Lob`（CLOB）
マッピングのため`FunctionArgumentException`（`lower()`の引数型不一致）でコンテキスト起動時に
失敗することが判明し、`LOWER(CAST(h.sql AS string))`に変更して解消した。
(3) `ReadOnlySqlValidatorTest`のP4テスト作成中、`"SELECT * FROM tbl; DROP TABLE tbl"`が
`CCJSqlParserUtil.parse()`では例外にならず`Select`型として通過してしまう（スタックドクエリ
形式の注入を検知できていない）ことをプロパティテストが検出した。
`ReadOnlySqlValidator.validate`を`CCJSqlParserUtil.parseStatements()`に変更し、解析結果が
常にちょうど1文かつ`Select`型であることを検証するよう修正した。いずれもハンドライトした
単体テストだけでは見逃されやすい欠陥であり、コンテキスト全体を起動する`@DataJpaTest`や
網羅的な入力を生成するプロパティテストの価値を示す事例となった。

**既知の課題（Step 3スコープ外）**: なし。`./gradlew compileJava`/`compileTestJava`はStep 2・
Step 3のいずれの時点でも成功しており、`./gradlew test`（バックエンド全体）もStep 3完了時点で
BUILD SUCCESSFULを確認済み。

## 変更要求（2026-07-15）: 接続コンテキストのグローバル化 + クエリ実行時スキーマ指定

`business-rules.md` 2.3節の反映として、`QueryExecutionService`に`EffectivePermissionResolver`
（`permission`パッケージ）を新規注入し、以下を実装した。

- `executeAdhocSql`/`executeSavedQuery`/`execute`/`runQuery`に`schema`パラメータを追加。実行前に
  `effectivePermissionResolver.listAccessibleSchemas(userId, connectionId)`で`schema`を検証し、
  含まれない場合は`PermissionDeniedException`を返す（`SET`文へのスキーマ名連結がSQL
  インジェクションのリスクとなるため必須の検証）。
- `DialectStrategy`に新規メソッド`buildSetSchemaStatement(String quotedSchema)`を追加し、
  `SCHEMA_BASED`方言（H2/PostgreSQL）のみ、方言ごとに異なるSET文構文（H2は`SET SCHEMA`、
  PostgreSQLは`SET search_path TO`）を実行直前に対象RDBMSへ発行する。同一コネクション上で
  SETとクエリを実行する必要があるため、`JdbcTemplate.execute(ConnectionCallback)`で
  借用したコネクションを`SingleConnectionDataSource(connection, true)`でラップして
  `NamedParameterJdbcTemplate`を構築する方式とした（`CATALOG_BASED`方言のMySQL/MariaDBでは
  `buildSetSchemaStatement`自体を呼び出さない——実装したらUnsupportedOperationExceptionを返す）。
- **設計時の見落とし訂正**: 当初`SET search_path TO`をH2にもそのまま発行する設計だったが、
  Step 5のテスト実行時にH2が`search_path`構文を持たない（`SET SCHEMA`のみサポート）ことが
  判明し、`DialectStrategy.buildSetSchemaStatement`として方言ごとに切り出す設計に訂正した。
- 新規メソッド`listAccessibleSchemas(userId, connectionId)`（`EffectivePermissionResolver`への
  単純委譲、`masterdata`/`querybuilder`と同じパターン）を追加し、`GET
  /api/query-execution/{connectionId}/schemas`（新規エンドポイント）から呼び出す。
- `QueryHistoryService.recordExecution`/`toEntry`で`schema`を伝播する。

### P11（新規性質）

| # | 対象 | 検証テストクラス | 状態 |
|---|---|---|---|
| P11 | スキーマ許可リスト検証Invariant（`listAccessibleSchemas`に含まれない`schema`を指定した場合、常に`PermissionDeniedException`となり対象RDBMSへの実行・履歴記録は一切発生しない） | `QueryExecutionServiceTest`（`executeAdhocSqlRejectsInaccessibleSchemaWithoutRecordingHistory`） | 実装済み |

P11に加え、`executeAdhocSqlAppliesSearchPathSoUnqualifiedTableNameResolvesToRequestedSchema`
（SCHEMA_BASED方言でのSET文の実効性を、スキーマ非修飾のテーブル参照が解決されることで確認する
実H2 TCPサーバ経由のテスト）・`executeAdhocSqlNeverIssuesSetSearchPathForCatalogBasedDialect`
（CATALOG_BASED方言では`Connection.createStatement()`が一切呼ばれないことをMockitoの
モックDataSource/Connectionで確認するテスト）を`@Example`（jqwikの単発テスト）として追加した。
`@JqwikSpringSupport`クラスでは`@BeforeContainer`（jqwikのみのライフサイクル）がJUnit標準の
`@Test`メソッドには適用されないため、素の`@Test`ではなく`@Example`を用いる必要がある点に注意
（`h2Server`/`validator`静的フィールドがnullのままになるバグとして一度検出・修正した）。