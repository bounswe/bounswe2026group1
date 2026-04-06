import { createContext, useContext, useEffect, useState } from 'react'

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
  // Lazy initializer: read token from localStorage but discard it if already expired.
  const [token, setToken] = useState(() => {
    const stored = localStorage.getItem('token')
    if (stored && isTokenExpired(stored)) {
      localStorage.removeItem('token')
      return null
    }
    return stored
  })

  const userId = token ? (parseJwt(token).id ?? null) : null

  useEffect(() => {
    function handleExpired() {
      localStorage.removeItem('token')
      setToken(null)
    }
    window.addEventListener('auth:expired', handleExpired)
    return () => window.removeEventListener('auth:expired', handleExpired)
  }, [])

  function login(newToken) {
    localStorage.setItem('token', newToken)
    setToken(newToken)
  }

  function logout() {
    localStorage.removeItem('token')
    setToken(null)
  }

  return (
    <AuthContext.Provider value={{ token, userId, isAuthenticated: !!token, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
