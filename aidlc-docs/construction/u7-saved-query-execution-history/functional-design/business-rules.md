# business-rules.md — U7: Saved Query / Execution / History

`u7-saved-query-execution-history-functional-design-plan.md`の回答（Q1〜Q11）に基づく
業務ルール定義。

---

## 1. 保存クエリの可視性・実行・編集・廃止権限（GEN-10〜12, Q5）

### 1.1 可視性（Visibility）

- `listQueries(userId, connectionId, includeRetired)`: Public全件＋自分が作成したPrivateのみを
  返す（他ユーザのPrivateは一覧に現れない）。`includeRetired=false`（既定）では`retired=true`の
  クエリを除外し、`includeRetired=true`を指定した場合は含める（画面側に「廃止済みも表示」
  トグルを用意する）。
- `getQuery(userId, savedQueryId)`: Publicなら誰でも取得可、Privateは作成者のみ
  （作成者以外は`PermissionDeniedException`）。`retired`の値に関わらず、可視性さえ満たせば
  取得できる（一覧から除外されるだけで、IDが分かっていれば詳細は引き続き参照できる）。

### 1.2 編集（GEN-12）

`updateQuery(userId, savedQueryId, name, sql, visibility)`は作成者のみ許可する（作成者以外は
`PermissionDeniedException`）。`retired=true`のクエリに対しては作成者本人であっても
`EntityNotFoundException`で拒否する（廃止は「以後の編集・実行を止める」意思表示であるため、
編集を許可すると廃止の意図と矛盾する）。

### 1.3 廃止（論理削除、Q5）

物理削除は`QueryHistory.savedQueryId`の参照整合性を壊すため行わない。`SavedQuery`に
`retired: boolean`（既定`false`）フラグを追加し、`retireQuery(userId, savedQueryId)`
（作成者のみ）で`true`に設定する一方向の操作とする。**復元（un-retire）はMVPスコープ外**。

`retired=true`が影響するのは`savedquery`パッケージが提供する保存クエリ自体の画面・APIのみ
であり、`queryhistory`（実行履歴）の見え方には一切影響しない（5節参照）。

まとめ:

| 操作 | `retired=false` | `retired=true` |
|---|---|---|
| `getQuery`（参照） | 可視性に従う | 可視性に従う（`retired`は無関係） |
| `updateQuery`（編集） | 作成者のみ可 | 作成者含め全員不可（`EntityNotFoundException`） |
| `executeSavedQuery`（実行） | 可視性に従う | 作成者含め全員不可（`EntityNotFoundException`） |
| `listQueries`（一覧） | 表示 | `includeRetired=true`指定時のみ表示 |

### 1.4 実行（GEN-11）

`executeSavedQuery`は`getQuery`と同じ可視性・`retired`チェックを内部で行う
（`SavedQueryService`経由でSQL取得時に検証、`QueryExecutionService`が直接可視性を判定しない
——単一責任の原則、`SavedQueryService.getQuery`相当のロジックを内部的に再利用）。保存クエリ
実行時はSQL編集不可（GEN-11 AC、フロントエンド側でSQL入力欄を読み取り専用にする）。

---

## 2. 読み取り専用SQL検証（GEN-13, Q4）

### 2.1 JSqlParserによる文種別判定（U6の`SqlParsingService`は経由しない）

読み取り専用検証はJSqlParserライブラリを`queryexecution`から**直接**利用し
（`CCJSqlParserUtil.parse(sql)`）、得られた`Statement`の型が
`net.sf.jsqlparser.statement.select.Select`かどうかのみで判定する。**U6の
`SqlParsingService.parse`（GEN-9用、`QueryBuilderModel`で表現できる範囲に解析可否が
限定される）は経由しない**——GEN-13は「クエリビルダーが組み立てられる範囲を超えた複雑な
読み取り専用SQL」（サブクエリ・UNION・CTE・ウィンドウ関数・OR条件等を含むSELECT文）も
手入力実行の対象であるべきであるため。したがって`component-dependency.md`には
`queryexecution → querybuilder`というパッケージ依存は追加しない——JSqlParserライブラリ
（U6のbuild.gradle.ktsで導入済み）への直接依存のみとする。

