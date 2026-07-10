# nfr-design-patterns.md — U4: Permission Management

`u4-permission-management-nfr-design-plan.md`（Question 1〜5、全回答A）に基づく設計パターン。

---

## 1. Performance Patterns

### 1.1 EffectivePermissionResolverのキャッシュキー設計・`@Cacheable`メソッド粒度（Question 1）

- `EffectivePermissionResolver`の6メソッドそれぞれに専用のキャッシュ名を割り当てる（Spring Cache
  抽象化 + Caffeine、`tech-stack-decisions.md` #1）:
  - `effectivePermissions.table` — `resolveEffectiveTablePermission(Long, Long, String, String)`
  - `effectivePermissions.columns` — `resolveEffectiveColumnPermissions(Long, Long, String, String)`
  - `effectivePermissions.canCreate` — `canCreate(Long, Long, String, String)`
  - `effectivePermissions.canDelete` — `canDelete(Long, Long, String, String)`
  - `effectivePermissions.schemas` — `listAccessibleSchemas(Long, Long)`
  - `effectivePermissions.tables` — `listAccessibleTables(Long, Long, String)`
- 各メソッドには`@Cacheable(cacheNames = "...")`のみを付与し、キー生成はSpring既定の
  `SimpleKeyGenerator`（引数値から自動生成）に委ねる。SpEL式によるキー明示指定は行わない。
- 理由: `resolveEffectiveTablePermission`/`resolveEffectiveColumnPermissions`/`canCreate`/
  `canDelete`の4メソッドは引数型シグネチャ`(Long, Long, String, String)`が完全に一致する。
  単一キャッシュ名を共有すると`SimpleKeyGenerator`（メソッド識別子を含まず引数値のみから
  キー生成）が同一引数の異なるメソッド呼び出しに同一キーを割り当ててしまい、戻り値型の
  不一致（`Permission` vs `boolean`）による`ClassCastException`、またはキャストが偶然通る
  場合は誤った権限判定結果を返す致命的なバグとなる。メソッドごとにキャッシュ名を分けることで
  この衝突が構造的に発生しなくなる。
- 各キャッシュの`Caffeine`設定（`maximumSize`/`expireAfterWrite`等の具体値）は
  `application.yml`の`spring.cache.caffeine.spec`で6キャッシュ共通のデフォルト値を指定する
  （個別キャッシュごとのチューニングは本ステージでは不要と判断、実測値に基づく調整は将来対応）。

### 1.2 キャッシュ無効化の対象キャッシュ指定（Question 1関連）

- 無効化側（`PermissionAssignmentService`書き込みメソッド、および1.3のキャッシュ無効化
  リスナー）は`@CacheEvict(cacheNames = {"effectivePermissions.table",
  "effectivePermissions.columns", "effectivePermissions.canCreate",
  "effectivePermissions.canDelete", "effectivePermissions.schemas",
  "effectivePermissions.tables"}, allEntries = true)`（`@Caching`は不要、配列指定の単一
  アノテーションで足りる）で6キャッシュを一括削除する。
- `tech-stack-decisions.md` #2の「`allEntries = true`による全エントリ削除」方針をそのまま
  6キャッシュ分に適用する（`userId`/`connectionId`単位の部分無効化は行わない。権限変更・
  グループ変更・スキーマ再取り込みいずれも影響範囲の特定コストが削除コストを上回るため）。

---

## 2. Reliability Patterns

### 2.1 キャッシュ無効化イベントリスナーの実行方式・トランザクション境界（Question 2）

- `permission`パッケージに配置するキャッシュ無効化コンポーネント（`PermissionCacheInvalidationListener`）
  に`@TransactionalEventListener(phase = AFTER_COMMIT)`（同期実行、`fallbackExecution`は
  既定値`false`のまま）を付与する。
- 対象イベント: `SchemaReimportedEvent`（U3 `schema`パッケージ発行）・`GroupChangedEvent`
  （U4 `group`パッケージ発行）に加え、`PermissionAssignmentService`自身の書き込み系
  （`setPermission`/`setAuxPermission`/`importPermissionsFromYaml`）にも同一メソッドを直接
  `@CacheEvict`として付与する（パッケージ内で完結するため、わざわざ自己宛てイベントを発行
  する必要はない。`tech-stack-decisions.md` #2の「`permission`パッケージ自身の書き込み
  メソッドにのみ直接付与」方針を踏襲）。
