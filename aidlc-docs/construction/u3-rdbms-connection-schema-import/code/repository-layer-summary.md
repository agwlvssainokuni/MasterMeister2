# U3 RDBMS Connection & Schema Import - リポジトリレイヤサマリ

Step 8（リポジトリレイヤ生成）・Step 9（リポジトリレイヤ単体テスト）で生成した3リポジトリの一覧。

## `RdbmsConnectionRepository`（`cherry.mastermeister.rdbmsconnection`）

`JpaRepository<RdbmsConnection, Long>` を継承。カスタムクエリメソッドは持たない。標準の
`save`/`saveAll`/`findById`/`findAll`/`delete`/`deleteAll`等のみで、`RdbmsConnectionService`の
全操作（接続設定のCRUD、接続テスト、スキーマインポートのトリガー）を満たす。`password`列は
`EncryptedStringConverter`（`@Convert`）により、エンティティの読み書きの都度、透過的に暗号化・
復号される。

## `SchemaTableRepository`（`cherry.mastermeister.schema`）

`JpaRepository<SchemaTable, Long>` を継承。

| メソッド | 説明 |
|---|---|
| `Optional<SchemaTable> findByConnectionIdAndSchemaNameAndTableName(Long connectionId, String schemaName, String tableName)` | `SchemaImportService.importSchema`が、対象RDBMSから取得したテーブル1件ごとに既存レコード（stale含む）の有無を判定するために使用（P7/P8/P9のUNCHANGED/ADDED/DELETED判定の起点） |
| `Optional<SchemaTable> findByConnectionIdAndSchemaNameAndTableNameAndStaleFalse(Long connectionId, String schemaName, String tableName)` | `SchemaQueryService.getTableDetail`が、非stale（最新インポート済み）の単一テーブル詳細を取得するために使用 |
| `List<SchemaTable> findByConnectionId(Long connectionId)` | `SchemaImportService.importSchema`が、インポート対象RDBMS配下の既存全レコード（stale含む）を洗い出し、インポート結果に現れなかったテーブルをstale化するために使用 |
| `List<SchemaTable> findByConnectionIdAndStaleFalse(Long connectionId)` | `SchemaQueryService.listSchemas`が、非staleテーブルからスキーマ名の重複排除一覧を（インメモリで）導出するために使用 |
| `List<SchemaTable> findByConnectionIdAndSchemaNameAndStaleFalse(Long connectionId, String schemaName)` | `SchemaQueryService.listTables`が、指定スキーマ配下の非staleテーブル一覧を取得するために使用 |

## `SchemaColumnRepository`（`cherry.mastermeister.schema`）

`JpaRepository<SchemaColumn, Long>` を継承。

| メソッド | 説明 |
|---|---|
| `List<SchemaColumn> findByTableIdAndStaleFalse(Long tableId)` | `SchemaQueryService.getTableDetail`が、非staleカラム一覧を取得し`primaryKeySequence`昇順（`Comparator.nullsLast`）で並べ替えて返すために使用（P12: stale除外の検証対象） |
| `Optional<SchemaColumn> findByTableIdAndColumnName(Long tableId, String columnName)` | `SchemaImportService.importColumns`が、対象テーブルのカラム1件ごとに既存レコード（stale含む）の有無を判定するために使用 |
| `List<SchemaColumn> findByTableId(Long tableId)` | `SchemaImportService.importColumns`が、インポート対象テーブル配下の既存全カラム（stale含む）を洗い出し、インポート結果に現れなかったカラムをstale化するために使用 |

## インデックス設計

`nfr-design-patterns.md` 5.1の設計判断に基づき、`SchemaTable`/`SchemaColumn`いずれも自然キー
（重複を許さない検索キー）を表す列の組に`@Table(uniqueConstraints = {...})`で複合unique制約を
付与するのみとし、`@Table(indexes = {...})`による非unique列への明示的な追加インデックスは
定義しない。

- `SchemaTable`: `@UniqueConstraint(columnNames = {"connectionId", "schemaName", "tableName"})`
  — `findByConnectionIdAndSchemaNameAndTableName`系メソッドの検索性能はこのunique制約により
  暗黙的に張られるインデックスで十分
- `SchemaColumn`: `@UniqueConstraint(columnNames = {"tableId", "columnName"})`
  — `findByTableIdAndColumnName`の検索性能も同様

`findByConnectionId`/`findByConnectionIdAndStaleFalse`/`findByConnectionIdAndSchemaNameAndStaleFalse`
（`SchemaTable`）や`findByTableIdAndStaleFalse`/`findByTableId`（`SchemaColumn`）は、上記unique
制約の先頭列（`connectionId`/`tableId`）を条件に含むため、複合インデックスの先頭列プレフィックス
一致で引ける。`stale`列単体・`stale`列を含む複合インデックスは追加しない（U1の`AuditLog`のような
非unique列への明示的インデックスに該当するケースがU3には存在しないため）。

`RdbmsConnection`はカスタムクエリメソッドを持たず、`id`（主キー）以外に検索キーとなる列も
存在しないため、追加インデックス・unique制約のいずれも不要。

## テストカバレッジ（Step 9）

| テストクラス | 検証内容 |
|---|---|
| `RdbmsConnectionRepositoryTest` | 基本CRUD（`saveAssignsGeneratedId`、`deleteRemovesEntity`、`findAllReturnsAllSavedConnections`）に加え、`savedPasswordRoundTripsThroughEncryptedStringConverter`（`password`列が`EncryptedStringConverter`経由で暗号化保存・復号読み出しされ、往復後に元の平文と一致することを検証）（4件） |
| `SchemaTableRepositoryTest` | 基本CRUD、5件のクエリメソッド全て（一致/不一致、stale除外込み）、`(connectionId, schemaName, tableName)`のunique制約違反時に`DataIntegrityViolationException`が発生することを検証（8件） |
| `SchemaColumnRepositoryTest` | 基本CRUD、3件のクエリメソッド全て（一致/不一致、stale除外込み）、`(tableId, columnName)`のunique制約違反時に`DataIntegrityViolationException`が発生することを検証（7件） |

いずれも`@DataJpaTest`＋組み込みH2で実行。P1〜P12（業務ロジックの性質）はリポジトリ層では
再検証せず、`business-logic-summary.md`記載のjqwik `@Property`テストに一元化している。ただし
P12（stale除外）はリポジトリの`*StaleFalse`系派生クエリ自体の正しさを検証する性質のため、
Step 3の`SchemaQueryServiceTest`（`@DataJpaTest`、実リポジトリ使用）で担保済みであり、本レイヤの
example-basedテストと役割が重複しない。

作業中に、`RdbmsConnection`エンティティの`EncryptedStringConverter`（`mm.app.rdbms-connection.
encryption-key`をコンストラクタ注入で要求）が`@DataJpaTest`のアプリ全体エンティティスキャンに
巻き込まれ、U1/U2の既存4つの`@DataJpaTest`クラスまで巻き添えで壊れる回帰が発覚。
`src/test/resources/application-test.yml`（テスト専用暗号化キー）＋`spring.profiles.active=test`
（`backend/build.gradle.kts`）による恒久修正を行い、Step 3で個別に追加していた
`SchemaImportServiceTest`/`SchemaQueryServiceTest`の重複`@TestPropertySource`も本レイヤ完了後に
削除済み。詳細は`u3-rdbms-connection-schema-import-code-generation-plan.md` Step 9の実装ノートを
参照。