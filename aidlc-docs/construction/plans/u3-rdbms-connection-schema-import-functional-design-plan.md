# U3: RDBMS Connection & Schema Import — Functional Design Plan

## Step 1: ユニットコンテキスト分析

- **ユニット定義**（`unit-of-work.md`）: バックエンドパッケージ `rdbmsconnection`, `schema`。
  フロントエンド `features/rdbmsConnection/`, `features/schema/`。対応ストーリー
  MVP-7, MVP-8, ADM-3。責務: 対象RDBMS（MySQL/MariaDB/PostgreSQL/H2）への接続情報管理と、
  接続先からのスキーマ（テーブル/ビュー構造）取り込み・参照。
  主要コンポーネント: `RdbmsConnectionService`, `ConnectionPoolRegistry`,
  `SchemaImportService`, `SchemaQueryService`。
  U1（`common`, `audit`）に依存。
- **対応ストーリー**（`unit-of-work-story-map.md` / `stories.md`）:
  - MVP-7: 対象RDBMS接続情報の登録（種別・ホスト・ポート・DB名・認証情報等を入力し保存、
    複数接続登録可、登録・変更は監査記録）
  - MVP-8: スキーマ取り込み（指定接続に対する取り込み実行、テーブル/ビューの物理名・
    コメント・型・制約を内部DBへ取り込み、接続ごとに紐づけ保持、取り込み結果は監査記録）
  - ADM-3: 複数の対象RDBMS接続の管理（2つ目以降の接続追加、接続ごとに独立したスキーマ・
    権限設定、ある接続の権限設定が他の接続に影響しない）
- **既存の確定事項**（Application Design / U1から継承、再検討不要）:
  - `component-methods.md`に以下のメソッドシグネチャが定義済み:
    ```
    RdbmsConnectionService:
      Long createConnection(ConnectionConfig config)
      void updateConnection(Long connectionId, ConnectionConfig config)
      ConnectionTestResult testConnection(ConnectionConfig config)
      List<ConnectionSummary> listConnections()
      ConnectionDetail getConnection(Long connectionId)

    ConnectionPoolRegistry:
      DataSource getDataSource(Long connectionId)
      NamedParameterJdbcTemplate getJdbcTemplate(Long connectionId)
      void invalidate(Long connectionId)   // 接続設定変更時にプールを再構築

    SchemaImportService:
      SchemaImportResult importSchema(Long connectionId)
        // DialectStrategy を用いて対象RDBMSのメタデータを読み取り、内部DBへ保存する
        // 結果（成功/失敗、取り込んだテーブル数等）は audit.AuditLogService へも明示連携される

    SchemaQueryService:
      List<String> listSchemas(Long connectionId)
      List<TableMetadata> listTables(Long connectionId, String schema)
      TableDetail getTableDetail(Long connectionId, String schema, String table)
        // カラム一覧・型・コメント・主キー構成（複合主キー対応）を含む
    ```
  - `DialectStrategy` / `DialectStrategyFactory` / `RdbmsType`（`MYSQL`/`MARIADB`/`POSTGRESQL`/`H2`）は
    U1で確立済み（`common/dialect`）。識別子クォート、ページング句生成、NULLソート順に加え
    `SchemaResolutionMode`（DB種別ごとのスキーマ/カタログ解釈差異、例: MySQLはスキーマ＝DB名、
    PostgreSQLはカタログ内スキーマ概念あり、H2は設定依存）を既に持つ。U3はこれをそのまま
    再利用し、スキーマ/カタログ解釈の差異吸収ロジックを再定義しない。
  - JDBC接続プールライブラリの選定・追加（例: HikariCPの明示的依存追加）、および
    MySQL/MariaDB/PostgreSQL用JDBCドライバの`build.gradle.kts`への追加は、技術非依存の
    Functional Designでは扱わず、NFR Design/Code Generationで決定する（現状
    `build.gradle.kts`にはH2ドライバのみ存在し、他の対象RDBMS用ドライバは未追加）。
  - 接続情報の暗号化（パスワード等）について、プロジェクト内に既存の暗号化ユーティリティや
    JPA `AttributeConverter`は存在しない（`User.passwordHash`はbcryptの一方向ハッシュであり、
    再利用不可＝復号が必要な接続パスワードには適用できない）。したがってQ1で新規に方針を
    決定する。
  - U4（Permission Management）はU3の`schema`パッケージに依存し、テーブル/カラム単位の
    権限をスキーマ取り込み結果（テーブルID等）に紐づけて保持する想定（`unit-of-work.md`）。
    このため、スキーマ再取り込み時の既存レコードの扱い（ID安定性）はU4の権限データ整合性に
    直結する（Q6）。

