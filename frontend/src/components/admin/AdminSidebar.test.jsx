import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import AdminSidebar from './AdminSidebar.jsx'

describe('AdminSidebar', () => {
  it('renders navigation links with correct targets', () => {
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/admin/*" element={<AdminSidebar />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getByRole('link', { name: /overview/i })).toHaveAttribute('href', '/admin')
    expect(screen.getByRole('link', { name: /users/i })).toHaveAttribute('href', '/admin/users')
    expect(screen.getByRole('link', { name: /validations/i })).toHaveAttribute('href', '/admin/validations')
  })

  it('sets aria-current on the active section', () => {
    render(
      <MemoryRouter initialEntries={['/admin/comments']}>
        <Routes>
          <Route path="/admin/*" element={<AdminSidebar />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getByRole('link', { name: /comments/i }).getAttribute('aria-current')).toBe('page')
  })
})
