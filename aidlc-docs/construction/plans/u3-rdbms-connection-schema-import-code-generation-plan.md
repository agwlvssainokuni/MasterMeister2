# u3-rdbms-connection-schema-import-code-generation-plan.md

U3（RDBMS Connection & Schema Import）の Code Generation 計画。本ドキュメントが Code
Generation の単一の真実源（single source of truth）であり、Part 2（Generation）はこの計画の
ステップを順に実行する。ワークスペースルート: `~/Documents/project/git/MasterMeister2`
（`aidlc-state.md` Workspace Root）。アプリケーションコードはワークスペースルート配下
（`backend/`, `frontend/`）にのみ生成し、`aidlc-docs/` にはドキュメント成果物のみ生成する。

---

## ユニットコンテキスト（code-generation.md Step 3）

### 対応ストーリー
MVP-7, MVP-8, ADM-3（`unit-of-work-story-map.md`）:
| ID | タイトル |
|---|---|
| MVP-7 | 対象RDBMS接続情報の登録 |
| MVP-8 | スキーマ取り込み |
| ADM-3 | 複数の対象RDBMS接続の管理 |

### 他ユニットへの依存
U1（Platform Foundation）のみに依存（`unit-of-work-dependency.md`）:
- `common`（`PageRequest`/`PageResult`/`ErrorResponse`/`common.exception`配下の
  `EntityNotFoundException`/`ValidationException`/`GlobalExceptionHandler`。本ユニットは
  `GlobalExceptionHandler`への新規例外マッピング追記は不要——`EntityNotFoundException`
  （既存、404）をそのまま「接続が見つからない」ケースに再利用する）
- `common.dialect`（`RdbmsType`/`DialectStrategy`/`DialectStrategyFactory`/
  `SchemaResolutionMode`はU1既存。本ユニットは`DialectStrategy`に`buildJdbcUrl(String host,
  int port, String databaseName)`を追加するブラウンフィールド拡張を行う——後述「ブラウン
  フィールド発見事項」参照）
- `audit`（`AuditLogService.record(EventCategory, EventType, Long userId, Long connectionId,
  Result, String targetDescription, String summaryMessage)`——U1が既に`connectionId`引数を
  持つシグネチャで実装済みであり、`EventType.RDBMS_CONNECTION_CHANGED`/`SCHEMA_IMPORTED`も
  U1で定義済み。新規追加は不要）

### ブラウンフィールド発見事項（Code Generation Planning時に判明、NFR Designからの訂正）

- **`SchemaResolutionMode`はU1のCode Generationで既に実装済み**であることが判明した。
  `nfr-design-patterns.md` 4.1は「本ステージで新たに確定する」「新規定義」と記述していたが、
  実際には`backend/src/main/java/cherry/mastermeister/common/dialect/`に
  `SchemaResolutionMode`（enum: `CATALOG_BASED`, `SCHEMA_BASED`）と、`DialectStrategy`
  インタフェースの`getSchemaResolutionMode()`、および4つの`DialectStrategy`実装
  （`MySqlDialectStrategy`/`MariaDbDialectStrategy`→`CATALOG_BASED`、
  `PostgreSqlDialectStrategy`/`H2DialectStrategy`→`SCHEMA_BASED`）が既に生成されている。
  列挙値の名称は`nfr-design-patterns.md`が想定した`CATALOG_AS_SCHEMA`/`NATIVE_SCHEMA`とは
  異なるが、意味論（MySQL/MariaDBはカタログ＝DB名でスキーマ概念を持たない／
  PostgreSQL/H2は複数スキーマを持つ）は完全に一致する。**本計画では新規enum定義を行わず、
  既存の`SchemaResolutionMode`をそのまま再利用する**（Step 2で変更なしを明記）。
- 一方、`DialectStrategy`には対象RDBMSへのJDBC URL組み立てメソッドが存在しない
  （`business-rules.md` 1.3が要求する「`rdbmsType`に対応する`DialectStrategy`を用いて
  host+port+databaseNameからベースURLを構造化して組み立てる」機能は未実装）。Step 2で
  `buildJdbcUrl(String host, int port, String databaseName): String`を`DialectStrategy`に
  追加し、4実装クラスにブラウンフィールド修正を行う。

### 提供インタフェース・契約（他ユニットが依存する公開API）
- U4（Permission Management）・U5（Master Data Maintenance）・U6（Query Builder）・
  U7（Saved Query/Execution/History）が`rdbmsconnection`（`ConnectionPoolRegistry`）・
  `schema`（`SchemaTable`/`SchemaColumn`メタデータ参照）に依存する
  （`unit-of-work-dependency.md`）。`ConnectionPoolRegistry`・`SchemaTable`/`SchemaColumn`・
  `SchemaTableRepository`/`SchemaColumnRepository`は将来他ユニットから参照される想定のため
  `public`で生成する。

### 本ユニットが所有するデータエンティティ（内部DB/JPA）
- `rdbmsconnection`パッケージ: `RdbmsConnection`
- `schema`パッケージ: `SchemaTable`, `SchemaColumn`
- （`RdbmsType`はU1所有、`TableType`は`schema`パッケージに新規追加）

### パッケージ設計判断（`nfr-design-patterns.md`/`logical-components.md`からの継承、AI決定事項を含む）
- `EncryptedStringConverter`・`ConnectionPoolRegistry`・`RdbmsConnectionService`は
  `cherry.mastermeister.rdbmsconnection`パッケージに配置する（`nfr-design-patterns.md` 2.1）。
- `SchemaImportService`・`SchemaQueryService`は`cherry.mastermeister.schema`パッケージに
  配置する（`nfr-design-patterns.md` 2.1）。
- **DTO配置**（本計画でのAI決定、Q&Aの対象外）: `ConnectionConfig`/`ConnectionSummary`/
  `ConnectionDetail`/`ConnectionTestResult`は`rdbmsconnection`パッケージ、
  `SchemaImportResult`/`TableMetadata`/`TableDetail`/`ColumnDetail`は`schema`パッケージに
  配置する（対応するServiceと同一パッケージ、U2の`PendingUserSummary`配置方針を踏襲）。
- **コントローラ分割**（本計画でのAI決定）: `RdbmsConnectionController`
  （`rdbmsconnection`パッケージ、`/api/rdbms-connections`配下の接続CRUD・接続テスト）と
  `SchemaController`（`schema`パッケージ、`/api/rdbms-connections/{connectionId}/schema-import`
  等のスキーマ取り込み・参照）を分離する。`business-rules.md` 4節のパスは全て
  `/api/rdbms-connections`配下に統一されているが、責務は`nfr-design-patterns.md` 2.1が
  確定した`rdbmsconnection`/`schema`パッケージ分割に従う（コントローラもサービス層の
  パッケージ境界と一致させる）。
- **`ConnectionPoolRegistry`のリポジトリ直接参照**（本計画でのAI決定）:
  `ConnectionPoolRegistry.getDataSource(connectionId)`が遅延生成時に接続設定
  （復号済みパスワード含む）を必要とするため、`RdbmsConnectionRepository`を直接参照する
  （`RdbmsConnectionService`経由にはしない——`nfr-design-patterns.md` 2.1の
  「`schema→rdbmsconnection`一方向」原則は他ユニットからの参照方向の話であり、
  同一`rdbmsconnection`パッケージ内の`ConnectionPoolRegistry`↔`RdbmsConnectionRepository`
  参照はパッケージ内完結のため対象外）。

### サービス境界・責務
- `common.dialect`（U1既存、ブラウンフィールド拡張）: `DialectStrategy`に
  `buildJdbcUrl(host, port, databaseName)`を追加、4実装クラスに実装を追記。
- `rdbmsconnection`: `EncryptedStringConverter`（AES/GCM暗号化コンバータ）、
  `ConnectionPoolRegistry`（`connectionId`ごとのHikariCPプールのキャッシュ・遅延生成・破棄）、
  `RdbmsConnectionService`（接続情報のCRUD・接続テスト）。
- `schema`: `SchemaImportService`（対象RDBMSからのメタデータ取り込み・upsert）、
  `SchemaQueryService`（取り込み済みメタデータの参照、stale除外）。
- フロントエンド: `features/rdbmsConnection/`（接続一覧・登録/編集フォーム）、
  `features/schema/`（スキーマ取り込みパネル・暫定閲覧画面）。U1の`apiClient`/`AppRouter`/
  `AppLayout`/`DataTable`/`ConfirmDialog`/`ToastNotification`/`ProtectedRoute`を
  ブラウンフィールド拡張・再利用する。

---

## ステップ一覧

