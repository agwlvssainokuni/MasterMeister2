# U6 Query Builder - テスティングサマリ

Step 3/6/12で生成した全テスト（ビジネスロジック層・API層・フロントエンド）を横断し、
PBT-10（補完的テスト戦略）の遵守状況、P1〜P10（`business-logic-summary.md`で識別したテスト可能な
性質）とテストクラスの対応関係、およびexample-basedテストの一覧を整理する。本ユニットは
`domain-entities.md`確定（Q1 = A）のとおり内部DBエンティティを一切持たないため、U1〜U5に
あったStep 8/9（リポジトリ層生成・単体テスト）は存在せず、`repository-layer-summary.md`も
生成しない（完了基準どおり）。

## PBT-10: 補完的テスト戦略

`property-based-testing`拡張のRule PBT-10は、プロパティベーステスト（PBT）がexample-basedテストを
**置き換えるのではなく補完する**ことを要求する。本ユニットでは以下の方針で遵守した（U1〜U5と
同一方針）。

- **性質（P1〜P10）の検証はjqwik `@Property`で実施**: `QueryBuilderMetadataServiceTest`・
  `SqlGenerationServiceTest`・`SqlParsingServiceTest`・`QueryBuilderRoundTripTest`の4テスト
  クラスで、対応する性質を広い入力空間に対して自動生成されたケースで検証する。
- **業務的に重要な具体シナリオはexample-basedテストで別途固定**: API層
  （`QueryBuilderControllerTest`）、フロントエンド（新規8ファイル）はすべてexample-based
  テストのみで構成し、認証済みユーザ成功系・未認証401などの規定の入出力を明示するケースを
  固定した。PBTが唯一のテストとなっている性質はない。
- **テストクラス/ファイルでPBTとexample-basedを明確に分離**: バックエンドはPBT専用クラス
  （ビジネスロジック層の4クラス）とexample-based専用クラス（API層1クラス）を完全に分離して
  おり、U2〜U5と同一方針を踏襲した。フロントエンドはU1〜U5同様、Vitest + React Testing
  Libraryによるexample-basedテストのみ（TypeScript側にPBTフレームワークは導入していない）。
- **P8（generate→parseラウンドトリップ）を独立クラスに分離**: `SqlGenerationService`と
  `SqlParsingService`の両方を利用する横断的な性質のため、単一責務の`SqlParsingServiceTest`とは
  別に`QueryBuilderRoundTripTest`として独立させた（Step 3-4実装時の判断、
  `business-logic-summary.md`参照）。
- **PBTが失敗を検出した場合の回帰テスト追加**: Step 3-3で`parseRejectsUnsupportedSyntax`
  （P7）がUNION文の`ClassCastException`という実装バグを検出し、Step 3-4で
  `generateThenParseRoundTripsToEquivalentModel`（P8）がクオート識別子の未対応という実装バグを
  検出した（いずれも`SqlParsingService.java`の生産コード修正で対応、詳細は
  `business-logic-summary.md`）。両プロパティテストは恒久的な回帰テストとしてそのまま残る。

## P1〜P10対応表（最終版）

| # | 対象 | 検証テストクラス | 検証方式 | 層 |
|---|---|---|---|---|
| P1 | `listSelectableColumns`のREAD未満カラム除外Invariant | `QueryBuilderMetadataServiceTest`（`listSelectableColumnsExcludesBelowReadPermissionColumns`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P2 | `generate`のJOIN句キーワード限定Invariant（`INNER/LEFT/RIGHT`のみ、`FULL`不出現） | `SqlGenerationServiceTest`（`generateJoinKeywordIsAlwaysRestrictedSet`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P3 | `generate`のGROUP BY制約違反Invariant（`ValidationException`） | `SqlGenerationServiceTest`（`generateRejectsNonAggregatedColumnMissingFromGroupBy`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P4 | `generate`のWHERE/HAVING句AND限定Invariant | `SqlGenerationServiceTest`（`generateBuildsWhereAndHavingAsAndOnlyConjunction`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P5 | `generate`のプレースホルダ/paramsキー一致Invariant | `SqlGenerationServiceTest`（`generateKeepsPlaceholdersAndParamsKeysInSync`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P6 | `generate`の識別子クオートInvariant（4方言） | `SqlGenerationServiceTest`（`generateAlwaysQuotesIdentifiers`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P7 | `parse`の非対応構文検出Invariant | `SqlParsingServiceTest`（`parseRejectsUnsupportedSyntax`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P8 | `generate`→`parse`ラウンドトリップInvariant | `QueryBuilderRoundTripTest`（`generateThenParseRoundTripsToEquivalentModel`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P9 | `parse`の権限フィルタInvariant | `SqlParsingServiceTest`（`parseRejectsWhenReferencedTableOrColumnLacksReadPermission`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P10 | `generate`のLIMIT OFFSET句有無Invariant | `SqlGenerationServiceTest`（`generateLimitOffsetClausePresenceMatchesNullability`） | jqwik `@Property` | ビジネスロジック（Step 3） |

