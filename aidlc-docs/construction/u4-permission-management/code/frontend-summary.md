# U4 Permission Management - フロントエンドサマリ

Step 11（フロントエンド生成）・Step 12（Vitest+RTLテスト）で生成したコンポーネント・
API・ルーティングの一覧。設計は`functional-design/frontend-components.md`に準拠する。

## 新規: `features/group/`

| ファイル | 内容 |
|---|---|
| `types.ts` | `GroupSummary`（`id`/`name`/`createdAt`）、`UserSummary`（`id`/`email`） |
| `api.ts` | `createGroup(name)` → `POST /api/groups`、`renameGroup(groupId, name)` → `PUT /api/groups/{id}`、`deleteGroup(groupId)` → `DELETE /api/groups/{id}`、`listGroups()` → `GET /api/groups`、`listGroupMembers(groupId)` → `GET /api/groups/{id}/members`、`addUserToGroup(groupId, userId)` → `POST /api/groups/{id}/members`、`removeUserFromGroup(groupId, userId)` → `DELETE /api/groups/{id}/members/{userId}` |
| `GroupListPage.tsx` | マウント時`listGroups()`。新規作成フォーム、`GroupTable`のコンテナ。成功/失敗は`ToastNotification`（U1）で通知 |
| `GroupTable.tsx` | `DataTable`（U1）利用。行ごとに詳細リンク・インライン名称変更（入力欄+保存/キャンセル）・削除ボタンを表示。削除は`ConfirmDialog`（所属ユーザのグループ経由権限も併せて削除される旨を警告文に含む）で確認 |
| `GroupDetailPage.tsx` | `useParams`でグループIDを取得。グループ単体取得APIが存在しないため、`listGroups()`と`listGroupMembers(groupId)`を`Promise.all`で並列取得し、`listGroups()`結果をクライアントサイドで`id`フィルタしてグループ名を表示する（ブラウンフィールド発見事項相当のAI決定）。ユーザ検索APIが存在しないため、所属ユーザ追加は数値のユーザID直接入力フォームとした |
| `GroupMemberTable.tsx` | `DataTable`（U1）利用。行ごとに削除ボタンを表示。削除は`ConfirmDialog`で確認 |

## 新規: `features/permission/`

| ファイル | 内容 |
|---|---|
| `types.ts` | `PrincipalType`（`USER`\|`GROUP`）、`PrincipalRef`、`Permission`（`NONE`\|`READ`\|`UPDATE`）、`AuxPermissionType`（`CREATE`\|`DELETE`）、`SchemaTreeNodeLevel`（`schema`\|`table`\|`column`）、`SchemaTreeNode`、`ImportResult` |
| `api.ts` | `setPermission(...)`/`setAuxPermission(...)` → `PUT /api/rdbms-connections/{connectionId}/permissions`（`Optional<T>`フィールドは未使用側を`null`で埋めて送信）。`exportPermissionsAsYaml(connectionId)`/`importPermissionsFromYaml(connectionId, file)`は`apiFetch`が常に`Content-Type: application/json`を付与しblob/`FormData`に不適合なため、`useAuthStore`から直接トークンを取得する素の`fetch`で実装（`frontend-components.md` item 11-2、Step 2完了後に確定したAI決定）。`importPermissionsFromYaml`はHTTP 200成功時・非200時のいずれも単一の`ImportResult`形状に正規化して返す |
| `PermissionAssignmentPage.tsx` | `ConnectionSelector`・`PrincipalSelector`・`PermissionTree`・`PermissionForm`（principal+ノード選択時のみ表示）・`PermissionYamlPanel`を組み合わせる親ページ。接続切替時は選択中ノードをリセットする |
| `ConnectionSelector.tsx` | `listConnections()`（`features/rdbmsConnection`）による対象接続の`<select>` |
| `PrincipalSelector.tsx` | USER/GROUPタブ切替。USERタブはユーザID直接入力（ユーザ検索APIが存在しないため）、GROUPタブは`listGroups()`（`features/permission`→`features/group`の一方向依存）による`<select>` |
| `PermissionTree.tsx` | アコーディオン形式のスキーマ/テーブル/カラムツリー。スキーマは接続ID変更時に即時ロード、テーブル・カラムはそれぞれスキーマ展開時・テーブル展開時に遅延ロードし`Record`にキャッシュする |
| `PermissionForm.tsx` | 選択中ノードの権限（NONE/READ/UPDATE）と補助権限（CREATE/DELETE、スキーマ/テーブルレベルのみ表示）を編集し送信する。単一のGET APIが存在しないため`currentPermission`/`currentAuxPermissions`は常に親ページから`null`で渡され、常にNONE/未チェックから開始する（本Stepではバックエンド変更が対象外のため許容したAI決定） |
| `PermissionYamlPanel.tsx` | エクスポートは`Blob`から`URL.createObjectURL`+一時`<a download>`クリックでダウンロードし`URL.revokeObjectURL`で解放。インポートは`<input type="file">`の`onChange`で送信し、結果を`ImportResult`として表示する |