### Step 1: プロジェクト構造セットアップ
- [x] 1-1. `backend/build.gradle.kts`（既存、ブラウンフィールド修正）の
      `dependencyManagement.dependencies`ブロックに以下を追記（CLAUDE.md「Gradleバージョン
      管理」規約、`tech-stack-decisions.md` #3）:
      `dependency("com.mysql:mysql-connector-j:9.3.0")`,
      `dependency("org.mariadb.jdbc:mariadb-java-client:3.5.3")`,
      `dependency("org.postgresql:postgresql:42.7.7")`（2026-07-10時点のMaven Central最新
      安定版）。`dependencies`ブロックに`runtimeOnly("com.mysql:mysql-connector-j")`,
      `runtimeOnly("org.mariadb.jdbc:mariadb-java-client")`,
      `runtimeOnly("org.postgresql:postgresql")`を追記した（H2は既存の
      `runtimeOnly("com.h2database:h2")`を再利用、追記不要）。

### Step 2: ビジネスロジック生成
- [x] 2-1. `backend/src/main/java/cherry/mastermeister/common/dialect/DialectStrategy.java`
      （既存、ブラウンフィールド修正）に`String buildJdbcUrl(String host, int port, String
      databaseName)`を追加（「ブラウンフィールド発見事項」参照、`business-rules.md` 1.3）。
      インターフェースにメソッドシグネチャを追加した（実装は2-2で対応）。
- [x] 2-2. `MySqlDialectStrategy`/`MariaDbDialectStrategy`/`PostgreSqlDialectStrategy`/
      `H2DialectStrategy`（既存、ブラウンフィールド修正）に`buildJdbcUrl`を実装:
      `jdbc:mysql://{host}:{port}/{databaseName}`,
      `jdbc:mariadb://{host}:{port}/{databaseName}`,
      `jdbc:postgresql://{host}:{port}/{databaseName}`,
      `jdbc:h2:tcp://{host}:{port}/{databaseName}`（対象RDBMSとしてのH2はTCPサーバモード
      接続を前提とする——`domain-entities.md`が`RdbmsConnection.host`/`port`を`H2`含む
      全`RdbmsType`で必須としているため）。4クラスすべてに実装し、`./gradlew compileJava`で
      コンパイル成功を確認した。
- [x] 2-3. **該当なし（変更なし）**: `SchemaResolutionMode`（enum: `CATALOG_BASED`,
      `SCHEMA_BASED`）はU1で実装済み。「ブラウンフィールド発見事項」のとおり、本計画では
      新規定義・変更を行わない。実装済みファイルを確認し、変更不要であることを確認した。
- [x] 2-4. `backend/src/main/java/cherry/mastermeister/rdbmsconnection/` に
      `RdbmsConnection`（JPAエンティティ。`domain-entities.md`のフィールド定義: `id`,
      `name`（not null）, `rdbmsType`（`RdbmsType`, not null）, `host`（not null）,
      `port`（Integer, not null）, `databaseName`（not null）, `username`（not null）,
      `password`（not null、`@Convert(converter = EncryptedStringConverter.class)`）,
      `additionalParams`（nullable）, `createdAt`, `updatedAt`）を生成。`User`/
      `RegistrationToken`（`userregistration`パッケージ）と同型のスタイル（`protected`引数なし
      コンストラクタ+全フィールド引数コンストラクタ+`update`メソッド+getterのみ）で実装。
      `EncryptedStringConverter`は2-5で生成するため、本項目時点では未解決参照
      （2-1/2-2と同様、ペア項目間のコンパイル未検証は許容）。
- [x] 2-5. `backend/src/main/java/cherry/mastermeister/rdbmsconnection/
      EncryptedStringConverter.java`（`@Component` + `@Converter`、
      `AttributeConverter<String, String>`）: コンストラクタで
      `@Value("${mm.app.rdbms-connection.encryption-key}")`（Base64エンコード32バイト鍵）を
      受け取り鍵長を検証してfail-fast（`JwtTokenProvider`と同型パターン、
      `domain-entities.md`「実装方針」）。AES/GCM暗号化、IVはGCM推奨12バイトを暗号化ごとに
      `SecureRandom`で生成し暗号文の先頭に付加して1つの文字列として保存
      （`nfr-requirements.md` 1.1）。`convertToDatabaseColumn`/`convertToEntityAttribute`を
      実装。AES/GCM/NoPadding、タグ長128ビット、鍵長32バイト（AES-256）を検証。IV+暗号文を
      連結しBase64エンコードして1文字列として保存/復元。`mm.app.rdbms-connection.encryption-key`
      の設定値追加は16-1で対応（`@Value`にデフォルト値は与えずfail-fastのまま）。
      `./gradlew compileJava`でコンパイル成功を確認、`RdbmsConnection`の未解決参照も解消した。
- [x] 2-6. `backend/src/main/java/cherry/mastermeister/rdbmsconnection/` に
      `ConnectionConfig`（record: `String name, RdbmsType rdbmsType, String host, int port,
      String databaseName, String username, String password, String additionalParams`
      〈nullable〉）、`ConnectionSummary`（record: `Long id, String name, RdbmsType
      rdbmsType, String host, String databaseName`）、`ConnectionDetail`（record: `Long id,
      String name, RdbmsType rdbmsType, String host, int port, String databaseName, String
      username, String additionalParams`——**パスワードは含めない**、
      `frontend-components.md`「パスワード入力の扱い」）、`ConnectionTestResult`（record:
      `boolean success, String message`）を生成。`userregistration.PendingUserSummary`と
      同型のスタイル（1ファイル1レコード、バリデーションアノテーションなし）で実装。
      `./gradlew compileJava`でコンパイル成功を確認した。
- [x] 2-7. `backend/src/main/java/cherry/mastermeister/rdbmsconnection/
      ConnectionPoolRegistry.java`（`@Component`、シングルトンスコープ既定）:
      `ConcurrentHashMap<Long, HikariDataSource>`でキャッシュ。
      `DataSource getDataSource(Long connectionId)`は`computeIfAbsent`で遅延生成
      （`nfr-design-patterns.md` 1.1）、`NamedParameterJdbcTemplate getJdbcTemplate(Long
      connectionId)`、`void invalidate(Long connectionId)`（`remove`+`close()`、
      `business-rules.md` 1.5）を実装。生成時は`RdbmsConnectionRepository`
      （`EntityNotFoundException`をスロー）から復号済み設定を取得し、
      `DialectStrategyFactory.resolve(rdbmsType)`の`buildJdbcUrl`+`additionalParams`
      （非空なら`?`区切りで1回だけ付加、`business-rules.md` 1.3）でJDBC URLを組み立て、
      `mm.app.rdbms-connection.pool.*`設定値（`maximumPoolSize`/`minimumIdle`/
      `connectionTimeout`）でHikariCPプールを生成。**ユーザ判断により計画どおりStep順序を維持**:
      `RdbmsConnectionRepository`はStep 8（8-1）まで生成されないため、本項目時点で
      `./gradlew compileJava`は「シンボルを見つけられません: RdbmsConnectionRepository」の
      未解決参照エラーのみで失敗する（想定どおり、他のエラーは無いことを確認済み）。
      8-1完了までコンパイル不可状態が継続する。
- [x] 2-8. `backend/src/main/java/cherry/mastermeister/rdbmsconnection/
      RdbmsConnectionService.java`（`@Service`）: `Long createConnection(ConnectionConfig
      config)`（保存後`AuditLogService.record(ADMIN_OPERATION, RDBMS_CONNECTION_CHANGED,
      adminUserId, connectionId, SUCCESS, name, ...)`、`business-rules.md` 1.2）、
      `void updateConnection(Long connectionId, ConnectionConfig config)`（`password`が
      空文字列の場合は既存の暗号化済みパスワードを変更せず他フィールドのみ更新
      ——`frontend-components.md`「パスワード入力の扱い」がCode Generationでの確定を
      要求していた事項、非空なら`EncryptedStringConverter`経由で再暗号化。成功時
      `ConnectionPoolRegistry.invalidate(connectionId)`+監査記録、`business-rules.md`
      1.2/1.5）、`ConnectionTestResult testConnection(ConnectionConfig config)`（保存前、
      使い捨て`HikariDataSource`〈`maximumPoolSize=1`、`nfr-design-patterns.md` 3.1〉で
      検証、`ConnectionPoolRegistry`には登録しない）、`ConnectionTestResult
      testConnection(Long connectionId)`（`getConnection`相当で復号済み設定取得後、上記に
      委譲、`business-rules.md` 1.4パターン2）、`List<ConnectionSummary>
      listConnections()`、`ConnectionDetail getConnection(Long connectionId)`
      （`EntityNotFoundException`）を実装（`business-logic-model.md`フロー1・2）。
      計画テキストのメソッドシグネチャに`adminUserId`が明示されていなかったが、監査記録に
      必須のため`createConnection(Long adminUserId, ConnectionConfig config)`/
      `updateConnection(Long adminUserId, Long connectionId, ConnectionConfig config)`として
      補完（`userregistration.UserRegistrationService`の`approveUser(adminUserId,
      targetUserId)`と同型の引数順）。`./gradlew compileJava`はStep 8待ちの
      `RdbmsConnectionRepository`未解決参照（2箇所→4箇所に増加、原因は同一・既知のまま）
      のみで失敗することを確認、新規エラーが無いことを検証した。
