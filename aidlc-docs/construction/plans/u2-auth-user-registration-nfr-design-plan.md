# NFR Design Plan — U2: Auth & User Registration

## ユニット適用可否の判定

`construction/nfr-design.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`）に基づき、
U2は **実行（EXECUTE）** と判定。

- U2のNFR Requirementsは、BCryptコストファクタの設定方式、不透明トークン（32バイト）生成方式、
  クライアント側`sessionStorage`同期方式、初期管理者ブートストラップの実装方式、トークン検索
  インデックスという具体的な非機能決定を含んでおり、これらを設計パターン・論理コンポーネントへ
  落とし込む必要がある。

- **Resilience Patterns**: 本ユニット固有の新規論点はない。U1の`nfr-design-patterns.md` 3.1
  （try-catch + `REQUIRES_NEW`によるメール送信・監査記録の障害分離）は`MailService`/
  `AuditLogService`という共通コンポーネント経由でU2からも利用されるため、U2側で追加のパターンを
  定義する必要はない（個別質問は設けない）。

→ 上記の中から5問を構成する。

---

## Step 1: NFR Requirements成果物の分析

- [x] `nfr-requirements.md`（Security/Scalability/PBT）確認
- [x] `tech-stack-decisions.md`（7件の決定事項、sessionStorage採用の補足）確認
- [x] U1 `nfr-design-patterns.md`（JWTフィルタチェーン構成、障害分離パターン、監査ログ
      インデックス実装パターン、設定配置パターン）を前提として参照し、重複質問を避ける

---

## 成果物生成タスク（`construction/nfr-design.md` Step 6）

- [ ] `aidlc-docs/construction/u2-auth-user-registration/nfr-design/nfr-design-patterns.md`
- [ ] `aidlc-docs/construction/u2-auth-user-registration/nfr-design/logical-components.md`

---

## Question 1: Security / Logical Components — PasswordEncoderの構成方式

`nfr-requirements.md` 1.1でBCrypt strength=10（`mm.app.security.password-encoder-strength`で
設定可能）と決定済み。Spring Bean構成方式を確認したい。

A. `SecurityConfig`（U1既存）に`@Bean PasswordEncoder passwordEncoder(@Value("${mm.app.security.
   password-encoder-strength:10}") int strength)`を追加し、`new BCryptPasswordEncoder(strength)`を
   返す。専用の`@Configuration`クラスは新設しない（既存の`SecurityConfig`に集約する方が、
   認証関連設定の置き場所として一貫する）（推奨）
B. 専用の`SecurityBeansConfig`等、新しい`@Configuration`クラスを新設する
C. その他（具体的な構成方式を指定）

[Answer]:

---

## Question 2: Security / Logical Components — 不透明トークン生成の実装方式

`nfr-requirements.md` 1.5で`RegistrationToken`/`RefreshToken`はともに32バイトの`SecureRandom`
バイト列をURL-safe base64エンコードすると決定済み。生成ロジックの配置方式を確認したい。

A. 共通の`TokenGenerator`コンポーネント（`cherry.mastermeister.userregistration`または`auth`
   パッケージ内、両パッケージから参照可能な位置に配置）を1つ設け、
   `generate(): String`（平文トークン）と`hash(String plainToken): String`（SHA-256ハッシュ、
   DB保存用）を提供する。`RegistrationTokenService`/`RefreshTokenService`（いずれもCode
   Generationで具体化）の双方がこれを利用し、生成・ハッシュ化ロジックの重複を避ける（推奨）
B. `RegistrationTokenService`・`RefreshTokenService`それぞれが個別に`SecureRandom`呼び出しを
   実装する（重複実装）
C. その他（具体的な配置方式を指定）

[Answer]:

---

## Question 3: Security / Logical Components — 初期管理者ブートストラップの実装パターン

`domain-entities.md`で承認済みの管理者ブートストラップ（起動時に`mm.app.admin.bootstrap.email`/
`password`から1件だけADMIN Userを作成、`nfr-requirements.md` 1.4でbcryptハッシュ化も決定済み）の
実装パターンを確認したい。

A. `ApplicationRunner`実装（`cherry.mastermeister.auth`パッケージ、例:
   `AdminBootstrapRunner`）とし、起動時に`mm.app.admin.bootstrap.email`に一致する`User`の
   存在有無を`UserRepository`で確認する（存在すれば何もしない＝冪等）。存在しなければ
   `PasswordEncoder`でハッシュ化し、`role = ADMIN`, `status = APPROVED`の`User`を作成する。
   `mm.app.admin.bootstrap.email`/`password`が未設定（空文字列）の場合は何も実行しない
   （開発環境以外でのブートストラップ強制を避ける）（推奨）
B. `CommandLineRunner`を使用する（`ApplicationRunner`との実質差異はほぼないが、既存コードとの
   一貫性のため`ApplicationRunner`系を優先する方針をここで確定する）
C. その他（具体的な実装パターンを指定）

[Answer]:

---

## Question 4: Scalability/Performance Patterns — トークンインデックスの実装方式

`nfr-requirements.md` 2.1で`RegistrationToken.tokenHash`/`RefreshToken.tokenHash`はunique制約の
みでインデックスを賄うと決定済み。U1の`nfr-design-patterns.md` 4.1（`AuditLog`の
`@Table(indexes = {...})`パターン）との整合を確認したい。

A. `RegistrationToken`/`RefreshToken`エンティティの`tokenHash`列に`@Column(unique = true,
   nullable = false)`を付与する（unique制約により暗黙的にインデックスが張られるため、
   `@Table(indexes = {...})`による明示的なインデックス定義は不要。U1の監査ログのような
   非unique列への複合インデックスとは異なるケース）（推奨）
B. `@Column(unique = true)`に加え、`@Table(indexes = {...})`でも明示的に同じ列へのインデックスを
   重複定義する
C. その他（具体的な実装方式を指定）

[Answer]:

---

## Question 5: Security — クライアント側`sessionStorage`同期の実装パターン

`nfr-requirements.md` 1.2で`authStore`は`sessionStorage`にアクセストークン・リフレッシュトークンを
保存すると決定済み。フロントエンドでの同期実装方式を確認したい。

A. 状態管理ライブラリ（`zustand`、U1 Code Generationで導入済み）の永続化ミドルウェア
   （`zustand/middleware`の`persist`、`storage`に`sessionStorage`を指定）を利用し、`authStore`の
   状態変更を自動的に`sessionStorage`と同期させる。ログアウト時・reuse detection時は
   `authStore`の状態をリセットするアクションを呼び出し、`persist`ミドルウェアが自動的に
   `sessionStorage`からも削除する（推奨。手動での`sessionStorage.setItem`/`getItem`呼び出しを
   `authStore`内に散在させずに済む）
B. `authStore`内でログイン成功時・ログアウト時に手動で`sessionStorage.setItem`/`removeItem`を
   呼び出す（`persist`ミドルウェアは使用しない）
C. その他（具体的な実装方式を指定）

[Answer]:

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。