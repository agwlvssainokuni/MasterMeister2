# U5 Master Data Maintenance - テスティングサマリ

Step 3/6/12で生成した全テスト（ビジネスロジック層・API層・フロントエンド）を横断し、
PBT-10（補完的テスト戦略）の遵守状況、P1〜P10（`business-logic-summary.md`で識別したテスト可能な
性質）とテストクラスの対応関係、およびexample-basedテストの一覧を整理する。本ユニットは
`domain-entities.md`確定（Q1 = A）のとおり内部DBエンティティを一切持たないため、U1〜U4に
あったStep 8/9（リポジトリ層生成・単体テスト）は存在せず、`repository-layer-summary.md`も
生成しない（完了基準どおり）。

## PBT-10: 補完的テスト戦略

`property-based-testing`拡張のRule PBT-10は、プロパティベーステスト（PBT）がexample-basedテストを
**置き換えるのではなく補完する**ことを要求する。本ユニットでは以下の方針で遵守した（U1〜U4と
同一方針）。

- **性質（P1〜P10）の検証はjqwik `@Property`で実施**: `MasterDataQueryServiceTest`・
  `MasterDataMutationServiceTest`の2テストクラスで、対応する性質を広い入力空間に対して
  自動生成されたケースで検証する。
- **業務的に重要な具体シナリオはexample-basedテストで別途固定**: API層
  （`MasterDataControllerTest`）、フロントエンド（新規4ファイル）はすべてexample-based
  テストのみで構成し、認証済みユーザ成功系・未認証401などの規定の入出力を明示するケースを
  固定した。PBTが唯一のテストとなっている性質はない。
- **テストクラス/ファイルでPBTとexample-basedを明確に分離**: バックエンドはPBT専用クラス
  （ビジネスロジック層の2クラス）とexample-based専用クラス（API層1クラス）を完全に
  分離しており、U1のような同居パターンは採らなかった（U2〜U4と同一方針。本ユニットも
  P1〜P10すべてがビジネスロジック層の性質であり、API層に先送りされる性質が無かったため）。
  フロントエンドはU1〜U4同様、Vitest + React Testing Libraryによるexample-basedテストのみ
  （TypeScript側にPBTフレームワークは導入していない）。
- **1テストメソッドが複数性質を検証するケース**: `MasterDataQueryServiceTest`の
  `listRecordsExcludesNonePermissionColumnsAndAlignsRowsToColumns`がP1（SELECT列決定
  Invariant）とP2（行の構造整合性Invariant）を同一テストで検証している（U2
  `RefreshTokenServiceTest`と同様のパターン）。
- **PBTが失敗を検出した場合の回帰テスト追加**: 本ユニットの開発中にjqwikが恒久的な回帰テストを
  要する失敗を検出した事例はない（該当なし）。

## P1〜P10対応表（最終版）

| # | 対象 | 検証テストクラス | 検証方式 | 層 |
|---|---|---|---|---|
| P1 | `listRecords`のSELECT列決定Invariant（`RecordListResult.columns`に`NONE`権限のカラムが含まれない） | `MasterDataQueryServiceTest`（`listRecordsExcludesNonePermissionColumnsAndAlignsRowsToColumns`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P2 | `RecordListResult`の構造整合性Invariant（各行の要素数・位置対応が`columns`と一致） | `MasterDataQueryServiceTest`（同上、P1と同一テストで検証） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P3 | `listRecords`のUIモード条件検証Invariant（READ未満カラム参照時は常に例外） | `MasterDataQueryServiceTest`（`listRecordsRejectsUiReferenceToNonePermissionColumn`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P4 | `listRecords`のRAWモード簡易防御Invariant（セミコロン含有時は常に例外） | `MasterDataQueryServiceTest`（`listRecordsRejectsRawCriteriaContainingSemicolon`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P5 | `listRecords`の大量データ監査Invariant（`large-record-threshold`境界値） | `MasterDataQueryServiceTest`（`listRecordsRecordsLargeRecordAuditAtThresholdBoundary`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P6 | `applyChanges`の権限検証all-or-nothing Invariant | `MasterDataMutationServiceTest`（`applyChangesRejectsAllOrNothingWhenAnyOperationFailsPermission`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P7 | `applyChanges`の主キーなしテーブル`RecordUpdate`拒否Invariant | `MasterDataMutationServiceTest`（`applyChangesRejectsRecordUpdateOnTableWithoutPrimaryKey`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P8 | `applyChanges`の主キーなしテーブル`RecordDelete`拒否Invariant（U4 `canDelete`常時falseとの連携） | `MasterDataMutationServiceTest`（`applyChangesRejectsRecordDeleteOnTableWithoutPrimaryKey`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P9 | `applyChanges`のトランザクション原子性Invariant（`SQLException`発生時は呼び出し前状態と完全一致） | `MasterDataMutationServiceTest`（`applyChangesRollsBackAllChangesWhenSqlExceptionOccurs`） | jqwik `@Property` | ビジネスロジック（Step 3） |
| P10 | `applyChanges`成功時の反映結果Invariant（`creates`/`updates`/`deletes`が過不足なく反映） | `MasterDataMutationServiceTest`（`applyChangesReflectsCreatesUpdatesDeletesExactlyOnSuccess`） | jqwik `@Property` | ビジネスロジック（Step 3） |

