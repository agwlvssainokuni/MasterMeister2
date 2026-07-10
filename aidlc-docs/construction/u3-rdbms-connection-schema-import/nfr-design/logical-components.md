# logical-components.md — U3: RDBMS Connection & Schema Import

`nfr-design-patterns.md`に基づく論理コンポーネント一覧。

---

## 1. RDBMS Connection（`cherry.mastermeister.rdbmsconnection`）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `RdbmsConnection` | JPA Entity | 対象RDBMS接続情報（`name`, `rdbmsType`, `host`, `port`, `databaseName`, `username`, `password`〈`EncryptedStringConverter`適用〉, `additionalParams`, `createdAt`, `updatedAt`）。`domain-entities.md`参照 |
| `EncryptedStringConverter` | `@Component` + `@Converter`（`AttributeConverter<String, String>`） | `RdbmsConnection.password`のAES/GCM暗号化・復号。コンストラクタで`mm.app.rdbms-connection.encryption-key`をfail-fast検証（`JwtTokenProvider`と同型パターン） |
| `ConnectionPoolRegistry` | `@Component`（シングルトンスコープ必須） | `connectionId`ごとのHikariCP `DataSource`を`ConcurrentHashMap`でキャッシュ・遅延生成（`computeIfAbsent`）。`getDataSource(connectionId)`/`getJdbcTemplate(connectionId)`/`invalidate(connectionId)`を提供。U5/U6からも直接参照される想定 |
| `RdbmsConnectionService` | Service | `createConnection`/`updateConnection`/`listConnections`/`getConnection`/`testConnection`（`business-rules.md` 1節）。`testConnection`は使い捨て`HikariDataSource`（`maximumPoolSize=1`）を都度生成・即`close()`し、`ConnectionPoolRegistry`には登録しない |

---

## 2. Schema（`cherry.mastermeister.schema`）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `SchemaTable` | JPA Entity | 取り込み済みテーブル/ビューのメタデータ。一意制約`(connectionId, schemaName, tableName)`のみでインデックスを賄う（明示的`@Table(indexes)`なし） |
| `SchemaColumn` | JPA Entity | 取り込み済みカラムのメタデータ。一意制約`(tableId, columnName)`のみでインデックスを賄う |
| `SchemaImportService` | Service（`@Transactional`はメソッド全体に付与） | `importSchema(connectionId)`。`ConnectionPoolRegistry`（`rdbmsconnection`パッケージ）経由で対象RDBMSへの接続を取得し、`java.sql.DatabaseMetaData`（`getTables`/`getColumns`/`getPrimaryKeys`）でメタデータを読み取り、`SchemaTable`/`SchemaColumn`へupsert（`business-rules.md` 2.2）。読み取り・upsertとも同一トランザクション境界内 |
| `SchemaQueryService` | Service | `listSchemas`/`listTables`/`getTableDetail`（取り込み済みメタデータのみ、レコードデータは扱わない。既定で`stale = true`を除外） |

**依存方向**: `schema → rdbmsconnection`の一方向のみ（`SchemaImportService` →
`ConnectionPoolRegistry`）。`rdbmsconnection`パッケージ側は`schema`の何も参照せず、循環参照は
生じない（`nfr-design-patterns.md` 2.1参照）。

---

## 3. Common（`cherry.mastermeister.common.dialect`、U1既存・本ユニットで拡張）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `SchemaResolutionMode`（新規定義） | enum | `CATALOG_AS_SCHEMA`（MySQL/MariaDB）、`NATIVE_SCHEMA`（PostgreSQL/H2）の2値。`DialectStrategy.getSchemaResolutionMode()`の戻り値型 |
| `DialectStrategy`（U1既存、`getSchemaResolutionMode()`の戻り値をQuestion 4で確定） | インタフェース | 各`RdbmsType`実装が`SchemaResolutionMode`を返す。`SchemaImportService`がメタデータ取得ロジックの分岐に利用 |

---

## 4. Frontend（`features/rdbmsConnection/`, `features/schema/`）

`u3-rdbms-connection-schema-import/functional-design/frontend-components.md`で確定済みの
コンポーネント構成をそのまま踏襲する（本ステージでの追加変更なし）。

---

## 5. 設定ファイル

| ファイル | 内容 |
|---|---|
| `application.yml` | `mm.app.rdbms-connection.encryption-key`（必須・fail-fast）、`mm.app.rdbms-connection.pool.maximum-pool-size`（既定`5`）、`mm.app.rdbms-connection.pool.minimum-idle`（既定`0`）、`mm.app.rdbms-connection.pool.connection-timeout`（既定`5`秒、`ConnectionPoolRegistry`と`testConnection`使い捨て接続の両方が参照） |
| `build.gradle.kts` | `mysql-connector-j`/`mariadb-java-client`/`postgresql`を`runtimeOnly`、バージョンは`dependencyManagement`で明示管理（`tech-stack-decisions.md` #3） |

---

## 6. U1/U3責務境界の再確認

- `DialectStrategy`インタフェース自体・`RdbmsType` enumはU1が所有し`common.dialect`パッケージに
  置かれたまま。本ユニットは`getSchemaResolutionMode()`の戻り値`SchemaResolutionMode`を新たに
  同パッケージへ追加し、各`RdbmsType`実装クラス（U1所有）にその実装を委ねる。
- `ConnectionPoolRegistry`はU1の内部DB単一プール（Spring Boot自動設定のHikariCP）とは別物の、
  U3が新設する対象RDBMS用の動的マルチプール管理コンポーネントであり、`common`ではなく
  `rdbmsconnection`パッケージに配置する（`nfr-design-patterns.md` 2.1参照。U5/U6は単一実装の
  サービスとして本パッケージから直接参照する）。