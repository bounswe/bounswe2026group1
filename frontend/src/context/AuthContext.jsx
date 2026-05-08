import { createContext, useContext, useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'

const AuthContext = createContext(null)

function parseJwt(token) {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(base64))
  } catch {
    return {}
  }
}

function isTokenExpired(token) {
  try {
    const { exp } = parseJwt(token)
    if (!exp) return false
    return Date.now() >= exp * 1000
  } catch {
    return true
  }
}

export function AuthProvider({ children }) {
  const queryClient = useQueryClient()

  // Lazy initializer: read token from localStorage but discard it if already expired.
  const [token, setToken] = useState(() => {
    const stored = localStorage.getItem('token')
    if (stored && isTokenExpired(stored)) {
      localStorage.removeItem('token')
      return null
    }
    return stored
  })

  const claims = token ? parseJwt(token) : {}
  const userId = claims.id ?? null
  const userRole = claims.role ?? null
  const isAdmin = userRole === 'ADMIN'

  useEffect(() => {
    function handleExpired() {
      localStorage.removeItem('token')
      setToken(null)
      queryClient.removeQueries({ queryKey: ['currentUser'] })
    }
    window.addEventListener('auth:expired', handleExpired)
    return () => window.removeEventListener('auth:expired', handleExpired)
  }, [queryClient])

  function login(newToken) {
    localStorage.setItem('token', newToken)
    setToken(newToken)
  }

  function logout() {
    localStorage.removeItem('token')
    setToken(null)
    queryClient.removeQueries({ queryKey: ['currentUser'] })
  }

  return (
    <AuthContext.Provider value={{ token, userId, userRole, isAdmin, isAuthenticated: !!token, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
