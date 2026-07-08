# nfr-design-patterns.md — U1: Platform Foundation

`u1-platform-foundation-nfr-design-plan.md`（Question 1〜6、全回答A）に基づく設計パターン。

---

## 1. Security Patterns

### 1.1 JWTフィルタチェーン構成（Question 1）

- `JwtAuthenticationFilter`（`OncePerRequestFilter`継承）を`SecurityFilterChain`に
  `UsernamePasswordAuthenticationFilter`より前に追加する。
- `Authorization: Bearer <token>`ヘッダからアクセストークンを取得し、HS256署名・有効期限を検証、
  成功時は`SecurityContextHolder`に認証情報を設定する。
- 認証失敗（トークン欠落・署名不正・期限切れ）時は`AuthenticationEntryPoint`実装が401
  （Unauthorized）を返却する。
- 認可失敗（認証済みだがロール不足）時は`AccessDeniedHandler`実装が403（Forbidden）を返却する。
- 公開エンドポイント（登録、ログイン、リフレッシュ、ヘルスチェック等）は
  `SecurityFilterChain`の`requestMatchers().permitAll()`で明示する。

### 1.2 リフレッシュエンドポイントの除外パターン（Question 2）

- 認証系エンドポイント（`/api/auth/**`のうちログイン・登録・リフレッシュ）は`permitAll()`と
  `JwtAuthenticationFilter`側の`shouldNotFilter`（またはパスマッチによるスキップ）の両方で
  アクセストークン検証対象外として扱う。
- 具体的なエンドポイントパス一覧はU2（Auth & User Registration）のFunctional Designで確定する。
  U1では「認証系エンドポイントはpermitAllパターンで除外する」という設計方針のみを確定する。

### 1.3 管理者専用APIの認可方式（規約、U2レビュー時に確定）

- 管理者専用エンドポイントは、`/api/admin/**`のような包括プレフィクスではなく、
  `SecurityConfig`の`requestMatchers()`で**個別のパスパターンごとに`hasRole("ADMIN")`を
  明示指定**する（既存実装 `requestMatchers("/api/audit-logs/**").hasRole("ADMIN")`と同方式）。
- 理由: 管理者専用アクションが公開/一般ユーザ向けエンドポイントと同一リソース階層に属する
  ケース（例: `GET /api/registrations/pending`、`POST /api/registrations/{userId}/approve`は
  `POST /api/registrations`と同じ`registrations`リソースの一部）があり、これを`/api/admin/**`に
  分離するとREST階層が崩れるため。APIパスは「操作対象リソース」を表現し、「誰がアクセス
  できるか」は`SecurityConfig`の認可ルールで表現する、という役割分担を維持する。
- フロントエンドのルーティング規約（管理者専用画面は`/admin`プレフィクス、
  `frontend-components.md` routes/参照）とは独立した方針であり、画面パスとAPIパスが
  一致しないことを許容する。

---

## 2. Logical Components / Design Patterns（Tech Stack関連）

### 2.1 DialectStrategyの解決パターン（Question 3）

- Strategyパターン: `DialectStrategy`インタフェース + RDBMS種別ごとの実装クラス。
- `DialectStrategyFactory`は`Map<RdbmsType, DialectStrategy>`（Springが複数実装Beanを自動的に
  Mapへ集約する仕組みを利用）を保持し、接続設定の`RdbmsType`をキーに該当実装を返す。
- 各実装クラスは自身が対応する`RdbmsType`を返すメソッド（例: `getSupportedType()`）を持ち、
  Factory初期化時にMapのキーとして登録される。
- 新規RDBMS対応時は実装クラスを1つ追加するだけで済み、Factory自体の変更は不要。

---

## 3. Resilience Patterns

### 3.1 メール送信・監査記録の障害分離（Question 4）

- `resiliency-baseline`拡張は現時点で無効のため、Resilience4j等の専用ライブラリは導入しない。
- メール送信: `MailService`内で`try-catch`により送信例外を捕捉し、アプリケーションログに
  エラー出力する。呼び出し元（登録処理等）へは例外を伝播させない。
- 監査記録: `AuditLogService.record()`は`@Transactional(propagation = Propagation.REQUIRES_NEW)`
  で主処理のトランザクションから分離する。記録失敗時も`try-catch`で捕捉し、ログ出力のみ行い
  主処理は継続する。

---

## 4. Performance / Scalability Patterns

### 4.1 監査ログインデックスの実装パターン（Question 5）

- `AuditLog`エンティティに`@Table(indexes = {...})`アノテーションで以下のインデックスを定義する:
  - `occurredAt`（単一、降順ソート・範囲検索用）
  - `userId`（単一）
  - `eventCategory` + `eventType`（複合）
- H2のスキーマ自動生成機能（`spring.jpa.hibernate.ddl-auto`）に委ね、Flyway/Liquibase等の
  マイグレーションツールは導入しない（本要件では未導入のため）。

---

## 5. Configuration Placement Patterns（Question 6）

- **メール送信タイムアウト**: 専用の`MailConfig`（`@Configuration`）クラスは設けない。
  `spring-boot-starter-mail`の自動設定（`MailSenderAutoConfiguration`）に委ね、
  `application.yml`の`spring.mail.properties.mail.smtp.connectiontimeout` /
  `mail.smtp.timeout`を5秒に設定する。
- **内部DB接続プール**: `application.yml`の`spring.datasource.hikari.*`で既定値を明示する。
  専用の`@Configuration`クラスは設けない。
- **ロギングパターン**: `logback-spring.xml`（`src/main/resources/`）に
  `nfr-requirements.md` 5.1のパターン文字列
  （`%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} [%thread] %-5level %logger{36} - %msg%n`）を設定する。