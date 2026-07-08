# u1-platform-foundation-code-generation-plan.md

U1（Platform Foundation）の Code Generation 計画。本ドキュメントが Code Generation の
単一の真実源（single source of truth）であり、Part 2（Generation）はこの計画のステップを
順に実行する。ワークスペースルート: `~/Documents/project/git/MasterMeister2`
（`aidlc-state.md` Workspace Root）。アプリケーションコードはワークスペースルート配下
（`backend/`, `frontend/`）にのみ生成し、`aidlc-docs/` にはドキュメント成果物のみ生成する。

---

## ユニットコンテキスト（code-generation.md Step 3）

### 対応ストーリー
- **主担当**: ADM-6（監査ログの閲覧・絞り込み、管理者ペルソナ）
- **横断的関与**: `AuditLogService.record(...)` は U2〜U7 の全ストーリーの監査記録ACから
  呼び出される（`unit-of-work-story-map.md`）。U1のCode GenerationはこのAPI契約
  （インタフェース）を確定させる責務を負うが、呼び出し元の実装は各ユニットのCode Generationで
  行う。

### 他ユニットへの依存
- なし（`unit-of-work.md`: 「他ユニットへの依存: なし（最基盤ユニット）」、
  `unit-of-work-dependency.md`: U1行は全て空欄）。

### 提供インタフェース・契約（他ユニットが依存する公開API）
- `AuditLogService.record(EventCategory, EventType, Long userId, Long connectionId, Result, String targetDescription, String summaryMessage)` — 全ユニットから呼び出される。
- `AuditLogService.search(AuditLogFilterCriteria, PageRequest)` → `PageResult<AuditLog>`
- `MailService.send(MailNotificationType, String email, Map<String, Object> variables)` — U2から呼び出される。
- `common.PageRequest` / `common.PageResult<T>` — 全ユニットのページング処理で使用。
- `common` 例外群（`PermissionDeniedException` / `EntityNotFoundException` / `ValidationException`）と
  `GlobalExceptionHandler`（`@RestControllerAdvice`）— 全ユニットのControllerが依存。
- `common.dialect.DialectStrategy` / `DialectStrategyFactory` / `RdbmsType` — U3
  （`SchemaImportService`）、U5（`masterdata`）、U6（`querybuilder`）、U7（`queryexecution`）が
  依存。
- `security.JwtAuthenticationFilter` / `SecurityConfig` — U2の`JwtTokenProvider`が発行する
  トークンをU1が検証する（U1/U2責任境界、`nfr-requirements.md` 1.1）。U2は`Role`列挙型・
  ユーザエンティティを所有し、U1側はJWTクレームから`userId`（Long）と`role`（String、
  `ROLE_`プレフィックス付きGrantedAuthorityへ変換）のみを汎用的に読み取る
  （U2の`Role`型に対する前方依存を避ける設計判断）。

### 本ユニットが所有するデータエンティティ（内部DB/JPA）
- `AuditLog`（`audit`パッケージ）のみ。`security`/`common`/`config`/`mail`はエンティティを
  持たない（技術的関心事、`domain-entities.md`冒頭の既定方針どおり）。

### サービス境界・責務
- `common` / `common.dialect`: 技術横断ユーティリティ（例外、DTO、RDBMS方言吸収）。業務ロジックなし。
- `security`: JWT検証・認可のみ（発行はU2）。
- `config`: 共通設定（`GlobalExceptionHandler`、JPA/DataSource既定値は`application.yml`に委ね
  専用`@Configuration`クラスは設けない — `nfr-design-patterns.md` 5節）。
- `audit`: 監査記録・検索（`business-rules.md` 1節）。
- `mail`: メール送信（`business-rules.md` 2節）。
- フロントエンド: `features/`外の共通基盤一式 + `features/auditLog/`。

---

## ステップ一覧

