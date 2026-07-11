# nfr-design-patterns.md — U5: Master Data Maintenance

`u5-master-data-maintenance-nfr-design-plan.md`（Question 1〜3、全回答A）に基づく設計パターン。

---

## 1. Logical Components Patterns

### 1.1 主要コンポーネントのパッケージ配置（Question 1）

- `MasterDataQueryService`・`MasterDataMutationService`、および本ユニット固有のDTO
  （`domain-entities.md`確定の`TableSummary`/`ColumnMetadata`/`RecordListResult`/
  `FilterCriteria`/`UiCondition`/`UiSort`/`RecordCreate`/`RecordUpdate`/`RecordDelete`/
  `MutationRequest`/`MutationResult`等）は、すべて`cherry.mastermeister.masterdata`
  パッケージに配置する。
- **依存方向**: `masterdata → schema`（U3、`SchemaQueryService`によるメタデータ参照）・
  `masterdata → permission`（U4、`EffectivePermissionResolver`による実効権限判定）・
  `masterdata → audit`（U1、`AuditLogService`による監査記録）の一方向のみ
  （`docs/PROJECT_STRUCTURE.md`の記載通り）。逆方向の参照は生じない。
- **`common`への切り出しは行わない**。理由: U4 Q3（`EffectivePermissionResolver`の配置判断）
  と同じ判断基準を適用する——`masterdata`が所有する単一実装のサービスであり、`common.dialect`
  の`DialectStrategy`（複数ユニットが対等に実装を追加する拡張ポイント）とは事情が異なる。
  U6（Query Builder）・U7（Saved Query / Execution / History）はいずれも
  `unit-of-work-dependency.md`上`SchemaQueryService`/`ConnectionPoolRegistry`/
  `EffectivePermissionResolver`を直接参照する設計であり、`masterdata`パッケージ自体への
  依存は想定されていないため、将来の参照拡大を見越した`common`切り出しの必要性もない。

---

## 2. Performance Patterns

### 2.1 対象RDBMS4種間の型マッピング差異の吸収方式（Question 2）

- `RecordListResult.records`の各行を構築する専用`RowMapper`（`masterdata`パッケージ内、
  クラス名はCode Generationで確定）を実装する。方言（MySQL/MariaDB/PostgreSQL/H2）ごとの
  分岐は行わない。
- 各列について`ResultSetMetaData.getColumnType(int)`（`java.sql.Types`）を参照し、以下の
  マッピングルールに従って`ResultSet.getObject(int columnIndex, Class<T> type)`
  （JDBC 4.2標準API、型引数を明示して要求する）を呼び出す：
  - `Types.DATE` → `LocalDate.class`
  - `Types.TIME` / `Types.TIME_WITH_TIMEZONE` → `LocalTime.class`
  - `Types.TIMESTAMP` → `LocalDateTime.class`
  - `Types.TIMESTAMP_WITH_TIMEZONE` → `OffsetDateTime.class`
  - `Types.NUMERIC` / `Types.DECIMAL` → `BigDecimal.class`
  - 上記以外の型 → `getObject(columnIndex)`（型引数なし、ドライバの既定マッピングをそのまま
    使用。`String`/`Integer`/`Long`/`Boolean`等は4ドライバ間で実質的に差異がないため）
- 4対象RDBMSのJDBCドライバはいずれもJDBC 4.2準拠であり、上記オーバーロードを共通サポート
  するため、ドライバ固有の型変換テーブルを個別実装する必要がない（`DialectStrategy`の拡張は
  不要）。CLAUDE.md規約の`java.time`使用方針（`java.sql.Date`/`Timestamp`等のレガシー型
  不使用）をこの方式で満たす。
- `columns`（`ColumnMetadata`）側の`dataType`/`nullable`も同じ`ResultSetMetaData`から
  導出済み（`domain-entities.md`確定）であり、本パターンと同一の`ResultSetMetaData`走査で
  一貫して扱う。

### 2.2 クエリタイムアウトの適用単位（Question 3）

- `mm.app.master-data.query-timeout`（NFR Requirements確定、既定30秒）は
  **ステートメント単位**でのみ適用する。トランザクション全体としての累積タイムアウト予算は
  導入しない。
