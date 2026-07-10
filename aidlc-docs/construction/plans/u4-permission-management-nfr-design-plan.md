# NFR Design Plan — U4: Permission Management

## ユニット適用可否の判定

`construction/nfr-design.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`）に基づき、
U4は **実行（EXECUTE）** と判定。

- U4のNFR Requirements（`nfr-requirements.md`/`tech-stack-decisions.md`）は、`EffectivePermissionResolver`
  のキャッシュ方式（Spring Cache + Caffeine、`@CacheEvict(allEntries = true)`）とパッケージ境界を
  越える無効化通知（`ApplicationEventPublisher`/`@EventListener`）を決定済みだが、キャッシュの
  キー設計・`@Cacheable`メソッド粒度、リスナーの同期/非同期・トランザクション境界は明示的に
  「NFR Designで確定する」と先送りされている。これらを設計パターン・論理コンポーネントへ
  落とし込む必要がある。

- **Resilience Patterns**: `resiliency-baseline`拡張は無効（`aidlc-state.md`）。本ユニットの
  キャッシュ無効化はインプロセスのCaffeineキャッシュに対する単純な全削除操作であり、U1の
  `MailService`（外部SMTP呼び出し）のような外部システム障害の分離が必要な処理とは性質が異なる。
  本ユニット固有の新規Resilience Patternは設けない（個別質問は設けない）。ただし
  `@TransactionalEventListener`の例外時挙動（呼び出し元への伝播有無）はReliabilityの観点で
  Question 2に含めて確認する。

→ 上記の中から5問を構成する。

---

## Step 1: NFR Requirements成果物の分析

- [x] `nfr-requirements.md`（Tech Stack 1.1〜1.3、Security 2.1〜2.2、Reliability 3.1、PBT）確認
- [x] `tech-stack-decisions.md`（決定事項8件、新規依存関係、イベント設計・Jackson YAML選定の補足）確認
- [x] `functional-design/component-methods.md`相当（`inception/application-design/component-methods.md`
      の`EffectivePermissionResolver`シグネチャ6メソッド）確認
- [x] `functional-design/domain-entities.md`（`Group`/`GroupMember`/`PermissionAssignment`/
      `AuxPermissionAssignment`の一意制約）確認
- [x] `functional-design/business-rules.md`（2.4 YAMLインポート検証項目1-5、2.5-2.6 実効権限解決・
      強整合性要件）確認
- [x] `docs/PROJECT_STRUCTURE.md`（`group/`・`permission/`パッケージ、permissionはgroupに依存）確認
- [x] U3 `nfr-design-patterns.md`/`logical-components.md`（`ConnectionPoolRegistry`のパッケージ
      配置判断・インデックス実装パターン）を前提として参照し、類似論点の判断基準を揃える

---

## Step 5: 回答収集・確定

- [x] Q1 = A（キャッシュキー: メソッドごとに専用キャッシュ名、`SimpleKeyGenerator`）
- [x] Q2 = A（`@TransactionalEventListener(phase = AFTER_COMMIT)`、`fallbackExecution=false`）
- [x] Q3 = A（`group`/`permission`パッケージ分割、`EffectivePermissionResolver`は`common`不配置）
- [x] Q4 = A（`GroupMember`に`(userId, groupId)`追加インデックス）
- [x] Q5 = A（命令的validation、Jakarta Bean Validation不採用）

全回答に曖昧さなし（追加質問不要）。

## 成果物生成タスク（`construction/nfr-design.md` Step 6）

- [ ] `aidlc-docs/construction/u4-permission-management/nfr-design/nfr-design-patterns.md`
- [ ] `aidlc-docs/construction/u4-permission-management/nfr-design/logical-components.md`

---

## Question 1: Performance/Tech Stack — キャッシュキー設計・`@Cacheable`メソッド粒度

`nfr-requirements.md` 1.1で「キー設計・`@Cacheable`を付与する具体的なメソッド粒度はNFR Designで
確定する」と先送りされていた論点。`EffectivePermissionResolver`は6メソッドを持つ
（`component-methods.md`）:

