# u2-auth-user-registration-code-generation-plan.md

U2（Auth & User Registration）の Code Generation 計画。本ドキュメントが Code Generation の
単一の真実源（single source of truth）であり、Part 2（Generation）はこの計画のステップを
順に実行する。ワークスペースルート: `~/Documents/project/git/MasterMeister2`
（`aidlc-state.md` Workspace Root）。アプリケーションコードはワークスペースルート配下
（`backend/`, `frontend/`）にのみ生成し、`aidlc-docs/` にはドキュメント成果物のみ生成する。

---

## ユニットコンテキスト（code-generation.md Step 3）

### 対応ストーリー
MVP-1〜MVP-6（`unit-of-work-story-map.md`）:
| ID | タイトル |
|---|---|
| MVP-1 | メールアドレスによる登録申請 |
| MVP-2 | 確認メールのリンクからパスワードを設定して登録完了 |
| MVP-3 | 登録完了ユーザ一覧の確認 |
| MVP-4 | ユーザの承認/却下 |
| MVP-5 | 承認/却下結果のメール通知 |
| MVP-6 | ログイン（JWT発行） |

### 他ユニットへの依存
U1（Platform Foundation）のみに依存（`unit-of-work-dependency.md`）:
- `common`（`PageRequest`/`PageResult`/例外群/`GlobalExceptionHandler` — 本ユニットは
  `GlobalExceptionHandler`に新規例外のマッピングを追記するブラウンフィールド拡張を行う）
- `audit`（`AuditLogService.record(...)`）
- `mail`（`MailService.send(...)`、`MailNotificationType`は U1 で
  `REGISTRATION_CONFIRMATION`/`REGISTRATION_APPROVED`/`REGISTRATION_REJECTED`の3種と対応する
  Thymeleafテンプレートまで生成済み — 本ユニットでの新規テンプレート作成は不要）
- `security`（U1 既存の`SecurityConfig`/`JwtAuthenticationFilter`/`JwtTokenValidator`/
  `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`/`WebConfig`。本ユニットは
  `OpaqueTokenGenerator`・`JwtTokenProvider`を同パッケージに追加し、`SecurityConfig`に
  `PasswordEncoder` Beanと`/api/registrations/**`向け認可ルールを追記する
  ブラウンフィールド拡張を行う — `nfr-design-patterns.md` 1.1/1.2/1.2.1）

### 提供インタフェース・契約（他ユニットが依存する公開API）
- U4（Permission Management）が`userregistration`（ユーザ参照）に依存する
  （`unit-of-work-dependency.md`）。本ユニットの`User`/`Role`/`UserRepository`は将来U4から
  参照される想定のため、パッケージプライベートにせず`public`で生成する。

### 本ユニットが所有するデータエンティティ（内部DB/JPA）
- `userregistration`パッケージ: `User`, `RegistrationToken`
- `auth`パッケージ: `RefreshToken`
- （`Role`・`UserStatus`列挙型は`User`に付随するため`userregistration`パッケージに配置）

### パッケージ設計判断（AI決定事項、ユーザ確認済みの2点を含む）
- **`OpaqueTokenGenerator`・`JwtTokenProvider`は`cherry.mastermeister.security`パッケージに配置**
  （ユーザレビューにより確定、`nfr-design-patterns.md` 1.2/1.2.1）。いずれも`auth`固有・
  `userregistration`固有の業務ロジックではない認証基盤の道具であり、両パッケージから対称に
  参照させることで`auth`↔`userregistration`間の依存発生を避ける。
- **`User`/`Role`/`UserStatus`/`UserRepository`は`userregistration`パッケージに配置する**
  （本計画でのAI決定、`domain-entities.md`が「userregistration / auth 共有ドメイン」と
  位置付けている実体）。`completeRegistration`（登録完了）でユーザ行を新規作成するのが
  `userregistration`の責務であり、パッケージ名からもエンティティのライフサイクル起点が
  ここにあることが明確なため。`auth`パッケージ（`AuthenticationService`、
  `RefreshTokenService`）はこの`User`/`UserRepository`を参照する
  （同一ユニット内のパッケージ間依存であり、`unit-of-work-dependency.md`のユニット間依存
  マトリクスの対象外——`OpaqueTokenGenerator`のケースとは異なり、双方から対称に参照される
  ものではなく片方向の依存で足りるため、`security`のような別置きは行わない）。