- [x] 2-9. `backend/src/main/java/cherry/mastermeister/schema/` に
      `SchemaTable`（JPAエンティティ。`domain-entities.md`のフィールド定義: `id`,
      `connectionId`（not null）, `schemaName`（not null）, `tableName`（not null）,
      `tableType`（`TableType`, not null）, `comment`（nullable）, `stale`（not null、
      既定`false`）, `importedAt`, `updatedAt`。一意制約
      `(connectionId, schemaName, tableName)`）、`SchemaColumn`（JPAエンティティ。`id`,
      `tableId`（not null）, `columnName`（not null）, `dataType`（not null）, `nullable`
      （not null）, `comment`（nullable）, `ordinalPosition`（not null）,
      `primaryKeySequence`（nullable）, `stale`（not null、既定`false`）, `importedAt`,
      `updatedAt`。一意制約`(tableId, columnName)`）、`TableType`（enum: `TABLE`, `VIEW`）を
      生成。いずれも明示的な`@Table(indexes = {...})`は追加しない（一意制約のみで賄う、
      `nfr-design-patterns.md` 5.1）。
      `RdbmsConnection`と同様の規約（protected引数なしコンストラクタ、全項目コンストラクタ、
      `update`ミューテータ、getterのみ）で生成した。`./gradlew compileJava`は既知の
      `RdbmsConnectionRepository`未解決参照（4箇所、同一原因）のみで失敗することを確認、
      新規エラーが無いことを検証した。
- [x] 2-10. `backend/src/main/java/cherry/mastermeister/schema/` に
      `SchemaImportResult`（record: `boolean success, int tableCount, String message`）、
      `TableMetadata`（record: `String schemaName, String tableName, TableType tableType,
      String comment`）、`ColumnDetail`（record: `String columnName, String dataType,
      boolean nullable, String comment, int ordinalPosition, Integer primaryKeySequence`）、
      `TableDetail`（record: `String schemaName, String tableName, TableType tableType,
      String comment, List<ColumnDetail> columns`）を生成。
      `./gradlew compileJava`は既知の`RdbmsConnectionRepository`未解決参照（4箇所、同一原因）
      のみで失敗することを確認、新規エラーが無いことを検証した。
- [x] 2-11. `backend/src/main/java/cherry/mastermeister/schema/SchemaImportService.java`
      （`@Service`、メソッド全体に`@Transactional`、`nfr-design-patterns.md` 4.2）:
      `SchemaImportResult importSchema(Long connectionId, Long adminUserId)`。
      `ConnectionPoolRegistry`経由で対象RDBMSへの`DataSource`を取得し、
      `Connection.getMetaData()`（標準`java.sql.DatabaseMetaData`）で
      `DialectStrategy.getSchemaResolutionMode()`に応じて`CATALOG_BASED`なら
      `getTables(catalog = RdbmsConnection.databaseName, schema = null, ...)`、
      `SCHEMA_BASED`なら`getSchemas(catalog, null)`列挙後スキーマごとに`getTables`を呼び出し
      （`nfr-design-patterns.md` 4.1）、`getColumns`/`getPrimaryKeys`でカラム・主キー構成を
      取得する。読み取った各テーブル/カラムを`(connectionId, schemaName, tableName)`/
      `(tableId, columnName)`の物理名で既存行とマッチングしupsert、対象RDBMS側で
      見つからなくなった既存行は`stale = true`に設定（削除しない、`business-rules.md`
      2.2）。ビュー（`tableType = VIEW`）のカラムは`primaryKeySequence`を常に`null`とする
      （`business-rules.md` 2.1）。処理途中の例外は`@Transactional`により内部DB変更を
      全ロールバック（`business-rules.md` 2.3）。成功/失敗いずれも
      `AuditLogService.record(ADMIN_OPERATION, SCHEMA_IMPORTED, adminUserId, connectionId,
      SUCCESS|FAILURE, connectionName, ...)`を呼び出す。
      実装ノート: `SQLException`は`try`ブロック内でキャッチし、
      `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`で
      明示的にロールバック指定した上で`SchemaImportResult(false, 0, message)`を返す
      （`@Transactional`メソッドが正常returnする経路のみを使うため、キャッチ後も
      ロールバックさせるにはこの明示指定が必要）。`SchemaTableRepository`/
      `SchemaColumnRepository`（未生成、item 8-2/8-3で生成予定）に対し、本項目時点で
      次のメソッドシグネチャを新たに確定した:
      `findByConnectionIdAndSchemaNameAndTableName(Long, String, String): Optional<SchemaTable>`,
      `findByConnectionId(Long): List<SchemaTable>`,
      `findByTableIdAndColumnName(Long, String): Optional<SchemaColumn>`,
      `findByTableId(Long): List<SchemaColumn>`。`./gradlew compileJava`は既知の
      `RdbmsConnectionRepository`未解決参照に加え、上記2つの未生成リポジトリ参照
      （新規、item 8-2/8-3待ちの既定路線内）のみで失敗することを確認、それ以外の
      新規エラーが無いことを検証した。
- [x] 2-12. `backend/src/main/java/cherry/mastermeister/schema/SchemaQueryService.java`
      （`@Service`）: `List<String> listSchemas(Long connectionId)`（`stale = false`の
      `SchemaTable`から`schemaName`をdistinct取得）、`List<TableMetadata>
      listTables(Long connectionId, String schema)`（`stale = false`のみ、
      `business-rules.md` 2.4）、`TableDetail getTableDetail(Long connectionId, String
      schema, String table)`（`stale = false`のカラムのみ、`primaryKeySequence`昇順で
      整列、`EntityNotFoundException`）を実装（`business-logic-model.md`フロー5）。
      `SchemaTableRepository`/`SchemaColumnRepository`（未生成、item 8-2/8-3で生成予定）に
      対し、本項目で新たに次のメソッドシグネチャを確定した:
      `findByConnectionIdAndStaleFalse(Long): List<SchemaTable>`,
      `findByConnectionIdAndSchemaNameAndStaleFalse(Long, String): List<SchemaTable>`,
      `findByConnectionIdAndSchemaNameAndTableNameAndStaleFalse(Long, String, String):
      Optional<SchemaTable>`, `findByTableIdAndStaleFalse(Long): List<SchemaColumn>`。
      `primaryKeySequence`昇順整列は`Comparator.nullsLast`でnull（ビュー由来カラム）を
      末尾に回す。`./gradlew compileJava`は既知の3型（`RdbmsConnectionRepository`,
      `SchemaTableRepository`, `SchemaColumnRepository`）未解決参照のみで15個のエラーで
      失敗することを確認、新規の未知シンボルが無いことを検証した。これでStep 2
      （ビジネスロジック生成）は完了。

### Step 3: ビジネスロジック単体テスト（PBT-01〜PBT-08, PBT-10）
`business-logic-model.md`のP1〜P11に加え、`SchemaQueryService`のstale除外挙動は
Functional Design時点のP1〜P11に含まれていなかったため、U1/U2の`common.dialect`（P9〜P12）・
`OpaqueTokenGenerator`（P12）と同様、本Code Generation計画で新たにP12として識別する。
- [x] 3-1. **P1**（`EncryptedStringConverter`のRound-trip）、**P2**（暗号化後の値が平文と
      不一致であるInvariant）: jqwikでランダムな文字列を生成する`@Property`テストを
      `EncryptedStringConverterTest`に生成。
      `backend/src/test/java/cherry/mastermeister/rdbmsconnection/EncryptedStringConverterTest.java`
      を新規生成。`plainTexts`（0〜200文字のランダム文字列）を`@Provide`で供給し、
      `encryptThenDecryptRoundTripsToOriginal`（P1）と`encryptedValueDiffersFromPlainText`
      （P2）の2つの`@Property`テストを実装。コンバータの鍵はテスト内で`SecureRandom`により
      毎回ランダムな32バイトAES鍵を生成しBase64化して渡す。`./gradlew test --tests
      EncryptedStringConverterTest`は、既知の3型（`RdbmsConnectionRepository`,
      `SchemaTableRepository`, `SchemaColumnRepository`）未解決参照のみで15個のエラーで
      失敗することを確認（item 2-12から変化なし、新規シンボルなし）。Step 8完了後に
      テスト自体が実行可能になる。
