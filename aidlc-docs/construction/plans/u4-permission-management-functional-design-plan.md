# U4: Permission Management — Functional Design Plan

## Step 1: ユニットコンテキスト分析

- **ユニット定義**（`unit-of-work.md`）: バックエンドパッケージ `group`, `permission`。
  フロントエンド `features/group/`, `features/permission/`（U4 Functional Designで
  単一パッケージから分割、詳細はQ7参照。U2の`auth`/`userregistration`、U3の
  `rdbmsconnection`/`schema`と同様の分割方針）。対応ストーリー MVP-9, ADM-1, ADM-2, ADM-4,
  ADM-5。責務: テーブル/カラム単位のアクセス権限（ユーザ個別・グループ）の設定と、実効権限の
  解決。
  - ユーザグループの作成・所属管理（ADM-1）
  - 主権限（なし/R/RU、スキーマ/テーブル/カラムの3階層）・補助権限（作成C/削除D、
    スキーマ/テーブルの2階層）の設定・変更、YAMLエクスポート/インポート
    （MVP-9, ADM-2, ADM-4, ADM-5）
  - 実効権限解決（`EffectivePermissionResolver`）— グループ合成・個別上書き・階層継承・
    補助権限と主キー権限の組合せ判定、アクセス可能スキーマ/テーブル一覧フィルタ。
    U5/U6/U7から共通利用される中心的Facade
  - 主要コンポーネント: `GroupService`, `PermissionAssignmentService`,
    `EffectivePermissionResolver`
  - U1（`common`, `audit`）, U2（`userregistration`）, U3（`schema`）に依存。
- **対応ストーリー**（`stories.md`）:
  - MVP-9: テーブル/カラム単位のアクセス権限設定（個別ユーザ）
  - ADM-1: ユーザグループの作成（グループ名指定で作成、既存ユーザの追加/削除、
    1ユーザが複数グループに所属可能）
  - ADM-2: グループ単位のアクセス権限設定（MVP-9と同粒度、ユーザ個別権限がグループ権限を
    優先、変更は監査記録）
  - ADM-4: アクセス権限設定のYAMLエクスポート（接続単位、ユーザ/グループ・テーブル/カラム
    単位の設定を含む）
  - ADM-5: アクセス権限設定のYAMLインポート（アップロード反映、形式不正時はエラー表示・
    未反映、操作は監査記録）
