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
import { exportPermissionsAsYaml, importPermissionsFromYaml } from './api'
import { PermissionYamlPanel } from './PermissionYamlPanel'

vi.mock('./api', () => ({
  exportPermissionsAsYaml: vi.fn(),
  importPermissionsFromYaml: vi.fn(),
}))

const exportPermissionsAsYamlMock = vi.mocked(exportPermissionsAsYaml)
const importPermissionsFromYamlMock = vi.mocked(importPermissionsFromYaml)

describe('PermissionYamlPanel', () => {
  beforeEach(() => {
    exportPermissionsAsYamlMock.mockReset()
    importPermissionsFromYamlMock.mockReset()
    vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:mock-url'), revokeObjectURL: vi.fn() })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  it('exports permissions and triggers a download via a temporary anchor', async () => {
    const blob = new Blob(['permissions: []'], { type: 'application/x-yaml' })
    exportPermissionsAsYamlMock.mockResolvedValue(blob)

    render(<PermissionYamlPanel connectionId={1} />)
    fireEvent.click(screen.getByTestId('permission-yaml-panel-export-button'))

    await vi.waitFor(() => expect(exportPermissionsAsYamlMock).toHaveBeenCalledWith(1))
    expect(URL.createObjectURL).toHaveBeenCalledWith(blob)
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')
  })

  it('imports a file and shows a success result message', async () => {
    importPermissionsFromYamlMock.mockResolvedValue({ success: true, message: '' })
    render(<PermissionYamlPanel connectionId={1} />)

    const file = new File(['permissions: []'], 'permissions.yaml', { type: 'application/x-yaml' })
    fireEvent.change(screen.getByTestId('permission-yaml-panel-import-input'), { target: { files: [file] } })

    expect(await screen.findByTestId('permission-yaml-panel-import-result')).toHaveTextContent(
      'インポートに成功しました',
    )
    expect(importPermissionsFromYamlMock).toHaveBeenCalledWith(1, file)
  })

  it('imports a file and shows a failure result message', async () => {
    importPermissionsFromYamlMock.mockResolvedValue({ success: false, message: '不正な形式です' })
    render(<PermissionYamlPanel connectionId={1} />)

    const file = new File(['invalid'], 'permissions.yaml', { type: 'application/x-yaml' })
    fireEvent.change(screen.getByTestId('permission-yaml-panel-import-input'), { target: { files: [file] } })

    expect(await screen.findByTestId('permission-yaml-panel-import-result')).toHaveTextContent(
      'インポートに失敗しました: 不正な形式です',
    )
  })
})