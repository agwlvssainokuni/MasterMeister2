# frontend-components.md — U6: Query Builder

`u6-query-builder-functional-design-plan.md`の回答（Q10）に基づくフロントエンド構成。U1既存の
共有コンポーネント（`ConfirmDialog`・`ToastNotification`、`routes/ProtectedRoute`）を再利用する。
一般ユーザ・管理者いずれも利用可能な機能（GEN-6〜GEN-9のACに管理者限定の記載なし）。
ルーティングプレフィックスは`/query-builder`。

---

## features/queryBuilder/

### コンポーネント階層

```
QueryBuilderPage
├── FromJoinTab          (GEN-6)
├── SelectTab
├── WhereHavingTab        (WHEREタブとHAVINGタブで再利用、propsで対象を切替)
├── GroupByOrderByTab      (GROUP BYタブとORDER BYタブで再利用、propsで対象を切替)
├── LimitOffsetTab
├── GeneratedSqlPanel      (GEN-8)
└── SqlReverseParsePanel   (GEN-9)
```

### QueryBuilderPage

- **状態**: `schema: string | null`,
  `activeTab: 'fromJoin' | 'select' | 'where' | 'groupBy' | 'having' | 'orderBy' | 'limitOffset'`,
  `model: QueryBuilderModel`, `generatedSql: GeneratedSql | null`,
  `selectableSchemas: string[]`（**2026-07-15変更要求**: `connectionId`はページ内stateではなく
  U1の`useConnection()`からグローバル接続コンテキストとして取得する。ページ内に接続選択UIは
  持たない）
- **責務**: スキーマ選択、タブ切り替えコンテナ。`connectionId`（グローバルコンテキスト）が
  変化するたびに`listSelectableSchemas`（フロー1手順1）を呼び出し選択肢を取得する。
  子タブコンポーネントに`model`とその更新関数を配布し、`GeneratedSqlPanel`に現在の`model`を
  渡す。URLクエリパラメータで`rawSql`を受け取った場合（U7からの遷移、GEN-9）、
  `SqlReverseParsePanel`に自動的に解析させる（フロー3手順1）。**（2026-07-15変更要求）**
  同様にURLクエリパラメータ`schema`を受け取った場合、初回のスキーマ選択肢取得後に
  `schema`の初期値としてプリフィルする（画面内で上書き可能）。`connectionId`が
  （初回マウント後に）変化した場合、選択中の`schema`・組み立て中の`model`・`generatedSql`を
  リセットする（`stories.md` CHG-2、U1`business-logic-model.md`フロー5手順4でU1から
  委譲された責務）。`connectionId`が`null`の場合は「接続が指定されていません。」を表示する。

### FromJoinTab

- **状態**: `selectableTables: TableRef[]`
- **Props**: `fromItem: FromItem | null`, `joinItems: JoinItem[]`,
  `onChange(fromItem: FromItem, joinItems: JoinItem[]): void`
- **責務**: `listSelectableTables`（フロー1手順3）を呼び出し、アクセス可能テーブルのみを
  選択肢として提示する（GEN-6 AC）。ベーステーブル（`fromItem`、単数）とJOINテーブル
  （`joinItems`、複数、JOIN種別は`INNER`/`LEFT`/`RIGHT`のみ選択可）・エイリアスを指定する。

### SelectTab

- **状態**: `selectableColumns: ColumnRef[]`
- **Props**: `fromItem: FromItem | null`, `joinItems: JoinItem[]`, `selectItems: SelectItem[]`,
  `onChange(items: SelectItem[]): void`
- **責務**: 選択済みテーブル/エイリアスに対し`listSelectableColumns`（フロー1手順4）で
  アクセス可能なカラムのみ提示する（GEN-7 AC）。カラムごとに集計関数
  （`NONE, COUNT, SUM, AVG, MIN, MAX`）と出力エイリアスを指定できる。`fromItem`・
  `joinItems`の各テーブル/エイリアスごとに「このテーブルの全カラムを追加」ボタンを配置し、
  押下すると対応する`selectableColumns`（権限フィルタ済み）を一括で`selectItems`に
  `aggregateFunction = NONE`として追加する（既に追加済みのカラムは重複除外）。バックエンドAPI
  呼び出しは不要（`listSelectableColumns`が既に取得済みの一覧をフロントエンド側で一括適用する
  のみ）。追加後の`selectItems`件数が`mm.app.query-builder.max-select-items`（既定`100`、
  U6 NFR Requirements Q4-2）を超える場合は、ボタン押下時にフロントエンド側で追加を行わず
  エラーメッセージを表示する。

### WhereHavingTab

- **状態**: `selectableColumns: ColumnRef[]`
- **Props**: `target: 'where' | 'having'`, `conditions: Condition[]`,
  `onChange(conditions: Condition[]): void`
- **責務**: AND結合のみの条件リストを組み立てるUI（OR・括弧グルーピングはMVPスコープ外、
  `business-rules.md` 2.1）。`target`に応じてWHERE/HAVINGいずれのタブにも使う共通
  コンポーネントとする。HAVING利用時は`aggregateFunction`付き条件も入力可能。

