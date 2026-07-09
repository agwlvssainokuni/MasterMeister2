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