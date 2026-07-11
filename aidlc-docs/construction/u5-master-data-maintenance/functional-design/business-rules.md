# business-rules.md — U5: Master Data Maintenance

`u5-master-data-maintenance-functional-design-plan.md`の回答（Q1〜Q8）に基づく業務ルール定義。

---

## 1. アクセス可能テーブル一覧（MVP-10）

### 1.1 スキーマ/テーブルの権限フィルタ
`listAccessibleSchemas(userId, connectionId)`/`listAccessibleTables(userId, connectionId,
schema)`は`EffectivePermissionResolver.listAccessibleSchemas`/`listAccessibleTables`
（U4既存、`effectivePermission != NONE`のもののみ）をそのまま呼び出す。`masterdata`側で
追加のフィルタロジックは実装しない。

### 1.2 `TableSummary`の組み立て
`listAccessibleTables`が返すテーブル名一覧に対し、`SchemaQueryService.listTables`（U3）から
`tableType`/`comment`を、`EffectivePermissionResolver.resolveEffectiveTablePermission`/
`canCreate`/`canDelete`（U4）から権限情報を取得し、`domain-entities.md`の`TableSummary`へ
組み立てる（MVP-10 AC「Allow権限のあるテーブル/ビューのみ表示」）。

---

## 2. レコード一覧取得（MVP-11, GEN-1, GEN-2）

### 2.1 SELECT句のカラム権限フィルタ（Q2）
`listRecords`は`EffectivePermissionResolver.resolveEffectiveColumnPermissions(userId,
connectionId, schema, table)`を呼び出し、`NONE`権限のカラムを実際に発行するSELECT文の
選択列から**除外する**（UI表示の抑制だけでなく、権限のないデータを対象RDBMSから取得
しない）。対象テーブルの実効テーブル権限自体が`NONE`の場合は、SELECT文を発行せず
`PermissionDeniedException`とする（1.1のアクセス可能テーブル一覧に含まれないテーブルへの
直接API呼び出しに対する防御）。

### 2.2 UIモード条件の権限検証（Q3, GEN-1）
`criteria.mode = UI`の場合、`uiConditions`/`uiSorts`が参照する`columnName`が2.1で
`READ`以上と判定されたカラムに含まれることを検証する。含まれないカラムが1件でも指定
された場合はSELECT文を発行せず`PermissionDeniedException`とする（GEN-1 AC「読み取り権限
以上のカラムのみ選択可」）。複数条件はAND結合のみ（OR・括弧グルーピングはMVPスコープ外）。
複数カラムソートはリスト順を優先順位として`ORDER BY`に反映する。

### 2.3 RAWモードの安全性方針（Q4, GEN-2）
`criteria.mode = RAW`の場合、`rawWhere`/`rawOrderBy`は2.2のカラム権限検証の対象外とする
（GEN-2 AC「カラムレベル読み取り権限フィルタ対象外」、設計上の意図的例外）。追加のSQL
構文解析・ホワイトリスト検証はFunctional Designでは導入しない（複雑なSQL構文解析はGEN-9
リバースエンジニアリング機能＝U6の責務であり、U5に重複実装しない。`queryexecution`
（U7）と同一方針）。ただし最低限の防御として、`rawWhere`/`rawOrderBy`にセミコロン
（`;`）が含まれる場合は複数ステートメント注入の簡易チェックとして拒否する
（`PermissionDeniedException`。`NamedParameterJdbcTemplate`は本来単一ステートメントしか
実行しないため実害は限定的だが、分断攻撃に対する最低限の防御として明示的に拒否する）。
UNION SELECT等による他テーブルへのアクセス拡大リスクは「対象RDBMS接続の管理者（Admin）が
許可した機能であり、一般ユーザの手入力SQL機能は要件上意図的にカラム権限フィルタの対象外と
されている」既知のリスクとしてここに明記し、`security-baseline`拡張のオプトイン時に改めて
要否確認する対象とする（`queryexecution`と同じ取り扱い）。

### 2.4 ページング・方言吸収
SELECT文のページング（`LIMIT`/`OFFSET`相当）・ソート方向・NULL順序は
`DialectStrategy.buildPagingClause`/`buildNullsOrderingClause`（U1既存）を用いて対象RDBMS
（MySQL/MariaDB/PostgreSQL/H2）の方言差異を吸収する。`ConnectionPoolRegistry.getJdbcTemplate`
（U3既存）が返す`NamedParameterJdbcTemplate`を経由し、`DriverManager.getConnection()`は
使用しない。

### 2.5 大量データ閲覧監査（MVP-11, Q7）
`application.yml`の`mm.app.master-data.large-record-threshold`（デフォルト`100`、環境変数
`MM_APP_MASTER_DATA_LARGE_RECORD_THRESHOLD`でオーバーライド可能）を設定として持つ。
`listRecords`が返す`RecordListResult.records`の実件数（1回のレスポンスに含まれる件数＝
ページサイズ）がこの閾値以上の場合、`AuditLogService.record(DATA_ACCESS,
LARGE_RECORD_READ, userId, connectionId, Result.SUCCESS, targetDescription=schema.table,
...)`を呼び出す（ページング自体の閾値＝1ページあたりの件数で判定し、テーブル全体の総
件数では判定しない）。

---

## 3. レコード変更（GEN-3, GEN-4, GEN-5）

