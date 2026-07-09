# U2 Auth & User Registration - テスティングサマリ

Step 3/6/9/12で生成した全テスト（ビジネスロジック層・API層・リポジトリ層・フロントエンド）を横断し、
PBT-10（補完的テスト戦略）の遵守状況、P1〜P13（`business-logic-summary.md`で識別したテスト可能な性質）と
テストクラスの対応関係、およびexample-basedテストの一覧を整理する。

## PBT-10: 補完的テスト戦略

`property-based-testing`拡張のRule PBT-10は、プロパティベーステスト（PBT）がexample-basedテストを
**置き換えるのではなく補完する**ことを要求する。本ユニットでは以下の方針で遵守した（U1と同一方針）。

- **性質（P1〜P13）の検証はjqwik `@Property`で実施**: `UserRegistrationServiceTest`,
  `RegistrationTokenServiceTest`, `AuthenticationServiceTest`, `RefreshTokenServiceTest`,
  `OpaqueTokenGeneratorTest`, `JwtTokenProviderTest`の各テストクラスで、対応する性質を広い
  入力空間に対して自動生成されたケースで検証する。
- **業務的に重要な具体シナリオはexample-basedテストで別途固定**: API層（`AuthControllerTest`,
  `RegistrationControllerTest`）、リポジトリ層（`UserRepositoryTest`,
  `RegistrationTokenRepositoryTest`, `RefreshTokenRepositoryTest`）、フロントエンド（10ファイル）
  はすべてexample-basedテストのみで構成し、規定の入出力を明示するケースを固定した。PBTが唯一の
  テストとなっている性質はない。
- **テストクラス/ファイルでPBTとexample-basedを明確に分離**: バックエンドはPBT専用クラス
  （ビジネスロジック層の6クラス）とexample-based専用クラス（API層・リポジトリ層の各クラス）を
  完全に分離しており、U1のような同居パターンは採らなかった（本ユニットではP1〜P13すべてが
  ビジネスロジック層の性質であり、API層・リポジトリ層に先送りされる性質が無かったため）。
  フロントエンドはU1同様、Vitest + React Testing Libraryによるexample-basedテストのみ
  （TypeScript側にPBTフレームワークは導入していない）。
- **1テストメソッドが複数性質を検証するケース**: `RefreshTokenServiceTest`の
  `rotationRoundTripsOnceThenDetectsReuseAndRevokesFamily`は、reuse detection（P9）と
  ローテーションのRound-trip（P10）を同一シナリオ内の連続した状態遷移として検証しており、
  1メソッドで2性質をカバーする（クラスの`tests=2`件のうち1件がP9+P10、もう1件がP11に対応）。
- **PBTが失敗を検出した場合の回帰テスト追加**: 本ユニットの開発中にjqwikが恒久的な回帰テストを
  要する失敗を検出した事例はない（該当なし）。

## P1〜P13対応表（最終版）

| # | 対象 | 検証テストクラス | 検証方式 | 層 |
|---|---|---|---|---|
| P1 | `requestRegistration`の列挙攻撃対策Invariant | `UserRegistrationServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P2 | `RegistrationTokenService`のRound-trip | `RegistrationTokenServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P3 | 再送信時の新旧トークンInvariant | `UserRegistrationServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P4 | `completeRegistration`のInvariant | `UserRegistrationServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P5 | 承認/却下のState machine性質 | `UserRegistrationServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P6 | `REJECTED`ユーザへの再発行なしInvariant | `UserRegistrationServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P7 | ログイン成否のOracle | `AuthenticationServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P8 | ログイン失敗時の監査記録Invariant | `AuthenticationServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P9 | リフレッシュトークンreuse detectionのInvariant | `RefreshTokenServiceTest` | jqwik `@Property`（P10と同一メソッド） | ビジネスロジック（Step 3） |
| P10 | リフレッシュトークンローテーションのRound-trip | `RefreshTokenServiceTest` | jqwik `@Property`（P9と同一メソッド） | ビジネスロジック（Step 3） |
| P11 | ログアウトの影響範囲限定Invariant | `RefreshTokenServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P12 | `OpaqueTokenGenerator`の性質 | `OpaqueTokenGeneratorTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P13 | `JwtTokenProvider`のRound-trip | `JwtTokenProviderTest` | jqwik `@Property` | ビジネスロジック（Step 3） |

P1〜P13全13性質にjqwik `@Property`テストが対応済み。U1と異なり、本ユニットは全性質が
ビジネスロジック層で完結しており（Repository層はStep 8前倒し生成のため`@DataJpaTest`環境で
性質検証を行うテストクラスもビジネスロジック層のテストとして分類、詳細は
`business-logic-summary.md`参照）、API層・リポジトリ層に先送りされた性質は無い。

