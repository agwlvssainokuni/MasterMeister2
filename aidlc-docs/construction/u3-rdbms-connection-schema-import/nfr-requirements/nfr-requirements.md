# nfr-requirements.md — U3: RDBMS Connection & Schema Import

`u3-rdbms-connection-schema-import-nfr-requirements-plan.md`（Question 1〜6）の回答に基づく非機能要件。

---

## 1. Security Requirements

### 1.1 暗号化鍵の形式・鍵長・IV管理方式（Question 1 = A）

- `RdbmsConnection.password`の暗号化にはAES-256（256ビット鍵）を使用する（`EncryptedStringConverter`、
  `business-rules.md` 1.1/3節）。
- 鍵は`mm.app.rdbms-connection.encryption-key`（fail-fastパターン、`JwtTokenProvider`と同型）に
  Base64エンコードされた32バイト鍵として設定する。
- IVはGCM推奨の12バイトを暗号化ごとに`SecureRandom`で生成し、暗号文の先頭に付加して1つの文字列
  として保存する（復号時に先頭12バイトをIVとして取り出す）。追加のIV管理テーブルは持たない。

### 1.2 接続テスト・取り込み失敗時のエラーメッセージ露出方針（Question 6 = A）

- JDBC例外の`getMessage()`をそのまま（またはほぼそのまま）`ConnectionTestResult`/
  `SchemaImportResult`のエラー概要として返す。
- 本ユニットの全機能は管理者専用（`business-rules.md` 4節、`hasRole("ADMIN")`）であり、接続設定の
  トラブルシューティングには具体的なドライバエラー内容が有用なため、一般ユーザ向けの情報漏洩リスク
  は該当しない。

---

## 2. Tech Stack Requirements

### 2.1 対象RDBMS動的コネクションプールの実装方式（Question 2 = A）

- `ConnectionPoolRegistry`（`business-rules.md` 1.5、`connectionId`ごとに複数プールをキャッシュに
  保持、`invalidate`によるプール破棄）は、U1で内部DB用に採用したHikariCPを再利用する。
- プールサイズは小さめの既定値（`maximumPoolSize=5`、`minimumIdle=0`）とし、管理者向け内部ツールと
  しての想定同時利用数（少数の管理者・少数の対象RDBMS）を踏まえて、明示的なチューニングが必要に
  なった時点で見直す。
- プールサイズは`application.yml`の設定キー（`mm.app.rdbms-connection.pool.maximum-pool-size`
  〈既定値5〉、`mm.app.rdbms-connection.pool.minimum-idle`〈既定値0〉）として外だしし、未設定時は
  既定値を使う通常のデフォルト値パターンとする（`mm.app.jwt.secret`のfail-fastパターンとは異なり、
  必須設定ではない）。

### 2.2 対象RDBMS JDBCドライバの選定（Question 3 = A）

- MySQL/MariaDB/PostgreSQLへの接続には各DB公式最新安定版のJDBCドライバを`runtimeOnly`スコープで
  追加する：`com.mysql:mysql-connector-j`（MySQL）、`org.mariadb.jdbc:mariadb-java-client`
  （MariaDB）、`org.postgresql:postgresql`（PostgreSQL）。
- バージョンは`dependencyManagement`ブロックで明示管理する（CLAUDE.md「Gradleバージョン管理」規約）。
  Spring Boot BOMは`mysql-connector-j`/`postgresql`のバージョンは提供するが`mariadb-java-client`は
  提供しないため、3ドライバとも一律`dependencyManagement`で明示管理し方針を統一する。
- H2は既存の`com.h2database:h2`依存を対象RDBMS接続にも再利用する。

---

## 3. Performance/Reliability Requirements

### 3.1 接続確立時のタイムアウト設定（Question 4 = A）

- `testConnection`（`business-rules.md` 1.4、使い捨て接続）およびプール初回接続確立
  （`ConnectionPoolRegistry`、1.5）の両方で、接続タイムアウトを5秒に設定する
  （U1のメール送信タイムアウト方針〈5秒程度〉と一貫する）。
- Q2の設定キー体系に合わせて`mm.app.rdbms-connection.pool.connection-timeout`（既定値5秒）として
  外だしし、HikariCPの`connectionTimeout`と`testConnection`の使い捨て接続の両方が同じ設定値を
  参照する。

### 3.2 スキーマ取り込みの同期実行方針（Question 5 = A）

- `importSchema`（`business-rules.md` 2.3、単一トランザクション・全ロールバック）は同期HTTP
  リクエストとして実装する。`SchemaImportPanel`はリクエスト完了までローディング表示のまま待機し、
  フロントエンド側に明示的なタイムアウト延長設定は設けない。
- 非同期ジョブ化（ポーリング・WebSocket通知等）は本フェーズでは実装しない。想定利用規模（内部
  マスタデータ管理システム、管理者が手動起動する低頻度操作）を踏まえ、同期処理で十分と判断する
  （U1/U2の「小規模内部利用が前提」という判断方針と一貫する）。

---

## 4. PBT Compliance（property-based-testing拡張）

- U1のNFR Requirementsで確定済みの`jqwik`採用（PBT-09）をそのまま踏襲する。本ユニットでの
  再確認・再質問は行わない。
- 他のPBTルール（PBT-01〜PBT-08, PBT-10）はCode Generation Planning/Code Generation/Build and Test
  ステージで適用される（`business-logic-model.md`のTestable Properties P1〜P11が対象）。