## Step 2-4: 計画・質問

以下8問について回答をお願いします。各質問には推奨案（A）を用意していますが、
別の選択肢や自由記述でも構いません。

---

### Q1. Business Rules — 接続パスワードの暗号化方式

`RdbmsConnection`エンティティが保持する接続パスワードを内部DB（H2）にどう保存するか。
`User.passwordHash`と異なり、対象RDBMSへの再接続に平文が必要なため、一方向ハッシュは使えない。

- **A（推奨）**: 新規のJPA `AttributeConverter`（例: `EncryptedStringConverter`）を追加し、
  AES/GCMなどの対称鍵暗号でパスワードを暗号化して保存する。鍵は環境変数
  （例: `mm.app.rdbms-connection.encryption-key`）から供給し、未設定/不正な長さの場合は
  起動時にfail-fastする（`mm.app.jwt.secret`と同様のパターン）。復号は`ConnectionPoolRegistry`が
  `DataSource`構築時にのみ行う。
- **B**: 平文のままDBに保存する（実装は簡素になるが、内部DB漏洩時に対象RDBMSの認証情報が
  そのまま漏洩するリスクを負う）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q2. Domain Model — `RdbmsConnection`エンティティのフィールド構成

接続情報として何を保持するか。

- **A（推奨）**: `id`, `name`（管理用表示名、ADM-3の一覧表示に使用）, `rdbmsType`（`RdbmsType` enum、
  U1で確立済みのものを再利用）, `host`, `port`, `databaseName`, `username`, `password`（Q1で暗号化）,
  `createdAt`/`updatedAt`（`java.time.Instant`）。JDBC URLは保存せず、`rdbmsType`+`host`+`port`+
  `databaseName`から`DialectStrategy`側のロジックで組み立てる（生URL直接入力を許すと
  DB種別との不整合や意図しないJDBCオプション注入のリスクがあるため）。
- **B**: 上記に加え、接続ごとのプール詳細設定（最大プールサイズ等）もエンティティに含め、
  管理者が接続単位でチューニング可能にする。
- **C**: その他（自由記述）

[Answer]: A

---

### Q3. Business Rules — コネクションプールのライフサイクル管理

`ConnectionPoolRegistry`が保持するプールを、いつ作成・破棄するか。

- **A（推奨）**: 遅延初期化（lazy）。`getDataSource(connectionId)`/`getJdbcTemplate(connectionId)`が
  初回呼び出された時点でプールを作成し、以降はキャッシュを返す。`updateConnection`成功時・
  接続削除時は`invalidate(connectionId)`を呼び既存プールを破棄する（次回アクセス時に
  新しい設定で再作成、または削除済みなら例外）。アプリケーション起動時に全接続へ
  一括接続はしない（未使用の対象RDBMSが起動不能でもアプリ自体は起動できる）。
- **B**: 即時初期化（eager）。接続情報の登録・更新時に毎回プールを作成し、接続不可なら
  登録/更新自体を失敗させる。
- **C**: その他（自由記述）

[Answer]: A

---

### Q4. Business Rules — `testConnection`の実行パターン

`testConnection(ConnectionConfig config)`は接続IDではなく設定値を直接受け取るシグネチャで
既に確定している。この設計が想定する利用パターンを確認する。

- **A（推奨）**: 2パターンに対応する。(1) 新規登録フォームで保存前に「接続テスト」ボタンから
  未保存の`ConnectionConfig`をそのまま渡してテストする（`ConnectionPoolRegistry`には登録しない
  一時的な単発接続で検証、成功/失敗と簡潔なエラー概要を返す）。(2) 既存接続の再テストは、
  `getConnection`で復号済み設定を取得し、それを`testConnection`に渡す（`RdbmsConnectionService`
  内部で完結、コントローラ層は`connectionId`のみ受け取るAPIを別途持つ）。テストは
  `ConnectionPoolRegistry`のプールを使わず、都度使い捨てのコネクションで検証する
  （プール汚染を避ける）。
- **B**: 既存接続の再テストは提供せず、新規登録前の未保存設定のテストのみサポートする。
- **C**: その他（自由記述）

[Answer]: A

---

### Q5. Business Logic Modeling — スキーマ取り込みの対象範囲

`importSchema(connectionId)`が対象RDBMSから何を取り込むか。

