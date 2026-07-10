# frontend-components.md — U4: Permission Management

`u4-permission-management-functional-design-plan.md`（Q7）に基づく`features/group/`・
`features/permission/`のコンポーネント設計。U1の共通基盤（`components/DataTable`・
`ConfirmDialog`・`ToastNotification`、`routes/ProtectedRoute`、`/admin`プレフィクス規約）を
再利用する。本ユニットの全画面は管理者専用（MVP-9, ADM-1, ADM-2, ADM-4, ADM-5の
ペルソナはいずれも「管理者」）。`group`と`permission`はU2の`auth`/`userRegistration`、
U3の`rdbmsConnection`/`schema`と同様に別featureとして分割する（バックエンドパッケージ
分割に対応、`unit-of-work.md`参照）。

---

## features/group/

### コンポーネント階層

```
GroupListPage（ProtectedRoute requiredRole="ADMIN"配下）
└── GroupTable（DataTableを利用、行内に詳細/名称変更/削除導線）

GroupDetailPage（ProtectedRoute requiredRole="ADMIN"配下）
└── GroupMemberTable（DataTableを利用、所属ユーザ一覧・追加/削除導線）
```

### GroupListPage
- **状態**: `groups: GroupSummary[]`, `loading: boolean`, `renamingGroupId: Long | null`
- **責務**: マウント時に`groupApi.listGroups()`を呼び出し、`GroupTable`に結果を渡す
  （`business-logic-model.md` フロー1手順5、ADM-1 AC）。「新規作成」操作は名前入力の
  簡易フォーム（インラインまたはモーダル）から`groupApi.createGroup(name)`を呼び出す
  （フロー1手順1）。

### GroupTable
- **Props**: `groups: GroupSummary[]`, `onOpenDetail(groupId)`, `onRename(groupId,
  newName)`, `onDelete(groupId)`
- **責務**: `DataTable`（U1）を用いてグループ名一覧を表示し、各行に「詳細」
  （`GroupDetailPage`への遷移）、「名称変更」（インライン編集、`groupApi.renameGroup`、
  フロー1手順2）、「削除」（`ConfirmDialog`（U1）で確認後に`groupApi.deleteGroup`、
  カスケード削除の影響を確認文言に含める、フロー1手順2）の導線を提供する。

### GroupDetailPage
- **状態**: `group: GroupSummary | null`, `members: UserSummary[]`, `loading: boolean`
- **責務**: マウント時に`groupApi.listGroupMembers(groupId)`を呼び出し、`GroupMemberTable`に
  結果を渡す（フロー1手順3）。「所属ユーザ追加」操作はユーザ選択（メールアドレス検索等、
  U2の`User`一覧を参照する想定、詳細はCode Generationで確定）から
  `groupApi.addUserToGroup(groupId, userId)`を呼び出す。

### GroupMemberTable
- **Props**: `members: UserSummary[]`, `onRemove(userId)`
- **責務**: `DataTable`（U1）を用いて所属ユーザ一覧（メールアドレス等）を表示し、各行に
  「削除」（`ConfirmDialog`で確認後に`groupApi.removeUserFromGroup(groupId, userId)`）の
  導線を提供する。

### api.ts（`features/group/`）
| 関数 | 対応API | 責務 |
|---|---|---|
| `createGroup(name)` | `POST /api/groups` | フロー1手順1 |
| `renameGroup(groupId, newName)` | `PUT /api/groups/{id}` | フロー1手順2 |
| `deleteGroup(groupId)` | `DELETE /api/groups/{id}` | フロー1手順2 |
| `listGroups()` | `GET /api/groups` | `GroupListPage`初期表示、`features/permission/`からも参照される（下記） |
| `listGroupMembers(groupId)` | `GET /api/groups/{id}/members` | `GroupDetailPage`初期表示 |
| `addUserToGroup(groupId, userId)` | `POST /api/groups/{id}/members` | フロー1手順3 |
| `removeUserFromGroup(groupId, userId)` | `DELETE /api/groups/{id}/members/{userId}` | フロー1手順3 |

（正確なエンドポイントパス・パラメータ名はCode Generation段階で確定する。
`component-methods.md`の`GroupService`シグネチャ＋Q4追加分に準拠する。）

---

## features/permission/

### コンポーネント階層

```
PermissionAssignmentPage（ProtectedRoute requiredRole="ADMIN"配下）
├── ConnectionSelector（対象接続の選択、U3のrdbmsConnectionApiを参照）
├── PrincipalSelector（ユーザ/グループ選択。グループ一覧はfeatures/group/のgroupApiを参照）
├── PermissionTree（スキーマ/テーブル/カラムのツリー表示、U3のschemaApiメタデータを利用）
├── PermissionForm（選択中階層の主権限・補助権限設定フォーム）
└── PermissionYamlPanel（エクスポート/インポート導線、独立ルートは持たない）
```

### PermissionAssignmentPage
- **状態**: `connectionId: Long | null`, `principal: PrincipalRef | null`,
  `schemaTree: SchemaTreeNode[]`, `selectedNode: SchemaTreeNode | null`
