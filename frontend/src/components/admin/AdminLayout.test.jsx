import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import AdminLayout from './AdminLayout.jsx'

vi.mock('../Navbar.jsx', () => ({
  default: () => <header data-testid="navbar-mock">Nav</header>,
}))
vi.mock('./AdminSidebar.jsx', () => ({
  default: () => <aside data-testid="sidebar-mock">Side</aside>,
}))

describe('AdminLayout', () => {
  it('renders navbar, sidebar, and outlet content', () => {
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route element={<AdminLayout />}>
            <Route path="/admin" element={<div>Dashboard body</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getByTestId('navbar-mock')).toBeInTheDocument()
    expect(screen.getByTestId('sidebar-mock')).toBeInTheDocument()
    expect(screen.getByText('Dashboard body')).toBeInTheDocument()
  })
})
