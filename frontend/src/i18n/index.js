import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import en from './locales/en.json'
import tr from './locales/tr.json'

// Supported UI languages. Keep in lockstep with the backend `Language` enum
// (#505) — translations the backend can persist must exist in this list.
export const SUPPORTED_LANGUAGES = ['en', 'tr']

i18n
  // Browser-language detection covers guests. Once a user logs in, their
  // persisted `preferredLanguage` from /api/users/me wins and we call
  // i18n.changeLanguage explicitly — see useSetLanguage and AuthContext.
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      tr: { translation: tr },
    },
    fallbackLng: 'en',
    supportedLngs: SUPPORTED_LANGUAGES,
    interpolation: { escapeValue: false },
    // Don't pollute the console in dev with missing-key warnings; we'll add
    // keys incrementally as components are converted.
    saveMissing: false,
  })

export default i18n
