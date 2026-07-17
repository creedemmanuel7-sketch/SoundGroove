import type { Metadata } from "next";
import "./globals.css";

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
      { url: "/icon.png", sizes: "512x512", type: "image/png" },
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
    <html lang="fr">
      <body>{children}</body>
    </html>
  );
}
