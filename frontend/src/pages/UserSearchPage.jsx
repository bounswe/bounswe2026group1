import Navbar from '../components/Navbar.jsx'
import UserSearchPanel from '../components/UserSearchPanel.jsx'

/**
 * Full-page user search at /search/users. Used as a deep-linkable URL and as
 * the mobile fallback when the navbar popover would be too cramped.
 *
 * All the actual search behaviour lives in UserSearchPanel — this file only
 * provides the page chrome (Navbar + bg-background container).
 */
function UserSearchPage() {
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Navbar />
      <main className="flex-1 max-w-3xl w-full mx-auto p-4 md:p-8 flex flex-col gap-4">
        <UserSearchPanel heading="Find people" />
      </main>
    </div>
  )
}

export default UserSearchPage