単一SQL文であり`Select`型であることが確認できれば、コメント内キーワードの誤検知やセミコロン
区切りの複数文（例: `SELECT ...; DROP TABLE ...`）も安全に排除できる（正規表現ベースの単純な
キーワード検査より安全）。JSqlParser自体がパースに失敗した場合（対応外の方言固有構文等）は、
安全側に倒して実行を拒否する（`ValidationException`、`domain-entities.md`）。

### 2.2 U4（テーブル/カラム単位の読み取り権限フィルタ）への非依存

`queryexecution`は`permission`（U4）パッケージに**テーブル/カラム単位の読み取り権限フィルタ
目的では**引き続き依存させない——要件上（`REQUIREMENTS.md` 5.7、`stories.md` GEN-13）
「読み取り専用SQLのみ実行可能」という制約のみが明記されており、`masterdata`のような
テーブル/カラム単位の読み取り権限フィルタは要件上明示されていない。この設計判断（対象RDBMSの
任意のテーブル/カラムを権限に関わらず読み取れる）は、`security-baseline`拡張のオプトイン時に
再検討すべき項目として引き続きフラグを残す（`component-dependency.md`の申し送り事項）。
**（2026-07-15変更要求）** ただし2.3節のスキーマ検証目的では、限定的に`permission`へ
新規依存する（テーブル/カラム単位の権限フィルタとは別軸）。

### 2.3 実行対象スキーマの指定・検証（2026-07-15変更要求、CHG-4・CHG-5）

クエリビルダー（U6）が生成するSQLはスキーマ非修飾（`business-rules.md`該当箇所参照、
再利用性を高めるための意図的な設計）であるため、実行時にどのスキーマへ向けて実行するかを
明示的に指定する必要がある。

- `executeAdhocSql`/`executeSavedQuery`は`schema`を必須パラメータとして受け取る。
- 実行前に`permission.EffectivePermissionResolver.listAccessibleSchemas(userId, connectionId)`
  を呼び出し、指定された`schema`がこの許可リストに含まれることを検証する。含まれない場合は
  `PermissionDeniedException`を返す。この検証は、`SET search_path`にスキーマ名を直接連結する
  ため必須（識別子はバインドパラメータ化できず、未検証の文字列を連結するとSQLインジェクションの
  リスクがある）。
- `DialectStrategy.getSchemaResolutionMode()`が`SCHEMA_BASED`（PostgreSQL/H2）の場合のみ、
  検証済みスキーマ名を`DialectStrategy.quoteIdentifier`でエスケープした上で、実行対象SQLと
  同一のコネクション上で実行直前に`SET search_path TO <quoted-schema>`を発行する
  （`ConnectionPoolRegistry`が返す接続プールから同一コネクションを借用して両方を実行する
  必要がある実装上の制約に留意する、詳細はCode Generationで確定）。
- `CATALOG_BASED`（MySQL/MariaDB）の場合は、スキーマが接続の`databaseName`で既に固定されている
  ため`SET`は発行しない（許可リスト検証自体は同様に行う——`schema`が`databaseName`と一致する
  ことが実質的な検証結果になる）。
- 実行後、`QueryHistoryService.recordExecution`に渡す`ExecutionRecord`に、検証済みの
  `schema`を含める（`domain-entities.md`の`QueryHistory.schema`）。

この変更に伴い`component-dependency.md`に`queryexecution → permission`という新規依存が
1本追加される（`schema`検証専用の限定的な依存、2.2節のテーブル/カラム権限フィルタとは別軸）。

