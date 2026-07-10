# u4-permission-management-code-generation-plan.md

U4（Permission Management）の Code Generation 計画。本ドキュメントが Code Generation の
単一の真実源（single source of truth）であり、Part 2（Generation）はこの計画のステップを
順に実行する。ワークスペースルート: `~/Documents/project/git/MasterMeister2`
（`aidlc-state.md` Workspace Root）。アプリケーションコードはワークスペースルート配下
（`backend/`, `frontend/`）にのみ生成し、`aidlc-docs/` にはドキュメント成果物のみ生成する。

---

## ユニットコンテキスト（code-generation.md Step 3）

### 対応ストーリー
MVP-9, ADM-1, ADM-2, ADM-4, ADM-5（`unit-of-work-story-map.md`）:
| ID | タイトル |
|---|---|
| MVP-9 | テーブル/カラム単位のアクセス権限設定（個別ユーザ） |
| ADM-1 | ユーザグループの作成 |
| ADM-2 | グループ単位のアクセス権限設定 |
| ADM-4 | アクセス権限設定のYAMLエクスポート |
| ADM-5 | アクセス権限設定のYAMLインポート |

### 他ユニットへの依存
U1・U2・U3に依存（`unit-of-work-dependency.md`: `permission→common,audit,userregistration,schema`）:
- `common`（`ErrorResponse`/`common.exception`配下の`EntityNotFoundException`/
  `ValidationException`。新規例外`PermissionYamlFormatException`は`permission`パッケージに
  定義し、`config.GlobalExceptionHandler`へのマッピング追記が必要——後述「ブラウンフィールド
  発見事項」参照）
- `audit`（`AuditLogService.record(EventCategory, EventType, Long userId, Long connectionId,
  Result, String targetDescription, String summaryMessage)`。`EventType.GROUP_CHANGED`/
  `PERMISSION_CHANGED`/`PERMISSION_YAML_EXPORTED`/`PERMISSION_YAML_IMPORTED`はいずれもU1の
  Code Generationで既に定義済み——確認済み、新規追加不要）
- `userregistration`（U2）: `User`/`UserRepository`（principal実在チェック、
  `business-rules.md` 2.1 (3)、および`GroupMember`所属ユーザの実在確認・メール表示用。
  `unit-of-work-dependency.md`は`permission→userregistration`のみを明記しているが、後述の
  「ブラウンフィールド発見事項」のとおり`group`パッケージも同依存が必要と判断する）
- `schema`（U3）: `SchemaTableRepository`/`SchemaColumnRepository`（参照整合性チェック、
  `business-rules.md` 2.1 (2)）、および新規`SchemaReimportedEvent`（後述）

### ブラウンフィールド発見事項（Code Generation Planning時に判明、NFR Design/NFR Requirementsからの訂正・補完）

- **`SchemaReimportedEvent`は未実装**であることが判明した。`nfr-design-patterns.md` 2.2・
  `nfr-requirements.md` 1.2は「U3の`SchemaImportService.importSchema`はインポート完了時に
  `SchemaReimportedEvent(connectionId)`を発行する」と記述していたが、実際のU3実装
  （`backend/src/main/java/cherry/mastermeister/schema/SchemaImportService.java`）は
  `ApplicationEventPublisher`を一切参照せずイベントを発行していない。本計画のStep 2で
  `schema`パッケージに`SchemaReimportedEvent`（record: `Long connectionId`）を新規追加し、
  `SchemaImportService.importSchema`にブラウンフィールド修正を加えてトランザクション成功時に
  イベントを発行する（`schema`パッケージの語彙のみで定義し、キャッシュ/権限の概念は含まない、
  `nfr-design-patterns.md` 2.2の方針どおり）。
- **`group`パッケージの`userregistration`依存が`unit-of-work-dependency.md`に未記載**である
  ことが判明した。同ドキュメントは`GroupService`が旧`permission`パッケージ案（NFR Design以前、
  `component-methods.md`の「## permission」見出し配下に`GroupService`が同居していた時点）を
  前提に集約されており、NFR Design Q3で`group`/`permission`へ分割した後の依存関係を反映して
  いない。`GroupMember.userId`は`User`（U2所有）への参照であり、`GroupService.
  addUserToGroup`のユーザ実在確認、および`listGroupMembers`が返す`UserSummary`（メール
  アドレス表示、`frontend-components.md`）の組み立てにはいずれも`userregistration.
  UserRepository`の参照が必要となる。本計画では`group→userregistration`の一方向依存を
  追加する（`group`パッケージが`User`エンティティを書き換えることはない、読み取り専用参照）。
  `docs/PROJECT_STRUCTURE.md`の依存関係表もStep 15のドキュメント生成時に合わせて補記する。
- **`PUT /api/rdbms-connections/{connectionId}/permissions`の単一エンドポイント設計**
  （`business-rules.md` 3節「principal・階層・値を含むリクエストボディで1件ずつ...詳細は
  Code Generationで確定」）を本計画で確定する。単一の`PermissionUpdateRequest`レコード
  （`principal: PrincipalRef, schema: String, table: Optional<String>, column:
  Optional<String>, permission: Optional<Permission>, auxType: Optional<AuxPermissionType>,
  granted: Optional<Boolean>`）を受け、コントローラで`permission`が存在すれば
  `setPermission`、`auxType`+`granted`が存在すれば`setAuxPermission`に委譲する
  （両方または両方欠落は`ValidationException`、`business-rules.md` 2.1の入力検証に準ずる
  コントローラ層の形式チェックとして追加）。

### 提供インタフェース・契約（他ユニットが依存する公開API）
- U5（Master Data Maintenance）・U6（Query Builder）が`permission`パッケージの
  `EffectivePermissionResolver`をサービス層で直接呼び出す（`unit-of-work-dependency.md`:
  `masterdata→...,permission`、`querybuilder→...,permission`）。`EffectivePermissionResolver`
  は`public`で生成し、コントローラは持たない。

