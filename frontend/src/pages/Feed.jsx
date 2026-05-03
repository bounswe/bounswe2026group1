import Navbar from '../components/Navbar.jsx'

function Feed() {
  return (
    <div className="flex flex-col h-screen overflow-hidden bg-background font-body">
      <Navbar />
      
      <main className="flex-1 overflow-auto relative bg-background flex items-center justify-center p-8">
        {/* Animated background blobs with app theme colors */}
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-primary/10 rounded-full mix-blend-multiply filter blur-[100px] opacity-70 animate-blob"></div>
        <div className="absolute top-1/3 right-1/4 w-96 h-96 bg-tertiary/10 rounded-full mix-blend-multiply filter blur-[100px] opacity-70 animate-blob animation-delay-2000"></div>
        <div className="absolute -bottom-8 left-1/2 w-96 h-96 bg-secondary/10 rounded-full mix-blend-multiply filter blur-[100px] opacity-70 animate-blob animation-delay-4000"></div>

        <div className="relative z-10 flex flex-col items-center max-w-3xl w-full bg-white/60 backdrop-blur-2xl border border-outline-variant/30 rounded-[2rem] p-12 shadow-[0_8px_30px_rgb(0,0,0,0.06)] overflow-hidden transition-transform hover:scale-[1.01] duration-500">
          
          <div className="w-24 h-24 mb-8 bg-gradient-to-tr from-primary to-primary-container rounded-3xl flex items-center justify-center shadow-lg transform rotate-[10deg] hover:rotate-0 transition-transform duration-500 cursor-pointer">
            <span className="material-symbols-outlined text-white" style={{ fontSize: '48px', fontVariationSettings: "'FILL' 1" }}>groups</span>
          </div>
          
          <h1 className="text-5xl md:text-7xl font-extrabold font-headline text-on-surface mb-6 text-center tracking-tight">
            COMING SOON
          </h1>
          
          <p className="text-lg md:text-xl text-on-surface-variant text-center mb-10 max-w-xl leading-relaxed">
            We're building a community feed! Soon you'll be able to explore, upvote, and interact with accessibility reports shared by others in your neighborhood.
          </p>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 w-full mt-6">
            <div className="bg-white border border-outline-variant/20 rounded-2xl p-6 shadow-sm hover:shadow-lg transition-all group cursor-pointer hover:-translate-y-1">
               <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                 <span className="material-symbols-outlined text-primary">forum</span>
               </div>
               <h3 className="font-bold text-on-surface mb-2 font-headline">Community Feed</h3>
               <p className="text-xs text-on-surface-variant leading-relaxed">Discover recently reported accessibility issues directly from other users.</p>
            </div>
            <div className="bg-white border border-outline-variant/20 rounded-2xl p-6 shadow-sm hover:shadow-lg transition-all group cursor-pointer hover:-translate-y-1">
               <div className="w-12 h-12 bg-tertiary/10 rounded-xl flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                 <span className="material-symbols-outlined text-tertiary">thumb_up</span>
               </div>
               <h3 className="font-bold text-on-surface mb-2 font-headline">Upvote & Verify</h3>
               <p className="text-xs text-on-surface-variant leading-relaxed">Support local issues by upvoting and verifying community reports.</p>
            </div>
            <div className="bg-white border border-outline-variant/20 rounded-2xl p-6 shadow-sm hover:shadow-lg transition-all group cursor-pointer hover:-translate-y-1">
               <div className="w-12 h-12 bg-secondary/10 rounded-xl flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                 <span className="material-symbols-outlined text-secondary">verified</span>
               </div>
               <h3 className="font-bold text-on-surface mb-2 font-headline">Track Impact</h3>
               <p className="text-xs text-on-surface-variant leading-relaxed">Follow along as city officials and others help resolve community issues.</p>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}

export default Feed
