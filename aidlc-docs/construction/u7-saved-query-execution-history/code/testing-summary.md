# U7 Saved Query / Execution / History - テスティングサマリ

Step 3/6/9/12で生成した全テスト（ビジネスロジック層・API層・リポジトリ層・フロントエンド）を
横断し、PBT-10（補完的テスト戦略）の遵守状況、P1〜P10（`business-logic-summary.md`で識別した
テスト可能な性質）とテストクラスの対応関係、およびexample-basedテストの一覧を整理する。

## PBT-10: 補完的テスト戦略

`property-based-testing`拡張のRule PBT-10は、プロパティベーステスト（PBT）がexample-basedテストを
**置き換えるのではなく補完する**ことを要求する。本ユニットでは以下の方針で遵守した（U1〜U6と
同一方針）。

- **性質（P1〜P10）の検証はjqwik `@Property`で実施**: `SavedQueryServiceTest`・
  `ReadOnlySqlValidatorTest`・`SqlParamDetectorTest`・`QueryExecutionServiceTest`・
  `QueryHistoryServiceTest`の5テストクラスで、対応する性質を広い入力空間に対して自動生成
  されたケースで検証する。
- **業務的に重要な具体シナリオはexample-basedテストで別途固定**: API層
  （`SavedQueryControllerTest`、`QueryExecutionControllerTest`、`QueryHistoryControllerTest`）、
  リポジトリ層（`SavedQueryRepositoryTest`、`QueryHistoryRepositoryTest`）、フロントエンド
  （新規5ファイル＋既存`QueryBuilderPage.test.tsx`拡張）はすべてexample-basedテストのみで
  構成し、認証済みユーザ成功系・未認証401・読み取り専用SQL違反400などの規定の入出力を明示する
  ケースを固定した。PBTが唯一のテストとなっている性質はない。
- **テストクラス/ファイルでPBTとexample-basedを明確に分離**: バックエンドはPBT専用クラス
  （ビジネスロジック層の5クラス）とexample-based専用クラス（API層3クラス・リポジトリ層2クラス）
  を完全に分離しており、U1のような同居パターンは採らなかった（U2〜U6と同一方針）。本ユニットも
  P1〜P10すべてがビジネスロジック層の性質であり、API層・リポジトリ層に先送りされる性質は
  無かった。フロントエンドはU1〜U6同様、Vitest + React Testing Libraryによるexample-basedテスト
  のみ（TypeScript側にPBTフレームワークは導入していない）。
- **1テストメソッドが複数性質を検証するケース**: `QueryHistoryServiceTest`の
  `listHistoryMasksAndBadgesIndependentlyPerRow`がP9（マスキングInvariant）とP10（「廃止済み」
  バッジの独立性Invariant）を同一テストで検証している（U5
  `listRecordsExcludesNonePermissionColumnsAndAlignsRowsToColumns`と同様のパターン）。
- **PBTが失敗を検出した場合の回帰テスト追加**: Step 3-2で`ReadOnlySqlValidatorTest`のP4
  テスト作成中、`"SELECT * FROM tbl; DROP TABLE tbl"`というスタックドクエリ形式の入力が
  `CCJSqlParserUtil.parse()`では例外にならず`Select`型として通過してしまう実装バグを
  プロパティテストが検出した（`CCJSqlParserUtil.parseStatements()`への変更で解消、詳細は
  `business-logic-summary.md`）。当該プロパティテストは恒久的な回帰テストとしてそのまま残る。

## P1〜P10対応表（最終版）

