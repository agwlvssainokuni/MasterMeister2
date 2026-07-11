# u5-master-data-maintenance-code-generation-plan.md

U5（Master Data Maintenance）の Code Generation 計画。本ドキュメントが Code Generation の
単一の真実源（single source of truth）であり、Part 2（Generation）はこの計画のステップを
順に実行する。ワークスペースルート: `~/Documents/project/git/MasterMeister2`
（`aidlc-state.md` Workspace Root）。アプリケーションコードはワークスペースルート配下
（`backend/`, `frontend/`）にのみ生成し、`aidlc-docs/` にはドキュメント成果物のみ生成する。

---

## ユニットコンテキスト（code-generation.md Step 3）

### 対応ストーリー
MVP-10, MVP-11, GEN-1〜GEN-5（`unit-of-work-story-map.md`）:
| ID | タイトル |
|---|---|
| MVP-10 | アクセス可能なテーブル/ビュー一覧の表示 |
| MVP-11 | レコード一覧の閲覧 |
| GEN-1 | レコードの絞り込み・並び替え（UI操作） |
| GEN-2 | WHERE句・ORDER BY句の手入力 |
| GEN-3 | レコードの編集 |
| GEN-4 | 変更内容の単一トランザクション反映 |
| GEN-5 | レコードの作成・削除 |

### 他ユニットへの依存
U1・U3・U4に依存（`unit-of-work-dependency.md`: `masterdata→common,audit,rdbmsconnection,schema,permission`）:
- `common`（`PageRequest`/`PageResult`、`common.dialect.DialectStrategy`/`SortDirection`、
  `common.exception`配下の`EntityNotFoundException`/`ValidationException`/
  `PermissionDeniedException`。いずれも既存、新規例外なし——`domain-entities.md`確定）
- `audit`（`AuditLogService.record(EventCategory, EventType, Long userId, Long connectionId,
  Result, String targetDescription, String summaryMessage)`。`EventType.LARGE_RECORD_READ`/
  `MASTER_DATA_MUTATION`はいずれもU1のCode Generationで既に定義済み——確認済み、新規追加不要）
- `rdbmsconnection`（U3）: `ConnectionPoolRegistry.getDataSource`/`getJdbcTemplate`（既存）、
  `getTransactionTemplate(connectionId)`（**未実装、本計画で新規追加**——後述「ブラウンフィールド
  発見事項」）
- `schema`（U3）: `SchemaQueryService.listTables`/`getTableDetail`（既存、テーブルメタデータ・
  主キー構成参照）
- `permission`（U4）: `EffectivePermissionResolver`の6メソッド全て（
  `resolveEffectiveTablePermission`/`resolveEffectiveColumnPermissions`/`canCreate`/
  `canDelete`/`listAccessibleSchemas`/`listAccessibleTables`）、`Permission`enum

### ブラウンフィールド発見事項（Code Generation Planning時に判明、Functional Design/NFR Design/NFR Requirementsからの補完・訂正）

1. **`ConnectionPoolRegistry.getTransactionTemplate(connectionId)`は未実装**であることが
   判明した。`business-rules.md` 3.3は「`DataSourceTransactionManager`/`TransactionTemplate`の
   構築ロジックは`ConnectionPoolRegistry`（U3）に置く」と明記しているが、実際のU3実装
   （`backend/src/main/java/cherry/mastermeister/rdbmsconnection/ConnectionPoolRegistry.java`）
   には`getDataSource`/`getJdbcTemplate`/`invalidate`の3メソッドしかない。本計画のStep 2で
   `getTransactionTemplate(Long connectionId)`を追加する（`new
   DataSourceTransactionManager(getDataSource(connectionId))`をラップした
   `TransactionTemplate`を都度生成して返す、Spring管理Beanにはしない——`business-rules.md`
   3.3の記述どおり）。

2. **`GET /api/rdbms-connections/{connectionId}/schemas`・`.../schemas/{schema}/tables`は
   既にU3の`SchemaController`が管理者専用（`hasRole("ADMIN")`）として実装済み**（生の
   `List<String>`/`List<TableMetadata>`を返す、権限フィルタなし）であることが判明した。
   `business-rules.md` 4節が示すU5のパス案（`GET .../schemas`、`GET .../schemas/{schema}/
   tables`）はU3の既存パスとメソッド・パスが完全一致するため、そのまま採用すると
   Spring MVCの`ambiguous mapping`エラーで起動に失敗する。加えてU3のこれらのエンドポイントは
   管理者専用・権限フィルタなしであり、U5の要件（一般ユーザ向け・`EffectivePermissionResolver`
   による権限フィルタ済み一覧）とはレスポンス内容も認可要件も異なる別物である。
   → ユーザ指示により、U5のエンドポイントは`/api/rdbms-connections/**`配下ではなく
   **独立したルートパス`/api/master-data/{connectionId}/**`に配置する**（`AuditLogController`
   の`/api/audit-logs`、`GroupController`の`/api/groups`と同様、コントローラ単位でリソース名を
   ルートに置く既存の命名慣習に倣う）。これによりU3の
   `/api/rdbms-connections/{connectionId}/schemas/**`（管理者向け・生メタデータ）とはパスの
   ルートから完全に分離される（詳細はStep 5参照）。