- [x] 3-2. **P3**（JDBC URL組み立てのInvariant: `additionalParams`の有無によらず構造化
      ベースURLで始まり、非空時のみ1回だけ末尾付加）: `DialectStrategy`各実装の
      `buildJdbcUrl`＋`RdbmsConnectionService`のURL組み立てロジックをjqwikでランダムな
      `additionalParams`（空/非空）を生成し検証する`@Property`テストを
      `RdbmsConnectionServiceTest`に生成。
      URL組み立てロジックは`testConnection`内にインライン記述されHikariCPの実接続を伴う
      ため直接プロパティテストするには不向きと判明し、副作用のない同一ロジックを
      package-privateメソッド`buildJdbcUrl(ConnectionConfig): String`として`
      RdbmsConnectionService`に抽出（動作は変更せず、`testConnection`はこれを呼び出す形に
      変更）。この抽出済みメソッドに対し、`additionalParams`がnull/空/空白のみの場合は
      ベースURLと完全一致すること（Property 1）、非空の場合は`"?" + additionalParams`が
      末尾に1回だけ付加されること（Property 2、`indexOf("?") == lastIndexOf("?")`で
      二重付加が無いことも検証）の2つの`@Property`テストを`RdbmsConnectionServiceTest`に
      新規生成。`RdbmsConnectionRepository`/`ConnectionPoolRegistry`/`AuditLogService`は
      Mockitoでモック化し、`DialectStrategyFactory`は実装4種を実際に組み込んで使用。
      `./gradlew compileJava`は既知の3型（`RdbmsConnectionRepository`,
      `SchemaTableRepository`, `SchemaColumnRepository`）未解決参照のみで15個のエラーで
      失敗することを確認（item 3-1から変化なし、新規シンボルなし）。
- [x] 3-3. **P4**（`ConnectionPoolRegistry.getDataSource`のIdempotence）、**P5**
      （`invalidate`後の新規インスタンス生成Invariant）: `RdbmsConnectionRepository`を
      モック化し、複数回呼び出し・`invalidate`前後の同一性/非同一性を検証する`@Property`
      テストを`ConnectionPoolRegistryTest`に生成。
      `createDataSource`が`new HikariDataSource(config)`で実接続を伴うため、モックだけでは
      検証不能と判明。埋め込み（in-process）のH2 TCPサーバ（`org.h2.tools.Server`、
      `@BeforeContainer`/`@AfterContainer`でテストクラス全体につき1回起動/停止、ランダム
      空きポートに`-tcpPort 0`、`-ifNotExists`でリモートからのDB作成を許可）を起動し、
      `mem:pooltest;DB_CLOSE_DELAY=-1`というインメモリDBに対しTCP経由で接続することで
      実際のプール生成を安全に検証。`build.gradle.kts`に`testImplementation(
      "com.h2database:h2")`を追加（既存の`runtimeOnly`はテストのコンパイルクラスパスに
      含まれないため、`org.h2.tools.Server`を直接importするのに必要）。`RdbmsConnectionRepository`
      はMockitoでモック化し、テスト対象の`connectionId`に対し上記H2接続情報を持つ
      `RdbmsConnection`を返すよう都度スタブ。`getDataSourceIsIdempotent`（P4: 同一id
      への複数回呼び出しが同一インスタンスを返す）と`getDataSourceReturnsNewInstance
      AfterInvalidate`（P5: `invalidate`後は異なる新規インスタンスを返す）の2
      `@Property`テストを生成（実接続を伴うため`tries = 20`に抑制）。事前にスクラッチで
      H2 TCPサーバ起動+接続の動作を単体検証済み。`./gradlew compileJava`は既知の3型のみ
      15個のエラーで失敗することを確認（item 3-2から変化なし、新規シンボルなし）。
- [x] 3-4. **P6**（`testConnection`呼び出し前後で`ConnectionPoolRegistry`のキャッシュ状態が
      変化しないInvariant）: jqwikで2パターン（未保存設定/既存接続ID）をランダムに生成し
      検証する`@Property`テストを`RdbmsConnectionServiceTest`に生成。
      `testConnection(ConnectionConfig)`・`testConnection(Long)`の実装を確認したところ、
      いずれも`ConnectionPoolRegistry`を一切参照せず`HikariDataSource`を直接生成/破棄する
      実装であるため、`ConnectionPoolRegistry`をMockitoでモック化し呼び出し後に
      `verifyNoInteractions(registry)`で無操作を検証する形で実装。
      `registryCacheUnchangedForUnsavedConfig`（未保存の`ConnectionConfig`を直接渡す
      パターン）と`registryCacheUnchangedForExistingConnectionId`（`connectionId`を
      渡し`RdbmsConnectionRepository`をモックで既存接続に見せかけるパターン）の2
      `@Property`テストを`RdbmsConnectionServiceTest`に追加。実接続を伴い失敗時に
      HikariCPが非チェック例外を送出しうるため、呼び出しは`try/catch(RuntimeException)`
      で包み検証対象を「例外の有無」ではなく「レジストリが一切操作されないこと」に限定。
      接続失敗を高速化するためhostは`"localhost"`固定、`connectionTimeout`は
      `Duration.ofMillis(200)`、`tries = 10`に抑制。`configsForCacheCheck`・
      `connectionIds`の2つの`@Provide`メソッドを追加。`./gradlew compileJava`／
      `compileTestJava`は既知の3型（`RdbmsConnectionRepository`, `SchemaTableRepository`,
      `SchemaColumnRepository`）未解決参照のみで15個のエラーで失敗することを確認
      （item 3-3から変化なし、新規シンボルなし）。
- [x] 3-5. **P7**（`importSchema`の物理名マッチングInvariant: idの不変性）、**P8**
      （削除された物理名が`stale = true`となり行削除されないInvariant）、**P9**
      （`importSchema`の連続実行に対するIdempotence）: `SchemaTableRepository`/
      `SchemaColumnRepository`をモック化し、対象RDBMS側のメタデータ変化パターン
      （不変/追加/削除）をjqwik Arbitraryで生成する`@Property`テストを
      `SchemaImportServiceTest`に生成。
      実リポジトリ未生成（Step 8）のため、`assignId`によるリフレクションID採番と
      `ArrayList`ベースの状態保持を組み合わせた手作りフェイクリポジトリ
      （`FakeRepositories`、Mockito`thenAnswer`で`save`/`findBy*`を実装）を使用。
      対象RDBMSには埋め込みH2 TCPサーバー（`ConnectionPoolRegistryTest`と同じ起動
      パターン）を使い、`importSchema`呼び出し前後で生DDL（`CREATE`/`ALTER`/`DROP`）を
      直接発行して物理変化を再現。`ChangePattern`（UNCHANGED/ADDED/DELETED）を
      テーブル単位・カラム単位で分けて2つの`@Property`テストとした理由は、
      `markStaleColumns`が`importColumns()`経由でしか呼ばれず、テーブル自体が
      削除された場合はその配下カラムのstale化が一切行われない（既存実装の仕様であり
      本タスクでの変更対象ではない）ため、カラム単位のP8検証はテーブルが存在し続ける
      シナリオでのみ意味を持つと判断したため。
      H2のカタログ名マッチングで2点の落とし穴を確認：(1)
      `DatabaseMetaData.getSchemas(catalog, null)`はcatalog引数の大文字小文字を
      区別するため、DB名は`"SCHEMAIMPORT" + counter`のようにあらかじめ大文字で
      統一する必要がある。(2) `SchemaImportService.importSchema`は
      `connection.getDatabaseName()`（`RdbmsConnection`エンティティの生値）を
      そのままJDBCカタログ名として使用するため、テスト用JDBC URLに含めるDB名と
      `RdbmsConnection.databaseName`に設定する値を完全一致させる必要がある。
      当初`mem:`インメモリDB方式で両者に`mem:`プレフィクスの有無の差異が生じ
      `resolveSchemaNames`が常に0件を返す不具合を実テスト実行で検出したため、
      H2 TCPサーバーを`-baseDir`付きファイルベース方式に変更し解消（`@BeforeContainer`で
      一時ディレクトリを作成、`@AfterContainer`で再帰削除）。
      なお本項目で、`./gradlew compileTestJava`は`compileJava`が失敗している間は
      実行されず（Gradleのタスク依存関係により`compileJava`失敗時点でビルドが中断する
      ため）、item 3-1〜3-4で行っていた「compileTestJavaで15エラー変化なしを確認」という
      検証は実質的に`compileJava`の失敗を再確認していただけで、新規テストファイル自体の
      構文・型正しさは独立検証できていなかったことが判明。本項目ではこれを是正するため、
      `SchemaTableRepository`/`SchemaColumnRepository`/`RdbmsConnectionRepository`の
      一時スタブ（Step 8で生成される実リポジトリと同じメソッドシグネチャのみを持つ、
      本実装に呼び出し箇所を精査して作成した最小限のインタフェース）を`src/main/java`に
      配置した状態で`./gradlew test --tests "...SchemaImportServiceTest"`を実行し、
      `BUILD SUCCESSFUL`（実H2サーバーに対する実プロパティテスト実行）を確認。
      このスタブは本コミットには含めず、Step 3の残り項目（3-6〜3-8）でも同様の実行時
      検証に再利用するため、追跡外ファイルとして一時的に残置する方針とした
      （リポジトリ生成そのものはStep 8の担当であり、スタブは`git status`で`??`表示の
      未追跡ファイルのままであることを確認済み。各コミットではstep実施項目の対象
      ファイルのみをstageするため誤コミットのリスクはない）。
