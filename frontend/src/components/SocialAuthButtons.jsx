function GoogleIcon() {
  return (
    <svg className="w-5 h-5" viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
        fill="#4285F4"
      />
      <path
        d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
        fill="#34A853"
      />
      <path
        d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z"
        fill="#FBBC05"
      />
      <path
        d="M12 5.38c1.62 0 3.06.56 4.21 1.66l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
        fill="#EA4335"
      />
    </svg>
  )
}

function AppleIcon() {
  return (
    <svg className="w-5 h-5 fill-[#2d2f2f]" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M17.05 20.28c-.96 0-2.04-.6-3.23-.6-1.2 0-2.09.59-3.15.59-1.3 0-2.46-.82-3.17-1.98-1.45-2.34-.37-5.83 1.04-7.87.69-.99 1.51-1.87 2.58-1.87.97 0 1.58.62 2.76.62 1.15 0 1.9-.66 2.96-.66 1.04 0 1.95.54 2.53 1.34-2.13 1.25-1.78 3.93.36 4.79-.81 1.22-1.85 2.64-2.68 2.64zm-3.41-15.35c.44-.54.74-1.29.74-2.03 0-.11 0-.21-.02-.31-.69.03-1.52.47-2.01 1.04-.44.5-.83 1.26-.83 1.99 0 .12.02.24.03.31.77-.01 1.63-.49 2.09-1z" />
    </svg>
  )
}

function SocialAuthButtons() {
  return null
  // return (
  //   <div className="grid grid-cols-2 gap-4">
  //     <button
  //       className="flex items-center justify-center gap-3 py-4 bg-[#f0f1f1] rounded-xl hover:bg-[#e7e8e8] transition-colors active:scale-95 duration-200 cursor-pointer"
  //       type="button"
  //     >
  //       <GoogleIcon />
  //       <span className="font-semibold text-[#2d2f2f] text-sm">Google</span>
  //     </button>
  //     <button
  //       className="flex items-center justify-center gap-3 py-4 bg-[#f0f1f1] rounded-xl hover:bg-[#e7e8e8] transition-colors active:scale-95 duration-200 cursor-pointer"
  //       type="button"
  //     >
  //       <AppleIcon />
  //       <span className="font-semibold text-[#2d2f2f] text-sm">Apple</span>
  //     </button>
  //   </div>
  // )
}

export default SocialAuthButtons
