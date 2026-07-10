# nfr-design-patterns.md — U3: RDBMS Connection & Schema Import

`u3-rdbms-connection-schema-import-nfr-design-plan.md`（Question 1〜5、全回答A）に基づく設計パターン。

---

## 1. Scalability Patterns

### 1.1 ConnectionPoolRegistryのキャッシュ実装（Question 1）

- `ConnectionPoolRegistry`はSpringのシングルトンスコープ（`@Component`のデフォルトスコープ）の
  Beanとする。キャッシュ機構が有効に機能するための前提条件であり、prototype等の他スコープでは
  注入のたびに空のマップを持つ別インスタンスが生成され、同一`connectionId`に対して複数のプールが
  並行生成されてしまう。
- キャッシュ本体は`ConcurrentHashMap<Long, HikariDataSource>`。`getDataSource(connectionId)`は
  `computeIfAbsent(connectionId, id -> createDataSource(...))`で遅延生成する。
  `ConcurrentHashMap`が同一キーに対する生成処理を排他的に実行することを保証するため、
  同一`connectionId`に対して二重にプールが生成される競合を防げる。追加の同期プリミティブ
  （`synchronized`等）を自前実装する必要はない。
- `invalidate(connectionId)`は`remove(connectionId)`で取得した`HikariDataSource`（存在すれば）
  に対して`close()`を呼ぶ。`business-rules.md` 1.5（`updateConnection`成功時・削除時の呼び出し）
  と整合する。

---

## 2. Logical Components Patterns

### 2.1 主要コンポーネントのパッケージ配置（Question 2）

- `EncryptedStringConverter`・`ConnectionPoolRegistry`・`RdbmsConnectionService`は
  `cherry.mastermeister.rdbmsconnection`パッケージに配置する。
- `SchemaImportService`・`SchemaQueryService`は`cherry.mastermeister.schema`パッケージに
  配置する。
- `docs/PROJECT_STRUCTURE.md`（Application Designで確定済み）が`rdbmsconnection/`
  （5.2 対象RDBMS接続情報管理）と`schema/`（5.2 スキーマ取込）を別パッケージとして明示し、
  `domain-entities.md`も「rdbmsconnectionドメイン」「schemaドメイン」の2ドメインに分けて
  記述していることと一致させる。
- **依存方向**: `schema → rdbmsconnection`の一方向のみ。`SchemaImportService`が
  `ConnectionPoolRegistry`を参照して対象RDBMSへの接続を取得する。`rdbmsconnection`パッケージ
  側は`schema`パッケージの何も参照しない。`SchemaTable.connectionId`は単なる`Long`型FKであり
  `RdbmsConnection`エンティティへの`@ManyToOne`参照ではないため、エンティティレベルでも
  依存が発生しない。したがって循環参照は生じない。
- **将来のU5/U6参照**: `ConnectionPoolRegistry`は将来的にU5（Master Data Maintenance）・
  U6（Query Builder）からも対象RDBMSへの`DataSource`/`NamedParameterJdbcTemplate`取得のために
  参照される見込みだが、`rdbmsconnection`パッケージから直接参照させる。U1の`DialectStrategy`
  （`common.dialect`パッケージ、複数ユニットが対等に実装を追加する拡張ポイント）とは事情が
  異なり、`ConnectionPoolRegistry`はU3が所有する単一実装のサービスをU5/U6が利用する関係で
  あるため、`common`への配置は行わない。

---

## 3. Performance Patterns

### 3.1 testConnectionの使い捨て接続タイムアウト実装（Question 3）

- `testConnection`専用に、`maximumPoolSize=1`の使い捨て`HikariDataSource`を都度生成する。
  `connectionTimeout`には`mm.app.rdbms-connection.pool.connection-timeout`と同じ設定値を
  適用し、プール本体（`ConnectionPoolRegistry`）と同一のタイムアウト値・同一の実現機構
  （HikariCP）を再利用する。
- 接続取得後（成功/失敗いずれも）は直ちに`close()`し、`ConnectionPoolRegistry`のキャッシュには
  登録しない（`business-rules.md` 1.4・1.6と整合）。
- `java.sql.DriverManager.setLoginTimeout(...)`（JVMグローバル状態）は用いない。並行して複数の
  `testConnection`が実行された場合の副作用を避け、プール側と実装を一貫させるため。

---

## 4. Reliability Patterns

### 4.1 importSchemaのメタデータ読み取り方式（Question 4）

- 標準JDBC APIの`java.sql.DatabaseMetaData`（`Connection.getMetaData()`で取得、全JDBCドライバが
  実装する標準インタフェース）の`getTables`/`getColumns`/`getPrimaryKeys`を用いる。DBMS固有の
  ドライバクラスは扱わない。