### Step 1: プロジェクト構造セットアップ（依存関係追加）
- [x] 1-1. `backend/build.gradle.kts` に依存関係を追加:
  `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-mail`,
  `spring-boot-starter-thymeleaf`, `com.h2database:h2`（runtime）,
  `io.jsonwebtoken:jjwt-api` / `jjwt-impl`（runtime）/ `jjwt-jackson`（runtime）,
  `net.jqwik:jqwik`（testImplementation、PBT-09確定済み）,
  `org.springframework.security:spring-security-test`（testImplementation）。
  `war`プラグインを追加し`bootWar`タスクが実行可能WARを生成できるようにする
  （`build.gradle.kts`既存のBOM/JavaCompile UTF-8設定・ライセンスヘッダは維持）。
- [x] 1-2. `frontend/package.json` に依存関係を追加:
  `react-router-dom`（ルーティング）, `zustand`（`authStore`用の軽量状態管理。
  `frontend-components.md`が要求する状態は`currentUser`/`token`のみで、Redux等の
  重量ライブラリは過剰と判断——低リスクな技術選定のため質問なしで決定）,
  devDependencies: `vitest`, `@testing-library/react`, `@testing-library/jest-dom`,
  `@testing-library/user-event`, `jsdom`。`vite.config.ts`にVitestの`test`設定
  （`environment: 'jsdom'`）を追加。`package.json`に`"test": "vitest run"`スクリプトを追加
  （CLAUDE.md記載の「No test runner is configured yet」を解消）。
- [x] 1-3. `frontend/src/styles/design-tokens.css` を新規作成（`frontend-components.md`
  デザインシステム節: プライマリ/セカンダリ色、警告/エラー/成功色、フォントサイズ・
  スペーシングスケール、ボーダー半径のCSSカスタムプロパティを`:root`スコープで定義）。

### Step 2: ビジネスロジック生成
- [x] 2-1. `backend/src/main/java/cherry/mastermeister/common/exception/` に
  `PermissionDeniedException` / `EntityNotFoundException` / `ValidationException`
  （いずれも`RuntimeException`継承、共通基底クラスは設けない）を生成。
- [x] 2-2. `backend/src/main/java/cherry/mastermeister/common/` に
  `PageRequest`（page, pageSize）, `PageResult<T>`（content, totalCount, page, pageSize）,
  `ErrorResponse`（error, message — `business-rules.md` 3.1のレスポンスDTO形式）を生成。
- [x] 2-3. `backend/src/main/java/cherry/mastermeister/common/dialect/` に
  `RdbmsType`（enum: `MYSQL`/`MARIADB`/`POSTGRESQL`/`H2`）、`DialectStrategy`
  （インタフェース: `getRdbmsType()`, `quoteIdentifier(String)`, `buildPagingClause(int, int)`,
  `buildNullsOrderingClause(SortDirection, NullsOrder)`, `getSchemaResolutionMode()`。
  `SortDirection`/`NullsOrder`/`SchemaResolutionMode`列挙型も同パッケージに定義）、
  `MySqlDialectStrategy` / `MariaDbDialectStrategy` / `PostgreSqlDialectStrategy` /
  `H2DialectStrategy`（各`@Component`実装。バッククォート/ダブルクォート識別子クォート、
  `LIMIT n OFFSET m`構文、`NULLS FIRST/LAST`または`ORDER BY (col IS NULL)`エミュレーションの
  方言差異を実装）、`DialectStrategyFactory`（`Map<RdbmsType, DialectStrategy>`をSpring自動集約で
  受け取り`resolve(RdbmsType)`で解決）を生成。