- **`AdminBootstrapRunner`は`userregistration`パッケージに配置する**（当初`auth`と
  していたがユーザレビューで訂正、`nfr-design-patterns.md` 1.3）。起動時に`User`行を
  直接作成するエンティティ作成処理であり、`User`のライフサイクルを所有する
  `userregistration`パッケージに置くのが上記方針と整合する（`auth`は`User`/
  `UserRepository`を参照するのみで作成は行わない）。
- **`AuthenticationService`に`refresh(String rawRefreshToken): AuthToken`を追加する**
  （`component-methods.md`のスナップショットは`login`/`logout`のみを列挙しているが、これは
  リフレッシュトークン機構がU1 NFR Requirements 4.2で後から確定した経緯によるものであり、
  `business-rules.md` 2.2（「認証（auth）」節）がトークンリフレッシュ処理を
  `AuthenticationService`の責務範囲として記述しているため、承認済みインタフェースへの
  整合的な拡張として追加する）。内部で`RefreshTokenService`（検証・ローテーション・
  reuse detection）と`JwtTokenProvider`（新アクセストークン発行）を呼び出す。
- **新規例外の配置**: `AuthenticationFailedException`/`InvalidTokenException`は`auth`
  パッケージ、`TokenExpiredException`/`TokenNotFoundException`/`InvalidUserStateException`
  （`business-rules.md` 1.4「終端状態への再操作」用、具体的な型はCode Generationで確定と
  されていた事項）は`userregistration`パッケージに配置する。U1の`GlobalExceptionHandler`
  （`config`パッケージ）にこれら5例外のマッピングを追記する
  （`AuthenticationFailedException`→401, `InvalidTokenException`→401,
  `TokenExpiredException`→400, `TokenNotFoundException`→404,
  `InvalidUserStateException`→409）。

### サービス境界・責務
- `security`（U1既存、拡張）: `PasswordEncoder` Bean、`OpaqueTokenGenerator`（不透明トークン
  生成・ハッシュ化）、`JwtTokenProvider`（アクセストークン発行・検証）。
- `auth`: `AuthenticationService`（ログイン/リフレッシュ/ログアウト）、`RefreshTokenService`
  （リフレッシュトークンの発行・ローテーション・reuse detection一括失効）。
- `userregistration`: `UserRegistrationService`（登録申請〜承認/却下）、
  `RegistrationTokenService`（登録確認トークンの発行・検証）、`AdminBootstrapRunner`
  （初期管理者アカウント作成。`auth`から移設、訂正）。
- フロントエンド: `features/auth/`（ログイン画面）、`features/userRegistration/`
  （登録申請・パスワード設定・承認待ち一覧画面）。U1の`authStore`/`apiClient`/`AppRouter`/
  `ProtectedRoute`/`AppLayout`/`DataTable`/`ConfirmDialog`/`ToastNotification`を
  ブラウンフィールド拡張・再利用する。

---

## ステップ一覧

### Step 1: プロジェクト構造セットアップ
- [ ] 1-1. **該当なし（N/A）**: 本ユニットが必要とする依存関係
      （`spring-boot-starter-security`, `spring-boot-starter-data-jpa`,
      `spring-boot-starter-mail`, `spring-boot-starter-thymeleaf`, `jjwt-*`,
      `net.jqwik:jqwik`, フロントエンドの`react-router-dom`, `zustand`, `vitest`一式）は
      いずれもU1 Code Generation Step 1で追加済み。新規依存の追加は不要。

