import { apiFetch } from '../../../api/apiClient'
import type { Role } from '../../../store/authStore'
import type { AuthToken } from '../types'

export function login(email: string, password: string): Promise<AuthToken> {
  return apiFetch<AuthToken>('/api/auth/login', { method: 'POST', body: { email, password } })
}

export function refresh(refreshToken: string): Promise<AuthToken> {
  return apiFetch<AuthToken>('/api/auth/refresh', { method: 'POST', body: { refreshToken } })
}

export function logout(refreshToken: string): Promise<void> {
  return apiFetch<void>('/api/auth/logout', { method: 'POST', body: { refreshToken } })
}

export interface DecodedAccessToken {
  userId: number
  role: Role
}

// アクセストークン（JWT）のペイロードをクライアント側でデコードし、ログイン直後の
// CurrentUser（id/role）を構築する。バックエンドのログインAPIはトークンのみを返し
// ユーザ情報を含まないため（AuthController#login）、署名検証はサーバ側の認可処理に
// 委ね、ここでは表示用の役割判定にのみ使用する。
export function decodeAccessToken(accessToken: string): DecodedAccessToken {
  const payload = accessToken.split('.')[1]
  const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as { sub: string; role: Role }
  return { userId: Number(json.sub), role: json.role }
}