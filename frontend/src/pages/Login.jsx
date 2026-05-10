import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import AuthLeftPanel from '../components/AuthLeftPanel.jsx'
import AuthFooter from '../components/AuthFooter.jsx'
import SocialAuthButtons from '../components/SocialAuthButtons.jsx'
import { loginUser } from '../services/authService.js'
import { useAuth } from '../context/AuthContext.jsx'

function Login() {
  const { t } = useTranslation()
  const [showPassword, setShowPassword] = useState(false)
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  function validateEmail(value) {
    if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
      setEmailError('Please enter a valid email address.')
    } else {
      setEmailError('')
    }
  }

  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setEmailError('Please enter a valid email address.')
      return
    }
    setIsLoading(true)
    try {
      const { token } = await loginUser({ email, password })
      login(token)
      const from = location.state?.from?.pathname ?? '/'
      navigate(from, { replace: true })
    } catch (err) {
      // Always show a generic message regardless of the actual error to avoid
      // leaking whether the email exists in the system.
      setError(t('auth.login.invalidCredentials'))
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="bg-[#f6f6f6] font-body text-[#2d2f2f] antialiased hide-scrollbar md:h-screen min-h-screen md:overflow-hidden flex flex-col">
      <main className="flex flex-1 md:min-h-0">
        <AuthLeftPanel
          headline={t('auth.login.heroHeadline')}
          description={t('auth.login.heroDescription')}
        />

        {/* Right Side */}
        <section className="w-full md:w-1/2 lg:w-2/5 bg-white px-6 py-12 md:px-16 lg:px-24 md:overflow-y-auto">
          <div className="min-h-full flex items-center justify-center">
            <div className="w-full max-w-[440px] space-y-8">
            {/* Mobile Branding */}
            <div className="md:hidden">
              <span className="text-2xl font-headline font-bold text-[#176a21] italic">
                Mapcess
              </span>
            </div>

            {/* Header */}
            <header className="space-y-3">
              <h1 className="text-4xl font-headline font-extrabold text-[#2d2f2f] tracking-tight">
                {t('auth.login.welcomeBack')}
              </h1>
              <p className="text-[#495f69] text-base">
                {t('auth.login.subtitle')}
              </p>
            </header>

            {/* Form */}
            <form className="space-y-4" onSubmit={handleSubmit}>
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
                  {t('auth.login.emailLabel')}
                </label>
                <input
                  className="w-full px-5 py-4 bg-[#f0f1f1] border-none rounded-xl focus:ring-2 focus:ring-[#176a21]/40 text-[#2d2f2f] placeholder:text-[#767777]/70 transition-all outline-none"
                  id="email"
                  placeholder={t('auth.login.emailPlaceholder')}
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => { setEmail(e.target.value); if (emailError) validateEmail(e.target.value) }}
                  onBlur={(e) => validateEmail(e.target.value)}
                  required
                />
                {emailError && (
                  <p className="text-xs text-red-600 px-1 mt-1">{emailError}</p>
                )}
              </div>

              {/* Password */}
              <div className="space-y-2">
                <div className="flex justify-between items-center px-1">
                  <label className="text-sm font-label font-bold text-[#5a5c5c]" htmlFor="password">
                    {t('auth.login.passwordLabel')}
                  </label>
                  <a
                    className="text-xs font-semibold text-[#176a21] hover:text-[#025d16] transition-colors"
                    href="#"
                  >
                    {t('auth.login.forgotPassword')}
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
                    aria-label={showPassword ? t('auth.login.hidePassword') : t('auth.login.showPassword')}
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
                {isLoading ? t('auth.login.signingIn') : (
                  <>
                    {t('auth.login.signIn')}
                    <span className="material-symbols-outlined text-xl select-none">arrow_forward</span>
                  </>
                )}
              </button>
            </form>

            <SocialAuthButtons />

            <p className="text-center text-[#495f69] text-sm">
              {t('auth.login.noAccount')}{' '}
              <a
                className="text-[#176a21] font-bold hover:underline underline-offset-4 ml-1"
                href="/signup"
              >
                {t('auth.login.createAccount')}
              </a>
            </p>
            </div>
          </div>
        </section>
      </main>

      <AuthFooter />
    </div>
  )
}

export default Login
