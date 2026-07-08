import { create } from 'zustand'

export type Role = 'ADMIN' | 'USER'

export interface CurrentUser {
  id: number
  email: string
  role: Role
}

interface AuthState {
  currentUser: CurrentUser | null
  token: string | null
  login: (currentUser: CurrentUser, token: string) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  currentUser: null,
  token: null,
  login: (currentUser, token) => set({ currentUser, token }),
  logout: () => set({ currentUser: null, token: null }),
}))