3. **`SecurityConfig`に一般ユーザ向け`master-data`エンドポイント用のルールが存在しない**
   ことが判明した（`business-rules.md` 4節「認証済み（`isAuthenticated()`）であればアクセス
   可能」を満たすルールを新規追加する必要がある）。発見事項2の対応により新パスは
   `/api/master-data/**`という独立したルートになったため、既存の`.requestMatchers(
   "/api/rdbms-connections/**").hasRole("ADMIN")`とは前方一致せず、**順序を意識した挿入は
   不要**（当初検討していた「既存ADMINルールより前に挿入」という制約は解消）。
   → 本計画のStep 5で`.requestMatchers("/api/master-data/**").authenticated()`を、他の
   一般ユーザ向けルールと同様の場所に追記する。

4. **`listRecords`の`criteria`（`FilterCriteria`）はGETのクエリパラメータに素直に
   フラット化できない**ことが判明した。`AuditLogController.search`（既存、`GET` +
   `@RequestParam`複数個）は`AuditLogFilterCriteria`が5個のスカラーフィールドのみで
   構成されるため単純にフラット化できたが、`FilterCriteria`は可変長の`List<UiCondition>`
   （各要素が`columnName`/`operator`/`value`の3フィールド）と`List<UiSort>`を持ち、GETの
   クエリパラメータでは表現が煩雑かつ非標準になる。
   → 本計画では`listRecords`を`POST .../records:search`（リクエストボディに`criteria`+
   `page`+`pageSize`をまとめた`RecordSearchRequest`を渡す）とする。`business-rules.md` 3節の
   `POST .../records:apply`（`MutationRequest`をボディで受け取る単一エンドポイント方針）と
   同型の「複雑な入力はPOST+ボディ」という設計判断であり、`frontend-components.md`の
   「正確なエンドポイントパス・クエリ/ボディの受け渡し方式はCode Generation段階で確定する」
   という留保に沿う（`frontend-components.md`のAPI一覧表に記載の`GET`は本計画で`POST`へ
   変更する）。

### 提供インタフェース・契約（他ユニットが依存する公開API）
- なし。`unit-of-work-dependency.md`上、U6（Query Builder）・U7（Saved Query / Execution /
  History）はいずれも`masterdata`パッケージへの依存を持たない（`SchemaQueryService`/
  `ConnectionPoolRegistry`/`EffectivePermissionResolver`を直接参照する設計）。
  `MasterDataQueryService`/`MasterDataMutationService`は`public`だがコントローラ以外の
  外部参照は想定しない。

### 本ユニットが所有するデータエンティティ（内部DB/JPA）
なし。`domain-entities.md`確定（Q1 = A）のとおり、`masterdata`パッケージは内部DBエンティティを
一切持たない。全てのDTOは純粋なJava `record`（JPA非対応）。

### パッケージ設計判断（`nfr-design-patterns.md`/`logical-components.md`からの継承）
- `MasterDataQueryService`/`MasterDataMutationService`・`RecordRowMapper`・本ユニット固有の
  全DTOは`cherry.mastermeister.masterdata`パッケージに配置する（`nfr-design-patterns.md`
  1.1、`common`への切り出しは行わない）。
- 依存方向は`masterdata → schema`（U3）・`masterdata → permission`（U4）・
  `masterdata → audit`（U1）の一方向のみ（`nfr-design-patterns.md` 1.1）。

### サービス境界・責務
- `masterdata`: `MasterDataQueryService`（`listAccessibleSchemas`/`listAccessibleTables`/
  `listRecords`、`business-rules.md` 1-2節）、`MasterDataMutationService`（`applyChanges`、
  3節）、`RecordRowMapper`（対象RDBMS4種間の型マッピング吸収、`nfr-design-patterns.md`
  2.1）、`MasterDataController`（REST API、4節）。
- `rdbmsconnection`（U3、ブラウンフィールド拡張）: `ConnectionPoolRegistry`に
  `getTransactionTemplate`追加（上記発見事項1）。
- `security`（U1、ブラウンフィールド拡張）: `SecurityConfig`に`master-data`エンドポイント用の
  `authenticated()`マッチャを追記（上記発見事項3）。
- フロントエンド: `features/masterData/`（`SchemaTableListPage`/`FilterPanel`/
  `RecordListPage`/`MutationResultDialog`/`api.ts`/`types.ts`、`frontend-components.md`）。
  U1の`apiClient`/`AppRouter`/`AppLayout`/`DataTable`/`ConfirmDialog`/`ToastNotification`/
  `ProtectedRoute`をブラウンフィールド拡張・再利用する。

### テスト可能な性質（PBT-01、`business-logic-model.md`で識別済み）
P1〜P10（`business-logic-model.md`「テスト可能な性質」表）。Step 3で対応する`@Property`
テストを生成する。

---

## ステップ一覧

### Step 1: プロジェクト構造セットアップ
- [x] 1-1. **該当なし（N/A）**: `tech-stack-decisions.md`確定のとおり本ユニットで新規追加
      する依存関係はない。`backend/build.gradle.kts`の変更は不要。

