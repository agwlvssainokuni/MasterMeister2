# U5 Master Data Maintenance - APIレイヤサマリ

Step 5（APIレイヤ生成）・Step 6（APIレイヤ単体テスト）で生成したエンドポイントの一覧。

## エンドポイント一覧

| パス | メソッド | 認可要件 | Controller |
|---|---|---|---|
| `/api/master-data/connections` | GET | 認証必須（ロール制約なし） | `MasterDataController#listAccessibleConnections` |
| `/api/master-data/{connectionId}/schemas` | GET | 認証必須（ロール制約なし） | `MasterDataController#listAccessibleSchemas` |
| `/api/master-data/{connectionId}/schemas/{schema}/tables` | GET | 認証必須（ロール制約なし） | `MasterDataController#listAccessibleTables` |
| `/api/master-data/{connectionId}/schemas/{schema}/tables/{table}/records:search` | POST | 認証必須（ロール制約なし） | `MasterDataController#listRecords` |
| `/api/master-data/{connectionId}/schemas/{schema}/tables/{table}/records:apply` | POST | 認証必須（ロール制約なし） | `MasterDataController#applyChanges` |

いずれも認可の強制は`SecurityConfig`の`authorizeHttpRequests`に委ね、Controller側での二重チェックはしない。`U1`〜`U4`の管理系エンドポイント（`hasRole("ADMIN")`）と異なり、本ユニットは全ユーザ向け機能のため`.requestMatchers("/api/master-data/**").authenticated()`（item 5-3で新規追加）を用いる。実際にはこの明示ルールがなくとも末尾の`.anyRequest().authenticated()`で同じ結果になるが、「ブラウンフィールド発見事項」3の記載どおり他の一般ユーザ向けルールと構成を揃えるため明示的に追加した。

## `MasterDataController`（`/api/master-data`）

### `GET /connections`（アクセス可能接続一覧）

ボディなし。`MasterDataQueryService.listAccessibleConnections(userId)`
（「ブラウンフィールド発見事項」5、`RdbmsConnectionRepository.findAll()`を
`EffectivePermissionResolver.listAccessibleSchemas(userId, connectionId)`が非空の接続
＝当該接続上に`NONE`超の権限を持つテーブルが1件以上存在する接続のみへ絞り込む）を呼び出す。
成功時`200 OK`で`List<ConnectionSummary>`（`rdbmsconnection`パッケージ既存、新規DTOなし）:
```json
[{"id": 42, "name": "conn1", "rdbmsType": "POSTGRESQL", "host": "localhost", "databaseName": "db1"}]
```

### `GET /{connectionId}/schemas`（アクセス可能スキーマ一覧）

ボディなし。`MasterDataQueryService.listAccessibleSchemas(userId, connectionId)`（`EffectivePermissionResolver`へそのまま委譲）を呼び出す。成功時`200 OK`で`List<String>`:
```json
["public", "sales"]
```

### `GET /{connectionId}/schemas/{schema}/tables`（アクセス可能テーブル一覧）

ボディなし。`MasterDataQueryService.listAccessibleTables(userId, connectionId, schema)`を呼び出す。成功時`200 OK`で`List<TableSummary>`:
```json
[{"schemaName": "public", "tableName": "employees", "tableType": "TABLE",
  "comment": "従業員マスタ", "effectivePermission": "UPDATE",
  "canCreate": true, "canDelete": false}]
```

### `POST /{connectionId}/schemas/{schema}/tables/{table}/records:search`（レコード検索）

リクエスト（`RecordSearchRequest`、item 5-1で新規生成）:
```json
{"criteria": {"mode": "UI",
              "uiConditions": [{"columnName": "status", "operator": "EQ", "value": "ACTIVE"}],
              "uiSorts": [{"columnName": "id", "direction": "ASC"}],
              "rawWhere": null, "rawOrderBy": null},
 "page": 0, "pageSize": 50}
```
`request.page()`/`request.pageSize()`から`PageRequest`を組み立て、`MasterDataQueryService.listRecords(userId, connectionId, schema, table, request.criteria(), page)`を呼び出す。成功時`200 OK`で`RecordListResult`:
```json
{"columns": [{"columnName": "id", "dataType": "INTEGER", "nullable": false,
              "primaryKeySequence": 1, "effectivePermission": "READ"}],
 "records": {"content": [[1]], "totalCount": 1, "page": 0, "pageSize": 50}}
```
テーブル権限が`NONE`、またはUIモードで`NONE`権限カラムを条件/並び替えに参照した場合は`PermissionDeniedException`（403）。RAWモードで`rawWhere`/`rawOrderBy`にセミコロンを含む場合も同例外（403、P4）。

