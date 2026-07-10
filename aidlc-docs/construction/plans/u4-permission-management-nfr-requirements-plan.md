# NFR Requirements Plan — U4: Permission Management

## ユニット適用可否の判定

`construction/nfr-requirements.md` の実行/スキップ判定基準（top-level `.claude/CLAUDE.md`）に
基づき、U4は **実行（EXECUTE）** と判定。

- **Tech Stack Selection**: `EffectivePermissionResolver`（`business-rules.md` 2.5-2.6）の
  strong consistency要件を満たすキャッシュ実装方式が未確定。U3の`SchemaImportService.importSchema`
  （再取り込み）をキャッシュ無効化契機に含める旨をFunctional Design Step 8レビューで追加した
  ばかりだが（`business-rules.md` 2.6、`business-logic-model.md` P11）、U3→U4間の実装上の連携
  方式（パッケージ依存方向を保ったままどう通知するか）が未確定。
- **Security**: YAMLインポートのパース方式・アップロードサイズ制限が未確定。YAMLインポート
  検証失敗時（`business-rules.md` 2.4）のエラーメッセージ詳細度も未確定。
- **Reliability**: `importPermissionsFromYaml`の全置換（delete→rebuild、`business-rules.md` 2.4）
  と`setPermission`/`setAuxPermission`の同時実行時の競合制御方針が未確定。
- **Scalability/Availability/Usability/Maintainability**: 本ユニット固有の新規論点は見当たらない
  （U1のNFR Requirements・Functional Designでカバー済み。個別質問は設けない）。

→ 上記の中から5問を構成する。

---

## Step 1: Functional Design成果物の分析

- [x] `domain-entities.md`（`Group`/`GroupMember`、`PermissionAssignment`/
      `AuxPermissionAssignment`/`PrincipalType`/`Permission`/`AuxPermissionType`）確認
- [x] `business-rules.md`（グループ管理1.1-1.4、権限設定2.1-2.6〈2.6はStep 8レビューで
      U3連携契機を追記済み〉、API認可）確認
- [x] `business-logic-model.md`（フロー1-5、Testable Properties P1-P11）確認
- [x] `frontend-components.md`（`features/group/`・`features/permission/`のコンポーネント構成、
      `features/permission/`→`features/group/`の一方向依存、ルーティング）確認
- [x] U1 `nfr-requirements.md`/`tech-stack-decisions.md`（HikariCP既定設定、ログ出力方式、
      「小規模内部利用が前提」という判断方針）とU3 `nfr-requirements.md`（接続タイムアウト・
      エラーメッセージ露出方針の先例）を前提として参照し、重複質問を避ける

---

## 成果物生成タスク（`construction/nfr-requirements.md` Step 6）

- [x] `aidlc-docs/construction/u4-permission-management/nfr-requirements/nfr-requirements.md`
- [x] `aidlc-docs/construction/u4-permission-management/nfr-requirements/tech-stack-decisions.md`

---

## Question 1: Tech Stack/Performance — EffectivePermissionResolverのキャッシュ実装方式

`business-rules.md` 2.6でstrong consistency（書き込み直後は必ず最新値を返す）は決定済みだが、
実装方式が未確定。U5/U6/U7がマスタデータ操作のたびに本Facadeを呼び出す想定であり、
キャッシュなしでは`GroupMember`・`PermissionAssignment`/`AuxPermissionAssignment`への問い合わせが
リクエストごとに発生する。

A. Spring Cache抽象化（`@Cacheable`/`@CacheEvict`）+ Caffeine（インプロセスキャッシュ）を
   採用する。単一ノード・小規模内部利用が前提（U1/U2/U3で一貫した判断方針）のため分散キャッシュ
   （Redis等）は不要と判断する。キー設計・具体的なキャッシュ粒度（principal単位/
   (principal, connectionId)単位等）はNFR Designで確定する（推奨）
B. キャッシュを導入せず、呼び出しのたびにDBへ再問い合わせする（実装が最も単純でstrong
   consistencyも自明に満たすが、呼び出し頻度が高い場合の性能リスクを許容する）
C. `ConcurrentHashMap`を用いた自前のインプロセスキャッシュを実装する（Caffeine等の既存ライブラリ
   は使わない）
D. その他（具体的な方式を指定）

