# NFR Requirements Plan — U3: RDBMS Connection & Schema Import

## ユニット適用可否の判定

`construction/nfr-requirements.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`）に
基づき、U3は **実行（EXECUTE）** と判定。

- **Security Requirements**: `EncryptedStringConverter`（AES/GCM、Functional Design Question 1）の
  鍵長・エンコーディング・IV管理方式が未確定。`testConnection`/`importSchema`失敗時に生の
  JDBC例外メッセージをどこまで露出するかも未確定。
- **Tech Stack Selection**: `ConnectionPoolRegistry`（対象RDBMSごとに動的生成する複数プール、
  U1の内部DB単一プールとは性質が異なる）の実装ライブラリ・プールサイズ方針が未確定。
  MySQL/MariaDB/PostgreSQL用のJDBCドライバ依存関係も未確定（H2は内部DBで既導入だが対象RDBMS
  としての動作確認済みかは別途確認要）。
- **Performance/Reliability**: `testConnection`・プール初回接続確立時のタイムアウト設定が
  未確定。`importSchema`（`business-rules.md` 2.3で単一トランザクション・全ロールバックと決定
  済み）を同期HTTPリクエストとして扱うか、大規模スキーマでのタイムアウトリスクをどう扱うかが
  未確定。
- **Availability/Usability**: 本ユニット固有の新規論点は見当たらない（U1のNFR Requirements・
  Functional Designでカバー済み。個別質問は設けない）。

→ 上記の中から6問を構成する。

---

## Step 1: Functional Design成果物の分析

- [x] `domain-entities.md`（`RdbmsConnection`、`EncryptedStringConverter`、`RdbmsType`、
      `SchemaTable`/`SchemaColumn`、`TableType`）確認
- [x] `business-rules.md`（接続管理1.1-1.6、スキーマ取り込み2.1-2.4、設定キー、API認可）確認
- [x] `business-logic-model.md`（フロー1-5、Testable Properties P1-P11）確認
- [x] `frontend-components.md`（`rdbmsConnection/`・`schema/`のコンポーネント構成、ルーティング）確認
- [x] U1 `nfr-requirements.md`/`tech-stack-decisions.md`（`DialectStrategy`、H2ファイルモード＋
      HikariCP既定設定、`mm.app.jwt.secret`と同型のfail-fast設定パターン）を前提として参照し、
      重複質問を避ける

---

## 成果物生成タスク（`construction/nfr-requirements.md` Step 6）

- [x] `aidlc-docs/construction/u3-rdbms-connection-schema-import/nfr-requirements/nfr-requirements.md`
- [x] `aidlc-docs/construction/u3-rdbms-connection-schema-import/nfr-requirements/tech-stack-decisions.md`

---

## Question 1: Security — 暗号化鍵の形式・鍵長・IV管理方式

`business-rules.md` 1.1/3節でAES/GCM暗号化・`mm.app.rdbms-connection.encryption-key`設定キー
（fail-fastパターン）は決定済みだが、鍵長・エンコーディング・IVの管理方式が未確定。

A. AES-256（256ビット鍵）を使用する。設定値はBase64エンコードされた32バイト鍵として
   `mm.app.rdbms-connection.encryption-key`に設定する。IVはGCM推奨の12バイトを暗号化ごとに
   `SecureRandom`で生成し、暗号文の先頭に付加して1つの文字列として保存する（復号時に
   先頭12バイトをIVとして取り出す。追加のIV管理テーブルは持たない）（推奨。業界標準的な
   鍵長であり、IVを暗号文と同一カラムに同梱することで実装・運用がシンプルになる）
B. AES-128（128ビット鍵）を使用する
C. その他（具体的な鍵長・IV管理方式を指定）

[Answer]: A

---

## Question 2: Tech Stack — 対象RDBMS動的コネクションプールの実装方式

`business-rules.md` 1.5で`ConnectionPoolRegistry`の遅延初期化・`invalidate`によるプール破棄は
決定済みだが、実装ライブラリとプールサイズ方針が未確定。U1で内部DB用に採用したHikariCPを
対象RDBMS用の動的プール（`connectionId`ごとに複数プールをキャッシュに保持）にも再利用するか
確認したい。

A. HikariCPを再利用する。プールごとに小さめのサイズ（`maximumPoolSize=5`、
   `minimumIdle=0`）を既定値とし、管理者向け内部ツールとしての想定同時利用数（少数の管理者・
   少数の対象RDBMS）を踏まえて、明示的なチューニングが必要になった時点で見直す
   （推奨。U1で既に依存関係・設定パターンが確立しており、動的生成にも同一APIで対応できる）。
   プールサイズは`application.yml`の設定キー（`mm.app.rdbms-connection.pool.maximum-pool-size`
   〈既定値5〉、`mm.app.rdbms-connection.pool.minimum-idle`〈既定値0〉）として外だしし、
   未設定時は既定値を使う通常のデフォルト値パターン（`mm.app.jwt.secret`のfail-fastパターンとは
   異なり、必須設定ではない）とする