**（2026-07-15変更要求、Functional Design時点で新規に識別した必要事項）**
フロントエンドのスキーマ選択UI（`frontend-components.md`の`QueryExecutionPage`参照）が
選択肢を提示するためには、`queryexecution`側にもスキーマ一覧を列挙するAPIが必要になる
（2.3節の検証ロジックは「含まれるか」の判定のみで、一覧の列挙には使えない）。`masterdata`/
`querybuilder`が同じ目的で`EffectivePermissionResolver.listAccessibleSchemas`への薄い委譲
メソッドをそれぞれ独自に持つ既存パターンに倣い、`QueryExecutionService`にも
`listAccessibleSchemas(userId, connectionId)`（新規メソッド）と対応する
`GET /api/query-execution/{connectionId}/schemas`（新規エンドポイント）を追加する。
これはApplication Design時点では明示的に洗い出されていなかった付随的な必要事項だが、
既存の`queryexecution → permission`依存の範囲内（`EffectivePermissionResolver`への単純委譲）
で完結するため、`component-dependency.md`のマトリクス自体への追加変更は不要。

---

## 3. パラメータ自動検出・バインド（GEN-13, Q6）

SQL文字列に対し正規表現（例: `:([a-zA-Z_][a-zA-Z0-9_]*)`、ただし文字列リテラル内の`:xxx`や
PostgreSQLの`::type`キャストとの誤検知を避けるため簡易的な文字列リテラルスキップを行う）で
パラメータ名を抽出し、フロントエンドへ返す（`DetectedParam`、`domain-entities.md`）。値は
`NamedParameterJdbcTemplate`にそのまま渡せる`Map<String, Object>`として受け取り、**型変換は
行わずすべて文字列として受け取り、JDBCドライバのデフォルトの型変換に委ねる**
（`java.time`型等への明示的な型指定はMVPスコープ外）。パラメータが1つも検出されない場合は
空の`Map`をそのまま渡し、通常のSQL実行として扱う。

---

## 4. ページング制御（GEN-13, Q7）

ユーザがページングを「あり」に設定した場合、入力SQLをサブクエリとしてラップし
`SELECT * FROM (<入力SQL>) AS subquery`の外側に`DialectStrategy.buildPagingClause`
（U1既存、4RDBMS方言吸収済み）でLIMIT/OFFSET相当句を付与する。この方式であれば入力SQLが既に
`ORDER BY`やLIMIT相当句を含んでいてもサブクエリ化により安全に外側から制御でき、入力SQL自体を
構文解析してLIMIT句の有無を判定する必要がない（2節のJSqlParser利用は文種別判定のみに限定し、
ページング注入には使わない）。ページング「なし」の場合はそのまま実行するが、7節の
`max-result-rows`上限が適用される。

---

## 5. 実行履歴の絞り込み・可視性・マスキング（GEN-15, Q8）

### 5.1 絞り込み仕様

`executorScope=ALL`は管理者ロール制約を設けず、認証済みユーザなら誰でも選択可能とする（2.2の
設計方針と一貫、`queryhistory`も追加の権限制約を持たない。`REQUIREMENTS.md` 5.8・
`stories.md` GEN-15で要件として明記済み）。`sqlTextSearch`はSQL文字列への部分一致
（大文字小文字を区別しない`LIKE '%...%'`相当）とする。

### 5.2 保存クエリの可視性変更・廃止に伴う履歴マスキング

保存クエリの可視性が実行後にPublic→Privateへ変更された場合、他ユーザの`executorScope=ALL`
一覧にその実行行のSQL本文が見え続けるのは望ましくない（Privateに変更した以上、他人に見せたく
ないはずという設計意図）。`retired`（1.3節）はこのマスキングの条件には**含めない**——`retired`
は`savedquery`側の一覧・詳細画面の既定非表示にのみ影響し、実行履歴の見え方（マスキングの要否）
とは無関係とする。実行履歴側では「廃止済みであることが分かるようにする」ためのバッジ表示のみ
行い、内容のマスキングは行わない。

`listHistory`の絞り込みクエリ自体（日時範囲・`sqlTextSearch`等）に可視性チェックを組み込むと
複雑化するため、以下の2段階方式で分離する。

1. `listHistory`のページング・絞り込みは`QueryHistory`単体に対する単純なクエリのままとする
   （5.1節の設計を変更しない）。
