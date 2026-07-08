# U1 Platform Foundation - リポジトリレイヤサマリ

Step 8（リポジトリレイヤ生成）・Step 9（リポジトリレイヤ単体テスト）で生成した
`AuditLogRepository`の一覧。

## `AuditLogRepository`

`JpaRepository<AuditLog, Long>` を継承。標準の`save`/`saveAll`/`findById`/`delete`/
`deleteAll`等に加え、以下のカスタムクエリメソッドを定義する。

| メソッド | 説明 |
|---|---|
| `Page<AuditLog> search(AuditLogFilterCriteria criteria, Pageable pageable)` | `dateFrom`/`dateTo`/`userId`/`eventCategory`/`eventType`の5条件をAND結合で絞り込み、ページングして返す。各条件はSpring Data JPAのSpELパラメータ式（`:#{#criteria.field}`）により独立してnull許容（`IS NULL OR ...`）とし、未指定条件は絞り込みに使用しない。`AuditLogService.search`から呼び出される（呼び出し元がソート条件を`Pageable`に含めて渡す。固定`occurredAt`降順ソートはサービス層の責務）。 |

### 実装方針

`@Query`のJPQLに1本化し、5条件の全組み合わせ（32通り）に対して動的クエリメソッド名を
作らず、SpELの`:#{#criteria.field} IS NULL OR ...`パターンで単一クエリに集約した。
`AuditLogFilterCriteria`（record）をそのまま`@Param`として渡し、各フィールドへは
`#criteria.dateFrom`のようにSpELでアクセスする。

## インデックス設計

`AuditLog`エンティティ（`@Table(name = "audit_log")`）に定義済みの3インデックス
（Step 2生成時点、`search`メソッドの絞り込み条件・ソート条件に対応）:

| インデックス名 | 対象カラム | 用途 |
|---|---|---|
| `idx_audit_log_occurred_at` | `occurred_at` | `dateFrom`/`dateTo`範囲検索、および固定`occurredAt`降順ソート |
| `idx_audit_log_user_id` | `user_id` | `userId`完全一致検索 |
| `idx_audit_log_category_type` | `event_category, event_type` | `eventCategory`/`eventType`の複合絞り込み（`eventType`単独検索時もリーディングカラム`event_category`省略時は複合インデックスの先頭列一致とならない点に留意し、将来的に`eventType`単独インデックスが必要になった場合は別途追加を検討） |

## テストカバレッジ（Step 9）

| テストクラス | 検証内容 |
|---|---|
| `AuditLogRepositoryTest` | **P2**（Round-trip）: `TestEntityManager.persistFlushFind`により実DB書き込み/読み出しを経由し、`AuditLog`全フィールド（`id`含む、`userId`/`connectionId`/`targetDescription`/`summaryMessage`のnull許容ケース込み）が保存前後で一致することをjqwik `@Property`で検証（1000ケース）。基本CRUD（`saveAssignsGeneratedId`、`deleteRemovesEntity`）、`search`のフィルタ・ソート（`searchReturnsAllRowsDescendingByOccurredAtWhenCriteriaAllNull`、`searchFiltersByUserIdEventCategoryEventTypeAndDateRange`）をexample-basedテストで追加検証（4件）。 |