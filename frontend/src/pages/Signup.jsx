import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import AuthLeftPanel from '../components/AuthLeftPanel.jsx'
import AuthFooter from '../components/AuthFooter.jsx'
import SocialAuthButtons from '../components/SocialAuthButtons.jsx'
import { registerUser, loginUser } from '../services/authService.js'
import { useAuth } from '../context/AuthContext.jsx'
import { validatePassword } from '../utils/passwordValidation.js'

function Signup() {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [passwordTouched, setPasswordTouched] = useState(false)
  const [terms, setTerms] = useState(false)
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

  const passwordValidation = validatePassword(password)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setEmailError('Please enter a valid email address.')
      return
    }
    if (!passwordValidation.isValid) {
      setPasswordTouched(true)
      return
    }
    setIsLoading(true)
    try {
      await registerUser({ name, email, password })
      // The register endpoint returns user data, not a token, so a second
      // request is needed to obtain the JWT and log the user in immediately.
      const { token } = await loginUser({ email, password })
      login(token)
      navigate('/')
    } catch (err) {
      // The backend sends a plain-text body for errors, so we inspect the
      // message string to distinguish a 409 duplicate-email from other failures.
      if (err.message.toLowerCase().includes('already')) {
        setError(t('auth.signup.duplicateEmail'))
      } else {
        setError(err.message || t('auth.signup.genericError'))
      }
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="bg-[#f6f6f6] font-body text-[#2d2f2f] antialiased hide-scrollbar md:h-screen min-h-screen md:overflow-hidden flex flex-col">
      <main className="flex flex-1 md:min-h-0">
        <AuthLeftPanel
          headline={t('auth.signup.heroHeadline')}
          description={t('auth.signup.heroDescription')}
        />

        {/* Right Side */}
        <section className="w-full md:w-1/2 lg:w-2/5 bg-white px-6 py-12 md:px-16 lg:px-24 md:overflow-y-auto">
          <div className="min-h-full flex items-center justify-center">
            <div className="w-full max-w-[440px] space-y-8">
            {/* Mobile Branding */}
            <div className="md:hidden flex justify-center">
              <span className="text-2xl font-headline font-bold text-[#176a21] italic tracking-tight">
                Mapcess
              </span>
            </div>

            {/* Heading */}
            <header className="space-y-3">
              <h1 className="text-4xl font-headline font-extrabold text-[#2d2f2f] tracking-tight">
                {t('auth.signup.joinMapcess')}
              </h1>
              <p className="text-[#495f69] font-medium">
                {t('auth.signup.subtitle')}
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

              <div className="space-y-3">
                <div className="space-y-2">
                  <label
                    className="block text-sm font-label font-bold text-[#5a5c5c] px-1"
                    htmlFor="full_name"
                  >
                    {t('auth.signup.fullNameLabel')}
                  </label>
                  <input
                    className="w-full px-5 py-4 bg-[#f0f1f1] border-none rounded-xl focus:ring-2 focus:ring-[#176a21]/40 text-[#2d2f2f] placeholder:text-[#767777] transition-all outline-none"
                    id="full_name"
                    placeholder={t('auth.signup.fullNamePlaceholder')}
                    type="text"
                    autoComplete="name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    required
                  />
                </div>

                <div className="space-y-2">
                  <label
                    className="block text-sm font-label font-bold text-[#5a5c5c] px-1"
                    htmlFor="email"
                  >
                    {t('auth.signup.emailLabel')}
                  </label>
                  <input
                    className="w-full px-5 py-4 bg-[#f0f1f1] border-none rounded-xl focus:ring-2 focus:ring-[#176a21]/40 text-[#2d2f2f] placeholder:text-[#767777] transition-all outline-none"
                    id="email"
                    placeholder={t('auth.signup.emailPlaceholder')}
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

                <div className="space-y-2">
                  <label
                    className="block text-sm font-label font-bold text-[#5a5c5c] px-1"
                    htmlFor="password"
                  >
                    {t('auth.signup.passwordLabel')}
                  </label>
                  <div className="relative">
                    <input
                      className="w-full px-5 py-4 bg-[#f0f1f1] border-none rounded-xl focus:ring-2 focus:ring-[#176a21]/40 text-[#2d2f2f] placeholder:text-[#767777] transition-all outline-none pr-12"
                      id="password"
                      placeholder="••••••••"
                      type={showPassword ? 'text' : 'password'}
                      autoComplete="new-password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      aria-invalid={passwordTouched && !passwordValidation.isValid}
                      aria-describedby="password-requirements"
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
                  <ul
                    id="password-requirements"
                    className="mt-2 space-y-1 text-sm"
                    aria-live="polite"
                  >
                    {passwordValidation.results.map((rule) => {
                      // Show red only after the user has actually tried to
                      // submit (passwordTouched). While they're still working
                      // on the form, unmet rules stay grey — passed rules
                      // turn green in real time.
                      // Note: rule labels/messages remain in English (see
                      // passwordValidation.js) — pulling them through i18n is
                      // a follow-up so the util stays free of React context.
                      const showFailure = passwordTouched && !rule.passed
                      const colorClass = rule.passed
                        ? 'text-[#176a21]'
                        : showFailure
                          ? 'text-red-600'
                          : 'text-[#767777]'
                      return (
                        <li
                          key={rule.id}
                          data-testid={`password-rule-${rule.id}`}
                          data-passed={rule.passed}
                          className={`flex items-center gap-2 ${colorClass}`}
                        >
                          <span aria-hidden="true">{rule.passed ? '✓' : '•'}</span>
                          <span>{showFailure ? rule.message : rule.label}</span>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              </div>

              {/* Terms */}
              <div className="flex items-start gap-3">
                <input
                  className="mt-0.5 w-5 h-5 rounded border-none bg-[#e1e3e3] text-[#176a21] focus:ring-[#176a21]/40 cursor-pointer shrink-0"
                  id="terms"
                  type="checkbox"
                  checked={terms}
                  onChange={(e) => setTerms(e.target.checked)}
                  aria-label={t('auth.signup.termsLabel')}
                />
                <label className="text-sm text-[#5a5c5c] leading-tight cursor-pointer" htmlFor="terms">
                  {t('auth.signup.termsLabel')}
                </label>
              </div>

              {/* Submit */}
              <button
                className="w-full py-4 bg-gradient-to-b from-[#176a21] to-[#025d16] text-[#d1ffc8] font-headline font-bold text-lg rounded-full shadow-lg hover:brightness-110 hover:scale-[1.02] active:scale-95 transition-all duration-300 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed disabled:scale-100"
                type="submit"
                disabled={!terms || isLoading}
              >
                {isLoading ? t('auth.signup.creatingAccount') : t('auth.signup.createAccount')}
              </button>
            </form>

            <SocialAuthButtons />

            <p className="text-center text-[#5a5c5c] text-sm">
              {t('auth.signup.haveAccount')}{' '}
              <a className="text-[#176a21] font-bold hover:underline underline-offset-4 ml-1" href="/login">
                {t('auth.signup.login')}
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

export default Signup