### 本ユニットが所有するデータエンティティ（内部DB/JPA）
- `group`パッケージ: `Group`, `GroupMember`
- `permission`パッケージ: `PermissionAssignment`, `AuxPermissionAssignment`

### パッケージ設計判断（`nfr-design-patterns.md`/`logical-components.md`からの継承）
- `Group`/`GroupMember`/`GroupService`/`GroupChangedEvent`は`cherry.mastermeister.group`
  パッケージに配置する（`nfr-design-patterns.md` 3.1）。
- `PermissionAssignment`/`AuxPermissionAssignment`/`PrincipalType`/`Permission`/
  `AuxPermissionType`/`PrincipalRef`/`PermissionAssignmentService`/
  `EffectivePermissionResolver`/`PermissionCacheInvalidationListener`は
  `cherry.mastermeister.permission`パッケージに配置する（`nfr-design-patterns.md` 3.1）。
- **DTO配置**（本計画でのAI決定）: `GroupSummary`/`UserSummary`は`group`パッケージ、
  `PermissionUpdateRequest`/`ImportResult`/YAMLバインド用POJO
  （`PermissionYamlDocument`/`PrincipalYaml`/`PermissionEntryYaml`/
  `AuxPermissionEntryYaml`）は`permission`パッケージに配置する（対応するServiceと同一
  パッケージ、U2の`PendingUserSummary`配置方針を踏襲）。
- **コントローラ分割**（本計画でのAI決定）: `GroupController`（`group`パッケージ、
  `/api/groups`配下）と`PermissionController`（`permission`パッケージ、
  `/api/rdbms-connections/{connectionId}/permissions`配下）に分離する。パッケージ境界と
  コントローラ境界を一致させる（U3の`RdbmsConnectionController`/`SchemaController`分割と
  同じ方針）。

### サービス境界・責務
- `group`: `Group`/`GroupMember`エンティティ、`GroupChangedEvent`（`ApplicationEventPublisher`
  経由で発行）、`GroupService`（グループCRUD・所属管理、`business-rules.md` 1節）。
- `permission`: `PermissionAssignment`/`AuxPermissionAssignment`エンティティ、
  `PermissionAssignmentService`（権限設定CRUD・YAML入出力、`business-rules.md` 2節）、
  `EffectivePermissionResolver`（実効権限解決Facade、`@Cacheable`、
  `component-methods.md`判定ロジック）、`PermissionCacheInvalidationListener`
  （`@TransactionalEventListener(AFTER_COMMIT)`、`nfr-design-patterns.md` 2.1）。
- `schema`（U3、ブラウンフィールド拡張）: `SchemaReimportedEvent`追加、
  `SchemaImportService.importSchema`にイベント発行を追記。
- `config`（U1、ブラウンフィールド拡張）: `GlobalExceptionHandler`に
  `PermissionYamlFormatException`ハンドラを追記。
- `security`（U1、ブラウンフィールド拡張）: `SecurityConfig`に`/api/groups/**`・
  `/api/rdbms-connections/{connectionId}/permissions/**`の`hasRole("ADMIN")`を追記
  （`/api/rdbms-connections/**`は既にU3で全体を管理者専用にしているため、
  `/api/rdbms-connections/{connectionId}/permissions/**`は実質的に追加不要——後述Step 5で
  確認のみ行う）。
- フロントエンド: `features/group/`（グループ一覧・詳細・所属管理）、
  `features/permission/`（権限設定ツリー・フォーム・YAML入出力パネル）。U1の`apiClient`/
  `AppRouter`/`AppLayout`/`DataTable`/`ConfirmDialog`/`ToastNotification`/`ProtectedRoute`、
  U3の`rdbmsConnectionApi`/`schemaApi`をブラウンフィールド拡張・再利用する。

### テスト可能な性質（PBT-01、`business-logic-model.md`で識別済み）
P1〜P11（`business-logic-model.md`「テスト可能な性質」表）。Step 3で対応する`@Property`
テストを生成する。

---

## ステップ一覧

**実行順序の変更（ユーザ判断、2026-07-11）**: U3ではStep 2（ビジネスロジック生成）を
Step 8（リポジトリレイヤ生成）に先行させたため、Step 2〜7の間`./gradlew compileJava`が
Repository未定義エラーで失敗し続ける状態を許容していた。U4ではこれを避けるため、
実行順序を以下のとおり入れ替える（Step番号・item番号は計画書の元の採番を維持し、
実施順序のみ変更する）:
1. item 2-2（`Group`/`GroupMember`エンティティ）
2. item 8-1（`GroupRepository`/`GroupMemberRepository`）— Step 8内で先行実施する分を「暫定実装」と呼ぶ
3. item 2-6（`PermissionAssignment`/`AuxPermissionAssignment`/enum/`PrincipalRef`）
4. item 8-2 暫定実装（`PermissionAssignmentRepository`/`AuxPermissionAssignmentRepository`を
   `JpaRepository<Entity, Long>`のみの最小形で生成。カスタムクエリメソッドはStep 2の
   残り項目〈2-9, 2-11等〉で実際に必要になった時点で追記する）
5. item 2-1, 2-3, 2-4, 2-5, 2-7, 2-8, 2-9, 2-10, 2-11, 2-12（元の番号順）— このうち2-5/2-9/2-11で
   リポジトリに追加メソッドが必要になった場合は都度8-1/8-2のファイルに追記する
6. Step 9（リポジトリ単体テスト）・Step 10（リポジトリレイヤサマリ）は、Step 2の残り項目が
   完了しリポジトリのメソッド一式が確定してから実施する（今回は未実施のまま据え置き）。

### Step 1: プロジェクト構造セットアップ
- [x] 1-1. `backend/build.gradle.kts`（既存、ブラウンフィールド修正）の`dependencies`ブロックに
      `implementation("org.springframework.boot:spring-boot-starter-cache")`、
      `implementation("com.github.ben-manes.caffeine:caffeine")`、
      `implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")`を追記する。
      いずれもSpring Boot BOM管理下のため`dependencyManagement`への明示バージョン指定は不要
      （`tech-stack-decisions.md`依存関係追加、CLAUDE.md「Gradleバージョン管理」規約）。

