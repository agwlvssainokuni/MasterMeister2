# NFR Design Plan — U5: Master Data Maintenance

## ユニット適用可否の判定

`construction/nfr-design.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`）に基づき、
U5は **実行（EXECUTE）** と判定。

- **Logical Components**: `MasterDataQueryService`/`MasterDataMutationService`のパッケージ配置・
  依存方向が未確定（U4 Q3の`group`/`permission`分割と同種の論点）。
- **Performance/Tech Stack**: `domain-entities.md`「設計判断」節で「対象RDBMS4種
  （MySQL/MariaDB/PostgreSQL/H2）間の型マッピング差異の吸収方式は、NFR Design/Code
  Generationで確定する」と明示的に先送りされている。`RecordListResult.records`の
  `List<Object>`各要素の型決定方式が未確定。
- **Reliability**: `mm.app.master-data.query-timeout`（NFR Requirements確定、既定30秒）は
  `Statement`単位（`JdbcTemplate.setQueryTimeout`はステートメント単位）で適用されるため、
  `applyChanges`のように1トランザクション内で複数ステートメントを発行する場合、
  トランザクション全体としての累積タイムアウト予算は存在しない点の扱いが未確定。
- **Scalability/Performance（インデックス）**: 該当なし。`domain-entities.md`確定済み
  （Q1 = A）のとおり本ユニットは内部DBエンティティを一切持たない
  （`SchemaQueryService`/`ConnectionPoolRegistry`/`EffectivePermissionResolver`/
  `AuditLogService`への都度委譲のみ）ため、新規インデックス設計は対象外。
- **Security**: 該当なし。RAWモードの安全性方針（セミコロンチェック等）は
  `business-rules.md` 2.3で既に確定済みであり、NFR Requirementsでも新たな論点は
  生じていない。
- **Resilience**: `resiliency-baseline`拡張は無効（`aidlc-state.md`）。対象RDBMSへの
  クエリ失敗・タイムアウトは`listRecords`/`applyChanges`という本ユニットの主機能そのものの
  結果であり、U3 `nfr-design-patterns.md` 6.（対象RDBMS接続失敗は副次的な失敗ではない）と
  同じ判断基準により、本ユニット固有の新規Resilience Patternは設けない（個別質問は設けない）。

→ 上記の中から3問を構成する。

---

## Step 1: NFR Requirements成果物の分析

- [x] `nfr-requirements.md`（1.1-1.2 ページング・最大変更件数、2.1-2.2 クエリタイムアウト・
      個別実行ループ、3. 他NFR領域は新規論点なし、4. PBT）確認
- [x] `tech-stack-decisions.md`（決定事項5件、依存関係追加なし）確認
- [x] `functional-design/domain-entities.md`（内部DBエンティティなし、
      `RecordListResult`/`FilterCriteria`/`MutationRequest`等の型カタログ、型マッピング
      先送り事項）確認
- [x] `functional-design/business-rules.md`（1.1-1.2 テーブル一覧、2.1-2.5 レコード一覧・
      権限フィルタ・RAWモード・ページング・大量データ監査、3.1-3.4 変更検証・
      トランザクション制御・監査、4. API認可）確認
- [x] `functional-design/business-logic-model.md`（P1〜P10）確認
- [x] `functional-design/frontend-components.md`確認
- [x] U4 `nfr-design-patterns.md`（Question 3のパッケージ配置判断基準——`common`拡張点 vs
      単一実装サービスの直接参照——を本ユニットの`MasterDataQueryService`/
      `MasterDataMutationService`にも適用できるか確認）、U3 `nfr-design-patterns.md`
      （Question 1のキャッシュ実装判断、6.のResilience判断基準）を前提として参照し、
      判断基準を揃える
