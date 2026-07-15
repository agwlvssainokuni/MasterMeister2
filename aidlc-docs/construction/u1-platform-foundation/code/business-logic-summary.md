# business-logic-summary.md — U1: Platform Foundation

Step 2（ビジネスロジック生成）・Step 3（ビジネスロジック単体テスト）で生成したクラス一覧と、
`business-logic-model.md`のP1〜P12（テスト可能な性質）との対応関係。

## 生成クラス一覧（Step 2）

| パッケージ | クラス/インタフェース | 役割 |
|---|---|---|
| `common.exception` | `PermissionDeniedException`, `EntityNotFoundException`, `ValidationException` | 共通業務例外（`RuntimeException`独立サブクラス） |
| `common` | `PageRequest`, `PageResult<T>`, `ErrorResponse` | ページング・共通エラーDTO |
| `common.dialect` | `RdbmsType`, `SortDirection`, `NullsOrder`, `SchemaResolutionMode` | 方言関連の列挙型 |
| `common.dialect` | `DialectStrategy`（interface） | 対象RDBMS方言吸収の戦略インタフェース |
| `common.dialect` | `MySqlDialectStrategy`, `MariaDbDialectStrategy`, `PostgreSqlDialectStrategy`, `H2DialectStrategy` | 方言別実装（識別子クォート・ページング句・NULLS整列句） |
| `common.dialect` | `DialectStrategyFactory` | `RdbmsType`→`DialectStrategy`の解決 |
| `security` | `JwtClaims`, `JwtValidationException` | JWTクレーム・検証例外 |
| `security` | `JwtTokenValidator` | HS256検証（jjwt 0.12） |
| `security` | `JwtAuthenticationFilter` | `OncePerRequestFilter`によるJWT認証 |
| `security` | `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler` | 401/403のJSONエラー応答 |
| `security` | `SecurityConfig` | `SecurityFilterChain`定義 |
| `config` | `WebConfig` | 開発環境向けCORS設定 |
| `config` | `GlobalExceptionHandler` | `@RestControllerAdvice`（共通例外→HTTPステータス変換） |
| `audit` | `EventCategory`, `EventType`, `Result` | 監査ログ列挙型 |
| `audit` | `AuditLog`（JPA entity） | 監査ログエンティティ |
| `audit` | `AuditLogFilterCriteria` | 検索フィルタ条件 |
| `audit` | `AuditLogService` | 監査記録（`record`）・検索（`search`） |
| `mail` | `MailNotificationType` | メール通知種別 |
| `mail` | `MailService` | Thymeleafテンプレートによるメール送信 |

## 生成テストクラス一覧（Step 3）

| テストクラス | 検証方式 |
|---|---|
| `audit.AuditLogServiceTest` | jqwik `@Property`（`@DataJpaTest`相当のSpring統合スライス、jqwik-spring経由） |
| `mail.MailServiceTest` | jqwik `@Property`（POJO単体、`JavaMailSender`モック） |
| `common.dialect.DialectStrategyTest` | jqwik `@Property`（4方言実装をパラメータ化） |

## P1〜P12対応表

| # | 対象 | 検証テストクラス | 状態 |
|---|---|---|---|
| P1 | `AuditLogService.record`が内部DB書き込み失敗時も例外を伝播しない | `AuditLogServiceTest` | 実装済み（Step 3） |
| P2 | `AuditLog`のDB書き込み/読み出しラウンドトリップ | `AuditLogRepositoryTest`（予定） | Step 9で実装（Repository層） |
| P3 | `AuditLogService.search`のフィルタ正当性 | `AuditLogServiceTest` | 実装済み（Step 3） |
| P4 | `AuditLogService.search`の`occurredAt`降順整列 | `AuditLogServiceTest` | 実装済み（Step 3） |
| P5 | `AuditLogService.search`のページサイズ上限 | `AuditLogServiceTest` | 実装済み（Step 3） |
| P6 | `MailService.send`が送信失敗時も例外を伝播しない | `MailServiceTest` | 実装済み（Step 3） |
| P7 | テンプレート変数が本文に反映され未解決プレースホルダが残らない | `MailServiceTest` | 実装済み（Step 3） |
| P8 | `@ControllerAdvice`共通例外変換のHTTPステータスマッピング一致 | `GlobalExceptionHandlerTest`（予定） | Step 6で実装（API層） |
| P9 | （`common.dialect`はFunctional Designスコープ外として再識別） | — | P10〜P12として再識別済み |
| P10 | `DialectStrategy.quoteIdentifier`の識別子クォート構文的妥当性 | `DialectStrategyTest` | 実装済み（Step 3） |
| P11 | `DialectStrategy.buildPagingClause`のSQL断片構文的妥当性 | `DialectStrategyTest` | 実装済み（Step 3） |
| P12 | `DialectStrategy.buildNullsOrderingClause`の整列意図との整合性 | `DialectStrategyTest` | 実装済み（Step 3） |

**補足**: P2はStep 8（Repository Layer Generation）でのRepository実装完了後、Step 9
（Repository Layer Unit Testing）で検証する。P8はStep 5（API Layer Generation）での
Controller実装完了後、Step 6（API Layer Unit Testing）で検証する。PBT-10（補完的テスト
戦略の明示）はStep 15で`testing-summary.md`として文書化する。

---

## 2026-07-15変更要求（接続コンテキストのグローバル化）による追加

| # | 対象 | 検証テストクラス | 状態 |
|---|---|---|---|
| P13 | `connectionStore`の状態遷移Invariant | `connectionStore.test.ts` | 実装済み |
| P14 | `AppLayout`接続切替時ナビゲーションのパターンマッチOracle | `AppLayout.test.tsx` | 実装済み |

`business-logic-model.md`のFunctional Design改訂時点では便宜上P10・P11として記載していたが、
本ドキュメントで既にP10〜P12（`DialectStrategy`系、Code Generation時に新規識別）を使用済みで
あることが判明したため、P13・P14に訂正した（`business-logic-model.md`も合わせて訂正済み）。