P1〜P10全10性質にjqwik `@Property`テストが対応済み（PBT-02〜PBT-08準拠）。U2〜U4と同様、本ユニットも
全性質がビジネスロジック層で完結しており、API層に先送りされた性質は無い（詳細は
`business-logic-summary.md`参照）。両テストクラスとも`org.h2.tools.Server`によるH2 TCPサーバを
対象RDBMS役として実接続する構成（U3 `SchemaImportServiceTest`踏襲）を用いており、
`SchemaQueryService`/`EffectivePermissionResolver`/`AuditLogService`は全テストでMockitoモックと
することで、`MasterDataQueryService`/`MasterDataMutationService`自身のSQL構築・権限検証統合
ロジックのみを対象RDBMSに対する実SQL実行を通じて検証している。

## example-basedテスト一覧

### バックエンド（Step 6）

| テストクラス | 検証内容 | 件数 |
|---|---|---|
| `MasterDataControllerTest` | 5エンドポイント（`listAccessibleConnections`/`listAccessibleSchemas`/`listAccessibleTables`/`listRecords`/`applyChanges`）それぞれについて認証済みユーザ成功系・未認証401を検証（本ユニットは管理者ロール制約を持たないため403系テストは対象外） | 10 |

本ユニットはリポジトリ層が存在しない（内部DBエンティティを持たないため対象外、Step 8相当は
N/A）ため、バックエンドexample-based合計はAPI層の10件のみ。`api-layer-summary.md`（Step 7）
作成時点の記載と、本Step時点の実テスト実行結果を突き合わせ、件数が正確であることを確認した。

### フロントエンド（Step 12、全てexample-based）

| テストファイル | 件数 |
|---|---|
| `features/masterData/SchemaTableListPage.test.tsx` | 4 |
| `features/masterData/FilterPanel.test.tsx` | 8 |
| `features/masterData/RecordListPage.test.tsx` | 12 |
| `features/masterData/MutationResultDialog.test.tsx` | 3 |
| `routes/AppRouter.test.tsx`・`components/AppLayout.test.tsx`（ブラウンフィールド修正、既存分のまま） | 0件追加（回帰確認のみ、既存12件） |

本ユニットでの新規分: 27件（新規4ファイル）。U1〜U4既存分と合わせ、フロントエンド全体は
44ファイル・183テストとなる（詳細は`frontend-summary.md`参照）。

## 実行確認状況

本サマリ作成時点で実際にテストを実行し、グリーンであることを確認済み（Build and Testステージの
再確認対象ではあるが、Step 15完了時点で以下を確認している）。

- **バックエンド**: `./gradlew test` 43テストクラス・233/233件成功、0失敗・0エラー
  （テスト結果XML: `build/test-results/test/`で個別に集計・確認済み）。うちU5で新規追加した
  テストクラスは3クラス・19件
  （`MasterDataQueryServiceTest` 4、`MasterDataMutationServiceTest` 5、
  `MasterDataControllerTest` 10）。U1〜U4既存40クラス・214件は回帰なし。
  初回実行時、バックグラウンドで起動中だった`./gradlew bootRun`プロセスがファイルベースH2DB
  （`backend/data/mastermeister.mv.db`）をロックしていたため`MasterMeisterApplicationTests`の
  ApplicationContext起動が失敗し、同一コンテキスト構成を使う`PermissionCacheConsistencyTest`・
  `SchemaReimportCacheConsistencyTest`（いずれもU4既存、`@SpringBootTest`）へ
  「ApplicationContext failure threshold exceeded」として連鎖する事象が一時的に発生した
  （U5のコード変更に起因するものではない）。ユーザ確認の上で当該`bootRun`プロセスを停止し
  再実行したところ全件成功した。
- **フロントエンド**: `npx vitest run` 44ファイル・183/183件成功、`npx tsc -b`
  （型チェック）・`npm run lint`（oxlint）共にエラー・警告なし。

## 既知の課題

なし。本ユニットは内部DBエンティティを持たずStep 8相当の未解決参照が発生しないため、
`./gradlew compileJava`/`compileTestJava`はStep 2・Step 3のいずれの時点でも成功しており、
両ビジネスロジックテストクラスも独立して実行・成功することを都度確認済み
（`business-logic-summary.md`参照）。上記「実行確認状況」に記載したbootRunプロセスとの
H2ファイルロック競合は環境要因であり、コード上の既知課題ではない。