### Step 2: ビジネスロジック生成
- [x] 2-1. `backend/src/main/java/cherry/mastermeister/security/SecurityConfig.java`
      （既存、ブラウンフィールド修正）に以下を追記:
      - `@Bean PasswordEncoder passwordEncoder(@Value("${mm.app.security.password-encoder-strength:10}") int strength)`
        が`new BCryptPasswordEncoder(strength)`を返す（`nfr-design-patterns.md` 1.1）。
      - `authorizeHttpRequests`に`anyRequest().authenticated()`より前の行として追加:
        `requestMatchers(HttpMethod.POST, "/api/registrations").permitAll()`,
        `requestMatchers(HttpMethod.POST, "/api/registrations/complete").permitAll()`,
        `requestMatchers(HttpMethod.GET, "/api/registrations/pending").hasRole("ADMIN")`,
        `requestMatchers(HttpMethod.POST, "/api/registrations/*/approve").hasRole("ADMIN")`,
        `requestMatchers(HttpMethod.POST, "/api/registrations/*/reject").hasRole("ADMIN")`
        （`business-rules.md` 5節のパスパターンに準拠。`/api/auth/**`は既存の`permitAll()`が
        `/api/auth/login`・`/api/auth/refresh`・`/api/auth/logout`をカバーするため変更不要）。
- [x] 2-2. `backend/src/main/java/cherry/mastermeister/security/OpaqueTokenGenerator.java`
      （新規、`@Component`）: `String generate()`（32バイト`SecureRandom`→URL-safe base64、
      パディングなし）、`String hash(String plainToken)`（SHA-256、16進文字列）を実装
      （`nfr-design-patterns.md` 1.2）。
- [x] 2-3. `backend/src/main/java/cherry/mastermeister/security/JwtTokenProvider.java`
      （新規、`@Component`）: `String generateToken(Long userId, Role role, Duration expiry)`
      （U1の`JwtTokenValidator`と同じ`mm.app.jwt.secret`（環境変数）でHS256署名。クレームに
      `userId`・`role`（`role.name()`文字列）を設定）、
      `JwtClaims parseAndValidate(String rawToken)`（失敗時`InvalidTokenException`をスロー。
      U1の`JwtTokenValidator`と同じ秘密鍵・検証ロジックを用いる）を実装
      （`component-methods.md`のシグネチャ準拠）。
- [x] 2-4. `backend/src/main/java/cherry/mastermeister/userregistration/` に
      `Role`（enum: `ADMIN`, `USER`）、`UserStatus`（enum: `PENDING_APPROVAL`, `APPROVED`,
      `REJECTED`）、`User`（JPAエンティティ。`domain-entities.md`のフィールド定義:
      `id`, `email`（`@Column(unique = true, nullable = false)`）, `passwordHash`, `role`,
      `status`, `createdAt`, `decidedAt`）を生成。
- [x] 2-5. `backend/src/main/java/cherry/mastermeister/userregistration/` に
      `RegistrationToken`（JPAエンティティ。`domain-entities.md`のフィールド定義:
      `id`, `email`, `tokenHash`（`@Column(unique = true, nullable = false)`）, `expiresAt`,
      `invalidatedAt`, `consumedAt`, `createdAt`）、`RegistrationTokenStatus`（enum:
      `VALID`, `EXPIRED`, `NOT_FOUND`）を生成。
- [x] 2-6. `backend/src/main/java/cherry/mastermeister/userregistration/` に
      `TokenExpiredException`, `TokenNotFoundException`, `InvalidUserStateException`
      （いずれも`RuntimeException`継承）を生成。
- [x] 2-7. `backend/src/main/java/cherry/mastermeister/userregistration/RegistrationTokenService.java`
      （`@Service`）: `String issueToken(String email, Duration expiry)`（`OpaqueTokenGenerator`
      で生成、`tokenHash`のみ永続化し平文を戻り値として返す）、
      `RegistrationTokenStatus validate(String token)`（`domain-entities.md`の判定ロジック:
      未検出→`NOT_FOUND`、`invalidatedAt`/`consumedAt`設定済みまたは`expiresAt`超過→
      `EXPIRED`、それ以外→`VALID`）を実装。