```
Permission resolveEffectiveTablePermission(Long userId, Long connectionId, String schema, String table)
Map<String, Permission> resolveEffectiveColumnPermissions(Long userId, Long connectionId, String schema, String table)
boolean canCreate(Long userId, Long connectionId, String schema, String table)
boolean canDelete(Long userId, Long connectionId, String schema, String table)
List<String> listAccessibleSchemas(Long userId, Long connectionId)
List<String> listAccessibleTables(Long userId, Long connectionId, String schema)
```

このうち4メソッド（`resolveEffectiveTablePermission`/`resolveEffectiveColumnPermissions`/
`canCreate`/`canDelete`）は引数型シグネチャが`(Long, Long, String, String)`で完全に一致する。
単一キャッシュ名を全メソッドで共有しSpring既定の`SimpleKeyGenerator`（引数値のみからキー生成し
メソッド識別子を含まない）に任せた場合、同一引数で呼び出された異なるメソッドのキャッシュエントリが
衝突し、戻り値の型が異なる（`Permission` vs `boolean`）ため`ClassCastException`、または偶然
キャストが通ってしまう場合は誤った権限判定結果を返す致命的なバグとなる。

A. 6メソッドそれぞれに専用のキャッシュ名を割り当てる（例:
   `effectivePermissions.table` / `effectivePermissions.columns` / `effectivePermissions.canCreate`
   / `effectivePermissions.canDelete` / `effectivePermissions.schemas` /
   `effectivePermissions.tables`）。各メソッドには`@Cacheable(cacheNames = "...")`のみを付与し、
   キー生成はSpring既定の`SimpleKeyGenerator`（引数値から自動生成）に委ねる。無効化側
   （`PermissionAssignmentService`書き込みメソッド、および後述のイベントリスナー）は
   `@CacheEvict(cacheNames = {6キャッシュ名全て}, allEntries = true)`（`@Caching`不要、配列指定の
   単一アノテーションで足りる）で全キャッシュを一括削除する。キャッシュ名を分けることでメソッド間の
   キー衝突が構造的に発生しなくなる（推奨）
B. 単一キャッシュ名を全メソッドで共有し、`key = "#root.methodName + ':' + #userId + ':' + ..."`の
   ようなSpEL式を各`@Cacheable`に明示指定してメソッド識別子をキーに含める
C. その他（具体的なキー設計・メソッド粒度を指定）

[Answer]: A

---

## Question 2: Reliability/Tech Stack — キャッシュ無効化イベントリスナーの実行方式・トランザクション境界

`nfr-requirements.md` 1.2・`tech-stack-decisions.md`補足で「同期/非同期・トランザクション境界
（`@TransactionalEventListener(phase = AFTER_COMMIT)`の要否）はNFR Designで確定する」と先送り
されていた論点。`business-rules.md` 2.6は「権限変更の直後に`EffectivePermissionResolver`を
呼び出した場合、必ず変更後の値が返らなければならない（strong consistency）」と規定している。

書き込みメソッド（`PermissionAssignmentService`/`GroupService`）内で`ApplicationEventPublisher.
publishEvent(...)`を呼んだ時点で通常の`@EventListener`（同期・トランザクション境界を意識しない）が
即座に発火すると、書き込みトランザクションがコミットされる**前**にキャッシュが削除される。その
削除直後・コミット完了前の間隙で別スレッドが`EffectivePermissionResolver`を呼び出すと、DBには
まだ旧い値しか存在しないためキャッシュには**旧い値が再キャッシュ**されてしまい、その後書き込み
トランザクションがコミットされても、次の呼び出しが同じキャッシュエントリを再利用する限り
2.6の強整合性要件に違反する。

