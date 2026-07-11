# business-logic-model.md — U5: Master Data Maintenance

`domain-entities.md`・`business-rules.md`で定義したモデル・ルールに基づく業務フロー。
技術非依存（実装方式はNFR Design/Code Generationで確定）。

---

## フロー1: アクセス可能テーブル一覧の表示（MVP-10）

**関与コンポーネント**: フロントエンド`masterData/`（`SchemaTableListPage`） →
`MasterDataQueryService` → `EffectivePermissionResolver`（U4） → `SchemaQueryService`（U3）

1. 一般ユーザが`SchemaTableListPage`で対象接続を選択すると`listAccessibleSchemas(userId,
   connectionId)`が呼び出される（`business-rules.md` 1.1）。
2. スキーマ選択後`listAccessibleTables(userId, connectionId, schema)`が呼び出され、
   `EffectivePermissionResolver`によりNONE権限のテーブルが除外された一覧を取得する
   （1.1）。取得したテーブル名一覧に対し`SchemaQueryService.listTables`のメタデータと
   `canCreate`/`canDelete`判定を組み合わせ`TableSummary`一覧を組み立てる（1.2）。
3. `SchemaTableListPage`は`TableSummary`一覧を`DataTable`（U1）で表示し、行選択で
   `RecordListPage`へ遷移する（MVP-10 AC）。

---

## フロー2: レコード一覧の絞り込み・並び替え・閲覧（MVP-11, GEN-1, GEN-2）

**関与コンポーネント**: フロントエンド`masterData/`（`RecordListPage`, `FilterPanel`） →
`MasterDataQueryService` → `EffectivePermissionResolver`（U4） → `ConnectionPoolRegistry`
（U3） → `AuditLogService`（U1）

1. `RecordListPage`は`FilterPanel`でUIモード/RAWモードを切り替えられる（Q3, Q8）。UI
   モードでは`UiCondition`/`UiSort`をプルダウン等で組み立て、RAWモードでは`rawWhere`/
   `rawOrderBy`をテキスト入力する。
2. 「検索」操作で`listRecords(userId, connectionId, schema, table, criteria, page)`が
   呼び出される。`MasterDataQueryService`は`resolveEffectiveColumnPermissions`で
   NONE権限カラムを除いたSELECT列を決定し（`business-rules.md` 2.1）、UIモードの場合は
   `uiConditions`/`uiSorts`のカラムがREAD以上であることを検証する（2.2）。RAWモードの
   場合はこの検証をスキップしつつ、複数ステートメント注入の簡易チェックのみ行う（2.3）。
3. `ConnectionPoolRegistry.getJdbcTemplate(connectionId)`経由でSELECT文を発行し
   （`DialectStrategy`でページング・方言差異を吸収、2.4）、結果を`RecordListResult`
   （`columns` + `records`）として返す。
4. 1ページあたりの件数が`mm.app.master-data.large-record-threshold`以上の場合、
   `AuditLogService.record(DATA_ACCESS, LARGE_RECORD_READ, ...)`を呼び出す（2.5、MVP-11
   AC）。
5. `RecordListPage`は`RecordListResult`を`DataTable`（U1、拡張版）へ渡してページング付きで
   表示する（U7の結果表示と共通の`columns`+`records`構造のため、`DataTable`側の描画ロジック
   は将来U7とも再利用可能、Q2申し送り事項）。

---

## フロー3: レコードの編集・作成・削除と単一トランザクション反映（GEN-3, GEN-4, GEN-5）

**関与コンポーネント**: フロントエンド`masterData/`（`RecordListPage`,
`MutationResultDialog`） → `MasterDataMutationService` → `EffectivePermissionResolver`
（U4） → `SchemaQueryService`（U3） → `ConnectionPoolRegistry`（U3） → `AuditLogService`
（U1）

1. `RecordListPage`はフロー2で取得した`RecordListResult.columns`の`effectivePermission`に
   基づき、`UPDATE`権限を持つカラムのみインライン編集可能なセルとして表示する（GEN-3 AC、
   Q8「読み取り専用カラムは編集不可の表示」）。`canCreate`/`canDelete`が`true`の場合のみ
   「新規行追加」ボタン・行選択チェックボックス（削除用）を表示する（GEN-5 AC、Q8）。
2. ユーザが行った編集・新規行・削除選択はフロントエンド側で差分として保持され、「反映」
   ボタン押下時に`MutationRequest`（`creates`/`updates`/`deletes`）へ集約して送信する
   （GEN-4 AC「反映ボタンで作成/更新/削除すべてを単一APIエンドポイントへ送信」）。
