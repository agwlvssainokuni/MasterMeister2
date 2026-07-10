# business-rules.md — U4: Permission Management

`u4-permission-management-functional-design-plan.md`の回答（Q1〜Q8）に基づく業務ルール定義。

---

## 1. グループ管理（group）

### 1.1 グループ名の一意性（Q1, Q4）
`createGroup(name)`/`renameGroup(groupId, newName)`はいずれも`Group.name`の一意性を
チェックする。既存の別グループと同名の場合は`IllegalArgumentException`相当の例外とする。

### 1.2 所属の重複防止（Q1）
`addUserToGroup(groupId, userId)`は`GroupMember`の一意制約`(groupId, userId)`に対応する
重複を業務ロジック側で事前チェックする（既に所属済みのユーザを再度追加しようとした場合は
例外とし、DB制約違反として顕在化させない）。`removeUserFromGroup(groupId, userId)`は
対象の`GroupMember`行が存在しない場合も例外とする（存在しない所属関係の削除は不正操作と
みなす）。

### 1.3 グループ削除時のカスケード削除（Q4）
`deleteGroup(groupId)`は以下を同一トランザクションで削除する。
1. 該当`groupId`の`GroupMember`全行
2. `principalType = GROUP` かつ `principalId = groupId`の`PermissionAssignment`/
   `AuxPermissionAssignment`全行

削除対象のグループに紐づく権限設定を残置しない（孤立した`principalId`参照を防ぐ、Q3 (4)の
実在チェックと整合）。

### 1.4 監査記録（Q4、既存規約の適用）
`createGroup`/`addUserToGroup`/`removeUserFromGroup`/`renameGroup`/`deleteGroup`はいずれも
成功時に`AuditLogService.record(ADMIN_OPERATION, GROUP_CHANGED, adminUserId, ...,
targetDescription=グループ名)`を呼び出す（U1`domain-entities.md`で`GROUP_CHANGED`イベント
種別が定義済み）。`summaryMessage`で操作種別（作成/追加/削除/名称変更/グループ削除）を
区別する。失敗時（1.1/1.2のバリデーション例外）も`Result.FAILURE`で記録する。

---

## 2. 権限設定（permission）

### 2.1 `setPermission`/`setAuxPermission`の入力検証（Q3）
以下を順にチェックし、いずれかに違反する場合は例外とし一切保存しない。監査ログには
`PERMISSION_CHANGED`・`Result.FAILURE`で記録する。
1. **階層の整合性**: `column`を指定する場合は`table`も指定必須（`setPermission`のみ、
   `setAuxPermission`は`column`パラメータを持たないため対象外）。
2. **参照整合性**: 指定した`schemaName`/`tableName`/`columnName`が対象接続に取り込み済みの
   `SchemaTable`/`SchemaColumn`として実在すること（`stale = true`の行も許容 — 削除済み
   テーブルへの権限が残っていても実効権限解決時にそのテーブル自体がアクセス不能になるだけ）。
3. **principal実在チェック**: `PrincipalRef.principalId`が`principalType`に応じて実在の
   `User.id`（`USER`）または`Group.id`（`GROUP`）を指していること（`domain-entities.md`
   「設計判断」節）。

いずれの違反も存在しない物理名/principalを指定した場合と同様に`IllegalArgumentException`
相当の例外とする。

### 2.2 監査記録（既存規約の適用）
`setPermission`/`setAuxPermission`成功時は`AuditLogService.record(ADMIN_OPERATION,
PERMISSION_CHANGED, adminUserId, connectionId, Result.SUCCESS, targetDescription=principal
+スキーマ/テーブル/カラム, ...)`を呼び出す（MVP-9/ADM-2 AC）。2.1の検証失敗時は
`Result.FAILURE`で記録する。