A. `@TransactionalEventListener(phase = AFTER_COMMIT)`（同期実行、`fallbackExecution`は既定値
   `false`のまま）を`permission`パッケージのキャッシュ無効化コンポーネントに付与する。書き込み
   トランザクションのコミットが確定した**後**にのみキャッシュを削除するため、上記の間隙が
   構造的に発生しない。`fallbackExecution`を`true`にしない理由: `PermissionAssignmentService`・
   `GroupService`の書き込みメソッド、`SchemaImportService.importSchema`はいずれも
   メソッド全体に`@Transactional`が付与されている（`business-rules.md`・U3 `nfr-design-patterns.md`
   4.2）ため、トランザクション外でイベントが発行されるケースは想定しない。万一トランザクションが
   付与し忘れられた場合は`fallbackExecution=false`によりリスナーが発火せず無効化漏れとして
   顕在化させ、サイレントな2.6違反を防ぐ。リスナー内で例外が発生した場合の追加のtry-catchは
   行わない（Spring既定動作でAFTER_COMMIT同期処理中の例外はログ出力のみで、既にコミット済みの
   書き込み結果には影響しない。インプロセスのCaffeineキャッシュへの`allEntries=true`削除操作は
   実質的に例外が発生し得ないため、追加の防御は過剰）（推奨）
B. 通常の`@EventListener`（同期・トランザクション境界を意識しない）を採用する
C. `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`（別スレッドでの非同期実行）を
   採用する
D. その他（具体的な実行方式・トランザクション境界を指定）

[Answer]: A

---

## Question 3: Logical Components — 主要コンポーネントのパッケージ配置

`docs/PROJECT_STRUCTURE.md`は`group/`（ユーザグループの作成・所属管理）と`permission/`
（テーブル/カラム権限、YAMLエクスポート/インポート、**groupに依存**）を別パッケージとして
明示している。`domain-entities.md`も「groupドメイン」「permissionドメイン」の2ドメインに分けて
記述している。U3の`ConnectionPoolRegistry`（`nfr-design-patterns.md` 2.1）は「複数ユニットが
対等に実装を追加する拡張ポイント」ではなく「単一実装のサービスを他ユニットが直接参照する関係」
であるため`common`へ配置しなかった判断があり、`EffectivePermissionResolver`（U5/U6/U7から
サービス層で直接呼び出される想定、`business-rules.md` 3節）にも同じ判断基準が当てはまるか
確認したい。

A. `Group`・`GroupMember`・`GroupService`は`cherry.mastermeister.group`パッケージに、
   `PermissionAssignment`・`AuxPermissionAssignment`・`PrincipalType`・`Permission`・
   `AuxPermissionType`・`PermissionAssignmentService`・`EffectivePermissionResolver`・
   キャッシュ無効化リスナー（Question 2のコンポーネント）は`cherry.mastermeister.permission`
   パッケージに配置する。依存方向は`permission → group`の一方向のみ（`docs/PROJECT_STRUCTURE.md`
   の記載通り）、`permission → schema`（U3、`connectionId`はFKだが`SchemaTable`への直接参照は
   物理名ベースのため実質的な依存は薄い）も同方向を維持する。`EffectivePermissionResolver`は
   `ConnectionPoolRegistry`と同じ理由（U4が所有する単一実装サービスをU5/U6/U7が直接参照する
   関係であり、複数ユニットが実装を追加する拡張ポイントではない）で`common`には配置しない
   （推奨）
B. `EffectivePermissionResolver`のみを`cherry.mastermeister.common`パッケージに切り出す
   （将来のU5/U6/U7からの参照を見据えて）
C. その他（具体的な配置方針を指定）

[Answer]: A

---

## Question 4: Scalability/Performance — GroupMember/PermissionAssignment/AuxPermissionAssignmentのインデックス実装方針

`domain-entities.md`で`GroupMember`は`(groupId, userId)`、`PermissionAssignment`は
`(principalType, principalId, connectionId, schemaName, tableName, columnName)`、
`AuxPermissionAssignment`は`(principalType, principalId, connectionId, schemaName, tableName,
auxType)`の一意制約を持つと決定済み。U3の`nfr-design-patterns.md` 5.1（一意制約のみで賄う判断）
との整合を確認したい。

