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