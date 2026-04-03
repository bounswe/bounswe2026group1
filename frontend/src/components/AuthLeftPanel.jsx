// Replace with your chosen open-source image URL
const HERO_IMAGE_URL =
  'https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?w=1400&q=80'

function AuthLeftPanel({ headline, description }) {
  return (
    <section className="hidden md:flex md:w-1/2 lg:w-3/5 relative overflow-hidden bg-[#025d16]">
      <div className="absolute inset-0 z-10 bg-gradient-to-t from-[#025d16]/60 via-transparent to-transparent" />
      <img
        src={HERO_IMAGE_URL}
        alt="Person navigating an accessible urban path"
        className="absolute inset-0 w-full h-full object-cover"
      />
      <div className="relative z-20 flex flex-col justify-between h-full p-16 text-[#d1ffc8]">
        <span className="text-3xl font-headline font-bold italic tracking-tight">
          Mapcess
        </span>
        <div className="max-w-xl">
          <h2 className="font-headline text-5xl font-extrabold leading-tight mb-6">
            {headline}
          </h2>
          <p className="text-lg opacity-90 leading-relaxed font-light">
            {description}
          </p>
        </div>
      </div>
    </section>
  )
}

export default AuthLeftPanel
