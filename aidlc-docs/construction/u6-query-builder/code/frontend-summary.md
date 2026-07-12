# U6 Query Builder - フロントエンドサマリ

Step 11（フロントエンド生成）・Step 12（Vitest+RTLテスト）で生成したコンポーネント・
API・ルーティングの一覧。設計は`functional-design/frontend-components.md`に準拠する。

## 新規: `features/queryBuilder/`

| ファイル | 内容 |
|---|---|
| `types.ts` | `TableRef`/`ColumnRef`/`FromItem`/`JoinType`/`AggregateFunction`/`Operator`/`SortDirection`/`Condition`/`JoinItem`/`SelectItem`/`OrderByItem`/`QueryBuilderModel`/`GeneratedSql`/`ParseResult`。`SortDirection`は`masterData`/`schema`と同様、本feature内にローカル再定義（他feature非依存の方針）。Step 11-3実装時に、接続一覧APIの新設（後述）に伴い`ConnectionSummary`/`RdbmsType`（`masterData/types.ts`と同一shape）も追加した |
| `api.ts` | `listSelectableConnections()` → `GET /api/query-builder/connections`（Step 11-3実装時に追加）、`listSelectableSchemas(connectionId)` → `GET /api/query-builder/{connectionId}/schemas`、`listSelectableTables(connectionId, schema)` → `GET .../schemas/{schema}/tables`、`listSelectableColumns(connectionId, schema, table)` → `GET .../tables/{table}/columns`、`generateSql(connectionId, model)` → `POST .../generate`、`parseSql(connectionId, rawSql)` → `POST .../parse`。U1の`apiClient`（`apiFetch`）を再利用 |
| `QueryBuilderPage.tsx` | 接続・スキーマ選択、7タブ（FROM/JOIN・SELECT・WHERE・GROUP BY・HAVING・ORDER BY・LIMIT/OFFSET）切り替えコンテナ。`fromItem`/`joinItems`/`selectItems`/`whereConditions`/`groupByColumns`/`havingConditions`/`orderByItems`/`limit`/`offset`の各stateを保持し子タブへ配布する。`fromItem`確定後のみ`GeneratedSqlPanel`を常時表示（タブ切替に関わらず）し、`SqlReverseParsePanel`は接続選択後（スキーマ選択前でも）常時表示する。URLクエリパラメータ`connectionId`/`rawSql`（U7からの遷移、GEN-9）をマウント時に読み取り、自動で接続選択・SQL解析を行う |
| `FromJoinTab.tsx` | `listSelectableTables`でアクセス可能テーブルのみを選択肢とするベーステーブル（テーブル・エイリアス）/JOINテーブル（種別`INNER`/`LEFT`/`RIGHT`・テーブル・エイリアス・ON条件）の追加・編集・削除UI（GEN-6 AC）。ON条件の右辺は`Condition.value`の`"alias.column"`文字列表現（Step 2-2確定）をUI側で分解・再構成する |
| `SelectTab.tsx` | `fromItem`・`joinItems`の各テーブル/エイリアスについて`listSelectableColumns`で取得した権限フィルタ済みカラムのみを選択肢とするSELECT項目（カラム・集計関数・出力エイリアス）指定UI（GEN-7 AC）。テーブルごとの「全カラムを追加」一括ボタン（重複除外、バックエンドAPI呼び出し不要）と単一項目の追加ボタンを提供し、`maxSelectItems`props（既定100）超過時はフロントエンド側でエラー表示し追加を行わない |
| `WhereHavingTab.tsx` | `target`（`where`/`having`）propsで切替、AND結合のみの条件リスト組み立てUI（OR・括弧グルーピングはMVPスコープ外）。`having`時のみ集計関数選択を表示する共通コンポーネント |
| `GroupByOrderByTab.tsx` | `target`（`groupBy`/`orderBy`）propsで切替。`groupBy`時は`"alias.column"`文字列（`SqlGenerationService.buildGroupByClause`が解釈する形式と同一）の選択UI、`orderBy`時は`OrderByItem`（カラム・集計関数・ASC/DESC）の選択UIを提供する共通コンポーネント |
| `LimitOffsetTab.tsx` | 状態を持たず、`limit`/`offset`の数値入力のみを提供する |
| `GeneratedSqlPanel.tsx` | 「SQL生成」ボタン→（呼び出し元の`onGenerate`経由で）生成SQL・パラメータ表示・コピーボタンを提供する（GEN-8）。`ValidationException`のエラー表示のため`error?: string | null` propsを実装時に追加した（`frontend-components.md`のprops定義では表示先が未規定だったため）。「保存」「実行」ボタンは配置するが`onNavigateToSave`/`onNavigateToExecute`props未指定時はdisabled（U6時点では未実装） |
| `SqlReverseParsePanel.tsx` | 手入力SQL貼り付け→`parseSql`呼び出し→`fullyParsed=true`なら`onApply(model)`でタブへ反映、`false`なら`notice`表示（GEN-9）。`initialRawSql`props指定時はマウント時に自動解析する |

