# U2 Auth & User Registration - フロントエンドサマリ

Step 11（フロントエンド生成）・Step 12（Vitest+RTLテスト）で生成したコンポーネント・
API・store・ルーティングの一覧。設計は`functional-design/frontend-components.md`、
実装パターンは`nfr-design/nfr-design-patterns.md`（特に1.4「クライアント側sessionStorage
同期の実装パターン」）に準拠する。

## 既存資産の拡張（U1で生成済み、本Stepでbrownfield拡張）

| ファイル | 変更内容 |
|---|---|
| `src/store/authStore.ts` | `refreshToken`フィールド、`setTokens`/`clearTokens`アクションを追加。`persist`ミドルウェアで`sessionStorage`（`nfr-design-patterns.md` 1.4）に同期 |
| `src/hooks/useAuth.ts` | `setTokens`を公開。既存コンポーネント（`AppLayout.tsx`等、本Step対象外）への影響を避けるため、公開プロパティ名は`logout`のまま内部で`clearTokens`に委譲 |
| `src/api/apiClient.ts` | 401応答時に`refreshAccessToken()`（内部で`fetch`を直接使用し`apiFetch`の再帰呼び出しを回避）でアクセストークンを更新し、元のリクエストを1回だけ再試行する処理を追加。再試行後も失敗した場合は既存のトークンクリア＋`/login`リダイレクト処理にフォールバック |
| `src/routes/AppRouter.tsx` | トップレベル`<Routes>`に公開ルート（`/login`, `/register`, `/register/complete`）を追加し、`/*`ワイルドカードで`AuthenticatedRoutes`（`AppLayout`＋ネストした保護ルート用`<Routes>`）に委譲する構成に変更。保護ルートに`/admin/pending-users`を追加 |

## 新規: `features/auth/`

| ファイル | 内容 |
|---|---|
| `types.ts` | `AuthToken { accessToken, refreshToken }` |
| `api/authApi.ts` | `login(email, password)` → `POST /api/auth/login`、`refresh(refreshToken)` → `POST /api/auth/refresh`、`logout(refreshToken)` → `POST /api/auth/logout`、`decodeAccessToken(accessToken)`（JWTペイロードをbase64url decode + `JSON.parse`し`sub`/`role`クレームを抽出。署名検証はクライアント側では行わない） |
| `LoginPage.tsx` | ログインフォーム（`data-testid="login-page"`）。送信成功時、`decodeAccessToken`でユーザ情報を復元して`authStore`に格納し、ロールに応じて`ADMIN`→`/admin/pending-users`、`USER`→`/`へ遷移 |

## 新規: `features/userRegistration/`

| ファイル | 内容 |
|---|---|
| `types.ts` | `PendingUserSummary { id, email, createdAt }` |
| `api/userRegistrationApi.ts` | `requestRegistration(email)` → `POST /api/registration/request`、`completeRegistration(token, password)` → `POST /api/registration/complete`、`listPendingUsers()` → `GET /api/registration/pending`、`approveUser(id)` → `POST /api/registration/{id}/approve`、`rejectUser(id)` → `POST /api/registration/{id}/reject` |
| `RegistrationRequestPage.tsx` | メールアドレス入力フォーム（`data-testid="registration-request-page"`）。列挙攻撃対策（`business-rules.md` 1.1）により、API呼び出しの成功/失敗に関わらず常に同一の完了メッセージを表示する。エラーは`catch`ブロックで意図的に握りつぶし、呼び出し元へ伝播させない（未処理のPromise rejectionを防止するため） |
| `PasswordSetupPage.tsx` | URLクエリパラメータ`token`からトークンを取得しパスワード設定フォームを表示。トークン欠落時は即座にエラー表示。`ApiError.code`が`TOKEN_EXPIRED`または`TOKEN_NOT_FOUND`の場合は無効トークン向けのエラー表示＋再申請リンクを表示 |
| `PendingUsersTable.tsx` | 承認待ちユーザ一覧テーブル。承認ボタンは確認ダイアログなしで即時`onApprove`を呼び出し、却下ボタンは`ConfirmDialog`での確認後に`onReject`を呼び出す |
| `PendingUsersPage.tsx` | `PendingUsersTable`のコンテナ。マウント時に`listPendingUsers`を実行し、承認/却下成功時は一覧を再取得しつつ`ToastNotification`で成功/失敗を通知 |

