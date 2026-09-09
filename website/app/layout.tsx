import type { Metadata } from "next";
import { Instrument_Sans, Syne } from "next/font/google";
import "./globals.css";

const instrumentSans = Instrument_Sans({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  style: ["normal", "italic"],
  display: "swap",
  variable: "--font-instrument",
});

const syne = Syne({
  subsets: ["latin"],
  weight: ["600", "700", "800"],
  display: "swap",
  variable: "--font-syne",
});

export const metadata: Metadata = {
  metadataBase: new URL(
    process.env.NEXT_PUBLIC_SITE_URL ?? "https://soundgroove.app",
  ),
  title: "SoundGroove — Lecteur musical local",
  description:
    "Lecteur Android pour votre bibliothèque locale : paroles, égaliseur, thèmes personnalisables et respect de la vie privée.",
  icons: {
    icon: [
      { url: "/favicon-32.png", sizes: "32x32", type: "image/png" },
      { url: "/icon.png", sizes: "192x192", type: "image/png" },
    ],
    apple: [{ url: "/apple-touch-icon.png", sizes: "180x180", type: "image/png" }],
  },
  openGraph: {
    title: "SoundGroove",
    description:
      "Votre musique, votre rythme. Lecteur local avec paroles, EQ et thèmes.",
    images: ["/icon.png"],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="fr" className={`${instrumentSans.variable} ${syne.variable}`}>
      <body className={instrumentSans.className}>{children}</body>
    </html>
  );
}
