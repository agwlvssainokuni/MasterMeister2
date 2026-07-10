# U4 Permission Management - リポジトリレイヤサマリ

Step 8（リポジトリレイヤ生成）・Step 9（リポジトリレイヤ単体テスト）で生成した4リポジトリの一覧。

## `GroupRepository`（`cherry.mastermeister.group`）

`JpaRepository<Group, Long>` を継承。

| メソッド | 説明 |
|---|---|
| `Optional<Group> findByName(String name)` | `GroupService.createGroup`/`renameGroup`が、グループ名の重複を判定するために使用 |

## `GroupMemberRepository`（`cherry.mastermeister.group`）

`JpaRepository<GroupMember, Long>` を継承。

| メソッド | 説明 |
|---|---|
| `Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId)` | `GroupService.addUserToGroup`が既メンバーか判定するため、`removeUserFromGroup`が削除対象を取得するために使用 |
| `List<GroupMember> findByGroupId(Long groupId)` | `GroupService.listGroupMembers`が、グループ配下のメンバー一覧を取得するために使用 |
| `List<GroupMember> findByUserId(Long userId)` | `EffectivePermissionResolver.groupIdsOf`が、ユーザが所属する全グループIDを取得し権限合成の起点とするために使用 |
| `void deleteByGroupId(Long groupId)` | `GroupService.deleteGroup`が、グループ削除時にメンバーシップを一括カスケード削除するために使用 |

## `PermissionAssignmentRepository`（`cherry.mastermeister.permission`）

`JpaRepository<PermissionAssignment, Long>` を継承。

| メソッド | 説明 |
|---|---|
| `Optional<PermissionAssignment> findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnName(...)` | `PermissionAssignmentService.setPermission`が既存行の有無（新規作成/更新の分岐）を判定するため、`EffectivePermissionResolver.findMostSpecificPermission`がcolumn→table→schemaの各段で権限を解決するために使用（`table`/`column`は`null`を渡すことでスキーマ/テーブルレベルの行も検索可能） |
| `List<PermissionAssignment> findByConnectionId(Long connectionId)` | `PermissionAssignmentService.exportPermissionsAsYaml`が、対象接続配下の全権限設定をYAML出力するために使用 |
| `void deleteByConnectionId(Long connectionId)` | `PermissionAssignmentService.importPermissionsFromYaml`が、YAMLインポート時の全置換（既存設定の一括削除→再作成）のために使用 |
| `void deleteByPrincipalTypeAndPrincipalId(PrincipalType principalType, Long principalId)` | `GroupService.deleteGroup`が、グループ削除時に当該グループの権限設定を一括カスケード削除するために使用 |

Step 8確認時に、`EffectivePermissionResolver`の階層解決用と記載されていた
`findByPrincipalTypeAndPrincipalIdAndConnectionId`（複数件取得版）が実際には
どこからも呼び出されていない未使用メソッドと判明し削除した（実際の階層解決は上記の
`...ColumnName`メソッドをcolumn/table/schemaの3段で個別に呼ぶ方式）。

## `AuxPermissionAssignmentRepository`（`cherry.mastermeister.permission`）

`JpaRepository<AuxPermissionAssignment, Long>` を継承。`PermissionAssignmentRepository`と対になる
構成（`permission`列の代わりに`auxType`/`granted`列を持つ）。

| メソッド | 説明 |
|---|---|
| `Optional<AuxPermissionAssignment> findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxType(...)` | `PermissionAssignmentService.setAuxPermission`が既存行の有無を判定するため、`EffectivePermissionResolver.findMostSpecificAuxPermission`がtable→schemaの各段で補助権限を解決するために使用 |
| `List<AuxPermissionAssignment> findByConnectionId(Long connectionId)` | `PermissionAssignmentService.exportPermissionsAsYaml`が、対象接続配下の全補助権限設定をYAML出力するために使用 |
| `void deleteByConnectionId(Long connectionId)` | `PermissionAssignmentService.importPermissionsFromYaml`が、YAMLインポート時の全置換のために使用 |
| `void deleteByPrincipalTypeAndPrincipalId(PrincipalType principalType, Long principalId)` | `GroupService.deleteGroup`が、グループ削除時に当該グループの補助権限設定を一括カスケード削除するために使用 |

同様に`findByPrincipalTypeAndPrincipalIdAndConnectionId`（未使用）を削除済み。

## インデックス設計

`nfr-design-patterns.md` 4.1の設計判断に基づく。

- `Group`: `@Column(unique = true)`で`name`に単一unique制約。`findByName`はこのインデックスで
  引ける
- `GroupMember`: 一意制約`(groupId, userId)`に加え、`@Table(indexes = {@Index(columnList =
  "userId, groupId")})`で`(userId, groupId)`への明示的追加インデックスを付与。`findByGroupId`は
  unique制約の先頭列（`groupId`）プレフィックス一致で引けるが、`findByUserId`（`userId`が
  unique制約の非先頭列）は`EffectivePermissionResolver.groupIdsOf`が権限解決のたび（リクエスト毎、
  `@Cacheable`未適用のメソッドを含む）に呼び出す高頻度クエリのため、追加インデックスで
  専用にカバーする
- `PermissionAssignment`/`AuxPermissionAssignment`: 一意制約
  `(principalType, principalId, connectionId, schemaName, tableName, columnName/auxType)`のみで
  インデックスを賄う（明示的`@Table(indexes)`なし）。`findByConnectionId`/
  `deleteByConnectionId`はunique制約の非先頭列（`connectionId`が3列目）が条件のため単純な
  プレフィックス一致は効かないが、エクスポート・全置換インポートは低頻度操作（管理者による
  YAML入出力時のみ）であり、`nfr-design-patterns.md` 4.1により追加インデックスは見送りとした。
  `deleteByPrincipalTypeAndPrincipalId`も同様（グループ削除は低頻度操作）

## テストカバレッジ（Step 9）

| テストクラス | 検証内容 |
|---|---|
| `GroupRepositoryTest` | 基本CRUD、`findByName`（一致/不一致）、`name`のunique制約違反時に`DataIntegrityViolationException`が発生することを検証（5件） |
| `GroupMemberRepositoryTest` | 基本CRUD、`findByGroupIdAndUserId`/`findByGroupId`/`findByUserId`/`deleteByGroupId`、`(groupId, userId)`のunique制約違反を検証（8件） |
| `PermissionAssignmentRepositoryTest` | 基本CRUD、`findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnName`（NULL列/非NULL列の判別含む）、`findByConnectionId`、`deleteByConnectionId`、`deleteByPrincipalTypeAndPrincipalId`、unique制約違反を検証（8件） |
| `AuxPermissionAssignmentRepositoryTest` | 基本CRUD、`findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxType`（NULLテーブル/非NULLテーブルの判別含む）、`findByConnectionId`、`deleteByConnectionId`、`deleteByPrincipalTypeAndPrincipalId`、unique制約違反を検証（8件） |

いずれも`@DataJpaTest`＋組み込みH2で実行。unique制約テストでは、`columnName`（`PermissionAssignment`）
/`tableName`（`AuxPermissionAssignment`）がNULL許容カラムであり、SQLのunique制約はNULL同士を
別値扱いする点に注意し、いずれも非NULL値の組み合わせで重複を発生させて検証した。P1〜P11
（業務ロジックの性質）はリポジトリ層では再検証せず、`business-logic-summary.md`記載のjqwik
`@Property`テスト（`GroupServiceTest`/`PermissionAssignmentServiceTest`/
`EffectivePermissionResolverTest`等）に一元化している。