## ルーティング一覧

| パス | 種別 | コンポーネント |
|---|---|---|
| `/login` | 公開 | `LoginPage` |
| `/register` | 公開 | `RegistrationRequestPage` |
| `/register/complete` | 公開 | `PasswordSetupPage` |
| `/` 以下（`/audit-logs`等） | 保護（`AppLayout`配下） | 既存＋`/admin/pending-users` → `PendingUsersPage` |

未認証で保護ルートにアクセスした場合は`ProtectedRoute`（既存、変更なし）により`/login`へリダイレクトする。

## data-testid一覧（新規分）

`login-page`, `registration-request-page`, `registration-request-page-email-input`,
`registration-request-page-submit-button`, `registration-request-page-success-message`,
`password-setup-page-password-input`, `password-setup-page-confirm-password-input`,
`password-setup-page-submit-button`, `password-setup-page-error-message`,
`password-setup-page-success-message`, `pending-users-table-approve-button`,
`pending-users-table-reject-button`

## 実装時判断事項（設計未規定・自律的に決定した内容)

- **JWTクライアント側デコード**: バックエンドの`AuthenticationService.login()`は
  `AuthToken(accessToken, refreshToken)`のみを返しユーザ識別情報を含まないため、
  `frontend-components.md`が要求する`authStore`への`{ id, email, role }`格納を実現する目的で、
  アクセストークンのペイロードをクライアント側でデコードして`sub`（userId）・`role`
  （カスタムクレーム、`JwtTokenProvider`の`ROLE_CLAIM`）を取得している。署名検証はサーバ側
  （`JwtTokenValidator`/`JwtAuthenticationFilter`）で行われる前提であり、クライアント側の
  デコード結果はUI表示・ルーティング判定にのみ使用し、認可判定には使用しない。
- **ログイン後の遷移先**: `ADMIN`ロールは`/admin/pending-users`、`USER`ロールは`/`へ遷移する
  仕様とした。汎用的な認証後ホーム画面は他ユニットで未定義のため、当面の実装時判断である。
- **`useAuth().logout`の名称維持**: store側では`logout`を`clearTokens`に統合したが、
  `AppLayout.tsx`（本Step対象外）への影響を避けるため、`useAuth()`フックが公開する
  プロパティ名は`logout`のまま維持し、内部で`clearTokens`に委譲している。

## テストカバレッジ（Step 12）

| テストファイル | 件数 | 検証内容 |
|---|---|---|
| `store/authStore.test.ts` | 6 | トークン/ユーザ情報の設定・クリア、`sessionStorage`への永続化 |
| `hooks/useAuth.test.ts` | 3 | `setTokens`／`logout`（`clearTokens`委譲）の動作 |
| `api/apiClient.test.ts` | 6 | 既存4件＋401時の自動リフレッシュ＆再試行の成功/失敗パターン2件 |
| `features/auth/api/authApi.test.ts` | 4 | `login`/`refresh`/`logout`のリクエスト、`decodeAccessToken`のJWTペイロード抽出 |
| `features/auth/LoginPage.test.tsx` | 3 | フォーム送信、ロール別遷移先、エラー表示 |
| `features/userRegistration/RegistrationRequestPage.test.tsx` | 2 | 成功時・失敗時いずれも同一の完了メッセージを表示（列挙攻撃対策） |
| `features/userRegistration/PasswordSetupPage.test.tsx` | 4 | トークン欠落、パスワード不一致、成功、無効トークン（`TOKEN_EXPIRED`） |
| `features/userRegistration/PendingUsersTable.test.tsx` | 4 | 一覧表示、承認即時実行、却下の確認ダイアログ（キャンセル/確定） |
| `features/userRegistration/PendingUsersPage.test.tsx` | 4 | 初期読込、承認成功時の再取得、却下成功時の再取得、承認失敗時のエラートースト |
| `routes/AppRouter.test.tsx` | 4 | 公開ルートで`AppLayout`ナビが非表示、保護ルート未認証時に`/login`へリダイレクト、認証済みで`AppLayout`ナビ表示 |

**合計 68件、全テスト成功**（`npm test -- --run`）。`npm run build`（`tsc -b && vite build`）・
`npm run lint`（oxlint）もエラーなしで完了している。