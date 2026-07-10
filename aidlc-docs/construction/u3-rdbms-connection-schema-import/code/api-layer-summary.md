# U3 RDBMS Connection & Schema Import - APIレイヤサマリ

Step 5（APIレイヤ生成）・Step 6（APIレイヤ単体テスト）で生成したエンドポイントの一覧。

## エンドポイント一覧

| パス | メソッド | 認可要件 | Controller |
|---|---|---|---|
| `/api/rdbms-connections` | POST | 認証必須 + `ROLE_ADMIN` | `RdbmsConnectionController#createConnection` |
| `/api/rdbms-connections/{id}` | PUT | 認証必須 + `ROLE_ADMIN` | `RdbmsConnectionController#updateConnection` |
| `/api/rdbms-connections` | GET | 認証必須 + `ROLE_ADMIN` | `RdbmsConnectionController#listConnections` |
| `/api/rdbms-connections/{id}` | GET | 認証必須 + `ROLE_ADMIN` | `RdbmsConnectionController#getConnection` |
| `/api/rdbms-connections/test` | POST | 認証必須 + `ROLE_ADMIN` | `RdbmsConnectionController#testConnection`（保存前設定） |
| `/api/rdbms-connections/{id}/test` | POST | 認証必須 + `ROLE_ADMIN` | `RdbmsConnectionController#testConnection`（既存接続） |
| `/api/rdbms-connections/{connectionId}/schema-import` | POST | 認証必須 + `ROLE_ADMIN` | `SchemaController#importSchema` |
| `/api/rdbms-connections/{connectionId}/schemas` | GET | 認証必須 + `ROLE_ADMIN` | `SchemaController#listSchemas` |
| `/api/rdbms-connections/{connectionId}/schemas/{schema}/tables` | GET | 認証必須 + `ROLE_ADMIN` | `SchemaController#listTables` |
| `/api/rdbms-connections/{connectionId}/schemas/{schema}/tables/{table}` | GET | 認証必須 + `ROLE_ADMIN` | `SchemaController#getTableDetail` |

いずれも認可の強制は`SecurityConfig`の`authorizeHttpRequests`（`requestMatchers("/api/rdbms-connections/**").hasRole("ADMIN")`）に委ね、Controller側での二重チェックはしない。

## `RdbmsConnectionController`（`/api/rdbms-connections`）

### `POST ""`（接続作成）

リクエスト（`ConnectionConfig`）:
```json
{"name": "test", "rdbmsType": "MYSQL", "host": "localhost", "port": 3306,
 "databaseName": "mastermeister", "username": "user", "password": "pass",
 "additionalParams": null}
```
`Authentication.getPrincipal()`（`JwtAuthenticationFilter`が設定する`Long`型のユーザーID）を`adminUserId`として`RdbmsConnectionService.createConnection(adminUserId, config)`に渡す。成功時`201 Created`で新規接続の`id`（`Long`）。

### `PUT /{id}`（接続更新）

リクエストは作成と同じ`ConnectionConfig`。`RdbmsConnectionService.updateConnection(adminUserId, id, config)`を呼び出す。成功時`204 No Content`。存在しない`id`は`EntityNotFoundException`（404）。

### `GET ""`（接続一覧）

`RdbmsConnectionService.listConnections()`を呼び出す。成功時`200 OK`で`List<ConnectionSummary>`（`password`は含まない）:
```json
[{"id": 42, "name": "test", "rdbmsType": "MYSQL", "host": "localhost", "databaseName": "mastermeister"}]
```

### `GET /{id}`（接続詳細）

`RdbmsConnectionService.getConnection(id)`を呼び出す。成功時`200 OK`で`ConnectionDetail`（`password`は含まない）:
```json
{"id": 42, "name": "test", "rdbmsType": "MYSQL", "host": "localhost", "port": 3306,
 "databaseName": "mastermeister", "username": "user", "additionalParams": null}
```
存在しない`id`は`EntityNotFoundException`（404）。

### `POST /test`（保存前設定の接続テスト）

リクエストは作成と同じ`ConnectionConfig`。`RdbmsConnectionService.testConnection(config)`を呼び出す。成功時`200 OK`で`ConnectionTestResult`:
```json
{"success": true, "message": "OK"}
```
接続失敗時も例外を投げず`success: false`＋メッセージを返す（P6: レジストリへの副作用なし）。

### `POST /{id}/test`（既存接続の接続テスト）

