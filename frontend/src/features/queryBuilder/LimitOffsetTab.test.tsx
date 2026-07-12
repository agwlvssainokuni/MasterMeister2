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
import { LimitOffsetTab } from './LimitOffsetTab'

describe('LimitOffsetTab', () => {
  it('renders the given limit/offset values', () => {
    render(<LimitOffsetTab limit={50} offset={10} onChange={vi.fn()} />)

    expect(screen.getByTestId('limit-offset-tab-limit-input')).toHaveValue(50)
    expect(screen.getByTestId('limit-offset-tab-offset-input')).toHaveValue(10)
  })

  it('calls onChange with the new limit while keeping the current offset', () => {
    const onChange = vi.fn()
    render(<LimitOffsetTab limit={null} offset={10} onChange={onChange} />)

    fireEvent.change(screen.getByTestId('limit-offset-tab-limit-input'), { target: { value: '25' } })

    expect(onChange).toHaveBeenCalledWith(25, 10)
  })

  it('calls onChange with the new offset while keeping the current limit', () => {
    const onChange = vi.fn()
    render(<LimitOffsetTab limit={50} offset={null} onChange={onChange} />)

    fireEvent.change(screen.getByTestId('limit-offset-tab-offset-input'), { target: { value: '5' } })

    expect(onChange).toHaveBeenCalledWith(50, 5)
  })

  it('clears the value to null when the input is emptied', () => {
    const onChange = vi.fn()
    render(<LimitOffsetTab limit={50} offset={10} onChange={onChange} />)

    fireEvent.change(screen.getByTestId('limit-offset-tab-limit-input'), { target: { value: '' } })

    expect(onChange).toHaveBeenCalledWith(null, 10)
  })
})