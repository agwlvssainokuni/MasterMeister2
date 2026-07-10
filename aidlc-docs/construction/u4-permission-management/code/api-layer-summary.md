# U4 Permission Management - APIレイヤサマリ

Step 5（APIレイヤ生成）・Step 6（APIレイヤ単体テスト）で生成したエンドポイントの一覧。

## エンドポイント一覧

| パス | メソッド | 認可要件 | Controller |
|---|---|---|---|
| `/api/groups` | POST | 認証必須 + `ROLE_ADMIN` | `GroupController#createGroup` |
| `/api/groups/{id}` | PUT | 認証必須 + `ROLE_ADMIN` | `GroupController#renameGroup` |
| `/api/groups/{id}` | DELETE | 認証必須 + `ROLE_ADMIN` | `GroupController#deleteGroup` |
| `/api/groups` | GET | 認証必須 + `ROLE_ADMIN` | `GroupController#listGroups` |
| `/api/groups/{id}/members` | GET | 認証必須 + `ROLE_ADMIN` | `GroupController#listGroupMembers` |
| `/api/groups/{id}/members` | POST | 認証必須 + `ROLE_ADMIN` | `GroupController#addUserToGroup` |
| `/api/groups/{id}/members/{userId}` | DELETE | 認証必須 + `ROLE_ADMIN` | `GroupController#removeUserFromGroup` |
| `/api/rdbms-connections/{connectionId}/permissions` | PUT | 認証必須 + `ROLE_ADMIN` | `PermissionController#updatePermission` |
| `/api/rdbms-connections/{connectionId}/permissions/export` | GET | 認証必須 + `ROLE_ADMIN` | `PermissionController#exportPermissions` |
| `/api/rdbms-connections/{connectionId}/permissions/import` | POST | 認証必須 + `ROLE_ADMIN` | `PermissionController#importPermissions` |

いずれも認可の強制は`SecurityConfig`の`authorizeHttpRequests`に委ね、Controller側での二重チェックはしない。`/api/groups/**`はitem 5-4で新規追記した`hasRole("ADMIN")`エントリ。`/api/rdbms-connections/{connectionId}/permissions/**`は既存の`/api/rdbms-connections/**`エントリ（U3）が前方一致で包含するため追加不要（item 5-4で確認済み）。

## `GroupController`（`/api/groups`）

### `POST ""`（グループ作成）

リクエスト（`GroupCreateRequest`）:
```json
{"name": "team-a"}
```
`Authentication.getPrincipal()`を`adminUserId`として`GroupService.createGroup(adminUserId, name)`に渡す。成功時`201 Created`で新規グループの`id`（`Long`）。同名グループが存在する場合は`ValidationException`（400）。

### `PUT /{id}`（グループ名変更）

リクエスト（`GroupRenameRequest`）:
```json
{"name": "team-b"}
```
`GroupService.renameGroup(adminUserId, id, name)`を呼び出す。成功時`204 No Content`。存在しない`id`は`EntityNotFoundException`（404）、変更後の名前が既存の別グループと重複する場合は`ValidationException`（400）。

### `DELETE /{id}`（グループ削除）

ボディなし。`GroupService.deleteGroup(adminUserId, id)`を呼び出す。成功時`204 No Content`。所属メンバー・関連する権限設定・補助権限設定も連鎖削除される。存在しない`id`は`EntityNotFoundException`（404）。

### `GET ""`（グループ一覧）

`GroupService.listGroups()`を呼び出す。成功時`200 OK`で`List<GroupSummary>`:
```json
[{"id": 42, "name": "team-a", "createdAt": "2026-07-11T00:00:00Z"}]
```

### `GET /{id}/members`（グループメンバー一覧）

`GroupService.listGroupMembers(id)`を呼び出す。成功時`200 OK`で`List<UserSummary>`:
```json
[{"id": 7, "email": "user@example.com"}]
```
存在しない`id`は`EntityNotFoundException`（404）。

### `POST /{id}/members`（メンバー追加）

リクエスト（`GroupMemberAddRequest`）:
```json
{"userId": 7}
```
`GroupService.addUserToGroup(adminUserId, id, userId)`を呼び出す。成功時レスポンスボディなしの`201 Created`（`GroupMember`自体を返すエンドポイントではないため）。存在しない`id`/`userId`は`EntityNotFoundException`（404）、既にメンバーの場合は`ValidationException`（400）。

### `DELETE /{id}/members/{userId}`（メンバー削除）

ボディなし。`GroupService.removeUserFromGroup(adminUserId, id, userId)`を呼び出す。成功時`204 No Content`。存在しない`id`/非メンバーの`userId`は`EntityNotFoundException`（404）。

## `PermissionController`（`/api/rdbms-connections/{connectionId}/permissions`）

### `PUT ""`（権限更新）

リクエスト（`PermissionUpdateRequest`）。`permission`が存在すれば`setPermission`、存在しなければ`auxType`/`granted`を用いて`setAuxPermission`へ分岐する（ブラウンフィールド発見事項：Step 2で1メソッドに集約されず2メソッドに分かれているサービス設計に対し、Controller側で入力形状から分岐する設計とした）。

