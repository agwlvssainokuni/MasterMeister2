# logical-components.md — U1: Platform Foundation

`nfr-design-patterns.md`に基づく論理コンポーネント一覧。

---

## 1. Security（`cherry.mastermeister.security`想定）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `SecurityConfig` | `@Configuration` | `SecurityFilterChain` Beanを定義。`JwtAuthenticationFilter`の追加位置、`permitAll()`エンドポイントの列挙、`AuthenticationEntryPoint`/`AccessDeniedHandler`の登録を行う |
| `JwtAuthenticationFilter` | `OncePerRequestFilter` | リクエストヘッダからアクセストークンを取得し、`JwtTokenValidator`で検証、`SecurityContextHolder`に認証情報を設定する。認証系エンドポイントはスキップする |
| `JwtTokenValidator` | Component | HS256署名検証・有効期限チェック（トークンの発行は行わない。発行はU2の`JwtTokenProvider`） |
| `RestAuthenticationEntryPoint` | `AuthenticationEntryPoint`実装 | 未認証アクセス時に401（JSON形式のエラーレスポンス）を返却する |
| `RestAccessDeniedHandler` | `AccessDeniedHandler`実装 | 認可失敗時に403（JSON形式のエラーレスポンス）を返却する |
| `WebConfig` | `@Configuration` | 開発プロファイルのみCORS設定（`localhost:5173`等の許可） |

---

## 2. RDBMS Dialect（`cherry.mastermeister.common.dialect`想定）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `RdbmsType` | Enum | `MYSQL` / `MARIADB` / `POSTGRESQL` / `H2` |
| `DialectStrategy` | インタフェース | ページング構文、識別子クォート等の方言差異を吸収する操作を定義。`getSupportedType()`で対応種別を返す |
| `MySqlDialectStrategy` / `MariaDbDialectStrategy` / `PostgreSqlDialectStrategy` / `H2DialectStrategy` | Component（`DialectStrategy`実装） | 各RDBMS種別ごとの方言差異吸収ロジック |
| `DialectStrategyFactory` | Component | `Map<RdbmsType, DialectStrategy>`（Spring自動集約）を保持し、`RdbmsType`から該当実装を解決する |

---

## 3. Audit（`cherry.mastermeister.audit`想定）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `AuditLog` | JPA Entity | `@Table(indexes = {@Index(columnList = "occurredAt"), @Index(columnList = "userId"), @Index(columnList = "eventCategory,eventType")})` |
| `AuditLogService` | Service | `record()`を`@Transactional(propagation = Propagation.REQUIRES_NEW)`で実行し、例外は`try-catch`で捕捉してログ出力のみ行う（主処理へ非伝播） |

---

## 4. Mail（`cherry.mastermeister.mail`想定）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `MailService` | Service | `JavaMailSender`（Spring Boot自動設定Bean）を利用してメール送信。送信例外は`try-catch`で捕捉しログ出力のみ行う（呼び出し元へ非伝播） |
| （`MailConfig`は設けない） | — | `spring-boot-starter-mail`の`MailSenderAutoConfiguration`に委ねる |

---

## 5. 設定ファイル

| ファイル | 内容 |
|---|---|
| `application.yml` | `spring.mail.host`/`port`/`username`/`password`、`spring.mail.properties.mail.smtp.connectiontimeout`/`mail.smtp.timeout`（5秒）、`spring.datasource.hikari.*`（既定値）、`spring.jpa.hibernate.ddl-auto`、`spring.datasource.url`（`jdbc:h2:file:...`） |
| `logback-spring.xml` | `src/main/resources/`配下に配置。標準出力へのプレーンテキスト出力、パターン: `%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} [%thread] %-5level %logger{36} - %msg%n` |

---

## 6. 未確定・後続ユニットへの引き継ぎ事項

- リフレッシュトークンの永続化エンティティ、発行・ローテーション・失効ロジック、
  `AuthenticationService`/`JwtTokenProvider`はU2（Auth & User Registration）の
  Functional Design/NFR Requirementsで確定する（`nfr-requirements.md` 1.1参照）。
- 認証系エンドポイントの具体的なパス一覧もU2のFunctional Designで確定する
  （`nfr-design-patterns.md` 1.2参照）。