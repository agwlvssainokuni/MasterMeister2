# business-logic-model.md — U2: Auth & User Registration

`domain-entities.md`・`business-rules.md`で定義したモデル・ルールに基づく業務フロー。
技術非依存（実装方式はNFR Design/Code Generationで確定）。

---

## フロー1: 登録申請（MVP-1）

**関与コンポーネント**: フロントエンド`userRegistration/` → `UserRegistrationService` →
`RegistrationTokenService` → `MailService`（U1）

1. 一般ユーザが`RegistrationRequestPage`でメールアドレスを入力し送信すると、
   `UserRegistrationService.requestRegistration(email)`が呼び出される。
2. `business-rules.md` 1.2の分岐に従い、新規メールアドレスまたは有効トークン再送信の場合のみ
   `RegistrationTokenService.issueToken(email, expiry)`（`expiry` =
   `mm.app.user-registration.token-expiry`、既定`3h`）でトークンを発行し、
   `MailService.send(MailNotificationType.REGISTRATION_CONFIRMATION, email, {リンクURL,
   有効期限})`を呼び出す（U1 `business-rules.md` 2.1のThymeleafテンプレート機構を使用）。
3. 既存の`PENDING_APPROVAL`/`APPROVED`/`REJECTED`ユーザの場合は2を実行しない。
4. いずれの場合も呼び出し元へは同一の成功結果を返す（`business-rules.md` 1.1、列挙攻撃対策）。

---

## フロー2: 登録完了（MVP-2）

**関与コンポーネント**: フロントエンド`userRegistration/` → `UserRegistrationService` →
`RegistrationTokenService`

1. 一般ユーザがメール内リンクを開くと、`PasswordSetupPage`がURLのトークンパラメータを
   保持した状態で表示される。
2. パスワード入力・送信時、`UserRegistrationService.completeRegistration(token, rawPassword)`
   が呼び出される。
3. 内部で`RegistrationTokenService.validate(token)`を実行し、`EXPIRED`/`NOT_FOUND`の場合は
   例外をスローする（`business-rules.md` 1.3）。フロントエンドはエラー表示＋
   `RegistrationRequestPage`への再申請導線を表示する（MVP-2 AC）。
4. `VALID`の場合、パスワードをbcryptハッシュ化し、`User`（`status = PENDING_APPROVAL`）を
   作成、対象`RegistrationToken.consumedAt`を更新する（同一トランザクション）。
5. フロントエンドは完了メッセージ（「管理者の承認をお待ちください」等）を表示する。

---

## フロー3: 管理者による承認/却下（MVP-3, MVP-4, MVP-5）

**関与コンポーネント**: フロントエンド`userRegistration/`（管理者向け） →
`UserRegistrationService` → `MailService`（U1） → `AuditLogService`（U1）

1. 管理者が`PendingUsersPage`を開くと、`UserRegistrationService.listPendingUsers()`が
   呼び出され、`status = PENDING_APPROVAL`のユーザ一覧（`createdAt`昇順）が表示される
   （MVP-3 AC: メールアドレス・登録完了日時を表示）。
2. 管理者が個別に承認/却下を実行すると、`UserRegistrationService.approveUser(adminUserId,
   targetUserId)` または `rejectUser(adminUserId, targetUserId)`が呼び出される
   （`business-rules.md` 1.4）。
3. `status`更新後、`MailService.send(REGISTRATION_APPROVED`または`REGISTRATION_REJECTED,
   対象ユーザのemail, variables)`を呼び出す（MVP-5、送信失敗は主処理を失敗させない）。
4. `AuditLogService.record(ADMIN_OPERATION, USER_REGISTRATION_APPROVED`または
   `USER_REGISTRATION_REJECTED, adminUserId, ..., targetDescription=対象ユーザのemail)`を
   呼び出す。
5. 却下されたユーザは以後ログイン不可（フロー4）、かつ同一メールアドレスでの再登録も
   不可（フロー1のフロー1、`business-rules.md` 1.2の`REJECTED`分岐）。