## ルーティング一覧（追加分）

| パス | 種別 | コンポーネント |
|---|---|---|
| `/admin/groups` | 保護（ADMIN限定） | `GroupListPage` |
| `/admin/groups/:id` | 保護（ADMIN限定） | `GroupDetailPage` |
| `/admin/permissions` | 保護（ADMIN限定） | `PermissionAssignmentPage` |

`AppLayout.tsx`には「グループ管理」（`/admin/groups`）・「権限設定」（`/admin/permissions`）への
ナビゲーションリンクを、既存の「RDBMS接続管理」リンクと同一条件
（`isAuthenticated && currentUser?.role === 'ADMIN'`）で追加した。未認証で保護ルートに
アクセスした場合は`ProtectedRoute`（既存、変更なし）により`/login`へリダイレクトする。

## data-testid一覧（新規分）

`group-list-page`, `group-list-page-new-name-input`, `group-list-page-new-button`,
`group-table-detail-button`, `group-table-rename-button`, `group-table-rename-input`,
`group-table-rename-commit-button`, `group-table-rename-cancel-button`,
`group-table-delete-button`, `group-detail-page`, `group-detail-page-new-user-id-input`,
`group-detail-page-add-user-button`, `group-member-table-remove-button`,
`permission-assignment-page`, `connection-selector-select`, `principal-selector`,
`principal-selector-user-tab`, `principal-selector-group-tab`,
`principal-selector-user-id-input`, `principal-selector-user-select-button`,
`principal-selector-group-select`, `permission-tree`, `permission-tree-schema-toggle`,
`permission-tree-schema-select`, `permission-tree-table-toggle`,
`permission-tree-table-select`, `permission-tree-column-select`, `permission-form`,
`permission-form-error`, `permission-form-permission-select`,
`permission-form-aux-create-checkbox`, `permission-form-aux-delete-checkbox`,
`permission-form-submit-button`, `permission-yaml-panel`,
`permission-yaml-panel-export-button`, `permission-yaml-panel-import-input`,
`permission-yaml-panel-import-result`, `app-layout-nav-groups`, `app-layout-nav-permissions`

（削除確認は`GroupTable`・`GroupMemberTable`いずれもU1既存の`ConfirmDialog`を再利用しており、
固有の`data-testid`は追加していない。ダイアログ側の`role="dialog"`・
`confirm-dialog-confirm-button`・`confirm-dialog-cancel-button`をそのまま使用する。）

## 実装時判断事項（設計未規定・自律的に決定した内容）

- **グループ単体取得APIの不在**: `GroupController`は一覧・所属ユーザ一覧のみを提供し、
  単一グループ取得のGETエンドポイントがない。`GroupDetailPage`は`listGroups()`の結果を
  クライアントサイドで`id`フィルタしてグループ名を取得する回避策とした
  （本Stepではバックエンド変更が対象外のため）。
- **ユーザ検索APIの不在**: 承認済みユーザをメールアドレスで検索するAPIが存在しない
  （`RegistrationController`は`/pending`のみ）。`GroupDetailPage`の所属ユーザ追加、
  `PrincipalSelector`のUSERタブのいずれも、数値のユーザID直接入力フォームとした
  （`frontend-components.md`が「詳細はCode Generationで確定する」としていた箇所）。
- **権限現在値取得APIの不在**: `PermissionController`は`PUT`（単一フィールドずつ`Optional<T>`
  で更新）・`GET /export`（YAML全体）・`POST /import`（全置換）のみで、特定principal・
  スキーマ/テーブル/カラムノードに対する現在の権限割当を取得するGETがない。
  `PermissionAssignmentPage`は`currentPermission`/`currentAuxPermissions`を常に`null`で
  `PermissionForm`へ渡し、フォームは常にNONE/未チェックから開始する。
