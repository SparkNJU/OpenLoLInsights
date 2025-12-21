/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: '#409eff', // Element Plus primary default
        secondary: '#64748b',
        dark: '#0f172a',
      }
    },
  },
  plugins: [],
}
