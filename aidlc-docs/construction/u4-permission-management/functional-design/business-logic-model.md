# business-logic-model.md — U4: Permission Management

`domain-entities.md`・`business-rules.md`で定義したモデル・ルールに基づく業務フロー。
技術非依存（実装方式はNFR Design/Code Generationで確定）。

---

## フロー1: グループの作成・所属管理（ADM-1、Q1, Q4）

**関与コンポーネント**: フロントエンド`group/` → `GroupService` → `AuditLogService`（U1）

1. 管理者が`GroupListPage`で「新規作成」からグループ名を入力すると`createGroup(name)`が
   呼び出される（`business-rules.md` 1.1の一意性チェック）。
2. `GroupListPage`の行内アクションから「名称変更」（`renameGroup(groupId, newName)`、
   Q4）・「削除」（`deleteGroup(groupId)`、`ConfirmDialog`で確認後に実行、Q4）を実行できる。
   `deleteGroup`は`business-rules.md` 1.3のカスケード削除（`GroupMember`・当該グループの
   `PermissionAssignment`/`AuxPermissionAssignment`）を同一トランザクションで行う。
3. `GroupDetailPage`はグループ名と`listGroupMembers(groupId)`によるユーザ一覧を表示し、
   「追加」「削除」導線から`addUserToGroup(groupId, userId)`/`removeUserFromGroup(groupId,
   userId)`を呼び出す（`business-rules.md` 1.2の重複チェック）。
4. いずれの書き込み操作も成功/失敗を問わず`AuditLogService.record(ADMIN_OPERATION,
   GROUP_CHANGED, adminUserId, ...)`を呼び出す（`business-rules.md` 1.4）。
5. `GroupListPage`は`listGroups()`でグループ一覧を表示する（ADM-1 AC）。

---

## フロー2: テーブル/カラム単位の権限設定（MVP-9, ADM-2、Q3）

**関与コンポーネント**: フロントエンド`permission/` → `PermissionAssignmentService` →
`group/`の`GroupService`（principal選択時のグループ一覧参照） → `schema/`の
`SchemaQueryService`（U3、階層メタデータ参照） → `AuditLogService`（U1）

1. 管理者が`PermissionAssignmentPage`で接続を選択し、principal（ユーザ or グループ）を
   選択する。グループを選ぶ場合は`features/group/`の`groupApi.listGroups()`を呼び出す
   （機能間の一方向依存、`unit-of-work.md`参照）。
2. `SchemaQueryService.listSchemas`/`listTables`/`getTableDetail`（U3）が返す取り込み済み
   メタデータを用いてスキーマ/テーブル/カラムのツリーを表示する。
3. 各階層に対し主権限（`NONE`/`READ`/`UPDATE`）を選択、テーブル/スキーマ階層には補助権限
   （`CREATE`/`DELETE`の許可有無）を選択するフォームを操作する。
4. 保存操作で`setPermission(principal, connectionId, schema, table, column, permission)`/
   `setAuxPermission(principal, connectionId, schema, table, auxType, granted)`が呼び出され、
   `business-rules.md` 2.1の検証（階層整合性・参照整合性・principal実在チェック）を通過した
   場合のみ保存される。
5. 検証に違反した場合は例外となり画面にエラー表示する（保存しない）。成功/失敗いずれも
   `AuditLogService.record(ADMIN_OPERATION, PERMISSION_CHANGED, adminUserId, connectionId,
   ...)`を呼び出す（`business-rules.md` 2.2）。
6. 保存成功後、`EffectivePermissionResolver`のキャッシュ無効化がトリガーされる
   （`business-rules.md` 2.6、実装方式はNFR Design）。

---

## フロー3: 権限設定のYAMLエクスポート（ADM-4、Q5）

**関与コンポーネント**: フロントエンド`permission/` → `PermissionAssignmentService` →
`AuditLogService`（U1）

1. 管理者が`PermissionYamlPanel`で接続を選択し「エクスポート」ボタンを押下すると
   `exportPermissionsAsYaml(connectionId)`が呼び出される。
2. 対象接続の全`PermissionAssignment`/`AuxPermissionAssignment`をprincipal
   （`User.email`/`Group.name`で解決）ごとにグルーピングし、`business-rules.md` 2.3の
   YAML構造で組み立てる。
