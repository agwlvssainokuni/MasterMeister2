# NFR Design Plan — U3: RDBMS Connection & Schema Import

## ユニット適用可否の判定

`construction/nfr-design.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`）に基づき、
U3は **実行（EXECUTE）** と判定。

- U3のNFR Requirementsは、暗号化鍵の形式・IV管理方式、`ConnectionPoolRegistry`のHikariCP再利用・
  プールサイズ、JDBCドライバ選定、接続タイムアウト、スキーマ取り込みの同期実行、エラーメッセージ
  露出方針という具体的な非機能決定を含んでおり、これらを設計パターン・論理コンポーネントへ
  落とし込む必要がある。

- **Resilience Patterns**: `resiliency-baseline`拡張は無効（`aidlc-state.md`）。`testConnection`/
  `importSchema`の失敗は`ConnectionTestResult`/`SchemaImportResult`の失敗フラグとして呼び出し元へ
  そのまま返す設計（`nfr-requirements.md` 1.2、Question 6 = A）であり、U1の`MailService`/
  `AuditLogService`のような「例外を握りつぶし主処理を継続する」障害分離パターンとは性質が異なる
  （対象RDBMS接続失敗はU3の主機能そのものの結果であり、副次的な失敗ではないため）。本ユニット
  固有の新規Resilience Patternは設けない（個別質問は設けない）。

→ 上記の中から5問を構成する。

---

## Step 1: NFR Requirements成果物の分析

- [x] `nfr-requirements.md`（Security/Tech Stack/Performance-Reliability/PBT）確認
- [x] `tech-stack-decisions.md`（7件の決定事項、新規設定キー一覧、JDBCドライバ選定補足）確認
- [x] U1 `nfr-design-patterns.md`（`DialectStrategy`のStrategyパターン、障害分離パターン、
      監査ログインデックス実装パターン、設定配置パターン）を前提として参照し、重複質問を避ける
- [x] U2 `nfr-design-patterns.md`（`OpaqueTokenGenerator`のパッケージ配置判断、トークン
      インデックス実装パターン）を前提として参照し、類似論点の判断基準を揃える

---

## 成果物生成タスク（`construction/nfr-design.md` Step 6）

- [ ] `aidlc-docs/construction/u3-rdbms-connection-schema-import/nfr-design/nfr-design-patterns.md`
- [ ] `aidlc-docs/construction/u3-rdbms-connection-schema-import/nfr-design/logical-components.md`

---

## Question 1: Scalability — ConnectionPoolRegistryのキャッシュ実装（スレッドセーフ性）

`business-rules.md` 1.5・`business-logic-model.md`フロー3で`ConnectionPoolRegistry`は
`connectionId`ごとにプールを遅延生成・キャッシュすると決定済みだが、複数の管理者・複数の
バックグラウンド処理から並行アクセスされうるため、キャッシュのデータ構造・排他制御方式が
未確定。

A. `ConcurrentHashMap<Long, HikariDataSource>`を用い、`computeIfAbsent`で遅延生成する
   （生成処理自体は`ConcurrentHashMap`が同一キーに対して排他的に実行することを保証するため、
   同一`connectionId`に対して二重にプールが生成される競合を防げる）。`invalidate(connectionId)`は
   `remove`＋取得済み`HikariDataSource`の`close()`を行う（推奨。追加の同期プリミティブを
   自前実装する必要がなく、JDK標準APIの保証のみで正しさを担保できる）
B. `HashMap`＋明示的な`synchronized`ブロックで排他制御する
C. その他（具体的な実装方式を指定）

[Answer]: A

---

## Question 2: Logical Components — 主要コンポーネントのパッケージ配置

`EncryptedStringConverter`・`ConnectionPoolRegistry`・`SchemaImportService`の配置パッケージを
確認したい。`ConnectionPoolRegistry`は将来的にU5（Master Data Maintenance）・U6（Query Builder）
からも対象RDBMSへの`DataSource`/`NamedParameterJdbcTemplate`取得のために参照される見込み
（`business-logic-model.md`フロー3、4項）だが、U1の`DialectStrategy`が`common.dialect`に
置かれている（複数ユニットが対等に実装を追加する拡張ポイントであるため）のとは事情が異なる。

A. `EncryptedStringConverter`・`ConnectionPoolRegistry`・`SchemaImportService`はいずれも
   `cherry.mastermeister.rdbmsconnection`パッケージに配置する（`RdbmsConnection`エンティティの
   ライフサイクルを所有するパッケージに、それに付随する暗号化・プール管理・スキーマ取り込みの
   道具も集約する）。U5/U6は`ConnectionPoolRegistry`を単一実装のサービスとしてこのパッケージから
   参照する。`DialectStrategy`のように複数ユニットが実装を追加する拡張ポイントではなく、U3が
   所有する単一のサービスをU5/U6が利用する関係であるため、`common`への配置は行わない（推奨。
   U2の`AdminBootstrapRunner`が「エンティティのライフサイクルを所有するパッケージに置く」と
   判断した基準と一貫する）
