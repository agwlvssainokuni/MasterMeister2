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
- [x] 3-1. **P1**（`deleteGroup`後の関連行ゼロInvariant）、**P2**（`addUserToGroup`の重複拒否
      Idempotence）: `GroupServiceTest`に`@Property`テストを生成する。
      実装メモ: `SchemaImportServiceTest`の`FakeRepositories`パターン（Mockito
      `mock()`＋`thenAnswer`/`doAnswer`でリポジトリをインメモリリストにバックする）を踏襲した。
      P1（`deleteGroupRemovesAllReferencingRows`）は`GroupMember`/`PermissionAssignment`/
      `AuxPermissionAssignment`の付随件数を`0〜5`でjqwik生成し、削除後に対象`groupId`を参照する
      行がゼロであることに加え、無関係の他`groupId`（`groupId + 100_000`）の行が誤って削除
      されないことも合わせて検証した（Invariantの範囲を「対象行のみ削除」まで厳密化）。P2
      （`addUserToGroupRejectsDuplicateMembership`）は同一`(groupId, userId)`への1回目成功→
      2回目`ValidationException`を検証し、2回目呼び出し前後で`GroupMember`件数が不変であることを
      確認した。エンティティへの`id`付与は`SchemaImportServiceTest`と同じリフレクション
      ヘルパー（`assignId`）を再利用した。`./gradlew compileJava compileTestJava`成功、
      `./gradlew test --tests GroupServiceTest`で2件とも成功（failures=0, errors=0）を確認。
- [x] 3-2. **P3**（`setPermission`/`setAuxPermission`のIdempotence）:
      `PermissionAssignmentServiceTest`に`@Property`テストを生成する。
      実装メモ: `GroupServiceTest`と同型の`FakeRepositories`パターンで
      `PermissionAssignmentRepository`/`AuxPermissionAssignmentRepository`をインメモリリストに
      バックした。`SchemaTableRepository`/`SchemaColumnRepository`/`UserRepository`/
      `GroupRepository`は参照整合性・principal実在チェックを常に通過させるためのスタブ
      （`existsBy*`→`true`、`findBy*`→常に存在するモックエンティティを返す）とし、P3の関心事
      （Idempotence）から独立させた。`setPermissionIsIdempotent`は`(principal, connectionId,
      schema, table, column)`の組をjqwikで生成し（`column`は`table`が存在する場合のみ生成する
      `flatMap`ベースの`targets()`ジェネレータ、`setPermission`のcolumn単体指定拒否ルールと
      整合)、同一`permission`値で3回連続呼び出し後も行数が常に1件・値が不変であることを検証。
      `setAuxPermissionIsIdempotent`も同型で`granted`の3回連続呼び出しを検証。エンティティへの
      `id`付与は`GroupServiceTest`と同じリフレクションヘルパー（`assignId`）を再利用した。
      `./gradlew compileJava compileTestJava`成功、
      `./gradlew test --tests PermissionAssignmentServiceTest`で2件とも成功
      （failures=0, errors=0）を確認。
- [x] 3-3. **P4**（export→importのRound-trip）、**P5**（重複検出Invariant）、**P6**
      （全置換Invariant）: `PermissionAssignmentServiceTest`に`@Property`テストを追加生成
      する（P4は組み込みH2または`@DataJpaTest`での実データ往復、P5/P6はMockito/フェイク
      リポジトリでの検証を状況に応じて選択する）。
      実装メモ: 計画時点の想定を変更し、P4/P5/P6ともMockito/フェイクリポジトリで実装した
      （`@DataJpaTest`は不要と判断）。候補ターゲット（`table`/`column`または`table`/
      `auxType`の組。値は固定）を`PERM_CANDIDATES`/`AUX_CANDIDATES`として定義し、jqwikの
      `Arbitraries.of(list).list().uniqueElements()`で部分集合を生成することで、重複のない
      target組み合わせのバリエーションを網羅した。`exportImportRoundTrip`（P4）はUSER/GROUP
      各1principalにこれらの部分集合を`PermissionAssignment`/`AuxPermissionAssignment`として
      直接投入し、`exportPermissionsAsYaml`→`importPermissionsFromYaml`の前後で
      `(principalType, principalId, schema, table, column, permission)`
      （auxは`auxType, granted`）のタプル集合が完全一致することを検証した（他connectionIdの
      行が対象外であることも確認）。`registerUser`/`registerGroup`ヘルパーを`FakeRepositories`
      に追加し、`UserRepository.findById/findByEmail`・`GroupRepository.findById/findByName`
      をエクスポート時の`requireUser/requireGroup`とインポート時の`findByEmail/findByName`の
      両方に対応させた。`importRejectsDuplicatePermissionEntries`/
      `importRejectsDuplicateAuxPermissionEntries`（P5）は`PermissionEntryYaml`/
      `AuxPermissionEntryYaml`を手組みし、同一`(schema, table[, column|auxType])`の2エントリ
      （値はjqwikで振る）を含むYAMLが常に`ImportResult.success()==false`となることを検証した。
      この経路は`importPermissionsFromYaml`の`catch`節が
      `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`を呼ぶため、
      素の`newService()`呼び出しでは`NoTransactionException`となることが判明した。対策として
      `AnnotationTransactionAttributeSource`＋`TransactionInterceptor`＋
      `AbstractPlatformTransactionManager`のno-op実装（`NoopTransactionManager`）を用いた
      `ProxyFactory`ベースの`transactionalProxy()`ヘルパーを追加し、実際の`@Transactional`
      AOPプロキシ経由でサービスを呼び出すようにした（`TransactionInterceptor`のPlatformTx
      Manager版コンストラクタは非推奨のため`TransactionManager`型変数経由で非推奨でない
      オーバーロードを選択）。`importReplacesAllExistingRowsForConnection`（P6、成功経路の
      ため上記プロキシは不要）は、対象`connectionId`にYAML未含有の既存行（`OLD_TABLE`）と
      他`connectionId`の行を事前投入した上でインポートし、成功後に前者が消え・後者が変化せず・
      最終集合が新YAML内容と完全一致することを検証した。`./gradlew compileJava
      compileTestJava`成功、`./gradlew test --tests PermissionAssignmentServiceTest`で
      6件全て成功（failures=0, errors=0）を確認。