- [x] 3-6. **P10**（`importSchema`失敗時のロールバックRound-trip）: 取り込み処理途中で
      例外を発生させた場合に内部DB状態が処理開始前と一致することを、`@DataJpaTest`＋
      実際のトランザクション境界で検証する`@Property`テストを`SchemaImportServiceTest`に
      生成。
      実装メモ:
      - `SchemaImportServiceTest`内に`@Group @DataJpaTest @JqwikSpringSupport`を付けた
        非staticな入れ子クラス`RollbackRoundTrip`を追加し、既存のMockitoベースの
        P7/P8/P9テスト（Springコンテキスト不要）とはSpringコンテキスト起動要否で分離した。
      - Spring Boot 4.1では`@DataJpaTest`が`spring-boot-test-autoconfigure`から
        新モジュール`spring-boot-data-jpa-test`（パッケージ
        `org.springframework.boot.data.jpa.test.autoconfigure`）へ移動している点が
        既存情報と異なっていたため、importを修正して対応した（依存自体は
        `spring-boot-starter-data-jpa-test`として既存）。
      - `RdbmsConnection`エンティティが参照する`EncryptedStringConverter`はコンストラクタ引数
        `mm.app.rdbms-connection.encryption-key`が未定義（Step 16で本設定予定）だが、
        `@DataJpaTest`はHibernate永続ユニット構築時に本Converterを必ずBean化するため、
        `@TestPropertySource`で`RollbackRoundTrip`グループにのみ有効なテスト専用キー
        （32バイトゼロ列のBase64値）を注入し、本番設定（Step 16の担当範囲）には触れずに解決した。
      - カラム保存失敗を注入する手段として当初`Mockito.spy(columnRepository)`＋`doThrow`を
        試みたが、Spring DataのJPAリポジトリ実装自体がJDK動的プロキシであるため
        `UnfinishedStubbingException`が発生し利用不可と判明。`java.lang.reflect.Proxy`で
        `SchemaColumnRepository`を素朴にラップし、`save`呼び出し時のみ例外を送出、他の
        呼び出しは実Beanへ委譲する方式に置き換えて解決した。
      - `SchemaImportService`をテストコード内で`new`により直接生成すると、Springの
        トランザクション用AOPプロキシを経由しないため`@Transactional`が一切効かず、
        ロールバックの実在を検証できないことが判明（1回目の実行で内部DB状態が
        ロールバックされず残留するテスト失敗として顕在化）。`RollbackRoundTrip`内に
        `@TestConfiguration`の静的入れ子クラスで`SchemaImportService`を`@Bean`として登録し
        （カラム失敗用プロキシはこの`@Bean`メソッド内でのみ組み立てることで、
        `SchemaColumnRepository`型Beanの重複によるオートワイヤ曖昧性を回避）、
        テストメソッドでは`@Autowired`でプロキシ経由のインスタンスを取得する方式に変更し解決した。
      - `@DataJpaTest`が既定でテストメソッドに付与する外側トランザクションを、プロパティ
        テストメソッド自体に`@Transactional(propagation = Propagation.NOT_SUPPORTED)`を
        付けて無効化し、`importSchema()`自身の`@Transactional`（`Propagation.REQUIRED`）が
        唯一の物理トランザクションとなるようにした。これにより、失敗時のロールバックが
        呼び出し直後の時点で既に完了しており、テストメソッド内でリポジトリを問い合わせて
        ロールバック済みの実データを確認できる。
      - `./gradlew test --tests "cherry.mastermeister.schema.SchemaImportServiceTest"`で
        実行し、`RollbackRoundTrip`グループのテスト（`importSchemaRollsBackAllChangesOnFailure`）
        を含め`BUILD SUCCESSFUL`（tests=1, failures=0, errors=0, skipped=0）を確認済み。
- [x] 3-7. **P11**（ビュー取り込み時、`SchemaColumn.primaryKeySequence`が常に`null`である
      Invariant）: `tableType = VIEW`のケースをjqwikで生成する`@Property`テストを
      `SchemaImportServiceTest`に生成。
      実装メモ:
      - `importColumns()`は`tableType == TABLE`の場合のみ`DatabaseMetaData.getPrimaryKeys`を
        問い合わせて`primaryKeySequences`を構築し、`VIEW`の場合は常に空の`Map`のまま
        （＝lookupは常に`null`を返す）という既存実装を確認した上でテストを設計。
      - `importSchemaSetsNullPrimaryKeySequenceForViewColumns`を追加: `ID INT PRIMARY KEY`を
        持つ基底テーブル`BASE`（`C0`〜`Cn-1`のjqwik生成カラムを0〜3個追加）を作成し、
        `CREATE VIEW V1 AS SELECT * FROM BASE`でその主キー列を含む全列をそのまま射影する
        ビューを作成。取り込み後、`V1`の`SchemaTable.tableType`が`VIEW`であること、および
        `ID`列を含む全列の`SchemaColumn.primaryKeySequence`が`null`であることを検証する
        （基底テーブル側でPRIMARY KEYとして定義されている列をビューがそのまま射影していても、
        ビュー側では実装分岐によりnullになることの確認が目的）。
      - P7/P8/P9と同じMockitoベースの`FakeRepositories`/`newService`をそのまま再利用（Spring
        コンテキスト不要のため`RollbackRoundTrip`グループとは独立）。
      - `./gradlew test --tests "cherry.mastermeister.schema.SchemaImportServiceTest"`で実行し、
        `BUILD SUCCESSFUL`（tests=3, failures=0, errors=0, skipped=0、新規テスト含む）を確認済み。
- [x] 3-8. **P12**（`SchemaQueryService`のstale除外Invariant: `listTables`/
      `getTableDetail`が返す結果に`stale = true`の`SchemaTable`/`SchemaColumn`が
      含まれない）: `stale`値の組み合わせをjqwik Arbitraryで生成する`@Property`テストを
      `SchemaQueryServiceTest`に生成。
      実装メモ:
      - 新規`SchemaQueryServiceTest`を`@DataJpaTest @JqwikSpringSupport`のクラス全体に
        付与する形で生成（`SchemaQueryService`には`@Transactional`が付与されておらず
        AOPプロキシを介する必要がないため、item 3-6のような`@TestConfiguration`/`@Bean`は
        不要で、テストメソッド内で`new SchemaQueryService(tableRepository, columnRepository)`
        すれば足りる）。`RdbmsConnectionRepository`を全く使わないテストクラスでも
        `@DataJpaTest`はHibernate永続ユニット構築時に`EncryptedStringConverter`をBean化
        するため、item 3-6と同じ`@TestPropertySource`テスト専用キーを再利用。
      - `listTablesExcludesStaleTables`: 1〜5件のテーブルをjqwikで生成した`stale`値の
        組み合わせで保存し、`listTables`が返す物理名集合が非staleな物理名集合と完全一致
        することを検証。
      - `getTableDetailExcludesStaleColumns`: 非staleな1テーブルに対し1〜5件のカラムを
        jqwikで生成した`stale`値の組み合わせで保存し、`getTableDetail`が返す物理名集合が
        非staleな物理名集合と完全一致することを検証。
      - 両テストとも、リポジトリの`*StaleFalse`派生クエリメソッドの実際の絞り込みを
        検証したいため、`SchemaImportServiceTest`のP7/P8/P9のようなMockitoベースの手製
        フェイクではなく、`@DataJpaTest`による実JPAリポジトリ・実クエリを用いた。
      - `./gradlew test --tests "cherry.mastermeister.schema.SchemaQueryServiceTest"`で実行し、
        `BUILD SUCCESSFUL`（tests=2, failures=0, errors=0, skipped=0）を確認済み。
      - Step 3（P1〜P12プロパティテスト生成）が全項目完了。

