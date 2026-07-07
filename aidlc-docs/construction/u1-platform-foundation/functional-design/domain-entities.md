# domain-entities.md — U1: Platform Foundation

`u1-platform-foundation-functional-design-plan.md`（回答Q1〜Q7）に基づくドメインモデル。
`common`/`config`は技術的関心事のみのためドメインエンティティを持たない
（`components.md`の`PageRequest`/`PageResult<T>`・共通例外群は技術横断DTOであり、
本ドキュメントでは対象外）。

---

## audit ドメイン

### AuditLog（監査ログエントリ）

内部DB（JPA）で永続化するエンティティ。全ユニットの業務処理から
`AuditLogService.record(...)` 経由で書き込まれる（Question 2/3 = A: 対象RDBMS操作とは
別トランザクションで、対象RDBMS操作の成否に関わらず記録される）。

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | 主キー |
| `occurredAt` | `java.time.Instant` | イベント発生日時 |
| `userId` | Long（nullable） | 実行者。ログイン失敗など認証前イベントではnull可 |
| `connectionId` | Long（nullable） | 対象RDBMS接続に紐づくイベントの場合のみ設定 |
| `eventCategory` | `EventCategory`（enum） | 3分類（Question 1 = A） |
| `eventType` | `EventType`（enum、`eventCategory`のサブ区分） | 具体的な操作種別 |
| `result` | `Result`（enum: `SUCCESS` / `FAILURE`） | 成否 |
| `targetDescription` | String（nullable） | 対象の識別情報（例: テーブル名、接続名、対象ユーザメール等）。自由文字列 |
| `summaryMessage` | String（nullable） | 概要メッセージ（失敗理由の要約等。`masterdata`の設計方針と同様、詳細は含めない） |

### EventCategory（enum、Question 1 = A）

- `AUTHENTICATION`（認証イベント）
- `ADMIN_OPERATION`（管理操作）
- `DATA_ACCESS`（データアクセスイベント）

### EventType（enum、`eventCategory`ごとのサブ区分）

| `eventCategory` | `eventType` | 発生元（対応ストーリー） |
|---|---|---|
| `AUTHENTICATION` | `LOGIN_SUCCESS` | U2 `AuthenticationService`（MVP-6） |
| `AUTHENTICATION` | `LOGIN_FAILURE` | U2 `AuthenticationService`（MVP-6） |
| `AUTHENTICATION` | `LOGOUT` | U2 `AuthenticationService`（明示APIコール） |
| `ADMIN_OPERATION` | `USER_REGISTRATION_APPROVED` | U2 `UserRegistrationService`（MVP-4） |
| `ADMIN_OPERATION` | `USER_REGISTRATION_REJECTED` | U2 `UserRegistrationService`（MVP-4） |
| `ADMIN_OPERATION` | `RDBMS_CONNECTION_CHANGED` | U3 `RdbmsConnectionService`（MVP-7, ADM-3） |
| `ADMIN_OPERATION` | `SCHEMA_IMPORTED` | U3 `SchemaImportService`（MVP-8） |
| `ADMIN_OPERATION` | `GROUP_CHANGED` | U4 `GroupService`（ADM-1） |
| `ADMIN_OPERATION` | `PERMISSION_CHANGED` | U4 `PermissionAssignmentService`（MVP-9, ADM-2） |
| `ADMIN_OPERATION` | `PERMISSION_YAML_EXPORTED` | U4 `PermissionAssignmentService`（ADM-4） |
| `ADMIN_OPERATION` | `PERMISSION_YAML_IMPORTED` | U4 `PermissionAssignmentService`（ADM-5） |
| `DATA_ACCESS` | `LARGE_RECORD_READ` | U5 `MasterDataQueryService`（MVP-11、既定閾値100件以上） |
| `DATA_ACCESS` | `MASTER_DATA_MUTATION` | U5 `MasterDataMutationService`（GEN-4） |
| `DATA_ACCESS` | `QUERY_EXECUTED` | U7 `QueryExecutionService`（GEN-14） |

**設計メモ**: 上記は現時点で判明している呼び出し元（`components.md`・`stories.md`）を
網羅した初期セットであり、他ユニットのFunctional Design/Code Generation時に不足が判明した
場合は`EventType`へ追加する（`enum`のためコンパイル時に呼び出し側との整合が取れる、
Question 1 = Aの意図）。

---

## mail ドメイン

### MailNotificationType（enum、永続化はしない・送信時の種別区別のみ）

- `REGISTRATION_CONFIRMATION`（登録確認メール、MVP-1/MVP-2向けリンク）
- `REGISTRATION_APPROVED`（承認結果通知、MVP-5）
- `REGISTRATION_REJECTED`（却下結果通知、MVP-5）

`MailService`は永続エンティティを持たず、都度テンプレート（Question 5 = B、Thymeleafテンプレート
ファイル、`business-rules.md`参照）に変数を埋め込んでメール本文を生成し送信する。