- [x] 3-4. **P7**（グループ合成のCommutativity）、**P8**（階層継承・個別上書きInvariant）、
      **P9**（`canCreate`/`canDelete`の主キーなしテーブルInvariant）:
      `EffectivePermissionResolverTest`に`@Property`テストを生成する。
      実装メモ: `PermissionAssignmentServiceTest`と同様のMockito/フェイクリポジトリ方式
      （`@DataJpaTest`は不使用）で`FakeRepositories`（`PermissionAssignmentRepository`/
      `AuxPermissionAssignmentRepository`/`GroupMemberRepository`/`SchemaTableRepository`/
      `SchemaColumnRepository`のMock＋インメモリリスト）を新規に構築した。P7（前半、主権限の
      グループ合成Commutativity）は固定4グループ（`GROUP_IDS`）にjqwikで振った`Permission`を
      割り当て、`Arbitraries.shuffle(GROUP_IDS)`で生成した2種の評価順序それぞれで
      `GroupMember`投入順を変えて`resolveEffectiveTablePermission`を呼び出し、結果が互いに
      一致し、かつ`Permission::max`によるreduce期待値とも一致することを検証した。P7（後半、
      補助権限のOR合成Commutativity）は、`canCreate`が主キーなしテーブルでは補助権限Cのみで
      判定される（P9と同じ性質）ことを利用し、主権限を一切介在させずに`canCreate`の戻り値が
      評価順序に依存せず`anyMatch`のOR結果と一致することを確認する形で検証した（`resolveAux
      Permission`は`private`のため直接呼べないことへの対応）。P8は、テーブル階層とカラム階層
      それぞれについて、ユーザの明示的個別設定がグループ合成結果や上位階層のユーザ設定に
      よらず常にその値で解決されることを検証（テーブル階層は`resolveEffectiveTablePermission`、
      カラム階層は`resolveEffectiveColumnPermissions`を使用、後者は`SchemaTable`/`SchemaColumn`
      のフェイクデータ投入が必要）。P9は、主キー未登録（`primaryKeySequence=null`のみの
      `SchemaColumn`）のテーブルに対し、`canDelete`が補助権限D・主権限の値によらず常に`false`
      となること、`canCreate`が補助権限Cの値と完全一致し主権限の値によらないことをそれぞれ
      検証した。`./gradlew compileJava compileTestJava`成功、`./gradlew test --tests
      EffectivePermissionResolverTest`で6件全て成功（tests=6, failures=0, errors=0）、
      `./gradlew test`（全体）でも回帰なしを確認。
