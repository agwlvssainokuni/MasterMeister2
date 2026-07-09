# tech-stack-decisions.md — U3: RDBMS Connection & Schema Import

`nfr-requirements.md`に基づく技術選定の決定事項一覧。

| # | 決定事項 | 選定 | 根拠 |
|---|---|---|---|
| 1 | 暗号化鍵の形式・鍵長・IV管理方式 | AES-256、Base64エンコード32バイト鍵（`mm.app.rdbms-connection.encryption-key`、fail-fast）、IVはGCM推奨12バイトを暗号化ごとに`SecureRandom`生成し暗号文先頭に付加 | Question 1 = A |
| 2 | 対象RDBMS動的コネクションプールの実装ライブラリ | HikariCP再利用（U1と同一API）。プールごとに`maximumPoolSize=5`/`minimumIdle=0`を既定値とし`mm.app.rdbms-connection.pool.*`で外だし | Question 2 = A |
| 3 | 対象RDBMS JDBCドライバ | `mysql-connector-j`/`mariadb-java-client`/`postgresql`を`runtimeOnly`、バージョンは`dependencyManagement`で明示管理。H2は既存依存を再利用 | Question 3 = A |
| 4 | 接続確立時のタイムアウト | 5秒（HikariCP `connectionTimeout`・`testConnection`共通）、`mm.app.rdbms-connection.pool.connection-timeout`で外だし | Question 4 = A |
| 5 | スキーマ取り込みの実行方式 | 同期HTTPリクエスト。非同期ジョブ化（ポーリング等）は本フェーズ未実装 | Question 5 = A |
| 6 | 接続テスト・取り込み失敗時のエラーメッセージ | JDBC例外の`getMessage()`をそのまま返す（管理者専用機能のため情報漏洩リスク該当なし） | Question 6 = A |
| 7 | PBT（Property-Based Testing）フレームワーク | `jqwik`（U1で確定済み、再選定なし） | U1 `tech-stack-decisions.md` #16を踏襲 |

---

## 設定キー一覧（本ユニットで新規追加）

| 設定キー | 既定値 | パターン | 用途 |
|---|---|---|---|
| `mm.app.rdbms-connection.encryption-key` | なし（必須） | fail-fast（`mm.app.jwt.secret`と同型） | `EncryptedStringConverter`の暗号化鍵（Base64、32バイト） |
| `mm.app.rdbms-connection.pool.maximum-pool-size` | `5` | 通常デフォルト | `ConnectionPoolRegistry`のHikariCP `maximumPoolSize` |
| `mm.app.rdbms-connection.pool.minimum-idle` | `0` | 通常デフォルト | `ConnectionPoolRegistry`のHikariCP `minimumIdle` |
| `mm.app.rdbms-connection.pool.connection-timeout` | `5秒` | 通常デフォルト | HikariCPの`connectionTimeout`および`testConnection`使い捨て接続の両方が参照 |

`encryption-key`のみfail-fast（未設定時は起動失敗）。プール関連3キーは通常のデフォルト値パターン
（未設定時は既定値を使用、起動失敗しない）——Question 2/4の回答で明示的に区別された方針。

---

## JDBCドライバ選定の補足（Question 3 = A）

- Spring Boot BOMは`mysql-connector-j`・`postgresql`のバージョンを提供するが、`mariadb-java-client`は
  提供しない。この非対称性により、BOM任せ（選択肢B）ではMariaDBドライバのみバージョン管理方針が
  異なることになるため、3ドライバとも一律`dependencyManagement`ブロックで明示バージョン管理する
  方針（CLAUDE.md「Gradleバージョン管理」規約）に統一した。
- いずれも`runtimeOnly`スコープ（コンパイル時には`NamedParameterJdbcTemplate`/`DriverManager`関連
  APIのみに依存し、各DB固有クラスをアプリケーションコードから直接参照しない）。