### Step 4: ビジネスロジックサマリ
- [x] 4-1. `aidlc-docs/construction/u3-rdbms-connection-schema-import/code/
      business-logic-summary.md`を生成し、Step 2・Step 3で生成したクラス一覧とP1〜P12の
      対応関係を表形式で記載する。
      実装メモ: U2の`business-logic-summary.md`の形式（生成クラス一覧・生成テストクラス一覧・
      P1〜N対応表・補足・既知の課題の5セクション構成）を踏襲して生成した。Step 2の全13項目
      （2-1〜2-12、うち2-3は変更なし確認のみ）から抽出したクラス一覧表と、Step 3の5テスト
      クラス（`EncryptedStringConverterTest`/`RdbmsConnectionServiceTest`/
      `ConnectionPoolRegistryTest`/`SchemaImportServiceTest`/`SchemaQueryServiceTest`）の
      検証方式一覧、およびP1〜P12対応表を記載。補足として、P10/P12が3-6/3-8で確立した
      「一時スタブを`JpaRepository`拡張へアップグレードした上で`@DataJpaTest`により実クエリを
      検証する」方式の理由（手製フェイクでは検証できないSpring Data JPA派生クエリ自体の
      正しさが対象のため）を明記。既知の課題として、Step 8完了までは`compileJava`単体が
      3つの未生成リポジトリ参照により失敗し続ける既定路線であることを記載（U2の
      `contextLoads()`既知課題とは異なる性質のため、U2の書き方をそのまま流用せず本ユニット
      固有の状況として書き直した）。

### Step 5: APIレイヤ生成
- [x] 5-1. `backend/src/main/java/cherry/mastermeister/rdbmsconnection/
      RdbmsConnectionController.java`（`@RestController
      @RequestMapping("/api/rdbms-connections")`）: `POST ""`（`createConnection`→201）,
      `PUT "/{id}"`（`updateConnection`→204）, `GET ""`（`listConnections`→
      `List<ConnectionSummary>`）, `GET "/{id}"`（`getConnection`→`ConnectionDetail`）,
      `POST "/test"`（`testConnection(config)`→`ConnectionTestResult`）,
      `POST "/{id}/test"`（`testConnection(id)`→`ConnectionTestResult`）を生成
      （`business-rules.md` 4節のパスパターンに準拠。`adminUserId`は
      `SecurityContextHolder`から取得——U2の`RegistrationController`と同じ取得方法）。
      **実装メモ**: U2の`RegistrationController`（`userregistration`パッケージ）を参考パターンとして
      採用。`adminUserId`は`Authentication#getPrincipal()`を`(Long)`キャストして取得（同パッケージの
      `JwtAuthenticationFilter`がプリンシパルとしてユーザIDを設定する既存規約に準拠、
      `SecurityContextHolder`から直接取るのではなくメソッド引数の`Authentication`をSpring MVCに
      解決させる方式もU2と同一）。`createConnection`は`@ResponseStatus(HttpStatus.CREATED)`で201、
      `updateConnection`は`ResponseEntity.noContent().build()`で204。`testConnection`は
      オーバーロード2種（`ConnectionConfig`引数版・`Long id`引数版）をパスで区別
      （`POST /test`と`POST /{id}/test`）し、いずれも`RdbmsConnectionService`の同名メソッドへ
      委譲するのみでコントローラ側に業務ロジックを持たせない。`RdbmsConnectionService`・
      DTO（`ConnectionConfig`/`ConnectionSummary`/`ConnectionDetail`/`ConnectionTestResult`）は
      Step 2で生成済みのものをそのまま利用。`./gradlew compileJava`で該当ファイルが問題なく
      コンパイルされることを確認（3つの未追跡スタブリポジトリが`src/main/java`配下に存在する
      ローカル環境限定、Step 8まではコミット後のクリーンチェックアウトでは依然失敗する既知の状態）。
- [x] 5-2. `backend/src/main/java/cherry/mastermeister/schema/SchemaController.java`
      （`@RestController @RequestMapping("/api/rdbms-connections/{connectionId}")`）:
      `POST "/schema-import"`（`importSchema`→`SchemaImportResult`）,
      `GET "/schemas"`（`listSchemas`→`List<String>`）,
      `GET "/schemas/{schema}/tables"`（`listTables`→`List<TableMetadata>`）,
      `GET "/schemas/{schema}/tables/{table}"`（`getTableDetail`→`TableDetail`）を生成
      （`business-rules.md` 4節のパスパターンに準拠）。
      **実装メモ**: `connectionId`はクラスレベルの`@RequestMapping`パス変数として共通化し、
      各メソッドで`@PathVariable Long connectionId`を受け取る形に統一。`importSchema`は
      `SchemaImportService`へ、`listSchemas`/`listTables`/`getTableDetail`は
      `SchemaQueryService`へそれぞれ委譲するのみでコントローラ側に業務ロジックを持たせない。
      `importSchema`の`adminUserId`取得方法は5-1の`RdbmsConnectionController`と同一
      （`Authentication#getPrincipal()`を`(Long)`キャスト）。`SchemaImportService`・
      `SchemaQueryService`はStep 2で生成済みのものをそのまま利用。
      `./gradlew compileJava`で該当ファイルが問題なくコンパイルされることを確認
      （3つの未追跡スタブリポジトリが存在するローカル環境限定、Step 8完了までは
      コミット後のクリーンチェックアウトでは依然失敗する既知の状態、変化なし）。
- [x] 5-3. `backend/src/main/java/cherry/mastermeister/security/SecurityConfig.java`
      （既存、ブラウンフィールド修正）に`authorizeHttpRequests`の
      `anyRequest().authenticated()`より前の行として
      `requestMatchers("/api/rdbms-connections/**").hasRole("ADMIN")`を追記
      （`business-rules.md` 4節「全機能が管理者専用」、U1 NFR Design 1.3の規約に従う）。
      **実装メモ**: 既存の`/api/audit-logs/**`エントリ直後、`anyRequest().authenticated()`直前に
      1行追加。`/api/rdbms-connections/**`は`SchemaController`のパス
      （`/api/rdbms-connections/{connectionId}/schemas`等）も前方一致で包含するため、
      `RdbmsConnectionController`・`SchemaController`両方の全エンドポイントが本行1本で
      管理者専用になる。`./gradlew compileJava`で`BUILD SUCCESSFUL`を確認
      （3つの未追跡スタブリポジトリが存在するローカル環境限定、変化なし）。

### Step 6: APIレイヤ単体テスト
- [x] 6-1. `RdbmsConnectionControllerTest`（`@WebMvcTest` + `spring-security-test`）:
      接続CRUD・接続テスト（保存前/既存接続）を`@WithMockUser(roles = "ADMIN")`と
      管理者以外403・未認証401のexample-basedテストで検証。
      **実装メモ**: U2の`RegistrationControllerTest`をパターンとして踏襲し、
      `@WebMvcTest(RdbmsConnectionController.class)` + `@Import({SecurityConfig.class,
      RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})`で構成。
      6エンドポイント（create/update/list/get/test-by-config/test-by-id）それぞれについて
      管理者成功系・非管理者403・未認証401の3パターンを検証（`createConnection`/
      `updateConnection`は`Authentication`を`UsernamePasswordAuthenticationToken`で
      明示注入し、principalがadminUserIdとしてサービス呼び出しに渡ることも
      `verify`で確認）。`RdbmsConnectionService`・`JwtTokenValidator`は`@MockitoBean`で
      スタブ化。`./gradlew test --tests
      "cherry.mastermeister.rdbmsconnection.RdbmsConnectionControllerTest"`で
      `BUILD SUCCESSFUL`を確認。