- [x] 3-5. **P10**（書き込み直後の強整合性Invariant）: `PermissionCacheInvalidationListener`
      を含む統合的な検証（`@SpringBootTest`または`@DataJpaTest`＋実キャッシュBeanでの
      検証）を`EffectivePermissionResolverTest`または専用テストクラスに生成する。
      実装メモ: テスト作成前の事前確認で、アプリケーション全体に`@EnableCaching`が
      一度も付与されていないことが判明した（`@SpringBootTest`でBeanを取得し、プロキシ
      クラス名に`$$SpringCGLIB$$`が含まれないことで確認）。Spring Bootのキャッシュ
      自動構成は`CacheManager` Beanを用意するのみで、`@Cacheable`/`@CacheEvict`の
      AOPプロキシ化には`@EnableCaching`の明示付与が必須（Spring Boot 4.1で変更なし）。
      これが欠けていたため、6キャッシュの`@Cacheable`/`@CacheEvict`は現状すべて
      no-opであり、P10の検証対象（無効化の有無で結果が変わる状況）自体が存在しない
      状態だった。`backend/src/main/java/cherry/mastermeister/config/CacheConfig.java`
      （`@Configuration @EnableCaching`の空クラス、`GlobalExceptionHandler`と同じ
      `config`パッケージに配置）を追加し、`@EnableCaching`を付与した上で
      `EffectivePermissionResolver`がCGLIBプロキシ化されCaffeineの`CacheManager`が
      有効になることを再確認した（`spring.cache.type`/`spring.cache.caffeine.spec`は
      `nfr-design-patterns.md` 1.1どおりStep 16で追記予定のため未設定のままだが、
      Caffeineがクラスパス上にあるため既定で`CaffeineCacheManager`が自動選択される
      ことを確認済み）。
      本体のテストは新規`PermissionCacheConsistencyTest`（`@SpringBootTest`＋
      `@JqwikSpringSupport`、実DB・実キャッシュBeanを使用、`EffectivePermissionResolverTest`
      のMockito方式とは別クラス）として生成した。`business-rules.md` 2.6のフロー1
      （`GroupService.addUserToGroup`/`removeUserFromGroup`）・フロー2
      （`PermissionAssignmentService.setPermission`/`setAuxPermission`）・フロー4
      （`importPermissionsFromYaml`）に対応する4件の`@Property`テストを生成し、
      いずれも「キャッシュに旧値を乗せてから書き込み、直後に新値が返ることを確認する」
      形式で強整合性を検証した。フロー1のテスト（`groupMembershipChangeIsImmediately
      VisibleAfterCommit`）は`PermissionCacheInvalidationListener.onGroupChanged`
      （`@TransactionalEventListener(phase = AFTER_COMMIT)`）を実際に発火させる
      唯一の経路であり、書き込みがコミットされるまでイベントが飛ばないことを保証する
      ため、テストクラス自体やテストメソッドに`@Transactional`を付与しない設計とした
      （付与するとテストトランザクションがロールバックされAFTER_COMMITイベントが
      発火しない）。フロー2・フロー4は`PermissionAssignmentService`自身の
      `@CacheEvict(allEntries = true)`による直接無効化を検証する。フロー2の
      補助権限テストは主キーなしテーブル（`EffectivePermissionResolverTest`のP9と
      同じ手法）を使い、`canCreate`の戻り値が補助権限Cの値のみに一致することを
      利用して検証した。フロー4はYAMLを`PermissionEntryYaml`/`PrincipalYaml`/
      `PermissionYamlDocument`から`YAMLMapper`で組み立てて`importPermissionsFromYaml`
      に渡した。テストは内部DB（ファイルベースH2、`AuthenticationServiceTest`等
      既存テストと同じ実DBを使用、Spring Boot 4.1では`@DataJpaTest`/
      `@AutoConfigureTestDatabase`のどちらも組み込みDBへの自動置換をサポートしない
      ことを確認済み）を用いるため、複数回のテスト実行間でも一意なキーとなるよう
      `System.nanoTime()`を混ぜた`RUN_SEED`とインクリメンタルな`SEQ`を組み合わせて
      email/グループ名/schema名/connectionIdを生成した（`AuthenticationServiceTest`の
      `deleteAll()`方式は、他機能のデータも含む共有DBを丸ごと削除するリスクがあるため
      採用しなかった）。`./gradlew compileJava compileTestJava`成功、
      `./gradlew test --tests PermissionCacheConsistencyTest`を2回連続実行して
      いずれも4件全て成功（tests=4, failures=0, errors=0）することを確認（実行間の
      一意性キー設計の検証を兼ねる）、`./gradlew test`（全体）でも151件全て成功
      （failures=0, errors=0）で回帰なしを確認。