### `POST /{connectionId}/schemas/{schema}/tables/{table}/records:apply`（レコード更新）

リクエスト（`MutationRequest`）:
```json
{"creates": [{"values": {"id": 10, "name": "new"}}],
 "updates": [{"primaryKeyValues": {"id": 1}, "changedValues": {"name": "updated"}}],
 "deletes": [{"primaryKeyValues": {"id": 2}}]}
```
`MasterDataMutationService.applyChanges(userId, connectionId, schema, table, request)`を呼び出す。権限検証（`canCreate`/カラムUPDATE権限/`canDelete`、all-or-nothing）失敗時は`PermissionDeniedException`（403）、主キーなしテーブルへの`updates`/`deletes`指定時や合計操作件数が`mm.app.master-data.max-mutation-batch-size`超過時は`ValidationException`（400）——いずれもDBアクセス前に例外として投げる（Step 2-6の設計）。一方、トランザクション実行中の`SQLException`等の実行時DB失敗は例外を投げず、`200 OK`で`MutationResult(success=false, ...)`として返却される（Step 2-6の設計判断、あえてHTTPステータスを分離しない）:
```json
{"success": true, "createdCount": 1, "updatedCount": 1, "deletedCount": 1, "errorMessage": null}
```
```json
{"success": false, "createdCount": 0, "updatedCount": 0, "deletedCount": 0,
 "errorMessage": "Unique index or primary key violation: ..."}
```

## エラーレスポンス

`GlobalExceptionHandler`（`@RestControllerAdvice`、U1既存＋U2/U3/U4で追記済み）が全コントローラ共通で以下にマッピングする。本ユニットは新規の例外クラスを追加していない。

| 例外 | HTTPステータス | `error` |
|---|---|---|
| 未認証（`SecurityConfig`のstateless認証チェーンで拒否） | `401 Unauthorized` | （`RestAuthenticationEntryPoint`が`ErrorResponse`を返却） |
| `PermissionDeniedException`（テーブル/カラム権限不足、RAWセミコロン検知） | `403 Forbidden` | `PERMISSION_DENIED` |
| `ValidationException`（主キーなしテーブルへの更新/削除、バッチサイズ超過） | `400 Bad Request` | `VALIDATION_ERROR` |
| `EntityNotFoundException`（`connectionId`が存在しない） | `404 Not Found` | `ENTITY_NOT_FOUND` |
| その他未捕捉の`Exception` | `500 Internal Server Error` | `INTERNAL_ERROR`（メッセージは固定文言、詳細はサーバーログにのみ出力） |

エラーボディ共通形状（`ErrorResponse`）:
```json
{"error": "PERMISSION_DENIED", "message": "No access to table: public.employees"}
```

**既知の課題**: なし。本ユニットは`/import`のような「例外を内部でcatchしてボディで成否を表現する」設計を`applyChanges`のDB実行時失敗にのみ適用しており（上記`MutationResult(success=false, ...)`）、権限検証・入力検証の失敗は他ユニット同様に例外経由でHTTPステータスへマッピングされるため、U4の`PermissionYamlFormatException`のような「到達しないハンドラ」は存在しない。

## テストカバレッジ（Step 6）

| テストクラス | 検証内容 |
|---|---|
| `MasterDataControllerTest` | 5エンドポイント（`listAccessibleConnections`/`listAccessibleSchemas`/`listAccessibleTables`/`listRecords`/`applyChanges`）それぞれについて認証済みユーザ成功系・未認証401を検証（本ユニットは管理者ロール制約を持たないため403系テストは対象外、example-based、10件） |

P1〜P10（業務ロジックの性質）はController層では再検証せず、`business-logic-summary.md`記載のjqwik `@Property`テスト（`MasterDataQueryServiceTest`/`MasterDataMutationServiceTest`）に一元化している。