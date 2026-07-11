# business-logic-summary.md — U5: Master Data Maintenance

Step 2（ビジネスロジック生成）・Step 3（ビジネスロジック単体テスト）で生成したクラス一覧と、
`business-logic-model.md`のP1〜P10との対応関係。

## 生成クラス一覧（Step 2）

| パッケージ | クラス/インタフェース | 役割 |
|---|---|---|
| `rdbmsconnection` | `ConnectionPoolRegistry`（既存、ブラウンフィールド修正） | `TransactionTemplate getTransactionTemplate(Long connectionId)`を追加（`DataSourceTransactionManager`をラップしたインスタンスを都度生成、Spring管理Beanにはしない） |
| `masterdata` | `TableSummary`, `ColumnMetadata`, `RecordListResult`（record） | テーブル/カラム一覧・レコード検索結果の読み取り系DTO |
| `masterdata` | `FilterMode`, `Operator`（enum） | UI/RAWフィルタモード、UI条件の比較演算子 |
| `masterdata` | `UiCondition`, `UiSort`, `FilterCriteria`（record） | UIモード条件・並び替え・フィルタ全体を表す読み取り系DTO |
| `masterdata` | `RecordCreate`, `RecordUpdate`, `RecordDelete`（record） | 単一レコードの作成・更新・削除を表す更新系DTO |
| `masterdata` | `MutationRequest`, `MutationResult`（record） | 複数操作をまとめた単一トランザクション更新要求・結果DTO |
| `masterdata` | `RecordRowMapper`（`RowMapper<List<Object>>`） | `java.sql.Types`に基づくJDBC→Java型マッピング（`DATE`/`TIME`/`TIMESTAMP`/`TIMESTAMP_WITH_TIMEZONE`/`NUMERIC`/`DECIMAL`を`java.time`/`BigDecimal`へ明示変換） |
| `masterdata` | `MasterDataQueryService` | `listAccessibleSchemas`/`listAccessibleTables`/`listRecords`（SELECT列のNONE権限除外、UI/RAWモード検証、ページング、`large-record-threshold`監査記録） |
| `masterdata` | `MasterDataMutationService` | `applyChanges`（バッチサイズ上限検証→権限検証all-or-nothing→単一トランザクション内でINSERT/UPDATE/DELETEを逐次実行、成功・失敗いずれも監査記録） |

## 生成テストクラス一覧（Step 3）

| テストクラス | 検証方式 |
|---|---|
| `masterdata.MasterDataQueryServiceTest` | jqwik `@Property`（H2 TCPサーバを対象RDBMS役として実接続、`SchemaQueryService`/`EffectivePermissionResolver`/`AuditLogService`はMockitoモック） |
| `masterdata.MasterDataMutationServiceTest` | jqwik `@Property`（同上の構成、`ConnectionPoolRegistry`経由の実トランザクション実行を含む） |

## P1〜P10対応表

