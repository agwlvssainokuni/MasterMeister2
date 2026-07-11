# NFR Requirements Plan — U5: Master Data Maintenance

## ユニット適用可否の判定

`construction/nfr-requirements.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`）に
基づき、U5は **実行（EXECUTE）** と判定。

- **Performance/Scalability**: `listRecords`のページングの既定/許容ページサイズが未確定
  （`business-rules.md` 2.4）。2.5の大量データ閲覧監査閾値（既定100）との整合も検討が必要。
- **Reliability/Security**: 対象RDBMSへのSQL実行タイムアウトが未確定。U3で確立した
  `connection-timeout`（既定5秒、`ConnectionPoolRegistry`）はコネクション取得までの
  タイムアウトであり、コネクション取得後のSQL文実行時間（特にRAWモードで一般ユーザが
  任意入力するWHERE/ORDER BY句、`business-rules.md` 2.3）には及ばない。限られた
  コネクションプール（既定最大5）を長時間実行クエリが占有するリスクがある。
- **Tech Stack/Performance**: `applyChanges`の実行方式（個別実行ループ vs JDBCバッチ更新）が
  未確定。`RecordUpdate.changedValues`は行ごとに異なるカラム集合を持ちうる疎な表現
  （Functional Design Q2）であり、バッチ更新との相性を検討する必要がある。
- **Reliability/Scalability**: 単一`applyChanges`リクエストあたりの最大件数（上限）が未確定。
- **Availability/Usability/Maintainability**: 本ユニット固有の新規論点は見当たらない
  （U1〜U4のNFR Requirements・Functional Designで確立した「小規模内部利用が前提」
  「単一ノードデプロイ」という判断方針を踏襲する）。

→ 上記の中から4問を構成する。

---

## Step 1: Functional Design成果物の分析

- [x] `domain-entities.md`（`TableSummary`/`ColumnMetadata`/`RecordListResult`/
      `FilterCriteria`/`MutationRequest`/`MutationResult`等）確認
- [x] `business-rules.md`（1.1-1.2 アクセス可能テーブル一覧、2.1-2.5 レコード一覧取得・
      ページング・大量データ監査閾値、3.1-3.4 レコード変更・トランザクション制御方式・
      `ConnectionPoolRegistry.getTransactionTemplate()`、API認可）確認
- [x] `business-logic-model.md`（フロー1-3、Testable Properties P1-P10）確認
- [x] `frontend-components.md`（`features/masterData/`のコンポーネント構成、
      `DataTable`拡張、U7との将来的な共通化申し送り事項）確認
- [x] U1 `nfr-requirements.md`/`tech-stack-decisions.md`（HikariCP既定設定、
      `AuditLogService`のページサイズ検証方式）、U3 `nfr-requirements.md`
      （接続タイムアウト5秒の先例）、U4 `nfr-requirements.md`（キャッシュ・イベント方式、
      本ユニットでは`EffectivePermissionResolver`の既存キャッシュをそのまま利用するため
      重複質問は設けない）を前提として参照し、重複質問を避ける

---

## 成果物生成タスク（`construction/nfr-requirements.md` Step 6）

- [ ] `aidlc-docs/construction/u5-master-data-maintenance/nfr-requirements/nfr-requirements.md`
- [ ] `aidlc-docs/construction/u5-master-data-maintenance/nfr-requirements/tech-stack-decisions.md`

---

## Question 1: Performance/Scalability — ページングの既定ページサイズ・許容ページサイズ

`business-rules.md` 2.4はページング処理自体を`DialectStrategy`で吸収するとしているが、
既定ページサイズ・上限が未確定。2.5の大量データ閲覧監査閾値（既定`100`）との整合も
必要（許容ページサイズの上限が閾値未満だと、閾値超過が構造的に発生しえなくなる）。

A. `AuditLogService`（U1）と同じ「既定ページサイズ＋選択肢リストによる検証」方式を踏襲する。
   設定キー`mm.app.master-data.default-page-size`（既定値`50`）・
   `mm.app.master-data.page-size-options`（既定値`50,100,200`）を新設し、
   `AuditLogService.resolvePageSize`と同様のロジック（リストにない値は既定値へフォールバック）
   で検証する。選択肢の最大値（`200`）は大量データ監査閾値（既定`100`）を上回るため、
   閾値超過が構造的に発生しうる（推奨）

