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
import { GroupDetailPage } from '../features/group/GroupDetailPage'
import { GroupListPage } from '../features/group/GroupListPage'
import { RecordListPage } from '../features/masterData/RecordListPage'
import { SchemaTableListPage } from '../features/masterData/SchemaTableListPage'
import { PermissionAssignmentPage } from '../features/permission/PermissionAssignmentPage'
import { QueryBuilderPage } from '../features/queryBuilder/QueryBuilderPage'
import { QueryExecutionPage } from '../features/queryExecution/QueryExecutionPage'
import { QueryHistoryListPage } from '../features/queryHistory/QueryHistoryListPage'
import { ConnectionFormPage } from '../features/rdbmsConnection/ConnectionFormPage'
import { ConnectionListPage } from '../features/rdbmsConnection/ConnectionListPage'
import { SavedQueryDetailPage } from '../features/savedQuery/SavedQueryDetailPage'
import { SavedQueryListPage } from '../features/savedQuery/SavedQueryListPage'
import { SavedQuerySaveForm } from '../features/savedQuery/SavedQuerySaveForm'
import { SchemaBrowserPage } from '../features/schema/SchemaBrowserPage'
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
        <Route
          path="/admin/rdbms-connections"
          element={
            <ProtectedRoute requiredRole="ADMIN">
              <ConnectionListPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/rdbms-connections/new"
          element={
            <ProtectedRoute requiredRole="ADMIN">
              <ConnectionFormPage mode="create" />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/rdbms-connections/:id"
          element={
            <ProtectedRoute requiredRole="ADMIN">
              <ConnectionFormPage mode="edit" />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/schema/:connectionId"
          element={
            <ProtectedRoute requiredRole="ADMIN">
              <SchemaBrowserPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/groups"
          element={
            <ProtectedRoute requiredRole="ADMIN">
              <GroupListPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/groups/:id"
          element={
            <ProtectedRoute requiredRole="ADMIN">
              <GroupDetailPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/permissions"
          element={
            <ProtectedRoute requiredRole="ADMIN">
              <PermissionAssignmentPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/master-data"
          element={
            <ProtectedRoute>
              <SchemaTableListPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/master-data/:connectionId/:schema/:table"
          element={
            <ProtectedRoute>
              <RecordListPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/query-builder"
          element={
            <ProtectedRoute>
              <QueryBuilderPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/saved-queries"
          element={
            <ProtectedRoute>
              <SavedQueryListPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/saved-queries/new"
          element={
            <ProtectedRoute>
              <SavedQuerySaveForm />
            </ProtectedRoute>
          }
        />
        <Route
          path="/saved-queries/:id"
          element={
            <ProtectedRoute>
              <SavedQueryDetailPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/query-execution"
          element={
            <ProtectedRoute>
              <QueryExecutionPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/query-history"
          element={
            <ProtectedRoute>
              <QueryHistoryListPage />
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