### GroupByOrderByTab

- **状態**: `selectableColumns: ColumnRef[]`
- **Props**: `target: 'groupBy' | 'orderBy'`, `groupByColumns?: string[]`,
  `orderByItems?: OrderByItem[]`, `onChange(value: string[] | OrderByItem[]): void`
- **責務**: `target = 'groupBy'`時はカラム名リストの選択UI、`target = 'orderBy'`時はカラム・
  集計関数・並び順（`ASC`/`DESC`）の選択UIを提供する共通コンポーネント。

### LimitOffsetTab

- **状態**: なし（親`model`を直接編集）
- **Props**: `limit: number | null`, `offset: number | null`,
  `onChange(limit: number | null, offset: number | null): void`
- **責務**: 件数・オフセットの数値入力（GEN-7 AC）。

### GeneratedSqlPanel

- **Props**: `model: QueryBuilderModel`, `generatedSql: GeneratedSql | null`,
  `onGenerate(): void`, `onNavigateToSave?(sql: GeneratedSql): void`,
  `onNavigateToExecute?(sql: GeneratedSql): void`
- **責務**: 「SQL生成」ボタン押下で`SqlGenerationService.generate`相当のAPIを呼び出し
  （フロー2）、生成SQL・パラメータを表示、コピーボタンを提供する。GROUP BY制約違反等の
  `ValidationException`はエラーメッセージとして表示する。「保存」「実行」ボタンは配置するが、
  クリックハンドラは`onNavigateToSave`/`onNavigateToExecute`のprops経由とし、U6時点では
  未実装（U7のFunctional Design/Code Generationで実装を差し込む、`business-rules.md` 7）。
  **（2026-07-15変更要求）** U7側の実装では、遷移先（`savedQuery`/`queryExecution`）URLに
  `connectionId`（グローバルコンテキストの値）に加え、`QueryBuilderPage`で選択中の`schema`も
  URLクエリパラメータとして引き継ぐ（GEN-8改訂AC）。

### SqlReverseParsePanel

- **状態**: `rawSql: string`, `parseResult: ParseResult | null`
- **Props**: `onApply(model: QueryBuilderModel): void`
- **責務**: 手入力SQLの貼り付け→`SqlParsingService.parse`相当のAPI呼び出し→タブ反映のUI
  （フロー3）。GEN-9は本来U7画面からの遷移時の自動反映が主だが、U6単体でも動作確認・利用が
  できるよう手動貼り付け入力も用意する。`parseResult.fullyParsed = false`の場合は
  `parseResult.notice`を表示し、タブへの反映は行わない。

### api.ts（`features/queryBuilder/`）

| 関数 | 対応API | 責務 |
|---|---|---|
| `listSelectableSchemas(connectionId)` | `GET /api/query-builder/{connectionId}/schemas` | フロー1手順1 |
| `listSelectableTables(connectionId, schema)` | `GET /api/query-builder/{connectionId}/schemas/{schema}/tables` | フロー1手順3 |
| `listSelectableColumns(connectionId, schema, table)` | `GET /api/query-builder/{connectionId}/schemas/{schema}/tables/{table}/columns` | フロー1手順4 |
| `generateSql(model)` | `POST /api/query-builder/generate` | フロー2 |
| `parseSql(rawSql)` | `POST /api/query-builder/parse` | フロー3 |

正確なパス・リクエスト/レスポンス形状はCode Generationで確定する（`business-rules.md` 8）。
`features/queryBuilder/`は`masterData/`・`savedQuery/`・`queryExecution/`等、他featureのAPIを
直接参照しない（`component-dependency.md`のとおり`querybuilder`パッケージが`masterdata`等に
依存しないことに対応するフロントエンド側の制約）。

---

## U7（保存クエリ・クエリ実行、未着手）との連携に関する申し送り事項

Q9のとおり、U6時点での「保存」「実行」ボタンはUI上に配置するのみで、実際の画面遷移・API
連携はU7未着手のため実装しない。`GeneratedSqlPanel`の`onNavigateToSave`/`onNavigateToExecute`
propsはU7のFunctional Design時に具体的な実装（`savedQuery`/`queryExecution`featureへの
遷移、`GeneratedSql`の受け渡し方法）を確定する。また、GEN-9（U7のクエリ実行画面/クエリ履歴
画面からの遷移時のSQL自動解析）についても、`QueryBuilderPage`のURLクエリパラメータ
（`rawSql`）経由の連携方式はU6側で用意済みだが、実際に遷移元となるU7側画面の実装はU7の
Code Generationで行う。

---

## AppRouter.tsxへの追加

| パス | コンポーネント | 認可 |
|---|---|---|
| `/query-builder` | `QueryBuilderPage` | `ProtectedRoute`（`requiredRole`指定なし、認証済みユーザ全員） |

`AppLayout`（U1既存）のナビゲーションに「クエリビルダー」リンクを追加する（全ユーザに表示、
管理者限定ではない）。