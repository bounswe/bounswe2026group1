import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import RequireAuth from './RequireAuth.jsx'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))

import { useAuth } from '../context/AuthContext.jsx'

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<div>login page</div>} />
        <Route element={<RequireAuth />}>
          <Route path="/profile" element={<div>profile page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('RequireAuth', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('redirects unauthenticated users to /login', () => {
    useAuth.mockReturnValue({ isAuthenticated: false })
    renderAt('/profile')
    expect(screen.getByText('login page')).toBeInTheDocument()
    expect(screen.queryByText('profile page')).not.toBeInTheDocument()
  })

  it('renders the protected route for authenticated users', () => {
    useAuth.mockReturnValue({ isAuthenticated: true })
    renderAt('/profile')
    expect(screen.getByText('profile page')).toBeInTheDocument()
  })
})
