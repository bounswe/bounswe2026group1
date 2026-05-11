import Navbar from '../components/Navbar.jsx'

function SettingsPage() {
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Navbar />
      <main className="flex-1 max-w-3xl w-full mx-auto p-4 md:p-8 flex flex-col gap-6">
        <h1 className="text-3xl font-bold font-headline text-on-surface">Settings</h1>
        <p className="text-sm text-on-surface-variant">
          Preferences will appear here.
        </p>
      </main>
    </div>
  )
}

export default SettingsPage
