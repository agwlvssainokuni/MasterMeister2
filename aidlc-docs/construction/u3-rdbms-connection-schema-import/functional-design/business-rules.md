# business-rules.md — U3: RDBMS Connection & Schema Import

`u3-rdbms-connection-schema-import-functional-design-plan.md`の回答（Q1〜Q8）に基づく
業務ルール定義。

---

## 1. 接続管理（rdbmsconnection）

### 1.1 パスワード暗号化（Question 1 = A）
`RdbmsConnection.password`は`domain-entities.md`の`EncryptedStringConverter`により
AES/GCM暗号化してH2に保存する。復号済みパスワードを実際に使用するのは、対象RDBMSへの
接続を試みる箇所（`ConnectionPoolRegistry`の`DataSource`構築、`testConnection`の使い捨て
接続）のみであり、`@Convert`はエンティティロード時に常に透過的に復号を行う（手動復号を
2箇所以上で重複実装する必要はない）。

### 1.2 登録・更新の監査記録（MVP-7 AC、Question 1〜2の対象外・既存規約の適用）
`createConnection`/`updateConnection`成功時は`AuditLogService.record(ADMIN_OPERATION,
RDBMS_CONNECTION_CHANGED, adminUserId, ..., targetDescription=接続名)`を呼び出す（U1
`domain-entities.md`で`RDBMS_CONNECTION_CHANGED`イベント種別が定義済み）。パスワードは
監査ログの対象詳細（`targetDescription`等）に含めない。

### 1.3 JDBC URLの組み立て（Question 2 = A）
`rdbmsType`に対応する`DialectStrategy`を用いて`host`+`port`+`databaseName`から
ベースURLを構造化して組み立て、`additionalParams`が設定されている場合はその文字列を
そのまま末尾に付加する。`additionalParams`の中身自体はバリデーションしない
（管理者専用機能であり値の出所は信頼できるため。形式不正であれば1.4の`testConnection`で
顕在化する）。

### 1.4 `testConnection`の実行パターン（Question 4 = A）
2パターンに対応する。
| パターン | 動作 |
|---|---|
| 新規登録フォームでの保存前テスト | 未保存の`ConnectionConfig`をそのまま`testConnection(config)`に渡す。`ConnectionPoolRegistry`には登録しない使い捨て接続で検証し、成功/失敗と簡潔なエラー概要を返す |
| 既存接続の再テスト | コントローラは`connectionId`のみ受け取るAPIを持ち、`RdbmsConnectionService`内部で`getConnection(connectionId)`により復号済み`ConnectionConfig`を取得し、`testConnection(config)`に渡す |

いずれのパターンでも`ConnectionPoolRegistry`のプールは使わない（都度使い捨てのコネクション
で検証し、プール汚染を避ける、1.6と整合）。

### 1.5 コネクションプールのライフサイクル（Question 3 = A）
- `ConnectionPoolRegistry.getDataSource(connectionId)`/`getJdbcTemplate(connectionId)`は
  遅延初期化（lazy）。初回呼び出し時にプールを作成し、以降はキャッシュを返す。
- `updateConnection`成功時、および接続削除時は`invalidate(connectionId)`を呼び既存プールを
  破棄する。次回アクセス時に新しい設定で再作成される（削除済みの場合は例外）。
- アプリケーション起動時に全接続へ一括接続はしない（未使用の対象RDBMSが接続不能でも
  アプリ自体は起動できる）。

### 1.6 `testConnection`とプールの独立性（Question 4 = A）
1.4のいずれのパターンも1.5の`ConnectionPoolRegistry`のプールを経由しない。`testConnection`
の呼び出し自体ではプールは作成されない。

---

## 2. スキーマ取り込み（schema）

### 2.1 取り込み対象範囲（Question 5 = A）
`importSchema(connectionId)`は、`DialectStrategy.getSchemaResolutionMode()`が返す解釈に
従い、接続先DBに存在する全スキーマ配下の全テーブル・全ビューを一括取り込みする
（スキーマ選択UIは持たない）。ビューは物理名・コメント・カラム構成を取り込むが、
`SchemaColumn.primaryKeySequence`は常に`null`とする（ビューには主キー制約が存在しない
ため）。`SchemaTable.tableType`に`TABLE`/`VIEW`を保持し、後続ユニット（権限設定・
マスタメンテナンス）が区別に利用できるようにする。

