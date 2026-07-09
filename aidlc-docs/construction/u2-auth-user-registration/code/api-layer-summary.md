# U2 Auth & User Registration - APIレイヤサマリ

Step 5（APIレイヤ生成）・Step 6（APIレイヤ単体テスト）で生成したエンドポイントの一覧。

## エンドポイント一覧

| パス | メソッド | 認可要件 | Controller |
|---|---|---|---|
| `/api/auth/login` | POST | 不要（`SecurityConfig`の`/api/auth/**` → `permitAll()`） | `AuthController#login` |
| `/api/auth/refresh` | POST | 不要（同上） | `AuthController#refresh` |
| `/api/auth/logout` | POST | 不要（同上。リフレッシュトークン自体が認可の代替） | `AuthController#logout` |
| `/api/registrations` | POST | 不要（`SecurityConfig`で明示的に`permitAll()`） | `RegistrationController#requestRegistration` |
| `/api/registrations/complete` | POST | 不要（同上） | `RegistrationController#completeRegistration` |
| `/api/registrations/pending` | GET | 認証必須 + `ROLE_ADMIN` | `RegistrationController#listPendingUsers` |
| `/api/registrations/{userId}/approve` | POST | 認証必須 + `ROLE_ADMIN` | `RegistrationController#approveUser` |
| `/api/registrations/{userId}/reject` | POST | 認証必須 + `ROLE_ADMIN` | `RegistrationController#rejectUser` |

いずれも認可の強制は`SecurityConfig`の`authorizeHttpRequests`に委ね、Controller側での二重チェックはしない。

## `AuthController`（`/api/auth`）

### `POST /login`

リクエスト（`LoginRequest`）:
```json
{"email": "user@example.com", "password": "password"}
```
`AuthenticationService.login(email, password)`を呼び出す。成功時`200 OK`で`AuthToken`（`AuthenticationFailedException`失敗時は下記エラーレスポンス参照）:
```json
{"accessToken": "...", "refreshToken": "..."}
```

### `POST /refresh`

リクエスト（`RefreshRequest`）:
```json
{"refreshToken": "..."}
```
`AuthenticationService.refresh(refreshToken)`を呼び出す（内部で`RefreshTokenService.rotate`によるreuse detectionを実施）。成功時`200 OK`で新しい`AuthToken`。無効/reuse検知時は`InvalidTokenException`。

### `POST /logout`

リクエスト（`LogoutRequest`）:
```json
{"refreshToken": "..."}
```
`AuthenticationService.logout(refreshToken)`を呼び出し、成功時`204 No Content`（ボディなし）。

## `RegistrationController`（`/api/registrations`）

### `POST ""`（登録申請）

リクエスト（`RequestRegistrationRequest`）:
```json
{"email": "user@example.com"}
```
`UserRegistrationService.requestRegistration(email)`を呼び出す。列挙攻撃対策（P1）により既存状態によらず常に`204 No Content`（例外を投げない）。

### `POST /complete`（登録完了）

リクエスト（`CompleteRegistrationRequest`）:
```json
{"token": "...", "password": "..."}
```
`UserRegistrationService.completeRegistration(token, password)`を呼び出す。成功時`204 No Content`。トークン無効時は`TokenExpiredException`/`TokenNotFoundException`。

### `GET /pending`（承認待ち一覧）

`UserRegistrationService.listPendingUsers()`を呼び出す。成功時`200 OK`で`List<PendingUserSummary>`:
```json
[{"id": 1, "email": "pending@example.com", "createdAt": "2026-07-09T00:00:00Z"}]
```

### `POST /{userId}/approve`・`POST /{userId}/reject`

ボディなし。`Authentication.getPrincipal()`（`JwtAuthenticationFilter`が設定する`Long`型のユーザーID）を`adminUserId`として`UserRegistrationService.approveUser(adminUserId, userId)`/`rejectUser(...)`に渡す。成功時`204 No Content`。`PENDING_APPROVAL`以外の対象への実行は`InvalidUserStateException`（409）、存在しない`userId`は`EntityNotFoundException`（404）。

## エラーレスポンス

`GlobalExceptionHandler`（`@RestControllerAdvice`、U1既存＋本ユニットで5例外を追記）が全コントローラ共通で以下にマッピングする:

| 例外 | HTTPステータス | `error` |
|---|---|---|
| 未認証（`SecurityConfig`のstateless認証チェーンで拒否） | `401 Unauthorized` | （`RestAuthenticationEntryPoint`が`ErrorResponse`を返却） |
| `ROLE_ADMIN`以外での認可失敗（`SecurityConfig`の`hasRole`で拒否） | `403 Forbidden` | （`RestAccessDeniedHandler`が`ErrorResponse`を返却） |
| `AuthenticationFailedException` | `401 Unauthorized` | `AUTHENTICATION_FAILED` |
| `InvalidTokenException` | `401 Unauthorized` | `INVALID_TOKEN` |
| `TokenExpiredException` | `400 Bad Request` | `TOKEN_EXPIRED` |
| `TokenNotFoundException` | `404 Not Found` | `TOKEN_NOT_FOUND` |
| `InvalidUserStateException` | `409 Conflict` | `INVALID_USER_STATE` |
| その他未捕捉の`Exception` | `500 Internal Server Error` | `INTERNAL_ERROR`（メッセージは固定文言、詳細はサーバーログにのみ出力） |

エラーボディ共通形状（`ErrorResponse`）:
```json
{"error": "AUTHENTICATION_FAILED", "message": "Invalid email or password"}
```

## テストカバレッジ（Step 6）

| テストクラス | 検証内容 |
|---|---|
| `AuthControllerTest` | ログイン成功/失敗（401）、リフレッシュ成功/失敗（401、無効・reuse detectionは`InvalidTokenException`に集約されるため単一ケースで代表）、ログアウト成功（204）（example-based、5件） |
| `RegistrationControllerTest` | 登録申請・登録完了の未認証アクセス（204）、承認待ち一覧の管理者成功（200）/非管理者403/未認証401、承認・却下の管理者成功（204、`Authentication.getPrincipal()`から`adminUserId`を取得しサービスに渡すことを検証）/非管理者403/未認証401（example-based、9件） |

P1〜P13（業務ロジックの性質）はController層では再検証せず、`business-logic-summary.md`記載のjqwik `@Property`テスト（`UserRegistrationServiceTest`等）に一元化している。