- 適用方法: `ConnectionPoolRegistry.getJdbcTemplate(connectionId)`から取得した
  `NamedParameterJdbcTemplate`インスタンスに対し、`masterdata`パッケージ側で
  `((JdbcTemplate) namedTemplate.getJdbcOperations()).setQueryTimeout(queryTimeoutSeconds)`
  を、当該インスタンスで最初にSQL文を実行する前に1回呼び出す。
- `getJdbcTemplate`は呼び出しの都度新規`NamedParameterJdbcTemplate`インスタンスを返す
  （NFR Requirements Question 2で確定済み）ため、この呼び出しは他ユニット（U3/U4/U6/U7）の
  利用箇所や、同じ`masterdata`パッケージ内の別リクエストには一切影響しない。
- `listRecords`は1リクエストにつき`getJdbcTemplate`を1回呼び出し1つのSELECT文を発行するため
  `setQueryTimeout`も1回。`applyChanges`は`getTransactionTemplate`コールバック内で
  `getJdbcTemplate`を1回呼び出し、同一インスタンスを`creates`/`updates`/`deletes`の個別実行
  ループ全体で再利用する（NFR Requirements 2.2確定の個別実行ループ方式）ため、
  `setQueryTimeout`もループ開始前に1回呼び出せば全ステートメントに適用される。
- 累積予算を導入しない理由: `mm.app.master-data.max-mutation-batch-size`（既定500、NFR
  Requirements確定）による1リクエストあたりの件数上限と組み合わせれば、各ステートメントが
  異常に遅延しない限り実用上の問題は生じないと判断する。経過時間計測によるロールバック等の
  累積制御は、小規模内部利用が前提の本フェーズでは実装コストに見合わない過剰な複雑化と判断
  する。

---

## 3. Scalability/Performance Patterns（インデックス）

- 該当なし。`domain-entities.md`確定済み（Q1 = A）のとおり、本ユニットは内部DBエンティティを
  一切持たない（`SchemaQueryService`/`ConnectionPoolRegistry`/`EffectivePermissionResolver`/
  `AuditLogService`への都度委譲のみで完結する）ため、新規インデックス設計は対象外
  （`u5-master-data-maintenance-nfr-design-plan.md`のユニット適用可否判定を参照）。

---

## 4. Security Patterns

- 該当なし。RAWモードの安全性方針（セミコロンによる複数ステートメント注入の簡易チェック、
  カラム権限フィルタの意図的な適用除外）は`business-rules.md` 2.3で既にFunctional Design段階
  で確定済みであり、NFR Requirements・NFR Designいずれの段階でも新たな論点は生じていない
  （`security-baseline`拡張は無効、オプトイン時に改めて要否確認する対象として
  `business-rules.md` 2.3に既に明記済み）。

---

## 5. Resilience Patterns

- 本ユニット固有の新規パターンはない（`u5-master-data-maintenance-nfr-design-plan.md`の
  ユニット適用可否判定を参照）。`resiliency-baseline`拡張は無効（`aidlc-state.md`）。
- 対象RDBMSへのクエリ失敗・タイムアウトは`listRecords`/`applyChanges`という本ユニットの
  主機能そのものの結果であり、U3 `nfr-design-patterns.md` 6.（対象RDBMS接続失敗は副次的な
  失敗ではなくU3の主機能そのものの結果）と同じ判断基準により、障害分離パターン
  （リトライ・サーキットブレーカ等）は導入しない。`applyChanges`の`SQLException`
  （`DataAccessException`）はトランザクションロールバック＋`MutationResult.errorMessage`
  としてそのまま呼び出し元へ返す設計（`business-rules.md` 3.3で確定済み）を踏襲する。

---

## 6. PBT適用性（property-based-testing拡張）

- 本ステージ（NFR Design）ではPBT-09（フレームワーク選定）を含むいずれのPBTルールも対象外
  （`property-based-testing.md`のEnforcement Integration表: NFR Designは対象外ステージ）。
  U1〜U4のNFR Design承認時と同様、N/Aとして扱う。