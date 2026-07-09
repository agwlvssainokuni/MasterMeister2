# U2 Auth & User Registration - リポジトリレイヤサマリ

Step 8（リポジトリレイヤ生成。実装順の都合上、Step 2と同時に前倒しで生成済み）・
Step 9（リポジトリレイヤ単体テスト）で生成した3リポジトリの一覧。

## `UserRepository`（`cherry.mastermeister.userregistration`）

`JpaRepository<User, Long>` を継承。標準の`save`/`saveAll`/`findById`/`delete`/`deleteAll`等に
加え、以下のクエリメソッドを定義する。

| メソッド | 説明 |
|---|---|
| `Optional<User> findByEmail(String email)` | ログイン時のユーザ特定、登録申請時の既存ユーザ確認に使用 |
| `long countByRole(Role role)` | `AdminBootstrapRunner`の冪等性判定（`role = ADMIN`が1件も存在しないことの確認）に使用 |
| `List<User> findByStatusOrderByCreatedAtAsc(UserStatus status)` | 承認待ち一覧（`UserRegistrationService.listPendingUsers`）の取得に使用。`status = PENDING_APPROVAL`を渡し、申請日時の古い順で返す |

## `RegistrationTokenRepository`（`cherry.mastermeister.userregistration`）

`JpaRepository<RegistrationToken, Long>` を継承。

| メソッド | 説明 |
|---|---|
| `Optional<RegistrationToken> findByTokenHash(String tokenHash)` | 登録完了時（フロー2）のトークン検証に使用 |
| `Optional<RegistrationToken> findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull(String email)` | 同一メールアドレスへの再送信時（`business-rules.md` 1.2）、無効化すべき旧トークンの検索に使用 |

## `RefreshTokenRepository`（`cherry.mastermeister.auth`）

`JpaRepository<RefreshToken, Long>` を継承。

| メソッド | 説明 |
|---|---|
| `Optional<RefreshToken> findByTokenHash(String tokenHash)` | リフレッシュ時（フロー5）のトークン検証に使用 |
| `List<RefreshToken> findByFamilyId(String familyId)` | reuse detection発動時（P9）、同一`familyId`の全行を一括失効させるために使用 |

## インデックス設計

`nfr-design-patterns.md` 2.1（Question 4）の設計判断に基づき、`RegistrationToken`/
`RefreshToken`いずれも`tokenHash`列に`@Column(unique = true, nullable = false)`を付与するのみで、
`@Table(indexes = {...})`による明示的な追加インデックスは定義しない。unique制約により暗黙的に
インデックスが張られるため、`findByTokenHash`はこれで十分な検索性能となる。`User.email`も同様に
`@Column(unique = true, nullable = false)`のみ（`findByEmail`用）。

U1の`AuditLog`（`occurredAt`/`userId`/`eventCategory`+`eventType`という非unique列への複合
インデックス）とは異なり、本ユニットの3エンティティはいずれも検索キーがunique制約対象の列
（`email`/`tokenHash`）のみであるため、非unique列への明示的インデックスは不要と判断している。

## テストカバレッジ（Step 9）

| テストクラス | 検証内容 |
|---|---|
| `UserRepositoryTest` | 基本CRUD（`saveAssignsGeneratedId`、`deleteRemovesEntity`）、`findByEmail`（一致/不一致）、`countByRole`（`ADMIN`/`USER`別カウント）、`findByStatusOrderByCreatedAtAsc`（`PENDING_APPROVAL`のみ抽出・`createdAt`昇順）、`email`のunique制約違反（`DataIntegrityViolationException`）をexample-basedテストで検証（7件） |
| `RegistrationTokenRepositoryTest` | 基本CRUD、`findByTokenHash`（一致/不一致）、`findByEmailAndConsumedAtIsNullAndInvalidatedAtIsNull`（消費済み/無効化済みを除外し未消費・未無効化の1件のみ返す、該当なしで空）、`tokenHash`のunique制約違反をexample-basedテストで検証（7件） |
| `RefreshTokenRepositoryTest` | 基本CRUD、`findByTokenHash`（一致/不一致）、`findByFamilyId`（同一`familyId`の全行取得・別`familyId`を含まない、該当なしで空）、`tokenHash`のunique制約違反をexample-basedテストで検証（7件） |

P1〜P11（業務ロジックの性質）はリポジトリ層では再検証せず、`business-logic-summary.md`記載の
jqwik `@Property`テスト（`UserRegistrationServiceTest`等）に一元化している。U1の
`AuditLogRepositoryTest`（P2: Round-trip）とは異なり、本ユニットの3リポジトリはいずれも
単純なCRUD＋一意検索キーの取得のみであり、リポジトリ層固有のプロパティテストは設計しない
（`u2-auth-user-registration-code-generation-plan.md` Step 9の記載どおりexample-basedのみ）。