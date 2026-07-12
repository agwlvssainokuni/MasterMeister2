# NFR Requirements Plan — U6: Query Builder

## ユニット適用可否の判定

`construction/nfr-requirements.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`）に
基づき、U6は **実行（EXECUTE）** と判定。

- **Tech Stack/Security**: `business-rules.md` 6.1で導入を確定した外部SQLパーサ（JSqlParser）の
  具体的なバージョン・ライセンスが未確定。
- **Reliability/Security**: GEN-9のSQL解析API（`POST /api/query-builder/parse`）は認証済み
  ユーザからの任意のSQL文字列を受け付ける。悪意ある/病的な入力（極端に長い文字列、深い
  ネスト構造等）によるパーサの処理時間肥大化・リソース枯渇のリスクが未検討。
- **Performance**: `listSelectableSchemas`/`listSelectableTables`/`listSelectableColumns`
  （フロー1）はタブ切替のたびに繰り返し呼び出されうるが、`querybuilder`独自のキャッシュ層を
  設けるかどうかが未確定。U3/U4で確立済みのキャッシュ（`EffectivePermissionResolver`等）との
  重複を避けたい。
- **Scalability/Reliability**: `QueryBuilderModel`の各リスト（`selectItems`/`joinItems`/
  `whereConditions`/`groupByColumns`/`havingConditions`/`orderByItems`）の件数に上限がない場合、
  極端に大きなモデルがSQL生成処理時間・生成SQL文長を肥大化させるリスクがある。
- **Availability/Usability/Maintainability**: 本ユニット固有の新規論点は見当たらない
  （U1〜U5のNFR Requirementsで確立した「小規模内部利用が前提」「単一ノードデプロイ」という
  判断方針を踏襲する）。U6はGEN-8/GEN-9いずれも対象RDBMSへの実行を伴わない（`business-rules.md`
  7、実行はU7の責務）ため、U3/U5で確立した「対象RDBMSへのクエリ実行タイムアウト」に相当する
  論点はU6には存在しない。

→ 上記の中から4問を構成する。

---

## Step 1: Functional Design成果物の分析

- [x] `domain-entities.md`（`TableRef`/`ColumnRef`/`QueryBuilderModel`一式/`GeneratedSql`/
      `ParseResult`、内部DBエンティティなし）確認
- [x] `business-rules.md`（1.1-1.2 権限フィルタ、2.1 AND結合のみ、3.1 JOIN種別、
      4.1-4.2 集計関数/GROUP BY制約、5.1-5.2 SQL生成方式、6.1-6.2 SQL解析方式（JSqlParser）、
      7 GEN-8連携範囲、8 API認可）確認
- [x] `business-logic-model.md`（フロー1-3、Testable Properties P1-P10）確認
- [x] `frontend-components.md`（`features/queryBuilder/`のコンポーネント構成、api.ts、
      U7との連携申し送り事項）確認
- [x] U3 `nfr-requirements.md`（接続タイムアウト5秒の先例、`SchemaQueryService`の
      既存キャッシュ方式）、U5 `nfr-requirements.md`（クエリ実行タイムアウト30秒の先例——
      U6には適用対象なしと判断）を前提として参照し、重複質問を避ける

---

## 成果物生成タスク（`construction/nfr-requirements.md` Step 6）

- [ ] `aidlc-docs/construction/u6-query-builder/nfr-requirements/nfr-requirements.md`
- [ ] `aidlc-docs/construction/u6-query-builder/nfr-requirements/tech-stack-decisions.md`

---

## Question 1: Tech Stack/Security — JSqlParserのバージョン・ライセンス

`business-rules.md` 6.1でJSqlParser導入を確定済みだが、具体的なバージョン・ライセンスが未確定。

A. JSqlParser（Apache License 2.0、Maven Central `com.github.jsqlparser:jsqlparser`）の最新
   安定版を`querybuilder`モジュールの依存関係として追加する。ライセンスはApache License 2.0
   であり、本プロジェクトのソースヘッダ規約（Apache License 2.0）と両立する（推奨）

B. 古いバージョン（LGPL単独ライセンス期のもの）を明示的に固定して使用する

C. JSqlParser以外のSQLパーサライブラリ（例: Apache Calcite）へ変更する

D. その他（具体的な方針を指定）

[Answer]: A

---

## Question 2: Reliability/Security — SQL解析APIの入力サイズ・処理時間ガード

GEN-9のSQL解析API（`POST /api/query-builder/parse`）は認証済みユーザからの任意のSQL文字列を
受け付ける。極端に長い入力や病的な構造（深いネスト等）によるパーサの処理時間肥大化・
リソース枯渇のリスクがある。

A. 設定キー`mm.app.query-builder.parse-max-length`（既定値`10000`文字）を新設し、これを
   超える入力は`SqlParsingService.parse`呼び出し前に`ValidationException`で拒否する。加えて
   JSqlParserの`CCJSqlParserUtil`にパース処理時間の上限（`mm.app.query-builder.parse-timeout`、
   既定値`5`秒、`ExecutorService`によるタイムアウト制御）を設定する（推奨）

B. 入力サイズ・処理時間の制限は設けない（認証済みユーザのみが呼び出せるため、リスクは
   限定的と判断する）

C. 入力サイズ制限のみを設け、処理時間タイムアウトは設けない

D. その他（具体的な方針を指定）

[Answer]: A

---

## Question 3: Performance — メタデータ選択API（スキーマ/テーブル/カラム一覧）のキャッシュ方針

フロー1の`listSelectableSchemas`/`listSelectableTables`/`listSelectableColumns`はタブ切替の
たびに繰り返し呼び出されうる。`querybuilder`独自のキャッシュ層を新設するかどうかが未確定。

A. `querybuilder`独自のキャッシュ層は新設しない。`SchemaQueryService`（U3既存、インポート済み
   スキーマメタデータをJPA経由で取得）・`EffectivePermissionResolver`（U4既存、権限判定結果を
   キャッシュ済み）の既存キャッシュにそのまま委譲する。両者とも対象RDBMSへのライブ接続を
   伴わない内部DB参照であり、追加キャッシュによる複雑化のメリットが薄い（推奨）

B. `QueryBuilderMetadataService`に短TTL（例: 60秒）のインメモリキャッシュ層を新設し、
   同一スキーマ/テーブルへの繰り返し呼び出しをさらに削減する

C. フロントエンド側（`QueryBuilderPage`の状態）でのみキャッシュし、タブ間遷移では再取得しない

D. その他（具体的な方針を指定）

[Answer]: A

---

## Question 4: Scalability/Reliability — QueryBuilderModelの各リスト件数上限

`selectItems`/`joinItems`/`whereConditions`/`groupByColumns`/`havingConditions`/`orderByItems`
の件数に上限がない場合、極端に大きなモデルがSQL生成処理時間・生成SQL文長を肥大化させる
リスクがある。

A. 設定キー`mm.app.query-builder.max-items-per-list`（既定値`50`）を新設し、
   `SqlGenerationService.generate`・`SqlParsingService.parse`（変換後モデル）の両方で
   各リストの件数がこれを超える場合は`ValidationException`で拒否する。U5の
   `max-mutation-batch-size`と同様、SQL生成/実行前の事前検証として実施する（推奨）

B. リストごとに個別の上限値を設定する（例: `joinItems`は10件まで、`whereConditions`は
   30件まで等）

C. 上限を設けない（UI側のタブ操作で自然に小規模にとどまることを前提とする）

D. その他（具体的な方針を指定）

[Answer]: A

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。