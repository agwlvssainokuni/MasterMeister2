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
import { describe, expect, it, vi } from 'vitest'
import { SchemaSelector } from './SchemaSelector'

describe('SchemaSelector', () => {
  it('renders an option for each schema', () => {
    render(<SchemaSelector schemas={['public', 'app']} selected={null} onSelect={vi.fn()} />)

    expect(screen.getByText('public')).toBeInTheDocument()
    expect(screen.getByText('app')).toBeInTheDocument()
  })

  it('calls onSelect with the chosen schema name', () => {
    const onSelect = vi.fn()
    render(<SchemaSelector schemas={['public', 'app']} selected={null} onSelect={onSelect} />)

    fireEvent.change(screen.getByTestId('schema-selector-select'), { target: { value: 'app' } })

    expect(onSelect).toHaveBeenCalledWith('app')
  })

  it('reflects the currently selected schema', () => {
    render(<SchemaSelector schemas={['public', 'app']} selected="app" onSelect={vi.fn()} />)

    expect(screen.getByTestId('schema-selector-select')).toHaveValue('app')
  })
})