- **既存の確定事項**（Application Design / U1-U3から継承、再検討不要）:
  - `component-methods.md`に以下のメソッドシグネチャが定義済み:
    ```
    GroupService:
      Long createGroup(String name)
      void addUserToGroup(Long groupId, Long userId)
      void removeUserFromGroup(Long groupId, Long userId)
      List<GroupSummary> listGroups()
      List<UserSummary> listGroupMembers(Long groupId)

    PermissionAssignmentService:
      void setPermission(PrincipalRef principal, Long connectionId, String schema,
                          Optional<String> table, Optional<String> column, Permission permission)
        // Permission = NONE | READ | UPDATE
      void setAuxPermission(PrincipalRef principal, Long connectionId, String schema,
                             Optional<String> table, AuxPermissionType auxType, boolean granted)
        // AuxPermissionType = CREATE | DELETE
      byte[] exportPermissionsAsYaml(Long connectionId)
      ImportResult importPermissionsFromYaml(Long connectionId, byte[] yamlContent)
        // 形式不正時: PermissionYamlFormatException（反映せずエラー表示、監査ログに記録）

    PrincipalRef = (PrincipalType[USER|GROUP], principalId)

    EffectivePermissionResolver:
      Permission resolveEffectiveTablePermission(Long userId, Long connectionId, String schema, String table)
      Map<String, Permission> resolveEffectiveColumnPermissions(Long userId, Long connectionId, String schema, String table)
      boolean canCreate(Long userId, Long connectionId, String schema, String table)
      boolean canDelete(Long userId, Long connectionId, String schema, String table)
      List<String> listAccessibleSchemas(Long userId, Long connectionId)
      List<String> listAccessibleTables(Long userId, Long connectionId, String schema)
    ```
  - 判定ロジックの要旨（Application Design Question 1, 2, 9で確定済み、変更不要）:
    1. 主権限はスキーマ→テーブル→カラムの順に継承（下位の明示設定が上位を上書き）。
    2. 補助権限（C/D）も同じ継承・上書きをスキーマ→テーブルの2階層で適用。
    3. 複数グループ所属時はグループごとに解決した上で「最も緩い権限」で合成
       （主権限は なし＜R＜RUの最大、補助権限はOR）。
    4. ユーザ個別設定が存在する階層は、3.のグループ合成結果を上書き（個別設定がない階層は
       合成結果を継続適用＝部分上書きが起こり得る）。
    5. `canCreate`: 補助権限C有効 かつ 主キー構成全カラムがRU（複合主キーはAND）。主キー
       なしテーブルは補助権限Cのみで作成許可（例外規定）。
    6. `canDelete`: 補助権限D有効 かつ 主キー構成全カラムがR以上（AND）。主キーなしテーブルは
       常に不可。
  - `PermissionAssignmentService.setPermission`/`setAuxPermission`は`table`/`column`を
    **物理名（String）で参照**する（`SchemaTable.id`/`SchemaColumn.id`への内部ID直接参照では
    ない）— U3の`domain-entities.md`「設計判断」節で確認済み。したがってスキーマ再取り込み
    （U3 Q6のupsert・staleフラグ）が権限データの整合性に直接影響することはない。
  - `User`（U2, `auth`/`userregistration`）: `id`, `email`, `role`（`ADMIN`/`USER`）,
    `status`等。ロールは管理者/一般ユーザの2種類のみ。
  - `SchemaTable`/`SchemaColumn`（U3, `schema`）: `connectionId`, `schemaName`, `tableName`/
    `columnName`, `stale`（削除されたが残置）等。権限設定UIは`SchemaQueryService`
    （`listSchemas`/`listTables`/`getTableDetail`）が返すメタデータを参照して構築する想定。
  - 監査ログ（U1, `audit`）: `AuditLogService.record(EventCategory, EventType, Long userId,
    Long connectionId, Result, String targetDescription, String summaryMessage)`。
    `EventType`に本ユニット向けの値が既に予約済み: `GROUP_CHANGED`, `PERMISSION_CHANGED`,
    `PERMISSION_YAML_EXPORTED`, `PERMISSION_YAML_IMPORTED`（`EventCategory.ADMIN_OPERATION`）。
    新規`EventType`追加は不要。
  - U5（Master Data Maintenance）・U6（Query Builder）はいずれも`EffectivePermissionResolver`を
    共通Facadeとして利用する設計（`unit-of-work.md`）。本ユニットのAPI形状（特に
    `resolveEffectiveColumnPermissions`の戻り値やアクセス可能一覧系メソッド）は後続ユニットの
    実装に直接影響するため、Functional Designで曖昧さなく確定する必要がある。

## Step 2-4: 計画・質問

以下8問について回答をお願いします。各質問には推奨案（A）を用意していますが、
別の選択肢や自由記述でも構いません。

---

### Q1. Domain Model — グループ・所属関係のエンティティ構成

`GroupService`が扱う`Group`とユーザ所属関係をどう表現するか。

- **A（推奨）**: `Group`（`id`, `name`（unique, not null）, `createdAt`）と、`GroupMember`
  （中間テーブル、`id`, `groupId`（FK）, `userId`（FK）, `joinedAt`）の2エンティティで表現する。
  一意制約`(groupId, userId)`で同一ユーザの重複所属を防ぐ（1ユーザが複数グループに所属する
  こと自体はADM-1で許容、あくまで同一グループへの重複追加を防ぐ制約）。
- **B**: `User`エンティティ側に`groupIds`のようなコレクションを直接持たせ、中間テーブルを
  JPAの`@ManyToMany`で暗黙管理する。
- **C**: その他（自由記述）

[Answer]: A

---

### Q2. Domain Model — 権限データの保存モデル

主権限（NONE/READ/UPDATE、スキーマ/テーブル/カラムの3階層）と補助権限（CREATE/DELETE、
スキーマ/テーブルの2階層）を内部DBにどう保存するか。継承ルール上、明示設定のない階層は
行を持たない（疎な保存）想定。