2. 取得した**ページ内の行のみ**を対象に、`savedQueryId`が非nullかつ`row.userId != 閲覧者`の
   行についてのみ、`SavedQueryService`に新設する軽量なバッチ判定API
   `Map<Long, SavedQueryStatus> getStatuses(Long viewerId, Set<Long> savedQueryIds)`
   （`domain-entities.md`の`SavedQueryStatus`、`visibleToViewer`と`retired`を独立フィールド
   として返す）を1回呼び出す。`visibleToViewer=false`の行は`sql`/`savedQueryName`/`params`を
   「(非公開のため表示できません)」等のプレースホルダに差し替える（行自体は除外しない——
   実行日時・結果件数・実行時間・実行者等の非機微なメタデータはそのまま表示する）。
   `retired=true`の行は（マスキングとは独立に、`visibleToViewer`の値に関わらず）「廃止済み」
   バッジを付与する（マスキング中の行にもバッジ自体は表示してよい——バッジはSQL内容を漏らさ
   ないため）。行を除外せずマスキングのみに留めることで、ページングの件数整合性（除外による
   再ページングの複雑化）を回避する。自分自身が実行した行（`row.userId == 閲覧者`）は、
   参照先`SavedQuery`の可視性状態に関わらず常に非マスキングで表示する（自分が実際に実行した
   記録であり、新たな情報漏洩には当たらないため）。

### 5.3 可視性マトリクス

| 行の種別 | 実行者と閲覧者の関係 | 参照先`SavedQuery`の可視性 | 「廃止済み」バッジ | `executorScope=SELF` | `executorScope=ALL` |
|---|---|---|---|---|---|
| 手入力SQL実行（`savedQueryId=null`） | 閲覧者自身が実行 | （該当なし） | 付与しない | 表示（フル） | 表示（フル） |
| 手入力SQL実行（`savedQueryId=null`） | 他ユーザが実行 | （該当なし） | 付与しない | 一覧に現れない | 表示（フル、可視性判定の対象外） |
| 保存クエリ実行 | 閲覧者自身が実行 | 任意 | `retired`次第で付与 | 表示（フル） | 表示（フル、自分の実行記録は常にマスクなし） |
| 保存クエリ実行 | 他ユーザが実行 | Public | `retired`次第で付与 | 一覧に現れない | 表示（フル） |
| 保存クエリ実行 | 他ユーザが実行 | Private・閲覧者が所有者 | `retired`次第で付与 | 一覧に現れない | 表示（フル、所有者だから見える） |
| 保存クエリ実行 | 他ユーザが実行 | Private・閲覧者は所有者でない | `retired`次第で付与 | 一覧に現れない | 表示（マスク——`sql`/`savedQueryName`/`params`をプレースホルダ化、バッジと他の列は表示） |

`executorScope=SELF`は`row.userId == 閲覧者`のみを対象とするフィルタのため、他ユーザの行は
そもそも一覧に現れない——マスキングの論点は`executorScope=ALL`選択時のみ発生する。「廃止済み」
バッジは可視性（Public/Private）とは独立した軸であり、上表のどの行にも（マスキングされていても）
`retired=true`であれば付与され得る。

### 5.4 実行結果表現

GEN-14の実行結果（`QueryResult`、`domain-entities.md`）は、U5の`RecordListResult`と同一の形
（カラムメタデータ＋結果行の配列）で統一する——`masterdata`と`queryexecution`の両方が同じ形の
結果を返すことで、フロントエンド側の結果テーブル描画ロジック（`DataTable`のアダプタ部分）を
U5・U7間で共通化できる余地を残す（U5からの申し送り事項どおり）。ただし`masterdata`パッケージ
への直接のバックエンド依存は追加しない（型定義のみ`queryexecution`側に独立して複製する、
フロントエンドの`他feature非依存の方針`と同じ扱い）。

---

## 6. U6↔U7連携（GEN-8, GEN-16, Q9）