- [x] 3-6. **P11**（U3スキーマ再取り込みとの一貫性Invariant）: `SchemaReimportedEvent`発行
      〜`PermissionCacheInvalidationListener`〜`EffectivePermissionResolver`再判定の連携を
      検証する`@Property`テストを生成する。
      - 実装メモ: `backend/src/test/java/cherry/mastermeister/permission/
        SchemaReimportCacheConsistencyTest.java`（新規）を生成した。`SchemaImportService
        .importSchema`は実際の対象RDBMS接続（`ConnectionPoolRegistry`経由のHikari実プール）
        を必要とするため、`SchemaImportServiceTest`と同じ手法（`org.h2.tools.Server`による
        H2 TCPサーバをテスト対象RDBMS役として`@BeforeContainer`/`@AfterContainer`で起動・
        停止し、試行ごとに専用DBを新規作成）を踏襲しつつ、`@SpringBootTest`
        `@JqwikSpringSupport`で内部DB・キャッシュ（item 3-5で追加した`CacheConfig`の
        `@EnableCaching`込み）・`PermissionCacheInvalidationListener`を含む実アプリケー
        ションコンテキストを使う（`PermissionCacheConsistencyTest`と同一方針）。
        `RdbmsConnection`は`RdbmsConnectionRepository.save`で内部DBに実登録し、
        `ConnectionPoolRegistry`が実際にHikari経由でH2 TCPサーバへ接続する。2件の
        `@Property`を生成した。(1)
        `primaryKeyRestructuringChangesCanCreateWithoutPermissionAssignmentChange`:
        主キーなし⇔ありの物理テーブル再作成（`@ForAll boolean startsWithPrimaryKey`で
        両方向を生成）によって`importSchema`が`SchemaColumn.primaryKeySequence`を書き換える
        ことを利用し、`PermissionAssignment`/`AuxPermissionAssignment`を一切変更せずに
        （補助権限Cのみ最初に1回付与）、`canCreate`が2.5手順5（主キーなしは補助権限Cのみで
        可、主キーありは主キー全カラムUPDATE以上必要だが本テストでは主キー列に主権限を
        一切付与しない）どおり直後に反転することを検証する。(2)
        `columnRemovalExcludesStaleColumnFromEffectiveColumnPermissionsImmediately`:
        2カラムテーブルの一方を物理`ALTER TABLE DROP COLUMN`後に再取り込みすると、
        `EffectivePermissionResolver#resolveEffectiveColumnPermissions`が
        `findByTableIdAndStaleFalse`でstale列を除外する実装（行自体は削除されない、
        U3のP8と整合）により、`PermissionAssignment`を一切変更せずに削除カラムが
        直後に結果セットから消えることを検証する（`@ForAll boolean dropFirstColumn`で
        どちらの列を削除するかを両方向生成）。テストデータの一意性はitem 3-5と同じ
        `RUN_SEED`（`System.nanoTime()`）＋`SEQ`方式（DB名・接続名・ユーザemailに使用）。
        `./gradlew compileJava compileTestJava`成功、
        `./gradlew test --tests SchemaReimportCacheConsistencyTest`を2回連続実行して
        いずれも2件全て成功（tests=2, failures=0, errors=0）、`./gradlew test`（全体）でも
        153件全て成功（failures=0, errors=0）で回帰なしを確認。

### Step 4: ビジネスロジックサマリ
- [x] 4-1. `aidlc-docs/construction/u4-permission-management/code/business-logic-summary.md`
      を生成し、Step 2・Step 3で生成したクラス一覧とP1〜P11の対応関係を表形式で記載する
      （U1/U2/U3の`business-logic-summary.md`と同一構成）。
      実装メモ: U3の`business-logic-summary.md`と同一構成（生成クラス一覧／生成テストクラス
      一覧／P対応表／補足／既知の課題の5セクション）で生成した。生成クラス一覧はStep 2の
      2-1〜2-12（`SchemaReimportedEvent`新規＋`SchemaImportService`ブラウンフィールド修正、
      `group`パッケージ5クラス、`permission`パッケージ10クラス）に加え、item 3-5で追加した
      `config.CacheConfig`（Step 2計画には含まれていなかったブラウンフィールド発見事項として
      「補足」節に明記）を含めた。生成テストクラス一覧はStep 3の3-1〜3-6で生成した5クラス
      （`GroupServiceTest`/`PermissionAssignmentServiceTest`/`EffectivePermissionResolverTest`/
      `PermissionCacheConsistencyTest`/`SchemaReimportCacheConsistencyTest`）を検証方式付きで
      列挙した。P1〜P11対応表は`business-logic-model.md`のP1〜P11行の文言をそのまま要約し、
      対応するテストクラスと状態（すべて「実装済み（Step 3）」）を記載した。「既知の課題」節は
      U3と同型（`GroupRepository`/`GroupMemberRepository`/`PermissionAssignmentRepository`/
      `AuxPermissionAssignmentRepository`がStep 8未生成のため`compileJava`単体は失敗し続ける
      既知の状態）を明記した。

