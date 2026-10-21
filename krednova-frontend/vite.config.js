import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
// Forwards /api calls to Spring Boot on :8080 during development (no CORS setup needed locally).
export default defineConfig({ plugins: [react()], server: { proxy: { '/api': 'http://localhost:8080' } } });