- [x] 既存コード（`AuditLogService`の`@Value`フィールド注入パターン、
      `ConnectionPoolRegistry.getJdbcTemplate`の実装）を確認し、本プロジェクトで
      `@ConfigurationProperties`クラスの前例がなく、全て`@Value`によるコンストラクタ注入で
      統一されていることを確認（設定バインディング方式に関する新規質問は不要と判断）

---

## Step 5: 回答収集・確定

- [x] Q1 = A（`masterdata`パッケージへ集約、`common`切り出しなし）
- [x] Q2 = A（JDBC 4.2標準`getObject(int, Class)`による専用RowMapper、方言別分岐なし）
- [x] Q3 = A（ステートメント単位のタイムアウトのみ、累積予算は導入しない）

全回答に曖昧さなし（追加質問不要）。

## 成果物生成タスク（`construction/nfr-design.md` Step 6）

U1〜U4の先例（`logical-components.md`は独立ファイルとして作成せず、`nfr-design-patterns.md`内の
「Logical Components Patterns」節に統合する運用が一貫して踏襲されている）に倣い、本ユニットも
同様とする。

- [x] `aidlc-docs/construction/u5-master-data-maintenance/nfr-design/nfr-design-patterns.md`

---

## Question 1: Logical Components — `MasterDataQueryService`/`MasterDataMutationService`のパッケージ配置

`docs/PROJECT_STRUCTURE.md`は機能ごとにパッケージを分ける方針。U4 Q3では`EffectivePermissionResolver`
（U5/U6/U7から直接参照される単一実装サービス）を、複数ユニットが実装を追加する拡張ポイント
（`common.dialect`の`DialectStrategy`のような場合）ではないとして`permission`パッケージに残し
`common`へ切り出さなかった前例がある。`MasterDataQueryService`/`MasterDataMutationService`は
U6/U7からの参照は想定されない（U6のクエリビルダ・U7のクエリ実行はいずれも`SchemaQueryService`/
`ConnectionPoolRegistry`/`EffectivePermissionResolver`を直接使う設計であり、`masterdata`
パッケージ自体への依存は`unit-of-work-dependency.md`上想定されていない）が、配置方針を明示
しておきたい。

A. `MasterDataQueryService`・`MasterDataMutationService`および本ユニット固有のDTO
   （`domain-entities.md`の`TableSummary`/`ColumnMetadata`/`RecordListResult`/
   `FilterCriteria`/`RecordCreate`/`RecordUpdate`/`RecordDelete`/`MutationRequest`/
   `MutationResult`等）をすべて`cherry.mastermeister.masterdata`パッケージに配置する。
   依存方向は`masterdata → schema`（U3）・`masterdata → permission`（U4）・
   `masterdata → audit`（U1）の一方向のみ（`docs/PROJECT_STRUCTURE.md`の記載通り）。
   `common`への切り出しは行わない（U4 Q3と同じ判断基準：`masterdata`が所有する単一実装の
   サービスであり、複数ユニットが実装を追加する拡張ポイントではない）（推奨）
B. `RecordListResult`等の読み取り系DTOのみ`cherry.mastermeister.common`に切り出す
   （将来のU6/U7からの参照を見据えて）
C. その他（具体的な配置方針を指定）

[Answer]: A

---

## Question 2: Performance/Tech Stack — 対象RDBMS4種間の型マッピング差異の吸収方式

`domain-entities.md`「設計判断」節で明示的にNFR Designへ先送りされていた論点。
`RecordListResult.records`の各行は`ResultSetMetaData`由来の列と対になる`List<Object>`
（位置ベース）であり、CLAUDE.md規約により`java.time`型（`LocalDate`/`LocalDateTime`/
`OffsetDateTime`等、`java.sql.Date`/`Timestamp`等のレガシー型は不可）で値を保持する必要がある。
しかし`ResultSet.getObject(int)`（型引数なし）の戻り値の実際のJava型はJDBCドライバ実装依存
であり、MySQL/MariaDB/PostgreSQL/H2の4ドライバ間で一致する保証がない
（例：日時列が`java.sql.Timestamp`で返るドライバと`java.time.LocalDateTime`で返るドライバが
混在しうる）。