- 理由: `business-rules.md` 2.6は「権限変更の直後に`EffectivePermissionResolver`を呼び出した
  場合、必ず変更後の値が返らなければならない（strong consistency）」と規定している。通常の
  `@EventListener`（同期・トランザクション境界を意識しない）を採用した場合、書き込み
  トランザクションのコミット**前**にキャッシュが削除されるため、削除直後・コミット完了前の
  間隙で別スレッドが`EffectivePermissionResolver`を呼び出すとDBの旧い値がキャッシュに
  再キャッシュされ、その後トランザクションがコミットされても次の呼び出しが同じキャッシュ
  エントリを再利用する限り2.6の強整合性要件に違反する。`AFTER_COMMIT`はコミット確定後にのみ
  無効化を実行するため、この間隙が構造的に発生しない。
- `fallbackExecution`を`true`にしない理由: `PermissionAssignmentService`・`GroupService`の
  書き込みメソッド、`SchemaImportService.importSchema`（U3）はいずれもメソッド全体に
  `@Transactional`が付与されている（U3 `nfr-design-patterns.md` 4.2、本書2.2）ため、
  トランザクション外でイベントが発行されるケースは想定しない。万一トランザクションが付与
  し忘れられた場合は`fallbackExecution=false`によりリスナーが発火せず無効化漏れとして
  顕在化させ、サイレントな2.6違反を防ぐ。
- リスナー内での追加のtry-catchは行わない。Spring既定動作で`AFTER_COMMIT`同期処理中の例外は
  ログ出力のみで、既にコミット済みの書き込み結果には影響しない。インプロセスのCaffeine
  キャッシュへの`allEntries=true`削除操作は実質的に例外が発生し得ないため、追加の防御は過剰。

### 2.2 イベント発行元パッケージの独立性（`tech-stack-decisions.md`補足の確定）

- `SchemaReimportedEvent`（発行元: U3 `schema`パッケージ）・`GroupChangedEvent`（発行元:
  U4 `group`パッケージ）は、いずれも発行元パッケージ自身の語彙のみで定義し、キャッシュ・
  権限という概念を一切含まない。発行元パッケージは受信側（`permission`パッケージ）の存在を
  知らない。
- `permission`パッケージ側（`PermissionCacheInvalidationListener`）が両イベント型をimportして
  `@TransactionalEventListener`で購読することで、パッケージ依存方向（`permission → schema`、
  `permission → group`）を`docs/PROJECT_STRUCTURE.md`の既定方針のまま維持する。

---

## 3. Logical Components Patterns

### 3.1 主要コンポーネントのパッケージ配置（Question 3）

- `Group`・`GroupMember`・`GroupService`は`cherry.mastermeister.group`パッケージに配置する。
- `PermissionAssignment`・`AuxPermissionAssignment`・`PrincipalType`・`Permission`・
  `AuxPermissionType`・`PermissionAssignmentService`・`EffectivePermissionResolver`・
  `PermissionCacheInvalidationListener`（2.1）は`cherry.mastermeister.permission`パッケージに
  配置する。
- **依存方向**: `permission → group`の一方向のみ（`docs/PROJECT_STRUCTURE.md`の記載通り）。
  `permission → schema`（U3、`connectionId`はFKだが`SchemaTable`への直接参照は物理名ベースの
  ため実質的な依存は薄い）も同方向を維持する。`group`パッケージ側は`permission`の何も参照
  せず、循環参照は生じない。
- **将来のU5/U6/U7参照**: `EffectivePermissionResolver`は将来的にU5（Master Data
  Maintenance）・U6（Query Builder）・U7（Saved Query / Execution / History）からもサービス層
  で直接呼び出される想定（`business-rules.md` 3節）だが、`permission`パッケージから直接
  参照させる。U1の`DialectStrategy`（`common.dialect`パッケージ、複数ユニットが対等に実装を
  追加する拡張ポイント）とは事情が異なり、U3の`ConnectionPoolRegistry`（U3
  `nfr-design-patterns.md` 2.1）と同じ理由——`permission`パッケージが所有する単一実装の
  サービスをU5/U6/U7が直接参照する関係であり、複数ユニットが実装を追加する拡張ポイントでは
  ない——で`common`には配置しない。

---

## 4. Scalability/Performance Patterns（インデックス）