### 3.1 `MutationRequest`の権限検証（Q5, all-or-nothing）
`applyChanges`は対象RDBMSへの問い合わせに先立ち、リクエスト内の全操作を検証する。
- `RecordCreate`: `EffectivePermissionResolver.canCreate(userId, connectionId, schema,
  table)`が`true`であること（GEN-5 AC「フルアクセス権限があるテーブルのみ作成可能」）。
- `RecordUpdate`: `changedValues`の全カラムが`resolveEffectiveColumnPermissions`で`UPDATE`
  権限を持つこと（GEN-3 AC「RU以上のカラムのみ編集可能」）。加えて3.2の主キー構成チェック。
- `RecordDelete`: `EffectivePermissionResolver.canDelete(userId, connectionId, schema,
  table)`が`true`であること（GEN-5 AC）。

リクエスト内の**いずれか1件でも**上記検証に失敗した場合、対象RDBMSへの問い合わせを一切
行わずリクエスト全体を拒否する（`PermissionDeniedException`、`MutationResult.errorMessage`
に権限検証失敗の概要を設定）。GEN-4の「単一トランザクション」「一部失敗で全体
ロールバック」という要件と一貫させ、「検証段階の失敗」と「実行段階（`SQLException`）の
失敗」を同じall-or-nothing方針で統一する。権限検証失敗も`AuditLogService.record(...)`で
`MASTER_DATA_MUTATION`・`Result.FAILURE`として記録する。

なお、この検証はフロントエンド側のUI制御（Q8: `canCreate`/`canDelete`が`false`の場合の
ボタン非表示等）を前提とせず、直接API呼び出しに対する最終防衛線として常に実施する
（フロントエンドのUI制御はあくまで正規操作フローの利便性のためのガードであり、権限検証を
代替しない）。

### 3.2 主キーなしテーブルの更新・削除の扱い（Q6）
`RecordUpdate`は主キー値による行の再特定を前提とする。`MasterDataMutationService`が
`SchemaQueryService`のメタデータから対象テーブルの主キー構成の有無を判定し、主キーなし
テーブルへの`RecordUpdate`が1件でも含まれていれば、対象カラムの権限に関わらずリクエスト
全体を拒否する（3.1と同じ全体拒否方針、`PermissionDeniedException`ではなく
`ValidationException`——権限の有無ではなく構造上不可能な操作のため）。`RecordCreate`
（`INSERT`、行の再特定が不要）は主キーなしテーブルでも`canCreate`の例外規定（補助権限C
のみで許可、U4確定済み）どおり許可する。`RecordDelete`は`canDelete`が常に`false`になる
ことで自然に拒否される（3.1の検証で自動的に弾かれるため追加のチェック不要）。結果として、
主キーなしテーブルは「作成のみ可能・更新/削除は不可」という一貫した扱いになる。

### 3.3 単一トランザクション実行・ロールバック（GEN-4）
3.1の検証を全て通過した場合、対象RDBMSへの接続（`ConnectionPoolRegistry`）上で単一
トランザクションを開始し、`creates`→`updates`→`deletes`の順（または要件上問題のない順序、
Code Generationで確定）で`INSERT`/`UPDATE`/`DELETE`文を発行する。いずれか1件でも
`SQLException`が発生した場合はトランザクション全体をロールバックし、`MutationResult`の
`errorMessage`には`SQLException`由来の概要メッセージのみを設定する（行・カラム単位の詳細
特定は行わない、Application Design Question 8 = B）。`INSERT`/`UPDATE`/`DELETE`の実際の
SQL文組み立ても`DialectStrategy.quoteIdentifier`で識別子をクオートする（2.4と同様）。

### 3.4 監査記録
`applyChanges`の成功・失敗いずれも`AuditLogService.record(DATA_ACCESS,
MASTER_DATA_MUTATION, userId, connectionId, Result.SUCCESS|FAILURE, targetDescription=
schema.table, summaryMessage=作成/更新/削除件数または失敗概要)`を呼び出す（GEN-4 AC
「変更内容は監査ログに記録される」）。

---

## 4. API認可（`SecurityConfig`、U1 NFR Design 1.3の規約に基づく）

本ユニットの全機能は一般ユーザ向け（MVP-10, MVP-11, GEN-1〜5のペルソナはいずれも
「一般ユーザ」）。管理者専用ロール制約は課さない（認証済みユーザであれば呼び出し可能、
アクセス可否は`EffectivePermissionResolver`による実効権限判定に委ねる）。

| パスパターン（想定、正確なパスはCode Generationで確定） | 対象 |
|---|---|
| `GET /api/rdbms-connections/{connectionId}/schemas` | `listAccessibleSchemas` |
| `GET /api/rdbms-connections/{connectionId}/schemas/{schema}/tables` | `listAccessibleTables` |
| `GET /api/rdbms-connections/{connectionId}/schemas/{schema}/tables/{table}/records` | `listRecords`（`criteria`はクエリパラメータまたはリクエストボディ、詳細はCode Generationで確定） |
| `POST /api/rdbms-connections/{connectionId}/schemas/{schema}/tables/{table}/records:apply` | `applyChanges`（`MutationRequest`をボディで受け取る単一エンドポイント、GEN-4の統一API方針） |

いずれも認証済み（`isAuthenticated()`）であればアクセス可能とし、テーブル/カラム単位の
実効権限判定はコントローラではなくサービス層（`MasterDataQueryService`/
`MasterDataMutationService`）内で行う。