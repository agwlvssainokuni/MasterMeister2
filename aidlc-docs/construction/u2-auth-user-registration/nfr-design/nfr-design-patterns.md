# nfr-design-patterns.md — U2: Auth & User Registration

`u2-auth-user-registration-nfr-design-plan.md`（Question 1〜5、全回答A）に基づく設計パターン。

---

## 1. Security Patterns

### 1.1 PasswordEncoderの構成方式（Question 1）

- `SecurityConfig`（U1既存、`cherry.mastermeister.security`）に`@Bean`メソッドを追加する:
  `PasswordEncoder passwordEncoder(@Value("${mm.app.security.password-encoder-strength:10}")
  int strength)`が`new BCryptPasswordEncoder(strength)`を返す。
- 専用の`@Configuration`クラスは新設しない。認証関連のBean定義は`SecurityConfig`に集約する。

### 1.2 不透明トークン生成の実装方式（Question 2、ユーザレビューによりパッケージ確定）

- 共通コンポーネント`OpaqueTokenGenerator`を`cherry.mastermeister.security`パッケージに配置する。
  `auth`（`RefreshTokenService`）・`userregistration`（`RegistrationTokenService`）のいずれの
  業務ロジックにも属さない認証基盤の道具であり、`security`パッケージから両パッケージへ対称に
  参照させることで、`auth`↔`userregistration`間の依存発生を避ける（U1で`PasswordEncoder`
  Beanを`SecurityConfig`＝`security`パッケージに置いた方針と一貫させる）。
- `generate(): String`（32バイトの`SecureRandom`バイト列をURL-safe base64エンコードした平文
  トークン）と`hash(String plainToken): String`（SHA-256ハッシュ、DB保存用の`tokenHash`列に
  使用）を提供する。
- `RegistrationTokenService`・`RefreshTokenService`（いずれもCode Generationで具体化）の双方が
  `OpaqueTokenGenerator`を利用し、生成・ハッシュ化ロジックの重複を避ける。
- 命名は`OpaqueTokenGenerator`とし、`JwtTokenProvider`（アクセストークン＝JWT用、同じく
  `security`パッケージに配置。1.2.1参照）と明確に区別する（ユーザレビューによる命名修正、
  `u2-auth-user-registration-nfr-design-plan.md` Question 2参照）。

### 1.2.1 JwtTokenProviderの責務所在の訂正

- `u2-auth-user-registration/functional-design/business-rules.md`・`business-logic-model.md`で
  使用されている`JwtTokenProvider`（アクセストークン発行）は、U1側成果物
  （`u1-platform-foundation/nfr-requirements/nfr-requirements.md` 1.1、`tech-stack-decisions.md`、
  `nfr-design/logical-components.md`）で一貫して**U2（本ユニット）の責務**と確定している。
  本ユニットの`logical-components.md`（本改訂前の版）で「U1責務境界」としていたのは誤記であり、
  本改訂で「U2責務、`security`パッケージに配置」に訂正する（`OpaqueTokenGenerator`のパッケージ
  検討に伴いユーザレビューで判明・訂正）。
- `JwtTokenProvider`は`OpaqueTokenGenerator`と同様、`auth`固有の業務ロジックではなく認証基盤の
  道具であるため、`cherry.mastermeister.security`パッケージに配置する（U1の
  `JwtTokenValidator`＝検証専用、と対になる発行専用コンポーネント）。

### 1.3 初期管理者ブートストラップの実装パターン（Question 3、domain-entities.mdとの整合修正あり）

- `ApplicationRunner`実装（`cherry.mastermeister.auth`パッケージ、例: `AdminBootstrapRunner`）とし、
  起動時に`mm.app.admin.bootstrap.email`/`mm.app.admin.bootstrap.password`が両方設定されている
  場合のみ処理を行う（いずれか未設定なら何もしない）。
- **冪等性判定**: `UserRepository`で`role = ADMIN`の`User`が1件も存在しないことを確認する
  （**Question 3の選択肢文言は「ブートストラップメールアドレスに一致するUserの存在有無」と
  記載していたが、これは`domain-entities.md`「設計判断」節で既に承認済みの確定内容——判定条件は
  「`role = ADMIN`の行が1件も存在しない場合のみ作成」——と食い違うため、承認済みの
  `domain-entities.md`を正として本設計パターンを補正した**）。ADMIN行が既に1件でも存在すれば
  何もしない。
- 上記のいずれの条件も満たす場合のみ、`PasswordEncoder`でパスワードをハッシュ化し、
  `role = ADMIN`, `status = APPROVED`の`User`を1件作成する。

### 1.4 クライアント側`sessionStorage`同期の実装パターン（Question 5）

- `authStore`（U1で新設済み、zustand製、U2で`refreshToken`/`setTokens`/`clearTokens`を拡張）に
  `zustand/middleware`の`persist`ミドルウェアを適用し、`storage`オプションに`sessionStorage`
  （`createJSONStorage(() => sessionStorage)`）を指定する。
- `setTokens(accessToken, refreshToken)`呼び出し時の状態変更が自動的に`sessionStorage`へ同期される。
- `clearTokens()`（ログアウト時、リフレッシュ失敗時、reuse detection検知時）呼び出しで状態がリセット
  され、`persist`ミドルウェアが`sessionStorage`からも自動的に該当エントリを削除する。
- `authStore`内での手動`sessionStorage.setItem`/`getItem`/`removeItem`呼び出しは行わない
  （`persist`ミドルウェアに委譲する）。

---

## 2. Scalability/Performance Patterns

### 2.1 トークンインデックスの実装方式（Question 4）

- `RegistrationToken`/`RefreshToken`エンティティの`tokenHash`列に`@Column(unique = true,
  nullable = false)`を付与する。unique制約により暗黙的にインデックスが張られるため、
  `@Table(indexes = {...})`による明示的なインデックス定義は行わない。
- U1の`AuditLog`（`occurredAt`/`userId`/`eventCategory`+`eventType`という非unique列への
  複合インデックス、`nfr-design-patterns.md` 4.1）とは異なるケースであり、`tokenHash`は
  unique制約のみで十分と判断する。

---

## 3. Resilience Patterns

- 本ユニット固有の新規パターンはない。`MailService`/`AuditLogService`経由の障害分離は
  U1の`nfr-design-patterns.md` 3.1（try-catch + `REQUIRES_NEW`）をそのまま踏襲する
  （`u2-auth-user-registration-nfr-design-plan.md`のユニット適用可否判定を参照）。

---

## 4. PBT適用性（property-based-testing拡張）

- 本ステージ（NFR Design）ではPBT-09（フレームワーク選定）を含むいずれのPBTルールも対象外
  （`property-based-testing.md`のEnforcement Integration表: NFR Designは対象外ステージ）。
  U1のNFR Design承認時と同様、N/Aとして扱う。