### 4.1 GroupMember/PermissionAssignment/AuxPermissionAssignmentのインデックス実装方針（Question 4）

- `PermissionAssignment`/`AuxPermissionAssignment`は一意制約（`domain-entities.md`で確定済みの
  `(principalType, principalId, connectionId, schemaName, tableName, columnName)`・
  `(principalType, principalId, connectionId, schemaName, tableName, auxType)`）のみに委ねる。
  追加の明示的インデックスは設けない。一意制約の先頭2列が`(principalType, principalId)`のため、
  `principalId`起点のクエリ（`EffectivePermissionResolver`の実効権限解決、`business-rules.md`
  1.3のグループ削除カスケード）はいずれも既存の一意制約が複合インデックスの先頭一致として
  自然にカバーする（U3 `nfr-design-patterns.md` 5.1と同じ考え方）。
- `GroupMember`には一意制約`(groupId, userId)`に加え、`@Table(indexes = {...})`による
  `(userId, groupId)`への追加の明示的インデックスを付与する。
- 理由: `GroupMember`の一意制約`(groupId, userId)`は`groupId`起点のクエリ（グループ削除時の
  カスケード削除、`business-rules.md` 1.3）はカバーするが、`EffectivePermissionResolver`の
  グループ合成（判定ロジック要旨3.「ユーザが複数グループに所属する場合はグループごとに
  解決」、`userId`起点で所属グループを検索する）はカバーしない（複合インデックスは左端列
  からの前方一致のみ有効なため、`userId`のみを条件とする検索では先頭列`groupId`を経由できず
  インデックスが効かない）。`EffectivePermissionResolver`はキャッシュミス時に毎回この検索を
  行う想定であり、テーブルスキャンを許容すると1.のキャッシュ導入効果がキャッシュミス時の
  レイテンシで損なわれるため、追加インデックスによって明示的にカバーする。

---

## 5. Security Patterns

### 5.1 YAMLインポート検証（`business-rules.md` 2.4項目1〜5）の実装方式（Question 5）

- Jacksonでバインドした`PrincipalYaml`/`PermissionYaml`等のPOJOツリーに対し、
  `PermissionAssignmentService.importPermissionsFromYaml`内で`business-rules.md` 2.4項目1〜5を
  順に命令的にチェックする（`setPermission`の2.1と同じ「順にチェックし、いずれかに違反する
  場合は例外」パターンを踏襲、本プロジェクトでJakarta Bean Validationを使った前例はない）。
- 項目1（YAML構文不正）: Jacksonのパース呼び出し（`ObjectMapper.readValue`）を`try-catch`し、
  `JsonProcessingException`系を`PermissionYamlFormatException`にラップする。
- 項目2（必須フィールド欠落）: バインド後のPOJOに対する`null`/空文字チェック。
- 項目3（enum値不正）: YAML上の値とJava enumの対応を明示的に検証するデシリアライザまたは
  バインド後チェック。
- 項目4（参照整合性）・項目5（同一ファイル内重複定義）: リスト全体を横断する検証のため、
  そもそもフィールド単位のアノテーションでは表現できない。POJOツリー全体を走査する専用の
  検証メソッド内で実施する。
- Jakarta Bean Validation（`spring-boot-starter-validation`）は導入しない。新規依存追加を
  避け、既存の命令的validationパターンとの一貫性を優先する。

---

## 6. Resilience Patterns

- 本ユニット固有の新規パターンはない（`u4-permission-management-nfr-design-plan.md`のユニット
  適用可否判定を参照）。`resiliency-baseline`拡張は無効（`aidlc-state.md`）。
- 本ユニットのキャッシュ無効化はインプロセスのCaffeineキャッシュに対する単純な全削除操作
  （2.1参照）であり、U1の`MailService`（外部SMTP呼び出し）のような外部システム障害の分離が
  必要な処理とは性質が異なる。`@TransactionalEventListener`の例外時挙動は2.1で確認済み
  （Spring既定動作でログ出力のみ、追加のtry-catchは過剰）であり、独立したResilience Pattern
  としては扱わない。

---

## 7. PBT適用性（property-based-testing拡張）

- 本ステージ（NFR Design）ではPBT-09（フレームワーク選定）を含むいずれのPBTルールも対象外
  （`property-based-testing.md`のEnforcement Integration表: NFR Designは対象外ステージ）。
  U1/U2/U3のNFR Design承認時と同様、N/Aとして扱う。