B. `common`パッケージに配置する（複数ユニットから参照されるため）
C. その他（具体的な配置方針を指定）

[Answer]: A

---

## Question 3: Performance — testConnectionの使い捨て接続におけるタイムアウト実装方式

`nfr-requirements.md` 3.1で`testConnection`（使い捨て接続）とプール初回接続確立の両方に
5秒のタイムアウトを適用すると決定済み。プール側はHikariCPの`connectionTimeout`プロパティで
実現できるが、プールを経由しない`testConnection`（`business-rules.md` 1.6）でどう5秒を強制
するかが未確定。

A. `testConnection`専用に、`maximumPoolSize=1`の使い捨て`HikariDataSource`を都度生成し、
   `connectionTimeout`に`mm.app.rdbms-connection.pool.connection-timeout`と同じ設定値を
   適用する。接続取得後（成功/失敗いずれも）は直ちに`close()`し、`ConnectionPoolRegistry`の
   キャッシュには登録しない（`business-rules.md` 1.6と整合）。プール本体と同一のHikariCPの
   タイムアウト機構を再利用できるため、`java.sql.DriverManager`のグローバル状態
   （`setLoginTimeout`）を変更する方式より実装が一貫し、並行実行時の副作用もない（推奨）
B. `java.sql.DriverManager.setLoginTimeout(5)`をJVMグローバルに設定してから
   `DriverManager.getConnection(...)`を呼び出す
C. その他（具体的な実装方式を指定）

[Answer]: A

---

## Question 4: Reliability — importSchemaの`@Transactional`適用範囲

`nfr-requirements.md` 3.2・`business-rules.md` 2.3で`importSchema`全体を単一トランザクション・
全ロールバックとすると決定済み。対象RDBMSからのメタデータ読み取り（JDBC、内部DBのJPA
トランザクションとは無関係）と内部DBへのupsert（JPA、トランザクショナル）が1つのメソッド内に
混在するため、`@Transactional`の適用範囲を確認したい。

A. `SchemaImportService.importSchema(connectionId)`メソッド全体に`@Transactional`を付与する。
   対象RDBMSからのメタデータ読み取り（JDBC経由、内部DBのトランザクションとは独立した接続）は
   実行してもロールバック対象にはならないが、副作用のない読み取り専用操作であるため問題ない。
   内部DBへの全upsert操作（`SchemaTable`/`SchemaColumn`の作成・更新・stale化）が単一トランザクション
   内で行われることが本質的な要件であり、メソッド全体に付与することで読み取り中に発生した例外も
   そのままロールバックのトリガーとして機能する（推奨。分割するとトランザクション境界の管理が
   複雑になるだけでメリットがない）
B. 内部DB更新部分のみを別メソッドに切り出し、そこにのみ`@Transactional`を付与する
   （メタデータ読み取りはトランザクション外で行う）
C. その他（具体的な適用範囲を指定）

[Answer]: A

---

## Question 5: Scalability/Performance — SchemaTable/SchemaColumnのインデックス実装方針

`domain-entities.md`で`SchemaTable`は`(connectionId, schemaName, tableName)`、`SchemaColumn`は
`(tableId, columnName)`の一意制約を持つと決定済み。U1の`nfr-design-patterns.md` 4.1
（`AuditLog`への`@Table(indexes = {...})`明示的インデックス定義）・U2の`nfr-design-patterns.md`
2.1（`tokenHash`はunique制約のみで賄う判断）との整合を確認したい。

A. `SchemaTable`/`SchemaColumn`とも、一意制約（`@Table(uniqueConstraints = {...})`）のみに委ねる。
   一意制約が複合インデックスを暗黙的に張るため、`listTables(connectionId, schema)`
   （`business-rules.md` 2.4）のような`connectionId`を含む検索も、`(connectionId, schemaName,
   tableName)`複合インデックスの先頭カラムとして利用できる。`@Table(indexes = {...})`による
   追加の明示的インデックス定義は不要（U2の`tokenHash`方針と同じ考え方。`AuditLog`は非unique列
   への複合インデックスが必要だった別ケース）（推奨）
B. 一意制約に加え、`connectionId`単独への`@Table(indexes = {...})`を明示的に追加する
C. その他（具体的な実装方針を指定）

[Answer]: A

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。