- `catalog`/`schema`パラメータの意味づけがDBMSにより異なる（MySQL/MariaDBは真のスキーマ概念を
  持たず`catalog`＝データベース名、PostgreSQL/H2は`catalog`固定＋複数の`schema`が存在しうる）
  ため、`DialectStrategy.getSchemaResolutionMode()`は次の2値を持つ`SchemaResolutionMode`
  （`common.dialect`パッケージ、新規定義）を返す:
  - **`CATALOG_AS_SCHEMA`**（MySQL/MariaDB）: `getTables(catalog = RdbmsConnection.databaseName,
    schema = null, ...)`で取得し、`SchemaTable.schemaName`には`RdbmsConnection.databaseName`を
    そのまま設定する（スキーマの列挙は行わない、常に1件）。
  - **`NATIVE_SCHEMA`**（PostgreSQL/H2）: まず`getSchemas(catalog, schemaPattern = null)`で
    接続先データベース内の全スキーマ名を列挙し、各スキーマごとに`getTables(catalog, schema =
    対象スキーマ名, ...)`を呼び出す。
- このメタデータ読み取り方式は、U1で未文書化だった`DialectStrategy.getSchemaResolutionMode()`
  の具体的な取得APIと列挙値を本ステージで新たに確定するもの（`business-rules.md` 2.1が参照する
  「`DialectStrategy.getSchemaResolutionMode()`の解釈」の中身に該当）。

### 4.2 importSchemaの`@Transactional`適用範囲（Question 4）

- `SchemaImportService.importSchema(connectionId)`メソッド全体に`@Transactional`を付与する。
- 対象RDBMSからのメタデータ読み取り（4.1、内部DBのトランザクションとは独立した接続）は
  実行してもロールバック対象にはならないが、副作用のない読み取り専用操作であるため問題ない。
- 内部DBへの全upsert操作（`SchemaTable`/`SchemaColumn`の作成・更新・stale化、
  `business-rules.md` 2.2）が単一トランザクション内で行われることが本質的な要件であり、
  メソッド全体に付与することで読み取り中に発生した例外もそのままロールバックのトリガーとして
  機能する。読み取り部分のみを別メソッドに切り出す分割は行わない（トランザクション境界の管理が
  複雑になるだけでメリットがないため）。

---

## 5. Scalability/Performance Patterns（インデックス）

### 5.1 SchemaTable/SchemaColumnのインデックス実装方針（Question 5）

- `SchemaTable`/`SchemaColumn`とも、一意制約（`@Table(uniqueConstraints = {...})`、
  `domain-entities.md`で確定済みの`(connectionId, schemaName, tableName)`・
  `(tableId, columnName)`）のみに委ねる。一意制約が複合インデックスを暗黙的に張るため、
  `listTables(connectionId, schema)`（`business-rules.md` 2.4）のような`connectionId`を
  含む検索も、`(connectionId, schemaName, tableName)`複合インデックスの先頭カラムとして
  利用できる。
- `@Table(indexes = {...})`による追加の明示的インデックス定義は不要（U2の`tokenHash`方針、
  `u2-auth-user-registration/nfr-design-patterns.md` 2.1と同じ考え方）。U1の`AuditLog`
  （`nfr-design-patterns.md` 4.1）は非unique列への複合インデックスが必要だった別ケースであり、
  本ユニットには該当しない。

---

## 6. Resilience Patterns

- 本ユニット固有の新規パターンはない（`u3-rdbms-connection-schema-import-nfr-design-plan.md`の
  ユニット適用可否判定を参照）。`resiliency-baseline`拡張は無効（`aidlc-state.md`）。
  `testConnection`/`importSchema`の失敗は`ConnectionTestResult`/`SchemaImportResult`の
  失敗フラグとして呼び出し元へそのまま返す設計（`nfr-requirements.md` 1.2、Question 6 = A）で
  あり、U1の`MailService`/`AuditLogService`のような「例外を握りつぶし主処理を継続する」障害
  分離パターンとは性質が異なる（対象RDBMS接続失敗はU3の主機能そのものの結果であり、副次的な
  失敗ではないため）。

---

## 7. PBT適用性（property-based-testing拡張）

- 本ステージ（NFR Design）ではPBT-09（フレームワーク選定）を含むいずれのPBTルールも対象外
  （`property-based-testing.md`のEnforcement Integration表: NFR Designは対象外ステージ）。
  U1/U2のNFR Design承認時と同様、N/Aとして扱う。