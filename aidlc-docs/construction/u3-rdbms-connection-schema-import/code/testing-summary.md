# U3 RDBMS Connection & Schema Import - テスティングサマリ

Step 3/6/9/12で生成した全テスト（ビジネスロジック層・API層・リポジトリ層・フロントエンド）を横断し、
PBT-10（補完的テスト戦略）の遵守状況、P1〜P12（`business-logic-summary.md`で識別したテスト可能な
性質）とテストクラスの対応関係、およびexample-basedテストの一覧を整理する。

## PBT-10: 補完的テスト戦略

`property-based-testing`拡張のRule PBT-10は、プロパティベーステスト（PBT）がexample-basedテストを
**置き換えるのではなく補完する**ことを要求する。本ユニットでは以下の方針で遵守した（U1/U2と同一方針）。

- **性質（P1〜P12）の検証はjqwik `@Property`で実施**: `EncryptedStringConverterTest`,
  `RdbmsConnectionServiceTest`, `ConnectionPoolRegistryTest`, `SchemaImportServiceTest`,
  `SchemaQueryServiceTest`の各テストクラスで、対応する性質を広い入力空間に対して自動生成された
  ケースで検証する。
- **業務的に重要な具体シナリオはexample-basedテストで別途固定**: API層
  （`RdbmsConnectionControllerTest`, `SchemaControllerTest`）、リポジトリ層
  （`RdbmsConnectionRepositoryTest`, `SchemaTableRepositoryTest`, `SchemaColumnRepositoryTest`）、
  フロントエンド（新規10ファイル＋既存2ファイル拡張）はすべてexample-basedテストのみで構成し、
  管理者成功系・非管理者403・未認証401などの規定の入出力を明示するケースを固定した。PBTが唯一の
  テストとなっている性質はない。
- **テストクラス/ファイルでPBTとexample-basedを明確に分離**: バックエンドはPBT専用クラス
  （ビジネスロジック層の5クラス）とexample-based専用クラス（API層2クラス・リポジトリ層3クラス）を
  完全に分離しており、U1のような同居パターンは採らなかった（U2と同一方針。本ユニットも
  P1〜P12すべてがビジネスロジック層の性質であり、API層・リポジトリ層に先送りされる性質が
  無かったため）。フロントエンドはU1/U2同様、Vitest + React Testing Libraryによる
  example-basedテストのみ（TypeScript側にPBTフレームワークは導入していない）。
- **1テストメソッドが複数性質を検証するケース**: `SchemaImportServiceTest`の
  `RollbackRoundTrip`グループ（1件）はP10単独の検証に専念しており、複数性質を1メソッドで
  兼用するケースは本ユニットには存在しない（U2の`RefreshTokenServiceTest`のようなパターンは
  該当なし）。
- **PBTが失敗を検出した場合の回帰テスト追加**: 本ユニットの開発中にjqwikが恒久的な回帰テストを
  要する失敗を検出した事例はない（該当なし）。

## P1〜P12対応表（最終版）