B. 対象RDBMS用は独立した別のプールライブラリ（Apache DBCP2等）を採用する
C. プールを使わずリクエストごとに`DriverManager`相当の使い捨て接続を都度生成する（CLAUDE.mdが
   `DriverManager.getConnection()`直接利用を禁止しているため不採用対象だが選択肢として提示）
D. その他（具体的な方式を指定）

[Answer]: A

---

## Question 3: Tech Stack — 対象RDBMS JDBCドライバの選定

MySQL/MariaDB/PostgreSQLへの接続には各DB用のJDBCドライバ依存関係が必要（H2は内部DBの依存関係を
対象RDBMS接続にも再利用可能）。バージョン選定方針を確認したい。

A. 各DBの公式最新安定版ドライバを`runtimeOnly`スコープで追加する：
   `com.mysql:mysql-connector-j`（MySQL）、`org.mariadb.jdbc:mariadb-java-client`
   （MariaDB）、`org.postgresql:postgresql`（PostgreSQL）。バージョンは`dependencyManagement`
   ブロックで明示管理する（CLAUDE.md「Gradleバージョン管理」規約）。H2は既存の`com.h2database:h2`
   依存を対象RDBMS接続にも再利用する（推奨）
B. Spring Boot BOMが提供するバージョンにすべて委ねる（個別バージョン指定なし）
C. その他（具体的なドライバ・バージョン方針を指定）

[Answer]: A

---

## Question 4: Performance/Reliability — 接続確立時のタイムアウト設定

`testConnection`（`business-rules.md` 1.4、使い捨て接続）およびプール初回接続確立
（`ConnectionPoolRegistry`、1.5）の両方で、対象RDBMSが到達不能な場合に長時間ブロックしない
ための接続タイムアウト値を確認したい。

A. 接続タイムアウトを5秒に設定する（HikariCPの`connectionTimeout`、および`testConnection`の
   使い捨て接続にも同一値を適用）。管理者操作（接続テスト・保存）が長時間フリーズすることを
   避ける（推奨。U1のメール送信タイムアウト方針〈5秒程度〉と一貫する）。Q2の設定キー体系に
   合わせて`mm.app.rdbms-connection.pool.connection-timeout`（既定値5秒）として外だしし、
   `testConnection`の使い捨て接続にも同じ設定値を参照させる
B. より長いタイムアウト（30秒等）を明示的に指定する
C. その他（具体的な秒数を指定）

[Answer]: A

---

## Question 5: Reliability — スキーマ取り込みの同期実行方針

`business-rules.md` 2.3で`importSchema`全体を単一トランザクションとして扱うことは決定済みだが、
HTTPリクエストとして同期実行するか、大規模スキーマでのタイムアウトリスクをどう扱うかが未確定。

A. 同期HTTPリクエストとして実装する（`SchemaImportPanel`はリクエスト完了までローディング表示
   のまま待機、フロントエンド側に明示的なタイムアウト延長設定は設けない）。非同期ジョブ化
   （ポーリング・WebSocket通知等）は本フェーズでは実装しない。想定利用規模（内部マスタデータ
   管理システム、管理者が手動起動する低頻度操作）を踏まえ、同期処理で十分と判断する
   （推奨。U1/U2の「小規模内部利用が前提」という判断方針と一貫する）
B. 非同期ジョブとして実装し、`SchemaImportResult`は「実行中」状態を経てポーリングで結果を
   取得する方式にする
C. その他（具体的な方式を指定）

[Answer]: A

---

## Question 6: Security — 接続テスト・取り込み失敗時のエラーメッセージ露出方針

`testConnection`（フロー2）・`importSchema`（フロー4）が失敗した場合、`ConnectionTestResult`/
`SchemaImportResult`に含める「簡潔なエラー概要」（`business-logic-model.md`）の内容方針が
未確定。生のJDBC例外メッセージ（ホスト名・DB名・ドライバ固有のエラーコード等を含みうる）を
どこまで返すか確認したい。

A. JDBC例外の`getMessage()`をそのまま（またはほぼそのまま）`ConnectionTestResult`/
   `SchemaImportResult`のエラー概要として返す。本ユニットの全機能は管理者専用
   （`business-rules.md` 4節、`hasRole("ADMIN")`）であり、接続設定のトラブルシューティングには
   具体的なドライバエラー内容が有用なため、一般ユーザ向けの情報漏洩リスクは該当しない
   （推奨）
B. 例外メッセージをサニタイズし、汎用的なエラー分類（「認証失敗」「ホスト到達不能」等）のみを
   返す。詳細はアプリケーションログにのみ出力する
C. その他（具体的な方針を指定）

[Answer]: A

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。