[Answer]: A（無効化は`@CacheEvict(allEntries = true)`による全エントリ削除方式とする。原因を
問わずキャッシュ全体を破棄する最も単純な方式であり、strong consistency要件〈2.6〉を
自明に満たす。無関係なprincipal/connectionのキャッシュも道連れで破棄される粗さはあるが、
小規模内部利用が前提のため許容する。この方針によりQ2の無効化通知〈イベントリスナー〉は
「無条件に1つの全削除メソッドを呼ぶ」だけでよくなり、`connectionId`単位の個別キー特定が
不要になる。ただし直接`@CacheEvict`を付与できるのは、キャッシュと同じ`permission`パッケージに
属する`PermissionAssignmentService`自身の書き込みメソッドのみ。`GroupService`は別パッケージ
（Q2参照）のため対象外。）

---

## Question 2: Tech Stack — 他パッケージ（U3スキーマ再取り込み・U4内group）からの
キャッシュ無効化通知方式

`business-rules.md` 2.6（Step 8レビューで追記）で、U3の`SchemaImportService.importSchema`
（再取り込み）もキャッシュ無効化契機に含めることが決定済み。ただし`unit-of-work.md`の
パッケージ依存方向はU4（`permission`）→U3（`rdbmsConnection`/`schema`）の一方向であり、
U3側から直接U4のコンポーネントを呼び出す実装は依存方向を逆転させてしまう。同様に、U4内部でも
`permission`→`group`の一方向依存（Functional Design Q7、`business-logic-model.md`フロー2手順1）
であり、`GroupService`（`group`パッケージ）の書き込み系（`addUserToGroup`/
`removeUserFromGroup`/`renameGroup`/`deleteGroup`）も`permission`パッケージが所有するキャッシュを
直接操作できない（同じ理由）。

A. Spring `ApplicationEventPublisher`/`@EventListener`（アプリケーションイベント）を用いる。
   U3の`SchemaImportService`はインポート完了時にイベント（例:
   `SchemaReimportedEvent(connectionId)`）を、`group`パッケージの`GroupService`は書き込み系の
   各メソッド成功時にイベント（例: `GroupChangedEvent(groupId)`）を、それぞれ自身のパッケージの
   語彙で発行する（いずれもキャッシュ/権限という概念を一切知らない）。`permission`パッケージ側の
   `EffectivePermissionResolver`（または専用のキャッシュ無効化コンポーネント）が両イベントを
   `@EventListener`で受信し、内部で`@CacheEvict`相当の全削除を呼ぶ。イベント型・発行元は
   それぞれU3の`schema`パッケージ・U4の`group`パッケージに置き、`permission`側がそれを参照する
   形にすることでパッケージ依存方向（`permission`→`schema`、`permission`→`group`）を保ったまま
   実装できる。同期/非同期・トランザクション境界（`@TransactionalEventListener`の要否）はNFR
   Designで確定する（推奨）
B. U3の`SchemaImportService`・`group`パッケージの`GroupService`がそれぞれU4の
   `EffectivePermissionResolver`を直接呼び出す（依存方向が逆転するため非推奨だが選択肢として
   提示）
C. 通知の仕組みを設けず、キャッシュに短時間のTTL（有効期限）のみを設定して整合性を担保する
   （2.6のstrong consistency要件と矛盾するため非推奨だが選択肢として提示）
D. その他（具体的な方式を指定）

[Answer]: A（`@CacheEvict`を直接付与できるのは、キャッシュと同じ`permission`パッケージに
属する`PermissionAssignmentService`自身の書き込みメソッドのみ（Q1参照）。U3の`importSchema`
およびU4内`group`パッケージの`GroupService`書き込み系は、いずれも自パッケージのドメインを
表すイベント〈`SchemaReimportedEvent`/`GroupChangedEvent`〉を発行するだけにとどめ、
`permission`パッケージ側が`@EventListener`で受信して無効化する。これによりU3・`group`いずれも
キャッシュ/権限という`permission`固有の関心事を一切知らずに済み、パッケージ依存方向
〈`permission`→`schema`、`permission`→`group`〉を保てる。）

---

## Question 3: Security/Tech Stack — YAMLインポートのパースライブラリ・アップロードサイズ