## example-basedテスト一覧

### バックエンド（Step 6, 9）

| テストクラス | 検証内容 | 件数 |
|---|---|---|
| `AuthControllerTest` | ログイン成功/失敗、リフレッシュ成功/失敗、ログアウト成功 | 5 |
| `RegistrationControllerTest` | 登録申請/完了の未認証アクセス、承認待ち一覧の管理者成功/非管理者403/未認証401、承認・却下の管理者成功/非管理者403/未認証401 | 9 |
| `UserRepositoryTest` | 基本CRUD、`findByEmail`、`countByRole`、`findByStatusOrderByCreatedAtAsc`、emailのunique制約違反 | 7 |
| `RegistrationTokenRepositoryTest` | 基本CRUD、`findByTokenHash`、`findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull`、tokenHashのunique制約違反 | 7 |
| `RefreshTokenRepositoryTest` | 基本CRUD、`findByTokenHash`、`findByFamilyId`、tokenHashのunique制約違反 | 7 |

バックエンドexample-based合計: 35件（API層14件＋リポジトリ層21件）。

### フロントエンド（Step 12、全てexample-based）

| テストファイル | 件数 | 備考 |
|---|---|---|
| `src/store/authStore.test.ts` | 5 | U1の3件から拡張（`refreshToken`/`setTokens`/`clearTokens`対応） |
| `src/components/AppLayout.test.tsx` | 3 | レビュー指摘で追加した`/admin/pending-users`ナビリンクの表示/非表示（新規ファイル） |
| `src/hooks/useAuth.test.ts` | 3 | U1と同数（`setTokens`/`logout`委譲に対応） |
| `src/api/apiClient.test.ts` | 6 | U1の4件から拡張（401自動リフレッシュ&リトライの成功/失敗2件を追加） |
| `src/features/auth/api/authApi.test.ts` | 4 | 新規 |
| `src/features/auth/LoginPage.test.tsx` | 3 | 新規 |
| `src/features/userRegistration/RegistrationRequestPage.test.tsx` | 2 | 新規 |
| `src/features/userRegistration/PasswordSetupPage.test.tsx` | 4 | 新規 |
| `src/features/userRegistration/PendingUsersTable.test.tsx` | 4 | 新規 |
| `src/features/userRegistration/PendingUsersPage.test.tsx` | 4 | 新規 |
| `src/routes/AppRouter.test.tsx` | 4 | 新規 |

本ユニットでの新規/拡張分: 42件（新規ファイル8件・28テスト＋既存3ファイルの拡張分14テスト）。
`AppLayout.test.tsx`（3件）は、Code Generation完了メッセージ提示後のユーザレビューで
`/admin/pending-users`へのナビリンク欠落が指摘され、`AppLayout.tsx`への修正と合わせて追加した
新規ファイルである。U1既存分（`usePagination`, `ProtectedRoute`, `DataTable`, `Pagination`,
`ToastNotification`, `ConfirmDialog`, `features/auditLog/*`の9ファイル・29テスト）と合わせ、
フロントエンド全体は20ファイル・71テストとなる。

## 実行確認状況

本サマリ作成時点で実際にテストを実行し、グリーンであることを確認済み（Build and Testステージの
再確認対象ではあるが、Step 11-13完了時点で以下を確認している）:

- **フロントエンド**: `npm test -- --run`（Vitest）20ファイル・71/71件成功、`npm run build`
  （`tsc -b && vite build`）・`npm run lint`（oxlint）共にエラーなし。初回実行時、
  `RegistrationRequestPage.tsx`の`try/finally`が失敗時にPromiseを再送出し未処理rejectionと
  なる不具合を検出・修正済み（`catch`ブロックで意図的に握りつぶす実装に修正、詳細は
  `frontend-summary.md`参照）。Code Generation完了メッセージ提示後のユーザレビューで
  `/admin/pending-users`へのナビリンク欠落が指摘され、`AppLayout.tsx`の修正と
  `AppLayout.test.tsx`（3件）の新規追加により対応済み（`frontend-summary.md`参照）。
- **バックエンド**: `./gradlew build`（コンパイル・全テスト実行・check・assemble）成功、
  18テストクラス・68/68件成功、0失敗・0エラー。`business-logic-summary.md`のStep 3時点で
  記録されていた`MasterMeisterApplicationTests.contextLoads()`のHibernate方言解決失敗
  （`application.yml`未存在に起因、U1由来の既知事象）は、U1自身のCode Generation Step 16
  （`spring.datasource`/`spring.jpa`設定の追加、コミット`eb4d40a`）で既に解消済みであることを
  確認した（本ユニットのStep 16で追記した6件のU2設定キーはいずれも`spring.datasource`/
  `spring.jpa`に関与しないため、本Step自体は当該事象の解消に寄与していない）。