| # | 対象 | 検証テストクラス | 検証方式 | 層 |
|---|---|---|---|---|
| P1 | `listQueries`の可視性フィルタInvariant（PUBLIC or 自分の所有のみ） | `SavedQueryServiceTest`（`listQueriesReturnsOnlyPublicOrOwnQueries`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P2 | `retired`状態遷移Invariant（`getExecutableQuery`/`updateQuery`は常に拒否、`getQuery`は可視性のみで決まる） | `SavedQueryServiceTest`（`retiredQueryAlwaysRejectsExecutionAndUpdateRegardlessOfVisibility`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P3 | `retireQuery`の一方向性Invariant | `SavedQueryServiceTest`（`retireQueryNeverReversesOnSubsequentOperations`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P4 | 読み取り専用SQL検証Invariant（SELECT文1件のみ許可、スタックドクエリ拒否） | `ReadOnlySqlValidatorTest`（`validateRejectsNonSelectOrUnparsableSql`/`validateAcceptsSelectSql`/`validateRejectsSqlExceedingMaxLength`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P5 | パラメータ自動検出Invariant（文字列リテラル外の`:paramName`集合との一致） | `SqlParamDetectorTest`（`detectMatchesPlaceholdersOutsideStringLiterals`ほか） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P6 | ページング適用時の件数上限Invariant（有効時pageSize以下／無効時max-result-rows以下＋truncated境界） | `QueryExecutionServiceTest`（`executeAdhocSqlWithPagingNeverExceedsPageSize`/`executeAdhocSqlWithoutPagingTruncatesAtMaxResultRowsBoundary`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P7 | 実行のたびの二重記録Invariant（QueryHistory・AuditLog各1件） | `QueryExecutionServiceTest`（`executeAdhocSqlAlwaysRecordsHistoryAndAuditExactlyOnce`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P8 | `executionCount`のインクリメント整合性Invariant | `QueryExecutionServiceTest`（`executeSavedQueryIncrementsExecutionCountConsistently`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P9 | 実行履歴のマスキングInvariant | `QueryHistoryServiceTest`（`listHistoryMasksAndBadgesIndependentlyPerRow`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P10 | 「廃止済み」バッジの独立性Invariant | `QueryHistoryServiceTest`（`listHistoryMasksAndBadgesIndependentlyPerRow`、同一テストでP9と独立に検証） | jqwik `@Property` | ビジネスロジック（Step 3） |

P1〜P10全10性質にjqwik `@Property`テストが対応済み（PBT-02〜PBT-08準拠）。U2〜U6と同様、本
ユニットも全性質がビジネスロジック層で完結しており、API層・リポジトリ層に先送りされた性質は
無い（詳細は`business-logic-summary.md`参照）。`savedquery`/`queryhistory`は内部DB（JPA）のみで
完結するため`@DataJpaTest`（組み込みH2）で足りる一方、`queryexecution`（`QueryExecutionService`）
は対象RDBMSへの実アクセスを伴うためU3〜U6と同じ`org.h2.tools.Server`（H2 TCPサーバ）手法を用いる。

## example-basedテスト一覧

### バックエンド（Step 6, 9）

| テストクラス | 検証内容 | 件数 |
|---|---|---|
| `SavedQueryControllerTest` | 5エンドポイント（`listQueries`/`saveQuery`/`getQuery`/`updateQuery`/`retireQuery`）それぞれについて認証済みユーザ成功系・未認証401を検証（本ユニットは管理者ロール制約を持たないため403系テストは対象外） | 10 |
| `QueryExecutionControllerTest` | 2エンドポイント（`executeAdhocSql`/`executeSavedQuery`）の認証済みユーザ成功系・未認証401に加え、読み取り専用SQL違反時の`ValidationException`→400を検証 | 5 |
| `QueryHistoryControllerTest` | `listHistory`エンドポイントの認証済みユーザ成功系・未認証401を検証 | 2 |
| `SavedQueryRepositoryTest` | 基本CRUD、`findVisible`（可視性/所有者/`retired`条件の組み合わせ）、`incrementExecutionCount`の並行実行整合性（20スレッド同時呼び出しで加算漏れが発生しないこと） | 5 |
| `QueryHistoryRepositoryTest` | 基本CRUD、`params`の`JsonMapConverter`往復、`search`（日時範囲・実行者・SQL部分一致・大文字小文字非依存・ページング） | 7 |

バックエンドexample-based合計: 29件（API層17件＋リポジトリ層12件）。`api-layer-summary.md`・
`repository-layer-summary.md`作成時点（Step 7/Step 10）の記載と、Step 9完了後の実テスト実行
結果を突き合わせ、上表の件数が正確であることを確認した。

### フロントエンド（Step 12、全てexample-based）

| テストファイル | 件数 |
|---|---|
| `features/savedQuery/SavedQueryListPage.test.tsx` | 5 |
| `features/savedQuery/SavedQuerySaveForm.test.tsx` | 4 |
| `features/savedQuery/SavedQueryDetailPage.test.tsx` | 5 |
| `features/queryExecution/QueryExecutionPage.test.tsx` | 6 |
| `features/queryHistory/QueryHistoryListPage.test.tsx` | 8 |
| `features/queryBuilder/QueryBuilderPage.test.tsx`（既存、ブラウンフィールド修正） | 2件追加（既存6件＋新規2件＝計8件） |