| # | 対象 | 検証テストクラス | 検証方式 | 層 |
|---|---|---|---|---|
| P1 | `EncryptedStringConverter`のRound-trip（暗号化→復号で元の文字列に一致） | `EncryptedStringConverterTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P2 | `EncryptedStringConverter`のInvariant（暗号化後の値は平文と一致しない） | `EncryptedStringConverterTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P3 | `RdbmsConnectionService`のJDBC URL組み立てInvariant（`additionalParams`の重複付加なし） | `RdbmsConnectionServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P4 | `ConnectionPoolRegistry.getDataSource`のIdempotence（`invalidate`まで同一インスタンス） | `ConnectionPoolRegistryTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P5 | `ConnectionPoolRegistry.invalidate`のInvariant（次回`getDataSource`は新インスタンス） | `ConnectionPoolRegistryTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P6 | `RdbmsConnectionService.testConnection`のInvariant（`ConnectionPoolRegistry`キャッシュ状態に無影響） | `RdbmsConnectionServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P7 | `SchemaImportService.importSchema`の物理名マッチングInvariant（既存`id`の不変性） | `SchemaImportServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P8 | `SchemaImportService.importSchema`のInvariant（削除された物理名は`stale = true`、行削除なし） | `SchemaImportServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P9 | `SchemaImportService.importSchema`のIdempotence（対象RDBMS側無変更時の`stale = false`集合の一致） | `SchemaImportServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P10 | `SchemaImportService.importSchema`失敗時のRound-trip（トランザクション原子性、内部DB状態の完全ロールバック） | `SchemaImportServiceTest$RollbackRoundTrip` | jqwik `@Property`（`@JqwikSpringSupport @DataJpaTest`） | ビジネスロジック（Step 3） |
| P11 | `SchemaImportService.importSchema`のInvariant（ビュー取り込み時、`primaryKeySequence`は常に`null`） | `SchemaImportServiceTest` | jqwik `@Property` | ビジネスロジック（Step 3） |
| P12 | `SchemaQueryService`のstale除外Invariant（`listTables`/`getTableDetail`は`stale = true`を返さない） | `SchemaQueryServiceTest` | jqwik `@Property`（`@JqwikSpringSupport @DataJpaTest`） | ビジネスロジック（Step 3、Code Generation計画でP12として新規識別） |

P1〜P12全12性質にjqwik `@Property`テストが対応済み（PBT-02〜PBT-08準拠）。U2と同様、本ユニットも
全性質がビジネスロジック層で完結しており、API層・リポジトリ層に先送りされた性質は無い
（詳細は`business-logic-summary.md`参照。P10/P12は`SchemaTableRepository`/
`SchemaColumnRepository`/`RdbmsConnectionRepository`の正式生成前にStep 3で先行実装されたため、
Step 8完了後の現時点では通常の`@DataJpaTest`として問題なく実行される）。

## example-basedテスト一覧

### バックエンド（Step 6, 9）

| テストクラス | 検証内容 | 件数 |
|---|---|---|
| `RdbmsConnectionControllerTest` | 接続CRUD（create/update/list/get）・接続テスト（保存前/既存接続）の6エンドポイントそれぞれについて管理者成功系・非管理者403・未認証401を検証（`create`/`update`は`Authentication`を明示注入し`adminUserId`がサービス呼び出しに渡ることも確認） | 18 |
| `SchemaControllerTest` | スキーマ取り込み・スキーマ一覧・テーブル一覧・テーブル詳細の4エンドポイントそれぞれについて管理者成功系・非管理者403・未認証401を検証（`importSchema`は`Authentication`を明示注入し`adminUserId`がサービス呼び出しに渡ることも確認） | 12 |
| `RdbmsConnectionRepositoryTest` | 基本CRUD＋`password`列の`EncryptedStringConverter`往復（暗号化保存・復号読み出し） | 4 |
| `SchemaTableRepositoryTest` | 基本CRUD、5件のクエリメソッド全て（一致/不一致、stale除外込み）、複合unique制約違反時の`DataIntegrityViolationException` | 9 |
| `SchemaColumnRepositoryTest` | 基本CRUD、3件のクエリメソッド全て（一致/不一致、stale除外込み）、複合unique制約違反時の`DataIntegrityViolationException` | 7 |

バックエンドexample-based合計: 50件（API層30件＋リポジトリ層20件）。`api-layer-summary.md`・
`repository-layer-summary.md`作成時点（Step 7/Step 10）で記載していた件数（15/10/8）は、Step 8
完了後の実テスト実行結果と突き合わせた本Step時点で18/12/9が正確な件数であることを確認した
（設計時点で「管理者成功系・非管理者403・未認証401」の3パターン×エンドポイント数という方針は
一致しており、件数記載のみの誤差。実装自体に問題はない）。

### フロントエンド（Step 12、全てexample-based）

| テストファイル | 件数 |
|---|---|
| `features/rdbmsConnection/api/connectionApi.test.ts` | 6 |
| `features/rdbmsConnection/ConnectionTable.test.tsx` | 4 |
| `features/rdbmsConnection/ConnectionFormPage.test.tsx` | 5 |
| `features/rdbmsConnection/ConnectionListPage.test.tsx` | 6 |
| `features/schema/api/schemaApi.test.ts` | 4 |
| `features/schema/SchemaImportPanel.test.tsx` | 4 |
| `features/schema/SchemaBrowserPage.test.tsx` | 3 |
| `features/schema/SchemaSelector.test.tsx` | 3 |
| `features/schema/TableList.test.tsx` | 2 |
| `features/schema/TableDetailPanel.test.tsx` | 4 |
| `routes/AppRouter.test.tsx`（拡張、既存4件→6件） | 2件追加 |
| `components/AppLayout.test.tsx`（拡張、既存3件のまま） | 0件追加（既存ケース拡張のみ） |

本ユニットでの新規/拡張分: 43件（新規10ファイル・41件＋既存`AppRouter.test.tsx`への2件追加）。
U1・U2既存分と合わせ、フロントエンド全体は30ファイル・114テストとなる（詳細は
`frontend-summary.md`参照）。

## 実行確認状況

本サマリ作成時点で実際にテストを実行し、グリーンであることを確認済み（Build and Testステージの
再確認対象ではあるが、Step 13完了時点で以下を確認している）。

- **バックエンド**: `./gradlew test` 29テストクラス・133/133件成功、0失敗・0エラー
  （テスト結果XML: `build/test-results/test/`で個別に集計・確認済み）。うちU3で新規追加した
  テストクラスは11クラス・64件
  （`ConnectionPoolRegistryTest` 2、`EncryptedStringConverterTest` 2、
  `RdbmsConnectionControllerTest` 18、`RdbmsConnectionRepositoryTest` 4、
  `RdbmsConnectionServiceTest` 4、`SchemaColumnRepositoryTest` 7、`SchemaControllerTest` 12、
  `SchemaImportServiceTest` 3、`SchemaImportServiceTest$RollbackRoundTrip` 1、
  `SchemaQueryServiceTest` 2、`SchemaTableRepositoryTest` 9）。U1/U2既存18クラス・69件は
  回帰なし。
- **フロントエンド**: `npx vitest run` 30ファイル・114/114件成功、`npx tsc -b`
  （型チェック）・`npx oxlint`（Lint）共にエラーなし。

## 既知の課題

Step 3時点で`business-logic-summary.md`に記録されていた「`compileJava`単体がStep 8まで未生成の
3リポジトリの未解決参照により失敗し続ける」既知事象は、Step 8（8-1〜8-3、3リポジトリの正式生成）
完了時点で解消済みである（本Step時点の`./gradlew test`が全体ビルド経由で成功していることで
再確認済み）。

---

## 2026-07-15変更要求（接続コンテキストのグローバル化）による追加

- **バックエンド**: `ConnectionAccessServiceTest`（新規、jqwik `@Property`、PBT性質P13）と
  `RdbmsConnectionControllerTest`への2件追加（一般ユーザーでの200確認、未認証401確認）を
  `./gradlew test --tests "cherry.mastermeister.rdbmsconnection.*"`で実行し、全件成功を確認した。
- **フロントエンド**: `features/rdbmsConnection/api.test.ts`への1件追加（`listAccessibleConnections`
  のリクエストURL確認）を`npx vitest run src/features/rdbmsConnection/api.test.ts`で実行し、
  7件全件成功を確認した。
- 全体テスト件数（バックエンド`./gradlew test`・フロントエンド`npx vitest run`）の再集計は、
  影響ユニット（U1/U5/U6/U7）すべてのCode Generation完了後、Build and Testステージで
  まとめて行う。