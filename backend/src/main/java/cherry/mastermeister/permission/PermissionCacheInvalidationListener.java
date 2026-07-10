/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cherry.mastermeister.permission;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cherry.mastermeister.group.GroupChangedEvent;
import cherry.mastermeister.schema.SchemaReimportedEvent;

@Component
public class PermissionCacheInvalidationListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @CacheEvict(cacheNames = {
            "effectivePermissions.table", "effectivePermissions.columns",
            "effectivePermissions.canCreate", "effectivePermissions.canDelete",
            "effectivePermissions.schemas", "effectivePermissions.tables"
    }, allEntries = true)
    public void onGroupChanged(GroupChangedEvent event) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @CacheEvict(cacheNames = {
            "effectivePermissions.table", "effectivePermissions.columns",
            "effectivePermissions.canCreate", "effectivePermissions.canDelete",
            "effectivePermissions.schemas", "effectivePermissions.tables"
    }, allEntries = true)
    public void onSchemaReimported(SchemaReimportedEvent event) {
    }

}