- [x] 2-8. `backend/src/main/java/cherry/mastermeister/userregistration/UserRegistrationService.java`
      （`@Service`）: `requestRegistration(String email)`（`business-rules.md` 1.2の分岐、
      `AuditLogService`呼び出しは対象外——`business-rules.md`に監査記録要件の明記なし）、
      `completeRegistration(String token, String rawPassword)`（`RegistrationTokenService.validate`
      →`VALID`以外は`TokenExpiredException`/`TokenNotFoundException`、`VALID`なら
      `PasswordEncoder`でハッシュ化した`User`作成+`RegistrationToken.consumedAt`更新を
      同一トランザクションで実行）、`approveUser(Long adminUserId, Long targetUserId)`/
      `rejectUser(...)`（`status = PENDING_APPROVAL`以外は`InvalidUserStateException`、
      成功時`MailService.send(...)`+`AuditLogService.record(ADMIN_OPERATION, ...)`）、
      `List<PendingUserSummary> listPendingUsers()`（`status = PENDING_APPROVAL`、
      `createdAt`昇順）を実装（`business-rules.md` 1節、`business-logic-model.md`フロー1〜3）。
- [x] 2-9. `backend/src/main/java/cherry/mastermeister/userregistration/PendingUserSummary.java`
      （record: `Long id, String email, Instant createdAt`）を生成。
- [x] 2-10. `backend/src/main/java/cherry/mastermeister/auth/RefreshToken.java`
      （JPAエンティティ。`domain-entities.md`のフィールド定義: `id`, `userId`, `familyId`,
      `tokenHash`（`@Column(unique = true, nullable = false)`）, `expiresAt`, `rotatedAt`,
      `revokedAt`, `createdAt`）を生成。
- [x] 2-11. `backend/src/main/java/cherry/mastermeister/auth/` に
      `AuthenticationFailedException`, `InvalidTokenException`（いずれも`RuntimeException`
      継承）、`AuthToken`（record: `String accessToken, String refreshToken`）を生成。
- [x] 2-12. `backend/src/main/java/cherry/mastermeister/auth/RefreshTokenService.java`
      （`@Service`）: `String issue(Long userId)`（新規`familyId`（UUID）で`RefreshToken`を
      1件作成し平文トークンを返す）、`RotationResult rotate(String rawToken)`（record:
      `Long userId, String newPlainToken`。`domain-entities.md`の有効性判定+
      `business-rules.md` 2.2のreuse detection: `rotatedAt`設定済みトークンの再利用検知時は
      同一`familyId`全行の`revokedAt`を一括更新してから`InvalidTokenException`をスロー）、
      `void revoke(String rawToken)`（対象行のみ`revokedAt`設定、`business-rules.md` 2.3）を
      実装。いずれも`OpaqueTokenGenerator`を利用。
- [x] 2-13. `backend/src/main/java/cherry/mastermeister/auth/AuthenticationService.java`
      （`@Service`）: `AuthToken login(String email, String rawPassword)`（`business-rules.md`
      2.1の判定+`AuditLogService.record(AUTHENTICATION, LOGIN_FAILURE/LOGIN_SUCCESS, ...)`、
      成功時`JwtTokenProvider.generateToken`+`RefreshTokenService.issue`）、
      `AuthToken refresh(String rawRefreshToken)`（`RefreshTokenService.rotate`+
      `JwtTokenProvider.generateToken`で新アクセストークンを発行）、
      `void logout(String rawRefreshToken)`（`RefreshTokenService.revoke`+
      `AuditLogService.record(AUTHENTICATION, LOGOUT, ...)`）を実装
      （`business-rules.md` 2節、`component-methods.md`の拡張——本計画「パッケージ設計判断」
      参照）。
- [x] 2-14. `backend/src/main/java/cherry/mastermeister/userregistration/AdminBootstrapRunner.java`
      （`ApplicationRunner`）: `mm.app.admin.bootstrap.email`/`mm.app.admin.bootstrap.password`
      が両方設定済み、かつ`UserRepository`で`role = ADMIN`の`User`が0件の場合のみ、
      `PasswordEncoder`でハッシュ化したパスワードで`role = ADMIN`, `status = APPROVED`の
      `User`を1件作成する（`nfr-design-patterns.md` 1.3、`domain-entities.md`「設計判断」節。
      `auth`パッケージから`userregistration`パッケージへ配置訂正——ユーザレビュー）。