3. `AuditLogService.record(ADMIN_OPERATION, PERMISSION_YAML_EXPORTED, adminUserId,
   connectionId, Result.SUCCESS, ...)`を呼び出す（ADM-4 AC）。
4. フロントエンドはレスポンスのYAMLバイト列をファイルとしてダウンロードさせる。

---

## フロー4: 権限設定のYAMLインポート（ADM-5、Q6）

**関与コンポーネント**: フロントエンド`permission/` → `PermissionAssignmentService` →
`AuditLogService`（U1）

1. 管理者が`PermissionYamlPanel`で接続を選択し、YAMLファイルをアップロードすると
   `importPermissionsFromYaml(connectionId, yamlContent)`が呼び出される。
2. `business-rules.md` 2.4の検証チェックリスト（構文・必須フィールド・enum値・参照整合性・
   同一ファイル内重複）を順に評価する。いずれかに違反すれば`PermissionYamlFormatException`と
   なり、一切反映せずロールバックする。
3. 全ての検証を通過した場合、同一トランザクション内で対象接続の既存
   `PermissionAssignment`/`AuxPermissionAssignment`を全削除し、YAMLの内容から再構築する
   （全置換方式、`business-rules.md` 2.4）。
4. 成功/失敗いずれも`AuditLogService.record(ADMIN_OPERATION, PERMISSION_YAML_IMPORTED,
   adminUserId, connectionId, Result.SUCCESS|FAILURE, ...)`を呼び出す。
5. `ImportResult`（成功可否、失敗時は違反概要）をフロントエンドへ返し、
   `PermissionYamlPanel`が結果を表示する。
6. インポート成功後、`EffectivePermissionResolver`のキャッシュ無効化がトリガーされる
   （`business-rules.md` 2.6）。

---

## フロー5: 実効権限解決（`EffectivePermissionResolver`、横断的Facade、Q8）

**関与コンポーネント**: `EffectivePermissionResolver`（U5/U6/U7から呼び出される支援機構
であり、本ユニット単独のUI操作トリガーは持たない）

1. `resolveEffectiveTablePermission`/`resolveEffectiveColumnPermissions`/`canCreate`/
   `canDelete`/`listAccessibleSchemas`/`listAccessibleTables`のいずれかが呼び出されると、
   対象ユーザの所属グループ（`GroupMember`）と、個別/グループの`PermissionAssignment`/
   `AuxPermissionAssignment`を参照し、`business-rules.md` 2.5の判定ロジック（階層継承→
   グループ合成→個別上書き→補助権限とのAND/OR判定）を適用する。
2. 呼び出しごとに常に最新の権限設定を反映する（`business-rules.md` 2.6のstrong
   consistency要件）。フロー1（グループ書き込み系）・フロー2（`setPermission`/
   `setAuxPermission`）・フロー4（`importPermissionsFromYaml`）のいずれかが実行された
   直後の呼び出しは、必ず変更後の値を返す。加えて、U3の`SchemaImportService.importSchema`
   （再取り込み）が主キー構成（`primaryKeySequence`）や`stale`フラグを変更した場合も、
   直後の呼び出しは変更後の値（`canCreate`/`canDelete`の再判定結果、アクセス可否）を
   返す（`business-rules.md` 2.6）。
3. U5（Master Data Maintenance）・U6（Query Builder）・U7（Saved Query / Execution /
   History）は、この判定結果に基づいてアクセス可能なスキーマ/テーブル/カラムのフィルタ、
   作成/削除操作の可否を決定する（`unit-of-work.md`）。

---

## テスト可能な性質（Testable Properties, PBT-01）

`property-based-testing`拡張（enabled）のRule PBT-01に基づき、本ユニットの業務ロジック
（フロー1〜5）が持つ性質をカテゴリ別に識別する。実際のPBTケース設計・生成器定義はCode
Generation計画時に確定する。