### Step 2: ビジネスロジック生成
- [x] 2-1. `backend/src/main/java/cherry/mastermeister/schema/SchemaReimportedEvent.java`
      （新規、record: `Long connectionId`）を生成し、`SchemaImportService.importSchema`
      （既存、ブラウンフィールド修正）にトランザクション成功時（`SchemaImportResult.success
      == true`のreturn直前）の`ApplicationEventPublisher.publishEvent(new
      SchemaReimportedEvent(connectionId))`呼び出しを追記する（「ブラウンフィールド発見事項」
      参照、`nfr-design-patterns.md` 2.2）。
      実装メモ: `SchemaImportService`のコンストラクタに`ApplicationEventPublisher`を追加
      注入し、監査ログ記録直後・`return`直前で発行するよう変更した。既存の
      `SchemaImportServiceTest`内の2箇所の`new SchemaImportService(...)`呼び出しに
      `mock(ApplicationEventPublisher.class)`引数を追加して追随させた（イベント発行自体の
      検証はテスト対象外のまま）。`./gradlew compileJava compileTestJava`成功、
      `SchemaImportServiceTest`全件成功を確認。
- [x] 2-2. `backend/src/main/java/cherry/mastermeister/group/` に`Group`（JPAエンティティ:
      `id`, `name`〈unique, not null〉, `createdAt`。`domain-entities.md`）、`GroupMember`
      （JPAエンティティ: `id`, `groupId`〈not null〉, `userId`〈not null〉, `joinedAt`。一意
      制約`(groupId, userId)`＋`@Table(indexes = {...})`で`(userId, groupId)`への追加
      インデックス、`nfr-design-patterns.md` 4.1）を生成する。既存`RdbmsConnection`等と同型の
      スタイル（protected引数なしコンストラクタ＋全項目コンストラクタ＋`update`/`rename`
      ミューテータ＋getterのみ）で実装する。
      **実装メモ**: `Group`の`@Table(name = ...)`は`group`がSQL予約語のため
      `app_group`とした（`User`エンティティの`app_user`と同じ命名方針）。`GroupMember`は
      `Group`のような更新対象フィールドを持たないため`rename`/`update`ミューテータは実装せず
      getterのみとした（追加・削除はレコード自体の作成/削除で表現、`GroupService`側の責務）。
      `compileJava`成功を確認済み。
- [x] 2-3. `backend/src/main/java/cherry/mastermeister/group/GroupChangedEvent.java`
      （新規、record: `Long groupId`）を生成する（`nfr-requirements.md` 1.2、`group`
      パッケージの語彙のみで定義）。
      実装メモ: `SchemaReimportedEvent`と同型の単純recordとして実装。発行元
      （`GroupService`、item 2-5）はまだ未実装のためこの時点では未使用。
      `./gradlew compileJava`成功を確認。
- [x] 2-4. `backend/src/main/java/cherry/mastermeister/group/` に`GroupSummary`（record:
      `Long id, String name, Instant createdAt`）、`UserSummary`（record: `Long id, String
      email`）を生成する（`component-methods.md`シグネチャ、「ブラウンフィールド発見事項」
      のDTO配置方針）。
      実装メモ: 既存`UserSummary`（他パッケージ）は無く、命名衝突なし。`ConnectionSummary`
      と同型の単純recordとして実装。`./gradlew compileJava`成功を確認。
- [x] 2-5. `backend/src/main/java/cherry/mastermeister/group/GroupService.java`
      （`@Service`、書き込み系メソッド全体に`@Transactional`）: `Long createGroup(String
      name)`（`business-rules.md` 1.1一意性チェック）、`void renameGroup(Long groupId,
      String newName)`（1.1）、`void deleteGroup(Long groupId)`（1.3カスケード削除:
      `GroupMember`全行＋`principalType=GROUP AND principalId=groupId`の
      `PermissionAssignment`/`AuxPermissionAssignment`全行を同一トランザクションで削除。
      `PermissionAssignment`/`AuxPermissionAssignment`のリポジトリは`permission`パッケージ
      所属のため、`GroupService`が両リポジトリを直接参照する形とする——`permission`
      パッケージのエンティティを`group`パッケージのServiceから参照する一方向のみで、
      `permission→group`の依存方向〈`nfr-design-patterns.md` 3.1〉とは逆方向のため矛盾しない
      ことを確認済み）、`void addUserToGroup(Long groupId, Long userId)`（1.2重複チェック＋
      `UserRepository`によるユーザ実在確認、「ブラウンフィールド発見事項」）、`void
      removeUserFromGroup(Long groupId, Long userId)`（1.2、対象`GroupMember`不在は例外）、
      `List<GroupSummary> listGroups()`、`List<UserSummary> listGroupMembers(Long groupId)`
      を実装する。書き込み系5メソッドはいずれも成功・失敗を問わず
      `AuditLogService.record(ADMIN_OPERATION, GROUP_CHANGED, adminUserId, ...)`を呼び出し
      （1.4）、成功時のみ`ApplicationEventPublisher.publishEvent(new
      GroupChangedEvent(groupId))`を発行する（`deleteGroup`後の`groupId`は削除済みだが
      イベントのpayloadとしては引き続き有効な識別子として使う）。メソッドシグネチャに
      `adminUserId`が明示されていないため、U3の`RdbmsConnectionService`と同型で
      `createGroup(Long adminUserId, String name)`のように補完する。
      実装メモ: 名前重複・重複所属チェックの違反は`common/exception/ValidationException`
      （既存）を使用（`AuthenticationService.login`と同型でバリデーション/実在チェック→
      失敗時は`Result.FAILURE`で`record`後に例外送出→成功時は`Result.SUCCESS`で`record`後に
      イベント発行、という順序を各メソッドで踏襲）。`Group`/`GroupMember`不在は
      `EntityNotFoundException`（既存）。`deleteGroup`のカスケード削除用に
      `PermissionAssignmentRepository`/`AuxPermissionAssignmentRepository`（item 8-2）へ
      `deleteByPrincipalTypeAndPrincipalId(PrincipalType, Long)`を追加した（8-2時点では
      未定だったメソッド、Step 2先行実装との整合を優先する方針どおり）。
      `./gradlew compileJava compileTestJava`成功を確認（単体テストはitem 3で作成）。