- **`Optional<T>`のJSONワイヤ形式**: `PermissionControllerTest.java`のリクエストボディ例
  （例: `{"principal":{...},"schema":"public","table":"employees","column":null,"permission":"READ","auxType":null,"granted":null}`）
  から、SpringはJacksonで`Optional<T>`フィールドを素の`null`/値としてシリアライズ・
  デシリアライズすると確認した上で、`features/permission/api.ts`のリクエストボディを
  この形式に合わせて実装した。
- **YAML エクスポート/インポートの`apiFetch`バイパス**: `apiFetch`（`api/apiClient.ts`）は
  常に`Content-Type: application/json`を付与しリクエストボディをJSON文字列化するため、
  blobダウンロード（エクスポート）・`FormData`アップロード（インポート）に使用できない。
  両関数は`useAuthStore.getState().token`から直接トークンを取得する素の`fetch`で実装した
  （`frontend-components.md` item 11-2に事前に記載されていたAI決定）。
- **`importPermissionsFromYaml`のレスポンス正規化**: `PermissionAssignmentService.importPermissionsFromYaml`の
  実装を確認した結果、`PermissionYamlFormatException`はサービス内部で捕捉され
  `ImportResult(false, message)`としてHTTP 200で返る（コントローラテストの400応答は
  サービスを直接例外送出させるモックによるものであり、実運用経路では発生しない）。
  この実装挙動に合わせ、フロントエンドの`importPermissionsFromYaml`はHTTP 200・非200の
  いずれの応答も単一の`ImportResult`形状へ正規化して返すようにした。

## テストカバレッジ（Step 12）

| テストファイル | 件数 | 検証内容 |
|---|---|---|
| `features/group/GroupTable.test.tsx` | 6 | 行描画、詳細ボタンのコールバック、インライン名称変更のコミット/キャンセル、削除確認ダイアログのキャンセル/確定 |
| `features/group/GroupListPage.test.tsx` | 5 | マウント時一覧表示、新規作成成功/失敗トースト、名称変更、削除 |
| `features/group/GroupMemberTable.test.tsx` | 3 | 行描画、削除確認ダイアログのキャンセル/確定 |
| `features/group/GroupDetailPage.test.tsx` | 4 | `listGroups()`結果からのクライアントサイドfilterによるグループ名表示・所属ユーザ一覧、ユーザID追加の成功/失敗トースト、削除 |
| `features/permission/ConnectionSelector.test.tsx` | 2 | 接続一覧表示、選択時の`onSelect`呼び出し |
| `features/permission/PrincipalSelector.test.tsx` | 3 | USERタブ既定・ID送信、GROUPタブ切替・一覧表示・選択、USERタブ中は`listGroups`未呼び出し |
| `features/permission/PermissionTree.test.tsx` | 5 | スキーマ即時ロード、スキーマ選択コールバック、テーブル遅延ロード、カラム遅延ロード、カラム選択コールバック |
| `features/permission/PermissionForm.test.tsx` | 5 | `currentPermission=null`時のNONE/未チェック初期化、テーブルレベルでの補助権限フィールド表示・カラムレベルでの非表示、テーブルレベル送信時の`setPermission`+`setAuxPermission`×2呼び出し、カラムレベル送信時は`setAuxPermission`が呼ばれないこと、保存失敗時のエラー表示 |
| `features/permission/PermissionYamlPanel.test.tsx` | 3 | エクスポート（`URL.createObjectURL`/`HTMLAnchorElement.prototype.click`のスタブ化）、インポート成功、インポート失敗 |
| `features/permission/PermissionAssignmentPage.test.tsx` | 3 | 接続選択後のツリー/YAMLパネル表示とフォーム非表示、principal+ノード選択後のフォーム表示、接続切替によるフォーム再非表示 |
| `routes/AppRouter.test.tsx`（拡張） | 3件追加 | `/admin/groups`・`/admin/groups/:id`・`/admin/permissions`への未認証アクセス時の`/login`リダイレクト |
| `components/AppLayout.test.tsx`（拡張） | 既存3件を拡張 | `app-layout-nav-groups`・`app-layout-nav-permissions`リンクの表示（管理者）／非表示（非管理者・未認証） |

上表の新規10ファイル・39件に加え、既存`AppRouter.test.tsx`への3件追加、`AppLayout.test.tsx`の
既存3件拡張（新規テストケース追加なし）を合わせ、U4で新規/拡張したテストは合計**42件**。
U1〜U3既存分と合わせ、フロントエンド全体は**40ファイル・156件、全テスト成功**
（`npx vitest run`）。`npx tsc -b --noEmit`（型チェック）・`npx oxlint`（Lint）も
エラーなしで完了している（詳細は`testing-summary.md`参照）。