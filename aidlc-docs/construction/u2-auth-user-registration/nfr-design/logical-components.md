# logical-components.md — U2: Auth & User Registration

`nfr-design-patterns.md`に基づく論理コンポーネント一覧。

---

## 1. Security / Auth（`cherry.mastermeister.auth`想定）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `SecurityConfig`（U1既存、拡張） | `@Configuration` | `PasswordEncoder` Bean（BCrypt、`mm.app.security.password-encoder-strength`で強度指定）を追加する |
| `OpaqueTokenGenerator` | Component | `generate(): String`（32バイトSecureRandom、URL-safe base64）、`hash(String): String`（SHA-256）。`RegistrationToken`/`RefreshToken`の双方から利用される |
| `AdminBootstrapRunner` | `ApplicationRunner` | 起動時、`mm.app.admin.bootstrap.email`/`password`が設定済みかつ`role = ADMIN`の`User`が0件の場合のみ、bcryptハッシュ化した1件のADMIN Userを作成する（冪等） |
| `RefreshToken` | JPA Entity | `userId`, `familyId`, `tokenHash`（`@Column(unique = true)`）, `expiresAt`, `rotatedAt`, `revokedAt`, `createdAt` |
| `RefreshTokenService` | Service | リフレッシュトークンの発行・検証・ローテーション・reuse detection時の一括失効（`domain-entities.md`参照）。`OpaqueTokenGenerator`を利用 |
| `AuthenticationService` | Service | ログイン（`login`）、ログアウト（`logout`）。認証成功時は`JwtTokenProvider`（U1責務境界）でアクセストークンを発行し、`RefreshTokenService`でリフレッシュトークンを発行する |

---

## 2. User Registration（`cherry.mastermeister.userregistration`想定）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `RegistrationToken` | JPA Entity | `email`, `tokenHash`（`@Column(unique = true)`）, `expiresAt`, `invalidatedAt`, `consumedAt`, `createdAt` |
| `RegistrationTokenService` | Service | `validate(token)`（`VALID`/`EXPIRED`/`NOT_FOUND`判定）、トークン発行・無効化。`OpaqueTokenGenerator`を利用 |
| `UserRegistrationService` | Service | `requestRegistration`/`completeRegistration`/`approveUser`/`rejectUser`/`listPendingUsers`（`business-rules.md` 1節） |

---

## 3. Frontend（`features/auth/`, `features/userRegistration/`）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `authStore`（U1新設、U2で拡張） | zustand store + `persist`ミドルウェア | `currentUser`, `token`（U1）、`refreshToken`（U2追加）を保持。`persist`ミドルウェアで`sessionStorage`（`createJSONStorage(() => sessionStorage)`）に自動同期する |
| `setTokens(accessToken, refreshToken)` | `authStore`アクション | ログイン成功時・トークンリフレッシュ成功時に呼び出す。状態変更が`persist`経由で自動的に`sessionStorage`へ反映される |
| `clearTokens()` | `authStore`アクション | ログアウト時・リフレッシュ失敗時・reuse detection検知時に呼び出す。`persist`ミドルウェアが`sessionStorage`からも自動的に削除する |

---

## 4. 設定ファイル

| ファイル | 内容 |
|---|---|
| `application.yml` | `mm.app.security.password-encoder-strength`（既定`10`）、`mm.app.user-registration.token-expiry`（既定`3h`）、`mm.app.jwt.refresh-token-expiry`（既定`24h`）、`mm.app.admin.bootstrap.email`/`mm.app.admin.bootstrap.password`（既定未設定） |

---

## 5. U1/U2責務境界の再確認

- U1の`SecurityConfig`はアクセストークン検証フィルタチェーンを担当し、本ユニットで
  `PasswordEncoder` Beanを追加する形で拡張する（新しい`@Configuration`クラスは作らない）。
- U1の`JwtTokenProvider`（アクセストークン発行、U1 NFR Requirements 1.1で確定）と本ユニットの
  `OpaqueTokenGenerator`（`RegistrationToken`/`RefreshToken`という非JWTトークンの生成）は
  明確に役割が異なるコンポーネントである。