本ユニットでの新規/拡張分: 30件（新規5ファイル・28件＋既存`QueryBuilderPage.test.tsx`への2件
追加）。U1〜U6既存分と合わせ、フロントエンド全体は57ファイル・254テストとなる（詳細は
`frontend-summary.md`参照）。

## 実行確認状況

本サマリ作成時点で実際にテストを実行し、グリーンであることを確認済み（Build and Testステージの
再確認対象ではあるが、Step 15完了時点で以下を確認している）。

- **バックエンド**: `./gradlew test`（JUnit XML集計） 58テストクラス・299/299件成功、
  0失敗・0エラー。うちU7で新規追加したテストクラスは10クラス・43件
  （`SavedQueryServiceTest` 3、`ReadOnlySqlValidatorTest` 3、`SqlParamDetectorTest` 3、
  `QueryExecutionServiceTest` 4、`QueryHistoryServiceTest` 1、`SavedQueryControllerTest` 10、
  `QueryExecutionControllerTest` 5、`QueryHistoryControllerTest` 2、
  `SavedQueryRepositoryTest` 5、`QueryHistoryRepositoryTest` 7）。U1〜U6既存48クラス・256件は
  回帰なし。
- **フロントエンド**: `npx vitest run` 57ファイル・254/254件成功、`tsc -b`（型チェック）・
  `npm run lint`（oxlint）共にエラー・警告なし。

## 既知の課題

なし。Step 3で発見・修正した3件の実装バグ（`JsonMapConverter`のDI設計、`QueryHistoryRepository.
search`のCLOB関数引数型エラー、`ReadOnlySqlValidator`のスタックドクエリ検知漏れ）はいずれも
`business-logic-summary.md`に記録済みで、本Step時点で`./gradlew test`が全体ビルド経由で成功
していることにより解消済みであることを再確認した。

## 変更要求（2026-07-15）: 接続コンテキストのグローバル化 + クエリ実行時スキーマ指定

P1〜P10に加え、新規性質P11（スキーマ許可リスト検証Invariant）を追加した。

| # | 対象 | 検証テストクラス | 検証方式 | 層 |
|---|---|---|---|---|
| P11 | スキーマ許可リスト検証Invariant（`listAccessibleSchemas`に含まれない`schema`指定は常に`PermissionDeniedException`となり実行・履歴記録は一切発生しない） | `QueryExecutionServiceTest`（`executeAdhocSqlRejectsInaccessibleSchemaWithoutRecordingHistory`） | jqwik `@Example` | ビジネスロジック |

`QueryExecutionServiceTest`にはP11の他、SET文の実効性検証（SCHEMA_BASED方言でのスキーマ非修飾
テーブル参照の解決）・SET文の非発行検証（CATALOG_BASED方言でのMockitoベース検証）の2つの
example-basedテストを`@Example`で追加した。いずれも`@JqwikSpringSupport`クラスにおいて
`@BeforeContainer`（jqwikのみのライフサイクル）が素のJUnit `@Test`には適用されないため、
`@Example`（jqwikの単発テストアノテーション）を用いる必要がある——これを誤って`@Test`で
実装し、静的フィールド未初期化のNullPointerExceptionを起こした後に修正した経緯がある
（`business-logic-summary.md`参照）。

`QueryExecutionServiceTest`は4件→7件、`QueryExecutionControllerTest`は5件→7件
（新規スキーマ一覧エンドポイントの成功系・未認証401）に拡張した。バックエンド全体は
**303件、全テスト成功**（`./gradlew test`）。フロントエンドは`QueryExecutionPage.test.tsx`が
6件→9件に拡張し、`SavedQueryListPage.test.tsx`・`SavedQuerySaveForm.test.tsx`・
`SavedQueryDetailPage.test.tsx`・`QueryHistoryListPage.test.tsx`もグローバル接続コンテキスト・
スキーマ引き継ぎの検証を追加する形で改訂した。フロントエンド全体は**59ファイル・276件、
全テスト成功**（`npx vitest run`）、`tsc -b`・`npm run lint`（oxlint）もエラー・警告なし。