- [x] 2-6. `backend/src/main/java/cherry/mastermeister/permission/` に`PermissionAssignment`
      （JPAエンティティ: `id`, `principalType`, `principalId`, `connectionId`, `schemaName`,
      `tableName`〈nullable〉, `columnName`〈nullable〉, `permission`, `updatedAt`。一意制約
      `(principalType, principalId, connectionId, schemaName, tableName, columnName)`）、
      `AuxPermissionAssignment`（JPAエンティティ: `id`, `principalType`, `principalId`,
      `connectionId`, `schemaName`, `tableName`〈nullable〉, `auxType`, `granted`,
      `updatedAt`。一意制約`(principalType, principalId, connectionId, schemaName,
      tableName, auxType)`）、`PrincipalType`（enum: `USER`, `GROUP`）、`Permission`
      （enum: `NONE`, `READ`, `UPDATE`。強さ順序比較のため`Comparable`実装または
      `ordinal()`比較用ヘルパーを追加）、`AuxPermissionType`（enum: `CREATE`, `DELETE`）、
      `PrincipalRef`（record: `PrincipalType principalType, Long principalId`）を生成する。
      いずれも明示的な`@Table(indexes = {...})`は追加しない（一意制約のみで賄う、
      `nfr-design-patterns.md` 4.1）。
      実装メモ: `Permission`は宣言順（NONE, READ, UPDATE）が強さ順序と一致するため、
      `Comparable`実装（enumが自動的に持つ`compareTo`）に加えて`max(Permission,
      Permission)`静的ヘルパーを追加し、グループ合成時の「最も緩い権限」判定で
      使用する想定とした。両エンティティとも既存パターン（`RdbmsConnection`/
      `SchemaTable`）に倣い`update(...)`メソッドで更新可能フィールド（`permission`/
      `granted`と`updatedAt`）のみ変更可能にした。`./gradlew compileJava`成功を確認。
- [x] 2-7. `backend/src/main/java/cherry/mastermeister/permission/` に`ImportResult`
      （record: `boolean success, String message`）、`PermissionUpdateRequest`（record:
      `PrincipalRef principal, String schema, Optional<String> table, Optional<String>
      column, Optional<Permission> permission, Optional<AuxPermissionType> auxType,
      Optional<Boolean> granted`、「ブラウンフィールド発見事項」）を生成する。
      実装メモ: プラン記載シグネチャのまま素朴な`record`として生成。`ImportResult`は
      `component-methods.md`/`business-logic-model.md`双方に既出（フロントエンドへの
      成功可否・違反概要返却用）、`PermissionUpdateRequest`は既存設計書に未出（プラン
      注記どおりブラウンフィールド発見事項）だがプラン記載フィールド構成をそのまま採用。
      `./gradlew compileJava`成功を確認。
- [x] 2-8. `backend/src/main/java/cherry/mastermeister/permission/` にYAMLバインド用POJO
      `PermissionYamlDocument`（`List<PrincipalYaml> principals`）、`PrincipalYaml`（`String
      type, String email, String name, List<PermissionEntryYaml> permissions,
      List<AuxPermissionEntryYaml> auxPermissions`）、`PermissionEntryYaml`（`String schema,
      String table, String column, String permission`）、`AuxPermissionEntryYaml`（`String
      schema, String table, String type, Boolean granted`）を生成する（`business-rules.md`
      2.3のYAML構造、Jackson YAMLでバインドするための素朴なPOJO、フィールドは全て文字列/
      boolean型でバインド後にenum変換・必須チェックを行う——`nfr-design-patterns.md` 5.1
      「バインド後チェック」方針）。
      実装メモ: `nfr-design-patterns.md` 5.1で明示されたとおりJakarta Bean Validationは
      導入せず、Jacksonバインド用に全フィールドgetter/setter付きの可変POJO（`record`では
      なく通常クラス）として生成した。フィールドは全て`String`/`Boolean`のプラン記載通りで、
      enum変換・必須チェック・参照整合性・重複検証はitem 2-9の`importPermissionsFromYaml`
      内で命令的に行う想定（本itemではバインド用の型定義のみ）。`./gradlew compileJava`
      成功を確認。