| # | 対象 | カテゴリ | 性質 | 備考 |
|---|---|---|---|---|
| P1 | `GroupService.deleteGroup`（フロー1） | Invariant（状態遷移） | `deleteGroup(groupId)`実行後、当該`groupId`を参照する`GroupMember`/`PermissionAssignment`/`AuxPermissionAssignment`は1件も存在しない | `business-rules.md` 1.3、Q4 |
| P2 | `GroupService.addUserToGroup`（フロー1） | Idempotence（拒否の一貫性という意味での） | 既に所属済みの`(groupId, userId)`に対する2回目の`addUserToGroup`呼び出しは常に例外となり、`GroupMember`の行数は変化しない | `business-rules.md` 1.2 |
| P3 | `PermissionAssignmentService.setPermission`/`setAuxPermission`（フロー2） | Idempotence | 同一`(principal, connectionId, schema, table, column)`に同一`permission`値で複数回呼び出しても、`PermissionAssignment`は常に1行のみ存在し値も変化しない | `domain-entities.md`一意制約、Q2 |
| P4 | `exportPermissionsAsYaml` → `importPermissionsFromYaml`（フロー3→フロー4） | Round-trip | 他の変更が介在しない限り、ある接続に対し`exportPermissionsAsYaml`で得たYAMLをそのまま同じ接続へ`importPermissionsFromYaml`した結果、`(principalType, principal識別子, schema, table, column, permission)`／`(..., auxType, granted)`のタプル集合はエクスポート前と完全一致する（`id`/`updatedAt`は対象外） | Q5, Q6全置換方式 |
| P5 | `importPermissionsFromYaml`の重複検出（フロー4） | Invariant | `permissions`（または`auxPermissions`）配下に同一`(principal, schema, table, column)`（または`(..., auxType)`）の組み合わせが2回以上出現するYAMLは、出現順序やその他の内容によらず常に`PermissionYamlFormatException`となる | `business-rules.md` 2.4項目5 |
| P6 | `importPermissionsFromYaml`の全置換（フロー4） | Invariant（状態遷移） | 検証を通過したインポート成功後、対象`connectionId`の`PermissionAssignment`/`AuxPermissionAssignment`集合はYAMLの内容と完全に一致し、インポート前に存在した（YAMLに含まれない）行は1件も残らない | `business-rules.md` 2.4「全置換方式」 |
| P7 | `EffectivePermissionResolver`のグループ合成（フロー5、`business-rules.md` 2.5手順3） | Commutativity | ユーザが複数グループに所属する場合、グループを評価する順序を入れ替えても「最も緩い権限」（主権限は最大値、補助権限はOR）による合成結果は変化しない | 最大値/ORはいずれも可換 |
| P8 | `EffectivePermissionResolver`の階層継承・個別上書き（フロー5、`business-rules.md` 2.5手順1・4） | Invariant | ある階層（スキーマ/テーブル/カラム）に明示的な個別設定が存在する場合、`resolveEffectiveTablePermission`/`resolveEffectiveColumnPermissions`の結果は常にその明示設定の値と一致する（継承・グループ合成の結果によらず上書きされる） | Q3, 2.5手順4 |
| P9 | `EffectivePermissionResolver.canCreate`/`canDelete`（フロー5、`business-rules.md` 2.5手順5・6） | Invariant | 主キーを持たないテーブルに対し`canDelete`は常に`false`を返す（補助権限D・主権限の値によらない） | 2.5手順6の例外規定 |
| P10 | `EffectivePermissionResolver`の一貫性（フロー5、`business-rules.md` 2.6） | Invariant（状態遷移） | フロー1/2/4のいずれかの書き込み操作が成功した直後に`EffectivePermissionResolver`を呼び出すと、必ず書き込み後の権限設定を反映した結果が返る（呼び出し順序に関わらず古い値が返ることはない） | Q8、strong consistency |
| P11 | `EffectivePermissionResolver`とU3`SchemaImportService.importSchema`の一貫性（フロー5、`business-rules.md` 2.6） | Invariant（状態遷移） | `importSchema`の再取り込みが対象カラムの`primaryKeySequence`または`SchemaTable`/`SchemaColumn`の`stale`フラグを変更した場合、その直後に`canCreate`/`canDelete`/`resolveEffectiveColumnPermissions`を呼び出すと、`PermissionAssignment`/`AuxPermissionAssignment`側の行に変更がなくても、必ず変更後のスキーマ構造を反映した結果が返る | U3との連携、実装方式（通知手段）はNFR Designで決定 |