- **責務**: `ConnectionSelector`・`PrincipalSelector`・`PermissionTree`・`PermissionForm`・
  `PermissionYamlPanel`を束ねる親ページ。接続とprincipalの選択が揃った時点で
  `permissionApi`経由の各種取得・更新操作を仲介する（フロー2手順1〜5）。

### ConnectionSelector
- **Props**: `selected: Long | null`, `onSelect(connectionId)`
- **責務**: U3の`rdbmsConnectionApi.listConnections()`を呼び出し、接続選択プルダウンを
  提供する（機能間の一方向依存、`unit-of-work.md`のU4→U3依存に対応）。

### PrincipalSelector
- **Props**: `selected: PrincipalRef | null`, `onSelect(principal)`
- **責務**: `USER`/`GROUP`の切り替えタブまたはラジオを提供する。`USER`選択時はメール
  アドレス検索（U2の`User`一覧参照、詳細はCode Generationで確定）、`GROUP`選択時は
  `features/group/`の`groupApi.listGroups()`を呼び出してグループ一覧から選択させる
  （フロー2手順1、機能間の一方向依存 = バックエンドの`permission`→`group`依存と対応）。

### PermissionTree
- **Props**: `connectionId: Long`, `selectedNode: SchemaTreeNode | null`,
  `onSelectNode(node)`
- **責務**: U3の`schemaApi.listSchemas`/`listTables`/`getTableDetail`が返す取り込み済み
  メタデータを用いてスキーマ→テーブル→カラムのツリーを表示する（フロー2手順2）。ノード
  選択時に`selectedNode`を更新し、`PermissionForm`が現在の設定値を表示できるようにする。

### PermissionForm
- **Props**: `principal: PrincipalRef`, `connectionId: Long`, `node: SchemaTreeNode`,
  `currentPermission: Permission | null`, `currentAuxPermissions:
  Record<AuxPermissionType, boolean> | null`
- **状態**: `permission: Permission`, `auxPermissions: Record<AuxPermissionType, boolean>`,
  `submitting: boolean`, `error: string | null`
- **責務**: 選択中の階層（スキーマ/テーブル/カラム）に応じたフォームを表示する。
  スキーマ/テーブル階層では主権限＋補助権限（`CREATE`/`DELETE`）、カラム階層では主権限のみ
  入力可能とする（`business-rules.md` 2.1 (1)の階層整合性に対応、`column`選択時は
  補助権限フォームを表示しない）。保存操作で`permissionApi.setPermission(...)`/
  `permissionApi.setAuxPermission(...)`を呼び出し（フロー2手順4）、検証エラー
  （`business-rules.md` 2.1）はレスポンスの例外メッセージをそのまま`error`に表示する
  （フロー2手順5）。

### PermissionYamlPanel
- **状態**: `exporting: boolean`, `importing: boolean`, `importResult: ImportResult | null`
- **責務**: 選択中接続（`connectionId`）に対し、「エクスポート」ボタン押下で
  `permissionApi.exportPermissionsAsYaml(connectionId)`を呼び出しファイルダウンロードを
  トリガーする（フロー3）。「インポート」はファイルアップロードUIから
  `permissionApi.importPermissionsFromYaml(connectionId, file)`を呼び出し、結果
  （成功可否・失敗時は違反概要）を`importResult`に基づき表示する（フロー4）。

### api.ts（`features/permission/`）
| 関数 | 対応API | 責務 |
|---|---|---|
| `setPermission(principal, connectionId, schema, table, column, permission)` | `PUT /api/rdbms-connections/{connectionId}/permissions` | フロー2手順4 |
| `setAuxPermission(principal, connectionId, schema, table, auxType, granted)` | `PUT /api/rdbms-connections/{connectionId}/permissions` | フロー2手順4 |
| `exportPermissionsAsYaml(connectionId)` | `GET /api/rdbms-connections/{connectionId}/permissions/export` | フロー3 |
| `importPermissionsFromYaml(connectionId, file)` | `POST /api/rdbms-connections/{connectionId}/permissions/import` | フロー4 |

`features/permission/`は`features/group/`の`groupApi.listGroups()`のみを参照する
（機能間の一方向依存、`unit-of-work.md`のバックエンド`permission`→`group`依存と対応）。逆方向
（`features/group/`が`features/permission/`を参照）は発生しない。

（正確なエンドポイントパス・パラメータ名・PermissionForm/PermissionTreeの状態受け渡し方式は
Code Generation段階で確定する。`component-methods.md`の`PermissionAssignmentService`
シグネチャに準拠する。）

---

## AppRouter.tsxへの追加

| パス | コンポーネント | 認可 |
|---|---|---|
| `/admin/groups` | `GroupListPage` | `ProtectedRoute requiredRole="ADMIN"`（U1のルーティング規約） |
| `/admin/groups/:id` | `GroupDetailPage` | 同上 |
| `/admin/permissions` | `PermissionAssignmentPage`（接続選択を内包、`PermissionYamlPanel`も同一ページ内） | 同上 |

`AppLayout`（U1）のナビゲーションに「グループ管理」「権限設定」への各リンクを追加する
（管理者ロールのみ表示、U1`frontend-components.md`の出し分け機構を再利用）。