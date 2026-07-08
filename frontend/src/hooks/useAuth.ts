import { useAuthStore } from '../store/authStore'

export function useAuth() {
  const currentUser = useAuthStore((state) => state.currentUser)
  const token = useAuthStore((state) => state.token)
  const login = useAuthStore((state) => state.login)
  const logout = useAuthStore((state) => state.logout)

  return {
    currentUser,
    token,
    isAuthenticated: currentUser !== null && token !== null,
    login,
    logout,
  }
}