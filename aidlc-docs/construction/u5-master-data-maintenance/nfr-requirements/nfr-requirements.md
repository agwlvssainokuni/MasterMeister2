# nfr-requirements.md — U5: Master Data Maintenance

`u5-master-data-maintenance-nfr-requirements-plan.md`（Q1〜Q4）の回答に基づく非機能要件。
4問すべて回答Aのため、質問本文の推奨案どおりに確定する。

---

## 1. Performance/Scalability Requirements

### 1.1 `listRecords`のページング（Q1）

- `AuditLogService`（U1）と同じ「既定ページサイズ＋選択肢リストによる検証」方式を踏襲する。
  設定キー`mm.app.master-data.default-page-size`（既定値`50`）・
  `mm.app.master-data.page-size-options`（既定値`50,100,200`）を新設する。
- 検証ロジックは`AuditLogService.resolvePageSize`と同様：リクエストされたページサイズが
  `page-size-options`のリストに存在しない場合は`default-page-size`へフォールバックする。
- 選択肢の最大値（`200`）は`business-rules.md` 2.5の大量データ閲覧監査閾値（既定`100`）を
  上回るため、閾値超過が構造的に発生しうる状態を維持する（監査ログのトリガー条件が
  形骸化しない）。

### 1.2 単一`applyChanges`リクエストあたりの最大件数（Q4）

- 設定キー`mm.app.master-data.max-mutation-batch-size`（既定値`500`）を新設する。
- `creates.size() + updates.size() + deletes.size()`がこれを超える場合、対象RDBMSへの
  問い合わせを一切行わず`ValidationException`でリクエスト全体を拒否する。
- 検証タイミングは`business-rules.md` 3.1のall-or-nothing事前検証（権限チェック・型検証等）
  と同じフェーズで実施し、SQL実行前に完結させる。

---

## 2. Reliability Requirements

### 2.1 対象RDBMSへのクエリ実行タイムアウト（Q2）

- `NamedParameterJdbcTemplate`（内部的には`JdbcOperations.setQueryTimeout(int)`、単位は秒）に
  クエリタイムアウトを設定する。
- 設定キー`mm.app.master-data.query-timeout`（既定値`30`秒）を新設し、`listRecords`
  （SELECT）・`applyChanges`（INSERT/UPDATE/DELETE）の両方に一律適用する。UIモード・RAWモード
  で区別しない。
- `ConnectionPoolRegistry.getJdbcTemplate`が返す`NamedParameterJdbcTemplate`は呼び出しの都度
  生成されるインスタンスであるため、`masterdata`パッケージ側で`setQueryTimeout`を呼び出しても
  他ユニット（U3/U4/U6/U7）の利用箇所には影響しない。
- U3で確立した`connection-timeout`（既定5秒、コネクション取得までの待機時間）とは独立した
  別の設定値であり、両者を混同しない。本設定はコネクション取得後のSQL文実行時間を制限する。

### 2.2 `applyChanges`の実行方式（Q3）

- `creates`/`updates`/`deletes`それぞれを`NamedParameterJdbcTemplate.update()`で1件ずつ実行する
  単純なループ方式を採用する。JDBCバッチ更新（`batchUpdate`）は不採用。
- 理由：`RecordUpdate.changedValues`はFunctional Design Q2で確定した疎な変更表現であり、
  行ごとに異なるカラム集合（＝異なるSQL文）になりうるため、同一SQL文の繰り返し実行を前提と
  する`batchUpdate`とは本質的に相性が悪い。
- 性能面は1.2で確定した`max-mutation-batch-size`（既定`500`）により1リクエストあたりの件数を
  上限内に収めることで担保する。件数がこの規模であれば個別実行ループでも実用上の性能問題は
  生じないと判断する。
- 個別実行ループはいずれも`business-rules.md` 3.3で確定した`ConnectionPoolRegistry.
  getTransactionTemplate(connectionId)`が返す`TransactionTemplate`の`execute`コールバック内で
  実行し、単一トランザクションとしての原子性を維持する。

---

## 3. Scalability/Availability/Security/Usability/Maintainability Requirements

本ユニット固有の新規論点は見当たらない。U1〜U4のNFR Requirements・Functional Designで確立した
「小規模内部利用が前提」「単一ノードデプロイ」という判断方針をそのまま適用する。
`EffectivePermissionResolver`のキャッシュ方式（U4確定）も本ユニットでそのまま再利用し、新規の
キャッシュ機構は導入しない。

---

## 4. PBT Compliance（property-based-testing拡張）

- 本ステージ（NFR Requirements）で新たに適用されるPBTルールはない（U4 NFR Requirementsの
  先例を踏襲）。PBT-01（Property Identification）はFunctional Design段階で適用済み
  （`business-logic-model.md` P1〜P10）。PBT-02以降はCode Generation Planning/Code
  Generation/Build and Testステージで適用される。