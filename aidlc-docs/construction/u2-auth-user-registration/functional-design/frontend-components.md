# frontend-components.md — U2: Auth & User Registration

`u2-auth-user-registration-functional-design-plan.md`（Question 7 = A）に基づく
`features/auth/`・`features/userRegistration/`のコンポーネント設計。U1の共通基盤
（`api/apiClient`, `store/authStore`, `hooks/useAuth`, `routes/AppRouter`・`ProtectedRoute`,
`components/DataTable`・`ConfirmDialog`・`ToastNotification`）を再利用する。

---

## features/auth/

### コンポーネント階層

```
LoginPage（AppRouter配下、未認証ユーザ向けパブリックルート）
└── （フォームのみ、子コンポーネントなし）
```

### LoginPage
- **状態**: `email: string`, `password: string`, `error: string | null`, `submitting: boolean`
- **責務**: メールアドレス・パスワード入力フォームを表示し、送信時に`authApi.login(email,
  password)`を呼び出す（`business-logic-model.md` フロー4）。成功時は`authStore`に
  アクセストークン・リフレッシュトークン・ユーザ情報（`{ id, email, role }`）を設定し、
  役割に応じたトップページへ遷移する。失敗時は`error`にエラーメッセージを設定して表示する
  （MVP-6 AC: 誤った認証情報でのログイン失敗表示）。
- **バリデーション**: メールアドレス形式・両フィールド必須のクライアント側チェックのみ
  （パスワード強度チェックはしない、`business-rules.md` 4節）。

### authApi.ts（`features/auth/api/`）
| 関数 | 対応API | 責務 |
|---|---|---|
| `login(email, password)` | `POST /api/auth/login` | アクセストークン・リフレッシュトークン・ユーザ情報を取得する |
| `refresh(refreshToken)` | `POST /api/auth/refresh` | `business-logic-model.md` フロー5。U1の`apiClient`が401検知時に内部的に呼び出す |
| `logout(refreshToken)` | `POST /api/auth/logout` | `business-logic-model.md` フロー6 |

### authStore（U1で新設済み、本ユニットで拡張）
U1定義の`authStore`（`currentUser`, `token`）に以下を追加する:
- `refreshToken: string | null`
- `setTokens(accessToken, refreshToken)` / `clearTokens()`（ログアウト・401時に使用）

### apiClient（U1、本ユニットとの連携点）
U1の`apiClient`が401レスポンスを受けた際、即座にログアウトするのではなく、まず
`authApi.refresh(authStore.refreshToken)`を1回試行し、成功すれば元のリクエストを
新しいアクセストークンで再試行する。リフレッシュ自体が失敗した場合にのみ、U1
`business-logic-model.md` フロー4の401処理（`authStore`クリア＋ログイン画面リダイレクト）を
実行する（`business-logic-model.md` フロー5）。

---

## features/userRegistration/

### コンポーネント階層

```
RegistrationRequestPage（パブリックルート）

PasswordSetupPage（パブリックルート、URLクエリパラメータでトークン受け渡し）

PendingUsersPage（ProtectedRoute requiredRole="ADMIN"配下）
└── PendingUsersTable（DataTableを利用）
    └── （承認/却下ボタン、ConfirmDialogで確認）
```

### RegistrationRequestPage
- **状態**: `email: string`, `submitted: boolean`, `submitting: boolean`
- **責務**: メールアドレス入力フォームを表示し、送信時に`userRegistrationApi.
  requestRegistration(email)`を呼び出す。送信結果は`business-rules.md` 1.1（列挙攻撃対策）
  により常に成功として扱われるため、`submitted = true`にして「確認メールを送信しました
  （該当するメールアドレスの場合）」という一律メッセージを表示する（MVP-1 AC、既存/未登録の
  判別につながる文言を出さない）。

### PasswordSetupPage
- **状態**: `token: string`（URLクエリパラメータから取得）, `password: string`,
  `confirmPassword: string`, `tokenStatus: 'checking' | 'valid' | 'invalid'`,
  `error: string | null`, `completed: boolean`
- **責務**: マウント時にトークンの有効性を暗黙的に扱う（明示的な事前チェックAPIは持たず、
  `completeRegistration`呼び出し自体の成否で判定する）。パスワード入力・送信時に
  `userRegistrationApi.completeRegistration(token, password)`を呼び出す。成功時は
  `completed = true`にして「登録完了、管理者の承認をお待ちください」を表示する
  （MVP-2 AC）。`TokenExpiredException`/`TokenNotFoundException`相当のエラーレスポンス
  受信時は`tokenStatus = 'invalid'`とし、エラー表示＋`RegistrationRequestPage`への
  再申請リンクを表示する（MVP-2 AC）。
- **バリデーション**: `password === confirmPassword`のクライアント側チェック。強度チェックは
  しない。

### PendingUsersPage
- **状態**: `users: PendingUserSummary[]`, `loading: boolean`
- **責務**: マウント時に`userRegistrationApi.listPendingUsers()`を呼び出し、
  `PendingUsersTable`に結果を渡す（`business-logic-model.md` フロー3）。

### PendingUsersTable
- **Props**: `users: PendingUserSummary[]`, `onApprove(userId)`, `onReject(userId)`
- **責務**: `DataTable`（U1）を用いてメールアドレス・登録完了日時（MVP-3 AC）と承認/却下
  ボタンを表示する。却下ボタン押下時は`ConfirmDialog`（U1）で確認する（破壊的操作に準じる
  扱い）。操作成功後は`ToastNotification`（U1）で結果を通知し、一覧を再取得する。

### userRegistrationApi.ts（`features/userRegistration/api/`）
| 関数 | 対応API | 責務 |
|---|---|---|
| `requestRegistration(email)` | `POST /api/registrations` | フロー1 |
| `completeRegistration(token, password)` | `POST /api/registrations/complete` | フロー2 |
| `listPendingUsers()` | `GET /api/registrations/pending` | フロー3 |
| `approveUser(userId)` | `POST /api/registrations/{userId}/approve` | フロー3 |
| `rejectUser(userId)` | `POST /api/registrations/{userId}/reject` | フロー3 |

（正確なエンドポイントパス・パラメータ名はCode Generation段階で確定する。`component-methods.md`
の`UserRegistrationService`シグネチャに準拠する。）

---

## AppRouter.tsx（U1で新設済み）への追加

| パス | コンポーネント | 認可 |
|---|---|---|
| `/login` | `LoginPage` | パブリック |
| `/register` | `RegistrationRequestPage` | パブリック |
| `/register/complete` | `PasswordSetupPage` | パブリック |
| `/admin/pending-users` | `PendingUsersPage` | `ProtectedRoute requiredRole="ADMIN"` |

U1の`AppLayout`（ヘッダー・ナビゲーション）は`/login`・`/register`・`/register/complete`では
表示しない（未認証パブリックページのため、専用のシンプルなレイアウトを使用する。
具体的なレイアウト分岐方法はCode Generationで確定）。