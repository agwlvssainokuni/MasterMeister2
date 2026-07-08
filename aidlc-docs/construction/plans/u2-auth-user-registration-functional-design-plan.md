# U2: Auth & User Registration — Functional Design Plan

## Step 1: ユニットコンテキスト分析

- **ユニット定義**（`unit-of-work.md`）: バックエンドパッケージ `auth`, `userregistration`。
  フロントエンド `features/auth/`, `features/userRegistration/`。対応ストーリー
  MVP-1〜MVP-6。責務: メールアドレス起点の2段階ユーザ登録（申請→確認→パスワード設定→
  管理者承認/却下）と、承認済みユーザの認証（JWT発行・検証・リフレッシュ・ログアウト）。
  主要コンポーネント: `UserRegistrationService`, `RegistrationTokenService`,
  `AuthenticationService`, `JwtTokenProvider`, `AuthenticatedPrincipal`。
  U1（`common`, `audit`, `mail`）に依存。
- **対応ストーリー**（`unit-of-work-story-map.md` / `stories.md`）:
  - MVP-1: メールアドレスによる登録申請（列挙攻撃対策必須）
  - MVP-2: 確認メールのリンクからパスワードを設定して登録完了
  - MVP-3: 登録完了ユーザ一覧の確認（管理者）
  - MVP-4: ユーザの承認/却下（管理者）
  - MVP-5: 承認/却下結果のメール通知
  - MVP-6: ログイン（JWT発行）
- **既存の確定事項**（Application Design / U1 NFR Requirementsから継承、再検討不要）:
  - `services.md` フロー1に登録〜承認〜ログインの全体シーケンスが既に定義済み
    （`UserRegistrationService`/`RegistrationTokenService`/`AuthenticationService`/
    `JwtTokenProvider`/`MailService`/`AuditLogService`の呼び出し関係）。
  - `component-methods.md`に`AuthenticationService`/`JwtTokenProvider`/
    `UserRegistrationService`/`RegistrationTokenService`のメソッドシグネチャが定義済み。
  - JWT方式（U1 NFR Requirements 1.1）: HS256、アクセストークン有効期限10分
    （ステートレス、`SecurityConfig`が検証）、リフレッシュトークン有効期限24時間
    （内部DB永続化、single-use rotating、再利用検知時は全セッション強制失効）。
    U1/U2責務境界: U1=アクセストークン検証フィルタチェーンのみ、
    U2=トークン発行・リフレッシュトークンの永続化/検証/ローテーション/失効。
  - パスワードポリシー（requirements.md 3.4）: bcrypt等の標準的ハッシュ化のみ、強度ポリシーなし。
  - 登録トークン有効期限（`docs/REQUIREMENTS.md` 5.1）: デフォルト3時間、
    設定キーはQ5でDuration形式`mm.app.user-registration.token-expiry`（デフォルト`3h`）に確定。
  - `SecurityConfig`は既に`/api/auth/**`をpermitAllとして構成済み（U1、Step 6生成）。
    登録申請系エンドポイントの認可設定はU2側で追加が必要（具体的パスはCode Generationで決定）。

## Step 2-4: 計画・質問

以下7問について回答をお願いします。各質問には推奨案（A）を用意していますが、
別の選択肢や自由記述でも構いません。

---

### Q1. Business Logic Modeling — Userレコード作成タイミング（状態遷移モデル）

`requestRegistration(email)`実行時点で`User`レコードを作成するか、それとも
`completeRegistration`（パスワード設定）成功まで`User`レコードを作らないか。

- **A（推奨）**: `completeRegistration`成功時に初めて`User`レコードを作成する
  （状態=`PENDING_APPROVAL`）。`requestRegistration`〜`completeRegistration`間の
  一時状態は`RegistrationToken`エンティティ（メールアドレス+トークン+有効期限）のみで管理し、
  `User`テーブルには「実在するアカウント」のみを保持する。列挙攻撃対策の判定
  （メール既存チェック）は「`User`に同一メールが存在するか」+「有効な`RegistrationToken`が
  存在するか」の両方を見て行う。
- **B**: `requestRegistration`時点で`User`レコードを仮作成する（状態=`UNCONFIRMED`）。
  `completeRegistration`成功時に`PENDING_APPROVAL`へ遷移する。`User`テーブルが
  登録プロセス全体のトラッキングを一元化できる一方、パスワード未設定の「空」レコードが
  残る。
- **C**: その他（自由記述）

[Answer]: A

---

### Q2. Domain Model — トークンの形式と保存方式

`RegistrationToken`（登録確認トークン）と`RefreshToken`（リフレッシュトークン）を
どのような形式で生成し、DBにどう保存するか。

- **A（推奨）**: どちらも`SecureRandom`由来のURL-safe base64エンコード文字列
  （JWTではない不透明トークン）として生成する。DBには**ハッシュ値**（SHA-256等）のみを
  保存し、平文トークンはレスポンス（メールリンク/Cookie等）でのみ返す
  （DB漏洩時の悪用防止。アクセストークンと異なり内部DB照会が前提のステートフルな
  トークンのため）。
- **B**: 平文のままDBに保存する（実装をシンプルにする代わりに、DB漏洩時のリスクが残る）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q3. Business Rules — 登録申請の再送信（同一メールアドレスへの重複リクエスト）

有効期限内の`RegistrationToken`が既に存在するメールアドレスに対して、再度
`requestRegistration`が呼ばれた場合の挙動。

- **A（推奨）**: 既存の有効なトークンを無効化し、新しいトークンを発行してメールを
  再送する（ユーザが確認メールを紛失した場合の再送手段として機能する）。列挙攻撃対策上、
  レスポンスは初回と同一。
