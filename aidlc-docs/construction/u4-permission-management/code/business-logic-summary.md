# business-logic-summary.md — U4: Permission Management

Step 2（ビジネスロジック生成）・Step 3（ビジネスロジック単体テスト）で生成したクラス一覧と、
`business-logic-model.md`のP1〜P11との対応関係。

## 生成クラス一覧（Step 2）

| パッケージ | クラス/インタフェース | 役割 |
|---|---|---|
| `schema` | `SchemaReimportedEvent`（新規、record） | U3`SchemaImportService.importSchema`の再取り込み成功を通知するアプリケーションイベント |
| `schema` | `SchemaImportService`（既存、ブラウンフィールド修正） | 再取り込み成功時に`SchemaReimportedEvent`を発行するよう変更 |
| `group` | `Group`（JPA entity） | グループエンティティ（`name`一意制約、`@Table(name = "app_group")`） |
| `group` | `GroupMember`（JPA entity） | グループ所属エンティティ（`(groupId, userId)`一意制約） |
| `group` | `GroupChangedEvent`（record） | グループ所属・定義変更を通知するアプリケーションイベント |
| `group` | `GroupSummary`, `UserSummary`（record） | グループ/ユーザ一覧DTO |
| `group` | `GroupService` | グループCRUD・所属管理（`@Transactional`、成功時`GroupChangedEvent`発行、失敗時含め監査ログ記録） |
| `permission` | `PermissionAssignment`, `AuxPermissionAssignment`（JPA entity） | 主権限（NONE/READ/UPDATE）・補助権限（CREATE/DELETE）の割当エンティティ |
| `permission` | `PrincipalType`, `Permission`, `AuxPermissionType`, `PrincipalRef`（enum/record） | principal種別・権限強度・補助権限種別・principal参照の値型 |
| `permission` | `ImportResult`, `PermissionUpdateRequest`（record） | YAMLインポート結果・権限更新要求DTO |
| `permission` | `PermissionYamlDocument`, `PrincipalYaml`, `PermissionEntryYaml`, `AuxPermissionEntryYaml`（POJO） | YAMLバインド用可変POJO（Jackson `YAMLMapper`） |
| `permission` | `PermissionAssignmentService` | 権限設定の書き込み（`setPermission`/`setAuxPermission`）・YAMLエクスポート/インポート（全置換方式）、書き込み系は`@CacheEvict(allEntries = true)`込み |
| `permission` | `PermissionYamlFormatException` | YAMLインポート検証違反用例外 |
| `permission` | `EffectivePermissionResolver` | 実効権限判定Facade（階層継承・グループ合成・個別上書き・`canCreate`/`canDelete`、6メソッドに`@Cacheable`） |
| `permission` | `PermissionCacheInvalidationListener` | `GroupChangedEvent`/`SchemaReimportedEvent`を`@TransactionalEventListener(phase = AFTER_COMMIT)`で購読し6キャッシュを`@CacheEvict(allEntries = true)`で一括無効化 |
| `config` | `CacheConfig`（新規） | `@EnableCaching`付与（item 3-5でStep 2完了後に事前確認により必要と判明し追加） |

## 生成テストクラス一覧（Step 3）

| テストクラス | 検証方式 |
|---|---|
| `group.GroupServiceTest` | jqwik `@Property`（Mockito `FakeRepositories`パターン、`SchemaImportServiceTest`踏襲） |
| `permission.PermissionAssignmentServiceTest` | jqwik `@Property`（Mockito `FakeRepositories`、P5のみ`ProxyFactory`＋`TransactionInterceptor`による実`@Transactional`AOPプロキシ経由） |
| `permission.EffectivePermissionResolverTest` | jqwik `@Property`（Mockito `FakeRepositories`、キャッシュ無効の素のインスタンスで判定ロジックのみ検証） |
| `permission.PermissionCacheConsistencyTest` | jqwik `@Property`（`@JqwikSpringSupport @SpringBootTest`、実DB・実キャッシュBean、`CacheConfig`導入の契機） |
| `permission.SchemaReimportCacheConsistencyTest` | jqwik `@Property`（`@JqwikSpringSupport @SpringBootTest`、実H2 TCPサーバを対象RDBMS役とした`SchemaImportService.importSchema`＋実キャッシュBean） |

## P1〜P11対応表