- [x] 2-15. `backend/src/main/java/cherry/mastermeister/config/GlobalExceptionHandler.java`
      （既存、ブラウンフィールド修正）に`@ExceptionHandler`を5件追記:
      `AuthenticationFailedException`→401, `InvalidTokenException`→401,
      `TokenExpiredException`→400, `TokenNotFoundException`→404,
      `InvalidUserStateException`→409（いずれも`ErrorResponse`形式で返却、U1の既存パターンを
      踏襲）。

### Step 3: ビジネスロジック単体テスト（PBT-01〜PBT-08, PBT-10）
`business-logic-model.md`のP1〜P11に加え、`security`パッケージに新設する
`OpaqueTokenGenerator`/`JwtTokenProvider`はFunctional Design時点でスコープ外だったため、
U1の`common.dialect`（P9〜P12）と同様、本Code Generation計画で新たにP12〜P13として識別する。
- [x] 3-1. **P1**（`requestRegistration`の列挙攻撃対策Invariant）: `UserRepository`/
      `RegistrationTokenRepository`をモック化し、新規/`PENDING_APPROVAL`/`APPROVED`/
      `REJECTED`/有効トークン再送信の5状態をjqwik Arbitraryで生成し、常に同一の
      成功結果（例外なし）となることを検証する`@Property`テストを
      `UserRegistrationServiceTest`に生成。
- [x] 3-2. **P2**（`RegistrationTokenService`のRound-trip）: 発行直後`validate`=`VALID`、
      `consumedAt`/`invalidatedAt`設定後`validate`=`EXPIRED`となることを検証する`@Property`
      テストを`RegistrationTokenServiceTest`に生成。
- [x] 3-3. **P3**（再送信時の新旧トークンInvariant）、**P6**（`REJECTED`ユーザへの
      再発行なしInvariant）: `RegistrationTokenServiceTest`/`UserRegistrationServiceTest`に
      `@Property`テストを生成。
- [x] 3-4. **P4**（`completeRegistration`のInvariant）: `VALID`/`EXPIRED`/`NOT_FOUND`の
      3ケースをjqwik Arbitraryで生成し、`VALID`時のみ`User`が1件作成されることを検証する
      `@Property`テストを`UserRegistrationServiceTest`に生成。
- [x] 3-5. **P5**（承認/却下のState machine性質）: `UserStatus`の全状態×`approveUser`/
      `rejectUser`の組み合わせをjqwik Arbitraryで網羅し、`PENDING_APPROVAL`からのみ遷移可能で
      終端状態への再実行は必ず`InvalidUserStateException`となることを検証する`@Property`
      テストを`UserRegistrationServiceTest`に生成。
- [x] 3-6. **P7**（ログイン成否のOracle）: `status`×パスワード一致有無の全組み合わせを
      jqwik Arbitraryで網羅し、期待される成否と一致することを検証する`@Property`テストを
      `AuthenticationServiceTest`に生成。
- [x] 3-7. **P8**（ログイン失敗時の監査記録Invariant）: `AuditLogService`をモック化し、
      存在しないメール（`userId = null`）/パスワード不一致（`userId`設定）の両ケースで
      `record(LOGIN_FAILURE, ...)`が必ず呼ばれることを検証する`@Property`テストを
      `AuthenticationServiceTest`に生成。
- [x] 3-8. **P9**（reuse detectionのInvariant）、**P10**（ローテーションのRound-trip）、
      **P11**（ログアウトの影響範囲限定Invariant）: `RefreshTokenRepository`をモック化し、
      `rotatedAt`済みトークンの再利用・正常ローテーション・ログアウトの3シナリオを
      jqwik Arbitraryで検証する`@Property`テストを`RefreshTokenServiceTest`に生成。
- [x] 3-9. **P12**（`OpaqueTokenGenerator`の性質: `hash`は同一入力に対し常に同一出力
      （決定的）、`generate()`はデコード可能な32バイトのURL-safe base64文字列を常に返す）:
      jqwikでランダムな文字列入力を生成する`@Property`テストを`OpaqueTokenGeneratorTest`に
      生成。
