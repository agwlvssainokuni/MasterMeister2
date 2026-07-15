# business-logic-summary.md — U3: RDBMS Connection & Schema Import

Step 2（ビジネスロジック生成）・Step 3（ビジネスロジック単体テスト）で生成したクラス一覧と、
`business-logic-model.md`のP1〜P11、および本Code Generation計画で新たに識別したP12
（`SchemaQueryService`、Functional Design時点ではスコープ外）との対応関係。

## 生成クラス一覧（Step 2）

| パッケージ | クラス/インタフェース | 役割 |
|---|---|---|
| `common.dialect` | `DialectStrategy`（既存、ブラウンフィールド修正） | `buildJdbcUrl(host, port, databaseName)`を追加 |
| `common.dialect` | `MySqlDialectStrategy`/`MariaDbDialectStrategy`/`PostgreSqlDialectStrategy`/`H2DialectStrategy`（既存、ブラウンフィールド修正） | 各方言の`buildJdbcUrl`実装 |
| `common.dialect` | `SchemaResolutionMode`（既存、変更なし） | U1で実装済みのため本ユニットでは再定義しない |
| `rdbmsconnection` | `RdbmsConnection`（JPA entity） | 対象RDBMS接続情報エンティティ（`password`は`EncryptedStringConverter`で透過暗号化） |
| `rdbmsconnection` | `EncryptedStringConverter` | AES/GCM暗号化コンバータ（`@Value`鍵、`JwtTokenProvider`と同型のfail-fastパターン） |
| `rdbmsconnection` | `ConnectionConfig`, `ConnectionSummary`, `ConnectionDetail`, `ConnectionTestResult` | 接続情報DTO（record） |
| `rdbmsconnection` | `ConnectionPoolRegistry` | `connectionId`ごとのHikariCPプールのキャッシュ・遅延生成・破棄 |
| `rdbmsconnection` | `RdbmsConnectionService` | 接続情報のCRUD・接続テスト |
| `schema` | `SchemaTable`, `SchemaColumn`（JPA entity） | 取り込み済みメタデータエンティティ（`stale`フラグで論理除外） |
| `schema` | `TableType` | テーブル種別列挙型（`TABLE`, `VIEW`） |
| `schema` | `SchemaImportResult`, `TableMetadata`, `ColumnDetail`, `TableDetail` | スキーマ取り込み・参照DTO（record） |
| `schema` | `SchemaImportService` | 対象RDBMSからのメタデータ取り込み・upsert（`@Transactional`） |
| `schema` | `SchemaQueryService` | 取り込み済みメタデータの参照、`stale`除外 |

## 生成テストクラス一覧（Step 3）

| テストクラス | 検証方式 |
|---|---|
| `rdbmsconnection.EncryptedStringConverterTest` | jqwik `@Property`（POJO単体、Springコンテキスト不要、直接インスタンス化） |
| `rdbmsconnection.RdbmsConnectionServiceTest` | jqwik `@Property`（純Mockito、`RdbmsConnectionRepository`/`ConnectionPoolRegistry`をモック化） |
| `rdbmsconnection.ConnectionPoolRegistryTest` | jqwik `@Property`（純Mockito＋実HikariCP、埋め込みH2 TCPサーバに接続） |
| `schema.SchemaImportServiceTest` | jqwik `@Property`（P7〜P9・P11は手製フェイクリポジトリ＋実H2 TCPサーバ、P10のみ`@JqwikSpringSupport @DataJpaTest`＋`@TestConfiguration`経由のSpring管理Bean） |
| `schema.SchemaQueryServiceTest` | jqwik `@Property`（`@JqwikSpringSupport @DataJpaTest`、実`SchemaTableRepository`/`SchemaColumnRepository`） |

## P1〜P12対応表