`business-rules.md` 2.3-2.4でYAML構造・検証チェックリストは決定済みだが、パースに使う
ライブラリとアップロードサイズの上限方針が未確定。

A. Spring Boot依存に既に含まれる`SnakeYAML`をそのまま利用する。アップロードサイズは
   アプリケーション全体の`spring.servlet.multipart.max-file-size`既定値をそのまま適用し、
   本ユニット固有の追加上限は設けない（管理者専用機能であり、想定されるYAMLファイルサイズは
   小規模なため）
B. Jackson YAML（`jackson-dataformat-yaml`）を導入し、`ObjectMapper`ベースでYAML↔POJO
   （`PrincipalYaml`/`PermissionYaml`等）の変換を行う。REST層で既に使われているJacksonの
   アノテーション（`@JsonProperty`等）と同じ流儀でDTOを定義できる。内部的にはSnakeYAMLを
   パーサとして利用するため実質的に別パーサを導入するわけではなく、Jacksonのバインド層が
   加わるだけ。宣言した型にのみバインドされ、SnakeYAMLのタグベース任意型解決（`Constructor`の
   誤用によるデシリアライズリスク、CVE-2022-1471相当のクラス）を経由しないため安全側に倒れる。
   バージョンはSpring Boot BOMの`jackson-dataformat-*`管理下にあり明示指定不要（CLAUDE.md
   「Gradleバージョン管理」規約と整合）。アップロードサイズはAと同じく
   `spring.servlet.multipart.max-file-size`既定値をそのまま適用し、本ユニット固有の追加上限は
   設けない（推奨）
C. 本ユニット固有の明示的なファイルサイズ上限を設定キー（例:
   `mm.app.permission.yaml-import.max-file-size`）として追加する
D. その他（具体的な方針を指定）

[Answer]: B（REST層と同じJacksonベースのDTOバインドで実装の一貫性を確保する。アップロード
サイズはA同様、アプリ全体の既定値をそのまま適用し本ユニット固有の追加上限は設けない。）

---

## Question 4: Reliability — 権限データ書き込みの同時実行制御

`importPermissionsFromYaml`の全置換（`business-rules.md` 2.4、削除→再構築を同一トランザクション
内で実行）と、同一接続に対する`setPermission`/`setAuxPermission`が同時に実行された場合の競合
制御方針が未確定。

A. 明示的な排他制御は導入せず、DBトランザクション分離レベル（既定値、例: PostgreSQLの
   READ COMMITTED）に委ねる。管理者操作は少数の管理者による低頻度な手動操作が前提
   （U1/U2/U3で一貫した「小規模内部利用」判断方針）であり、同時実行の競合は稀と判断し、
   本フェーズでは明示的なロック機構を導入しない（推奨）
B. `connectionId`単位の悲観的ロック（`SELECT ... FOR UPDATE`相当）を導入し、全置換処理中は
   同一接続への他の権限書き込みをブロックする
C. `PermissionAssignment`/`AuxPermissionAssignment`に楽観的ロック用のバージョンカラムを追加する
D. その他（具体的な方針を指定）

[Answer]: A

---

## Question 5: Security — YAMLインポート検証失敗時のエラーメッセージ詳細度

`business-rules.md` 2.4で「例外メッセージには最初に検出した違反内容の概要を含める」ことは
決定済みだが、具体的にどこまでの情報（違反したprincipal・schema/table/column名等）を含めるかが
未確定。U3 Question 6（接続テスト/取り込み失敗時のエラー露出方針）と同種の論点。

A. 検証失敗箇所の具体的な内容（該当principal・schema/table/column名、違反した検証項目
   〈`business-rules.md` 2.4の項目1-5のいずれか〉）をそのまま例外メッセージ・
   `ImportResult`に含めて返す。本ユニットの全機能は管理者専用（`hasRole("ADMIN")`）のため、
   一般ユーザ向けの情報漏洩リスクは該当しない（推奨、U3 Question 6と同方針）
B. 汎用的なエラー分類（「必須フィールド欠落」等の項目種別のみ）にとどめ、具体的な
   principal/schema/table/column名はアプリケーションログにのみ出力する
C. その他（具体的な方針を指定）

[Answer]: A

---

## 回答の記入方法

各 `[Answer]:` タグの直後に回答を記入してください。全ての質問に回答後、その旨を伝えてください。