---

## フロー4: ログイン（MVP-6）

**関与コンポーネント**: フロントエンド`auth/` → `AuthenticationService` →
`JwtTokenProvider` → `AuditLogService`（U1）

1. `LoginPage`でメールアドレス・パスワードを入力し送信すると、
   `AuthenticationService.login(email, rawPassword)`が呼び出される。
2. `business-rules.md` 2.1の判定に従い、以下のいずれかで失敗する場合は
   `AuthenticationFailedException`をスローする前に`AuditLogService.record(AUTHENTICATION,
   LOGIN_FAILURE, ...)`を記録する:
   - 該当メールアドレスの`User`が存在しない（`userId = null`で記録）
   - `User`は存在するが`status != APPROVED`、またはパスワード不一致（`userId`を設定して記録）
3. 成功時: `JwtTokenProvider.generateToken(userId, role, accessTokenExpiry)`でアクセストークン
   （既定有効期限10分、U1 NFR Requirements 1.1）を発行し、新規`familyId`で`RefreshToken`を
   1件作成（既定有効期限24時間、`mm.app.jwt.refresh-token-expiry`）。
   `AuditLogService.record(AUTHENTICATION, LOGIN_SUCCESS, userId, ...)`を記録する。
4. アクセストークン・リフレッシュトークン（いずれも平文）をJSONレスポンスボディで返す
   （`business-rules.md` 2.1）。フロントエンドは`authStore`（U1で新設済み）に両トークンと
   ユーザ情報（`{ id, email, role }`）を保持する。

---

## フロー5: トークンリフレッシュ（U1 NFR Requirements 1.1/4.2）

**関与コンポーネント**: フロントエンド`api/apiClient`（U1） → `AuthenticationService`

1. アクセストークンの有効期限が近い、またはAPI呼び出しが401（トークン期限切れ）で
   失敗した場合、フロントエンドの共通APIクライアント（U1）が保持しているリフレッシュ
   トークンで`/api/auth/refresh`を呼び出す。
2. `business-rules.md` 2.2の判定を実行する:
   - 未検出/期限切れ/失効済み → 401を返し、フロントエンドは`authStore`をクリアして
     ログイン画面へリダイレクトする（U1 `business-logic-model.md` フロー4の401処理を再利用）。
   - **再利用検知**（既にローテーション済みのトークンが再送された）→ 同一`familyId`の
     全`RefreshToken`を強制失効し、401を返す（フロントエンド処理は上記と同様、強制
     再ログイン）。
   - 有効な未消費トークン → 消費済みにマークし、新しいアクセストークン・リフレッシュ
     トークンのペアを発行してレスポンスとする。フロントエンドは`authStore`を新しい
     トークンで更新する。

---

## フロー6: ログアウト

**関与コンポーネント**: フロントエンド`auth/` → `AuthenticationService` →
`AuditLogService`（U1）

1. ユーザがログアウト操作を行うと、`AuthenticationService.logout(rawToken)`
   （`rawToken` = リフレッシュトークン）が呼び出される。
2. 対象`RefreshToken`の`revokedAt`を現在時刻で設定する（同一`familyId`の他トークンには
   影響しない、`business-rules.md` 2.3）。
3. `AuditLogService.record(AUTHENTICATION, LOGOUT, userId, ...)`を記録する。
4. フロントエンドは`authStore`をクリアし、ログイン画面へ遷移する。

---

## テスト可能な性質（Testable Properties, PBT-01）

`property-based-testing`拡張（enabled）のRule PBT-01に基づき、本ユニットの業務ロジック
（フロー1〜6）が持つ性質をカテゴリ別に識別する。実際のPBTケース設計・生成器定義は
Code Generation計画時に確定する。