| # | 対象 | 検証テストクラス | 状態 |
|---|---|---|---|
| P1 | `EncryptedStringConverter`のRound-trip（暗号化→復号で元の文字列に一致） | `EncryptedStringConverterTest` | 実装済み（Step 3） |
| P2 | `EncryptedStringConverter`のInvariant（暗号化後の値は平文と一致しない） | `EncryptedStringConverterTest` | 実装済み（Step 3） |
| P3 | `RdbmsConnectionService`のJDBC URL組み立てInvariant（`additionalParams`の重複付加なし） | `RdbmsConnectionServiceTest` | 実装済み（Step 3） |
| P4 | `ConnectionPoolRegistry.getDataSource`のIdempotence（`invalidate`まで同一インスタンス） | `ConnectionPoolRegistryTest` | 実装済み（Step 3） |
| P5 | `ConnectionPoolRegistry.invalidate`のInvariant（次回`getDataSource`は新インスタンス） | `ConnectionPoolRegistryTest` | 実装済み（Step 3） |
| P6 | `RdbmsConnectionService.testConnection`のInvariant（`ConnectionPoolRegistry`キャッシュ状態に無影響） | `RdbmsConnectionServiceTest` | 実装済み（Step 3） |
| P7 | `SchemaImportService.importSchema`の物理名マッチングInvariant（既存`id`の不変性） | `SchemaImportServiceTest` | 実装済み（Step 3） |
| P8 | `SchemaImportService.importSchema`のInvariant（削除された物理名は`stale = true`、行削除なし） | `SchemaImportServiceTest` | 実装済み（Step 3） |
| P9 | `SchemaImportService.importSchema`のIdempotence（対象RDBMS側無変更時の`stale = false`集合の一致） | `SchemaImportServiceTest` | 実装済み（Step 3） |
| P10 | `SchemaImportService.importSchema`失敗時のRound-trip（トランザクション原子性、内部DB状態の完全ロールバック） | `SchemaImportServiceTest`（`RollbackRoundTrip`グループ） | 実装済み（Step 3） |
| P11 | `SchemaImportService.importSchema`のInvariant（ビュー取り込み時、`primaryKeySequence`は常に`null`） | `SchemaImportServiceTest` | 実装済み（Step 3） |
| P12 | `SchemaQueryService`のstale除外Invariant（`listTables`/`getTableDetail`は`stale = true`を返さない） | `SchemaQueryServiceTest` | 実装済み（Step 3、Code Generation計画でP12として新規識別） |

**補足**: U3はU2と同様、P1〜P12すべてがStep 3で実装完了している。ただし`SchemaTableRepository`/
`SchemaColumnRepository`/`RdbmsConnectionRepository`の正式生成はStep 8（8-1〜8-3）の担当であり、
Step 3時点では3つとも一時的な未追跡スタブ（`src/main/java`配下、`git status`で`??`、実行時検証
専用、コミット対象外）として存在する。P10（`SchemaImportServiceTest`の`RollbackRoundTrip`
グループ）とP12（`SchemaQueryServiceTest`）は、いずれもこのスタブを`JpaRepository`拡張として
アップグレードした上で`@DataJpaTest`による実クエリ検証を行っている——手製フェイクでは
検証できない、Spring Data JPA派生クエリ自体の正しさ（`*StaleFalse`メソッド、`@Transactional`
ロールバック境界）が対象のため。

**既知の課題（Step 3スコープ外）**: `compileJava`単体は、Step 8まで未生成の
`RdbmsConnectionRepository`/`SchemaTableRepository`/`SchemaColumnRepository`
（一時スタブは`src/main/java`配下に存在するが未追跡・未コミットのためコミット時点の
ソースツリーには含まれない）の未解決参照により失敗し続ける、既知・意図された状態が
継続している。個々のテストクラスは`./gradlew test --tests "..."`で（スタブが存在する
開発時ローカル環境において）独立して実行・成功することを都度確認済み。この状態は
Step 8完了まで解消しない。

---

## 2026-07-15変更要求（接続コンテキストのグローバル化）による追加

| パッケージ | クラス | 役割 |
|---|---|---|
| `rdbmsconnection` | `ConnectionAccessService` | `listAccessibleConnections(userId)`。`masterdata`/`querybuilder`が個別に重複実装していた「アクセス可能な接続一覧」ロジックを一本化（`business-rules.md` 1.7） |

| テストクラス | 検証方式 |
|---|---|
| `rdbmsconnection.ConnectionAccessServiceTest` | jqwik `@Property`（`@JqwikSpringSupport @DataJpaTest`、実`RdbmsConnectionRepository`＋モック化`EffectivePermissionResolver`） |

P13（新規、`business-logic-model.md`フロー6）: `ConnectionAccessService.listAccessibleConnections`
のInvariant（返される接続は必ず`listAccessibleSchemas`が空でない接続のみ）を
`ConnectionAccessServiceTest`で検証済み。既存のP12（本ドキュメント上表、`SchemaQueryService`の
stale除外Invariant、Code Generation時に新規識別）との番号衝突を避けるためP13を採番した。