### Step 2: ビジネスロジック生成
- [x] 2-1. `backend/src/main/java/cherry/mastermeister/rdbmsconnection/
      ConnectionPoolRegistry.java`（既存、ブラウンフィールド修正）に
      `TransactionTemplate getTransactionTemplate(Long connectionId)`を追加する
      （「ブラウンフィールド発見事項」1、`business-rules.md` 3.3: `new
      DataSourceTransactionManager(getDataSource(connectionId))`をラップした
      `TransactionTemplate`を都度生成して返す、Spring管理Beanにはしない）。

      実装メモ: `getJdbcTemplate`と同型の1行メソッドとして実装（
      `new TransactionTemplate(new DataSourceTransactionManager(getDataSource(connectionId)))`
      を都度生成して返す）。Spring管理Beanにしないため`@Bean`定義は追加していない。
      `getJdbcTemplate`自体に専用の単体テストがないのと同じ理由で、本メソッド単体の
      直接テストは追加していない（Step 3-5のP9/P10でトランザクション動作を間接的に検証する）。
      `./gradlew compileJava`成功を確認。
- [x] 2-2. `backend/src/main/java/cherry/mastermeister/masterdata/` に読み取り系DTOを生成する
      （`domain-entities.md`確定）: `TableSummary`（record: `schemaName, tableName,
      TableType tableType, comment, Permission effectivePermission, canCreate, canDelete`）、
      `ColumnMetadata`（record: `columnName, dataType, nullable, Integer primaryKeySequence,
      Permission effectivePermission`）、`RecordListResult`（record: `List<ColumnMetadata>
      columns, PageResult<List<Object>> records`）、`FilterMode`（enum: `UI, RAW`）、
      `Operator`（enum: `EQ, NE, GT, LT, GE, LE, LIKE, IS_NULL, IS_NOT_NULL`）、`UiCondition`
      （record: `columnName, Operator operator, Object value`）、`UiSort`（record:
      `columnName, SortDirection direction`）、`FilterCriteria`（record: `FilterMode mode,
      List<UiCondition> uiConditions, List<UiSort> uiSorts, String rawWhere, String
      rawOrderBy`）。`TableType`（U3）・`Permission`（U4）・`SortDirection`（U1
      `common.dialect`）・`PageResult`（U1 `common`）は既存を再利用し新規定義しない。

      実装メモ: `domain-entities.md`記載のフィールド構成をそのまま8ファイルのrecord/enumとして
      実装（`TableSummary`/`ColumnMetadata`/`RecordListResult`/`FilterMode`/`Operator`/
      `UiCondition`/`UiSort`/`FilterCriteria`）。`TableMetadata`（schema）と同様Javadocなし・
      コンパクトなrecord宣言スタイルを踏襲。`./gradlew compileJava`成功を確認。
- [x] 2-3. `backend/src/main/java/cherry/mastermeister/masterdata/` に更新系DTOを生成する
      （`domain-entities.md`確定）: `RecordCreate`（record: `Map<String, Object> values`）、
      `RecordUpdate`（record: `Map<String, Object> primaryKeyValues, Map<String, Object>
      changedValues`）、`RecordDelete`（record: `Map<String, Object> primaryKeyValues`）、
      `MutationRequest`（record: `List<RecordCreate> creates, List<RecordUpdate> updates,
      List<RecordDelete> deletes`）、`MutationResult`（record: `boolean success, int
      createdCount, int updatedCount, int deletedCount, String errorMessage`）。

      実装メモ: `domain-entities.md`記載のフィールド構成をそのまま5ファイルのrecordとして
      実装。`./gradlew compileJava`成功を確認。
- [x] 2-4. `backend/src/main/java/cherry/mastermeister/masterdata/RecordRowMapper.java`
      （`RowMapper<List<Object>>`、`nfr-design-patterns.md` 2.1・`logical-components.md`
      1節）を生成する。`ResultSetMetaData.getColumnType(int)`（`java.sql.Types`）に基づき
      `DATE`→`LocalDate`、`TIME`/`TIME_WITH_TIMEZONE`→`LocalTime`、`TIMESTAMP`→
      `LocalDateTime`、`TIMESTAMP_WITH_TIMEZONE`→`OffsetDateTime`、`NUMERIC`/`DECIMAL`→
      `BigDecimal`は`ResultSet.getObject(int, Class)`で明示要求し、それ以外は
      `getObject(int)`をそのまま用いる（NFR Design Question 2確定方式）。

      実装メモ: `mapRow`で`ResultSetMetaData.getColumnCount()`分ループし、列ごとに
      `switch`式（`java.sql.Types`定数）で`nfr-design-patterns.md` 2.1のマッピング表を
      そのまま実装。`./gradlew compileJava`成功を確認（対象RDBMSへの実アクセスを伴う動作検証は
      Step 3のP1/P2で行う）。
