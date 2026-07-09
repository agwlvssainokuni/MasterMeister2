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