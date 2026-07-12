# nfr-requirements.md — U6: Query Builder

`u6-query-builder-nfr-requirements-plan.md`（Q1〜Q4、Q4-2）の回答に基づく非機能要件。

---

## 1. Tech Stack/Security Requirements

### 1.1 JSqlParserのバージョン・ライセンス（Q1）

- JSqlParser（Apache License 2.0、Maven Central `com.github.jsqlparser:jsqlparser`）の最新
  安定版を`querybuilder`モジュールの依存関係として追加する。
- ライセンスはApache License 2.0であり、本プロジェクトのソースヘッダ規約（Apache License 2.0）
  と両立する。正確なバージョン番号はCode Generation時に確定する。

---

## 2. Reliability/Security Requirements

### 2.1 SQL解析APIの入力サイズ・処理時間ガード（Q2）

- 設定キー`mm.app.query-builder.parse-max-length`（既定値`10000`文字）を新設し、これを超える
  入力は`SqlParsingService.parse`呼び出し前に`ValidationException`で拒否する。
- JSqlParserの`CCJSqlParserUtil`によるパース処理に時間上限を設定する。設定キー
  `mm.app.query-builder.parse-timeout`（既定値`5`秒）を新設し、`ExecutorService`による
  タイムアウト制御を行う。
- 対象は`POST /api/query-builder/parse`（GEN-9、`business-rules.md` 8）。認証済みユーザからの
  任意のSQL文字列入力であっても、極端に長い/病的な構造による処理時間肥大化・リソース枯渇を
  防ぐ。

---

## 3. Performance Requirements

### 3.1 メタデータ選択API（スキーマ/テーブル/カラム一覧）のキャッシュ方針（Q3）

- `querybuilder`独自のキャッシュ層は新設しない。`SchemaQueryService`（U3既存、インポート済み
  スキーマメタデータをJPA経由で取得）・`EffectivePermissionResolver`（U4既存、権限判定結果を
  キャッシュ済み）の既存キャッシュにそのまま委譲する。
- 両者とも対象RDBMSへのライブ接続を伴わない内部DB参照であり、タブ切替のたびに繰り返し呼び
  出されても追加キャッシュによる複雑化のメリットが薄いと判断する。

---

## 4. Scalability/Reliability Requirements

### 4.1 QueryBuilderModelの各リスト件数上限（Q4=B, Q4-2=A）

リストごとに個別の上限値を設定する（SELECT句は多め、JOIN句は少なめとするユーザ要望を反映）。

| リスト | 設定キー | 既定値 |
|---|---|---|
| `selectItems` | `mm.app.query-builder.max-select-items` | `100` |
| `joinItems` | `mm.app.query-builder.max-join-items` | `10` |
| `whereConditions` | `mm.app.query-builder.max-where-conditions` | `30` |
| `groupByColumns` | `mm.app.query-builder.max-group-by-columns` | `30` |
| `havingConditions` | `mm.app.query-builder.max-having-conditions` | `20` |
| `orderByItems` | `mm.app.query-builder.max-order-by-items` | `20` |

いずれも`SqlGenerationService.generate`・`SqlParsingService.parse`（変換後モデル）の両方で
検証し、超過時は`ValidationException`で拒否する。SQL生成/実行前の事前検証として実施する
（U5の`max-mutation-batch-size`と同様の検証タイミング）。

---

## 5. Scalability/Availability/Usability/Maintainability Requirements

本ユニット固有の新規論点は見当たらない。U1〜U5のNFR Requirements・Functional Designで確立
した「小規模内部利用が前提」「単一ノードデプロイ」という判断方針をそのまま適用する。

U6はGEN-8/GEN-9いずれも対象RDBMSへの実行を伴わない（`business-rules.md` 7、実行はU7の責務）
ため、U3/U5で確立した「対象RDBMSへのクエリ実行タイムアウト」に相当する論点はU6には存在しない。

---

## 6. PBT Compliance（property-based-testing拡張）

本ステージ（NFR Requirements）で新たに適用されるPBTルールはない（U4/U5 NFR Requirementsの
先例を踏襲）。PBT-01（Property Identification）はFunctional Design段階で適用済み
（`business-logic-model.md` P1〜P10）。PBT-02以降はCode Generation Planning/Code
Generation/Build and Testステージで適用される。