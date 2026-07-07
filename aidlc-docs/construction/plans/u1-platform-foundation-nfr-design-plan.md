# NFR Design Plan — U1: Platform Foundation

## ユニット適用可否の判定

`construction/nfr-design.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`）に基づき、
U1は **実行（EXECUTE）** と判定。

- U1のNFR RequirementsはJWT検証方式（HS256、アクセス/リフレッシュトークンのTTL、U1/U2責務境界）、
  `DialectStrategy`実装方式、H2永続化モード、監査ログのインデックス方針、メール送信の
  タイムアウト/リトライ方針、ロギング方式という具体的な非機能決定を含んでおり、これらを
  設計パターン・論理コンポーネントへ落とし込む必要がある。

---

## Step 1: NFR Requirements成果物の分析

- [x] `nfr-requirements.md`（Security/Tech Stack/Scalability/Reliability/Maintainability/PBT）確認
- [x] `tech-stack-decisions.md`（16件の決定事項、U1/U2責務境界の記録）確認

---

## 成果物生成タスク（`construction/nfr-design.md` Step 6）

- [ ] `aidlc-docs/construction/u1-platform-foundation/nfr-design/nfr-design-patterns.md`
- [ ] `aidlc-docs/construction/u1-platform-foundation/nfr-design/logical-components.md`

---

## Question 1: Security Patterns — JWTフィルタチェーンの構成

`nfr-requirements.md` 1.1により、`SecurityConfig`はアクセストークン（HS256）検証のみを担当する。
Spring Securityのフィルタチェーン構成方式を確認したい。

A. `OncePerRequestFilter`を継承した`JwtAuthenticationFilter`を`SecurityFilterChain`に
   `UsernamePasswordAuthenticationFilter`より前に追加する。認証失敗時は`AuthenticationEntryPoint`で
   401を返却し、認可失敗（ロール不足）時は`AccessDeniedHandler`で403を返却する。公開エンドポイント
   （登録、ログイン、リフレッシュ、ヘルスチェック等）は`requestMatchers().permitAll()`で明示する
   （推奨。Spring Securityの標準的なステートレスJWT構成パターン）
B. Spring Securityを使わず、独自の`HandlerInterceptor`でJWT検証を行う
C. その他（具体的な構成を指定）

[Answer]:

---

## Question 2: Security Patterns — リフレッシュエンドポイントの扱い

`nfr-requirements.md` 1.1により、`/api/auth/refresh`等のリフレッシュ関連エンドポイントは
アクセストークン検証の対象外として扱う必要がある（U2が実装するが、U1の`SecurityConfig`が
フィルタチェーンとして経路を用意する）。

A. `SecurityConfig`のフィルタチェームで`/api/auth/refresh`, `/api/auth/login`,
   `/api/auth/register**`等の認証系エンドポイントを`permitAll()`とし、`JwtAuthenticationFilter`は
   これらのパスをスキップする（`shouldNotFilter`等で判定）。具体的なパス一覧はU2のFunctional
   Designで確定するため、U1のNFR Designでは「認証系エンドポイントはpermitAllパターンで除外する」
   という設計方針のみを確定する（推奨）
B. U1のこの段階で具体的なエンドポイントパス一覧を確定する
C. その他（具体的な方針を指定）

[Answer]:

---

## Question 3: Logical Components — DialectStrategyの解決方式

`nfr-requirements.md` 2.1により`DialectStrategy`はStrategyパターンで実装するが、対象RDBMS種別
（接続設定ごとに異なる）からどう実装クラスを解決するかを確認したい。

A. `DialectStrategyFactory`が`RdbmsType`列挙型（`MYSQL`/`MARIADB`/`POSTGRESQL`/`H2`）を引数に
   受け取り、`Map<RdbmsType, DialectStrategy>`（Springの複数Bean自動注入 + 各実装が
   `getSupportedType()`のようなメソッドで自身の対応種別を返す構成）から該当実装を返す
   （推奨。Spring標準のStrategy解決パターンで、新規RDBMS対応時の拡張も容易）
B. `if`/`switch`文で直接インスタンス化する
C. その他（具体的な解決方式を指定）

[Answer]:

---

## Question 4: Resilience Patterns — メール送信・監査記録の障害分離実装方式

`nfr-requirements.md` 4.1（メール送信: 5秒タイムアウト・リトライなし）および
`business-rules.md`（監査記録失敗は主処理をブロックしない、別トランザクション）を実装レベルの
パターンに落とし込みたい。`resiliency-baseline`拡張は現時点で無効（将来オプトイン予定）。

A. サーキットブレーカー等の高度なレジリエンスライブラリ（Resilience4j等）は導入せず、
   `try-catch`による例外捕捉+ログ出力のみで障害分離を実現する（メール送信・監査記録の双方）。
   監査記録は`REQUIRES_NEW`トランザクション伝播で主処理のトランザクションから分離する
   （推奨。`resiliency-baseline`未導入の現段階では過剰設計を避ける）
B. Resilience4j等のライブラリを導入し、サーキットブレーカー/リトライパターンを適用する
C. その他（具体的な実装方式を指定）

[Answer]:

---

## Question 5: Performance/Scalability Patterns — 監査ログインデックスの実装方式

`nfr-requirements.md` 3.1で確定したインデックス方針（`occurredAt`単一、`userId`単一、
`eventCategory`+`eventType`複合）の実装方式を確認したい。

A. JPAエンティティ`AuditLog`に`@Table(indexes = {...})`アノテーションで3つのインデックスを
   直接定義する（H2の自動DDL生成に委ねる。マイグレーションツール（Flyway/Liquibase）は
   本要件では未導入のため、H2のスキーマ自動生成機能をそのまま利用する）（推奨）
B. Flyway/Liquibaseを導入し、明示的なDDLマイグレーションスクリプトでインデックスを管理する
C. その他（具体的な実装方式を指定）

[Answer]:

---

## Question 6: Logical Components — MailConfig / HikariCP / Logbackの設定コンポーネント化

`nfr-requirements.md`のメール送信タイムアウト（5秒）、H2接続プール（既定HikariCP）、
ロギングパターン（正規表現パース対応レイアウト）を、それぞれどのコンポーネント/設定ファイルに
落とし込むかを確認したい。

A. (1) `MailConfig`（`@Configuration`）で`JavaMailSender`のBeanを定義し、
   `mail.smtp.connectiontimeout`/`mail.smtp.timeout`を5秒に設定する。
   (2) 内部DBの接続プールは`application.yml`の`spring.datasource.hikari.*`で既定値を明示し、
   個別の`@Configuration`クラスは設けない。
   (3) ロギングパターンは`logback-spring.xml`（`src/main/resources/`）に
   `nfr-requirements.md` 5.1のパターン文字列を設定する。
   （推奨。Spring Bootの標準的な設定ファイル配置に従う）
B. 全て`application.yml`のみで完結させ、専用の`@Configuration`クラス/`logback-spring.xml`は
   設けない
C. その他（具体的な配置方針を指定）

[Answer]:

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。