- [x] 2-9. `backend/src/main/java/cherry/mastermeister/permission/
      PermissionAssignmentService.java`（`@Service`、書き込み系メソッド全体に
      `@Transactional`＋自メソッドに`@CacheEvict(cacheNames = {6キャッシュ名}, allEntries =
      true)`、`nfr-design-patterns.md` 1.2/2.1）: `void setPermission(Long adminUserId,
      PrincipalRef principal, Long connectionId, String schema, Optional<String> table,
      Optional<String> column, Permission permission)`（`business-rules.md` 2.1検証1〜3:
      階層整合性・参照整合性〈`SchemaTableRepository`/`SchemaColumnRepository`参照〉・
      principal実在チェック〈`UserRepository`/`GroupRepository`参照〉）、`void
      setAuxPermission(Long adminUserId, PrincipalRef principal, Long connectionId, String
      schema, Optional<String> table, AuxPermissionType auxType, boolean granted)`
      （同様の検証、`column`パラメータなし）、`byte[] exportPermissionsAsYaml(Long
      connectionId)`（2.3のYAML構造組み立て、principalは`User.email`/`Group.name`で解決、
      Jackson `YAMLMapper`でシリアライズ）、`ImportResult importPermissionsFromYaml(Long
      adminUserId, Long connectionId, byte[] yamlContent)`（2.4検証項目1〜5を順に命令的に
      チェック、違反時は`PermissionYamlFormatException`、全通過後は対象接続の既存
      `PermissionAssignment`/`AuxPermissionAssignment`を全削除し再構築する全置換方式）を
      実装する。全ての書き込みメソッドは成功・失敗を問わず`AuditLogService.record`を呼び出す
      （2.2, 2.4）。
      実装メモ: item 2-10（`PermissionYamlFormatException`）はコンパイル依存のため本item
      より先に生成した（既存の「必要な依存を先行実施」方針を踏襲）。
      - `exportPermissionsAsYaml`は`component-methods.md`/プラン記載シグネチャに`adminUserId`
        引数がないが、`business-rules.md` 2.3が成功時の`AuditLogService.record`呼び出しに
        `adminUserId`を要求するため、`setPermission`等の他メソッドと同様に`adminUserId`を
        第一引数として追加した（ブラウンフィールド発見事項、`GroupService`が既に同種の理由で
        `component-methods.md`に`adminUserId`を追加している前例を踏襲）。
      - `importPermissionsFromYaml`は`SchemaImportService.importSchema`（U3）と同型の
        「メソッド内で例外をcatchし`Result`型に変換して返す」パターンを採用した。
        `business-rules.md` 2.4は「違反があれば`PermissionYamlFormatException`とし一切反映
        しない」と規定する一方、`business-logic-model.md`フロー4手順5は「`ImportResult`
        （成功可否、失敗時は違反概要）をフロントエンドへ返す」としており、両者は
        「`PermissionYamlFormatException`はメソッド内部の検証失敗シグナルとして使い、
        呼び出し元へは`ImportResult(false, message)`として返す」と解釈することで整合する
        （`SchemaImportResult`の前例と同じ設計）。検証失敗時は
        `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`でロール
        バックを強制した上で`ImportResult(false, e.getMessage())`を返す。
      - スキーマレベル（`table`省略）の参照整合性チェック用に`SchemaTableRepository`へ
        `existsByConnectionIdAndSchemaName(Long, String)`を追加した（`SchemaTable`に
        スキーマ単体を表すエンティティがないため、当該スキーマのテーブルが1件以上存在する
        ことをもって「スキーマが実在する」とみなす）。
      - YAML直列化には`com.fasterxml.jackson.dataformat.yaml.YAMLMapper`
        （Jackson 2系、`jackson-dataformat-yaml`依存が引き込む）をサービス内の
        `private final`フィールドとして直接インスタンス化した（アプリ全体のREST用
        `ObjectMapper`はSpring Boot 4.1のJackson 3系`tools.jackson.databind.ObjectMapper`
        であり別系統のため、Beanとして共有せず本サービス専用とした）。
      - principal実在チェック（2.1検証3）は`UserRepository.existsById`/
        `GroupRepository.existsById`、YAMLインポート時のprincipal解決は
        `UserRepository.findByEmail`/`GroupRepository.findByName`を用いた。
      `./gradlew compileJava compileTestJava`成功を確認（単体テストはStep 3で作成）。
- [x] 2-10. `backend/src/main/java/cherry/mastermeister/permission/
      PermissionYamlFormatException.java`（新規、`RuntimeException`拡張、
      `EntityNotFoundException`等既存パターンと同型）を生成する。
      実装メモ: item 2-9のコンパイル依存のため先行生成した。`EntityNotFoundException`/
      `ValidationException`と同型（`(String message)`・`(String message, Throwable cause)`
      の2コンストラクタ）で生成。`./gradlew compileJava`成功を確認。
