# business-logic-model.md — U3: RDBMS Connection & Schema Import

`domain-entities.md`・`business-rules.md`で定義したモデル・ルールに基づく業務フロー。
技術非依存（実装方式はNFR Design/Code Generationで確定）。

---

## フロー1: 接続情報の登録・更新（MVP-7, ADM-3）

**関与コンポーネント**: フロントエンド`rdbmsConnection/` → `RdbmsConnectionService` →
`AuditLogService`（U1）

1. 管理者が`ConnectionFormPage`で接続情報（種別・ホスト・ポート・DB名・認証情報・
   追加パラメータ）を入力する。保存前に「接続テスト」ボタンから
   `RdbmsConnectionService.testConnection(config)`を呼び出せる（フロー2パターン1）。
2. 保存操作で`createConnection(config)`（新規）または`updateConnection(connectionId,
   config)`（編集）が呼び出される。パスワードは`EncryptedStringConverter`により
   透過的に暗号化されて保存される（`business-rules.md` 1.1）。
3. `updateConnection`成功時は`ConnectionPoolRegistry.invalidate(connectionId)`を呼び出し、
   既存プールを破棄する（`business-rules.md` 1.5、次回アクセス時に新設定で再作成）。
4. `AuditLogService.record(ADMIN_OPERATION, RDBMS_CONNECTION_CHANGED, adminUserId, ...)`を
   呼び出す（MVP-7 AC、`business-rules.md` 1.2）。
5. `ConnectionListPage`は`listConnections()`で登録済み接続の一覧を表示する（ADM-3 AC:
   複数接続の管理）。

---

## フロー2: 接続テスト（Question 4 = A）

**関与コンポーネント**: フロントエンド`rdbmsConnection/` → `RdbmsConnectionService`

1. **パターン1（新規登録前）**: `ConnectionFormPage`の「接続テスト」ボタンから、未保存の
   `ConnectionConfig`をそのまま`testConnection(config)`に渡す。
2. **パターン2（既存接続の再テスト）**: `ConnectionListPage`の行内アクションから
   `connectionId`のみを送信し、コントローラ経由で呼び出された
   `RdbmsConnectionService`内部が`getConnection(connectionId)`で復号済み設定を取得した上で
   `testConnection(config)`に渡す。
3. いずれのパターンも`ConnectionPoolRegistry`のプールを経由せず、都度使い捨てのコネクション
   で検証する（`business-rules.md` 1.6）。
4. `ConnectionTestResult`（成功/失敗と簡潔なエラー概要）をフロントエンドへ返し、
   `ConnectionFormPage`/`ConnectionListPage`はその場に結果を表示する。

---

## フロー3: コネクションプールの遅延初期化・破棄（Question 3 = A、横断的機構）

**関与コンポーネント**: `ConnectionPoolRegistry`（他ユニット・他フローから呼び出される
支援機構であり、単独のUI操作トリガーは持たない）

1. `getDataSource(connectionId)`/`getJdbcTemplate(connectionId)`が初めて呼び出された時点で
   プールを作成しキャッシュする。以降の呼び出しはキャッシュを返す。
2. `invalidate(connectionId)`は、フロー1の`updateConnection`成功時、および接続削除時に
   呼び出される（`business-rules.md` 1.5）。呼び出し後にキャッシュを破棄し、次回
   `getDataSource`/`getJdbcTemplate`呼び出し時に新しい設定で再作成する。
3. アプリケーション起動時には全接続への一括接続は行わない（未使用の対象RDBMSが接続不能でも
   アプリ自体は起動できる）。
4. `SchemaImportService`（フロー4）や将来のU5/U6のマスタデータ・クエリ実行機能が、この
   仕組みを通じて対象RDBMSへの`DataSource`/`NamedParameterJdbcTemplate`を取得する。

---

## フロー4: スキーマ取り込み（MVP-8）

**関与コンポーネント**: フロントエンド`schema/` → `SchemaImportService` →
`DialectStrategy`（U1） → `ConnectionPoolRegistry` → `AuditLogService`（U1）

1. 管理者が`SchemaImportPanel`（`ConnectionListPage`または接続詳細画面から起動）で
   取り込み実行を指示すると、`SchemaImportService.importSchema(connectionId)`が
   呼び出される。
2. `ConnectionPoolRegistry`経由で対象RDBMSへの接続を取得し、`DialectStrategy`
   （`getSchemaResolutionMode()`）を用いて対象接続に存在する全スキーマ配下の全テーブル・
   全ビューのメタデータ（物理名・コメント・型・主キー構成）を読み取る
   （`business-rules.md` 2.1）。
3. 読み取った各テーブル/カラムを、内部DBの既存`SchemaTable`/`SchemaColumn`と物理名で
   マッチングしupsertする。対象RDBMS側で見つからなくなった既存行は`stale = true`に
   設定する（削除しない、`business-rules.md` 2.2）。
4. 処理全体は単一トランザクションとして扱い、途中でエラーが発生した場合は内部DBへの
   変更を全ロールバックする（`business-rules.md` 2.3）。