### 2.3 YAMLエクスポート形式（Q5）
`exportPermissionsAsYaml(connectionId)`は`connectionId`をYAML本文に含めない（メソッド引数
として既に渡されているため）。principal（USER/GROUP）ごとに配下へ主権限
（`permissions`）・補助権限（`auxPermissions`）の設定一覧をネストする構造とし、principalの
参照キーは`User.email`（USER）/`Group.name`（GROUP）とする（内部IDではなく視認性を優先）。

```yaml
principals:
  - type: USER
    email: alice@example.com
    permissions:
      - schema: public
        table: employees        # 省略時はスキーマレベル設定
        column: salary           # 省略時はテーブルレベル設定
        permission: READ
    auxPermissions:
      - schema: public
        table: employees
        type: CREATE
        granted: true
  - type: GROUP
    name: sales_team
    permissions: [...]
    auxPermissions: [...]
```

成功時は`AuditLogService.record(ADMIN_OPERATION, PERMISSION_YAML_EXPORTED, adminUserId,
connectionId, Result.SUCCESS, ...)`を呼び出す（ADM-4 AC）。

### 2.4 YAMLインポートの検証・エラー処理（Q6）
`importPermissionsFromYaml(connectionId, yamlContent)`は以下いずれかに該当すれば
`PermissionYamlFormatException`とし、**一切反映しない**（部分反映は行わない）。
1. YAML構文自体が不正（パース不能）
2. 必須フィールド欠落（`type`/`email`（USER時）/`name`（GROUP時）/`schema`/`permission`等）
3. `type`/`permission`/`auxType`が既定のenum値以外
4. 参照先の`email`（USER）/`name`（GROUP）に一致するユーザ/グループが存在しない、または
   テーブル/カラム物理名が対象接続に存在しない（2.1の参照整合性チェックをインポート時にも
   同様に適用）
5. 同一ファイル内での重複定義 — 同一principal（`email`/`name`で識別）×`schema`×`table`×
   `column`の組み合わせが`permissions`配下に、または`schema`×`table`×`auxType`の組み合わせが
   `auxPermissions`配下に複数回出現する（`PermissionAssignment`/`AuxPermissionAssignment`の
   一意制約（`domain-entities.md`）に対応する重複をインポート時点で検出する）

例外メッセージには最初に検出した違反内容の概要を含める（詳細な行番号特定までは行わない）。
失敗時も`AuditLogService.record`で`PERMISSION_YAML_IMPORTED`・`Result.FAILURE`を記録する
（ADM-5「インポート操作は監査ログに記録される」は成功/失敗どちらも対象と解釈）。

**既存設定との関係（全置換方式）**: 上記の検証を全て通過した場合、対象接続
（`connectionId`）の既存`PermissionAssignment`/`AuxPermissionAssignment`を全削除してから
YAMLの内容で再構築する（マージではなく全置換）。削除→再構築は同一トランザクション内で
行う。成功時は`AuditLogService.record(..., PERMISSION_YAML_IMPORTED, Result.SUCCESS, ...)`を
呼び出す。

### 2.5 実効権限解決ロジック（Application Design Question 1, 2, 9で確定済み、再掲）
`EffectivePermissionResolver`は以下の順序でストレージ（`PermissionAssignment`/
`AuxPermissionAssignment`、`GroupMember`）を参照して判定する。
1. 主権限はスキーマ→テーブル→カラムの順に継承する（下位の明示設定が上位を上書き）。
2. 補助権限（C/D）も同じ継承・上書きをスキーマ→テーブルの2階層で適用する。
3. ユーザが複数グループに所属する場合はグループごとに解決した上で「最も緩い権限」で合成する
   （主権限は`NONE < READ < UPDATE`の最大値、補助権限はOR）。
4. ユーザ個別設定が存在する階層は、3.のグループ合成結果を上書きする（個別設定がない階層は
   合成結果を継続適用する — 部分上書きが起こり得る）。
5. `canCreate`: 補助権限C有効 かつ 主キー構成全カラムがUPDATE以上（複合主キーはAND）。
   主キーなしテーブルは補助権限Cのみで作成許可（例外規定）。