- [x] 3-10. **P13**（`JwtTokenProvider`のRound-trip: `parseAndValidate(generateToken(userId,
      role, expiry))`は元の`userId`・`role`と一致する`JwtClaims`を返す。期限切れ`expiry`
      （負のDuration）で生成したトークンは`parseAndValidate`で必ず`InvalidTokenException`と
      なる）: jqwikでランダムな`userId`・`Role`・`Duration`を生成する`@Property`テストを
      `JwtTokenProviderTest`に生成。

### Step 4: ビジネスロジックサマリ
- [x] 4-1. `aidlc-docs/construction/u2-auth-user-registration/code/business-logic-summary.md`
      を生成し、Step 2・Step 3で生成したクラス一覧とP1〜P13の対応関係を表形式で記載する。

### Step 5: APIレイヤ生成
- [x] 5-1. `backend/src/main/java/cherry/mastermeister/auth/` に
      `LoginRequest`（record: `String email, String password`）,
      `RefreshRequest`（record: `String refreshToken`）,
      `LogoutRequest`（record: `String refreshToken`）を生成。
- [x] 5-2. `backend/src/main/java/cherry/mastermeister/auth/AuthController.java`
      （`@RestController @RequestMapping("/api/auth")`）:
      `POST /login`（`LoginRequest`→`AuthenticationService.login`→`AuthToken`）,
      `POST /refresh`（`RefreshRequest`→`AuthenticationService.refresh`→`AuthToken`）,
      `POST /logout`（`LogoutRequest`→`AuthenticationService.logout`→204）を生成。
- [x] 5-3. `backend/src/main/java/cherry/mastermeister/userregistration/` に
      `RequestRegistrationRequest`（record: `String email`）,
      `CompleteRegistrationRequest`（record: `String token, String password`）を生成。
- [x] 5-4. `backend/src/main/java/cherry/mastermeister/userregistration/RegistrationController.java`
      （`@RestController @RequestMapping("/api/registrations")`）:
      `POST ""`（`requestRegistration`→204）, `POST "/complete"`（`completeRegistration`→204）,
      `GET "/pending"`（`listPendingUsers`→`List<PendingUserSummary>`。管理者ロールチェックは
      `SecurityConfig`の`hasRole("ADMIN")`に委ねる）,
      `POST "/{userId}/approve"`（`approveUser`。`adminUserId`は
      `SecurityContextHolder`から取得——具体的な取得方法はU1の`JwtAuthenticationFilter`が
      設定する`Authentication`の`principal`（`userId`）に準拠）,
      `POST "/{userId}/reject"`（`rejectUser`）を生成（`business-rules.md` 5節のパス
      パターンに準拠）。

### Step 6: APIレイヤ単体テスト
- [x] 6-1. `AuthControllerTest`（`@WebMvcTest` + `spring-security-test`）: ログイン成功/
      失敗、リフレッシュ成功/失敗（無効・reuse detection）、ログアウトをexample-basedテストで
      検証。
- [x] 6-2. `RegistrationControllerTest`（`@WebMvcTest` + `spring-security-test`の
      `@WithMockUser`）: 登録申請・登録完了（`permitAll`、未認証で200系）、承認待ち一覧・
      承認・却下（管理者以外403、未認証401）をexample-basedテストで検証。

### Step 7: APIレイヤサマリ
- [x] 7-1. `aidlc-docs/construction/u2-auth-user-registration/code/api-layer-summary.md`
      を生成し、エンドポイント一覧（パス・メソッド・認可要件・リクエスト/レスポンス形状）を
      記載。

### Step 8: リポジトリレイヤ生成
（実装順の都合上、3リポジトリインタフェースはStep 2のサービス実装がコンパイル可能である
必要があったため、Step 2と同時に前倒しで生成済み。内容はいずれも本ステップの記載どおり。）
- [x] 8-1. `backend/src/main/java/cherry/mastermeister/userregistration/UserRepository.java`
      （`JpaRepository<User, Long>`。`Optional<User> findByEmail(String email)`,
      `boolean existsByRoleEqualsAdmin()`相当のクエリメソッドまたは`countByRole(Role)`,
      `List<User> findByStatusOrderByCreatedAtAsc(UserStatus)`を定義）を生成。
