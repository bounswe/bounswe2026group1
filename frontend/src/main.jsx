import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './context/AuthContext.jsx'
import { SseProvider } from './context/SseContext.jsx'
import './index.css'
import 'leaflet/dist/leaflet.css'
import App from './App.jsx'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 2,
      refetchOnWindowFocus: false,
    },
  },
})

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <SseProvider>
          <AuthProvider>
            <App />
          </AuthProvider>
        </SseProvider>
      </QueryClientProvider>
    </BrowserRouter>
  </StrictMode>,
)
