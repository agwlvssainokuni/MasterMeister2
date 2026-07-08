# domain-entities.md — U2: Auth & User Registration

`u2-auth-user-registration-functional-design-plan.md`（回答Q1〜Q7）に基づくドメインモデル。
内部DB（JPA）で永続化するエンティティのみを扱う（JWTアクセストークン自体はステートレスで
永続化しない。U1の`JwtClaims`参照）。

---

## userregistration / auth 共有ドメイン

### User（利用者アカウント）

`completeRegistration`成功時に初めて作成される（Question 1 = A）。`requestRegistration`〜
`completeRegistration`間はこのテーブルに行を持たない。

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | 主キー |
| `email` | String（unique, not null） | ログインID。大文字小文字を区別しない一意制約とする |
| `passwordHash` | String（not null） | bcryptハッシュ（Question 3.4、強度ポリシーなし） |
| `role` | `Role`（enum, not null） | 既定`USER`。`ADMIN`は自己登録経路では設定されない（本ドキュメント末尾「設計判断」参照） |
| `status` | `UserStatus`（enum, not null） | 既定`PENDING_APPROVAL` |
| `createdAt` | `java.time.Instant` | `completeRegistration`成功時刻 |
| `decidedAt` | `java.time.Instant`（nullable） | 承認/却下操作の時刻（`approveUser`/`rejectUser`） |

### Role（enum）
- `ADMIN`
- `USER`

（`requirements.md` 3.2: ロールは管理者／一般ユーザの2種類のみ）

### UserStatus（enum、Question 1 = A の状態遷移）
- `PENDING_APPROVAL`（`completeRegistration`成功直後の初期状態）
- `APPROVED`（`approveUser`実行後。ログイン可能になる唯一の状態）
- `REJECTED`（`rejectUser`実行後。ログイン不可、再登録も不可 — Question 4 = A）

状態遷移: `(行が存在しない)` → `PENDING_APPROVAL` → (`APPROVED` | `REJECTED`)。
`APPROVED`/`REJECTED`は終端状態であり、それ以降の遷移はない（`REJECTED`からの復帰は
管理者の直接的なデータ操作を要し、本ユニットのAPIスコープ外）。

---

## userregistration ドメイン

### RegistrationToken（登録確認トークン）

`requestRegistration`時に発行される一時的な確認トークン。`User`とは独立したライフサイクルを持つ
（Question 1 = A: `User`作成前の状態はこのエンティティのみで管理する）。

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | 主キー |
| `email` | String（not null） | 登録申請のメールアドレス |
| `tokenHash` | String（unique, not null） | 発行した不透明トークン（`SecureRandom`由来のURL-safe base64文字列）のSHA-256ハッシュ値（Question 2 = A）。平文トークンはDBに保存しない |
| `expiresAt` | `java.time.Instant`（not null） | 発行時刻 + `mm.app.user-registration.token-expiry`（既定`3h`、Question 5 = A） |
| `invalidatedAt` | `java.time.Instant`（nullable） | 再送信（Question 3 = A）により旧トークンを無効化した時刻 |
| `consumedAt` | `java.time.Instant`（nullable） | `completeRegistration`成功によりこのトークンを消費した時刻 |
| `createdAt` | `java.time.Instant` | 発行時刻 |

`RegistrationTokenService.validate(token)`の判定ロジック（`VALID`/`EXPIRED`/`NOT_FOUND`）:
- 該当`tokenHash`の行が存在しない → `NOT_FOUND`
- 存在するが`invalidatedAt`が設定済み、`consumedAt`が設定済み、または`expiresAt`が現在時刻より
  過去 → `EXPIRED`
- 上記以外 → `VALID`

---

## auth ドメイン

### RefreshToken（リフレッシュトークン）

U1 NFR Requirements 1.1/4.2で定義された、ステートフル・single-use rotating・
reuse-detection方式を実装するエンティティ。

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | 主キー |
| `userId` | Long（not null, FK: `User.id`） | 発行対象ユーザ |
| `familyId` | String（UUID文字列, not null） | 1回のログインで開始されたローテーション連鎖を識別するID。ローテーションで発行される新トークンも同じ`familyId`を引き継ぐ |
| `tokenHash` | String（unique, not null） | 不透明トークン（`SecureRandom`由来）のSHA-256ハッシュ値（Question 2 = A） |
| `expiresAt` | `java.time.Instant`（not null） | 発行時刻 + `mm.app.jwt.refresh-token-expiry`（既定`24h`、Question 5 = A） |
| `rotatedAt` | `java.time.Instant`（nullable） | このトークンを消費し次のトークンを発行した時刻（single-use rotation） |
| `revokedAt` | `java.time.Instant`（nullable） | 明示的ログアウト、または同一`familyId`内での再利用検知（reuse detection）による強制失効時刻 |
| `createdAt` | `java.time.Instant` | 発行時刻 |

有効性判定: `revokedAt`・`rotatedAt`がいずれも未設定かつ`expiresAt`が未来である行のみ
「有効なリフレッシュトークン」として扱う。既に`rotatedAt`が設定済みのトークンが再度
リフレッシュ要求に使われた場合は「再利用」とみなし、同一`familyId`の全行の`revokedAt`を
現在時刻で一括更新する（強制全セッション失効、U1 NFR Requirements 4.2）。

---

## 設計判断（AI提案、Q1〜Q7の対象外事項）

### 初期管理者アカウントのプロビジョニング

自己登録フロー（MVP-1〜MVP-6、`RegistrationRequestPage`〜`completeRegistration`）は
一貫して`role = USER`のアカウントのみを作成する（ストーリーの関連ペルソナは「一般ユーザ」であり、
自己登録経路で`ADMIN`ロールを選択できるUI/APIはドキュメント上どこにも存在しない）。
一方、`requirements.md`/`stories.md`のいずれにも初期管理者アカウントの作成手順は明記されていない
（Q1〜Q7の質問票作成時に見落とした事項であり、本来ならQ1〜Q7と同様に質問すべき内容だが、
既回答（Q1〜Q4・Q6・Q7 = A, Q5 = A+調整）のいずれとも矛盾しない独立した追加事項のため、
U1のThymeleaf提案（`business-rules.md` 2.1）と同様にAI提案として扱い、ここに明記する。

**提案**: 起動時（`ApplicationRunner`）に、`mm.app.admin.bootstrap.email`/
`mm.app.admin.bootstrap.password`設定値が存在し、かつ`User`テーブルに`role = ADMIN`の行が
1件も存在しない場合のみ、指定されたメールアドレス・パスワード（bcryptハッシュ化）で
`status = APPROVED`の`User`を1件作成する。設定値が未指定、またはADMIN行が既に存在する場合は
何もしない（冪等・べき等起動）。具体的なプロパティキー名・実装クラスはCode Generation段階で
確定する。この提案に異論があれば、U2 Functional Designのレビュー時に指摘可能。