### Step 5: APIレイヤ生成
- [x] 5-1. `backend/src/main/java/cherry/mastermeister/group/GroupController.java`
      （`@RestController @RequestMapping("/api/groups")`）: `POST ""`（`createGroup`→201）,
      `PUT "/{id}"`（`renameGroup`→204）, `DELETE "/{id}"`（`deleteGroup`→204）, `GET ""`
      （`listGroups`→`List<GroupSummary>`）, `GET "/{id}/members"`（`listGroupMembers`→
      `List<UserSummary>`）, `POST "/{id}/members"`（`addUserToGroup`→201）, `DELETE
      "/{id}/members/{userId}"`（`removeUserFromGroup`→204）を生成する
      （`business-rules.md` 3節のパスパターン）。`adminUserId`は`Authentication#
      getPrincipal()`キャスト取得（U2/U3のコントローラと同一パターン）。
      実装メモ: `RdbmsConnectionController`/`RegistrationController`と同型のスタイル
      （コンストラクタ注入、`Authentication#getPrincipal()`キャストで`adminUserId`取得、
      更新系は`ResponseEntity<Void>` + `noContent()`、作成系は`@ResponseStatus(CREATED)`）で
      実装した。`GroupService.createGroup`/`renameGroup`/`addUserToGroup`はリクエストボディに
      `String name`/`Long userId`のようなプリミティブ引数を要求するため、
      `RegistrationController`の`RequestRegistrationRequest`/`CompleteRegistrationRequest`
      と同型の単純requestレコード（`GroupCreateRequest`, `GroupRenameRequest`,
      `GroupMemberAddRequest`、いずれも`group`パッケージに新規生成）を導入した。
      `addUserToGroup`は成功時に戻り値がないため`@ResponseStatus(CREATED)`のvoidメソッドとした
      （`GroupMember`自体を返すエンドポイントではないため、レスポンスボディなしの201）。
      `./gradlew compileJava compileTestJava`成功を確認（単体テストはStep 6で作成）。
- [x] 5-2. `backend/src/main/java/cherry/mastermeister/permission/
      PermissionController.java`（`@RestController @RequestMapping("/api/rdbms-connections/
      {connectionId}/permissions")`）: `PUT ""`（`PermissionUpdateRequest`を受け、`permission`
      有無で`setPermission`/`setAuxPermission`へ分岐、204。「ブラウンフィールド発見事項」）,
      `GET "/export"`（`exportPermissionsAsYaml`→`byte[]`、`Content-Type: application/
      x-yaml`、`Content-Disposition: attachment; filename=...`）, `POST "/import"`
      （`multipart/form-data`のYAMLファイルを受け`importPermissionsFromYaml`→
      `ImportResult`）を生成する（`business-rules.md` 3節）。
      【実装メモ】`updatePermission`は`request.permission()`が存在すれば`setPermission`、
      存在しなければ`request.auxType()`/`request.granted()`を`Optional.get()`で取り出し
      `setAuxPermission`へ分岐する204 voidエンドポイントとした（他の分岐判定用フィールドの
      整合性チェックは呼び出し元入力を信頼し追加しない）。`exportPermissions`は
      `ResponseEntity<byte[]>`に`Content-Type: application/x-yaml`と
      `Content-Disposition: attachment; filename=permissions-{connectionId}.yaml`ヘッダを
      設定して返す、リポジトリ初のbyte[]レスポンスエンドポイントとした。`importPermissions`は
      `consumes = MULTIPART_FORM_DATA_VALUE`の`@RequestParam("file") MultipartFile`を受け、
      `file.getBytes()`のIOExceptionは`UncheckedIOException`にラップして`importPermissionsFromYaml`
      （`ImportResult`を直接返す、例外を投げない設計）を呼ぶ、リポジトリ初のmultipartアップロード
      エンドポイントとした。`./gradlew compileJava compileTestJava`成功を確認
      （単体テストはStep 6で作成）。
- [x] 5-3. `backend/src/main/java/cherry/mastermeister/config/GlobalExceptionHandler.java`
      （既存、ブラウンフィールド修正）に`@ExceptionHandler(PermissionYamlFormatException
      .class)`（400 `PERMISSION_YAML_FORMAT_ERROR`）を追記する（「ブラウンフィールド発見
      事項」）。
      【実装メモ】既存の各`@ExceptionHandler`（`InvalidUserStateException`等）と同型で
      `PermissionYamlFormatException`ハンドラを追加し、400 `PERMISSION_YAML_FORMAT_ERROR`を
      返すようにした。【既知の課題】`PermissionAssignmentService.importPermissionsFromYaml`
      は現状`PermissionYamlFormatException`を内部でcatchし`ImportResult(false, message)`として
      200系で返す設計（Step 2/3で確定済み）のため、このハンドラは同例外がインポート経路以外
      から投げられた場合の防御的フォールバックとして機能する（現状のインポートAPI経路では
      到達しない）。`./gradlew compileJava compileTestJava`成功を確認
      （単体テストはStep 6で作成）。