- `PermissionAssignment`/`AuxPermissionAssignment`は一意制約の先頭2列が`(principalType,
  principalId)`のため、`principalId`起点のクエリ（`EffectivePermissionResolver`の実効権限解決、
  `business-rules.md` 1.3のグループ削除カスケード）はいずれも既存の一意制約が複合インデックスの
  先頭一致として自然にカバーする。
- 一方`GroupMember`の一意制約`(groupId, userId)`は`groupId`起点のクエリ（グループ削除時の
  カスケード削除、`business-rules.md` 1.3）はカバーするが、`EffectivePermissionResolver`の
  グループ合成（判定ロジック要旨3.「ユーザが複数グループに所属する場合はグループごとに解決」、
  `userId`起点で所属グループを検索する）はカバーしない（複合インデックスは左端列からの前方一致
  のみ有効なため、`userId`のみを条件とする検索では先頭列`groupId`を経由できずインデックスが
  効かない）。`EffectivePermissionResolver`はキャッシュミス時に毎回この検索を行う想定である。

A. `GroupMember`に`(userId, groupId)`への追加の明示的インデックス（`@Table(indexes = {...})`）を
   追加する。`PermissionAssignment`/`AuxPermissionAssignment`は一意制約のみに委ね、追加
   インデックスは設けない（推奨）
B. `GroupMember`・`PermissionAssignment`・`AuxPermissionAssignment`いずれも一意制約のみに委ね、
   追加インデックスは設けない（`userId`起点の検索はテーブルスキャンで許容する。小規模内部利用が
   前提のため）
C. その他（具体的なインデックス方針を指定）

[Answer]: A

---

## Question 5: Security/Logical Components — YAMLインポート検証（`business-rules.md` 2.4項目2〜5）の実装方式

`business-rules.md` 2.4は`importPermissionsFromYaml`の検証項目を5つ規定している（YAML構文不正、
必須フィールド欠落、enum値不正、参照整合性違反、同一ファイル内重複定義）。Jackson YAML
（`jackson-dataformat-yaml`、Question 3 = Bで確定済み）はYAML→POJO（`PrincipalYaml`/
`PermissionYaml`等）へのバインドは行うが、必須フィールド欠落時にデフォルトで例外を投げず
`null`のまま許容する。またU1〜U3の`business-rules.md`（`setPermission`の2.1含む）はいずれも
「順にチェックし、いずれかに違反する場合は例外」という命令的validationパターンを一貫して
採用しており、本プロジェクトでJakarta Bean Validation（`@NotBlank`等のアノテーション +
`Validator`）を使った前例はない。

A. Jacksonでバインドした`PrincipalYaml`/`PermissionYaml`等のPOJOツリーに対し、
   `PermissionAssignmentService.importPermissionsFromYaml`内で`business-rules.md` 2.4項目1〜5を
   順に命令的にチェックする（`setPermission`の2.1と同じパターンを踏襲）。項目1（YAML構文不正）は
   Jacksonのパース呼び出し（`ObjectMapper.readValue`）を`try-catch`し
   `JsonProcessingException`系を`PermissionYamlFormatException`にラップする。項目2（必須フィールド
   欠落）はバインド後のPOJOに対する`null`/空文字チェック、項目3（enum値不正）はYAML上の値と
   Java enumの対応を明示的に検証するデシリアライザまたはバインド後チェック、項目4（参照整合性）・
   項目5（同一ファイル内重複定義）はリスト全体を横断する検証のため、そもそもフィールド単位の
   アノテーションでは表現できない。Jakarta Bean Validationは導入しない（新規依存追加を避け、
   既存の命令的validationパターンとの一貫性を優先する）（推奨）
B. `spring-boot-starter-validation`を新規に追加し、`PrincipalYaml`/`PermissionYaml`に
   `@NotBlank`/`@NotNull`等を付与して項目2（必須フィールド欠落）のみアノテーションベースの
   検証に委ね、項目1・3〜5は命令的チェックのまま残す（ハイブリッド）
C. その他（具体的な実装方式を指定）

[Answer]: A

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。