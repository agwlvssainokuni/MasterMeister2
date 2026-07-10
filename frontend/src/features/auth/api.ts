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

import { apiFetch } from '../../api/apiClient'
import type { Role } from '../../store/authStore'
import type { AuthToken } from './types'

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