- [x] 2-4. `backend/src/main/java/cherry/mastermeister/security/` に
  `JwtTokenValidator`（HS256署名・有効期限検証、`application.yml`の`mm.app.jwt.secret`
  （環境変数経由）を使用、`JwtClaims`レコード（`userId: Long`, `role: String`）を返す、
  失敗時は内部例外を投げ`JwtAuthenticationFilter`が捕捉）、
  `JwtAuthenticationFilter`（`OncePerRequestFilter`。`Authorization: Bearer`ヘッダから
  トークン抽出、検証成功時は`SecurityContextHolder`に`role`を`ROLE_`プレフィックス付き
  `GrantedAuthority`として設定、`/api/auth/**`はスキップ）、
  `RestAuthenticationEntryPoint`（401、`ErrorResponse`をJSON返却）、
  `RestAccessDeniedHandler`（403、同様）、
  `SecurityConfig`（`@EnableWebSecurity`、`SecurityFilterChain` Bean。
  `/api/auth/**`は`permitAll()`、`/api/audit-logs/**`は`hasRole("ADMIN")`、他は
  `authenticated()`。`JwtAuthenticationFilter`を`UsernamePasswordAuthenticationFilter`より前に
  追加）、
  `WebConfig`（`@Profile("dev")`、CORS許可: `localhost:5173`）を生成。
- [x] 2-5. `backend/src/main/java/cherry/mastermeister/audit/` に
  `EventCategory`（enum: `AUTHENTICATION`/`ADMIN_OPERATION`/`DATA_ACCESS`）、
  `EventType`（enum、`domain-entities.md`の14種）、`Result`（enum: `SUCCESS`/`FAILURE`）、
  `AuditLog`（JPAエンティティ。`domain-entities.md`のフィールド定義、
  `@Table(indexes = {@Index(columnList = "occurredAt"), @Index(columnList = "userId"),
  @Index(columnList = "eventCategory,eventType")})`）、
  `AuditLogFilterCriteria`（dateFrom, dateTo, userId, eventCategory, eventType — 全て
  Optional相当のnullable）、
  `AuditLogService`（`@Service`。`record(...)`は`@Transactional(propagation =
  Propagation.REQUIRES_NEW)` + try-catchで例外を吸収しログ出力のみ行う（P1）。
  `search(criteria, page)`は`occurredAt`降順固定、`application.yml`の
  `mm.app.audit.default-page-size`/`page-size-options`を参照してページサイズ既定値・上限を
  適用する（P5））を生成。
- [x] 2-6. `backend/src/main/java/cherry/mastermeister/mail/` に
  `MailNotificationType`（enum: `REGISTRATION_CONFIRMATION`/`REGISTRATION_APPROVED`/
  `REGISTRATION_REJECTED`）、
  `MailService`（`@Service`。`JavaMailSender` + Thymeleaf `TemplateEngine`を使用し、
  `MailNotificationType`ごとにテンプレート名を解決（`templates/mail/
  registration-confirmation.html` 等）、`Context`へ`variables`を設定し本文生成、送信は
  try-catchで例外吸収・ログ出力のみ（呼び出し元へ非伝播、P6））を生成。
  `backend/src/main/resources/templates/mail/` に3つのThymeleafテンプレート
  （`registration-confirmation.html`, `registration-approved.html`,
  `registration-rejected.html`）を生成（変数プレースホルダ: 宛先名, リンクURL, 有効期限等）。
- [x] 2-7. `backend/src/main/java/cherry/mastermeister/config/` に
  `GlobalExceptionHandler`（`@RestControllerAdvice`。`business-rules.md` 3.1の
  マッピング: `PermissionDeniedException`→403, `EntityNotFoundException`→404,
  `ValidationException`→400, 未捕捉`Exception`→500。全て`ErrorResponse`形式で返却）を生成。

### Step 3: ビジネスロジック単体テスト（PBT-01〜PBT-08, PBT-10）
`property-based-testing`拡張は有効（enforce all as blocking）。jqwikを使用する
（PBT-09確定）。`business-logic-model.md`のP1〜P8に加え、`common/dialect`は
Functional Design時点でスコープ外(P9)とされていたため、本Code Generation計画で
テスト可能な性質を新たに識別する（P9再識別 = P10〜P12）。
- [x] 3-1. **P1**（`AuditLogService.record`は内部DB書き込み失敗時も例外を伝播しない）:
  `AuditLogRepository`をモック化し任意の例外を投げさせるjqwik `@Property`テストを
  `AuditLogServiceTest`に生成。