B. ページサイズは任意の正の整数を許容し、上限のみ設定（`mm.app.master-data.max-page-size`、
   既定値`200`）でガードする。選択肢リストによる離散検証は行わない

C. ページサイズを固定値（例: `50`）とし、クライアントからの指定を許可しない

D. その他（具体的な方針を指定）

[Answer]: A

---

## Question 2: Reliability/Security — 対象RDBMSへのクエリ実行タイムアウト

U3のNFR Requirementsで確立した`connection-timeout`（既定5秒）はコネクション取得までの
タイムアウトであり、コネクション取得後のSQL文実行時間（特にRAWモードで一般ユーザが
任意入力するWHERE/ORDER BY句、`business-rules.md` 2.3）には及ばない。長時間実行クエリが
限られたコネクションプール（既定最大`5`、U3確定）を占有し続けるリスクがある。

A. `NamedParameterJdbcTemplate`にクエリタイムアウトを設定する
   （`JdbcOperations.setQueryTimeout(int)`、単位は秒）。設定キー
   `mm.app.master-data.query-timeout`（既定値`30`秒）を新設し、`listRecords`（SELECT）・
   `applyChanges`（INSERT/UPDATE/DELETE）の両方に一律適用する。`ConnectionPoolRegistry.
   getJdbcTemplate`が返す`NamedParameterJdbcTemplate`は都度生成されるインスタンスのため、
   `masterdata`側で`setQueryTimeout`を呼んでも他ユニットの利用に影響しない（推奨）

B. タイムアウトを設定せず、対象RDBMSのドライバ既定動作（多くの場合無期限）に委ねる

C. UIモード・RAWモードで異なるタイムアウト値を設定する（RAWモードはより短く制限する）

D. その他（具体的な方針を指定）

[Answer]: A

---

## Question 3: Tech Stack/Performance — applyChangesの実行方式（個別実行 vs バッチ実行）

`MutationRequest`内の`RecordUpdate.changedValues`は行ごとに異なるカラム集合を持ちうる
（Functional Design Q2で確定した疎な変更表現）。JDBCバッチ更新（`batchUpdate`）は本来
同一SQL文・同一パラメータ構造の繰り返し実行を前提とするため、行ごとに異なるSQL文に
なりうる本設計とは相性が悪い。

A. `creates`/`updates`/`deletes`それぞれを`NamedParameterJdbcTemplate.update()`で1件ずつ
   実行する単純なループ方式を採用する。行ごとに異なるSQL文になりうる設計と自然に整合し、
   実装も単純。1リクエストあたりの件数は少数（Question 4で上限を設定）を前提とし、
   性能上の問題は生じないと判断する（推奨）

B. 変更カラム集合が完全に一致する行同士をグループ化し、グループ単位で`batchUpdate`を使う
   （実装が複雑化するが、大量の均一な更新がある場合に有利）

C. `RecordUpdate.changedValues`を全カラム含む形式に強制変換し（未変更カラムは元の値で
   埋める）、常に同一SQL文でのバッチ更新を可能にする（Functional Designで確定した疎な
   変更表現の設計方針と矛盾するため非推奨だが選択肢として提示）

D. その他（具体的な方針を指定）

[Answer]: A

---

## Question 4: Reliability/Scalability — 単一applyChangesリクエストあたりの最大件数

`MutationRequest`（`creates`+`updates`+`deletes`合計）の件数に上限がない場合、極端に
大きなリクエストが単一トランザクションを長時間占有し、他ユーザーの操作をブロックしたり、
メモリ使用量が増大するリスクがある。

A. 設定キー`mm.app.master-data.max-mutation-batch-size`（既定値`500`）を新設し、
   `creates.size() + updates.size() + deletes.size()`がこれを超える場合は対象RDBMSへの
   問い合わせを行わず`ValidationException`でリクエスト全体を拒否する（`business-rules.md`
   3.1のall-or-nothing検証と同じタイミングで実施）（推奨）

B. 上限を設けない（フロントエンド側の`pendingChanges`蓄積量が自然に小規模にとどまることを
   前提とする）

C. フロントエンド側でのみ上限を課し、バックエンドでは検証しない

D. その他（具体的な方針を指定）

[Answer]: A

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。