ボディなし。`RdbmsConnectionService.testConnection(id)`を呼び出す。レスポンス形状は`/test`と同じ`ConnectionTestResult`。存在しない`id`は`EntityNotFoundException`（404）。

## `SchemaController`（`/api/rdbms-connections/{connectionId}`）

### `POST /schema-import`（スキーマ取り込み）

ボディなし。`Authentication.getPrincipal()`を`adminUserId`として`SchemaImportService.importSchema(connectionId, adminUserId)`に渡す。成功時`200 OK`で`SchemaImportResult`:
```json
{"success": true, "tableCount": 5, "message": "OK"}
```
対象RDBMSへの接続情報が同期的にメタデータ取得され、全体が単一トランザクションで置き換わる（P10: 失敗時ロールバック）。存在しない`connectionId`は`EntityNotFoundException`（404）。

### `GET /schemas`（スキーマ一覧）

`SchemaQueryService.listSchemas(connectionId)`を呼び出す。成功時`200 OK`で`List<String>`（スキーマ名一覧）。

### `GET /schemas/{schema}/tables`（テーブル一覧）

`SchemaQueryService.listTables(connectionId, schema)`を呼び出す。成功時`200 OK`で`List<TableMetadata>`（`stale = false`のみ、P12）:
```json
[{"schemaName": "public", "tableName": "users", "tableType": "TABLE", "comment": "..."}]
```

### `GET /schemas/{schema}/tables/{table}`（テーブル詳細・カラム一覧）

`SchemaQueryService.getTableDetail(connectionId, schema, table)`を呼び出す。成功時`200 OK`で`TableDetail`（カラムは`stale = false`のみ、P12。ビューの`primaryKeySequence`は常に`null`、P11）:
```json
{"schemaName": "public", "tableName": "users", "tableType": "TABLE", "comment": "...",
 "columns": [{"columnName": "id", "dataType": "BIGINT", "nullable": false, "comment": "primary key",
              "ordinalPosition": 1, "primaryKeySequence": 1}]}
```
対象テーブルが存在しない（未取り込みまたはstale）場合は`EntityNotFoundException`（404）。

## エラーレスポンス

`GlobalExceptionHandler`（`@RestControllerAdvice`、U1既存＋U2で追記済み）が全コントローラ共通で以下にマッピングする。本ユニットは既存の`EntityNotFoundException`をそのまま再利用し、新規例外クラスは追加していない。

| 例外 | HTTPステータス | `error` |
|---|---|---|
| 未認証（`SecurityConfig`のstateless認証チェーンで拒否） | `401 Unauthorized` | （`RestAuthenticationEntryPoint`が`ErrorResponse`を返却） |
| `ROLE_ADMIN`以外での認可失敗（`SecurityConfig`の`hasRole`で拒否） | `403 Forbidden` | （`RestAccessDeniedHandler`が`ErrorResponse`を返却） |
| `EntityNotFoundException`（接続/テーブル未存在） | `404 Not Found` | `ENTITY_NOT_FOUND` |
| その他未捕捉の`Exception` | `500 Internal Server Error` | `INTERNAL_ERROR`（メッセージは固定文言、詳細はサーバーログにのみ出力） |

エラーボディ共通形状（`ErrorResponse`）:
```json
{"error": "ENTITY_NOT_FOUND", "message": "RdbmsConnection not found: id=42"}
```

## テストカバレッジ（Step 6）

| テストクラス | 検証内容 |
|---|---|
| `RdbmsConnectionControllerTest` | 接続CRUD（create/update/list/get）・接続テスト（保存前/既存接続）の6エンドポイントそれぞれについて管理者成功系・非管理者403・未認証401を検証（`create`/`update`は`Authentication`を明示注入し`adminUserId`がサービス呼び出しに渡ることも確認）（example-based、15件） |
| `SchemaControllerTest` | スキーマ取り込み・スキーマ一覧・テーブル一覧・テーブル詳細の4エンドポイントそれぞれについて管理者成功系・非管理者403・未認証401を検証（`importSchema`は`Authentication`を明示注入し`adminUserId`がサービス呼び出しに渡ることも確認）（example-based、10件） |

P1〜P12（業務ロジックの性質）はController層では再検証せず、`business-logic-summary.md`記載のjqwik `@Property`テスト（`RdbmsConnectionServiceTest`/`SchemaImportServiceTest`/`SchemaQueryServiceTest`等）に一元化している。