- [x] 5-4. `backend/src/main/java/cherry/mastermeister/security/SecurityConfig.java`
      （既存、ブラウンフィールド修正）を確認する。`/api/groups/**`の`hasRole("ADMIN")`
      エントリを`anyRequest().authenticated()`より前に追記する。
      `/api/rdbms-connections/{connectionId}/permissions/**`は既存の
      `/api/rdbms-connections/**`エントリ（U3）が前方一致で包含するため追加不要であることを
      確認するのみとする（「サービス境界・責務」参照）。
      【実装メモ】`.requestMatchers("/api/rdbms-connections/**").hasRole("ADMIN")`の直後、
      `anyRequest().authenticated()`より前に`.requestMatchers("/api/groups/**")
      .hasRole("ADMIN")`を追記した。`/api/rdbms-connections/**`エントリが既に
      `/api/rdbms-connections/{connectionId}/permissions/**`を前方一致で包含していることを
      確認し、`PermissionController`用の追加エントリは不要と判断した。
      `./gradlew compileJava compileTestJava`成功を確認（単体テストはStep 6で作成）。
      これでStep 5（APIレイヤ生成）の全4項目が完了。

### Step 6: APIレイヤ単体テスト
- [x] 6-1. `GroupControllerTest`（`@WebMvcTest` + `spring-security-test`）: 7エンドポイント
      それぞれについて管理者成功系・非管理者403・未認証401をexample-basedテストで検証する
      （U2/U3のControllerTestパターンを踏襲）。
      【実装メモ】`RdbmsConnectionControllerTest`と同型で`@WebMvcTest(GroupController.class)`
      + `@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class,
      RestAccessDeniedHandler.class})`の構成とし、`GroupService`を`@MockitoBean`化した。
      7エンドポイント×3ケース（管理者成功・非管理者403・未認証401）の21テストメソッドを実装。
      管理者系は`authentication()`リクエストポストプロセッサで`principal=1L`の
      `UsernamePasswordAuthenticationToken`を注入し`adminUserId`引数解決を検証、
      非管理者系は`@WithMockUser(roles = "USER")`、未認証系は`@WithAnonymousUser`を使用した。
      `./gradlew test --tests "cherry.mastermeister.group.GroupControllerTest"`成功
      （21件全て成功）。
- [x] 6-2. `PermissionControllerTest`（`@WebMvcTest` + `spring-security-test`）: 3エンドポイント
      （権限更新・エクスポート・インポート）それぞれについて管理者成功系・非管理者403・
      未認証401、およびインポート形式不正時の400をexample-basedテストで検証する。
      【実装メモ】`AuthControllerTest`と同様、`@WebMvcTest`は`GlobalExceptionHandler`
      （`@RestControllerAdvice`）を自動検出するため明示`@Import`不要であることを確認した。
      権限更新は`permission`present/absentの2分岐（`setPermission`/`setAuxPermission`への
      委譲）をそれぞれ成功系として検証。エクスポートは`Content-Type: application/x-yaml`と
      `Content-Disposition`ヘッダを`header()`マッチャで検証。インポートは`MockMultipartFile`
      による`multipart()`リクエストで成功系（`ImportResult`のJSONボディ）を検証し、形式不正時の
      400は`permissionAssignmentService.importPermissionsFromYaml`が`PermissionYamlFormatException`
      をスローするようモックして`GlobalExceptionHandler`の item 5-3 ハンドラとの結線を検証した
      （実サービス実装は同例外を内部でcatchし`ImportResult(false, ...)`を返す設計のため、
      この400経路はコントローラ層の防御的フォールバック配線の確認である旨、item 5-3の
      既知の課題と対応）。3エンドポイント×3〜4ケースの11テストメソッドを実装。
      `./gradlew test --tests "cherry.mastermeister.permission.PermissionControllerTest"`成功
      （11件全て成功）、`./gradlew test`（フルスイート）成功も確認しリグレッションなしを確認。
      これでStep 6（APIレイヤ単体テスト）の全2項目が完了。

### Step 7: APIレイヤサマリ
- [x] 7-1. `aidlc-docs/construction/u4-permission-management/code/api-layer-summary.md`を
      生成し、エンドポイント一覧（パス・メソッド・認可要件・リクエスト/レスポンス形状）と
      エラーレスポンス表（`PERMISSION_YAML_FORMAT_ERROR`含む）を記載する。
      【実装メモ】U3の`api-layer-summary.md`と同構成（エンドポイント一覧表、
      Controllerごとの詳細説明、エラーレスポンス表、テストカバレッジ表）で生成した。
      `PermissionController`の項では`PUT ""`のリクエスト例をテーブル/カラム権限設定と
      補助権限設定の2パターン併記し、エラーレスポンス表に既知の課題（インポートAPI経路では
      `PERMISSION_YAML_FORMAT_ERROR`は実際には到達せず、防御的フォールバックである旨）を
      明記した。

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
      訂正（Step 8確認時）: `EffectivePermissionResolver`の実際の階層解決ロジック
      （`findMostSpecificPermission`/`findMostSpecificAuxPermission`）はcolumn→table→schemaの
      各段で`find...ColumnName`/`find...AuxType`を個別に呼び出す方式であり、上記の
      `findByPrincipalTypeAndPrincipalIdAndConnectionId`（複数件取得版）は実際には
      どこからも呼び出されていない未使用メソッドと判明した。両リポジトリから削除した。