- [x] 6-2. `SchemaControllerTest`（`@WebMvcTest` + `spring-security-test`）: スキーマ取り込み・
      スキーマ/テーブル/カラム参照を同様のexample-basedテストで検証。
      **実装メモ**: 6-1の`RdbmsConnectionControllerTest`と同一パターンで構成。
      `@WebMvcTest(SchemaController.class)` + `@Import({SecurityConfig.class,
      RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})`。
      4エンドポイント（schema-import/schemas一覧/tables一覧/table詳細）それぞれについて
      管理者成功系・非管理者403・未認証401の3パターンを検証（`importSchema`は
      `Authentication`を`UsernamePasswordAuthenticationToken`で明示注入し、principalが
      adminUserIdとしてサービス呼び出しに渡ることも`verify`相当（`when(...eq(1L))`の
      スタブ一致）で確認）。`SchemaImportService`・`SchemaQueryService`・
      `JwtTokenValidator`は`@MockitoBean`でスタブ化。`./gradlew test --tests
      "cherry.mastermeister.schema.SchemaControllerTest"`で`BUILD SUCCESSFUL`を確認。

### Step 7: APIレイヤサマリ
- [x] 7-1. `aidlc-docs/construction/u3-rdbms-connection-schema-import/code/
      api-layer-summary.md`を生成し、エンドポイント一覧（パス・メソッド・認可要件・
      リクエスト/レスポンス形状）を記載。
      **実装メモ**: U2の`api-layer-summary.md`と同一構成（エンドポイント一覧表→
      Controllerごとの詳細→エラーレスポンス表→テストカバレッジ表）で生成。
      10エンドポイント（`RdbmsConnectionController`6件、`SchemaController`4件）の
      パス/メソッド/認可要件/リクエスト・レスポンスJSON例を記載。エラーレスポンスは
      本ユニット新規の例外クラスを追加していないため、既存`EntityNotFoundException`
      （404 `ENTITY_NOT_FOUND`）のみ再利用している旨を明記。テストカバレッジ表は
      6-1/6-2で生成した`RdbmsConnectionControllerTest`（15件）・`SchemaControllerTest`
      （10件）を記載。

### Step 8: リポジトリレイヤ生成
- [x] 8-1. `backend/src/main/java/cherry/mastermeister/rdbmsconnection/
      RdbmsConnectionRepository.java`（`JpaRepository<RdbmsConnection, Long>`）を生成。
      これまでuntracked状態だった暫定スタブ（内容は同一、ライセンスヘッダなし）を
      正式なApache License 2.0ヘッダ付きファイルで置き換え、コミット対象とした。
      `./gradlew test --tests "cherry.mastermeister.rdbmsconnection.*"`で`BUILD SUCCESSFUL`
      を確認。
- [x] 8-2. `backend/src/main/java/cherry/mastermeister/schema/SchemaTableRepository.java`
      （`JpaRepository<SchemaTable, Long>`。`Optional<SchemaTable>
      findByConnectionIdAndSchemaNameAndTableName(Long, String, String)`（upsertマッチング
      用）, `List<String> findDistinctSchemaNameByConnectionIdAndStaleFalse(Long)`,
      `List<SchemaTable> findByConnectionIdAndSchemaNameAndStaleFalse(Long, String)`,
      `List<SchemaTable> findByConnectionId(Long)`（再取り込み時のstale判定対象取得用）を
      定義）を生成。
      実装時の乖離: Step 2で先行実装済みの`SchemaImportService`/`SchemaQueryService`（item
      2-11/2-12でコミット済み、変更不可）が実際に参照しているメソッド集合に合わせて生成した
      （`SchemaQueryService.listSchemas`は`findDistinctSchemaNameByConnectionIdAndStaleFalse`
      ではなく`findByConnectionIdAndStaleFalse`の結果をストリームでdistinct変換する実装のため）。
      最終シグネチャ: `findByConnectionIdAndSchemaNameAndTableName`,
      `findByConnectionIdAndSchemaNameAndTableNameAndStaleFalse`, `findByConnectionId`,
      `findByConnectionIdAndStaleFalse`, `findByConnectionIdAndSchemaNameAndStaleFalse`の5件。
      `./gradlew compileJava`で`BUILD SUCCESSFUL`を確認（残る未生成は`SchemaColumnRepository`
      のみ）。
- [x] 8-3. `backend/src/main/java/cherry/mastermeister/schema/SchemaColumnRepository.java`
      （`JpaRepository<SchemaColumn, Long>`）を生成。これまでuntracked状態だった暫定スタブ
      （内容は同一、ライセンスヘッダなし）を正式なApache License 2.0ヘッダ付きファイルで
      置き換え、コミット対象とした。8-2と同様、計画時点の速記シグネチャ
      `findByTableIdAndStaleFalseOrderByOrdinalPositionAsc`は実際には使われておらず
      （`SchemaQueryService.getTableDetail`は`findByTableIdAndStaleFalse`の結果をストリームで
      `Comparator.comparing(SchemaColumn::getPrimaryKeySequence, ...)`によりインメモリソート
      する実装のため）、`grep`で確認した実際の呼び出し箇所
      （`SchemaImportService`: `findByTableIdAndColumnName`, `findByTableId`。
      `SchemaQueryService`: `findByTableIdAndStaleFalse`）に一致させた最終シグネチャ
      `findByTableIdAndStaleFalse`, `findByTableIdAndColumnName`, `findByTableId`の3件を採用。
      既存の暫定スタブが偶然この3件と完全一致していたため、ライセンスヘッダ追加のみで
      内容変更なし。`./gradlew compileJava`で`BUILD SUCCESSFUL`を確認。Step 8の3ファイル
      （8-1/8-2/8-3）すべて正式生成・コミット完了。

### Step 9: リポジトリレイヤ単体テスト
- [x] 9-1. `RdbmsConnectionRepositoryTest`/`SchemaTableRepositoryTest`/
      `SchemaColumnRepositoryTest`（いずれも`@DataJpaTest`、組み込みH2）: 基本CRUD・上記
      クエリメソッドのexample-basedテストを生成。一意制約
      （`(connectionId, schemaName, tableName)`, `(tableId, columnName)`）違反時の例外発生も
      検証する。`RdbmsConnectionRepositoryTest`はカスタムクエリ・unique制約を持たないため
      CRUD＋`EncryptedStringConverter`経由のpassword暗号化往復確認のみ。
      作業中に、Step 2で追加された`RdbmsConnection`エンティティ（`EncryptedStringConverter`が
      `mm.app.rdbms-connection.encryption-key`をコンストラクタ注入で要求）により、
      `@DataJpaTest`のデフォルトエンティティスキャンがアプリ全体（単一`@SpringBootApplication`
      ルート）に及ぶため、U1/U2の既存4つの`@DataJpaTest`クラス（`UserRepositoryTest`/
      `RegistrationTokenRepositoryTest`/`RefreshTokenRepositoryTest`/`AuditLogRepositoryTest`、
      いずれも`RdbmsConnection`と無関係）まで巻き添えで壊れていることを発見。ユーザ確認の上、
      共通test設定による一括修正を採用。当初`src/test/resources/application.properties`→
      `application.yml`（mainと同名）を試みたが、`classpath:application.yml`解決は
      マージでなく完全上書きのため`MasterMeisterApplicationTests.contextLoads()`
      （JWT秘密鍵等mainの他プロパティ欠落）を壊すことが判明し、撤回。最終的に
      `src/test/resources/application-test.yml`（テスト専用暗号化キーのみを定義）＋
      `backend/build.gradle.kts`の`tasks.withType<Test>`に
      `systemProperty("spring.profiles.active", "test")`を追加する、Spring Boot標準の
      プロファイル別設定ファイル方式（`application-dev.yml`と同じ命名規約）を採用。
      プロファイル別ファイルはベースの`application.yml`と加算マージされるため、
      既存4クラスも個別`@TestPropertySource`なしに復旧し、`./gradlew test`
      （全29テストクラス・133件）で`BUILD SUCCESSFUL`を確認。
      追記：上記の恒久修正（`application-test.yml`＋`spring.profiles.active=test`）が
      全`@DataJpaTest`に適用されるようになったため、Step 3で暫定対応として個別追加していた
      `SchemaImportServiceTest.RollbackRoundTrip`と`SchemaQueryServiceTest`の
      `@TestPropertySource(properties = "mm.app.rdbms-connection.encryption-key=...")`
      （およびその経緯を説明するコメント）は重複となり削除。`./gradlew test`で
      `BUILD SUCCESSFUL`（全29テストクラス）を再確認。