- **A（推奨）**: 接続先DBに存在する全スキーマ（`DialectStrategy.getSchemaResolutionMode()`が
  返す解釈に従う）配下の全テーブル・全ビューを一括取り込みする（スキーマ選択UIは持たない）。
  ビューはテーブルと同様に物理名・コメント・カラム構成を取り込むが、主キー構成は
  持たない（ビューには主キー制約がないため、`TableDetail`の主キー一覧は空）。
  テーブル種別（`TABLE`/`VIEW`）は`TableMetadata`/`TableDetail`に保持し、後続ユニット
  （権限設定・マスタメンテナンス）が区別に利用できるようにする。
- **B**: 取り込み前にスキーマ選択UIを提供し、管理者が選んだスキーマのみ取り込む
  （大規模DBで不要なスキーマの取り込みを避けられる一方、UIと事前スキーマ一覧取得APIが
  追加で必要）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q6. Business Rules — スキーマ再取り込み時の既存データの扱い

同一接続に対して`importSchema`が複数回呼ばれた場合（スキーマ変更後の再取り込み）の挙動。
U4（Permission Management）がテーブル/カラムをID参照で権限に紐づける想定のため、
IDの安定性が重要になる。

- **A（推奨）**: 物理名（スキーマ名+テーブル名+カラム名）でマッチングする差分更新
  （upsert）を行う。既存テーブル/カラムは同一物理名であればIDを維持したまま属性
  （型・コメント・主キー構成等）のみ更新する。対象RDBMS側で削除されたテーブル/カラムは
  内部DB側のレコードを削除せず「非アクティブ（stale）」フラグを立てて残す（既存の
  権限設定を壊さないため、削除は行わない）。新規追加されたテーブル/カラムは新規IDで
  追加する。
- **B**: 単純に全削除→全再作成する（実装は簡素だが、既存テーブルのIDが再取り込みの
  たびに変わり、U4側の権限設定が孤立/不整合になるリスクがある）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q7. Error Handling — スキーマ取り込み失敗時の挙動

取り込み処理の途中（一部テーブルのメタデータ取得後）で対象RDBMSとの接続が切れる等の
エラーが発生した場合の扱い。

- **A（推奨）**: 取り込み処理全体を単一トランザクションとして扱い、失敗時は内部DBへの
  変更を全ロールバックする（部分的な取り込み結果を残さない）。`SchemaImportResult`は
  成功/失敗フラグと概要メッセージ（取り込んだテーブル数、または失敗理由の概要）を返す。
  結果（成功/失敗いずれも）は`AuditLogService.record(...)`で明示的に記録する
  （MVP-8受け入れ基準どおり）。
- **B**: 部分的に取り込めたテーブルはコミットし、失敗したテーブルのみスキップして
  結果に含める（部分成功を許容、実装は複雑になる）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q8. Frontend Components — `rdbmsConnection/`・`schema/`機能のコンポーネント構成

U1/U2の`frontend-components.md`と同様の粒度で、本ユニットのフロントエンド機能を
どこまで詳細に設計するか。

- **A（推奨）**: 以下の画面・コンポーネントを設計する（いずれも管理者専用、`/admin`配下）。
  - `features/rdbmsConnection/`: `ConnectionListPage`（登録済み接続の一覧、U1の`DataTable`再利用、
    各行に「編集」「接続テスト」「スキーマ取り込み」導線）、`ConnectionFormPage`（新規登録/編集
    共用フォーム、保存前の「接続テスト」ボタン、パスワードは入力後マスク表示・編集時は
    再入力必須）、`connectionApi.ts`。
  - `features/schema/`: `SchemaImportPanel`（`ConnectionListPage`または詳細画面から起動する
    取り込み実行ボタン+結果表示、成功/失敗と取り込みテーブル数を表示）、
    `SchemaBrowserPage`（取り込み済みスキーマ/テーブル/カラム一覧の参照専用ビュー、
    権限フィルタなしの生データ、U4完成までは管理者が現状確認するための暫定閲覧画面）、
    `schemaApi.ts`。
  - `AppRouter.tsx`に`/admin/rdbms-connections`、`/admin/rdbms-connections/:id`、
    `/admin/schema`等の管理者専用ルートを追加する。
- **B**: 別の粒度・構成を希望する（自由記述）。

[Answer]: A

---

## Step 5: 回答分析

（ユーザ回答後にここへ記入）

## Step 6: 成果物生成チェックリスト

- [ ] `aidlc-docs/construction/u3-rdbms-connection-schema-import/functional-design/domain-entities.md`
- [ ] `aidlc-docs/construction/u3-rdbms-connection-schema-import/functional-design/business-rules.md`
- [ ] `aidlc-docs/construction/u3-rdbms-connection-schema-import/functional-design/business-logic-model.md`
  （PBT-01: テスト可能な性質セクションを含む）
- [ ] `aidlc-docs/construction/u3-rdbms-connection-schema-import/functional-design/frontend-components.md`