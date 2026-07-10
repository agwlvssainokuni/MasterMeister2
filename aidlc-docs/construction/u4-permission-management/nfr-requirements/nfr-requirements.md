# nfr-requirements.md — U4: Permission Management

`u4-permission-management-nfr-requirements-plan.md`（Q1〜Q5）の回答に基づく非機能要件。

---

## 1. Tech Stack Selection

### 1.1 `EffectivePermissionResolver`のキャッシュ方式（Q1）

- Spring Cache抽象化（`@Cacheable`/`@CacheEvict`）+ Caffeine（インプロセスキャッシュ）を採用する。
  単一ノード・小規模内部利用が前提（U1/U2/U3で一貫した判断方針）のため分散キャッシュ（Redis等）は
  不要と判断する。
- 無効化は`@CacheEvict(allEntries = true)`による全エントリ削除方式とする。原因を問わずキャッシュ
  全体を破棄する最も単純な方式であり、`business-rules.md` 2.6のstrong consistency要件を自明に
  満たす。無関係なprincipal/connectionのキャッシュも道連れで破棄される粗さはあるが、小規模内部
  利用が前提のため許容する。
- 直接`@CacheEvict`を付与できるのは、キャッシュと同じ`permission`パッケージに属する
  `PermissionAssignmentService`自身の書き込みメソッド（`setPermission`/`setAuxPermission`/
  `importPermissionsFromYaml`）のみ。
- キー設計・`@Cacheable`を付与する具体的なメソッド粒度（`resolveEffectiveTablePermission`等、
  `component-methods.md`参照）はNFR Designで確定する。

### 1.2 パッケージ境界を越えるキャッシュ無効化通知（Q2）

`permission`パッケージが所有するキャッシュは、`permission`パッケージ以外の書き込みが契機となる
場合がある（U3のスキーマ再取り込み、U4内`group`パッケージのグループ書き込み系）。いずれも
`unit-of-work.md`のパッケージ依存方向（`permission`→`schema`、`permission`→`group`の一方向）を
崩さずに無効化を伝播させる必要がある。

- Spring `ApplicationEventPublisher`/`@EventListener`（アプリケーションイベント）を採用する。
- U3の`SchemaImportService.importSchema`はインポート完了時に`SchemaReimportedEvent(connectionId)`
  を発行する（U3の`schema`パッケージの語彙のみで定義し、キャッシュ/権限という概念を一切含まない）。
- U4内`group`パッケージの`GroupService`書き込み系（`addUserToGroup`/`removeUserFromGroup`/
  `renameGroup`/`deleteGroup`）は各メソッド成功時に`GroupChangedEvent(groupId)`を発行する
  （同様に`group`パッケージの語彙のみ）。
- `permission`パッケージ側（`EffectivePermissionResolver`または専用のキャッシュ無効化
  コンポーネント）が両イベントを`@EventListener`で受信し、1.1の全削除を実行する。
- 発行元（U3 `schema`パッケージ、U4 `group`パッケージ）はいずれもキャッシュ/権限の存在を
  一切知らずに済み、`permission`パッケージ側のみが両イベント型をimportする形で依存方向を
  維持する。
- 同期/非同期・トランザクション境界（`@TransactionalEventListener`の要否）はNFR Designで
  確定する。

### 1.3 YAMLパースライブラリ（Q3）

- Jackson YAML（`jackson-dataformat-yaml`）を採用する。`ObjectMapper`ベースでYAML↔POJO
  （`PrincipalYaml`/`PermissionYaml`等）の変換を行い、REST層で既に使われているJacksonの
  アノテーション（`@JsonProperty`等）と同じ流儀でDTOを定義できる。
- 内部的にはSnakeYAMLをパーサとして利用するため実質的に別パーサを導入するわけではなく、
  Jacksonのバインド層が加わるだけ。宣言した型にのみバインドされ、SnakeYAMLのタグベース任意型
  解決（`Constructor`の誤用によるデシリアライズリスク）を経由しないため安全側に倒れる。
- バージョンはSpring Boot BOMの`jackson-dataformat-*`管理下にあり、明示指定不要
  （CLAUDE.md「Gradleバージョン管理」規約と整合）。

---

## 2. Security Requirements

### 2.1 YAMLインポート検証失敗時のエラーメッセージ詳細度（Q5）

- 検証失敗箇所の具体的な内容（該当principal・schema/table/column名、違反した検証項目
  〈`business-rules.md` 2.4の項目1-5のいずれか〉）をそのまま例外メッセージ・`ImportResult`に
  含めて返す。
- 本ユニットの全機能は管理者専用（`hasRole("ADMIN")`）のため、一般ユーザ向けの情報漏洩リスクは
  該当しない（U3 Question 6「接続テスト・取り込み失敗時のエラーメッセージ露出方針」と同方針）。

### 2.2 YAMLアップロードサイズ（Q3付随）

- アプリケーション全体の`spring.servlet.multipart.max-file-size`既定値をそのまま適用する。本
  ユニット固有の追加上限は設けない（管理者専用機能であり、想定されるYAMLファイルサイズは
  小規模なため）。

---

## 3. Reliability Requirements

### 3.1 権限データ書き込みの同時実行制御（Q4）

- 明示的な排他制御（悲観的ロック・楽観的ロック）は導入しない。DBトランザクション分離レベル
  （既定値）に委ねる。
- 管理者操作は少数の管理者による低頻度な手動操作が前提（U1/U2/U3で一貫した「小規模内部利用」
  判断方針）であり、`importPermissionsFromYaml`の全置換と`setPermission`/`setAuxPermission`の
  同時実行競合は稀と判断し、本フェーズでは明示的なロック機構を導入しない。

---

## 4. Scalability/Availability/Usability/Maintainability Requirements

本ユニット固有の新規論点は見当たらない。U1のNFR Requirements・Functional Designで確立した
「小規模内部利用が前提」「単一ノードデプロイ」という判断方針をそのまま適用する。

---

## 5. PBT Compliance（property-based-testing拡張）

- 本ステージ（NFR Requirements）で新たに適用されるPBTルールはない。PBT-09（フレームワーク
  選定）はU1で確定済み（`jqwik`）であり、本ユニットでも踏襲する（再選定なし）。
- PBT-01（Property Identification）はFunctional Design段階で適用済み
  （`business-logic-model.md` P1〜P11）。PBT-02以降はCode Generation Planning/Code
  Generation/Build and Testステージで適用される。