画面間のSQL・パラメータの引き継ぎはReact Routerの`useNavigate`による画面遷移＋URLクエリ
パラメータ（`connectionId`, `rawSql`）を統一的な方式とする（U6が既に`rawSql`/`connectionId`を
受け付ける実装を持つため、この方式に揃えるのが最小変更）。パラメータ値（`Map<String, Object>`）
はURLクエリパラメータでは表現が煩雑になるため引き継がず、遷移先でパラメータ自動検出（3節）に
より再度値入力を求める（GEN-16のACは「元のSQL・パラメータが引き継がれる」だが、パラメータ
**値**ではなく「どのパラメータが必要か」という構造の引き継ぎ——SQL文字列さえ引き継げば
`:param`検出で自動的に再現される——と解釈する）。

具体的な遷移先:

| 操作 | 遷移先 | 引き継ぐ情報 |
|---|---|---|
| 「再実行」（GEN-16） | `queryExecution`の実行画面 | `rawSql`、`schema`（2026-07-15変更要求、遷移先で上書き可能） |
| 「保存」（GEN-16、GEN-8） | `savedQuery`の保存フォーム | `rawSql`（`schema`は保存対象外、実行時に都度指定するため） |
| 「ビルダーで編集」（GEN-16） | `querybuilder`の`/query-builder` | `rawSql`、`schema`（2026-07-15変更要求。`connectionId`はグローバル接続コンテキストに従うためURL引き継ぎを廃止、`querybuilder/frontend-components.md`参照） |

U6側の`GeneratedSqlPanel`の`onNavigateToSave`/`onNavigateToExecute`実装は、`GeneratedSql.sql`
を`rawSql`として同様のクエリパラメータ経由で`savedQuery`/`queryExecution`へ`navigate`する
関数とする（`GeneratedSql.params`は同じ理由で引き継がない）。**（2026-07-15変更要求）**
`onNavigateToExecute`は`QueryBuilderPage`で選択中の`schema`もあわせて引き継ぐ
（`onNavigateToSave`は`schema`を引き継がない、`SavedQuery`がスキーマを保存しないため）。

---

## 7. 大量データ・タイムアウト対策（GEN-13, Q11）

`queryexecution`には以下2つの設定キーのみを導入する。

- `mm.app.query-execution.query-timeout`（既定30秒）
- `mm.app.query-execution.max-result-rows`（既定1000件、**ページング「なし」時のみ**適用する
  最大取得件数上限。超過分は切り捨てて`QueryResult.truncated`を`true`にする）。ページング
  「あり」の場合はリクエストごとのLIMIT自体が返却件数を1ページ分に制限するため、この上限は
  適用しない（対象がそもそも無制限に一括取得されることはない）。

`large-record-threshold`相当の大量レコード監査閾値は**導入しない**——既存の`AuditLog`の
`QUERY_EXECUTED`イベント記録（`AuditLog.summaryMessage`に結果件数を含める）で足りるため、
U5の`LARGE_RECORD_READ`のような専用イベント種別・専用閾値は不要と判断する。

---

## 8. API認可（`SecurityConfig`、U1 NFR Design 1.3の規約に基づく）

| パスパターン（想定、正確なパスはCode Generationで確定） | 対象 |
|---|---|
| `GET /api/saved-queries` | `SavedQueryService.listQueries` |
| `POST /api/saved-queries` | `SavedQueryService.saveQuery` |
| `GET /api/saved-queries/{savedQueryId}` | `SavedQueryService.getQuery` |
| `PUT /api/saved-queries/{savedQueryId}` | `SavedQueryService.updateQuery` |
| `POST /api/saved-queries/{savedQueryId}/retire` | `SavedQueryService.retireQuery` |
| `POST /api/query-execution/adhoc` | `QueryExecutionService.executeAdhocSql` |
| `POST /api/query-execution/saved/{savedQueryId}` | `QueryExecutionService.executeSavedQuery` |
| `GET /api/query-history` | `QueryHistoryService.listHistory` |

全エンドポイントは認証済みユーザ（一般ユーザ/管理者いずれも利用可）を対象とし、管理者限定の
制約は課さない（GEN-10〜16のACに管理者限定の記載なし）。実際のアクセス可否は1節（保存クエリの
可視性）・2節（読み取り専用検証のみ、テーブル/カラム権限フィルタなし）で制御される。