# domain-entities.md — U4: Permission Management

`u4-permission-management-functional-design-plan.md`（回答Q1〜Q8）に基づくドメインモデル。
内部DB（JPA）で永続化するエンティティのみを扱う。プロジェクト全体の規約に従い、JPAの
関係アノテーション（`@ManyToOne`/`@OneToMany`/`@ManyToMany`/`@JoinColumn`）は使用せず、
全てのエンティティ間参照は素の`Long`型FK保持フィールドで表現する（`ddl-auto: update`により
DBレベルのFK制約も生成されない）。全エンティティは`@Id @GeneratedValue(strategy =
GenerationType.IDENTITY) private Long id;`のサロゲートキーを持つ（`SchemaColumn`等
既存エンティティと同一パターン、Q1で確認済み）。

---

## group ドメイン

### Group（ユーザグループ）

管理者が作成するユーザのグループ分け（ADM-1）。

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | 主キー |
| `name` | String（unique, not null） | グループ名。`createGroup`/`renameGroup`双方で一意性チェック対象（Q1, Q4） |
| `createdAt` | `java.time.Instant` | 作成時刻 |

### GroupMember（グループ所属関係）

`Group`と`User`（U2, `auth`/`userregistration`）の中間テーブル（Q1 = A、
JPAの`@ManyToMany`は使用せず明示的な中間エンティティとして表現する）。

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | 主キー（サロゲートキー、Q1で確認済み） |
| `groupId` | Long（not null, FK: `Group.id`） | 所属先グループ |
| `userId` | Long（not null, FK: `User.id`、U2） | 所属ユーザ |
| `joinedAt` | `java.time.Instant` | 所属追加時刻 |

一意制約: `(groupId, userId)`の組み合わせで一意（同一グループへの重複所属を防ぐ、Q1）。
1ユーザが複数グループに所属すること自体は制約しない（ADM-1 AC）。

---

## permission ドメイン

### PermissionAssignment（主権限）

テーブル/カラム単位の主権限（NONE/READ/UPDATE）設定（MVP-9, ADM-2）。

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | 主キー |
| `principalType` | `PrincipalType`（enum, not null） | `USER`または`GROUP` |
| `principalId` | Long（not null） | `principalType`に応じて`User.id`または`Group.id`を指す。ポリモーフィックな参照のためDBレベルのFKは持てず、アプリケーション層のバリデーション（Q3 (4)）で実在確認する |
| `connectionId` | Long（not null, FK: `RdbmsConnection.id`、U3） | 対象接続 |
| `schemaName` | String（not null） | スキーマ名 |
| `tableName` | String（nullable） | テーブル名。`null`はスキーマレベル設定を意味する |
| `columnName` | String（nullable） | カラム名。`tableName`が`null`の場合は必ず`null`（Q3 (1)）。`null`はテーブルレベル設定を意味する |
| `permission` | `Permission`（enum, not null） | `NONE` / `READ` / `UPDATE` |
| `updatedAt` | `java.time.Instant` | 最終更新時刻 |

一意制約: `(principalType, principalId, connectionId, schemaName, tableName, columnName)`
（null許容列を含む複合一意制約。H2/MySQL/PostgreSQL/MariaDBいずれもNULLを区別せず許容する
動作を前提とする、Q2）。`schemaName`/`tableName`/`columnName`は`SchemaTable`/`SchemaColumn`の
内部IDではなく**物理名（String）で参照する**（U3の`domain-entities.md`「設計判断」節と
同一方針。スキーマ再取り込みのID変動が権限データに影響しない）。

### AuxPermissionAssignment（補助権限）

テーブル/スキーマ単位の補助権限（CREATE/DELETE）設定（MVP-9, ADM-2）。

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | 主キー |
| `principalType` | `PrincipalType`（enum, not null） | `USER`または`GROUP` |
| `principalId` | Long（not null） | `PermissionAssignment.principalId`と同様、アプリケーション層で実在確認する |
| `connectionId` | Long（not null, FK: `RdbmsConnection.id`、U3） | 対象接続 |
| `schemaName` | String（not null） | スキーマ名 |
| `tableName` | String（nullable） | テーブル名。`null`はスキーマレベル設定を意味する |
| `auxType` | `AuxPermissionType`（enum, not null） | `CREATE` / `DELETE` |
| `granted` | boolean（not null） | 許可されているか |
| `updatedAt` | `java.time.Instant` | 最終更新時刻 |

一意制約: `(principalType, principalId, connectionId, schemaName, tableName, auxType)`（Q2）。

主権限・補助権限を1テーブルに統合しない（階層数（3 vs 2）と値の型（enum vs boolean）が
異なるため、Q2 = A）。

### PrincipalType（enum）
- `USER`
- `GROUP`

`PrincipalRef`（`PrincipalType` + `principalId`のペア）はエンティティではなく、
`PermissionAssignmentService`のAPI引数として使われる値オブジェクト（`component-methods.md`
で定義済み）。

### Permission（enum、主権限、Q2）
- `NONE`
- `READ`
- `UPDATE`

強さの順序: `NONE < READ < UPDATE`（グループ合成時の「最も緩い権限」判定、Step 1
判定ロジック要旨3.で使用）。

### AuxPermissionType（enum、補助権限、Q2）
- `CREATE`
- `DELETE`

---

## 設計判断（AI提案、Q1〜Q8の対象外事項）

### principalIdのポリモーフィック参照とFK制約の不在（Q2/Q3で確定済み事項の要約）

`principalId`は`principalType`によって参照先テーブル（`User`または`Group`）が変わるため、
本プロジェクトの他の全FK同様にDBレベルのFK制約を持たない（プロジェクト全体でJPA関係
アノテーションを使わない方針のため他のFKも同様だが、`principalId`は加えてポリモーフィックで
あるため単一カラムでのDB FK表現自体が原理的に不可能という点で他のFKと性質が異なる）。この
不足は`PermissionAssignmentService`/`GroupService`のアプリケーション層バリデーション
（Q3 (4)、実在しない`principalId`は例外・監査ログ記録）で代替する、という方針をQ2〜Q3の
議論で確定済み。