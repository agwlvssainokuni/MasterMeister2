# tech-stack-decisions.md — U4: Permission Management

`nfr-requirements.md`に基づく技術選定の決定事項一覧。

| # | 決定事項 | 選定 | 根拠 |
|---|---|---|---|
| 1 | `EffectivePermissionResolver`のキャッシュ実装 | Spring Cache抽象化 + Caffeine（インプロセスキャッシュ、分散キャッシュ不採用） | Question 1 = A |
| 2 | キャッシュ無効化方式 | `@CacheEvict(allEntries = true)`による全エントリ削除。`permission`パッケージ自身の書き込みメソッド（`PermissionAssignmentService`）にのみ直接付与 | Question 1 = A |
| 3 | パッケージ境界を越える無効化通知 | Spring `ApplicationEventPublisher`/`@EventListener`。`SchemaReimportedEvent`（U3 `schema`パッケージ発行）・`GroupChangedEvent`（U4 `group`パッケージ発行）を`permission`パッケージが購読 | Question 2 = A |
| 4 | YAMLパースライブラリ | Jackson YAML（`jackson-dataformat-yaml`）、`ObjectMapper`ベースのPOJOバインド | Question 3 = B |
| 5 | YAMLアップロードサイズ上限 | アプリ全体の`spring.servlet.multipart.max-file-size`既定値を流用、本ユニット固有の追加上限なし | Question 3付随 |
| 6 | 権限データ書き込みの同時実行制御 | 明示的ロックなし。DBトランザクション分離レベル既定値に委ねる | Question 4 = A |
| 7 | YAMLインポート検証エラーメッセージの詳細度 | 違反箇所の具体的内容（principal・schema/table/column名等）を含めて返す | Question 5 = A |
| 8 | PBT（Property-Based Testing）フレームワーク | `jqwik`（U1で確定済み、再選定なし） | U1 `tech-stack-decisions.md` #16を踏襲 |

---

## 依存関係追加（本ユニットで新規追加）

| 依存関係 | スコープ | バージョン管理 | 用途 |
|---|---|---|---|
| `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` | `implementation` | Spring Boot BOM管理下（明示バージョン指定不要） | YAMLエクスポート/インポートのPOJOバインド（Question 3 = B） |
| `spring-boot-starter-cache` + Caffeine（`com.github.ben-manes.caffeine:caffeine`） | `implementation` | Spring Boot BOM管理下（明示バージョン指定不要） | `EffectivePermissionResolver`のインプロセスキャッシュ（Question 1 = A） |

いずれもSpring Boot BOMがバージョンを提供するため、CLAUDE.md「Gradleバージョン管理」規約が
主眼とするRDBMS系ドライバ（U3、Spring Boot BOM未提供）のような明示バージョン管理は不要。

---

## パッケージ境界を越えるイベント設計の補足（Question 2 = A）

- `SchemaReimportedEvent`（発行元: U3 `schema`パッケージ）と`GroupChangedEvent`（発行元: U4
  `group`パッケージ）は、いずれも発行元パッケージ自身の語彙のみで定義し、キャッシュ・権限という
  概念を一切含まない。発行元パッケージは受信側（`permission`パッケージ）の存在を知らない。
- `permission`パッケージ側が両イベント型をimportして`@EventListener`で購読することで、
  パッケージ依存方向（`permission`→`schema`、`permission`→`group`）を`unit-of-work.md`の既定
  方針のまま維持する。
- 具体的なリスナー実装クラス・同期/非同期・トランザクション境界（`@TransactionalEventListener
  (phase = AFTER_COMMIT)`の要否、`importSchema`/グループ書き込みのトランザクション内で発行する
  イベントがロールバック時に誤って無効化を実行しないための考慮）はNFR Designで確定する。

---

## Jackson YAML選定の補足（Question 3 = B）

- `jackson-dataformat-yaml`は内部的にSnakeYAMLをパーサとして利用するため、実質的に別パーサを
  導入するわけではない。Jacksonのバインド層が加わることで、宣言した型（`PrincipalYaml`等）に
  のみバインドされ、SnakeYAMLのタグベース任意型解決（`Constructor`の誤用によるデシリアライズ
  リスク）を経由しない点が、素のSnakeYAML直接利用に対する優位点。
- REST層の既存DTOと同じJacksonアノテーション規約でYAML用DTOを定義できるため、実装の一貫性が
  向上する。