3. `applyChanges(userId, connectionId, schema, table, request)`は`business-rules.md` 3.1の
   権限検証（`canCreate`/カラムUPDATE権限/`canDelete`）と3.2の主キー構成チェックを全操作に
   対して行う。いずれか1件でも失敗すれば対象RDBMSへの問い合わせを行わずリクエスト全体を
   拒否する（all-or-nothing、3.1）。
4. 検証を全て通過した場合のみ、単一トランザクション内で`creates`→`updates`→`deletes`を
   実行する（3.3）。`SQLException`発生時はロールバックし、`MutationResult.errorMessage`に
   概要メッセージのみを設定する（GEN-4 AC「一部失敗で全体ロールバック」）。
5. 成功・失敗いずれも`AuditLogService.record(DATA_ACCESS, MASTER_DATA_MUTATION, ...)`を
   呼び出す（3.4、GEN-4 AC「変更内容は監査ログに記録される」）。
6. `MutationResultDialog`が`MutationResult`を表示する。失敗時は`errorMessage`を表示し、
   フロントエンド側の編集差分は破棄しない（ユーザが修正して再送信できるようにする、詳細は
   Code Generationで確定）。

---

## テスト可能な性質（Testable Properties, PBT-01）

`property-based-testing`拡張（enabled）のRule PBT-01に基づき、本ユニットの業務ロジック
（フロー1〜3）が持つ性質をカテゴリ別に識別する。実際のPBTケース設計・生成器定義はCode
Generation計画時に確定する。

| # | 対象 | カテゴリ | 性質 | 備考 |
|---|---|---|---|---|
| P1 | `MasterDataQueryService.listRecords`のSELECT列決定（フロー2） | Invariant | `RecordListResult.columns`に`NONE`権限のカラムが含まれることはない（`resolveEffectiveColumnPermissions`の結果によらず常に成立） | `business-rules.md` 2.1、Q2の中核設計 |
| P2 | `RecordListResult`の構造整合性（フロー2） | Invariant | `records.content`の各行（`List<Object>`）の要素数は常に`columns`の要素数と一致し、位置`i`の値は`columns.get(i)`のカラムに対応する | Q2、列の食い違いが構造的に起こり得ない設計 |
| P3 | `listRecords`のUIモード条件検証（フロー2） | Invariant | `criteria.mode = UI`で`uiConditions`/`uiSorts`が参照するいずれかの`columnName`がREAD未満の場合、SELECT文は発行されず常に例外となる | `business-rules.md` 2.2、GEN-1 AC |
| P4 | `listRecords`のRAWモード簡易防御（フロー2） | Invariant | `criteria.mode = RAW`で`rawWhere`/`rawOrderBy`にセミコロンが含まれる場合、カラム権限（READ/UPDATE）の値によらず常に例外となる | `business-rules.md` 2.3 |
| P5 | `listRecords`の大量データ監査（フロー2） | Invariant（境界値） | 1ページあたりの返却件数が`large-record-threshold`ちょうどの場合を含めそれ以上であれば`LARGE_RECORD_READ`が必ず記録され、閾値未満であれば記録されない | `business-rules.md` 2.5、Q7 |
| P6 | `MasterDataMutationService.applyChanges`の権限検証all-or-nothing（フロー3） | Invariant | `MutationRequest`内のいずれか1件の操作が権限検証（`canCreate`/カラムUPDATE権限/`canDelete`）に失敗する場合、対象RDBMSの状態は呼び出し前後で一切変化しない（他の操作が全て有効であっても） | `business-rules.md` 3.1、Q5 |
| P7 | `applyChanges`の主キーなしテーブル`RecordUpdate`拒否（フロー3） | Invariant | 主キーを持たないテーブルへの`RecordUpdate`が`MutationRequest`に1件でも含まれる場合、対象カラムの権限値によらずリクエスト全体が拒否される | `business-rules.md` 3.2、Q6 |
| P8 | `applyChanges`の主キーなしテーブル`RecordDelete`拒否（フロー3） | Invariant | 主キーを持たないテーブルへの`RecordDelete`は、補助権限Dの値によらず常に拒否される（`canDelete`が常にfalseになるU4既存仕様に起因） | `business-rules.md` 3.2、U4 P9との連携 |
| P9 | `applyChanges`のトランザクション原子性（フロー3） | Invariant（状態遷移） | 検証を通過した`MutationRequest`の実行中に`SQLException`が1件でも発生した場合、対象RDBMSの状態は呼び出し前と完全に一致する（部分反映が残らない） | `business-rules.md` 3.3、GEN-4 AC |
| P10 | `applyChanges`成功時の反映結果（フロー3） | Invariant（状態遷移） | 検証・実行が両方成功した場合、対象RDBMSの状態は`creates`/`updates`/`deletes`の内容を過不足なく反映したものになる（`createdCount`/`updatedCount`/`deletedCount`が実際の行数と一致する） | `business-rules.md` 3.3 |