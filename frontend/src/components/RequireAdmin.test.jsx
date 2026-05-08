import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import RequireAdmin from './RequireAdmin.jsx'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))

import { useAuth } from '../context/AuthContext.jsx'

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<div>login page</div>} />
        <Route path="/" element={<div>home page</div>} />
        <Route element={<RequireAdmin />}>
          <Route path="/admin" element={<div>admin page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('RequireAdmin', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('redirects unauthenticated users to /login', () => {
    useAuth.mockReturnValue({ isAuthenticated: false, isAdmin: false })
    renderAt('/admin')
    expect(screen.getByText('login page')).toBeInTheDocument()
    expect(screen.queryByText('admin page')).not.toBeInTheDocument()
  })

  it('redirects authenticated non-admin users to /', () => {
    useAuth.mockReturnValue({ isAuthenticated: true, isAdmin: false })
    renderAt('/admin')
    expect(screen.getByText('home page')).toBeInTheDocument()
    expect(screen.queryByText('admin page')).not.toBeInTheDocument()
  })

  it('renders the admin route when the user is an admin', () => {
    useAuth.mockReturnValue({ isAuthenticated: true, isAdmin: true })
    renderAt('/admin')
    expect(screen.getByText('admin page')).toBeInTheDocument()
  })
})
