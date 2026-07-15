# U7 Saved Query / Execution / History - APIレイヤサマリ

Step 5（APIレイヤ生成）・Step 6（APIレイヤ単体テスト）で生成したエンドポイントの一覧。

## エンドポイント一覧

| パス | メソッド | 認可要件 | Controller |
|---|---|---|---|
| `/api/saved-queries` | GET | 認証必須（ロール制約なし） | `SavedQueryController#listQueries` |
| `/api/saved-queries` | POST | 認証必須（ロール制約なし） | `SavedQueryController#saveQuery` |
| `/api/saved-queries/{savedQueryId}` | GET | 認証必須（ロール制約なし） | `SavedQueryController#getQuery` |
| `/api/saved-queries/{savedQueryId}` | PUT | 認証必須（ロール制約なし） | `SavedQueryController#updateQuery` |
| `/api/saved-queries/{savedQueryId}/retire` | POST | 認証必須（ロール制約なし） | `SavedQueryController#retireQuery` |
| `/api/query-execution/adhoc` | POST | 認証必須（ロール制約なし） | `QueryExecutionController#executeAdhocSql` |
| `/api/query-execution/saved/{savedQueryId}` | POST | 認証必須（ロール制約なし） | `QueryExecutionController#executeSavedQuery` |
| `/api/query-history` | GET | 認証必須（ロール制約なし） | `QueryHistoryController#listHistory` |

いずれも認可の強制は`SecurityConfig`の`authorizeHttpRequests`に委ね、Controller側での二重
チェックはしない。`U1`〜`U4`の管理系エンドポイント（`hasRole("ADMIN")`）と異なり、本ユニットは
全ユーザ向け機能のため`.requestMatchers("/api/saved-queries/**").authenticated()`・
`.requestMatchers("/api/query-execution/**").authenticated()`・
`.requestMatchers("/api/query-history/**").authenticated()`（item 5-4で`/api/query-builder/**`
の直後に追加）を用いる。実際にはこの明示ルールがなくとも末尾の`.anyRequest().authenticated()`
で同じ結果になるが、`U5`/`U6`同様、他の一般ユーザ向けルールと構成を揃えるため明示的に追加した。

`connectionId`はU6と異なりクラスレベルパスに含めない（`SavedQuery`/`QueryHistory`エンティティが
`connectionId`を自身のフィールドとして持つため、リソースパスに含める必然性がない——Code
Generation時点で確定する事項1）。`connectionId`はリクエストボディ（POST/PUT）またはクエリ
パラメータ（GET）で渡す。

## `SavedQueryController`（`/api/saved-queries`）

### `GET ""`（保存済みクエリ一覧、P1）

クエリパラメータ: `connectionId`（必須）、`includeRetired`（省略時`false`）。
`SavedQueryService.listQueries(userId, connectionId, includeRetired)`（可視性フィルタ、P1）を
呼び出す。成功時`200 OK`で`List<SavedQuerySummary>`:
```json
[{"id": 10, "name": "q1", "visibility": "PUBLIC", "retired": false, "ownerId": 1}]
```

### `POST ""`（保存、GEN-10）

リクエスト（`SaveQueryRequest`、item 5-1で新規生成）:
```json
{"connectionId": 42, "name": "q1", "sql": "SELECT 1", "visibility": "PRIVATE"}
```
`SavedQueryService.saveQuery(userId, connectionId, name, sql, visibility)`を呼び出す。成功時
`201 Created`で生成された`savedQueryId`（bare `Long`、`RdbmsConnectionController#createConnection`
と同一パターン）:
```json
10
```

### `GET "/{savedQueryId}"`（詳細取得、`business-rules.md` 1.1）

ボディなし。`SavedQueryService.getQuery(userId, savedQueryId)`（`retired`を無視し可視性条件のみで
成否が決まる、P2）を呼び出す。成功時`200 OK`で`SavedQueryDetail`:
```json
{"id": 10, "ownerId": 1, "connectionId": 42, "name": "q1", "sql": "SELECT 1",
 "visibility": "PRIVATE", "retired": false, "executionCount": 0,
 "createdAt": "2026-07-13T10:00:00Z", "updatedAt": "2026-07-13T10:00:00Z"}
```
可視性条件を満たさない場合は`PermissionDeniedException`（403）。存在しない場合は
`EntityNotFoundException`（404）。

### `PUT "/{savedQueryId}"`（編集、GEN-12）