A. `DialectStrategy`（U1既存、`buildPagingClause`等と同じ拡張点）を拡張して
   ドライバ固有の型マッピングテーブルを4方言分individually実装するのではなく、JDBC 4.2で
   標準化された`ResultSet.getObject(int columnIndex, Class<T> type)`
   （型引数を明示して要求する標準API、4対象RDBMSのドライバは全てJDBC 4.2準拠でこの
   オーバーロードをサポート）を用いる専用`RowMapper`を`masterdata`パッケージ内に実装する。
   `ResultSetMetaData.getColumnType(int)`（`java.sql.Types`）を見て、日時系
   （`TIMESTAMP`→`LocalDateTime`、`DATE`→`LocalDate`、`TIME`→`LocalTime`、
   `TIMESTAMP_WITH_TIMEZONE`→`OffsetDateTime`）・数値系（`NUMERIC`/`DECIMAL`→`BigDecimal`）は
   `getObject(col, TargetClass.class)`で明示的に要求し、それ以外の型は`getObject(col)`
   （型引数なし）をそのまま用いる。ドライバ固有の分岐は不要（標準APIで移植性を担保する）（推奨）
B. `DialectStrategy`に方言ごとの型マッピングメソッドを追加し、4方言それぞれで個別に
   `java.sql.Types`→Javaクラスの対応表を持つ
C. 型変換は行わず、`ResultSet.getObject(int)`の結果をドライバ任せでそのまま返す
   （レガシー型が混入するリスクを許容する）
D. その他（具体的な吸収方式を指定）

[Answer]: A

---

## Question 3: Reliability — クエリタイムアウトの適用単位（ステートメント単位 vs トランザクション累積）

`mm.app.master-data.query-timeout`（NFR Requirements確定、既定30秒）は
`JdbcTemplate.setQueryTimeout(int)`によるステートメント単位のタイムアウトであり、
1回のSQL文実行に対してのみ適用される。`applyChanges`は`business-rules.md` 3.3で確定済みの
`ConnectionPoolRegistry.getTransactionTemplate(connectionId)`コールバック内で
`creates`/`updates`/`deletes`を個別実行ループ（NFR Requirements 2.2確定）で発行するため、
1トランザクションあたり最大`max-mutation-batch-size`（既定500）件のステートメントが
発行されうる。ステートメント単位のタイムアウトのみでは、理論上トランザクション全体の
所要時間に上限がない（各ステートメントが個別に30秒未満で完了し続ければ、合計時間は
無制限に伸びうる）。

A. ステートメント単位のタイムアウト（`query-timeout`を各`update()`/`query()`呼び出しの
   直前に取得した`NamedParameterJdbcTemplate`インスタンスへ設定する。`getJdbcTemplate`は
   呼び出しの都度新規インスタンスを返す〈NFR Requirements Q2確定〉ため、
   `masterdata`パッケージ側で`((JdbcTemplate) namedTemplate.getJdbcOperations())
   .setQueryTimeout(...)`を呼んでも他ユニットの利用箇所には影響しない）のみを採用し、
   トランザクション全体としての累積タイムアウト予算は導入しない。理由：
   `max-mutation-batch-size`（既定500）による件数上限と組み合わせれば、各ステートメントが
   異常に遅延しない限り実用上の問題は生じないと判断する。累積予算の実装
   （経過時間を計測し閾値超過でロールバックする等）は小規模内部利用が前提の本フェーズでは
   過剰な複雑化と判断する（推奨）
B. トランザクション全体の累積経過時間を計測し、`query-timeout`とは別の設定キー
   （例: `mm.app.master-data.mutation-transaction-timeout`）で上限を設け、超過時は
   残りのステートメント発行前にロールバックする
C. その他（具体的な適用単位・累積制御方針を指定）

[Answer]: A

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。