### Step 9: リポジトリレイヤ単体テスト
- [x] 9-1. `GroupRepositoryTest`/`GroupMemberRepositoryTest`/
      `PermissionAssignmentRepositoryTest`/`AuxPermissionAssignmentRepositoryTest`
      （いずれも`@DataJpaTest`、組み込みH2）: 基本CRUD・カスタムクエリメソッド・一意制約
      違反時の例外発生をexample-basedテストで検証する。
      実装メモ: `SchemaTableRepositoryTest`（U3）と同様の構成（`@Autowired Repository` +
      `@Autowired TestEntityManager`、`saveAndFlush`/`delete`/カスタムfindメソッド/
      unique制約違反の`DataIntegrityViolationException`を検証）で4クラスを生成した。
      `GroupRepositoryTest`（5件）: 基本CRUD、`findByName`、`name`のunique制約。
      `GroupMemberRepositoryTest`（8件）: 基本CRUD、`findByGroupIdAndUserId`/
      `findByGroupId`/`findByUserId`/`deleteByGroupId`、`(groupId, userId)`のunique制約。
      `PermissionAssignmentRepositoryTest`（8件）: 基本CRUD、
      `findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndColumnName`
      （NULL列と非NULL列の判別含む）、`findByConnectionId`、`deleteByConnectionId`、
      `deleteByPrincipalTypeAndPrincipalId`、unique制約（`columnName`がNULL許容のため
      NULL同士は制約対象外となる点に注意し、非NULL値の組み合わせで検証）。
      `AuxPermissionAssignmentRepositoryTest`（8件）: 基本CRUD、
      `findByPrincipalTypeAndPrincipalIdAndConnectionIdAndSchemaNameAndTableNameAndAuxType`
      （NULLテーブルと非NULLテーブルの判別含む）、`findByConnectionId`、
      `deleteByConnectionId`、`deleteByPrincipalTypeAndPrincipalId`、unique制約
      （`tableName`がNULL許容のため同様に非NULL値で検証）。全29件成功、フルスイートも回帰なし成功。

### Step 10: リポジトリレイヤサマリ
- [x] 10-1. `aidlc-docs/construction/u4-permission-management/code/
      repository-layer-summary.md`を生成し、4リポジトリのクエリメソッド一覧とインデックス
      設計（`GroupMember`の追加インデックス含む、`nfr-design-patterns.md` 4.1）を記載する。
      実装メモ: U3の`repository-layer-summary.md`と同構成（リポジトリごとのメソッド表、
      インデックス設計節、テストカバレッジ表）で生成した。`GroupMember`の
      `(userId, groupId)`追加インデックスは`EffectivePermissionResolver.groupIdsOf`が
      権限解決のたびに呼ぶ高頻度クエリのためと説明。`PermissionAssignment`/
      `AuxPermissionAssignment`はunique制約の非先頭列（`connectionId`）が条件の
      `findByConnectionId`/`deleteByConnectionId`について、エクスポート・全置換
      インポート・グループ削除はいずれも低頻度操作であるため追加インデックスを
      見送った旨を明記。Step 8確認時に削除した未使用メソッド
      `findByPrincipalTypeAndPrincipalIdAndConnectionId`の経緯も記載した。

### Step 11: フロントエンドコンポーネント生成
- [x] 11-1. `frontend/src/features/group/` に`GroupListPage.tsx`、`GroupTable.tsx`、
      `GroupDetailPage.tsx`、`GroupMemberTable.tsx`、`api.ts`、`types.ts`を生成する
      （`frontend-components.md`のコンポーネント階層・data-testid規約に準拠）。
      実装メモ: U3の`rdbmsConnection`（一覧+編集ページ分離）・U2の`userRegistration`
      （`PendingUsersPage`/`PendingUsersTable`のトースト通知＋`ConfirmDialog`パターン）を
      テンプレートに、`DataTable`・`ConfirmDialog`・`ToastNotification`（いずれもU1）を
      再利用して実装した。`GroupTable`は行内インライン編集（`useState`で`renamingGroupId`
      管理）で名称変更、`ConfirmDialog`で削除確認（カスケード削除の影響を確認文言に含める、
      frontend-components.md 37-38行）。`GroupDetailPage`は`GroupSummary`単体取得APIが
      存在しない（`GroupController`は`listGroups`のみ）ため、`groupApi.listGroups()`を
      呼び出しURLパラメータの`id`でクライアント側フィルタする方式とした（バックエンド変更
      なしでの対応、ブラウンフィールド発見事項相当のAI決定）。「所属ユーザ追加」は
      `frontend-components.md`が「詳細はCode Generationで確定する」としていたメール
      アドレス検索の代替として、既存バックエンドにユーザ検索API（`RegistrationController`は
      `/pending`のみで承認済みユーザの一覧・検索エンドポイントを持たない）が存在しないため
      数値のユーザID直接入力とした（`GroupMemberAddRequest(Long userId)`に整合）。
      `tsc -b --noEmit`・`npm run lint`（oxlint）ともにエラーなし。