| # | 対象 | 検証テストクラス | 状態 |
|---|---|---|---|
| P1 | `GroupService.deleteGroup`のInvariant（削除後、当該`groupId`を参照する`GroupMember`/`PermissionAssignment`/`AuxPermissionAssignment`が1件も残らない） | `GroupServiceTest` | 実装済み（Step 3） |
| P2 | `GroupService.addUserToGroup`のIdempotence（重複所属への2回目呼び出しは常に例外、行数不変） | `GroupServiceTest` | 実装済み（Step 3） |
| P3 | `PermissionAssignmentService.setPermission`/`setAuxPermission`のIdempotence（同一値での複数回呼び出しでも常に1行・値不変） | `PermissionAssignmentServiceTest` | 実装済み（Step 3） |
| P4 | `exportPermissionsAsYaml`→`importPermissionsFromYaml`のRound-trip（タプル集合が完全一致） | `PermissionAssignmentServiceTest` | 実装済み（Step 3） |
| P5 | `importPermissionsFromYaml`の重複検出Invariant（同一組み合わせが2回以上出現するYAMLは常に`PermissionYamlFormatException`） | `PermissionAssignmentServiceTest` | 実装済み（Step 3） |
| P6 | `importPermissionsFromYaml`の全置換Invariant（成功後、対象`connectionId`の集合はYAML内容と完全一致、旧行は残らない） | `PermissionAssignmentServiceTest` | 実装済み（Step 3） |
| P7 | `EffectivePermissionResolver`のグループ合成Commutativity（評価順序によらず最大値/OR合成結果は不変） | `EffectivePermissionResolverTest` | 実装済み（Step 3） |
| P8 | `EffectivePermissionResolver`の階層継承・個別上書きInvariant（明示的個別設定は常にその値で解決される） | `EffectivePermissionResolverTest` | 実装済み（Step 3） |
| P9 | `EffectivePermissionResolver.canCreate`/`canDelete`のInvariant（主キーなしテーブルで`canDelete`は常に`false`） | `EffectivePermissionResolverTest` | 実装済み（Step 3） |
| P10 | `EffectivePermissionResolver`の書き込み直後強整合性Invariant（フロー1/2/4いずれの成功直後も古い値が返らない） | `PermissionCacheConsistencyTest` | 実装済み（Step 3、`CacheConfig`新規導入の契機） |
| P11 | `EffectivePermissionResolver`とU3`SchemaImportService.importSchema`の一貫性Invariant（再取り込みによる`primaryKeySequence`/`stale`変化は、権限行に変更がなくても直後に反映される） | `SchemaReimportCacheConsistencyTest` | 実装済み（Step 3） |

**補足**: U4はU2/U3と同様、P1〜P11すべてがStep 3で実装完了している。ただし`GroupRepository`/
`GroupMemberRepository`/`PermissionAssignmentRepository`/`AuxPermissionAssignmentRepository`の
正式生成はStep 8（8-1〜8-2）の担当であり、Step 3時点では4つとも一時的な未追跡スタブ（`src/main/
java`配下、`git status`で`??`、実行時検証専用、コミット対象外）として存在する。
`PermissionCacheConsistencyTest`/`SchemaReimportCacheConsistencyTest`はいずれも`@SpringBootTest`で
実アプリケーションコンテキストを起動するため、このスタブが存在する開発時ローカル環境でのみ
実行可能という制約はU3の`SchemaImportServiceTest`（`RollbackRoundTrip`/`SchemaQueryServiceTest`）
と同型である。

**Step 2完了時点で新規判明した設計要素**: item 3-5（P10テスト作成前の事前確認）で、
`@EnableCaching`がアプリケーション全体に一度も付与されておらず、`EffectivePermissionResolver`/
`PermissionAssignmentService`/`PermissionCacheInvalidationListener`の`@Cacheable`/`@CacheEvict`が
すべてno-opだったことが判明した。これを受けて`config.CacheConfig`（`@Configuration
@EnableCaching`）をStep 2完了後・Step 3の途中で追加した（`nfr-design-patterns.md` 1.1の
Caffeineキャッシュ方針を実際に有効化するための必須クラスであり、当初のStep 2計画（2-1〜2-12）
には含まれていなかったブラウンフィールド発見事項）。

**既知の課題（Step 3スコープ外）**: `compileJava`単体は、Step 8まで未生成の`GroupRepository`/
`GroupMemberRepository`/`PermissionAssignmentRepository`/`AuxPermissionAssignmentRepository`
（一時スタブは`src/main/java`配下に存在するが未追跡・未コミットのためコミット時点のソース
ツリーには含まれない）の未解決参照により失敗し続ける、既知・意図された状態が継続している。
個々のテストクラスは`./gradlew test --tests "..."`で（スタブが存在する開発時ローカル環境に
おいて）独立して実行・成功することを都度確認済み。この状態はStep 8完了まで解消しない。