- [x] 8-2. `backend/src/main/java/cherry/mastermeister/userregistration/RegistrationTokenRepository.java`
      （`JpaRepository<RegistrationToken, Long>`。`Optional<RegistrationToken>
      findByTokenHash(String)`, `Optional<RegistrationToken> findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull(String)`
      （再送信時の旧トークン検索用）を定義）を生成。
- [x] 8-3. `backend/src/main/java/cherry/mastermeister/auth/RefreshTokenRepository.java`
      （`JpaRepository<RefreshToken, Long>`。`Optional<RefreshToken> findByTokenHash(String)`,
      `List<RefreshToken> findByFamilyId(String)`（reuse detection時の一括更新用）を定義）を
      生成。

### Step 9: リポジトリレイヤ単体テスト
- [x] 9-1. `UserRepositoryTest`/`RegistrationTokenRepositoryTest`/`RefreshTokenRepositoryTest`
      （いずれも`@DataJpaTest`、組み込みH2）: 基本CRUD・上記クエリメソッドのexample-basedテスト
      を生成。unique制約（`email`, `tokenHash`）違反時の例外発生も検証する。

### Step 10: リポジトリレイヤサマリ
- [x] 10-1. `aidlc-docs/construction/u2-auth-user-registration/code/repository-layer-summary.md`
      を生成し、3リポジトリのクエリメソッド一覧とインデックス設計
      （`tokenHash`/`email`のunique制約のみ、`nfr-design-patterns.md` 2.1）を記載。

### Step 11: フロントエンドコンポーネント生成
- [ ] 11-1. `frontend/src/store/authStore.ts`（既存、ブラウンフィールド修正）:
      `refreshToken: string | null`を追加、`login`アクションを`setTokens(currentUser,
      accessToken, refreshToken)`に拡張（既存`login`呼び出し元がない——U1では未使用のため
      安全にリネーム可能）、`logout`を`clearTokens`に統合。`zustand/middleware`の`persist`
      （`storage: createJSONStorage(() => sessionStorage)`）を適用し、状態変更を自動的に
      `sessionStorage`と同期する（`nfr-design-patterns.md` 1.4）。
- [ ] 11-2. `frontend/src/api/apiClient.ts`（既存、ブラウンフィールド修正）: 401受信時、
      即座にログアウトする前に`features/auth/api/authApi.ts`の`refresh(refreshToken)`を
      1回試行し、成功すれば元のリクエストを新しいアクセストークンで再試行する。リフレッシュ
      自体が失敗（401等）した場合にのみ、既存の`authStore`クリア＋`/login`リダイレクト処理を
      実行する（無限リトライ防止のため、リフレッシュ呼び出し自体は本ロジックを経由しない
      素の`fetch`または再試行フラグで制御する）（`business-logic-model.md`フロー5、
      `frontend-components.md`）。
- [ ] 11-3. `frontend/src/features/auth/` に
      `LoginPage.tsx`（`data-testid="login-page-email-input"` / `-password-input"` /
      `-submit-button"` / `-error-message"`）、
      `api/authApi.ts`（`login`, `refresh`, `logout`）、`types.ts`を生成
      （`frontend-components.md`）。
- [ ] 11-4. `frontend/src/features/userRegistration/` に
      `RegistrationRequestPage.tsx`（`data-testid="registration-request-page-email-input"` /
      `-submit-button"` / `-success-message"`）、
      `PasswordSetupPage.tsx`（`data-testid="password-setup-page-password-input"` /
      `-confirm-password-input"` / `-submit-button"` / `-error-message"` /
      `-success-message"`）、
      `PendingUsersPage.tsx`、
      `PendingUsersTable.tsx`（`DataTable`（U1）利用、`data-testid="pending-users-table-approve-button"` /
      `-reject-button"`、却下時`ConfirmDialog`（U1）表示）、
      `api/userRegistrationApi.ts`（`requestRegistration`, `completeRegistration`,
      `listPendingUsers`, `approveUser`, `rejectUser`）、`types.ts`を生成
      （`frontend-components.md`）。