- [x] 2-11. `backend/src/main/java/cherry/mastermeister/permission/
      EffectivePermissionResolver.java`（`@Component`、内部Facade、コントローラなし）:
      `Permission resolveEffectiveTablePermission(Long userId, Long connectionId, String
      schema, String table)`、`Map<String, Permission>
      resolveEffectiveColumnPermissions(Long userId, Long connectionId, String schema,
      String table)`、`boolean canCreate(Long userId, Long connectionId, String schema,
      String table)`、`boolean canDelete(...)`、`List<String>
      listAccessibleSchemas(Long userId, Long connectionId)`、`List<String>
      listAccessibleTables(Long userId, Long connectionId, String schema)`の6メソッドに
      それぞれ専用`@Cacheable(cacheNames = "effectivePermissions.*")`を付与する
      （`nfr-design-patterns.md` 1.1）。`component-methods.md`判定ロジック要旨1〜6
      （階層継承→グループ合成〈最も緩い権限〉→個別上書き→`canCreate`/`canDelete`の主キー
      条件）を実装する。`GroupMember`（`group`パッケージ、`permission→group`一方向参照）・
      `PermissionAssignment`/`AuxPermissionAssignment`・`SchemaTableRepository`/
      `SchemaColumnRepository`（U3、主キー構成参照）を利用する。
      実装メモ:
      - 階層継承・グループ合成・個別上書き（business-rules.md 2.5 手順1〜4）の相互作用は
        文面だけでは一意に定まらない（例: ユーザ個別設定がスキーマ階層にのみ存在し、テーブル/
        カラム階層には存在しない場合に、その個別設定がテーブル/カラム階層へ継承されるべきか、
        それともテーブル/カラム階層は都度グループ合成結果に差し戻るべきか、記述からは両読み
        できる）。以下の解釈で実装し、判断根拠を残す:
        `findMostSpecificPermission(principal, schema, table, column)`は特定のprincipal
        （ユーザ1名またはグループ1件）が持つ明示設定を、指定階層から上位（カラム→テーブル→
        スキーマ）へ順に探索し、最初に見つかった明示設定の値を返す（=単一principal内での
        手順1）。ユーザ自身にこの探索で明示設定が1件でも見つかれば、その値をそのまま採用する
        （テーブル階層の個別設定はカラム階層にも継承される＝手順1をユーザ個別設定内で完結
        させる）。ユーザに明示設定が一切ない場合のみ、所属する各グループについて同じ探索を
        行い（手順3前段）、グループごとの解決値を`Permission.max`で合成する（手順3後段）。
        これにより手順4「個別設定が存在する階層は上書きし、存在しない階層は合成結果を継続
        適用する（部分上書きが起こり得る）」は、"ユーザの明示設定チェーンのどこかに設定が
        見つかった時点でそれを採用し、見つからない場合のみグループ合成結果にフォールバック
        する"という単一ロジックとして実装した（グループ合成自体はユーザの上書きの影響を
        受けない独立した計算）。補助権限（C/D）もスキーマ→テーブルの2階層で同型ロジックを
        適用し、グループ間合成はOR（`resolveAuxPermission`/`findMostSpecificAuxPermission`）。
      - `canCreate`/`canDelete`は`SchemaColumnRepository.findByTableIdAndStaleFalse`から
        `primaryKeySequence != null`のカラムを抽出し（`primaryKeySequence`昇順ソート、AND
        判定の順序自体は結果に影響しないが可読性のため）、主キーなしテーブルは`canCreate`が
        補助権限Cのみで許可（`true`即返却）、`canDelete`は常に`false`という業務規則5・6の
        例外規定をそのまま分岐で実装した。
      - `resolveEffectiveColumnPermissions`/`canCreate`/`canDelete`はテーブル実在確認に
        `SchemaTableRepository.findByConnectionIdAndSchemaNameAndTableName`（stale問わず）を
        用いた。item 2-9の`referenceExists`と同じ理由（business-rules.md 2.1がstale=trueの
        行も実在対象として許容する）による判断の踏襲。`listAccessibleSchemas`/
        `listAccessibleTables`は`component-methods.md`の定義通り`stale=false`のテーブルのみを
        列挙対象とした（アクセス可能スキーマ/テーブルの一覧はUI表示用であり、消失した
        テーブルを含めるべきではないため）。
      - `listAccessibleSchemas`/`listAccessibleTables`は内部で`resolveMainPermission`
        （非`@Cacheable`private メソッド）を直接呼ぶ設計とした。`resolveEffectiveTablePermission`
        （`@Cacheable`付きpublicメソッド）を`this.`経由で自己呼び出しするとSpring AOPプロキシを
        経由せずキャッシュが効かない既知の制約があるため、意図的に内部ロジックを共有private
        メソッドに切り出し、自己呼び出しによるキャッシュ迂回を避けた（`effectivePermissions.table`
        キャッシュは外部から`resolveEffectiveTablePermission`が直接呼ばれた場合のみ利用される）。
      - `@Transactional`は付与しなかった（`SchemaQueryService`と同じ読み取り専用Facadeの
        既存パターンを踏襲、書き込みを伴わないため）。
      `./gradlew compileJava compileTestJava`成功を確認（単体テストはStep 3で作成）。
- [x] 2-12. `backend/src/main/java/cherry/mastermeister/permission/
      PermissionCacheInvalidationListener.java`（`@Component`）: `SchemaReimportedEvent`
      （U3 `schema`）・`GroupChangedEvent`（`group`）を`@TransactionalEventListener(phase =
      AFTER_COMMIT)`で購読し、`EffectivePermissionResolver`の6キャッシュを
      `@CacheEvict(cacheNames = {...}, allEntries = true)`を付与した空実装メソッドで一括
      削除する（`nfr-design-patterns.md` 2.1、Spring Cacheの`@CacheEvict`はメソッド呼び出し
      自体が無効化トリガーのため、リスナーメソッド本体は空でよい）。
      実装メモ: `onGroupChanged(GroupChangedEvent)`・`onSchemaReimported(SchemaReimportedEvent)`
      の2メソッドを生成し、いずれも本体は空、`@TransactionalEventListener(phase =
      TransactionPhase.AFTER_COMMIT)`＋`@CacheEvict(cacheNames = {6キャッシュ名},
      allEntries = true)`を付与した（`item 2-9`/`item 2-11`と同一の6キャッシュ名リテラル配列を
      アノテーション属性としてそのまま複製、`@CacheEvict`はコンパイル時定数制約のため定数化
      不可という既存の制約を踏襲）。イベントの発行側（`GroupService`での`GroupChangedEvent`
      publish、`SchemaImportService`での`SchemaReimportedEvent`publish）は既存実装済みで
      本item側の変更は不要だった。`./gradlew compileJava compileTestJava`成功を確認
      （単体テストはStep 3で作成）。これでStep 2〈ビジネスロジック生成〉全12項目が完了。

### Step 3: ビジネスロジック単体テスト（PBT-01〜PBT-08, PBT-10）
`business-logic-model.md`のP1〜P11に対応する`@Property`テストをjqwikで生成する。
- [ ] 3-1. **P1**（`deleteGroup`後の関連行ゼロInvariant）、**P2**（`addUserToGroup`の重複拒否
      Idempotence）: `GroupServiceTest`に`@Property`テストを生成する。
- [ ] 3-2. **P3**（`setPermission`/`setAuxPermission`のIdempotence）:
      `PermissionAssignmentServiceTest`に`@Property`テストを生成する。
- [ ] 3-3. **P4**（export→importのRound-trip）、**P5**（重複検出Invariant）、**P6**
      （全置換Invariant）: `PermissionAssignmentServiceTest`に`@Property`テストを追加生成
      する（P4は組み込みH2または`@DataJpaTest`での実データ往復、P5/P6はMockito/フェイク
      リポジトリでの検証を状況に応じて選択する）。
- [ ] 3-4. **P7**（グループ合成のCommutativity）、**P8**（階層継承・個別上書きInvariant）、
      **P9**（`canCreate`/`canDelete`の主キーなしテーブルInvariant）:
      `EffectivePermissionResolverTest`に`@Property`テストを生成する。
- [ ] 3-5. **P10**（書き込み直後の強整合性Invariant）: `PermissionCacheInvalidationListener`
      を含む統合的な検証（`@SpringBootTest`または`@DataJpaTest`＋実キャッシュBeanでの
      検証）を`EffectivePermissionResolverTest`または専用テストクラスに生成する。
