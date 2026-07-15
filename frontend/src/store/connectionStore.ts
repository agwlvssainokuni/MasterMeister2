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

import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { ConnectionSummary } from '../features/rdbmsConnection/types'

interface ConnectionState {
  connectionId: number | null
  connections: ConnectionSummary[]
  setConnectionId: (connectionId: number) => void
  setConnections: (connections: ConnectionSummary[]) => void
  clearConnection: () => void
}

export const useConnectionStore = create<ConnectionState>()(
  persist(
    (set) => ({
      connectionId: null,
      connections: [],
      setConnectionId: (connectionId) => set({ connectionId }),
      setConnections: (connections) => set({ connections }),
      clearConnection: () => set({ connectionId: null, connections: [] }),
    }),
    {
      name: 'connection-storage',
      storage: createJSONStorage(() => sessionStorage),
    },
  ),
)