- [x] 2-5. `backend/src/main/java/cherry/mastermeister/masterdata/
      MasterDataQueryService.java`（`@Service`）: `List<String>
      listAccessibleSchemas(Long userId, Long connectionId)`（`EffectivePermissionResolver`
      へそのまま委譲、`business-rules.md` 1.1）、`List<TableSummary>
      listAccessibleTables(Long userId, Long connectionId, String schema)`（1.1-1.2:
      `EffectivePermissionResolver.listAccessibleTables`のテーブル名一覧に
      `SchemaQueryService.listTables`のメタデータと`canCreate`/`canDelete`判定を組み合わせて
      `TableSummary`を構築）、`RecordListResult listRecords(Long userId, Long connectionId,
      String schema, String table, FilterCriteria criteria, PageRequest page)`（2.1-2.5:
      `resolveEffectiveColumnPermissions`でNONE列をSELECT列から除外し〈テーブル権限自体が
      NONEなら`PermissionDeniedException`〉、UIモードは条件カラムのREAD以上検証、RAWモードは
      セミコロン簡易チェックのみ、`DialectStrategy`でページング/方言吸収、
      `mm.app.master-data.query-timeout`を`setQueryTimeout`で適用、`RecordRowMapper`で行を
      構築、1ページの件数が`mm.app.master-data.large-record-threshold`以上なら
      `AuditLogService.record(DATA_ACCESS, LARGE_RECORD_READ, ...)`）を実装する。
      **実装メモ**: `listAccessibleSchemas`/`listAccessibleTables`は計画どおり実装。
      `listRecords`は`ResultSetExtractor`ラムダ内で`ResultSetMetaData`から`ColumnMetadata`の
      `columnName`/`dataType`/`nullable`を都度導出し（`domain-entities.md`確定どおりU3の
      `SchemaColumn`スナップショットに依存しない）、`primaryKeySequence`のみ
      `SchemaQueryService.getTableDetail`の結果をカラム名で突合、`RecordRowMapper.mapRow`を
      同一`ResultSetExtractor`内で行構築に再利用した。`DialectStrategy.getSchemaResolutionMode`
      が`CATALOG_BASED`（MySQL/MariaDB）の場合はテーブル参照をスキーマ修飾せず（接続時の
      カタログ＝schema値のため）、`SCHEMA_BASED`（PostgreSQL/H2）の場合のみ
      `quoteIdentifier(schema) + "." + quoteIdentifier(table)`で修飾する設計判断を追加した
      （`business-rules.md`未言及、U3`SchemaImportService.resolveSchemaNames`と同じ判定基準を
      流用）。UIモード条件のNULL順序は`NullsOrder.LAST`固定とした（要件に明記がないための
      AI判断、Code Generationレベルの詳細化）。ページング件数照会は`SELECT COUNT(*)`を
      別途発行する方式とした。`RdbmsConnectionRepository`への直接依存を追加（`DialectStrategy`
      解決に必要、`nfr-design-patterns.md` 1.1の依存方向記載は`ConnectionPoolRegistry`同様
      `rdbmsconnection`パッケージへの依存を前提としており矛盾しない）。
      `./gradlew compileJava`成功を確認。単体テストはStep 3-1/3-2で追加する。
- [x] 2-6. `backend/src/main/java/cherry/mastermeister/masterdata/
      MasterDataMutationService.java`（`@Service`）: `MutationResult applyChanges(Long
      userId, Long connectionId, String schema, String table, MutationRequest request)`
      （3.1: 全操作の権限検証をall-or-nothingで先に実施——`canCreate`/カラムUPDATE権限/
      `canDelete`、3.2: 主キーなしテーブルへの`RecordUpdate`は`ValidationException`で全体
      拒否、3.3: `ConnectionPoolRegistry.getTransactionTemplate(connectionId)`の
      `execute`コールバック内で同一`NamedParameterJdbcTemplate`を`creates`→`updates`→
      `deletes`の順に個別実行ループで発行し`setQueryTimeout`をループ開始前に1回適用、
      `DataAccessException`発生時はロールバックし`errorMessage`に概要のみ設定、3.4: 成功・
      失敗いずれも`AuditLogService.record(DATA_ACCESS, MASTER_DATA_MUTATION, ...)`）を実装
      する。リクエスト内の総操作件数（`creates.size() + updates.size() + deletes.size()`）が
      `mm.app.master-data.max-mutation-batch-size`を超える場合はSQL実行前に
      `ValidationException`で拒否する（`tech-stack-decisions.md` #4）。
      **実装メモ**: バッチサイズ超過チェック→権限検証（3.1: `creates`が空でなければ
      `canCreate`、`deletes`が空でなければ`canDelete`、`updates`が空でなければ主キー構成の
      有無を先にチェックしてから——3.2どおり主キーなしテーブルは`ValidationException`——
      各`changedValues`のカラムが`UPDATE`権限以上か検証）の順で実施し、いずれかの失敗
      （`PermissionDeniedException`/`ValidationException`）もバッチサイズ超過も、対象RDBMSへ
      問い合わせる前に`AuditLogService.record(..., Result.FAILURE, ...)`を記録してから
      例外を再送出する（3.1「権限検証失敗もFAILUREとして記録」・3.4の統一方針どおり）。
      検証を通過した場合のみ`ConnectionPoolRegistry.getTransactionTemplate(connectionId)`の
      `execute`コールバック内で`INSERT`→`UPDATE`→`DELETE`を`creates`/`updates`/`deletes`の
      順に1件ずつ`NamedParameterJdbcTemplate.update`で発行する（`setQueryTimeout`は
      コールバック内の最初に1回適用——Step 2-5の`MasterDataQueryService`と同じ理由で
      `ConnectionPoolRegistry.getJdbcTemplate`は呼び出しごとに新規インスタンスを返すため）。
      SQL文はMasterDataQueryServiceと同じCATALOG_BASED/SCHEMA_BASED判定でテーブル名を
      修飾し、`INSERT`は`create.values()`のキー集合から動的に列リストとプレースホルダを
      構築、`UPDATE`は`changedValues`をSET句に・`primaryKeyValues`をWHERE句に、`DELETE`は
      主キー列全部をWHERE句に使う（パラメータ名衝突を避けるため`UPDATE`のSET句パラメータは
      `set_`、WHERE句パラメータは`pk_`のプレフィックスを付与——`RecordUpdate`は
      `primaryKeyValues`と`changedValues`が別マップだが同じカラム名を含みうるため）。
      `DataAccessException`発生時は`TransactionTemplate.execute`のコールバックが例外を
      伝播し自動的にロールバックされる仕様（Spring標準）に委ね、呼び出し元でcatchして
      `errorMessage`に`getMostSpecificCause().getMessage()`（元の`SQLException`相当の
      メッセージのみ、行・カラム単位の詳細特定はしない=3.3どおり）を設定した
      `MutationResult(false, ...)`を返す（例外を再送出せず失敗結果として返却——呼び出し元
      APIが「バリデーション失敗=4xx」と「実行時失敗=結果に含めて200で返す」を区別できる
      よう、権限検証・バッチサイズ超過は例外、実行時のDB由来失敗は`MutationResult`という
      2種類の失敗表現を使い分けた。Code Generationレベルの設計判断、Step 5のAPI層で
      HTTPステータスへのマッピングを確定する）。`./gradlew compileJava`成功を確認。
      単体テストはStep 3-3/3-4で追加する。