- [x] 3-2. **P3〜P5**（`AuditLogService.search`のフィルタ正当性・降順整列・ページサイズ上限）:
  `@DataJpaTest`相当のSpring統合テストスライス上で、jqwik Arbitraryにより生成した
  `AuditLog`集合と`AuditLogFilterCriteria`の組み合わせに対する`@Property`テストを
  `AuditLogServiceTest`に生成。
- [x] 3-3. **P6**（`MailService.send`は送信失敗時も例外を伝播しない）:
  `JavaMailSender`をモック化し送信時に例外を投げさせるjqwik `@Property`テストを
  `MailServiceTest`に生成。
- [x] 3-4. **P7**（テンプレート変数が本文に反映され未解決プレースホルダが残らない）:
  jqwikでランダムな変数マップ（宛先名・URL文字列等）を生成し、生成された本文に
  各値が出現し`${`を含まないことを検証する`@Property`テストを`MailServiceTest`に生成。
- [x] 3-5. **P10**（`DialectStrategy.quoteIdentifier`は各方言で構文的に妥当な識別子クォートを
  返す。任意の英数字識別子に対し、クォート文字で開始・終了し内部にクォート文字の
  エスケープ漏れがない）、**P11**（`buildPagingClause`が生成する句は`limit`/`offset`の
  非負整数に対し常に構文的に妥当なSQL断片となる）、**P12**（`buildNullsOrderingClause`が
  `NullsOrder`の指定と実際の並び順意図が矛盾しない句を生成する）:
  4実装クラス（`MySqlDialectStrategy`等）それぞれに対しjqwik `@Property`テストを
  `DialectStrategyTest`（パラメータ化、各実装で共通の性質を検証）に生成。
- [x] 3-6. **P8**は本ステップではなくStep 5（API Layer Unit Testing）で検証する
  （`@ControllerAdvice`はHTTPレイヤの関心事のため）。（Step 6実施時に対応）
- [x] 3-7. **PBT-10（補完的テスト戦略）**の明示: 上記jqwikによるproperty-basedテストは
  業務ルールの不変条件・境界・オラクル比較を対象とし、API/Repository/Frontendレイヤの
  配線・契約確認（後述Step 5/8/11）は従来のexample-basedテスト（MockMvc, @DataJpaTest,
  Vitest+RTL）で行う方針を`aidlc-docs/construction/u1-platform-foundation/code/
  testing-summary.md`（Step 15で生成）に明記する。（方針表明のみ。実ファイルはStep 15で生成）

### Step 4: ビジネスロジックサマリ
- [x] 4-1. `aidlc-docs/construction/u1-platform-foundation/code/business-logic-summary.md`
  を生成し、Step 2・Step 3で生成したクラス一覧とP1〜P12の対応関係を表形式で記載する。

### Step 5: APIレイヤ生成
- [x] 5-1. `backend/src/main/java/cherry/mastermeister/audit/AuditLogController.java`
  （`@RestController`, `@RequestMapping("/api/audit-logs")`。`GET /api/audit-logs`
  （クエリパラメータ: dateFrom, dateTo, userId, eventCategory, eventType, page, pageSize）→
  `AuditLogService.search(...)`。管理者ロールチェックは`SecurityConfig`の
  `hasRole("ADMIN")`設定に委ね、Controller内では追加チェックしない
  （`frontend-components.md`の想定エンドポイントに準拠）を生成。

### Step 6: APIレイヤ単体テスト
- [x] 6-1. `AuditLogControllerTest`（`@WebMvcTest` + `spring-security-test`の
  `@WithMockUser`）: 正常系（絞り込みあり/なし、ページング）、管理者以外での403、
  未認証での401をexample-basedテストで検証。
- [x] 6-2. **P8**（`GlobalExceptionHandler`の例外→HTTPステータスマッピング、Oracle性質）:
  スタブController経由で`PermissionDeniedException`/`EntityNotFoundException`/
  `ValidationException`/汎用`Exception`をそれぞれ投げさせ、`business-rules.md` 3.1の
  マッピング表と一致することを検証するjqwik `@Property`テスト（例外種別4値の直積を
  Arbitraryで網羅）を`GlobalExceptionHandlerTest`に生成。

