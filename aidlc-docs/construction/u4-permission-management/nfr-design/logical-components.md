# logical-components.md — U4: Permission Management

`nfr-design-patterns.md`に基づく論理コンポーネント一覧。

---

## 1. Group（`cherry.mastermeister.group`）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `Group` | JPA Entity | ユーザグループ（`name`〈unique, not null〉, `createdAt`）。`domain-entities.md`参照 |
| `GroupMember` | JPA Entity | `Group`-`User`（U2）の中間テーブル（`groupId`, `userId`, `joinedAt`）。一意制約`(groupId, userId)`に加え、`@Table(indexes = {...})`で`(userId, groupId)`への明示的追加インデックスを付与（`EffectivePermissionResolver`のグループ合成クエリをカバー、`nfr-design-patterns.md` 4.1） |
| `GroupService` | Service | `createGroup`/`addUserToGroup`/`removeUserFromGroup`/`listGroups`/`listGroupMembers`/`renameGroup`/`deleteGroup`（`business-rules.md` 1節）。書き込み系メソッドはメソッド全体に`@Transactional`を付与し、成功時に`GroupChangedEvent`を発行する（`permission`パッケージのキャッシュ無効化リスナーが購読、`nfr-design-patterns.md` 2.2） |

---

## 2. Permission（`cherry.mastermeister.permission`）

| コンポーネント | 種別 | 責務 |
|---|---|---|
| `PermissionAssignment` | JPA Entity | 主権限（`NONE`/`READ`/`UPDATE`）設定。一意制約`(principalType, principalId, connectionId, schemaName, tableName, columnName)`のみでインデックスを賄う（明示的`@Table(indexes)`なし、`nfr-design-patterns.md` 4.1） |
| `AuxPermissionAssignment` | JPA Entity | 補助権限（`CREATE`/`DELETE`）設定。一意制約`(principalType, principalId, connectionId, schemaName, tableName, auxType)`のみでインデックスを賄う |
| `PrincipalType`（enum） | enum | `USER` / `GROUP` |
| `Permission`（enum） | enum | `NONE` / `READ` / `UPDATE`（強さの順序: `NONE < READ < UPDATE`） |
| `AuxPermissionType`（enum） | enum | `CREATE` / `DELETE` |
| `PrincipalRef` | 値オブジェクト | `(PrincipalType, principalId)`のペア。`PermissionAssignmentService`のAPI引数 |
| `PermissionAssignmentService` | Service（書き込み系メソッド全体に`@Transactional`＋自メソッドに`@CacheEvict`） | `setPermission`/`setAuxPermission`（`business-rules.md` 2.1入力検証）/`exportPermissionsAsYaml`（2.3 YAML形式）/`importPermissionsFromYaml`（2.4 検証項目1〜5、`nfr-design-patterns.md` 5.1、全置換方式）。全書き込みメソッドは`AuditLogService.record`を呼び出す |
| `EffectivePermissionResolver` | `@Component`（内部Facade、コントローラなし） | `resolveEffectiveTablePermission`/`resolveEffectiveColumnPermissions`/`canCreate`/`canDelete`/`listAccessibleSchemas`/`listAccessibleTables`の6メソッドそれぞれに専用`@Cacheable(cacheNames = "...")`を付与（`nfr-design-patterns.md` 1.1）。U5/U6/U7からサービス層で直接呼び出される想定 |
| `PermissionCacheInvalidationListener` | `@Component` | `SchemaReimportedEvent`（U3）・`GroupChangedEvent`（`group`パッケージ）を`@TransactionalEventListener(phase = AFTER_COMMIT)`で購読し、`EffectivePermissionResolver`の6キャッシュを`@CacheEvict(cacheNames = {...}, allEntries = true)`で一括削除する（`nfr-design-patterns.md` 2.1） |

**依存方向**: `permission → group`の一方向のみ、`permission → schema`（U3）も同方向。`group`
パッケージ側は`permission`の何も参照せず、循環参照は生じない（`nfr-design-patterns.md` 3.1参照）。

---

## 3. キャッシュ構成（Spring Cache + Caffeine）

| キャッシュ名 | 対応メソッド | 無効化契機 |
|---|---|---|
| `effectivePermissions.table` | `resolveEffectiveTablePermission` | `PermissionAssignmentService`書き込み系、`GroupChangedEvent`、`SchemaReimportedEvent` |
| `effectivePermissions.columns` | `resolveEffectiveColumnPermissions` | 同上 |
| `effectivePermissions.canCreate` | `canCreate` | 同上 |
| `effectivePermissions.canDelete` | `canDelete` | 同上 |
| `effectivePermissions.schemas` | `listAccessibleSchemas` | 同上 |
| `effectivePermissions.tables` | `listAccessibleTables` | 同上 |

6キャッシュとも`@CacheEvict(cacheNames = {6キャッシュ名全て}, allEntries = true)`で常に一括削除
する（`nfr-design-patterns.md` 1.2）。個別キャッシュの`maximumSize`/`expireAfterWrite`は
`application.yml`の`spring.cache.caffeine.spec`で共通値を指定する。

---

## 4. Frontend（`features/group/`, `features/permission/`）

`u4-permission-management/functional-design/frontend-components.md`で確定済みのコンポーネント
構成をそのまま踏襲する（本ステージでの追加変更なし）。

---

## 5. 設定ファイル

| ファイル | 内容 |
|---|---|
| `application.yml` | `spring.cache.type: caffeine`、`spring.cache.caffeine.spec`（6キャッシュ共通の`maximumSize`/`expireAfterWrite`既定値） |
| `build.gradle.kts` | `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine`（`implementation`、Spring Boot BOM管理下）、`jackson-dataformat-yaml`（`implementation`、Spring Boot BOM管理下）を追加（`tech-stack-decisions.md` 依存関係追加） |

---

## 6. U3/U4イベント連携の再確認

- `SchemaReimportedEvent`はU3 `schema`パッケージが所有・発行する（`SchemaImportService.
  importSchema`成功時、`business-rules.md` 2.6）。イベント自体はキャッシュ・権限の概念を
  含まない`schema`パッケージ語彙のみで定義され、U3側は`permission`パッケージの存在を知らない。
- `permission`パッケージ側の`PermissionCacheInvalidationListener`が`SchemaReimportedEvent`を
  importして購読することで、パッケージ依存方向`permission → schema`を維持する
  （`nfr-design-patterns.md` 2.2参照）。