### Step 10: リポジトリレイヤサマリ
- [x] 10-1. `aidlc-docs/construction/u3-rdbms-connection-schema-import/code/
      repository-layer-summary.md`を生成し、3リポジトリのクエリメソッド一覧とインデックス
      設計（一意制約のみ、`nfr-design-patterns.md` 5.1）を記載。
      U2の`repository-layer-summary.md`の構成を踏襲: `RdbmsConnectionRepository`
      （カスタムクエリなし、`EncryptedStringConverter`による透過的暗号化のみ言及）、
      `SchemaTableRepository`（5メソッド）、`SchemaColumnRepository`（3メソッド）の
      クエリメソッド一覧表、インデックス設計節（`SchemaTable`の
      `(connectionId, schemaName, tableName)`、`SchemaColumn`の`(tableId, columnName)`、
      いずれも複合unique制約のみで非unique列への明示的インデックスは追加しない旨）、
      Step 9のテストカバレッジ表、および同項目内で発見・解決した`@DataJpaTest`
      全体スキャン回帰と`application-test.yml`修正・重複`@TestPropertySource`削除の経緯を
      要約として記載。

### Step 11: フロントエンドコンポーネント生成
- [x] 11-1. `frontend/src/features/rdbmsConnection/` に
      `ConnectionListPage.tsx`（マウント時`connectionApi.listConnections()`、
      `data-testid="connection-list-page-new-button"`）、
      `ConnectionTable.tsx`（`DataTable`〈U1〉利用、
      `data-testid="connection-table-edit-button"` / `-test-button"` /
      `-import-schema-button"`、`ToastNotification`〈U1〉で接続テスト結果通知）、
      `ConnectionFormPage.tsx`（新規登録/編集共用、`data-testid=
      "connection-form-page-name-input"` / `-rdbms-type-select"` / `-host-input"` /
      `-port-input"` / `-database-name-input"` / `-username-input"` / `-password-input"` /
      `-additional-params-input"` / `-test-button"` / `-submit-button"` /
      `-test-result-message"`。編集時パスワード欄は空欄表示、空欄のまま保存で変更なし、
      `frontend-components.md`）、`api/connectionApi.ts`（`createConnection`,
      `updateConnection`, `listConnections`, `getConnection`, `testConnection`〈設定/ID
      両対応〉）、`types.ts`を生成。
      実装: `types.ts`（`RdbmsType`, `ConnectionSummary`, `ConnectionDetail`,
      `ConnectionConfig`, `ConnectionTestResult`）、`api/connectionApi.ts`（5関数。
      `testConnection`はTS関数オーバーロードで`ConnectionConfig`引数版と`connectionId`
      （`number`）引数版を単一エクスポート名に束ねた）、`ConnectionFormPage.tsx`（`mode`
      propで新規登録/編集共用、`useParams`で`id`取得、編集時は`useEffect`で
      `getConnection`を呼び既存値をセットするがパスワード欄のみ常に空欄初期化——
      `RdbmsConnectionService.updateConnection`が空欄パスワードを「変更なし」として
      扱う実装（`backend/.../RdbmsConnectionService.java:79-81`）を確認済み、指定の
      11個の`data-testid`全て配置）、`ConnectionTable.tsx`（`DataTable`利用、編集は
      `react-router-dom`の`Link`で`/admin/rdbms-connections/:id`へ遷移、接続テスト/
      スキーマ取り込みは`onTest`/`onImportSchema`コールバックpropとして委譲）、
      `ConnectionListPage.tsx`（マウント時`listConnections`、`handleTest`で
      `testConnection(id)`を呼び結果を`ToastNotification`で通知、新規登録導線は
      `Link`で`/admin/rdbms-connections/new`へ、`handleImportSchema`は本項目時点では
      プレースホルダ——`SchemaImportPanel`〈`features/schema`〉が存在しない11-1時点では
      結線せず、11-2で実結線する旨をコメントで明記）。`AppRouter.tsx`/`AppLayout.tsx`
      修正は11-3/11-4で行うためスコープ外。`npx oxlint`・`npx tsc -b`いずれも
      エラーなしを確認。
- [ ] 11-2. `frontend/src/features/schema/` に
      `SchemaImportPanel.tsx`（`data-testid="schema-import-panel-import-button"` /
      `-result-message"`）、
      `SchemaBrowserPage.tsx`（マウント時`schemaApi.listSchemas(connectionId)`）、
      `SchemaSelector.tsx`（`data-testid="schema-selector-select"`）、
      `TableList.tsx`（`DataTable`〈U1〉利用、`data-testid="table-list-row"`）、
      `TableDetailPanel.tsx`（カラム一覧・主キー構成表示）、
      `api/schemaApi.ts`（`importSchema`, `listSchemas`, `listTables`, `getTableDetail`）、
      `types.ts`を生成（`frontend-components.md`）。
- [ ] 11-3. `frontend/src/routes/AppRouter.tsx`（既存、ブラウンフィールド修正）:
      `AuthenticatedRoutes`内に`/admin/rdbms-connections`（`ConnectionListPage`）,
      `/admin/rdbms-connections/new`（`ConnectionFormPage mode='create'`）,
      `/admin/rdbms-connections/:id`（`ConnectionFormPage mode='edit'`）,
      `/admin/schema/:connectionId`（`SchemaBrowserPage`）を`ProtectedRoute
      requiredRole="ADMIN"`配下に追加（`frontend-components.md`ルーティング表）。
      `SchemaImportPanel`は独立ルートを持たず`ConnectionListPage`/`ConnectionFormPage`内から
      起動するモーダル/パネルとして生成する。
- [ ] 11-4. `frontend/src/components/AppLayout.tsx`（既存、ブラウンフィールド修正）に
      「RDBMS接続管理」（`/admin/rdbms-connections`）へのナビゲーションリンクを追加
      （管理者ロールのみ表示、U1の出し分け機構を再利用、`frontend-components.md`）。

### Step 12: フロントエンドコンポーネント単体テスト
- [ ] 12-1. Vitest + React Testing Library で以下のexample-basedテストを生成:
      `connectionApi`（各関数のリクエスト形状）,
      `ConnectionListPage`/`ConnectionTable`（一覧表示、編集/テスト/取り込み導線 —
      ADM-3 AC）,
      `ConnectionFormPage`（新規登録・編集の保存、接続テスト結果表示、パスワード空欄時の
      維持動作 — MVP-7 AC）,
      `schemaApi`（各関数のリクエスト形状）,
      `SchemaImportPanel`（取り込み実行と結果表示 — MVP-8 AC）,
      `SchemaBrowserPage`/`SchemaSelector`/`TableList`/`TableDetailPanel`（スキーマ選択→
      テーブル一覧→カラム詳細の遷移、複合主キー表示）,
      `AppRouter`（追加ルートの保護ルート・未認証リダイレクト）,
      `AppLayout`（管理者ロールでのナビゲーションリンク表示）。

### Step 13: フロントエンドコンポーネントサマリ
- [ ] 13-1. `aidlc-docs/construction/u3-rdbms-connection-schema-import/code/
      frontend-summary.md`を生成し、`features/rdbmsConnection/`・`features/schema/`の
      コンポーネント一覧・`data-testid`一覧を記載。

### Step 14: データベースマイグレーションスクリプト
- [ ] 14-1. **該当なし（N/A）**: U1/U2と同様、内部DB(H2)のスキーマ管理はJPAの自動DDL生成に
      委ね、Flyway/Liquibase等は導入しない。

### Step 15: ドキュメント生成
- [ ] 15-1. Step 4/7/10/13で生成した各サマリに加え、
      `aidlc-docs/construction/u3-rdbms-connection-schema-import/code/testing-summary.md`
      （P1〜P12とテストクラスの対応表、example-basedテスト一覧、PBT-10補完的テスト戦略の
      再確認）を生成する。

### Step 16: デプロイ成果物生成
- [ ] 16-1. `backend/src/main/resources/application.yml`（既存、ブラウンフィールド修正）に
      追記: `mm.app.rdbms-connection.encryption-key`（既定未設定、fail-fast、環境変数
      プレースホルダ）,
      `mm.app.rdbms-connection.pool.maximum-pool-size`（既定`5`）,
      `mm.app.rdbms-connection.pool.minimum-idle`（既定`0`）,
      `mm.app.rdbms-connection.pool.connection-timeout`（既定`5000`ミリ秒、
      `tech-stack-decisions.md`設定キー一覧）。

---

## 完了基準
- 上記全ステップの生成物がワークスペースルート配下に作成され、対応する単体テストが
  生成されていること（実行・グリーン確認はBuild and Testステージで行う）。
- P1〜P12全ての性質にjqwik `@Property`テストが対応していること（PBT-02〜PBT-08準拠）。
- `aidlc-docs/construction/u3-rdbms-connection-schema-import/code/`配下に5つのサマリ
  ドキュメント（business-logic-summary.md, api-layer-summary.md,
  repository-layer-summary.md, frontend-summary.md, testing-summary.md）が生成されている
  こと。