### Step 3: ビジネスロジック単体テスト（PBT-01〜PBT-08, PBT-10）
`business-logic-model.md`のP1〜P10に対応する`@Property`テストをjqwikで生成する。対象RDBMSへの
実アクセスを伴うため、U3 `SchemaImportServiceTest`/U4 `SchemaReimportCacheConsistencyTest`と
同じ手法（`org.h2.tools.Server`によるH2 TCPサーバを対象RDBMS役として起動、`@SpringBootTest`
`@JqwikSpringSupport`）を用いる。
- [x] 3-1. **P1**（SELECT列のNONE権限除外Invariant）・**P2**（`columns`と`records`各行の
      要素数・対応順序の構造整合性Invariant）: `MasterDataQueryServiceTest`に`@Property`
      テストを生成する。
      **実装メモ**: `MasterDataQueryServiceTest.java`を新規生成。
      `listRecordsExcludesNonePermissionColumnsAndAlignsRowsToColumns`という単一の`@Property`
      （tries=20）でP1・P2を同時検証する構成とした（両者とも同じ`listRecords`呼び出し1回の
      結果から導出できる不変性であり、テーブル/データセットアップを共有した方がプロパティの
      意図が明確になるため）。
      対象RDBMSはU3 `SchemaImportServiceTest`と同じ手法（`org.h2.tools.Server`によるH2 TCP
      サーバを`@BeforeContainer`/`@AfterContainer`で起動・停止し、`ConnectionPoolRegistry`
      経由で実接続）を用いた。`EffectivePermissionResolver`と`SchemaQueryService`は
      Mockitoでモック化し、権限解決ロジック自体（`EffectivePermissionResolverTest`側で
      別途検証済み）とは独立して`MasterDataQueryService`のSELECT列選定・行マッピングだけを
      検証できるようにした。
      テーブルは`ID INT PRIMARY KEY`固定列1つ（常にREAD権限、SELECT列が空にならない保証用）
      と`COL0`〜`COL3`の4つの生成列（`@ForAll`で`Permission`値をランダム割当）で構成。
      各生成列の値には自身のカラム名文字列自体を格納することで、返却された`records`の
      各行の位置`i`の値が`columns.get(i)`のカラムに実際に対応していることを直接検証できる
      （P2の「位置対応」を値レベルで確認する設計）。
      P1は`result.columns()`が`columnPermissions`から`NONE`を除外した期待リストと
      `containsExactlyElementsOf`で完全一致すること、およびいずれのカラムも
      `effectivePermission() == NONE`でないことの両面で検証。
      P2は`rowCount`（0〜3を`@ForAll`で生成）に応じた各行が`columns()`と同じ要素数を持つこと、
      および`ID`列は`Number`型、生成列はカラム名文字列と一致することを検証。
      `./gradlew test --tests "cherry.mastermeister.masterdata.MasterDataQueryServiceTest"`で
      成功を確認（BUILD SUCCESSFUL）。
- [x] 3-2. **P3**（UIモード条件のREAD未満カラム参照時の例外Invariant）・**P4**（RAWモードの
      セミコロン簡易防御Invariant）: `MasterDataQueryServiceTest`に追加生成する。
      実装メモ: P3は`listRecordsRejectsUiReferenceToNonePermissionColumn`として実装。
      `COL0`〜`COL3`のうち`@ForAll`で選んだ1列を強制的に`Permission.NONE`に上書きし、
      その列を`UiCondition`（`referenceViaSort=false`）または`UiSort`
      （`referenceViaSort=true`、`@ForAll boolean`でランダム選択）のいずれかから参照させ、
      他の列の権限パターンや参照経路によらず常に`PermissionDeniedException`が送出される
      ことを`assertThatThrownBy`で検証。P4は`listRecordsRejectsRawCriteriaContainingSemicolon`
      として実装。`semicolonInWhere`（`@ForAll boolean`）で`rawWhere`と`rawOrderBy`の
      どちらにセミコロンを含めるかを切り替え、生成カラムの権限パターン（NONE〜UPDATEまで
      ランダム）によらず常に`PermissionDeniedException`が送出されることを検証。
      いずれもテーブル作成のみでレコード投入は不要（例外検証のためSELECT自体が発行され
      ないことをテストの前提としている）。
      `./gradlew test --tests "cherry.mastermeister.masterdata.MasterDataQueryServiceTest"`で
      成功を確認（BUILD SUCCESSFUL）。
