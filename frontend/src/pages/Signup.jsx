// Replace HERO_IMAGE_URL with your chosen open-source photo URL
const HERO_IMAGE_URL =
  'https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?w=1400&q=80'

function Signup() {
  return (
    <div className="bg-[#f6f6f6] font-body text-[#2d2f2f] antialiased hide-scrollbar min-h-screen flex flex-col">
      <main className="flex flex-1">
        {/* Left Side: Visual Narrative */}
        <section className="hidden lg:flex lg:w-1/2 relative overflow-hidden">
          <div className="absolute inset-0 z-0">
            <img
              src={HERO_IMAGE_URL}
              alt="Person navigating an accessible urban path"
              className="h-full w-full object-cover"
            />
            <div className="absolute inset-0 bg-gradient-to-tr from-[#176a21]/70 to-transparent" />
          </div>

          <div className="relative z-10 p-16 flex flex-col justify-between w-full">
            {/* Logo */}
            <div className="flex items-center gap-2">
              <span className="text-3xl font-headline font-bold text-white italic tracking-tight">
                Mapcess
              </span>
            </div>

            {/* Hero Card */}
            <div className="max-w-md">
              <div className="bg-white/10 backdrop-blur-xl p-8 rounded-xl border border-white/10">
                <h1 className="font-headline text-5xl font-extrabold text-white leading-tight mb-6">
                  Make your city accessible for everyone.
                </h1>
                <p className="text-white/90 text-lg leading-relaxed">
                  Join a community of contributors mapping accessibility
                  features and barriers — so people with mobility challenges can
                  navigate their neighborhoods with confidence.
                </p>
              </div>
            </div>

            {/* Social Proof */}
            <div className="flex gap-4">
              <div className="bg-white/20 backdrop-blur-md px-4 py-2 rounded-full border border-white/20 flex items-center gap-2">
                <div className="flex -space-x-2">
                  <div className="w-8 h-8 rounded-full border-2 border-[#176a21] bg-[#dbdddd]" />
                  <div className="w-8 h-8 rounded-full border-2 border-[#176a21] bg-[#e7e8e8]" />
                  <div className="w-8 h-8 rounded-full border-2 border-[#176a21] bg-[#dbdddd]" />
                </div>
                <span className="text-white text-sm font-medium">
                  Join 4.2k contributors in your area
                </span>
              </div>
            </div>
          </div>
        </section>

        {/* Right Side: Interaction Canvas */}
        <section className="w-full lg:w-1/2 bg-white flex items-center justify-center p-8 md:p-16">
          <div className="w-full max-w-md space-y-12">
            {/* Mobile Branding */}
            <div className="lg:hidden flex justify-center mb-8">
              <span className="text-2xl font-headline font-bold text-[#176a21] italic tracking-tight">
                Mapcess
              </span>
            </div>

            {/* Heading */}
            <div className="space-y-3">
              <h2 className="text-4xl font-headline font-extrabold text-[#2d2f2f] tracking-tight">
                Join Mapcess
              </h2>
              <p className="text-[#495f69] font-medium">
                Create your profile and start improving your community.
              </p>
            </div>

            {/* Form */}
            <form className="space-y-6">
              <div className="space-y-5">
                <div className="space-y-2">
                  <label
                    className="block text-sm font-label font-semibold text-[#5a5c5c] uppercase tracking-wider"
                    htmlFor="full_name"
                  >
                    Full Name
                  </label>
                  <input
                    className="w-full px-5 py-4 bg-[#f0f1f1] border-none rounded-xl focus:ring-2 focus:ring-[#176a21]/40 text-[#2d2f2f] placeholder:text-[#767777] transition-all outline-none"
                    id="full_name"
                    placeholder="Alex Rivera"
                    type="text"
                    autoComplete="name"
                  />
                </div>

                <div className="space-y-2">
                  <label
                    className="block text-sm font-label font-semibold text-[#5a5c5c] uppercase tracking-wider"
                    htmlFor="email"
                  >
                    Email Address
                  </label>
                  <input
                    className="w-full px-5 py-4 bg-[#f0f1f1] border-none rounded-xl focus:ring-2 focus:ring-[#176a21]/40 text-[#2d2f2f] placeholder:text-[#767777] transition-all outline-none"
                    id="email"
                    placeholder="alex@example.com"
                    type="email"
                    autoComplete="email"
                  />
                </div>

                <div className="space-y-2">
                  <label
                    className="block text-sm font-label font-semibold text-[#5a5c5c] uppercase tracking-wider"
                    htmlFor="password"
                  >
                    Password
                  </label>
                  <input
                    className="w-full px-5 py-4 bg-[#f0f1f1] border-none rounded-xl focus:ring-2 focus:ring-[#176a21]/40 text-[#2d2f2f] placeholder:text-[#767777] transition-all outline-none"
                    id="password"
                    placeholder="••••••••"
                    type="password"
                    autoComplete="new-password"
                  />
                </div>
              </div>

              {/* Terms */}
              <div className="flex items-start gap-3 py-2">
                <div className="flex items-center h-5">
                  <input
                    className="w-5 h-5 rounded-lg border-none bg-[#e1e3e3] text-[#176a21] focus:ring-[#176a21]/40 cursor-pointer"
                    id="terms"
                    type="checkbox"
                  />
                </div>
                <label
                  className="text-sm text-[#5a5c5c] leading-tight cursor-pointer"
                  htmlFor="terms"
                >
                  I agree to the{' '}
                  <a className="text-[#176a21] font-semibold hover:underline" href="#">
                    Terms of Service
                  </a>{' '}
                  and{' '}
                  <a className="text-[#176a21] font-semibold hover:underline" href="#">
                    Privacy Policy
                  </a>
                  .
                </label>
              </div>

              {/* Submit */}
              <button
                className="w-full primary-gradient text-[#d1ffc8] font-headline font-bold py-5 rounded-xl cloud-shadow hover:brightness-110 active:scale-95 transition-all duration-200 uppercase tracking-widest text-sm cursor-pointer"
                type="submit"
              >
                Create Account
              </button>
            </form>

            {/* Social & Alternative Actions */}
            <div className="space-y-8">
              {/* Divider */}
              <div className="relative">
                <div className="absolute inset-0 flex items-center">
                  <div className="w-full border-t border-[#e1e3e3]" />
                </div>
                <div className="relative flex justify-center text-sm uppercase tracking-widest">
                  <span className="bg-white px-4 text-[#767777] font-semibold">
                    Or continue with
                  </span>
                </div>
              </div>

              {/* Social Buttons */}
              <div className="grid grid-cols-2 gap-4">
                <button
                  className="flex items-center justify-center gap-3 py-4 bg-[#f0f1f1] rounded-xl hover:bg-[#e7e8e8] transition-colors active:scale-95 duration-200 cursor-pointer"
                  type="button"
                >
                  <span
                    className="material-symbols-outlined text-[#5a5c5c]"
                    style={{ fontVariationSettings: "'FILL' 0" }}
                  >
                    google
                  </span>
                  <span className="font-semibold text-[#2d2f2f] text-sm">Google</span>
                </button>

                <button
                  className="flex items-center justify-center gap-3 py-4 bg-[#f0f1f1] rounded-xl hover:bg-[#e7e8e8] transition-colors active:scale-95 duration-200 cursor-pointer"
                  type="button"
                >
                  <span
                    className="material-symbols-outlined text-[#5a5c5c]"
                    style={{ fontVariationSettings: "'FILL' 1" }}
                  >
                    ios
                  </span>
                  <span className="font-semibold text-[#2d2f2f] text-sm">Apple</span>
                </button>
              </div>

              <p className="text-center text-[#5a5c5c]">
                Already have an account?{' '}
                <a className="text-[#176a21] font-bold hover:underline ml-1" href="#">
                  Log in
                </a>
              </p>
            </div>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="bg-[#f6f6f6] border-t border-[#2d2f2f]/5">
        <div className="flex flex-col md:flex-row justify-between items-center px-12 py-12 w-full max-w-[1440px] mx-auto gap-8">
          <div className="text-lg font-bold text-[#495f69] uppercase tracking-widest font-headline">
            Mapcess
          </div>
          <div className="flex flex-wrap justify-center gap-8">
            {['Privacy Policy', 'Terms of Service', 'Accessibility', 'Contact Support'].map(
              (link) => (
                <a
                  key={link}
                  className="text-[#495f69] hover:text-[#176a21] transition-all font-label text-sm uppercase tracking-widest"
                  href="#"
                >
                  {link}
                </a>
              ),
            )}
          </div>
          <div className="text-[#495f69] font-label text-sm uppercase tracking-widest text-center md:text-right">
            © 2025 Mapcess. All rights reserved.
          </div>
        </div>
      </footer>
    </div>
  )
}

export default Signup
