const LINKS = ['Privacy Policy', 'Terms of Service', 'Accessibility', 'Contact Support']

function AuthFooter() {
  return (
    <footer className="bg-[#f6f6f6] border-t border-[#2d2f2f]/10">
      <div className="flex flex-col md:flex-row justify-between items-center px-12 py-12 w-full max-w-[1440px] mx-auto gap-8">
        <div className="text-lg font-bold text-[#495f69] uppercase tracking-widest font-headline">
          Mapcess
        </div>
        <div className="flex flex-wrap justify-center gap-8">
          {LINKS.map((link) => (
            <a
              key={link}
              className="text-[#495f69] hover:text-[#176a21] transition-all font-label text-sm uppercase tracking-widest"
              href="#"
            >
              {link}
            </a>
          ))}
        </div>
        <div className="text-[#495f69] font-label text-sm uppercase tracking-widest text-center md:text-right">
          © 2025 Mapcess. All rights reserved.
        </div>
      </div>
    </footer>
  )
}

export default AuthFooter