- [x] 3-3. **P5**（`large-record-threshold`境界値での監査記録Invariant）:
      `MasterDataQueryServiceTest`に追加生成する。
      実装メモ: `listRecordsRecordsLargeRecordAuditAtThresholdBoundary`として実装。
      `newService`に`largeRecordThreshold`と`AuditLogService`モックを注入できるオーバーロードを
      追加し、`@ForAll("thresholds")`（1〜5の範囲）で境界値となるthreshold自体をランダム化。
      `atThreshold`（`@ForAll boolean`）で`threshold-1`件（未満）と`threshold`件（以上）の
      いずれを投入するかを切り替え、`threshold`件の場合のみ`EventType.LARGE_RECORD_READ`の
      監査記録が発生し、`threshold-1`件の場合は一切発生しないことをMockito
      `verify`/`verify(..., never())`で検証。PageRequestのpageSizeは`threshold+10`として
      ページングによる行数の切り詰めが境界値検証に影響しないようにしている。
      `./gradlew test --tests "cherry.mastermeister.masterdata.MasterDataQueryServiceTest"`で
      成功を確認（BUILD SUCCESSFUL）。
- [x] 3-4. **P6**（`applyChanges`権限検証all-or-nothing Invariant）・**P7**（主キーなし
      テーブルへの`RecordUpdate`拒否Invariant）・**P8**（主キーなしテーブルへの
      `RecordDelete`拒否Invariant）: `MasterDataMutationServiceTest`に`@Property`テストを
      生成する。
      実装メモ: `MasterDataMutationServiceTest.java`を新規生成し、3つの`@Property`テストを
      実装。P6は`applyChangesRejectsAllOrNothingWhenAnyOperationFailsPermission`として実装。
      `@ForAll("failureTargets")`（0〜2）でcreate/update/deleteのいずれか1件のみを失敗させ
      （`canCreate`/カラムUPDATE権限/`canDelete`をそれぞれfalse・READ・falseに設定）、
      他の2操作は有効なまま`MutationRequest`に含め、`PermissionDeniedException`送出後に
      対象テーブルの全行が呼び出し前の状態（ID=0/2の既存2行のCOL0値も含め）と完全一致する
      ことをJDBCで直接検証。P7は`applyChangesRejectsRecordUpdateOnTableWithoutPrimaryKey`
      として実装。`ColumnDetail`の`primaryKeySequence`を全カラムnullにして主キーなしテーブルを
      表現し、`@ForAll("columnPermissionPatterns")`でカラム権限をランダム化（NONE〜UPDATEの
      全域を含む）しても`RecordUpdate`を含むリクエストが常に`ValidationException`となることを
      検証。P8は`applyChangesRejectsRecordDeleteOnTableWithoutPrimaryKey`として実装。同じく
      主キーなしテーブルで、U4既存仕様により`canDelete`が常にfalseとなる契約を`newService`の
      `canDelete=false`注入で模擬し、`RecordDelete`を含むリクエストが常に
      `PermissionDeniedException`となること、および対象行が削除されず残存することを検証。
      3テストとも`MasterDataQueryServiceTest`と同じくH2 TCPサーバ（`@BeforeContainer`/
      `@AfterContainer`）・`ConnectionPoolRegistry`実接続・`SchemaQueryService`/
      `EffectivePermissionResolver`/`AuditLogService`のMockitoモックという構成を踏襲。
      `./gradlew test --tests "cherry.mastermeister.masterdata.MasterDataMutationServiceTest"`で
      成功を確認（BUILD SUCCESSFUL）。
- [x] 3-5. **P9**（トランザクション原子性Invariant：`SQLException`発生時に対象RDBMS状態が
      呼び出し前と完全一致）・**P10**（成功時の反映結果Invariant：`creates`/`updates`/
      `deletes`の内容が過不足なく反映）: `MasterDataMutationServiceTest`に追加生成する。
      実装メモ: 既存の`MasterDataMutationServiceTest.java`に2つの`@Property`テストを追加。
      P9は`applyChangesRollsBackAllChangesWhenSqlExceptionOccurs`として実装。テーブル
      `T1(ID PK, COL0 VARCHAR(5) NOT NULL)`と、`T1.ID`を外部キー参照する`T2`を用意し、
      `@ForAll("failureTargets")`（0〜2）でcreate（既存ID重複によるPRIMARY KEY違反）・
      update（VARCHAR(5)超過による値超過エラー）・delete（T2から参照中の行の削除による
      外部キー制約違反）のいずれか1件のみでSQLExceptionを発生させ、他の2操作が事前に
      成功していても（`MasterDataMutationService`はトランザクション内で逐次実行するため）
      `applyChanges`が`MutationResult(success=false, ...)`を返し、かつ対象テーブルの全行・
      関連行が呼び出し前の状態と完全一致することをJDBCで直接検証。P10は
      `applyChangesReflectsCreatesUpdatesDeletesExactlyOnSuccess`として実装。
      `@ForAll @IntRange(min=0,max=2)`でcreate/update/delete件数をそれぞれランダム化し、
      既存5行のテーブルに対して重複しないID範囲でcreates/updates/deletesを構成、
      `MutationResult`の各カウントが指定件数と一致すること、生成行・更新後の値・削除済み
      行の不在・未変更行の値・最終行数（5+createCount-deleteCount）の全てをJDBCで検証。
      両テストとも既存の`newService`/`openConnection`/`createSchema`ヘルパーとH2 TCP
      サーバ構成を再利用。
      `./gradlew test --tests "cherry.mastermeister.masterdata.MasterDataMutationServiceTest"`
      で成功を確認（BUILD SUCCESSFUL、5テスト全て成功）。Step 3は全項目完了、P1〜P10
      全てにプロパティテストが対応済み。

