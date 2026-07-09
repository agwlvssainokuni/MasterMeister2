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

import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AppLayout } from '../components/AppLayout'
import { AuditLogPage } from '../features/auditLog/AuditLogPage'
import { LoginPage } from '../features/auth/LoginPage'
import { PasswordSetupPage } from '../features/userRegistration/PasswordSetupPage'
import { PendingUsersPage } from '../features/userRegistration/PendingUsersPage'
import { RegistrationRequestPage } from '../features/userRegistration/RegistrationRequestPage'
import { ProtectedRoute } from './ProtectedRoute'

function AuthenticatedRoutes() {
  return (
    <AppLayout>
      <Routes>
        <Route
          path="/admin/audit-logs"
          element={
            <ProtectedRoute requiredRole="ADMIN">
              <AuditLogPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/pending-users"
          element={
            <ProtectedRoute requiredRole="ADMIN">
              <PendingUsersPage />
            </ProtectedRoute>
          }
        />
      </Routes>
    </AppLayout>
  )
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegistrationRequestPage />} />
        <Route path="/register/complete" element={<PasswordSetupPage />} />
        <Route path="/*" element={<AuthenticatedRoutes />} />
      </Routes>
    </BrowserRouter>
  )
}