| # | 対象 | 検証テストクラス | 状態 |
|---|---|---|---|
| P1 | `listRecords`のSELECT列決定Invariant（`RecordListResult.columns`に`NONE`権限のカラムが含まれない） | `MasterDataQueryServiceTest`（`listRecordsExcludesNonePermissionColumnsAndAlignsRowsToColumns`） | 実装済み（Step 3-1） |
| P2 | `RecordListResult`の構造整合性Invariant（各行の要素数・位置対応が`columns`と一致） | `MasterDataQueryServiceTest`（同上、P1と同一テストで検証） | 実装済み（Step 3-1） |
| P3 | `listRecords`のUIモード条件検証Invariant（READ未満カラム参照時は常に例外） | `MasterDataQueryServiceTest`（`listRecordsRejectsUiReferenceToNonePermissionColumn`） | 実装済み（Step 3-2） |
| P4 | `listRecords`のRAWモード簡易防御Invariant（セミコロン含有時は常に例外） | `MasterDataQueryServiceTest`（`listRecordsRejectsRawCriteriaContainingSemicolon`） | 実装済み（Step 3-2） |
| P5 | `listRecords`の大量データ監査Invariant（`large-record-threshold`境界値） | `MasterDataQueryServiceTest`（`listRecordsRecordsLargeRecordAuditAtThresholdBoundary`） | 実装済み（Step 3-3） |
| P6 | `applyChanges`の権限検証all-or-nothing Invariant | `MasterDataMutationServiceTest`（`applyChangesRejectsAllOrNothingWhenAnyOperationFailsPermission`） | 実装済み（Step 3-4） |
| P7 | `applyChanges`の主キーなしテーブル`RecordUpdate`拒否Invariant | `MasterDataMutationServiceTest`（`applyChangesRejectsRecordUpdateOnTableWithoutPrimaryKey`） | 実装済み（Step 3-4） |
| P8 | `applyChanges`の主キーなしテーブル`RecordDelete`拒否Invariant（U4 `canDelete`常時falseとの連携） | `MasterDataMutationServiceTest`（`applyChangesRejectsRecordDeleteOnTableWithoutPrimaryKey`） | 実装済み（Step 3-4） |
| P9 | `applyChanges`のトランザクション原子性Invariant（`SQLException`発生時は呼び出し前状態と完全一致） | `MasterDataMutationServiceTest`（`applyChangesRollsBackAllChangesWhenSqlExceptionOccurs`） | 実装済み（Step 3-5） |
| P10 | `applyChanges`成功時の反映結果Invariant（`creates`/`updates`/`deletes`が過不足なく反映） | `MasterDataMutationServiceTest`（`applyChangesReflectsCreatesUpdatesDeletesExactlyOnSuccess`） | 実装済み（Step 3-5） |

**補足**: U5はU2/U3/U4と同様、P1〜P10すべてがStep 3で実装完了している。`masterdata`パッケージは
`domain-entities.md`確定（Q1 = A）のとおり内部DBエンティティを一切持たないため、U4のような
Step 8専用リポジトリスタブは存在しない。両テストクラスとも`org.h2.tools.Server`によるH2 TCP
サーバを対象RDBMS役として`@BeforeContainer`/`@AfterContainer`で起動・停止し、
`ConnectionPoolRegistry`経由で実接続する構成（U3 `SchemaImportServiceTest`踏襲）を用いており、
`SchemaQueryService`/`EffectivePermissionResolver`/`AuditLogService`は全テストでMockitoモックと
することで、`MasterDataQueryService`/`MasterDataMutationService`自身のSQL構築・権限検証統合
ロジックのみを対象RDBMSに対する実SQL実行を通じて検証している。

**Step 2完了時点で新規判明した設計要素**: item 2-1（`ConnectionPoolRegistry.getTransactionTemplate`
追加）は計画時点から「ブラウンフィールド発見事項」として識別済みであり、Step 2実施時の新規判明
事項はない。item 2-5（`MasterDataQueryService.listRecords`）では、`DialectStrategy.
getSchemaResolutionMode`によるCATALOG_BASED/SCHEMA_BASEDのテーブル修飾判定（U3
`SchemaImportService.resolveSchemaNames`と同じ基準の流用）とUIモード条件のNULL順序
（`NullsOrder.LAST`固定）をCode Generationレベルの詳細設計として追加した。item 2-6
（`MasterDataMutationService.applyChanges`）では、権限検証・バッチサイズ超過失敗は例外として
再送出し、実行時のDB由来失敗（`SQLException`）は`MutationResult(success=false, ...)`として
返却するという2種類の失敗表現の使い分けをCode Generationレベルの設計判断として追加した
（Step 5のAPI層でHTTPステータスへのマッピングを確定する）。

**既知の課題（Step 3スコープ外）**: なし。U4と異なり本ユニットは内部DBエンティティを持たず
Step 8（該当なし）を待つ未解決参照が発生しないため、`./gradlew compileJava`/`compileTestJava`は
Step 2・Step 3のいずれの時点でも成功しており、両テストクラスも独立して実行・成功することを
都度確認済み。