6. `canDelete`: 補助権限D有効 かつ 主キー構成全カラムがREAD以上（AND）。主キーなしテーブルは
   常に不可。

### 2.6 実効権限解決の一貫性要件（Q8）
`EffectivePermissionResolver`は呼び出しごとに常に最新の権限設定を反映する（strong
consistency）。権限変更の直後に本Facadeを呼び出した場合、必ず変更後の値が返らなければ
ならない。NFR Designでキャッシュを導入する場合も、更新時の即時無効化
（write-through/invalidate-on-write）が必須制約となる。

キャッシュ無効化が必要な操作（業務ロジックとして確定、実装方式はNFR Designで決定）:
`PermissionAssignmentService`の書き込み系（`setPermission`/`setAuxPermission`/
`importPermissionsFromYaml`）に加え、`GroupService`の書き込み系（`addUserToGroup`/
`removeUserFromGroup`/`renameGroup`/`deleteGroup`）も対象に含む。グループ所属の変更は
グループ合成結果（2.5 手順3.）に影響し、権限設定自体に変更がなくても実効権限が変わり得る
ため。

さらに、U3の`SchemaImportService.importSchema(connectionId)`（初回・再取り込み共通の
単一メソッド、`u3-rdbms-connection-schema-import/functional-design/business-rules.md`
2.2）も対象に含める。再取り込みは既存`SchemaColumn`の`primaryKeySequence`（主キー構成）を
更新しうる（同business-rules.md「再取り込み時のupsert・staleフラグ」）ため、`canCreate`/
`canDelete`（2.5 手順5・6、いずれも主キー構成に基づく判定）の結果が`PermissionAssignment`/
`AuxPermissionAssignment`側に変更がなくても変わりうる。また`SchemaTable`/`SchemaColumn`の
`stale`フラグの設定・解除（テーブル/カラムの追加・消失に対応）も、対象スキーマ/テーブル/
カラムがアクセス可能かどうかの実効判定に影響する。`importSchema`はU4のPermissionAssignment
行を直接更新しないため、この無効化契機はU4単独では検知できず、U3側からの通知（実装方式は
NFR Designで決定、例: ドメインイベント発行）が必要になる。

---

## 3. API認可（`SecurityConfig`、U1 NFR Design 1.3の規約に基づく）

本ユニットの全機能（グループ管理・権限設定・YAML入出力）は管理者専用（MVP-9, ADM-1,
ADM-2, ADM-4, ADM-5のペルソナはいずれも「管理者」）。U1 NFR Design 1.3の「管理者専用APIは
パスパターンごとに`hasRole("ADMIN")`を明示指定する」方針に従う。

| パスパターン（想定、正確なパスはCode Generationで確定） | 対象 |
|---|---|
| `POST /api/groups` | `createGroup` |
| `PUT /api/groups/{id}` | `renameGroup` |
| `DELETE /api/groups/{id}` | `deleteGroup` |
| `GET /api/groups` | `listGroups` |
| `GET /api/groups/{id}/members` | `listGroupMembers` |
| `POST /api/groups/{id}/members` | `addUserToGroup` |
| `DELETE /api/groups/{id}/members/{userId}` | `removeUserFromGroup` |
| `PUT /api/rdbms-connections/{connectionId}/permissions` | `setPermission`/`setAuxPermission`（principal・階層・値を含むリクエストボディで1件ずつ、またはバッチ更新。詳細はCode Generationで確定） |
| `GET /api/rdbms-connections/{connectionId}/permissions/export` | `exportPermissionsAsYaml` |
| `POST /api/rdbms-connections/{connectionId}/permissions/import` | `importPermissionsFromYaml` |

いずれも`hasRole("ADMIN")`を要求する（`permitAll()`のエンドポイントは本ユニットに存在
しない）。`EffectivePermissionResolver`はコントローラを持たない内部Facade（U5/U6/U7から
サービス層で直接呼び出される）であり、上記の認可規約とは別に、呼び出し元ユニットが
一般ユーザ向けAPIとして提供する。