- **A（推奨）**: 2つのテーブルに分離する。
  - `PermissionAssignment`（主権限）: `id`, `principalType`（`USER`/`GROUP`）, `principalId`,
    `connectionId`, `schemaName`（not null）, `tableName`（nullable）, `columnName`
    （nullable、`tableName`がnullの場合は必ずnull）, `permission`（`NONE`/`READ`/`UPDATE`）,
    `updatedAt`。一意制約`(principalType, principalId, connectionId, schemaName, tableName,
    columnName)`（null許容列を含む複合一意制約、H2/MySQL/PostgreSQL/MariaDBいずれもNULLを
    区別せず許容する動作を前提とする）。
  - `AuxPermissionAssignment`（補助権限）: `id`, `principalType`, `principalId`,
    `connectionId`, `schemaName`（not null）, `tableName`（nullable）, `auxType`（`CREATE`/
    `DELETE`）, `granted`（boolean）, `updatedAt`。一意制約`(principalType, principalId,
    connectionId, schemaName, tableName, auxType)`。
  - 主権限・補助権限で階層数（3 vs 2）と値の型（enum vs boolean）が異なるため、無理に
    1テーブルへ統合せず分離する。
- **B**: 主権限・補助権限を1つの`PermissionAssignment`テーブルに統合し、
  `permissionType`（`MAIN`/`AUX_CREATE`/`AUX_DELETE`）のような判別列と汎用`value`列
  （文字列 or boolean相当）で表現する。
- **C**: その他（自由記述）

[Answer]: A

---

### Q3. Business Rules — 権限設定時のバリデーション

`setPermission`/`setAuxPermission`呼び出し時、どこまで入力検証を行うか。

- **A（推奨）**: (1) 階層の整合性チェック——`column`を指定する場合は`table`も指定必須
  （`table`なしで`column`のみの指定はエラー）。(2) 参照整合性チェック——指定した
  `schemaName`/`tableName`/`columnName`が、対象接続に取り込み済みの`SchemaTable`/
  `SchemaColumn`として実在すること（`stale=true`の行も許容——削除済みテーブルへの権限が
  残っていても実効権限解決時にそのテーブル自体がアクセス不能になるだけで、権限設定操作
  自体は妨げない）。存在しない物理名を指定した場合は`IllegalArgumentException`相当の
  例外とし、監査ログには失敗として記録する。(3) `setAuxPermission`は`column`パラメータを
  持たないため階層チェックは不要（スキーマ/テーブルの2階層のみ）。(4) principal実在
  チェック——`PrincipalRef`の`principalId`が、`principalType`に応じて実在の`User.id`
  （`USER`の場合）または`Group.id`（`GROUP`の場合）を指していること。`principalId`は
  DBレベルのFKを持てない（`principalType`次第で参照先テーブルが変わるポリモーフィックな
  参照のため）ので、この存在確認をアプリケーション層のバリデーションで代替する。存在しない
  `principalId`を指定した場合も(2)と同様に例外とし、監査ログに失敗を記録する。
- **B**: 参照整合性チェックは行わず、任意の文字列をそのまま保存する（存在しない物理名でも
  許可、`EffectivePermissionResolver`側で無視される想定）。
- **C**: その他（自由記述）

[Answer]: A

---

### Q4. Business Rules — グループ管理のスコープ

`GroupService`のメソッドは`createGroup`/`addUserToGroup`/`removeUserFromGroup`/
`listGroups`/`listGroupMembers`の5つが`component-methods.md`で確定済み。ADM-1の受け入れ
基準（グループ名指定で作成、既存ユーザの追加/削除、複数グループ所属）はこの5メソッドで
満たせるが、グループ名の変更・グループ自体の削除は明記されていない。

- **A（推奨）**: 本ユニットのスコープは`component-methods.md`確定済みの5メソッドのみとし、
  グループ名変更・グループ削除はMVPスコープ外とする（要件・ストーリーに記載がなく、
  将来必要になれば別途追加する）。グループ名の一意性のみ`createGroup`でチェックする。
- **B（採用）**: グループ名変更（`renameGroup`）・グループ削除（`deleteGroup`、削除時は
  `GroupMember`・関連する`PermissionAssignment`/`AuxPermissionAssignment`も連動して削除）を
  本ユニットに追加する。
  - 追加メソッドシグネチャ（`component-methods.md`のApplication Design確定分に対する
    Functional Design段階での追記として扱う。既存5メソッドの変更ではなく追加のため、
    Application Designの承認済み決定と矛盾しない）:
    ```
    GroupService:
      void renameGroup(Long groupId, String newName)
        // 一意性チェックはcreateGroupと同様
      void deleteGroup(Long groupId)
        // カスケード削除: GroupMember（該当groupId分）、
        // PermissionAssignment/AuxPermissionAssignment（principalType=GROUP かつ
        // principalId=groupId分）を同一トランザクションで削除
    ```
  - `deleteGroup`は監査ログ`EventType.GROUP_CHANGED`で記録する（既存の`addUserToGroup`等と
    同じイベント種別、`summaryMessage`で操作種別（削除）を区別する）。