### Step 7: APIレイヤサマリ
- [x] 7-1. `aidlc-docs/construction/u1-platform-foundation/code/api-layer-summary.md`
  を生成し、エンドポイント一覧（パス・メソッド・認可要件・リクエスト/レスポンス形状）を記載。

### Step 8: リポジトリレイヤ生成
- [x] 8-1. `backend/src/main/java/cherry/mastermeister/audit/AuditLogRepository.java`
  （`JpaRepository<AuditLog, Long>` + Spring Data JPAの動的クエリメソッド、または
  `AuditLogService`から呼び出す`@Query`によるフィルタ検索メソッドを定義。
  `dateFrom`/`dateTo`/`userId`/`eventCategory`/`eventType`のnull許容組み合わせに対応）を生成。

### Step 9: リポジトリレイヤ単体テスト
- [x] 9-1. **P2**（書き込んだ`AuditLog`の全フィールドが読み出しで同一値として得られる、
  Round-trip）: `@DataJpaTest`（組み込みH2）上でjqwikによりランダムな`AuditLog`
  （全フィールドの境界値・null許容フィールド込み）を生成し保存→再読込→フィールド一致を
  検証する`@Property`テストを`AuditLogRepositoryTest`に生成。
- [x] 9-2. 基本CRUD・フィルタクエリメソッドのexample-basedテストを追加。

### Step 10: リポジトリレイヤサマリ
- [x] 10-1. `aidlc-docs/construction/u1-platform-foundation/code/repository-layer-summary.md`
  を生成し、`AuditLogRepository`のクエリメソッド一覧とインデックス設計を記載。

### Step 11: フロントエンドコンポーネント生成
- [x] 11-1. `frontend/src/api/apiClient.ts`（fetchラッパー。ベースURL、`Authorization`
  ヘッダ自動付与、共通エラーDTOパース、401受信時は`authStore`のログアウト処理呼び出し＋
  ログイン画面リダイレクト）。`data-testid`要件は本コンポーネント自体には該当しない
  （非UI）。
- [x] 11-2. `frontend/src/store/authStore.ts`（zustand。`currentUser: {id, email, role} |
  null`, `token: string | null`, ログイン/ログアウトアクション）。
- [x] 11-3. `frontend/src/hooks/useAuth.ts`, `frontend/src/hooks/usePagination.ts`。
- [x] 11-4. `frontend/src/routes/ProtectedRoute.tsx`（`requiredRole`prop対応）,
  `frontend/src/routes/AppRouter.tsx`（react-router-dom使用、`features/*/routes`集約。
  現時点では`auditLog/`のみ登録）。
- [x] 11-5. `frontend/src/components/` に `AppLayout.tsx`（`data-testid="app-layout-nav"`等）,
  `DataTable.tsx`（`data-testid="data-table-{列名}-header"`等、ソート可能列に
  `data-testid="data-table-sort-button"`パターン）, `Pagination.tsx`
  （`data-testid="pagination-prev-button"` / `pagination-next-button"` /
  `pagination-page-size-select"`）, `ToastNotification.tsx`
  （`data-testid="toast-notification-{severity}"`）, `ConfirmDialog.tsx`
  （`data-testid="confirm-dialog-confirm-button"` / `confirm-dialog-cancel-button"`）を
  自動化テスト対応（`data-testid`属性、`{component}-{element-role}`命名）で生成。
- [x] 11-6. `frontend/src/features/auditLog/` に
  `AuditLogPage.tsx`（`data-testid="audit-log-page-search-input"`等の下位要素経由）,
  `AuditLogFilterPanel.tsx`（`data-testid="audit-log-filter-date-from-input"` /
  `-date-to-input"` / `-user-select"` / `-category-select"` / `-type-select"` /
  `-search-button"`）, `AuditLogTable.tsx`（`DataTable`を利用、`eventCategory`ごとの
  バッジ色分けは`design-tokens.css`のCSS変数参照）, `api.ts`（`GET /api/audit-logs`
  呼び出し）, `types.ts`（`AuditLog`型定義、バックエンドDTOに対応）を生成。

