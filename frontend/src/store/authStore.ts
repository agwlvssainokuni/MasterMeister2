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

export type Role = 'ADMIN' | 'USER'

export interface CurrentUser {
  id: number
  email: string
  role: Role
}

interface AuthState {
  currentUser: CurrentUser | null
  token: string | null
  refreshToken: string | null
  setTokens: (currentUser: CurrentUser, accessToken: string, refreshToken: string) => void
  clearTokens: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      currentUser: null,
      token: null,
      refreshToken: null,
      setTokens: (currentUser, accessToken, refreshToken) =>
        set({ currentUser, token: accessToken, refreshToken }),
      clearTokens: () => set({ currentUser: null, token: null, refreshToken: null }),
    }),
    {
      name: 'auth-storage',
      storage: createJSONStorage(() => sessionStorage),
    },
  ),
)