- **B**: 何もせず（新トークンを発行せず）、初回と同一のレスポンスのみ返す（冪等）。
  メールは再送されない。
- **C**: その他（自由記述）

[Answer]: A

---

### Q4. Business Rules — 却下されたユーザーの再登録可否

管理者に却下（`rejectUser`）されたメールアドレスが、再度`requestRegistration`を
実行した場合の挙動。

- **A（推奨）**: 却下された`User`レコードは「既存ユーザ」として扱い、再登録不可
  （列挙攻撃対策のレスポンスは変えず、内部的にはメール送信・トークン発行を行わない）。
  再登録を許可する場合は管理者による明示的な操作（本ユニットのスコープ外）を要する。
- **B**: 却下された`User`レコードを削除（または再登録可能な状態に戻す）し、
  再度登録フローを最初からやり直せるようにする。
- **C**: その他（自由記述）

[Answer]: A

---

### Q5. Data Flow — トークン有効期限の設定キー確定

登録確認トークンとリフレッシュトークンの有効期限設定キー名を確定する
（`docs/REQUIREMENTS.md`/U1 NFR Requirementsで大枠は決定済み、キー名の最終確認）。

- **A（推奨）**: `mm.app.user-registration.token-expiry`（デフォルト`3h`、Duration形式。
  U1が導入した`mm.app.jwt.access-token-expiry`=`10m`と命名・形式を揃える）、
  `mm.app.jwt.refresh-token-expiry`（デフォルト`24h`、同じくDuration形式）。
- **B**: 別の命名規則を採用する（自由記述）。

[Answer]: A（登録トークンの設定キーもDuration形式とする。`mm.app.user-registration.token-expiry-hours`ではなく
`mm.app.user-registration.token-expiry`（デフォルト`3h`）とする）

---

### Q6. Error Handling — ログイン失敗時の監査ログ記録

存在しないメールアドレスでのログイン失敗時、`AuditLogService.record(...)`の
`userId`フィールドをどう扱うか（`AuditLog.userId`は`Long`、U1で定義済み）。

- **A（推奨）**: `userId`は`null`とし、`targetDescription`または`summaryMessage`に
  試行されたメールアドレスを記録する（存在しないユーザのIDは採番できないため）。
  パスワード誤りによる失敗（ユーザは存在する）の場合は`userId`を設定する。
- **B**: 存在しないメールアドレスでの失敗は監査ログに記録しない（存在するユーザの
  失敗のみ記録）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q7. Frontend Components — `auth/`・`userRegistration/`機能のコンポーネント構成

U1の`frontend-components.md`と同様の粒度で、本ユニットのフロントエンド機能を
どこまで詳細に設計するか。

- **A（推奨）**: 以下の画面・コンポーネントを設計する。
  - `features/auth/`: `LoginPage`（メール+パスワード入力、ログイン失敗エラー表示）、
    `authApi.ts`（`login`/`logout`/`refresh`）。U1の`authStore`/`useAuth`
    （既存）と統合する。
  - `features/userRegistration/`: `RegistrationRequestPage`（メールアドレス入力、
    送信後は列挙攻撃対策上「送信しました」の一律メッセージ表示）、
    `PasswordSetupPage`（URLのトークンパラメータを読み取り、有効期限切れ/無効時は
    エラー表示+再申請導線、パスワード入力フォーム）、
    `PendingUsersPage`（管理者向け、登録完了ユーザ一覧+承認/却下ボタン、
    U1の`DataTable`/`ConfirmDialog`を再利用）。
  - `AppRouter.tsx`（U1で新設済み）にログイン・登録関連のパブリックルートを追加する。
- **B**: 別の粒度・構成を希望する（自由記述）。

[Answer]: A

---

## Step 5: 回答分析

全7問について回答を受領（Q1〜Q4・Q6・Q7=A、Q5=Aだが登録トークンの設定キーは
Duration形式`mm.app.user-registration.token-expiry`（デフォルト`3h`）に調整）。
整合性確認:

- Q1（`completeRegistration`成功時に`User`作成）とQ4（却下された`User`は既存扱いで
  再登録不可）は矛盾しない — 却下は「`User`が存在する」ことが前提の操作であり、
  Q1のモデルと整合する。
- Q2（トークンはハッシュ化して保存）はQ3（再送信時に旧トークンを無効化し新トークンを
  発行）と組み合わせても問題なし — 無効化は「ハッシュ値照合で該当レコードを
  失効済みにする」操作として実装可能。
- Q5の調整（Duration形式への統一）はQ1〜Q4・Q6・Q7のいずれとも独立しており、
  他の回答に影響しない。
- Q6（`userId`は不明メールの場合`null`、パスワード誤りの場合は設定）はQ1の
  `User`作成モデルと整合（`User`が存在しないなら`userId`を採番できないのは当然）。
- Q7（フロントエンド構成）は既存の`AppRouter.tsx`（U1で新設済み）にルート追加する
  前提で、バックエンドの決定（Q1〜Q6）と技術的な矛盾なし。

曖昧・矛盾点なし。Step 6（成果物生成）に進む。

## Step 6: 成果物生成チェックリスト

- [ ] `aidlc-docs/construction/u2-auth-user-registration/functional-design/domain-entities.md`
- [ ] `aidlc-docs/construction/u2-auth-user-registration/functional-design/business-rules.md`
- [ ] `aidlc-docs/construction/u2-auth-user-registration/functional-design/business-logic-model.md`
  （PBT-01: テスト可能な性質セクションを含む）
- [ ] `aidlc-docs/construction/u2-auth-user-registration/functional-design/frontend-components.md`