- [x] 11-2. `frontend/src/features/permission/` に`PermissionAssignmentPage.tsx`、
      `ConnectionSelector.tsx`、`PrincipalSelector.tsx`、`PermissionTree.tsx`、
      `PermissionForm.tsx`、`PermissionYamlPanel.tsx`、`api.ts`（YAMLエクスポートは
      `blob`ダウンロード、インポートは`FormData`アップロード。トークン取得は`apiClient`の
      `useAuthStore`を直接参照する形でJSON専用の`apiFetch`をバイパスする——「ブラウン
      フィールド発見事項」相当のAI決定、Step 2完了後に確定）、`types.ts`を生成する
      （`frontend-components.md`）。`features/permission/`は`features/group/`の
      `groupApi.listGroups()`のみを参照する（一方向依存）。
      実装メモ: `PermissionController`（`PermissionControllerTest`のJSONフィクスチャで
      `Optional<T>`フィールドがnull/値として素直にシリアライズされることを確認済み）に
      合わせ`setPermission`/`setAuxPermission`は共に単一の`PUT /permissions`
      エンドポイントへ`permission`/`auxType`+`granted`のいずれかをnullにして呼び分ける。
      `PermissionController`には単一principalの現在の権限設定を取得するGETエンドポイントが
      存在しない（`export`はYAML全件、`import`は全置換のみ）ため、`PermissionForm`の
      `currentPermission`/`currentAuxPermissions`は`PermissionAssignmentPage`から常に`null`
      を渡し、フォームは常に`NONE`/未チェックから開始する設計とした（バックエンド変更なし
      での対応、ブラウンフィールド発見事項相当のAI決定）。`PrincipalSelector`のユーザ選択は
      `features/group/`の`GroupDetailPage`と同様、ユーザ検索APIが存在しないためユーザID
      直接入力とした。`PermissionTree`はU3の`schemaApi`（`listSchemas`/`listTables`/
      `getTableDetail`）をスキーマ選択時・テーブル選択時に遅延取得するアコーディオン構成。
      `importPermissionsFromYaml`は`PermissionAssignmentService`の実装
      （`PermissionYamlFormatException`を内部でcatchし`ImportResult(false, message)`を
      200で返す設計、`PermissionControllerTest`の400テストはサービスをモックした場合のみの
      挙動）に合わせ、非200レスポンスも極力`ImportResult`形へ正規化して表示する。
      `tsc -b --noEmit`・`npm run lint`（oxlint）ともにエラーなし。
- [x] 11-3. `frontend/src/routes/AppRouter.tsx`（既存、ブラウンフィールド修正）:
      `/admin/groups`, `/admin/groups/:id`, `/admin/permissions`を`ProtectedRoute
      requiredRole="ADMIN"`配下に追加する。
      実装メモ: 既存の`/admin/schema/:connectionId`等のルート定義パターン（`ProtectedRoute
      requiredRole="ADMIN"`でラップ）をそのまま踏襲し、`AuthenticatedRoutes`内の
      `<Routes>`に3件追加した。`tsc -b --noEmit`・`npm run lint`（oxlint）ともにエラーなし。
      既存の`AppRouter.test.tsx`（9件）を`vitest run`で再実行し、退行がないことを確認した
      （item 11-3自体の新規ルートに対するテストはStep 12 item 12-2で追加する）。
- [x] 11-4. `frontend/src/components/AppLayout.tsx`（既存、ブラウンフィールド修正）に
      「グループ管理」「権限設定」へのナビゲーションリンクを追加する（管理者ロールのみ
      表示）。
      実装メモ: 既存の「RDBMS接続管理」等のナビゲーションリンクと同一パターン
      （`isAuthenticated && currentUser?.role === 'ADMIN'`条件、`<a>`要素、`data-testid`付与）
      をそのまま踏襲し、「RDBMS接続管理」リンクの直後・ログアウトボタンの直前に
      「グループ管理」（`/admin/groups`、`data-testid="app-layout-nav-groups"`）、
      「権限設定」（`/admin/permissions`、`data-testid="app-layout-nav-permissions"`）の
      2件を追加した。`tsc -b --noEmit`・`npm run lint`（oxlint）ともにエラーなし。
      既存の`AppRouter.test.tsx`・`AppLayout.test.tsx`（計9件）を`vitest run`で再実行し、
      退行がないことを確認した（新規ナビゲーションリンクに対するテストはStep 12
      item 12-2で追加する）。これによりStep 11（フロントエンドコンポーネント生成）は
      全4項目完了。

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