# business-logic-summary.md — U2: Auth & User Registration

Step 2（ビジネスロジック生成）・Step 3（ビジネスロジック単体テスト）で生成したクラス一覧と、
`business-logic-model.md`のP1〜P11、および本Code Generation計画で新たに識別したP12〜P13
（`security`パッケージ、Functional Design時点ではスコープ外）との対応関係。

## 生成クラス一覧（Step 2、Step 8前倒し分含む）

| パッケージ | クラス/インタフェース | 役割 |
|---|---|---|
| `userregistration` | `User`（JPA entity） | ユーザーエンティティ（`email`, `passwordHash`, `role`, `status`等） |
| `userregistration` | `Role`, `UserStatus` | ユーザー関連列挙型 |
| `userregistration` | `RegistrationToken`（JPA entity） | 登録トークンエンティティ |
| `userregistration` | `RegistrationTokenStatus` | トークン状態列挙型 |
| `userregistration` | `UserRepository`, `RegistrationTokenRepository` | Spring Data JPAリポジトリ（Step 8前倒し） |
| `userregistration` | `UserRegistrationService` | `requestRegistration`/`completeRegistration`/`approveUser`/`rejectUser`/`listPendingUsers` |
| `userregistration` | `RegistrationTokenService` | トークン発行・検証（`validate`）・無効化 |
| `userregistration` | `PendingUserSummary` | 承認待ち一覧DTO（record） |
| `userregistration` | `InvalidUserStateException`, `TokenExpiredException`, `TokenNotFoundException` | 業務例外 |
| `userregistration` | `AdminBootstrapRunner` | 起動時初期管理者アカウント作成（`ApplicationRunner`） |
| `auth` | `RefreshToken`（JPA entity） | リフレッシュトークンエンティティ（`familyId`によるreuse detection対応） |
| `auth` | `RefreshTokenRepository` | Spring Data JPAリポジトリ（Step 8前倒し） |
| `auth` | `RefreshTokenService` | `issue`/`rotate`（reuse detection含む）/`revoke` |
| `auth` | `AuthenticationService` | `login`/`refresh`/`logout` |
| `auth` | `AuthToken`, `RotationResult` | 認証結果DTO（record） |
| `auth` | `AuthenticationFailedException`, `InvalidTokenException` | 業務例外 |
| `security` | `OpaqueTokenGenerator` | 登録・リフレッシュトークン用の不透明トークン生成/ハッシュ化 |
| `security` | `JwtTokenProvider` | アクセストークン（JWT）の発行・検証 |
| `security` | `SecurityConfig` | 既存設定の拡張（ブラウンフィールド修正） |
| `config` | `GlobalExceptionHandler` | 既存設定への`@ExceptionHandler`5件追記（ブラウンフィールド修正） |

## 生成テストクラス一覧（Step 3）

| テストクラス | 検証方式 |
|---|---|
| `userregistration.UserRegistrationServiceTest` | jqwik `@Property`（純Mockito、リポジトリ・`MailService`・`AuditLogService`をモック化） |
| `userregistration.RegistrationTokenServiceTest` | jqwik `@Property`（`@JqwikSpringSupport @DataJpaTest`、実`RegistrationTokenRepository`） |
| `auth.AuthenticationServiceTest` | jqwik `@Property`（`@JqwikSpringSupport @DataJpaTest`、実`UserRepository`＋他コラボレータはモック化） |
| `auth.RefreshTokenServiceTest` | jqwik `@Property`（`@JqwikSpringSupport @DataJpaTest`、実`RefreshTokenRepository`） |
| `security.OpaqueTokenGeneratorTest` | jqwik `@Property`（POJO単体、Springコンテキスト不要） |
| `security.JwtTokenProviderTest` | jqwik `@Property`（POJO単体、Springコンテキスト不要、固定シークレットで直接インスタンス化） |

## P1〜P13対応表

| # | 対象 | 検証テストクラス | 状態 |
|---|---|---|---|
| P1 | `requestRegistration`の列挙攻撃対策Invariant（既存状態によらず常に同一の成功結果） | `UserRegistrationServiceTest` | 実装済み（Step 3） |
| P2 | `RegistrationTokenService`のRound-trip（発行直後`VALID`、消費/無効化後`EXPIRED`） | `RegistrationTokenServiceTest` | 実装済み（Step 3） |
| P3 | 再送信時の新旧トークンInvariant（旧トークン無効化、新トークン有効） | `UserRegistrationServiceTest` | 実装済み（Step 3） |
| P4 | `completeRegistration`のInvariant（`VALID`時のみ`User`作成） | `UserRegistrationServiceTest` | 実装済み（Step 3） |
| P5 | 承認/却下のState machine性質（`PENDING_APPROVAL`からのみ遷移可能） | `UserRegistrationServiceTest` | 実装済み（Step 3） |
| P6 | `REJECTED`ユーザへの再発行なしInvariant | `UserRegistrationServiceTest` | 実装済み（Step 3） |
| P7 | ログイン成否のOracle（`status`×パスワード一致の全組み合わせ） | `AuthenticationServiceTest` | 実装済み（Step 3） |
| P8 | ログイン失敗時の監査記録Invariant（`userId`の有無に関わらず必ず記録） | `AuthenticationServiceTest` | 実装済み（Step 3） |
| P9 | リフレッシュトークンreuse detectionのInvariant（同一family全行を無効化） | `RefreshTokenServiceTest` | 実装済み（Step 3） |
| P10 | リフレッシュトークンローテーションのRound-trip | `RefreshTokenServiceTest` | 実装済み（Step 3） |
| P11 | ログアウトの影響範囲限定Invariant（同一family内の他トークンには影響しない） | `RefreshTokenServiceTest` | 実装済み（Step 3） |
| P12 | `OpaqueTokenGenerator`の性質（`hash`の決定性、`generate()`のデコード可能性） | `OpaqueTokenGeneratorTest` | 実装済み（Step 3、Code Generation計画でP12として新規識別） |
| P13 | `JwtTokenProvider`のRound-trip（`parseAndValidate(generateToken(...))`の一致、期限切れ検知） | `JwtTokenProviderTest` | 実装済み（Step 3、Code Generation計画でP13として新規識別） |

**補足**: U2はU1と異なり、P1〜P13すべてがStep 3で実装完了している（Repository層・API層に
先送りする性質は無い——U2のRepository（`UserRepository`/`RegistrationTokenRepository`/
`RefreshTokenRepository`）はStep 8前倒しでStep 2時点で既に生成済みであり、
`RegistrationTokenServiceTest`/`AuthenticationServiceTest`/`RefreshTokenServiceTest`は
実リポジトリを用いた`@DataJpaTest`で検証しているため）。

**既知の課題（Step 3スコープ外）**: 全体テストスイート実行時、`MasterMeisterApplicationTests
.contextLoads()`が`HibernateException: Unable to determine Dialect without JDBC metadata`で
失敗する。原因は`backend/src`配下に`application.yml`/`application.properties`が一切存在せず、
実JPA `@Entity`がクラスパス上に存在する状態で組み込みH2データソースの自動構成が解決しない
ため。この事象はU1 Step 2でJPAエンティティが初めて導入されて以降の既存事象であり、本Step 3の
テスト追加が原因ではないことを、新規テストファイルを`git stash`で一時退避した状態での
単独再実行により確認済み。対応はCode Generation計画Step 16（`application.yml`のブラウン
フィールド追加）の責務であり、本ステップでは対応しない。