テーブル・カラム権限の設定例:
```json
{"principal": {"principalType": "USER", "principalId": 7},
 "schema": "public", "table": "employees", "column": null,
 "permission": "READ", "auxType": null, "granted": null}
```

補助権限（CREATE/DELETE）の設定例:
```json
{"principal": {"principalType": "GROUP", "principalId": 3},
 "schema": "public", "table": "employees", "column": null,
 "permission": null, "auxType": "DELETE", "granted": true}
```
`Authentication.getPrincipal()`を`adminUserId`として渡す。成功時`204 No Content`。`column`指定時に`table`未指定、または参照先テーブル/カラムやprincipalが存在しない場合は`ValidationException`（400）。

### `GET /export`（権限YAMLエクスポート）

ボディなし。`PermissionAssignmentService.exportPermissionsAsYaml(adminUserId, connectionId)`を呼び出す。成功時`200 OK`で`Content-Type: application/x-yaml`、`Content-Disposition: attachment; filename=permissions-{connectionId}.yaml`ヘッダを付けた`byte[]`ボディ（YAML形式）。リポジトリ初のbyte[]レスポンスエンドポイント。

### `POST /import`（権限YAMLインポート）

`multipart/form-data`でパート名`file`のYAMLファイルを受け取る。`file.getBytes()`（`IOException`は`UncheckedIOException`にラップ）した内容を`PermissionAssignmentService.importPermissionsFromYaml(adminUserId, connectionId, content)`に渡す。成功・形式不正いずれも例外を投げず`200 OK`で`ImportResult`を返す設計（`ImportResult.success`で成否を判定）:
```json
{"success": true, "message": "Import succeeded."}
```
```json
{"success": false, "message": "Missing required field: schema"}
```
リポジトリ初のmultipartアップロードエンドポイント。連結パーミッションは既存の`connectionId`配下の設定を全置換する（P8: 全置換設計）。

## エラーレスポンス

`GlobalExceptionHandler`（`@RestControllerAdvice`、U1既存＋U2/U3で追記済み）が全コントローラ共通で以下にマッピングする。本ユニットは`PermissionYamlFormatException`（item 5-3、ブラウンフィールド発見事項）を新規追加した。

| 例外 | HTTPステータス | `error` |
|---|---|---|
| 未認証（`SecurityConfig`のstateless認証チェーンで拒否） | `401 Unauthorized` | （`RestAuthenticationEntryPoint`が`ErrorResponse`を返却） |
| `ROLE_ADMIN`以外での認可失敗（`SecurityConfig`の`hasRole`で拒否） | `403 Forbidden` | （`RestAccessDeniedHandler`が`ErrorResponse`を返却） |
| `ValidationException`（グループ名重複、権限参照先未存在等） | `400 Bad Request` | `VALIDATION_ERROR` |
| `EntityNotFoundException`（グループ/メンバー未存在） | `404 Not Found` | `ENTITY_NOT_FOUND` |
| `PermissionYamlFormatException`（新規、item 5-3） | `400 Bad Request` | `PERMISSION_YAML_FORMAT_ERROR` |
| その他未捕捉の`Exception` | `500 Internal Server Error` | `INTERNAL_ERROR`（メッセージは固定文言、詳細はサーバーログにのみ出力） |

エラーボディ共通形状（`ErrorResponse`）:
```json
{"error": "ENTITY_NOT_FOUND", "message": "Group not found: id=42"}
```

**既知の課題**: `PermissionAssignmentService.importPermissionsFromYaml`は`PermissionYamlFormatException`を内部でcatchし`ImportResult(false, message)`を`200 OK`で返す設計（Step 2/3で確定済み）であるため、`/import`エンドポイントの実際のレスポンスとしては`PERMISSION_YAML_FORMAT_ERROR`（400）は到達しない。`GlobalExceptionHandler`側のハンドラは、同例外が将来的に他の経路（インポート以外）から投げられた場合の防御的フォールバックとして追加した。

## テストカバレッジ（Step 6）

| テストクラス | 検証内容 |
|---|---|
| `GroupControllerTest` | グループCRUD（create/rename/delete/list）・メンバー管理（list/add/remove）の7エンドポイントそれぞれについて管理者成功系・非管理者403・未認証401を検証（`create`/`rename`/`delete`/`add`/`remove`は`Authentication`を明示注入し`adminUserId`がサービス呼び出しに渡ることも確認）（example-based、21件） |
| `PermissionControllerTest` | 権限更新（`setPermission`/`setAuxPermission`の2分岐）・エクスポート・インポートの3エンドポイントそれぞれについて管理者成功系・非管理者403・未認証401、加えてインポート形式不正時の400（`GlobalExceptionHandler`との結線確認）を検証（example-based、11件） |

P1〜P11（業務ロジックの性質）はController層では再検証せず、`business-logic-summary.md`記載のjqwik `@Property`テスト（`GroupServiceTest`/`PermissionAssignmentServiceTest`/`EffectivePermissionResolverTest`等）に一元化している。
