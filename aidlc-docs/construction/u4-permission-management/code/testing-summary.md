# U4 Permission Management - テスティングサマリ

Step 3/6/9/12で生成した全テスト（ビジネスロジック層・API層・リポジトリ層・フロントエンド）を横断し、
PBT-10（補完的テスト戦略）の遵守状況、P1〜P11（`business-logic-summary.md`で識別したテスト可能な
性質）とテストクラスの対応関係、およびexample-basedテストの一覧を整理する。

## PBT-10: 補完的テスト戦略

`property-based-testing`拡張のRule PBT-10は、プロパティベーステスト（PBT）がexample-basedテストを
**置き換えるのではなく補完する**ことを要求する。本ユニットでは以下の方針で遵守した（U1/U2/U3と
同一方針）。

- **性質（P1〜P11）の検証はjqwik `@Property`で実施**: `GroupServiceTest`,
  `PermissionAssignmentServiceTest`, `EffectivePermissionResolverTest`,
  `PermissionCacheConsistencyTest`, `SchemaReimportCacheConsistencyTest`の各テストクラスで、
  対応する性質を広い入力空間に対して自動生成されたケースで検証する。
- **業務的に重要な具体シナリオはexample-basedテストで別途固定**: API層
  （`GroupControllerTest`, `PermissionControllerTest`）、リポジトリ層
  （`GroupRepositoryTest`, `GroupMemberRepositoryTest`, `PermissionAssignmentRepositoryTest`,
  `AuxPermissionAssignmentRepositoryTest`）、フロントエンド（新規10ファイル＋既存2ファイル拡張）は
  すべてexample-basedテストのみで構成し、管理者成功系・非管理者403・未認証401などの規定の入出力を
  明示するケースを固定した。PBTが唯一のテストとなっている性質はない。
- **テストクラス/ファイルでPBTとexample-basedを明確に分離**: バックエンドはPBT専用クラス
  （ビジネスロジック層の5クラス）とexample-based専用クラス（API層2クラス・リポジトリ層4クラス）を
  完全に分離しており、U1のような同居パターンは採らなかった（U2/U3と同一方針。本ユニットも
  P1〜P11すべてがビジネスロジック層の性質であり、API層・リポジトリ層に先送りされる性質が
  無かったため）。フロントエンドはU1/U2/U3同様、Vitest + React Testing Libraryによる
  example-basedテストのみ（TypeScript側にPBTフレームワークは導入していない）。
- **1テストメソッドが複数性質を検証するケース**: 本ユニットには該当なし（U2の
  `RefreshTokenServiceTest`のようなパターンは存在しない）。
- **PBTが失敗を検出した場合の回帰テスト追加**: 本ユニットの開発中にjqwikが恒久的な回帰テストを
  要する失敗を検出した事例はない（該当なし）。

## P1〜P11対応表（最終版）

| # | 対象 | 検証テストクラス | 検証方式 | 層 |
|---|---|---|---|---|
| P1 | `GroupService.deleteGroup`のInvariant（削除後、当該`groupId`を参照する`GroupMember`/`PermissionAssignment`/`AuxPermissionAssignment`が1件も残らない） | `GroupServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P2 | `GroupService.addUserToGroup`のIdempotence（重複所属への2回目呼び出しは常に例外、行数不変） | `GroupServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P3 | `PermissionAssignmentService.setPermission`/`setAuxPermission`のIdempotence（同一値での複数回呼び出しでも常に1行・値不変） | `PermissionAssignmentServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P4 | `exportPermissionsAsYaml`→`importPermissionsFromYaml`のRound-trip（タプル集合が完全一致） | `PermissionAssignmentServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P5 | `importPermissionsFromYaml`の重複検出Invariant（同一組み合わせが2回以上出現するYAMLは常に`PermissionYamlFormatException`） | `PermissionAssignmentServiceTest` | jqwik `@Property`（実`@Transactional`AOPプロキシ経由） | ビジネスロジック（Step 3） |
| P6 | `importPermissionsFromYaml`の全置換Invariant（成功後、対象`connectionId`の集合はYAML内容と完全一致、旧行は残らない） | `PermissionAssignmentServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P7 | `EffectivePermissionResolver`のグループ合成Commutativity（評価順序によらず最大値/OR合成結果は不変） | `EffectivePermissionResolverTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P8 | `EffectivePermissionResolver`の階層継承・個別上書きInvariant（明示的個別設定は常にその値で解決される） | `EffectivePermissionResolverTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P9 | `EffectivePermissionResolver.canCreate`/`canDelete`のInvariant（主キーなしテーブルで`canDelete`は常に`false`） | `EffectivePermissionResolverTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P10 | `EffectivePermissionResolver`の書き込み直後強整合性Invariant（フロー1/2/4いずれの成功直後も古い値が返らない） | `PermissionCacheConsistencyTest` | jqwik `@Property`（`@JqwikSpringSupport @SpringBootTest`、実DB・実キャッシュBean） | ビジネスロジック（Step 3、`CacheConfig`新規導入の契機） |
| P11 | `EffectivePermissionResolver`とU3`SchemaImportService.importSchema`の一貫性Invariant（再取り込みによる`primaryKeySequence`/`stale`変化は、権限行に変更がなくても直後に反映される） | `SchemaReimportCacheConsistencyTest` | jqwik `@Property`（`@JqwikSpringSupport @SpringBootTest`、実H2 TCPサーバを対象RDBMS役とした`SchemaImportService.importSchema`＋実キャッシュBean） | ビジネスロジック（Step 3） |