### Step 4: ビジネスロジックサマリ
- [x] 4-1. `aidlc-docs/construction/u5-master-data-maintenance/code/business-logic-summary.md`
      を生成し、Step 2・Step 3で生成したクラス一覧とP1〜P10の対応関係を表形式で記載する
      （U1〜U4の`business-logic-summary.md`と同一構成）。
      実装メモ: U4の`business-logic-summary.md`の構成（生成クラス一覧・生成テストクラス
      一覧・P1〜P10対応表・補足・Step 2完了時点で新規判明した設計要素・既知の課題）を
      踏襲して新規生成。生成クラス一覧はStep 2-1〜2-6の9クラス（`ConnectionPoolRegistry`
      ブラウンフィールド修正1件、読み取り系DTO8種・更新系DTO5種をまとめた行、
      `RecordRowMapper`、`MasterDataQueryService`、`MasterDataMutationService`）、
      生成テストクラス一覧は`MasterDataQueryServiceTest`・`MasterDataMutationServiceTest`の
      2クラス、P1〜P10対応表はStep 3-1〜3-5の実装メモから各Invariantとテストメソッド名を
      対応付けて記載した。U4と異なり本ユニットは内部DBエンティティを持たないため
      「既知の課題」はなし（Step 8該当なし）である点を補足に明記。

### Step 5: APIレイヤ生成
- [x] 5-1. `backend/src/main/java/cherry/mastermeister/masterdata/
      RecordSearchRequest.java`（record: `FilterCriteria criteria, int page, int
      pageSize`、「ブラウンフィールド発見事項」4）を生成する。

      実装メモ: `domain-entities.md`/「ブラウンフィールド発見事項」4のフィールド構成
      （`criteria, page, pageSize`）のとおりrecordとして実装。`./gradlew compileJava`成功を確認。
- [x] 5-2. `backend/src/main/java/cherry/mastermeister/masterdata/MasterDataController.java`
      （`@RestController @RequestMapping("/api/master-data/{connectionId}")`）: `GET
      "/schemas"`（`listAccessibleSchemas`）、`GET "/schemas/{schema}/tables"`
      （`listAccessibleTables`）、`POST "/schemas/{schema}/tables/{table}/records:search"`
      （`RecordSearchRequest`を受け`listRecords`→`RecordListResult`）、`POST
      "/schemas/{schema}/tables/{table}/records:apply"`（`MutationRequest`を受け
      `applyChanges`→`MutationResult`）を生成する（「ブラウンフィールド発見事項」2・4、
      `business-rules.md` 4節）。`userId`は`Authentication#getPrincipal()`キャスト取得
      （U2/U3/U4のコントローラと同一パターン）。

      実装メモ: `SchemaController`（U3）と同型のシンプルな戻り値直接返却スタイルで実装
      （`ResponseEntity`ラップなし、例外処理は`GlobalExceptionHandler`に委譲）。
      `records:search`は`RecordSearchRequest`の`page`/`pageSize`から`PageRequest`を組み立てて
      `listRecords`に渡す。`records:apply`は`MutationResult`をそのまま返却し、実行時DB失敗
      （`success=false`）も200 OKで返る（Step 2-6の設計どおり、HTTPステータスの分離は行わない）。
      `./gradlew compileJava`成功を確認。単体テストはStep 6-1で追加する。
- [x] 5-3. `backend/src/main/java/cherry/mastermeister/security/SecurityConfig.java`
      （既存、ブラウンフィールド修正）に`.requestMatchers("/api/master-data/**")
      .authenticated()`を、他の一般ユーザ向けルールと同様の場所に追記する
      （「ブラウンフィールド発見事項」3、`business-rules.md` 4節）。

      実装メモ: `.requestMatchers("/api/groups/**").hasRole("ADMIN")`と
      `.anyRequest().authenticated()`の間に1行追記。既存ADMINルールとの前方一致競合が
      ないため挿入順序上の制約はなし。`./gradlew compileJava`成功を確認。これにより**Step 5
      は全項目完了**。

### Step 6: APIレイヤ単体テスト
- [x] 6-1. `MasterDataControllerTest`（`@WebMvcTest` + `spring-security-test`）: 4エンドポイント
      それぞれについて認証済みユーザ成功系・未認証401をexample-basedテストで検証する
      （U2/U3/U4のControllerTestパターンを踏襲、本ユニットは管理者ロール制約がないため
      403系テストは不要——`business-rules.md` 4節）。

      実装メモ: `SchemaControllerTest`/`PermissionControllerTest`と同型の構成で8テスト
      （4エンドポイント×成功系/401）を実装。成功系は`UsernamePasswordAuthenticationToken(1L,
      null, ROLE_USER)`を`.with(authentication(...))`で明示適用（`@WithMockUser`の既定
      プリンシパルは`Long`にキャストできないため）。`records:search`/`records:apply`は
      JSON文字列ボディで検証。`./gradlew test --tests
      "cherry.mastermeister.masterdata.MasterDataControllerTest"`および
      `cherry.mastermeister.masterdata.*`で成功を確認（BUILD SUCCESSFUL）。