- [ ] 11-5. `frontend/src/routes/AppRouter.tsx`（既存、ブラウンフィールド修正）:
      `/login`, `/register`, `/register/complete`をパブリックルート（`AppLayout`なし）として
      `Routes`の先頭に追加し、既存の`AppLayout`配下ルート（`/audit-logs`）はネストした
      `Routes`（ワイルドカードパス配下）として維持する。`/admin/pending-users`
      （`ProtectedRoute requiredRole="ADMIN"`）を`AppLayout`配下に追加する
      （`frontend-components.md`のルーティング表）。

### Step 12: フロントエンドコンポーネント単体テスト
- [ ] 12-1. Vitest + React Testing Library で以下のexample-basedテストを生成:
      `authStore`（`setTokens`/`clearTokens`状態遷移、`sessionStorage`同期）,
      `apiClient`（401→リフレッシュ成功時の再試行、リフレッシュ失敗時のログアウト遷移）,
      `authApi`（`login`/`refresh`/`logout`のリクエスト形状）,
      `LoginPage`（成功時の画面遷移、失敗時のエラー表示 — MVP-6 AC）,
      `RegistrationRequestPage`（送信後の一律成功メッセージ表示 — MVP-1 AC）,
      `PasswordSetupPage`（パスワード不一致時のクライアント側エラー、トークン無効時の
      エラー表示＋再申請導線 — MVP-2 AC）,
      `PendingUsersPage`/`PendingUsersTable`（一覧表示、承認/却下確認ダイアログ、
      成功後の再取得 — MVP-3/MVP-4 AC）,
      `AppRouter`（パブリックルートで`AppLayout`が表示されないこと、保護ルートの
      未認証リダイレクト）。

### Step 13: フロントエンドコンポーネントサマリ
- [ ] 13-1. `aidlc-docs/construction/u2-auth-user-registration/code/frontend-summary.md`
      を生成し、`features/auth/`・`features/userRegistration/`のコンポーネント一覧・
      `data-testid`一覧を記載。

### Step 14: データベースマイグレーションスクリプト
- [ ] 14-1. **該当なし（N/A）**: U1と同様、内部DB(H2)のスキーマ管理はJPAの自動DDL生成に
      委ね、Flyway/Liquibase等は導入しない（U1 NFR Design Question 5 = A を踏襲）。

### Step 15: ドキュメント生成
- [ ] 15-1. Step 4/7/10/13で生成した各サマリに加え、
      `aidlc-docs/construction/u2-auth-user-registration/code/testing-summary.md`
      （P1〜P13とテストクラスの対応表、example-basedテスト一覧、PBT-10補完的テスト戦略の
      再確認）を生成する。

### Step 16: デプロイ成果物生成
- [ ] 16-1. `backend/src/main/resources/application.yml`（既存、ブラウンフィールド修正）に
      追記: `mm.app.security.password-encoder-strength`（既定`10`）,
      `mm.app.user-registration.token-expiry`（既定`3h`）,
      `mm.app.jwt.refresh-token-expiry`（既定`24h`）,
      `mm.app.admin.bootstrap.email`/`mm.app.admin.bootstrap.password`（既定未設定、
      環境変数プレースホルダ）,
      `mm.app.frontend.base-url`（既定`http://localhost:5173`、Step 2実装時にAI追加判断——
      メールテンプレート`registration-confirmation.html`/`registration-approved.html`が
      `linkUrl`変数を要求するため、確認/ログインリンク生成用のフロントエンドベースURLとして
      `UserRegistrationService`に追加した設定キー。当初のNFR Design/Code Generation計画には
      記載がなかった実装時の必要事項）。

---

## 完了基準
- 上記全ステップの生成物がワークスペースルート配下に作成され、対応する単体テストが
  生成されていること（実行・グリーン確認はBuild and Testステージで行う）。
- P1〜P13全ての性質にjqwik `@Property`テストが対応していること（PBT-02〜PBT-08準拠）。
- `aidlc-docs/construction/u2-auth-user-registration/code/`配下に5つのサマリドキュメント
  （business-logic-summary.md, api-layer-summary.md, repository-layer-summary.md,
  frontend-summary.md, testing-summary.md）が生成されていること。