### Step 12: フロントエンドコンポーネント単体テスト
- [x] 12-1. Vitest + React Testing Library で以下のexample-basedテストを生成:
  `apiClient`（401時の自動ログアウト呼び出し、エラーDTOパース）, `authStore`
  （状態遷移）, `useAuth`/`usePagination`, `ProtectedRoute`（未認証リダイレクト、
  `requiredRole`不一致時の挙動）, `DataTable`/`Pagination`/`ToastNotification`/
  `ConfirmDialog`（描画・イベントハンドラ呼び出し、`data-testid`経由でのクエリ）,
  `AuditLogPage`/`AuditLogFilterPanel`/`AuditLogTable`（初期表示、絞り込み送信、
  バリデーション — 開始日時>終了日時時のエラー表示）。

### Step 13: フロントエンドコンポーネントサマリ
- [x] 13-1. `aidlc-docs/construction/u1-platform-foundation/code/frontend-summary.md`
  を生成し、共通基盤コンポーネントと`auditLog/`機能のコンポーネント一覧・
  `data-testid`一覧を記載。

### Step 14: データベースマイグレーションスクリプト
- [x] 14-1. **該当なし（N/A）**: U1 NFR Design Question 5 = A の決定
  （`nfr-design-patterns.md` 4.1）により、内部DB(H2)のスキーマ管理はJPAの自動DDL生成
  （`spring.jpa.hibernate.ddl-auto`）に委ね、Flyway/Liquibase等のマイグレーションツールは
  導入しない。`docs/PROJECT_STRUCTURE.md`は本方針に合わせ`db/migration/`エントリを
  削除済み（Code Generation Step 1時点のdoc-sync、audit.md参照）。本ステップでは
  マイグレーションスクリプトを生成しない。

### Step 15: ドキュメント生成
- [ ] 15-1. Step 4/7/10/13で生成した各サマリに加え、
  `aidlc-docs/construction/u1-platform-foundation/code/testing-summary.md`
  （PBT-10補完的テスト戦略の説明、P1〜P12とテストクラスの対応表、
  example-basedテスト一覧）を生成する。

### Step 16: デプロイ成果物生成
- [ ] 16-1. `backend/build.gradle.kts`: `war`プラグイン追加、`bootWar`タスクが
  実行可能WARを生成するよう構成（12-factor、env-var設定）。
- [ ] 16-2. `backend/src/main/resources/application.yml`: 共通設定
  （`spring.datasource.url=jdbc:h2:file:...`、`spring.jpa.hibernate.ddl-auto`,
  `spring.mail.*`（環境変数プレースホルダ）+
  `spring.mail.properties.mail.smtp.connectiontimeout`/`mail.smtp.timeout`=5000,
  `spring.datasource.hikari.*`既定値, `mm.app.jwt.secret`（環境変数）,
  `mm.app.jwt.access-token-expiry`=10m, `mm.app.audit.default-page-size`=20,
  `mm.app.audit.page-size-options`=[20,50,100]）を追加。
- [ ] 16-3. `backend/src/main/resources/application-dev.yml`: 開発プロファイル
  （MailPit接続先、CORS有効化フラグ等）を追加。
- [ ] 16-4. `backend/src/main/resources/logback-spring.xml`: 標準出力プレーンテキスト、
  パターン `%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} [%thread] %-5level %logger{36} - %msg%n`
  （`nfr-requirements.md` 5.1）を生成。

---

## 完了基準
- 上記全ステップの生成物がワークスペースルート配下に作成され、対応する単体テストが
  生成されていること（実行・グリーン確認はBuild and Testステージで行う）。
- P1〜P12全ての性質にjqwik `@Property`テストが対応していること（PBT-02〜PBT-08準拠）。
- `aidlc-docs/construction/u1-platform-foundation/code/`配下に5つのサマリドキュメント
  （business-logic-summary.md, api-layer-summary.md, repository-layer-summary.md,
  frontend-summary.md, testing-summary.md）が生成されていること。