リクエスト（`UpdateQueryRequest`、item 5-1で新規生成）:
```json
{"name": "q2", "sql": "SELECT 2", "visibility": "PUBLIC"}
```
`SavedQueryService.updateQuery(userId, savedQueryId, name, sql, visibility)`
（`retired=true`は常に`EntityNotFoundException`、所有者以外は`PermissionDeniedException`、P2）を
呼び出す。成功時`204 No Content`（`RdbmsConnectionController#updateConnection`と同一パターン）。

### `POST "/{savedQueryId}/retire"`（廃止、GEN-12）

ボディなし。`SavedQueryService.retireQuery(userId, savedQueryId)`（所有者以外は
`PermissionDeniedException`、一度`retired=true`になると本ユニットのいかなる操作でも
`false`に戻らない、P3）を呼び出す。成功時`204 No Content`。

## `QueryExecutionController`（`/api/query-execution`）

### `POST "/adhoc"`（手入力SQL実行、GEN-13〜14）

リクエスト（`AdhocExecutionRequest`、item 5-2で新規生成）:
```json
{"connectionId": 42, "sql": "SELECT * FROM tbl WHERE id = :id", "params": {"id": 1},
 "paging": {"enabled": false, "page": 0, "pageSize": 0}}
```
`QueryExecutionService.executeAdhocSql(userId, connectionId, sql, params, paging)`を呼び出す。
読み取り専用検証（ちょうど1文かつ`Select`型、P4）・パラメータ事前検証（検出した`:paramName`が
`params`に存在しない場合`ValidationException`）を経て対象RDBMSへ実行される。成功時`200 OK`で
`QueryResult`:
```json
{"columns": [{"columnName": "id", "dataType": "INTEGER"}],
 "rows": [[1]], "totalRows": 1, "truncated": false}
```
`paging.enabled=true`の場合は常に`pageSize`以下、`false`の場合は常に`max-result-rows`以下
（超過時のみ`truncated=true`、P6）。成功のたびに`QueryHistory`・`AuditLog`
（`eventType=QUERY_EXECUTED`）が各1件記録される（P7、副作用のためレスポンスボディには
現れない）。読み取り専用違反・パラメータ不足は`ValidationException`（400）。

### `POST "/saved/{savedQueryId}"`（保存済みクエリ実行、GEN-11）

リクエスト（`SavedExecutionRequest`、item 5-2で新規生成。`connectionId`は
`SavedQuery.connectionId`との一致を`QueryExecutionService`内で検証する防御的二重チェックに
用いる——Code Generation時点で確定する事項1）:
```json
{"connectionId": 42, "params": {}, "paging": {"enabled": true, "page": 0, "pageSize": 50}}
```
`QueryExecutionService.executeSavedQuery(userId, connectionId, savedQueryId, params, paging)`を
呼び出す。`retired=true`の保存済みクエリは`EntityNotFoundException`（404、P2）。`connectionId`が
`SavedQuery.connectionId`と一致しない場合は`ValidationException`（400）。成功時`200 OK`で
`QueryResult`（`/adhoc`と同一形状）。成功のたびに`SavedQuery.executionCount`がちょうど1
インクリメントされ（P8）、増加後の値が`QueryHistory.executionCount`に記録される。

## `QueryHistoryController`（`/api/query-history`）

### `GET ""`（実行履歴一覧、GEN-15）

クエリパラメータ: `connectionId`（必須）、`executedAtFrom`/`executedAtTo`（省略可、ISO-8601
`Instant`）、`executorScope`（省略時`ALL`、`SELF`で自分の実行分のみ）、`sqlTextSearch`（省略可、
`sql`列への部分一致）、`page`/`pageSize`（省略時`0`/内部既定値）。
`QueryHistoryService.listHistory(userId, connectionId, criteria, page)`を呼び出す。成功時
`200 OK`で`PageResult<HistoryEntry>`:
```json
{"content": [{"id": 100, "userId": 1, "connectionId": 42, "sql": "SELECT 1", "params": {},
              "resultCount": 1, "elapsedMillis": 10, "executedAt": "2026-07-13T10:00:00Z",
              "savedQueryId": 10, "savedQueryName": "q1", "executionCount": 1,
              "retired": false, "masked": false}],
 "totalCount": 1, "page": 0, "pageSize": 50}
```
`savedQueryId`が非nullかつ実行者が自分以外の行のうち、参照先`SavedQuery`が非公開または廃止済み
可視性条件を満たさない場合、`sql`/`savedQueryName`/`params`が
`"(非公開のため表示できません)"`/`{}`へ置き換えられる（`masked=true`、P9）。「廃止済み」バッジ
（`retired`）はマスキングとは独立に、参照先`SavedQuery.retired`の値のみで決まる（P10）。