### 2.2 再取り込み時のupsert・staleフラグ（Question 6 = A）
`(connectionId, schemaName, tableName)`および`(tableId, columnName)`の物理名で既存の
`SchemaTable`/`SchemaColumn`とマッチングする。
| 状況 | 動作 |
|---|---|
| 既存の物理名と一致するテーブル/カラムが対象RDBMS側に存在する | 既存行のIDを維持したまま属性（型・コメント・主キー構成等）を更新し、`stale`を`false`に戻す（過去にstale化していた場合の復活を含む）、`updatedAt`を更新する |
| 対象RDBMS側に存在するが内部DBに未登録の物理名 | 新規IDで`SchemaTable`/`SchemaColumn`を追加する |
| 内部DBに存在するが対象RDBMS側で見つからなくなった物理名 | 行を削除せず`stale`を`true`に設定し、`updatedAt`を更新する（既存の権限設定・U4のFK参照を壊さないため） |

単純な全削除→全再作成は行わない（既存テーブルのIDが再取り込みのたびに変わり、U4側の
権限設定が孤立/不整合になるリスクを避けるため）。

### 2.3 取り込み処理のトランザクション・失敗時の挙動（Question 7 = A）
- `importSchema`全体を単一トランザクションとして扱う。処理途中（一部テーブルのメタデータ
  取得後等）でエラーが発生した場合、内部DBへの変更を全ロールバックする（部分的な取り込み
  結果を残さない）。
- `SchemaImportResult`は成功/失敗フラグと概要メッセージ（取り込んだテーブル数、または
  失敗理由の概要）を返す。
- 結果（成功/失敗いずれも）は`AuditLogService.record(ADMIN_OPERATION, SCHEMA_IMPORTED,
  adminUserId, result=SUCCESS|FAILURE, ..., targetDescription=接続名)`で明示的に記録する
  （MVP-8 AC、U1`domain-entities.md`で`SCHEMA_IMPORTED`イベント種別が定義済み）。

### 2.4 スキーマ参照（`SchemaQueryService`、メタデータのみ）
`listSchemas`/`listTables`/`getTableDetail`は取り込み済みメタデータ（物理名・コメント・
型・主キー構成）のみを返す。レコードデータ（対象RDBMSの実データ行）を返すAPIは本ユニットに
持たない（Question 8 = A、`frontend-components.md`参照）。既定では`stale = true`の
テーブル/カラムを一覧から除外する（暫定閲覧画面での混乱を避けるため。除外の可否切り替えは
本ユニットのスコープ外とする）。

---

## 3. 設定キー（Question 1 = Aの実装方針より）

| キー | デフォルト値 | 形式 |
|---|---|---|
| `mm.app.rdbms-connection.encryption-key` | なし（未設定は起動時エラー） | AES鍵材料の文字列表現（具体的なエンコーディング・鍵長はCode Generationで確定、`mm.app.jwt.secret`と同様のfail-fastパターン） |

---

## 4. API認可（`SecurityConfig`、U1 NFR Design 1.3の規約に基づく）

本ユニットの全機能（接続管理・スキーマ取り込み・スキーマ参照）は管理者専用
（MVP-7, MVP-8, ADM-3のペルソナはいずれも「管理者」）。U1 NFR Design 1.3の
「管理者専用APIはパスパターンごとに`hasRole("ADMIN")`を明示指定する」方針に従う。

| パスパターン（想定、正確なパスはCode Generationで確定） | 対象 |
|---|---|
| `POST /api/rdbms-connections` | `createConnection` |
| `PUT /api/rdbms-connections/{id}` | `updateConnection` |
| `GET /api/rdbms-connections` | `listConnections` |
| `GET /api/rdbms-connections/{id}` | `getConnection` |
| `POST /api/rdbms-connections/test` | `testConnection`（未保存設定、1.4パターン1） |
| `POST /api/rdbms-connections/{id}/test` | `testConnection`（既存接続再テスト、1.4パターン2） |
| `POST /api/rdbms-connections/{id}/schema-import` | `importSchema` |
| `GET /api/rdbms-connections/{id}/schemas` | `listSchemas` |
| `GET /api/rdbms-connections/{id}/schemas/{schema}/tables` | `listTables` |
| `GET /api/rdbms-connections/{id}/schemas/{schema}/tables/{table}` | `getTableDetail` |

いずれも`hasRole("ADMIN")`を要求する（`permitAll()`のエンドポイントは本ユニットに存在
しない）。