- **C**: その他（自由記述）

[Answer]: B

---

### Q5. Business Logic Modeling — YAMLエクスポート/インポートのフォーマット設計

`exportPermissionsAsYaml`/`importPermissionsFromYaml`が扱うYAML構造をどう設計するか
（ADM-4: 「ユーザ/グループ・テーブル/カラム単位の設定が含まれる」）。

- **A（採用、修正版）**: `connectionId`はYAML本文に含めない（`exportPermissionsAsYaml`/
  `importPermissionsFromYaml`とも`connectionId`はメソッド引数（APIではパスパラメータ）
  として渡され、YAML内で重複保持する必要がないため）。principal（ユーザ/グループ）ごとに
  配下へ主権限・補助権限の設定一覧をネストする構造とし、principalの参照キーは内部IDでは
  なく**メールアドレス（USER）/グループ名（GROUP）**とする（IDは視認性に欠けるため）。
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
  インポート時は`User.email`（一意）/`Group.name`（一意、Q1で確定）で該当principalを照合する。
  一致するユーザ/グループが存在しない場合はQ6のエラー処理に従う（`PermissionYamlFormatException`、
  一切反映しない）。
- **B**: principalではなくスキーマ/テーブル/カラムを起点にネストし、各項目配下に
  アクセス可能なprincipalの一覧を列挙する構造（テーブル中心のレビューがしやすい一方、
  同一principalの設定が複数箇所に分散する）。A/B両対応は不採用（インポート時の形式検出・
  二重のバリデーション実装コストに見合う要件上の必要性がないため）。
- **C**: その他（自由記述）

[Answer]: A（修正版）

---

### Q6. Error Handling — YAMLインポート時の検証・エラー処理

`importPermissionsFromYaml`で「形式不正時はエラー表示・反映しない」（ADM-5）をどこまで
厳密に扱うか。

- **A（推奨、Q5修正版に合わせて更新）**: 以下いずれかに該当すれば
  `PermissionYamlFormatException`とし、**一切反映しない**（部分反映は行わない、Q3の
  バリデーションと同じ全件ロールバック方針）。
  1. YAML構文自体が不正（パース不能）
  2. 必須フィールド欠落（`type`/`email`（USER時）/`name`（GROUP時）/`schema`/`permission`等）
  3. `type`/`permission`/`auxType`が既定の enum 値以外
  4. 参照先の`email`（USER）/`name`（GROUP）に一致するユーザ/グループが存在しない、または
     テーブル/カラム物理名が対象接続に存在しない（Q3の参照整合性チェックをインポート時にも
     同様に適用）
  5. **同一ファイル内での重複定義**——同一principal（`email`/`name`で識別）×`schema`×
     `table`×`column`の組み合わせが`permissions`（または`schema`×`table`×`auxType`の組み合わせが
     `auxPermissions`）配下に複数回出現する（`PermissionAssignment`/`AuxPermissionAssignment`の
     一意制約（Q2）に対応する重複をインポート時点で検出する）。
  例外メッセージには最初に検出した違反内容の概要を含める（詳細な行番号特定までは行わない）。
  失敗時も`AuditLogService.record`で`PERMISSION_YAML_IMPORTED`・`Result.FAILURE`を記録する
  （ADM-5「インポート操作は監査ログに記録される」は成功/失敗どちらも対象と解釈）。
  - **既存設定との関係（全置換方式を採用）**: バリデーションを通過した場合、対象接続の
    既存`PermissionAssignment`/`AuxPermissionAssignment`を全削除してからYAMLの内容で
    再構築する（マージではなく全置換）。「エクスポート→編集→再インポート」で
    YAMLから削除したエントリが実際の権限からも削除される、という単純なメンタルモデルを
    優先する。削除→再構築は同一トランザクション内で行う。
- **B**: 個々のprincipal/権限エントリ単位で検証し、不正なエントリのみスキップして残りは
  反映する（部分成功を許容、`ImportResult`に成功件数・スキップ件数・エラー概要を含める）。
