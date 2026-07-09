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

import { useAuthStore } from '../store/authStore'

export function useAuth() {
  const currentUser = useAuthStore((state) => state.currentUser)
  const token = useAuthStore((state) => state.token)
  const setTokens = useAuthStore((state) => state.setTokens)
  const logout = useAuthStore((state) => state.clearTokens)

  return {
    currentUser,
    token,
    isAuthenticated: currentUser !== null && token !== null,
    setTokens,
    logout,
  }
}