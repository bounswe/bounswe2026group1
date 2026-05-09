import { Routes, Route } from 'react-router-dom'
import Home from './pages/Home.jsx'
import Signup from './pages/Signup.jsx'
import Login from './pages/Login.jsx'
import Feed from './pages/Feed.jsx'
import ProfilePage from './pages/ProfilePage.jsx'
import UserProfilePage from './pages/UserProfilePage.jsx'
import RequireAdmin from './components/RequireAdmin.jsx'
import RequireAuth from './components/RequireAuth.jsx'
import AdminLayout from './components/admin/AdminLayout.jsx'
import AdminDashboardPage from './pages/admin/AdminDashboardPage.jsx'
import AdminUsersPage from './pages/admin/AdminUsersPage.jsx'
import AdminReportsPage from './pages/admin/AdminReportsPage.jsx'
import AdminCommentsPage from './pages/admin/AdminCommentsPage.jsx'
import AdminValidationsPage from './pages/admin/AdminValidationsPage.jsx'
import { useSseSync } from './hooks/useSseSync.js'
import { useNotificationSseSync } from './hooks/useNotificationSseSync.js'

function App() {
  useSseSync()
  useNotificationSseSync()

  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/signup" element={<Signup />} />
      <Route path="/login" element={<Login />} />
      <Route path="/feed" element={<Feed />} />
      <Route element={<RequireAuth />}>
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/profile/:userId" element={<UserProfilePage />} />
      </Route>
      <Route element={<RequireAdmin />}>
        <Route element={<AdminLayout />}>
          <Route path="/admin" element={<AdminDashboardPage />} />
          <Route path="/admin/users" element={<AdminUsersPage />} />
          <Route path="/admin/reports" element={<AdminReportsPage />} />
          <Route path="/admin/comments" element={<AdminCommentsPage />} />
          <Route path="/admin/validations" element={<AdminValidationsPage />} />
        </Route>
      </Route>
    </Routes>
  )
}

export default App