## 実装時に追加したAPI（Step 11-3着手時の発見・対応）

`frontend-components.md`のQueryBuilderPageは接続・スキーマ選択を内包する設計だが、Step 5-1〜5-3
確定時点の`querybuilder`パッケージには接続一覧を列挙するAPIが存在しなかった
（U5「ブラウンフィールド発見事項」5と同種の問題）。ユーザ指示によりU5と同一パターンで解決し、
バックエンド側にitem 2-9（`QueryBuilderMetadataService.listSelectableConnections`）・5-4
（`GET /api/query-builder/connections`、`QueryBuilderController`のクラスレベルマッピング
再構成）・6-2・7-2を追加した（詳細は`api-layer-summary.md`）。フロントエンド側は
`listSelectableConnections`（`api.ts`）・`ConnectionSummary`/`RdbmsType`（`types.ts`）を
Step 11-3実装の一環として追加した。

## ルーティング一覧（追加分）

| パス | 種別 | コンポーネント |
|---|---|---|
| `/query-builder` | 保護（全認証ユーザ、ロール制約なし） | `QueryBuilderPage` |

`AppLayout.tsx`には`isAuthenticated`のみを条件とする「クエリビルダー」ナビゲーションリンク
（`data-testid="app-layout-nav-query-builder"`）を、「マスタデータ」リンクの直後、管理者専用
リンク群より前のロール非依存の位置に追加した。未認証で保護ルートにアクセスした場合は
`ProtectedRoute`（既存、変更なし）により`/login`へリダイレクトする。

## data-testid一覧（新規分）

`query-builder-page`, `query-builder-page-connection-select`, `query-builder-page-schema-select`,
`query-builder-page-tab-{fromJoin|select|where|groupBy|having|orderBy|limitOffset}`,
`from-join-tab`, `from-join-tab-base-table-select`, `from-join-tab-base-alias-input`,
`from-join-tab-join-item`, `select-tab`, `select-tab-error`, `select-tab-item`,
`where-having-tab-{where|having}`, `where-having-tab-condition`,
`group-by-order-by-tab-{groupBy|orderBy}`, `group-by-order-by-tab-item`, `limit-offset-tab`,
`limit-offset-tab-limit-input`, `limit-offset-tab-offset-input`, `generated-sql-panel`,
`generated-sql-panel-generate-button`, `generated-sql-panel-error`, `generated-sql-panel-result`,
`generated-sql-panel-sql`, `sql-reverse-parse-panel`, `sql-reverse-parse-panel-raw-sql-input`,
`sql-reverse-parse-panel-parse-button`, `sql-reverse-parse-panel-notice`,
`app-layout-nav-query-builder`

## 実装時判断事項（設計未規定・自律的に決定した内容）

- **接続一覧APIの欠落**: 上記のとおり。U5と同一パターンで解決（既存の承認済みStep 5〜7を
  差し戻すのではなく、item追加として対応）。
