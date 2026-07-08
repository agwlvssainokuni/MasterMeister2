# business-rules.md — U2: Auth & User Registration

`u2-auth-user-registration-functional-design-plan.md` の回答（Q1〜Q7）に基づく業務ルール定義。

---

## 1. ユーザ登録（userregistration）

### 1.1 列挙攻撃対策の一貫性（`requirements.md` 5.1、Question 1 = Aと整合）
`requestRegistration(email)`は、`email`が既存の`User`（任意の`status`）に一致する場合、
有効な`RegistrationToken`が既に存在する場合、未登録の新規メールアドレスである場合の
いずれにおいても、呼び出し元（Controller）へは常に同一の成功結果を返す。内部的な分岐
（トークン発行の有無、メール送信の有無）は以下の1.2〜1.4の通り異なるが、レスポンスには
一切反映しない。

### 1.2 `requestRegistration`の内部分岐
| 状況 | 内部動作 |
|---|---|
| 新規メールアドレス（`User`にも有効な`RegistrationToken`にも該当なし） | 新規`RegistrationToken`を発行し、確認メールを送信する |
| 有効な`RegistrationToken`が既に存在する（未確認・未失効） | 既存トークンの`invalidatedAt`を現在時刻で設定し、新規トークンを発行して確認メールを送信する（Question 3 = A、再送信として扱う） |
| `status = PENDING_APPROVAL`または`status = APPROVED`の`User`が既に存在する | トークンは発行せず、メールも送信しない（列挙攻撃対策上レスポンスのみ1.1のとおり同一） |
| `status = REJECTED`の`User`が既に存在する | トークンは発行せず、メールも送信しない（Question 4 = A、却下ユーザは既存ユーザとして再登録不可） |

### 1.3 登録完了（`completeRegistration`）
- `RegistrationTokenService.validate(token)`が`VALID`の場合のみ処理を継続する。
  `EXPIRED`/`NOT_FOUND`の場合は`TokenExpiredException`/`TokenNotFoundException`を
  スローする（`component-methods.md`既定）。
- 継続する場合: パスワードをbcryptでハッシュ化し、`User`（`role = USER`,
  `status = PENDING_APPROVAL`）を新規作成する（Question 1 = A）。同時に対象の
  `RegistrationToken.consumedAt`を現在時刻で設定する。
- `User`作成とトークンの`consumedAt`更新は同一トランザクションで行う（部分的な不整合
  ＝トークン消費済みだがユーザ未作成、を防ぐ）。

### 1.4 承認/却下（`approveUser`/`rejectUser`）
- 対象`User.status`が`PENDING_APPROVAL`である場合のみ実行可能とする（`APPROVED`/
  `REJECTED`への遷移は終端状態のため、既に決定済みのユーザへの再操作は
  `IllegalStateException`系の共通例外とする。具体的な例外型はCode Generationで確定）。
- `approveUser`: `status`を`APPROVED`、`decidedAt`を現在時刻に更新。
  `MailService.send(REGISTRATION_APPROVED, ...)`を呼び出し、
  `AuditLogService.record(ADMIN_OPERATION, USER_REGISTRATION_APPROVED, adminUserId, ...)`を
  呼び出す（U1 `domain-entities.md`のイベント種別と対応）。
- `rejectUser`: `status`を`REJECTED`、`decidedAt`を現在時刻に更新。
  `MailService.send(REGISTRATION_REJECTED, ...)`、
  `AuditLogService.record(ADMIN_OPERATION, USER_REGISTRATION_REJECTED, adminUserId, ...)`を
  呼び出す。
- いずれもU1の`business-rules.md` 2.3（メール送信失敗は主処理を失敗させない）を準用する。

### 1.5 一覧表示（`listPendingUsers`）
`status = PENDING_APPROVAL`の`User`のみを対象とする。並び順は`createdAt`昇順
（先に申請したユーザから処理できるようにする）。ページングは本ユニットのスコープでは
必須としない（MVP-3のACに件数上限の言及なし。将来的な件数増加時はU1の
`PageRequest`/`PageResult<T>`パターンを適用する）。

---

## 2. 認証（auth）

### 2.1 ログイン（`AuthenticationService.login`）
1. `email`に一致する`User`を検索する。
   - 存在しない場合: `AuthenticationFailedException`をスローする前に、
     `AuditLogService.record(AUTHENTICATION, LOGIN_FAILURE, userId=null, ..., 
     targetDescription=試行されたemail)`を記録する（Question 6 = A）。
   - `status != APPROVED`（`PENDING_APPROVAL`/`REJECTED`）の場合: 存在しないメールアドレスと
     同様に失敗として扱う。`userId`は当該`User.id`を設定して記録する（`User`は実在するため）。
   - パスワード不一致の場合: `userId`は当該`User.id`を設定して`LOGIN_FAILURE`を記録する
     （Question 6 = A）。
