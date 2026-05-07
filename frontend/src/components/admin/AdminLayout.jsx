import { Outlet } from 'react-router-dom'
import Navbar from '../Navbar.jsx'
import AdminSidebar from './AdminSidebar.jsx'

function AdminLayout() {
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Navbar />
      <div className="flex-1 flex flex-col md:flex-row">
        <AdminSidebar />
        <main className="flex-1 p-4 md:p-8 max-w-full overflow-x-hidden">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export default AdminLayout