5. `SchemaImportResult`（成功/失敗フラグ、取り込んだテーブル数または失敗概要）を
   フロントエンドへ返し、`AuditLogService.record(ADMIN_OPERATION, SCHEMA_IMPORTED,
   adminUserId, result=SUCCESS|FAILURE, ...)`を呼び出す（MVP-8 AC）。
6. `SchemaImportPanel`は成功/失敗と取り込みテーブル数を表示する。

---

## フロー5: スキーマ参照（暫定閲覧、Question 8 = A）

**関与コンポーネント**: フロントエンド`schema/` → `SchemaQueryService`

1. 管理者が`SchemaBrowserPage`を開くと、`SchemaQueryService.listSchemas(connectionId)`で
   スキーマ一覧を取得し、選択したスキーマに対し`listTables(connectionId, schema)`で
   テーブル/ビュー一覧（`stale = true`のものは既定で除外、`business-rules.md` 2.4）を
   取得する。
2. テーブルを選択すると`getTableDetail(connectionId, schema, table)`でカラム一覧・型・
   コメント・主キー構成（複合主キー対応）を取得し表示する。
3. 本画面はメタデータのみを表示する参照専用ビューであり、権限フィルタを適用しない生データを
   表示する（U4完成までの暫定閲覧画面）。レコードデータ（実際の行データ）は表示しない
   —`SchemaQueryService`のAPIはメタデータのみを返し、レコードデータを返すAPIを持たない。
   レコードデータの閲覧は権限フィルタ（`EffectivePermissionResolver`、U4）を経由する必要が
   あるため、U5（Master Data Maintenance）の責務として明確に分離する。

---

## テスト可能な性質（Testable Properties, PBT-01）

`property-based-testing`拡張（enabled）のRule PBT-01に基づき、本ユニットの業務ロジック
（フロー1〜5）が持つ性質をカテゴリ別に識別する。実際のPBTケース設計・生成器定義はCode
Generation計画時に確定する。

| # | 対象 | カテゴリ | 性質 | 備考 |
|---|---|---|---|---|
| P1 | `EncryptedStringConverter`（`domain-entities.md`） | Round-trip | 任意の文字列に対し、`convertToDatabaseColumn`で暗号化した値を`convertToEntityAttribute`で復号すると必ず元の文字列に一致する | Question 1 = A |
| P2 | `EncryptedStringConverter`（`domain-entities.md`） | Invariant | 空文字列を除く任意の非空文字列に対し、暗号化後の値は元の平文と一致しない（暗号化が実際に行われている） | セキュリティ上の不変条件 |
| P3 | `RdbmsConnectionService`のURL組み立て（フロー1、`business-rules.md` 1.3） | Invariant | 任意の`additionalParams`（空/非空）に対し、組み立てられたJDBC URLは必ず構造化されたベースURL部分から始まり、`additionalParams`が非空の場合のみ末尾に1回だけ付加される（重複付加なし） | Question 2 = A |
| P4 | `ConnectionPoolRegistry.getDataSource`（フロー3） | Idempotence | 同一`connectionId`で複数回呼び出しても、`invalidate`が呼ばれない限り常に同一の`DataSource`インスタンスが返る | Question 3 = A |
| P5 | `ConnectionPoolRegistry.invalidate`（フロー3） | Invariant（状態遷移） | `invalidate(connectionId)`呼び出し後の次回`getDataSource`呼び出しは、呼び出し前にキャッシュされていたインスタンスとは異なる新しいインスタンスを返す | `business-rules.md` 1.5 |
| P6 | `RdbmsConnectionService.testConnection`（フロー2） | Invariant | いずれのパターン（未保存設定/既存接続再テスト）で呼び出しても、`ConnectionPoolRegistry`のキャッシュ状態（登録済みプールの集合）は呼び出し前後で変化しない | Question 4 = A、1.6 |
| P7 | `SchemaImportService.importSchema`（フロー4） | Invariant（物理名マッチング） | 変更のない対象RDBMSスキーマに対して`importSchema`を複数回実行しても、既存`SchemaTable`/`SchemaColumn`の`id`は常に同一物理名に対して不変である | Question 6 = A |
| P8 | `SchemaImportService.importSchema`（フロー4） | Invariant | 対象RDBMS側から削除された物理名のテーブル/カラムは、再取り込み後に`stale = true`となり、内部DBの行自体は削除されない | Question 6 = A |
| P9 | `SchemaImportService.importSchema`（フロー4） | Idempotence | 対象RDBMS側の状態が変化しない限り、`importSchema`を連続して複数回実行しても、`stale = false`の`SchemaTable`/`SchemaColumn`集合（id・属性含む）は1回実行時と同一である | フロー4 手順3 |
| P10 | `SchemaImportService.importSchema`（フロー4、失敗時） | Round-trip（トランザクション原子性） | 取り込み処理の途中で例外が発生した場合、処理後の内部DB状態（`SchemaTable`/`SchemaColumn`の全行）は処理開始前の状態と完全に一致する | Question 7 = A、`business-rules.md` 2.3 |
| P11 | `SchemaImportService.importSchema`（ビュー取り込み、フロー4） | Invariant | `tableType = VIEW`として取り込まれた`SchemaTable`配下の`SchemaColumn`は、`primaryKeySequence`が常に`null`である | Question 5 = A |