- **`GeneratedSqlPanel`のエラー表示先**: `frontend-components.md`のprops定義（`model`/
  `generatedSql`/`onGenerate`）だけでは`ValidationException`のエラー表示先が不明瞭だったため、
  `error?: string | null` propsを追加し、実際のAPI呼び出し・エラーハンドリングは呼び出し元の
  `QueryBuilderPage`側で行う設計とした（パネル自体は表示に徹する）。
- **`GeneratedSqlPanel`/`SqlReverseParsePanel`の配置**: `frontend-components.md`のコンポーネント
  階層図はフラットな子要素列挙のため、タブ切り替え対象（`FromJoinTab`等5つ）とは別に、
  タブ切り替えの下に常時表示のセクションとして配置した（生成SQL表示やSQL貼り付けはどのタブを
  見ていても利用したい機能のため）。
- **`FromJoinTab`のON条件カラム入力**: `FromJoinTab`は任意テーブルのカラム一覧を保持しない設計
  （`frontend-components.md`の状態定義に`selectableColumns`が定義されていないことと整合）のため、
  ON条件の左右カラム名はテキスト入力とした（エイリアスのみプルダウン選択）。
- **`SqlReverseParsePanel`の表示条件**: `parse` APIは`connectionId`のみを要求し`schema`を
  要求しないため、`schema`選択前でも動作するよう`connectionId !== null`のみを表示条件とし、
  スキーマ選択欄の直後に配置した。
- **URLクエリパラメータ連携**: `rawSql`（`frontend-components.md`で言及済み）に加え、
  `connectionId`もURLクエリパラメータとして読み取り、マウント時の自動接続選択に用いる設計を
  実装時に追加した（U7からの遷移時、対象接続が既知であることが自然なため）。

## テストカバレッジ（Step 12）

| テストファイル | 件数 | 検証内容 |
|---|---|---|
| `QueryBuilderPage.test.tsx` | 6 | 接続一覧表示とスキーマ選択前の非表示、接続/スキーマ選択後のタブ表示（FromJoinTabが既定）、タブ切替、`fromItem`未確定時の`GeneratedSqlPanel`非表示、ベーステーブル選択後の表示、SQL生成ボタン押下→表示（`MemoryRouter`でラップ） |
| `FromJoinTab.test.tsx` | 6 | テーブル一覧ロード、ベーステーブル選択時の既定エイリアス、エイリアス変更、JOIN追加（既定`INNER`）、JOIN削除、ベーステーブル未選択時のJOINボタン非活性 |
| `SelectTab.test.tsx` | 5 | カラム一覧ロード、全カラム一括追加（重複除外）、上限超過時のエラー表示・追加スキップ、単一項目追加、項目削除 |
| `WhereHavingTab.test.tsx` | 6 | WHERE/HAVINGの見出し切替、条件追加（既定`EQ`）、`IS_NULL`選択時の値入力欄非表示、`having`時のみの集計関数選択表示、条件削除 |
| `GroupByOrderByTab.test.tsx` | 5 | GROUP BYの`"alias.column"`追加、GROUP BY削除、ORDER BYの既定`ASC`追加、`DESC`への変更、ORDER BY削除 |
| `LimitOffsetTab.test.tsx` | 4 | 初期値描画、limit変更時のoffset保持、offset変更時のlimit保持、空入力時の`null`化 |
| `GeneratedSqlPanel.test.tsx` | 6 | 生成ボタン押下時の`onGenerate`呼び出し、`generatedSql=null`時の非表示、生成SQL/パラメータ表示、エラー表示、コピーボタンの`navigator.clipboard.writeText`呼び出し、保存/実行ボタンのdisabled切替 |
| `SqlReverseParsePanel.test.tsx` | 3 | 解析成功時の`onApply`呼び出し、非対応構文時の`notice`表示と`onApply`未呼び出し、`initialRawSql`指定時のマウント時自動解析 |

上表8ファイル・**41件**がU6で新規追加したフロントエンドテスト。U1〜U5既存分と合わせ、
フロントエンド全体は**52ファイル・224件、全テスト成功**（`npx vitest run`）。`npx tsc --noEmit`
（型チェック）・`npm run lint`（oxlint）もエラー・警告なしで完了している
（詳細は`testing-summary.md`参照）。