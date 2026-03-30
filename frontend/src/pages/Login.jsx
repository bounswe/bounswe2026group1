import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import AuthLeftPanel from '../components/AuthLeftPanel.jsx'
import AuthFooter from '../components/AuthFooter.jsx'
import SocialAuthButtons from '../components/SocialAuthButtons.jsx'
import { loginUser } from '../services/authService.js'
import { useAuth } from '../context/AuthContext.jsx'

function Login() {
  const [showPassword, setShowPassword] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  const { login } = useAuth()
  const navigate = useNavigate()

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setIsLoading(true)
    try {
      const { token } = await loginUser({ email, password })
      login(token)
      navigate('/')
    } catch (err) {
      // Always show a generic message regardless of the actual error to avoid
      // leaking whether the email exists in the system.
      setError('Invalid email or password.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="bg-[#f6f6f6] font-body text-[#2d2f2f] antialiased hide-scrollbar min-h-screen flex flex-col">
      <main className="flex flex-1">
        <AuthLeftPanel
          headline="Empowering mobility for every neighbour."
          description="Your reports help people with mobility challenges find accessible routes, ramps, and barrier-free paths in their neighbourhood."
        />

        {/* Right Side */}
        <section className="w-full md:w-1/2 lg:w-2/5 bg-white flex items-center justify-center px-6 py-12 md:px-16 lg:px-24">
          <div className="w-full max-w-[440px] space-y-10">
            {/* Mobile Branding */}
            <div className="md:hidden">
              <span className="text-2xl font-headline font-bold text-[#176a21] italic">
                Mapcess
              </span>
            </div>

            {/* Header */}
            <header className="space-y-3">
              <h1 className="text-4xl font-headline font-extrabold text-[#2d2f2f] tracking-tight">
                Welcome back
              </h1>
              <p className="text-[#495f69] text-base">
                Enter your credentials to continue your civic contribution.
              </p>
            </header>

            {/* Form */}
            <form className="space-y-6" onSubmit={handleSubmit}>
              {/* Error message */}
              {error && (
                <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-4 py-3">
                  {error}
                </p>
              )}

              {/* Email */}
              <div className="space-y-2">
                <label
                  className="block text-sm font-label font-bold text-[#5a5c5c] px-1"
                  htmlFor="email"
                >
                  Email Address
                </label>
                <input
                  className="w-full px-5 py-4 bg-[#f0f1f1] border-none rounded-xl focus:ring-2 focus:ring-[#176a21]/40 text-[#2d2f2f] placeholder:text-[#767777]/70 transition-all outline-none"
                  id="email"
                  placeholder="name@example.com"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>

              {/* Password */}
              <div className="space-y-2">
                <div className="flex justify-between items-center px-1">
                  <label className="text-sm font-label font-bold text-[#5a5c5c]" htmlFor="password">
                    Password
                  </label>
                  <a
                    className="text-xs font-semibold text-[#176a21] hover:text-[#025d16] transition-colors"
                    href="#"
                  >
                    Forgot password?
                  </a>
                </div>
                <div className="relative">
                  <input
                    className="w-full px-5 py-4 bg-[#f0f1f1] border-none rounded-xl focus:ring-2 focus:ring-[#176a21]/40 text-[#2d2f2f] placeholder:text-[#767777]/70 transition-all outline-none pr-12"
                    id="password"
                    placeholder="••••••••"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                  />
                  <button
                    className="absolute right-4 top-1/2 -translate-y-1/2 text-[#acadad] hover:text-[#2d2f2f] transition-colors"
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                  >
                    <span className="material-symbols-outlined text-xl select-none">
                      {showPassword ? 'visibility_off' : 'visibility'}
                    </span>
                  </button>
                </div>
              </div>

              {/* Sign In */}
              <button
                className="w-full py-4 bg-gradient-to-b from-[#176a21] to-[#025d16] text-[#d1ffc8] font-headline font-bold text-lg rounded-full shadow-lg hover:brightness-110 hover:scale-[1.02] active:scale-95 transition-all duration-300 flex items-center justify-center gap-2 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed disabled:scale-100"
                type="submit"
                disabled={isLoading}
              >
                {isLoading ? 'Signing in…' : (
                  <>
                    Sign In
                    <span className="material-symbols-outlined text-xl select-none">arrow_forward</span>
                  </>
                )}
              </button>
            </form>

            {/* OR Divider — hidden until social auth is enabled */}
            {/* <div className="relative flex items-center">
              <div className="flex-grow border-t border-[#acadad]/30" />
              <span className="flex-shrink mx-4 text-xs font-bold tracking-widest text-[#767777] uppercase">
                or continue with
              </span>
              <div className="flex-grow border-t border-[#acadad]/30" />
            </div> */}

            <SocialAuthButtons />

            <p className="text-center text-[#495f69] text-sm">
              Don't have an account?{' '}
              <a
                className="text-[#176a21] font-bold hover:underline underline-offset-4 ml-1"
                href="/signup"
              >
                Create an account
              </a>
            </p>
          </div>
        </section>
      </main>

      <AuthFooter />
    </div>
  )
}

export default Login
