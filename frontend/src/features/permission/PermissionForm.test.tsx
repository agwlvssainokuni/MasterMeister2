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

import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setAuxPermission, setPermission } from './api'
import { PermissionForm } from './PermissionForm'
import type { PrincipalRef, SchemaTreeNode } from './types'

vi.mock('./api', () => ({
  setPermission: vi.fn(),
  setAuxPermission: vi.fn(),
}))

const setPermissionMock = vi.mocked(setPermission)
const setAuxPermissionMock = vi.mocked(setAuxPermission)

const principal: PrincipalRef = { principalType: 'USER', principalId: 1 }
const tableNode: SchemaTreeNode = { level: 'table', schema: 'public', table: 'employees' }
const columnNode: SchemaTreeNode = { level: 'column', schema: 'public', table: 'employees', column: 'id' }

describe('PermissionForm', () => {
  beforeEach(() => {
    setPermissionMock.mockReset()
    setAuxPermissionMock.mockReset()
  })

  it('initializes with NONE and unchecked aux permissions when no current permission is given', () => {
    render(
      <PermissionForm
        principal={principal}
        connectionId={1}
        node={tableNode}
        currentPermission={null}
        currentAuxPermissions={null}
      />,
    )

    expect(screen.getByTestId('permission-form-permission-select')).toHaveValue('NONE')
    expect(screen.getByTestId('permission-form-aux-create-checkbox')).not.toBeChecked()
    expect(screen.getByTestId('permission-form-aux-delete-checkbox')).not.toBeChecked()
  })

  it('shows aux permission checkboxes for a table-level node but hides them for a column-level node', () => {
    const { rerender } = render(
      <PermissionForm
        principal={principal}
        connectionId={1}
        node={tableNode}
        currentPermission={null}
        currentAuxPermissions={null}
      />,
    )
    expect(screen.getByTestId('permission-form-aux-create-checkbox')).toBeInTheDocument()

    rerender(
      <PermissionForm
        principal={principal}
        connectionId={1}
        node={columnNode}
        currentPermission={null}
        currentAuxPermissions={null}
      />,
    )
    expect(screen.queryByTestId('permission-form-aux-create-checkbox')).not.toBeInTheDocument()
  })

  it('submits the selected permission and aux permissions for a table-level node', async () => {
    setPermissionMock.mockResolvedValue(undefined)
    setAuxPermissionMock.mockResolvedValue(undefined)
    render(
      <PermissionForm
        principal={principal}
        connectionId={1}
        node={tableNode}
        currentPermission={null}
        currentAuxPermissions={null}
      />,
    )

    fireEvent.change(screen.getByTestId('permission-form-permission-select'), { target: { value: 'UPDATE' } })
    fireEvent.click(screen.getByTestId('permission-form-aux-create-checkbox'))
    fireEvent.click(screen.getByTestId('permission-form-submit-button'))

    expect(setPermissionMock).toHaveBeenCalledWith(principal, 1, 'public', 'employees', null, 'UPDATE')
    await screen.findByTestId('permission-form-submit-button')
    expect(setAuxPermissionMock).toHaveBeenCalledWith(principal, 1, 'public', 'employees', 'CREATE', true)
    expect(setAuxPermissionMock).toHaveBeenCalledWith(principal, 1, 'public', 'employees', 'DELETE', false)
  })

  it('submits only the permission (no aux calls) for a column-level node', async () => {
    setPermissionMock.mockResolvedValue(undefined)
    render(
      <PermissionForm
        principal={principal}
        connectionId={1}
        node={columnNode}
        currentPermission={null}
        currentAuxPermissions={null}
      />,
    )

    fireEvent.click(screen.getByTestId('permission-form-submit-button'))

    expect(setPermissionMock).toHaveBeenCalledWith(principal, 1, 'public', 'employees', 'id', 'NONE')
    expect(setAuxPermissionMock).not.toHaveBeenCalled()
  })

  it('shows an error message when saving fails', async () => {
    setPermissionMock.mockRejectedValue(new Error('保存に失敗しました'))
    render(
      <PermissionForm
        principal={principal}
        connectionId={1}
        node={columnNode}
        currentPermission={null}
        currentAuxPermissions={null}
      />,
    )

    fireEvent.click(screen.getByTestId('permission-form-submit-button'))

    expect(await screen.findByTestId('permission-form-error')).toHaveTextContent('保存に失敗しました')
  })
})