2. 認証成功時（`status = APPROVED`かつパスワード一致）: `JwtTokenProvider.generateToken(userId,
   role, accessTokenExpiry)`でアクセストークンを発行し、`RefreshToken`エンティティを新規
   `familyId`（UUID新規生成）で1件作成する。`AuditLogService.record(AUTHENTICATION,
   LOGIN_SUCCESS, userId, ...)`を記録する。
3. レスポンスとして、アクセストークン（JWT文字列）とリフレッシュトークン（不透明トークン
   平文、DBにはハッシュのみ保存 — `domain-entities.md`参照）の両方をJSONボディで返す
   （Cookieは使用しない。`SecurityConfig`でCSRFが無効化されている構成と整合させ、
   追加のCSRF対策実装を不要にするための設計判断。Q1〜Q7の対象外だがAI提案として明記）。

### 2.2 トークンリフレッシュ（`/api/auth/refresh`、Code Generationで新設予定）
1. リクエストされたリフレッシュトークン（平文）のSHA-256ハッシュで`RefreshToken`を検索する。
2. 見つからない、または`revokedAt`/`expiresAt`超過の場合: 401相当のエラーとする。
3. `rotatedAt`が既に設定済み（＝一度消費済みのトークンの再利用）の場合: **reuse detection**
   と判定し、同一`familyId`の全`RefreshToken`行の`revokedAt`を現在時刻で一括更新する
   （U1 NFR Requirements 4.2）。エラーレスポンスを返し、フロントエンドは強制ログアウト
   （再ログインが必要）とする。
4. 上記いずれにも該当しない場合（有効な未消費トークン）: 対象行の`rotatedAt`を現在時刻に
   設定し、同一`userId`・同一`familyId`で新しい`RefreshToken`を1件発行する。新しいアクセス
   トークンも同時に発行し、両方をレスポンスとして返す。

### 2.3 ログアウト（`AuthenticationService.logout`）
- アクセストークンはステートレスのため明示的な失効はしない（`requirements.md` 3.1）。
- 送信されたリフレッシュトークンに対応する`RefreshToken`行の`revokedAt`を現在時刻で設定する
  （同一`familyId`の他行には影響しない — ログアウトは「このセッション」のみを終了させる操作で
  あり、reuse detection起因の全セッション強制失効とは別の意味を持つ）。
- `AuditLogService.record(AUTHENTICATION, LOGOUT, userId, ...)`を記録する。

---

## 3. 設定キー（Question 5 = A + 調整）

| キー | デフォルト値 | 形式 |
|---|---|---|
| `mm.app.user-registration.token-expiry` | `3h` | Duration |
| `mm.app.jwt.refresh-token-expiry` | `24h` | Duration |

`mm.app.jwt.access-token-expiry`（既定`10m`）はU1で導入済み。`docs/REQUIREMENTS.md` 5.1に
記載の`mm.app.user-registration.token-expiry-hours`（整数時間形式）は、U1の
`mm.app.jwt.access-token-expiry`とのDuration形式統一のため、本Functional Designにおいて
`mm.app.user-registration.token-expiry`（Duration形式）に置き換える（`docs/REQUIREMENTS.md`は
今後の更新対象とする）。

---

## 4. パスワードハッシュ化

bcrypt（Spring Securityの`PasswordEncoder`実装、具体的なコストファクタはCode Generationで
決定）を使用する。強度ポリシー（最低文字数・文字種混在等）は設けない（`requirements.md` 3.4）。

---

## 5. API認可（`SecurityConfig`、U1 NFR Design 1.3の規約に基づく）

U1 NFR Design 1.3で確定した「管理者専用APIはパスパターンごとに`hasRole("ADMIN")`を明示指定する」
方針に従い、本ユニットの管理者専用エンドポイントを以下のパターンで保護する（`/api/admin/**`の
ような包括プレフィクスは使わない。`GET /api/registrations/pending`
等は`POST /api/registrations`と同一の`registrations`リソースの一部であるため）。

| パスパターン | 対象 |
|---|---|
| `GET /api/registrations/pending` | `listPendingUsers` |
| `POST /api/registrations/*/approve` | `approveUser` |
| `POST /api/registrations/*/reject` | `rejectUser` |

`POST /api/registrations`（`requestRegistration`）・`POST /api/registrations/complete`
（`completeRegistration`）は`permitAll()`（列挙攻撃対策上、未認証ユーザからの呼び出しを前提と
するため）。正確なパスは`component-methods.md`のシグネチャに準拠し、`SecurityConfig`への
具体的な`requestMatchers()`追加はCode Generation段階で行う。