## エラーレスポンス

`GlobalExceptionHandler`（`@RestControllerAdvice`、U1既存＋U2〜U6で追記済み）が全コントローラ
共通で以下にマッピングする。本ユニットは新規の例外クラスを追加していない（Code Generation時点で
確定する事項4）。

| 例外 | HTTPステータス | `error` |
|---|---|---|
| 未認証（`SecurityConfig`のstateless認証チェーンで拒否） | `401 Unauthorized` | （`RestAuthenticationEntryPoint`が`ErrorResponse`を返却） |
| `ValidationException`（読み取り専用違反、パラメータ不足、`connectionId`不一致） | `400 Bad Request` | `VALIDATION_ERROR` |
| `PermissionDeniedException`（可視性条件を満たさない`getQuery`、所有者以外の`updateQuery`/`retireQuery`） | `403 Forbidden` | `PERMISSION_DENIED` |
| `EntityNotFoundException`（存在しない/廃止済みの`savedQueryId`、存在しない`connectionId`） | `404 Not Found` | `ENTITY_NOT_FOUND` |
| その他未捕捉の`Exception` | `500 Internal Server Error` | `INTERNAL_ERROR`（メッセージは固定文言、詳細はサーバーログにのみ出力） |

エラーボディ共通形状（`ErrorResponse`）:
```json
{"error": "VALIDATION_ERROR", "message": "Missing parameter: id"}
```

**既知の課題**: なし。

## 変更要求（2026-07-15）: クエリ実行時スキーマ指定

### `GET "/{connectionId}/schemas"`（新規、スキーマ選択UI用、`QueryExecutionController`）

ボディなし。`QueryExecutionService.listAccessibleSchemas(userId, connectionId)`
（`EffectivePermissionResolver`への単純委譲）を呼び出す。成功時`200 OK`で`List<String>`:
```json
["PUBLIC", "SALES"]
```

### `POST "/adhoc"`・`POST "/saved/{savedQueryId}"`の改訂

`AdhocExecutionRequest`/`SavedExecutionRequest`に`schema`フィールドを追加した（必須）:
```json
{"connectionId": 42, "schema": "PUBLIC", "sql": "SELECT * FROM tbl WHERE id = :id",
 "params": {"id": 1}, "paging": {"enabled": false, "page": 0, "pageSize": 0}}
```
`executeAdhocSql`/`executeSavedQuery`は実行前に`schema`が`listAccessibleSchemas`の許可リストに
含まれることを検証し、含まれない場合は`PermissionDeniedException`（403）を返す。

### `GET ""`（`/api/query-history`）のレスポンス改訂

`HistoryEntry`に`schema`フィールドを追加した:
```json
{"content": [{"id": 100, "userId": 1, "connectionId": 42, "schema": "PUBLIC", "sql": "SELECT 1",
              "params": {}, "resultCount": 1, "elapsedMillis": 10,
              "executedAt": "2026-07-13T10:00:00Z", "savedQueryId": 10, "savedQueryName": "q1",
              "executionCount": 1, "retired": false, "masked": false}],
 "totalCount": 1, "page": 0, "pageSize": 50}
```

### エラーレスポンス表への追加

| 例外 | HTTPステータス | `error` |
|---|---|---|
| `PermissionDeniedException`（許可リストに含まれない`schema`） | `403 Forbidden` | `PERMISSION_DENIED` |

## テストカバレッジ（Step 6）

| テストクラス | 検証内容 |
|---|---|
| `SavedQueryControllerTest` | 5エンドポイント（一覧・保存・取得・更新・廃止）それぞれについて認証済みユーザ成功系・未認証401を検証（本ユニットは管理者ロール制約を持たないため403系テストは対象外、example-based、10件） |
| `QueryExecutionControllerTest` | 2エンドポイント（手入力SQL実行・保存済みクエリ実行）の成功系・未認証401に加え、読み取り専用違反SQLでの400（`ValidationException`経由）を検証（example-based、5件） |
| `QueryHistoryControllerTest` | 1エンドポイント（履歴一覧）の成功系・未認証401を検証（example-based、2件） |

P1〜P10（業務ロジックの性質）はController層では再検証せず、`business-logic-summary.md`記載の
jqwik `@Property`テスト（`SavedQueryServiceTest`/`ReadOnlySqlValidatorTest`/
`SqlParamDetectorTest`/`QueryExecutionServiceTest`/`QueryHistoryServiceTest`）に一元化している。