P1〜P11全11性質にjqwik `@Property`テストが対応済み（PBT-02〜PBT-08準拠）。U2/U3と同様、本ユニットも
全性質がビジネスロジック層で完結しており、API層・リポジトリ層に先送りされた性質は無い
（詳細は`business-logic-summary.md`参照。P10/P11は`GroupRepository`/`GroupMemberRepository`/
`PermissionAssignmentRepository`/`AuxPermissionAssignmentRepository`の正式生成前にStep 3で
先行実装されたため、Step 8完了後の現時点では通常のテストとして問題なく実行される）。

## example-basedテスト一覧

### バックエンド（Step 6, 9）

| テストクラス | 検証内容 | 件数 |
|---|---|---|
| `GroupControllerTest` | グループCRUD（create/rename/delete/list）・メンバー管理（list/add/remove）の7エンドポイントそれぞれについて管理者成功系・非管理者403・未認証401を検証（`create`/`rename`/`delete`/`add`/`remove`は`Authentication`を明示注入し`adminUserId`がサービス呼び出しに渡ることも確認） | 21 |
| `PermissionControllerTest` | 権限更新（`setPermission`/`setAuxPermission`の2分岐）・エクスポート・インポートの3エンドポイントそれぞれについて管理者成功系・非管理者403・未認証401、加えてインポート形式不正時の400を検証 | 11 |
| `GroupRepositoryTest` | 基本CRUD、`findByName`（一致/不一致）、`name`のunique制約違反時の`DataIntegrityViolationException` | 5 |
| `GroupMemberRepositoryTest` | 基本CRUD、`findByGroupIdAndUserId`/`findByGroupId`/`findByUserId`/`deleteByGroupId`、`(groupId, userId)`のunique制約違反 | 8 |
| `PermissionAssignmentRepositoryTest` | 基本CRUD、column/table/schema段階での`findBy...`、`findByConnectionId`、`deleteByConnectionId`、`deleteByPrincipalTypeAndPrincipalId`、unique制約違反 | 8 |
| `AuxPermissionAssignmentRepositoryTest` | 基本CRUD、table/schema段階での`findBy...`、`findByConnectionId`、`deleteByConnectionId`、`deleteByPrincipalTypeAndPrincipalId`、unique制約違反 | 8 |

バックエンドexample-based合計: 61件（API層32件＋リポジトリ層29件）。`api-layer-summary.md`・
`repository-layer-summary.md`作成時点（Step 7/Step 10）で記載していた件数と、Step 9完了後の実
テスト実行結果を突き合わせ、本Step時点で上表の件数が正確であることを確認した（設計時点の方針との
乖離はなく、件数記載の精査のみ）。

### フロントエンド（Step 12、全てexample-based）

| テストファイル | 件数 |
|---|---|
| `features/group/GroupTable.test.tsx` | 6 |
| `features/group/GroupListPage.test.tsx` | 5 |
| `features/group/GroupMemberTable.test.tsx` | 3 |
| `features/group/GroupDetailPage.test.tsx` | 4 |
| `features/permission/ConnectionSelector.test.tsx` | 2 |
| `features/permission/PrincipalSelector.test.tsx` | 3 |
| `features/permission/PermissionTree.test.tsx` | 5 |
| `features/permission/PermissionForm.test.tsx` | 5 |
| `features/permission/PermissionYamlPanel.test.tsx` | 3 |
| `features/permission/PermissionAssignmentPage.test.tsx` | 3 |
| `routes/AppRouter.test.tsx`（拡張、既存分に3件追加） | 3件追加 |
| `components/AppLayout.test.tsx`（拡張、既存3件のまま） | 0件追加（既存ケース拡張のみ） |

本ユニットでの新規/拡張分: 42件（新規10ファイル・39件＋既存`AppRouter.test.tsx`への3件追加）。
U1・U2・U3既存分と合わせ、フロントエンド全体は40ファイル・156テストとなる（詳細は
`frontend-summary.md`参照）。

## 実行確認状況

本サマリ作成時点で実際にテストを実行し、グリーンであることを確認済み（Build and Testステージの
再確認対象ではあるが、Step 13完了時点で以下を確認している）。

- **バックエンド**: `./gradlew test` 40テストクラス・214/214件成功、0失敗・0エラー
  （テスト結果XML: `build/test-results/test/`で個別に集計・確認済み）。うちU4で新規追加した
  テストクラスは11クラス・81件
  （`GroupServiceTest` 2、`PermissionAssignmentServiceTest` 6、`EffectivePermissionResolverTest` 6、
  `PermissionCacheConsistencyTest` 4、`SchemaReimportCacheConsistencyTest` 2、
  `GroupControllerTest` 21、`PermissionControllerTest` 11、`GroupRepositoryTest` 5、
  `GroupMemberRepositoryTest` 8、`PermissionAssignmentRepositoryTest` 8、
  `AuxPermissionAssignmentRepositoryTest` 8）。U1/U2/U3既存29クラス・133件は回帰なし。
- **フロントエンド**: `npx vitest run` 40ファイル・156/156件成功、`npx tsc -b`
  （型チェック）・`npx oxlint`（Lint）共にエラーなし。

## 既知の課題

Step 3時点で`business-logic-summary.md`に記録されていた「`compileJava`単体がStep 8まで未生成の
4リポジトリ（`GroupRepository`/`GroupMemberRepository`/`PermissionAssignmentRepository`/
`AuxPermissionAssignmentRepository`）の未解決参照により失敗し続ける」既知事象は、Step 8（8-1〜8-2、
4リポジトリの正式生成）完了時点で解消済みである（本Step時点の`./gradlew test`が全体ビルド経由で
成功していることで再確認済み）。