- [ ] 3-6. **P11**（U3スキーマ再取り込みとの一貫性Invariant）: `SchemaReimportedEvent`発行
      〜`PermissionCacheInvalidationListener`〜`EffectivePermissionResolver`再判定の連携を
      検証する`@Property`テストを生成する。

### Step 4: ビジネスロジックサマリ
- [ ] 4-1. `aidlc-docs/construction/u4-permission-management/code/business-logic-summary.md`
      を生成し、Step 2・Step 3で生成したクラス一覧とP1〜P11の対応関係を表形式で記載する
      （U1/U2/U3の`business-logic-summary.md`と同一構成）。

### Step 5: APIレイヤ生成
- [ ] 5-1. `backend/src/main/java/cherry/mastermeister/group/GroupController.java`
      （`@RestController @RequestMapping("/api/groups")`）: `POST ""`（`createGroup`→201）,
      `PUT "/{id}"`（`renameGroup`→204）, `DELETE "/{id}"`（`deleteGroup`→204）, `GET ""`
      （`listGroups`→`List<GroupSummary>`）, `GET "/{id}/members"`（`listGroupMembers`→
      `List<UserSummary>`）, `POST "/{id}/members"`（`addUserToGroup`→201）, `DELETE
      "/{id}/members/{userId}"`（`removeUserFromGroup`→204）を生成する
      （`business-rules.md` 3節のパスパターン）。`adminUserId`は`Authentication#
      getPrincipal()`キャスト取得（U2/U3のコントローラと同一パターン）。
- [ ] 5-2. `backend/src/main/java/cherry/mastermeister/permission/
      PermissionController.java`（`@RestController @RequestMapping("/api/rdbms-connections/
      {connectionId}/permissions")`）: `PUT ""`（`PermissionUpdateRequest`を受け、`permission`
      有無で`setPermission`/`setAuxPermission`へ分岐、204。「ブラウンフィールド発見事項」）,
      `GET "/export"`（`exportPermissionsAsYaml`→`byte[]`、`Content-Type: application/
      x-yaml`、`Content-Disposition: attachment; filename=...`）, `POST "/import"`
      （`multipart/form-data`のYAMLファイルを受け`importPermissionsFromYaml`→
      `ImportResult`）を生成する（`business-rules.md` 3節）。
- [ ] 5-3. `backend/src/main/java/cherry/mastermeister/config/GlobalExceptionHandler.java`
      （既存、ブラウンフィールド修正）に`@ExceptionHandler(PermissionYamlFormatException
      .class)`（400 `PERMISSION_YAML_FORMAT_ERROR`）を追記する（「ブラウンフィールド発見
      事項」）。
- [ ] 5-4. `backend/src/main/java/cherry/mastermeister/security/SecurityConfig.java`
      （既存、ブラウンフィールド修正）を確認する。`/api/groups/**`の`hasRole("ADMIN")`
      エントリを`anyRequest().authenticated()`より前に追記する。
      `/api/rdbms-connections/{connectionId}/permissions/**`は既存の
      `/api/rdbms-connections/**`エントリ（U3）が前方一致で包含するため追加不要であることを
      確認するのみとする（「サービス境界・責務」参照）。

### Step 6: APIレイヤ単体テスト
- [ ] 6-1. `GroupControllerTest`（`@WebMvcTest` + `spring-security-test`）: 7エンドポイント
      それぞれについて管理者成功系・非管理者403・未認証401をexample-basedテストで検証する
      （U2/U3のControllerTestパターンを踏襲）。
- [ ] 6-2. `PermissionControllerTest`（`@WebMvcTest` + `spring-security-test`）: 3エンドポイント
      （権限更新・エクスポート・インポート）それぞれについて管理者成功系・非管理者403・
      未認証401、およびインポート形式不正時の400をexample-basedテストで検証する。

### Step 7: APIレイヤサマリ
- [ ] 7-1. `aidlc-docs/construction/u4-permission-management/code/api-layer-summary.md`を
      生成し、エンドポイント一覧（パス・メソッド・認可要件・リクエスト/レスポンス形状）と
      エラーレスポンス表（`PERMISSION_YAML_FORMAT_ERROR`含む）を記載する。

### Step 8: リポジトリレイヤ生成
- [x] 8-1. （item 2-2の直後に暫定実装として先行実施、`u4-permission-management-code-generation-plan.md`実行順序変更の注記参照）`backend/src/main/java/cherry/mastermeister/group/GroupRepository.java`
      （`JpaRepository<Group, Long>`。`Optional<Group> findByName(String)`）、
      `GroupMemberRepository.java`（`JpaRepository<GroupMember, Long>`。
      `Optional<GroupMember> findByGroupIdAndUserId(Long, Long)`,
      `List<GroupMember> findByGroupId(Long)`, `List<GroupMember> findByUserId(Long)`
      〈`EffectivePermissionResolver`のグループ合成、`(userId, groupId)`インデックスを利用〉,
      `void deleteByGroupId(Long)`〈カスケード削除〉）を生成する。
- [x] 8-2. `backend/src/main/java/cherry/mastermeister/permission/
      PermissionAssignmentRepository.java`（`JpaRepository<PermissionAssignment, Long>`。
      `principalId`起点・`connectionId`起点の検索メソッド一式）、
      `AuxPermissionAssignmentRepository.java`（同様）を生成する。実際に必要なメソッド
      シグネチャはStep 2実装時の呼び出し箇所（`EffectivePermissionResolver`の解決クエリ、
      `PermissionAssignmentService`の全置換・カスケード削除）に合わせて確定する
      （U3 Step 8と同様、Step 2先行実装との整合を優先）。
      実装メモ: item 2-6の直後に暫定・最小実装として先行実施（item 8-1と同様の
      実行順序変更）。両リポジトリとも
      `find...ByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableName...`
      （`setPermission`/`setAuxPermission`の既存行検索用、単数）、
      `findByPrincipalTypeAndPrincipalIdAndConnectionId`（`EffectivePermissionResolver`の
      階層解決用、複数）、`findByConnectionId`（YAMLエクスポート用）、
      `deleteByConnectionId`（YAMLインポート時の全置換用）の4メソッドを暫定実装した。
      item 2-9（`PermissionAssignmentService`）・2-11
      （`EffectivePermissionResolver`）の実装時に不足があれば追加する。
      `./gradlew compileJava`成功を確認。
      追記（item 2-5実装時）: `deleteByPrincipalTypeAndPrincipalId(PrincipalType, Long)`を
      両リポジトリに追加した（`GroupService.deleteGroup`のカスケード削除用）。

