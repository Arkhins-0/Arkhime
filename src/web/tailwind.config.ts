import type { Config } from "tailwindcss";

/**
 * Web app theme: AniArk's structure, styled to match the Arkhime Android app
 * (warm near-black surfaces, Arkhime rose accent, Plus Jakarta Sans, rounded cards).
 */
const config: Config = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        /** Arkhime ramp (keys kept from AniArk): muted neutrals through brand red. */
        pal: {
          crimson: "#5a4a4e",
          raspberry: "#6f5a5f",
          berry: "#8a6f74",
          plum: "#c77dba",
          violet: "#a98bd9",
          indigo: "#8b96a8",
          steel: "#e6a23c",
          ocean: "#f2884b",
          teal: "#ff4f6d",
          aqua: "#ff6a4d",
        },

        /**
         * AniList's dark-theme surface scale, lifted verbatim:
         * background 11,22,34 · foreground 21,31,46 · grey 15,22,31 ·
         * grey-dark 6,12,19 · 300 30,42,56
         */
        bg: {
          DEFAULT: "#130e10",
          fg: "#1d1517",
          grey: "#181112",
          greyDark: "#0d0a0b",
          300: "#33262a",
          400: "#5a4a4e",
          500: "#6e5d61",
        },

        /** AniList's dark-theme text ramp. */
        fg: {
          DEFAULT: "#c9bec0",
          bright: "#f2eaeb",
          light: "#a19598",
          lighter: "#b5a9ac",
          dim: "#8a7d80",
        },

        /** List statuses, spaced along the Deep Ocean ramp. */
        status: {
          current: "#ff6a4d",
          completed: "#e6a23c",
          planning: "#8b96a8",
          repeating: "#a98bd9",
          paused: "#8a6f74",
          dropped: "#5a4a4e",
        },
      },

      fontFamily: {
        // The Android app's typeface, loaded by next/font in the web layout.
        sans: ["var(--font-jakarta)", "-apple-system", "Segoe UI", "Roboto", "sans-serif"],
      },

      borderRadius: {
        // Rounder than AniList, like the Android app's cards.
        DEFAULT: "10px",
        sm: "6px",
        md: "10px",
        lg: "14px",
        xl: "18px",
      },

      maxWidth: {
        site: "1200px",
      },

      keyframes: {
        fadeUp: {
          from: { opacity: "0", transform: "translateY(8px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        popIn: {
          from: { opacity: "0", transform: "scale(0.985)" },
          to: { opacity: "1", transform: "scale(1)" },
        },
        slideUp: {
          from: { transform: "translateY(100%)" },
          to: { transform: "translateY(0)" },
        },
        pulseBar: {
          "0%,100%": { opacity: "1" },
          "50%": { opacity: "0.4" },
        },
      },
      animation: {
        fade: "fadeUp .28s cubic-bezier(.22,1,.36,1) both",
        pop: "popIn .18s cubic-bezier(.22,1,.36,1) both",
        "slide-up": "slideUp .22s cubic-bezier(.22,1,.36,1) both",
        "pulse-bar": "pulseBar 1.1s ease-in-out infinite",
      },
    },
  },
  plugins: [],
};

export default config;