| # | 対象 | カテゴリ | 性質 | 備考 |
|---|---|---|---|---|
| P1 | `UserRegistrationService.requestRegistration`（フロー1） | Invariant（列挙攻撃対策） | 任意のメールアドレス（新規/`PENDING_APPROVAL`既存/`APPROVED`既存/`REJECTED`既存/有効トークン再送信のいずれの状態でも）に対し、呼び出し元へ返る結果は常に同一（例外を投げない、レスポンス形状が一定） | `business-rules.md` 1.1。任意の内部状態を生成器でカバーするInvariant |
| P2 | `RegistrationTokenService.issueToken`/`validate`（フロー1, 2） | Round-trip | 発行直後のトークンで`validate`を呼ぶと必ず`VALID`が返る。`consumedAt`/`invalidatedAt`のいずれかを設定した後は必ず`EXPIRED`が返る | `domain-entities.md`のトークン状態遷移ロジック |
| P3 | `RegistrationTokenService.issueToken`（フロー1） | Invariant | 同一メールアドレスに対する再送信（`business-rules.md` 1.2）実行後、旧トークンで`validate`すると必ず`EXPIRED`（`invalidatedAt`起因）、新トークンでは`VALID`が返る | Question 3 = A の直接的な性質化 |
| P4 | `UserRegistrationService.completeRegistration`（フロー2） | Invariant | `VALID`なトークンで呼び出すと必ず`User`が1件作成され`status = PENDING_APPROVAL`となる。`EXPIRED`/`NOT_FOUND`なトークンでは`User`が作成されない（例外のみ） | `business-rules.md` 1.3 |
| P5 | `UserRegistrationService.approveUser`/`rejectUser`（フロー3） | State machine（状態遷移網羅） | `status = PENDING_APPROVAL`の`User`に対してのみ実行可能で、実行後は必ず`APPROVED`または`REJECTED`（終端状態）に遷移する。既に終端状態の`User`への再実行は必ず例外となる | `domain-entities.md`のUserStatus遷移図、PBT-03該当（不変条件） |
| P6 | `UserRegistrationService.requestRegistration`（フロー1、`REJECTED`ユーザ） | Invariant | `status = REJECTED`の`User`が存在するメールアドレスに対しては、何度呼び出しても新規`RegistrationToken`が発行されない | Question 4 = A |
| P7 | `AuthenticationService.login`（フロー4） | Oracle（参照比較） | 認証成功は`status = APPROVED`かつパスワード一致の場合に限り真となる決定的関数（`status`×パスワード一致有無の全組み合わせに対する期待結果がオラクルとして定義できる） | `business-rules.md` 2.1。PBT-05該当 |
| P8 | `AuthenticationService.login`（フロー4） | Invariant（監査ログ記録） | ログイン失敗時は必ず`AuditLogService.record(LOGIN_FAILURE, ...)`が呼ばれる。存在しないメールでは`userId = null`、存在するユーザのパスワード不一致では`userId`が設定される、という条件分岐が任意の入力で常に成立する | Question 6 = A |
| P9 | `RefreshToken`ローテーション（フロー5） | Invariant（single-use） | 一度`rotatedAt`が設定された`RefreshToken`で再度リフレッシュを試みると、必ずreuse detectionが発動し、同一`familyId`の全行の`revokedAt`が設定される | U1 NFR Requirements 4.2。`domain-entities.md`のfamilyId設計 |
| P10 | `RefreshToken`ローテーション（フロー5） | Round-trip | 有効な未消費トークンでリフレッシュすると、新しい`RefreshToken`が同一`userId`・同一`familyId`で1件発行され、旧トークンは`rotatedAt`が設定され二度と有効にならない | フロー5 |
| P11 | `AuthenticationService.logout`（フロー6） | Invariant（影響範囲の限定） | ログアウトは指定したリフレッシュトークンの行のみを失効させ、同一`familyId`の他の行（他デバイスのセッション等）の`revokedAt`は変化しない | `business-rules.md` 2.3。P9（reuse detectionは全行に影響）との対比が明確になる性質 |

**PBT-09（フレームワーク選定）**: U1と同様`jqwik`（JUnit 5統合）を候補としてNFR Requirements
ステージで確定する。