### Step 9: リポジトリレイヤ単体テスト
- [ ] 9-1. `GroupRepositoryTest`/`GroupMemberRepositoryTest`/
      `PermissionAssignmentRepositoryTest`/`AuxPermissionAssignmentRepositoryTest`
      （いずれも`@DataJpaTest`、組み込みH2）: 基本CRUD・カスタムクエリメソッド・一意制約
      違反時の例外発生をexample-basedテストで検証する。

### Step 10: リポジトリレイヤサマリ
- [ ] 10-1. `aidlc-docs/construction/u4-permission-management/code/
      repository-layer-summary.md`を生成し、4リポジトリのクエリメソッド一覧とインデックス
      設計（`GroupMember`の追加インデックス含む、`nfr-design-patterns.md` 4.1）を記載する。

### Step 11: フロントエンドコンポーネント生成
- [ ] 11-1. `frontend/src/features/group/` に`GroupListPage.tsx`、`GroupTable.tsx`、
      `GroupDetailPage.tsx`、`GroupMemberTable.tsx`、`api.ts`、`types.ts`を生成する
      （`frontend-components.md`のコンポーネント階層・data-testid規約に準拠）。
- [ ] 11-2. `frontend/src/features/permission/` に`PermissionAssignmentPage.tsx`、
      `ConnectionSelector.tsx`、`PrincipalSelector.tsx`、`PermissionTree.tsx`、
      `PermissionForm.tsx`、`PermissionYamlPanel.tsx`、`api.ts`（YAMLエクスポートは
      `blob`ダウンロード、インポートは`FormData`アップロード。トークン取得は`apiClient`の
      `useAuthStore`を直接参照する形でJSON専用の`apiFetch`をバイパスする——「ブラウン
      フィールド発見事項」相当のAI決定、Step 2完了後に確定）、`types.ts`を生成する
      （`frontend-components.md`）。`features/permission/`は`features/group/`の
      `groupApi.listGroups()`のみを参照する（一方向依存）。
- [ ] 11-3. `frontend/src/routes/AppRouter.tsx`（既存、ブラウンフィールド修正）:
      `/admin/groups`, `/admin/groups/:id`, `/admin/permissions`を`ProtectedRoute
      requiredRole="ADMIN"`配下に追加する。
- [ ] 11-4. `frontend/src/components/AppLayout.tsx`（既存、ブラウンフィールド修正）に
      「グループ管理」「権限設定」へのナビゲーションリンクを追加する（管理者ロールのみ
      表示）。

### Step 12: フロントエンドコンポーネント単体テスト
- [ ] 12-1. Vitest + React Testing Libraryで`features/group/`（一覧・詳細・所属管理・
      `groupApi`）のexample-basedテストを生成する。
- [ ] 12-2. Vitest + React Testing Libraryで`features/permission/`（ツリー選択・フォーム
      保存・YAMLエクスポート/インポート・`permissionApi`）のexample-basedテストと、
      `AppRouter`/`AppLayout`への追加ルート・ナビゲーションのテストを生成する。

### Step 13: フロントエンドコンポーネントサマリ
- [ ] 13-1. `aidlc-docs/construction/u4-permission-management/code/frontend-summary.md`を
      生成し、`features/group/`・`features/permission/`のコンポーネント一覧・data-testid
      一覧・追加ルーティングを記載する。

### Step 14: データベースマイグレーションスクリプト
- [ ] 14-1. **該当なし（N/A）**: U1/U2/U3と同様、内部DB(H2)のスキーマ管理はJPAの自動DDL
      生成に委ね、Flyway/Liquibase等は導入しない（U1 NFR Design Question 5 = Aを踏襲）。

### Step 15: ドキュメント生成
- [ ] 15-1. Step 4/7/10/13のサマリに加え、`aidlc-docs/construction/
      u4-permission-management/code/testing-summary.md`（P1〜P11とテストクラスの対応表、
      example-basedテスト一覧）を生成する。`docs/PROJECT_STRUCTURE.md`の依存関係表に
      `group→userregistration`の一方向依存を補記する（「ブラウンフィールド発見事項」）。

### Step 16: デプロイ成果物生成
- [ ] 16-1. `backend/src/main/resources/application.yml`（既存、ブラウンフィールド修正）に
      `spring.cache.type: caffeine`、`spring.cache.caffeine.spec`（6キャッシュ共通の
      `maximumSize`/`expireAfterWrite`既定値、`nfr-design-patterns.md` 1.1）を追記する。
      本ユニット固有の`mm.app.*`設定キーは既存の`spring.servlet.multipart.max-file-size`
      既定値をそのまま使うため追加なし（`nfr-requirements.md` 2.2）。

---

## 完了基準
- 上記全ステップの生成物がワークスペースルート配下に作成され、対応する単体テストが
  生成されていること（実行・グリーン確認はBuild and Testステージで行う）。
- P1〜P11全ての性質にjqwik `@Property`テストが対応していること（PBT-02〜PBT-08準拠）。
- `aidlc-docs/construction/u4-permission-management/code/`配下に5つのサマリドキュメント
  （business-logic-summary.md, api-layer-summary.md, repository-layer-summary.md,
  frontend-summary.md, testing-summary.md）が生成されていること。