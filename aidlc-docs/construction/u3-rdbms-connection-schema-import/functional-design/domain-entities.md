# domain-entities.md — U3: RDBMS Connection & Schema Import

`u3-rdbms-connection-schema-import-functional-design-plan.md`（回答Q1〜Q8）に基づく
ドメインモデル。内部DB（JPA）で永続化するエンティティのみを扱う。

---

## rdbmsconnection ドメイン

### RdbmsConnection（対象RDBMS接続情報）

管理者が登録する対象RDBMS（MySQL/MariaDB/PostgreSQL/H2）への接続情報（MVP-7, ADM-3）。

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | 主キー |
| `name` | String（not null） | 管理用表示名（`ConnectionListPage`一覧表示に使用、Question 2 = A） |
| `rdbmsType` | `RdbmsType`（enum, not null） | U1で確立済みの`common.dialect`パッケージのenumを再利用（`MYSQL`/`MARIADB`/`POSTGRESQL`/`H2`） |
| `host` | String（not null） | 接続先ホスト名 |
| `port` | Integer（not null） | 接続先ポート番号 |
| `databaseName` | String（not null） | 接続先DB名（`SchemaResolutionMode`の解釈は`DialectStrategy`に委譲、Q5参照） |
| `username` | String（not null） | 対象RDBMS認証ユーザ名（平文保存） |
| `password` | String（not null） | 対象RDBMS認証パスワード。`EncryptedStringConverter`（下記）で暗号化して保存（Question 1 = A） |
| `additionalParams` | String（nullable） | JDBC URLクエリパラメータ形式の生文字列（例: `useSSL=false&serverTimezone=Asia%2FTokyo`）。未指定なら付加しない（Question 2 = A追記事項） |
| `createdAt` | `java.time.Instant` | 作成時刻 |
| `updatedAt` | `java.time.Instant` | 最終更新時刻 |

JDBC URL全体は保存しない。`rdbmsType`+`host`+`port`+`databaseName`から`DialectStrategy`側の
ロジックでベースURLを構造化して組み立て、`additionalParams`が設定されていれば末尾に
そのまま付加する（`business-rules.md` 1.5）。

### EncryptedStringConverter（`JpaConverter`、非エンティティ）

`jakarta.persistence.AttributeConverter<String, String>`を実装する暗号化コンバータ
（Question 1 = A）。`RdbmsConnection.password`に`@Convert(converter =
EncryptedStringConverter.class)`で適用する。

**実装方針**（Question 1 回答時に確定）:
- `@Component` + `@Converter`を付与しSpring Bean化する。JPA/Hibernateはデフォルトでは
  `AttributeConverter`をリフレクションで直接生成しSpringのDIが効かないが、Spring Bootは
  Hibernateの`BeanContainer`をSpringの`ApplicationContext`に紐づける自動設定を持つため、
  `@Component`登録すればコンストラクタインジェクションが有効になる。
- コンストラクタで`@Value("${mm.app.rdbms-connection.encryption-key}")`を受け取り、鍵の
  生成・検証（不正な長さなら例外）をそこで行いfail-fastする。既存の`JwtTokenProvider`
  （`security/JwtTokenProvider.java`、`@Component`のコンストラクタで
  `@Value("${mm.app.jwt.secret}")`を受け取り`Keys.hmacShaKeyFor(...)`で検証する既存パターン）
  と実装方針を一貫させる。
- 暗号方式はAES/GCM（対称鍵暗号）。`convertToDatabaseColumn`で暗号化、
  `convertToEntityAttribute`で復号する。エンティティをリポジトリ経由でロードするたびに
  透過的に復号される（`@Convert`の標準動作であり、手動復号ロジックを別途持つ箇所はない）。

### RdbmsType（enum、U1で確立済み・本ユニットで再利用）
- `MYSQL`
- `MARIADB`
- `POSTGRESQL`
- `H2`

（`common.dialect`パッケージで定義済み。`DialectStrategyFactory.resolve(RdbmsType)`で
対応する`DialectStrategy`実装を取得する。）

---