- **C**: その他（自由記述）

[Answer]: A（重複拒否・全置換方式を含む）

---

### Q7. Frontend Components — `group/`・`permission/`機能のコンポーネント構成

U1-U3の`frontend-components.md`と同様の粒度で、本ユニットのフロントエンド機能を
どこまで詳細に設計するか。

- **A（採用、修正版）**: `group`と`permission`を別featureとして分割する（U2の
  `auth`/`userRegistration`、U3の`rdbmsConnection`/`schema`と同様の分割方針。バックエンド
  パッケージ分割（Step 1参照）に対応）。以下の画面・コンポーネントを設計する（いずれも
  管理者専用、`/admin`配下）。
  - **`features/group/`**: `GroupListPage`（グループ一覧、`DataTable`再利用、新規作成・
    名称変更・削除導線、削除は`ConfirmDialog`で確認）、`GroupDetailPage`（グループ名表示、
    所属ユーザ一覧・追加/削除）。
  - **`features/permission/`**: `PermissionAssignmentPage`（接続選択→principal（ユーザ or
    グループ）選択→スキーマ/テーブル/カラムのツリー表示（U3の`SchemaQueryService`メタデータを
    利用）→各階層に主権限・補助権限を設定するフォーム）。principal選択でグループを選ぶ際は
    `features/group/`の`groupApi.listGroups()`を呼び出し参照する（機能間の一方向依存、
    バックエンドの`permission`→`group`依存と対応）。`PermissionYamlPanel`（選択中接続の
    エクスポート（ダウンロード）ボタン、インポート（ファイルアップロード）ボタン+結果表示）。
  - `AppRouter.tsx`に`/admin/groups`、`/admin/groups/:id`、
    `/admin/permissions`（接続選択含む）等のルートを追加する。
- **B**: 別の粒度・構成を希望する（自由記述）。

[Answer]: A（group/permission分割版）

---

### Q8. Business Logic Modeling — `EffectivePermissionResolver`のパフォーマンス方針（技術非依存の範囲）

U5/U6/U7から頻繁に呼び出される中心的Facadeであるため、技術非依存の業務ロジックとして
「毎回ストレージへ問い合わせて都度計算する」以外の方針が必要かを確認する（キャッシュの
具体的な実装方式自体はNFR Designで決定するが、業務ロジック上「呼び出しごとに最新の権限
設定を反映する」ことを要件とするか、「一定期間古い設定が見える」ことを許容するかは
Functional Designで確定すべき業務要件）。

- **A（推奨）**: 呼び出しごとに常に最新の権限設定を反映する（strong consistency）。
  権限変更（`setPermission`等）の直後に`EffectivePermissionResolver`を呼び出した場合、
  必ず変更後の値が返る。NFR Designでキャッシュを導入する場合も、更新時の即時無効化
  （write-through/invalidate-on-write）が必須制約となる。
  - **キャッシュ無効化が必要な操作の洗い出し（業務ロジックとして確定、実装方式はNFR
    Designで決定）**: `PermissionAssignmentService`の書き込み系
    （`setPermission`/`setAuxPermission`/`importPermissionsFromYaml`）に加え、
    `GroupService`の書き込み系（`addUserToGroup`/`removeUserFromGroup`/`renameGroup`/
    `deleteGroup`）も対象に含む。グループ所属の変更はグループ合成結果（判定ロジック要旨3.）
    に影響し、権限設定自体に変更がなくても実効権限が変わり得るため。
- **B**: 一定期間（数秒〜数分）古い権限設定が見える可能性を許容する（eventual
  consistency、NFR Designでのキャッシュ設計の自由度が上がる一方、権限変更直後に
  一般ユーザ側へ反映されないタイムラグが生じ得る）。
- **C**: その他（自由記述）

[Answer]: A（キャッシュ無効化対象操作の洗い出しを含む）

---

## Step 6: 成果物生成チェックリスト

- [x] `aidlc-docs/construction/u4-permission-management/functional-design/domain-entities.md`
- [x] `aidlc-docs/construction/u4-permission-management/functional-design/business-rules.md`
- [x] `aidlc-docs/construction/u4-permission-management/functional-design/business-logic-model.md`
  （PBT-01: テスト可能な性質セクションを含む、P1〜P10）
- [x] `aidlc-docs/construction/u4-permission-management/functional-design/frontend-components.md`