P1〜P10全10性質にjqwik `@Property`テストが対応済み（PBT-02〜PBT-08準拠）。U2〜U5と同様、本ユニットも
全性質がビジネスロジック層で完結しており、API層に先送りされた性質は無い。U3〜U5と異なり、
本ユニットは`QueryBuilderMetadataService`/`SqlGenerationService`/`SqlParsingService`のいずれも
対象RDBMSへJDBC接続を直接開かない（メタデータは`SchemaQueryService`経由、SQL実行はU7の範囲）ため、
`org.h2.tools.Server`によるH2 TCPサーバは本ユニットでは不要と判断した（`EffectivePermissionResolver`/
`SchemaQueryService`/`RdbmsConnectionRepository`はいずれもMockitoモック、詳細は
`business-logic-summary.md`）。

## example-basedテスト一覧

### バックエンド（Step 6）

| テストクラス | 検証内容 | 件数 |
|---|---|---|
| `QueryBuilderControllerTest` | 6エンドポイント（`listSelectableConnections`/`listSelectableSchemas`/`listSelectableTables`/`listSelectableColumns`/`generate`/`parse`）それぞれについて認証済みユーザ成功系・未認証401を検証（本ユニットは管理者ロール制約を持たないため403系テストは対象外） | 12 |

本ユニットはリポジトリ層が存在しない（内部DBエンティティを持たないため対象外、Step 8相当は
N/A）ため、バックエンドexample-based合計はAPI層の12件のみ。`api-layer-summary.md`（Step 7）
作成時点の記載と、本Step時点の実テスト実行結果を突き合わせ、件数が正確であることを確認した。

### フロントエンド（Step 12、全てexample-based）

| テストファイル | 件数 |
|---|---|
| `features/queryBuilder/QueryBuilderPage.test.tsx` | 6 |
| `features/queryBuilder/FromJoinTab.test.tsx` | 6 |
| `features/queryBuilder/SelectTab.test.tsx` | 5 |
| `features/queryBuilder/WhereHavingTab.test.tsx` | 6 |
| `features/queryBuilder/GroupByOrderByTab.test.tsx` | 5 |
| `features/queryBuilder/LimitOffsetTab.test.tsx` | 4 |
| `features/queryBuilder/GeneratedSqlPanel.test.tsx` | 6 |
| `features/queryBuilder/SqlReverseParsePanel.test.tsx` | 3 |
| `routes/AppRouter.tsx`・`components/AppLayout.tsx`（ブラウンフィールド修正、既存分のまま） | 0件追加（回帰確認のみ、既存分） |

本ユニットでの新規分: 41件（新規8ファイル）。U1〜U5既存分と合わせ、フロントエンド全体は
52ファイル・224テストとなる（詳細は`frontend-summary.md`参照）。

## 実行確認状況

本サマリ作成時点で実際にテストを実行し、グリーンであることを確認済み（Build and Testステージの
再確認対象ではあるが、Step 15完了時点で以下を確認している）。

- **バックエンド**: `./gradlew test --rerun` 48テストクラス・255/255件成功、0失敗・0エラー
  （`build/test-results/test/`のJUnit XMLを集計して確認済み）。うちU6で新規追加した
  テストクラスは5クラス・22件
  （`QueryBuilderMetadataServiceTest` 1、`SqlGenerationServiceTest` 6、
  `SqlParsingServiceTest` 2、`QueryBuilderRoundTripTest` 1、`QueryBuilderControllerTest` 12）。
  U1〜U5既存43クラス・233件は回帰なし（jqwik `@Property`はメソッド単位で1件とカウントされる
  ため、`tries`指定数とは一致しない）。
- **フロントエンド**: `npm run test -- --run`（vitest） 52ファイル・224/224件成功、
  `npx tsc --noEmit`（型チェック）・`npm run lint`（oxlint）共にエラー・警告なし。

## 既知の課題

なし。本ユニットは内部DBエンティティを持たずStep 8相当の未解決参照が発生しないため、
`./gradlew compileJava`/`compileTestJava`はStep 2・Step 3のいずれの時点でも成功しており、
全4ビジネスロジックテストクラスも独立して実行・成功することを都度確認済み
（`business-logic-summary.md`参照）。Step 11-3着手時に発見した接続一覧API欠落（U5と同種の問題）は
item 2-9/5-4/6-2/7-2として解決済みであり、本Step時点で未解決の課題として残っていない。