## schema ドメイン

### SchemaTable（取り込み済みテーブル/ビューのメタデータ）

`importSchema(connectionId)`が対象RDBMSから取り込むテーブル/ビュー単位のメタデータ
（MVP-8）。`RdbmsConnection`に紐づく（Question 5, 6 = A）。

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | 主キー |
| `connectionId` | Long（not null, FK: `RdbmsConnection.id`） | 取り込み元接続 |
| `schemaName` | String（not null） | スキーマ名（`DialectStrategy.getSchemaResolutionMode()`の解釈に従う） |
| `tableName` | String（not null） | 物理テーブル名/ビュー名 |
| `tableType` | `TableType`（enum, not null） | `TABLE`または`VIEW`（Question 5 = A） |
| `comment` | String（nullable） | 対象RDBMS側のテーブルコメント |
| `stale` | boolean（not null, 既定`false`） | 再取り込み時に対象RDBMS側で見つからなくなった場合`true`（Question 6 = A、削除はしない） |
| `importedAt` | `java.time.Instant` | 初回取り込み時刻 |
| `updatedAt` | `java.time.Instant` | 最終更新時刻（再取り込みで属性が変わるたびに更新、staleフラグ変更時も更新） |

一意制約: `(connectionId, schemaName, tableName)`の組み合わせで一意（物理名マッチングの
基準、Question 6 = A）。

### SchemaColumn（取り込み済みカラムのメタデータ）

`SchemaTable`配下のカラム単位メタデータ。

| 属性 | 型 | 説明 |
|---|---|---|
| `id` | Long | 主キー |
| `tableId` | Long（not null, FK: `SchemaTable.id`） | 所属テーブル |
| `columnName` | String（not null） | 物理カラム名 |
| `dataType` | String（not null） | 対象RDBMS側のデータ型名（JDBCメタデータ由来の生表記） |
| `nullable` | boolean（not null） | NULL許容可否 |
| `comment` | String（nullable） | 対象RDBMS側のカラムコメント |
| `ordinalPosition` | Integer（not null） | テーブル内でのカラム定義順 |
| `primaryKeySequence` | Integer（nullable） | 主キー構成順（1始まり）。主キーを構成しないカラムは`null`。ビューのカラムは常に`null`（ビューには主キー制約がないため、Question 5 = A） |
| `stale` | boolean（not null, 既定`false`） | 再取り込み時に対象RDBMS側で見つからなくなった場合`true`（Question 6 = A） |
| `importedAt` | `java.time.Instant` | 初回取り込み時刻 |
| `updatedAt` | `java.time.Instant` | 最終更新時刻 |

一意制約: `(tableId, columnName)`の組み合わせで一意。複合主キーは`primaryKeySequence`が
設定された複数行として表現される（`TableDetail`の主キー一覧構築時は`primaryKeySequence`昇順で
並べる）。

### TableType（enum、Question 5 = A）
- `TABLE`
- `VIEW`

---

## 設計判断（AI提案、Q1〜Q8の対象外事項 — 確認事項）

### 権限参照は物理名ベース（U4との整合性に関する補足）

`component-methods.md`の`PermissionAssignmentService.setPermission`/
`EffectivePermissionResolver`のシグネチャは、`connectionId` + `schema`（String）+
`table`（Optional&lt;String&gt;）+ `column`（Optional&lt;String&gt;）という**物理名ベース**の
引数で権限を参照する（`SchemaTable.id`/`SchemaColumn.id`のような内部ID参照ではない）。
したがって、Q6で確定した「物理名マッチングによるID安定化・staleフラグ保持」は、U4の
権限データが内部IDへの直接FKを持つことを防ぐという意味での必須要件ではないが、
`EffectivePermissionResolver.canCreate`/`canDelete`が主キー構成カラムを判定する際に
（`SchemaColumn.primaryKeySequence`を介して）本ユニットのスキーマメタデータを参照する
ため、再取り込みのたびに主キー情報が失われない・物理名が安定して引き当てられることは
引き続き重要である。Q6の回答方針を変更する必要はない。