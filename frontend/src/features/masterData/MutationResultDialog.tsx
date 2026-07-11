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

import type { MutationResult } from './types'

interface MutationResultDialogProps {
  result: MutationResult | null
  onClose: () => void
}

export function MutationResultDialog({ result, onClose }: MutationResultDialogProps) {
  if (result === null) {
    return null
  }

  return (
    <div className="mutation-result-dialog-overlay" data-testid="mutation-result-dialog">
      <div className="mutation-result-dialog" role="dialog" aria-modal="true">
        {result.success ? (
          <>
            <h2>反映が完了しました</h2>
            <p>
              作成: {result.createdCount}件 / 更新: {result.updatedCount}件 / 削除: {result.deletedCount}件
            </p>
          </>
        ) : (
          <>
            <h2>反映に失敗しました</h2>
            <p>{result.errorMessage}</p>
          </>
        )}
        <button type="button" data-testid="mutation-result-dialog-close" onClick={onClose}>
          閉じる
        </button>
      </div>
    </div>
  )
}