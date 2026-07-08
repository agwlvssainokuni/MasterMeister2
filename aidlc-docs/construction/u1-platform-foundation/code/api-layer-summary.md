# U1 Platform Foundation - APIレイヤサマリ

Step 5（APIレイヤ生成）・Step 6（APIレイヤ単体テスト）で生成したエンドポイントの一覧。

## エンドポイント一覧

| パス | メソッド | 認可要件 | Controller |
|---|---|---|---|
| `/api/audit-logs` | GET | 認証必須 + `ROLE_ADMIN`（`SecurityConfig`の`/api/audit-logs/**` → `hasRole("ADMIN")`により強制。Controller側での二重チェックはしない） | `AuditLogController#search` |

## `GET /api/audit-logs`

監査ログを検索条件・ページングで絞り込んで取得する。

### リクエスト（クエリパラメータ、すべて任意）

| パラメータ | 型 | 説明 |
|---|---|---|
| `dateFrom` | `Instant`（ISO-8601） | 発生日時の下限（含む） |
| `dateTo` | `Instant`（ISO-8601） | 発生日時の上限（含む） |
| `userId` | `Long` | 操作ユーザーID |
| `eventCategory` | `EventCategory` | `AUTHENTICATION` / `ADMIN_OPERATION` / `DATA_ACCESS` |
| `eventType` | `EventType` | `LOGIN_SUCCESS` / `LOGIN_FAILURE` / `LOGOUT` / `USER_REGISTRATION_APPROVED` / `USER_REGISTRATION_REJECTED` / `RDBMS_CONNECTION_CHANGED` / `SCHEMA_IMPORTED` / `GROUP_CHANGED` / `PERMISSION_CHANGED` / `PERMISSION_YAML_EXPORTED` / `PERMISSION_YAML_IMPORTED` / `LARGE_RECORD_READ` / `MASTER_DATA_MUTATION` / `QUERY_EXECUTED` |
| `page` | `int`（デフォルト`0`） | ページ番号（0始まり） |
| `pageSize` | `int`（デフォルト`0`＝`mm.app.audit.default-page-size`適用） | 1ページ件数。`mm.app.audit.page-size-options`の範囲に丸められる |

条件は全てAND結合。未指定の条件は絞り込みに使用しない（`AuditLogFilterCriteria`のnull許容フィールドとしてサービス層に渡す）。

### レスポンス（`200 OK`）

`PageResult<AuditLogResponse>`:

```json
{
  "content": [
    {
      "id": 1,
      "occurredAt": "2026-07-08T10:00:00Z",
      "userId": 42,
      "connectionId": null,
      "eventCategory": "ADMIN_OPERATION",
      "eventType": "USER_REGISTRATION_APPROVED",
      "result": "SUCCESS",
      "targetDescription": "target",
      "summaryMessage": "summary"
    }
  ],
  "totalCount": 1,
  "page": 0,
  "pageSize": 20
}
```

結果は`occurredAt`降順で固定ソートされる（並び替えパラメータなし）。

`AuditLogResponse`はJPAエンティティ`AuditLog`をそのまま返さず、専用レスポンスレコードとして`AuditLogResponse.from(AuditLog)`でマッピングする（entity-as-DTO漏洩の回避）。

### エラーレスポンス

`GlobalExceptionHandler`（`@RestControllerAdvice`）が全コントローラ共通で以下にマッピングする（Step 6 `GlobalExceptionHandlerTest`でP8として検証済み）:

| 例外 | HTTPステータス | `error` |
|---|---|---|
| 未認証（`SecurityConfig`のstateless認証チェーンで拒否） | `401 Unauthorized` | （`RestAuthenticationEntryPoint`が`ErrorResponse`を返却） |
| `ROLE_ADMIN`以外での認可失敗（`SecurityConfig`の`hasRole`で拒否） | `403 Forbidden` | （`RestAccessDeniedHandler`が`ErrorResponse`を返却） |
| `PermissionDeniedException` | `403 Forbidden` | `PERMISSION_DENIED` |
| `EntityNotFoundException` | `404 Not Found` | `ENTITY_NOT_FOUND` |
| `ValidationException` | `400 Bad Request` | `VALIDATION_ERROR` |
| その他未捕捉の`Exception` | `500 Internal Server Error` | `INTERNAL_ERROR`（メッセージは固定文言、詳細はサーバーログにのみ出力） |

エラーボディ共通形状（`ErrorResponse`）:

```json
{
  "error": "PERMISSION_DENIED",
  "message": "denied"
}
```

## テストカバレッジ（Step 6）

| テストクラス | 検証内容 |
|---|---|
| `AuditLogControllerTest` | フィルタなし検索（200）、フィルタあり検索（サービスへの条件伝播）、page/pageSize伝播、非管理者での403、未認証での401（example-based、5件） |
| `GlobalExceptionHandlerTest` | P8: 上記4種の業務例外→HTTPステータス/エラーコードのマッピングをjqwik `@Property`で網羅的に検証（`ExceptionKind`全4値） |