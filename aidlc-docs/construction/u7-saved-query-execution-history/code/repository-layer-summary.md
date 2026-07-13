# U7 Saved Query / Execution / History - リポジトリレイヤサマリ

Step 8（リポジトリレイヤ生成、実体はStep 2で先行実施済み）・Step 9（リポジトリレイヤ単体テスト）
で生成した2リポジトリの一覧。

## `SavedQueryRepository`（`cherry.mastermeister.savedquery`）

`JpaRepository<SavedQuery, Long>` を継承。

| メソッド | 説明 |
|---|---|
| `List<SavedQuery> findVisible(Long connectionId, Long userId, boolean includeRetired)` | `SavedQueryService.listQueries`が、`connectionId`＋可視性条件（`visibility=PUBLIC`または`ownerId=userId`）＋`retired`条件（`includeRetired=false`時は未廃止のみ）を満たす一覧を`name`昇順で取得するために使用（`business-rules.md` 1.1、P1） |
| `int incrementExecutionCount(Long id)`（`@Modifying(clearAutomatically = true)`） | `SavedQueryService.incrementExecutionCount`が、実効行数を`executionCount = executionCount + 1`のアトミックUPDATEで加算するために使用（`nfr-requirements.md` 2.2、楽観ロックなし、P8） |

## `QueryHistoryRepository`（`cherry.mastermeister.queryhistory`）

`JpaRepository<QueryHistory, Long>` を継承。

| メソッド | 説明 |
|---|---|
| `Page<QueryHistory> search(Long connectionId, Instant executedAtFrom, Instant executedAtTo, Long userId, String sqlTextSearch, Pageable pageable)` | `QueryHistoryService.listHistory`が、`connectionId`必須＋日時範囲/実行者/SQL部分一致（`LOWER(CAST(h.sql AS string)) LIKE ...`、大文字小文字非依存）の任意フィルタでページング取得するために使用（`business-rules.md` 5節） |

`sql`が`@Lob`（CLOB）マッピングのため、`LOWER()`関数へ直接渡すとHibernateの引数型検証で
`FunctionArgumentException`となる（Step 3で発覚、`business-logic-summary.md`記載）。
`CAST(h.sql AS string)`で文字列型へ明示変換してから`LOWER()`に渡すことで解消している。

## インデックス設計

`nfr-design-patterns.md` 2の設計判断に基づく。

- `SavedQuery`: 追加インデックスなし。想定規模が小さく（ユーザ・接続あたり数十件程度）、
  `connectionId`単体でのテーブルスキャンでも十分な性能が見込めるため（`nfr-design-patterns.md`
  2で明示的に見送り）
- `QueryHistory`: `@Table(indexes = {@Index(columnList = "connectionId, executedAt"),
  @Index(columnList = "savedQueryId")})`で2つの明示的インデックスを付与。本ユニットの内部DB
  エンティティの中で唯一リテンションポリシーを持たず無制限に増加し続けるテーブルであり
  （U1監査ログと同じ設計判断を継承）、`search`の主要フィルタ列（`connectionId`＋
  `executedAt`降順ソート）と、`QueryHistoryService.listHistory`が`getStatuses`のバッチ判定に
  用いる`savedQueryId`絞り込みの双方を専用インデックスでカバーする

## テストカバレッジ（Step 9）

| テストクラス | 検証内容 |
|---|---|
| `SavedQueryRepositoryTest` | 基本CRUD、`findVisible`（可視性/所有者/`retired`条件の組み合わせ）、`incrementExecutionCount`の並行実行整合性（20スレッド同時呼び出しで加算漏れが発生しないこと、`TransactionTemplate`で各呼び出しを個別トランザクションとして実行）を検証（6件） |
| `QueryHistoryRepositoryTest` | 基本CRUD、`params`の`JsonMapConverter`往復、`search`（日時範囲・実行者・SQL部分一致・大文字小文字非依存・ページング）を検証（7件） |

いずれも`@DataJpaTest`＋組み込みH2で実行。`incrementExecutionCount`の並行実行テストでは、
`@Modifying`付きカスタムクエリメソッドは`SimpleJpaRepository`の標準CRUDメソッドと異なり
リポジトリ呼び出し自体が自動でトランザクションを開始しないため（本番では
`SavedQueryService.incrementExecutionCount`の`@Transactional`が担う）、直接呼び出すと
`TransactionRequiredException`となることがStep 9実行時に判明した。`@DataJpaTest`既定の
外側トランザクションを`Propagation.NOT_SUPPORTED`で無効化した上で、各スレッドの呼び出しを
`TransactionTemplate`で個別の実トランザクションとして包む方式（`SchemaImportServiceTest`の
`RollbackRoundTrip`グループと同種の手法）で解決した。P1〜P10（業務ロジックの性質）は
リポジトリ層では再検証せず、`business-logic-summary.md`記載のjqwik `@Property`テスト
（`SavedQueryServiceTest`/`QueryExecutionServiceTest`/`QueryHistoryServiceTest`）に一元化
している。