### Step 7: APIレイヤサマリ
- [ ] 7-1. `aidlc-docs/construction/u5-master-data-maintenance/code/api-layer-summary.md`を
      生成し、エンドポイント一覧（パス・メソッド・認可要件・リクエスト/レスポンス形状）を
      記載する。

### Step 8〜10: リポジトリレイヤ
- [ ] 8/9/10-1. **該当なし（N/A）**: `domain-entities.md`確定（Q1 = A）のとおり本ユニットは
      内部DBエンティティを一切持たない。リポジトリ生成・単体テスト・サマリのいずれも対象外。

### Step 11: フロントエンドコンポーネント生成
- [ ] 11-1. `frontend/src/features/masterData/types.ts`: `frontend-components.md`・
      `domain-entities.md`のDTOに対応するTypeScript型（`TableSummary`/`ColumnMetadata`/
      `RecordListResult`/`FilterCriteria`/`UiCondition`/`UiSort`/`RecordCreate`/
      `RecordUpdate`/`RecordDelete`/`MutationRequest`/`MutationResult`等）を定義する。
- [ ] 11-2. `frontend/src/features/masterData/api.ts`: `listAccessibleSchemas`/
      `listAccessibleTables`/`listRecords`/`applyChanges`（「ブラウンフィールド発見事項」2・4
      反映後の実パス、Step 5参照）を実装する。U1の`apiClient`を再利用する。
- [ ] 11-3. `frontend/src/features/masterData/SchemaTableListPage.tsx`: 接続選択→スキーマ選択→
      `DataTable`（U1既存）で`TableSummary`一覧表示、行選択で`RecordListPage`へ遷移する
      （`frontend-components.md`、フロー1）。
- [ ] 11-4. `frontend/src/features/masterData/FilterPanel.tsx`: UI/RAWモードトグル、UIモードは
      `columns`のうち`effectivePermission >= READ`のカラムのみ対象に`UiCondition`/`UiSort`
      組み立てUIを提供、RAWモードは`rawWhere`/`rawOrderBy`テキスト入力欄を提供する
      （`frontend-components.md`、フロー2手順1）。
- [ ] 11-5. `frontend/src/features/masterData/RecordListPage.tsx`・
      `MutationResultDialog.tsx`: `RecordListResult`を`DataTable`拡張版（インライン編集セル・
      行選択チェックボックス・新規行追加ボタン）で表示し、`pendingChanges`に差分を蓄積、
      「反映」ボタンで`applyChanges`を呼び出し`MutationResultDialog`で結果表示する
      （`frontend-components.md`、フロー2〜3）。
- [ ] 11-6. `frontend/src/routes/AppRouter.tsx`（既存、ブラウンフィールド修正）に
      `/master-data`（`SchemaTableListPage`）・`/master-data/:connectionId/:schema/:table`
      （`RecordListPage`）を`ProtectedRoute`配下に追加する。`frontend/src/components/
      AppLayout.tsx`（既存、ブラウンフィールド修正）に「マスタデータ」ナビゲーションリンクを
      全ユーザ表示で追加する（`frontend-components.md` AppRouter.tsxへの追加）。

### Step 12: フロントエンドコンポーネント単体テスト
- [ ] 12-1. `SchemaTableListPage.test.tsx`・`FilterPanel.test.tsx`・`RecordListPage.test.tsx`・
      `MutationResultDialog.test.tsx`（vitest + Testing Library、U4の`features/permission/`
      配下のテストパターンを踏襲）を生成する。

### Step 13: フロントエンドコンポーネントサマリ
- [ ] 13-1. `aidlc-docs/construction/u5-master-data-maintenance/code/frontend-summary.md`を
      生成する。

### Step 14: データベースマイグレーションスクリプト
- [ ] 14-1. **該当なし（N/A）**: 本ユニットは内部DBエンティティを持たないため対象外
      （U1 NFR Design Question 5 = Aを踏襲）。

### Step 15: ドキュメント生成
- [ ] 15-1. `aidlc-docs/construction/u5-master-data-maintenance/code/testing-summary.md`
      （P1〜P10とテストクラスの対応表、example-basedテスト一覧）を生成する。

### Step 16: デプロイ成果物生成
- [ ] 16-1. `backend/src/main/resources/application.yml`（既存、ブラウンフィールド修正）に
      `mm.app.master-data.default-page-size`（既定`50`）、
      `mm.app.master-data.page-size-options`（既定`50,100,200`）、
      `mm.app.master-data.query-timeout`（既定`30`秒）、
      `mm.app.master-data.max-mutation-batch-size`（既定`500`）、
      `mm.app.master-data.large-record-threshold`（既定`100`）を追記する
      （`nfr-requirements.md`・`business-rules.md` 2.5確定）。

---

## 完了基準
- 上記全ステップの生成物がワークスペースルート配下に作成され、対応する単体テストが
  生成されていること（実行・グリーン確認はBuild and Testステージで行う）。
- P1〜P10全ての性質にjqwik `@Property`テストが対応していること（PBT-02〜PBT-08準拠）。
- `aidlc-docs/construction/u5-master-data-maintenance/code/`配下に4つのサマリドキュメント
  （business-logic-summary.md, api-layer-summary.md, frontend-summary.md,
  testing-summary.md）が生成されていること（リポジトリ層はN/Aのため
  repository-layer-summary.mdは生成しない）。