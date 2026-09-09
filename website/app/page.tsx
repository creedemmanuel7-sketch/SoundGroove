import dynamic from "next/dynamic";
import { DownloadButton } from "@/components/DownloadButton";
import { AppMockup } from "@/components/AppMockup";

/** Below-the-fold : hors du bundle critique pour FCP / TBT. */
const Features = dynamic(
  () => import("@/components/Features").then((m) => m.Features),
);
const AppScreens = dynamic(
  () => import("@/components/AppScreens").then((m) => m.AppScreens),
);
const Privacy = dynamic(
  () => import("@/components/Privacy").then((m) => m.Privacy),
);
const InstallGuide = dynamic(
  () => import("@/components/InstallGuide").then((m) => m.InstallGuide),
);
const FAQ = dynamic(() => import("@/components/FAQ").then((m) => m.FAQ));
const Footer = dynamic(
  () => import("@/components/Footer").then((m) => m.Footer),
);

export default function HomePage() {
  return (
    <>
      <header className="hero">
        <div className="hero__glow" aria-hidden="true" />

        <div className="hero__layout">
          <div className="hero__content">
            <div className="hero__brand-row">
              <img
                className="hero__logo"
                src="/brand/logo-mark.svg"
                alt=""
                width={48}
                height={48}
                decoding="async"
              />
              <span className="hero__brand">SoundGroove</span>
            </div>
            <h1 className="hero__headline">Votre musique, votre rythme.</h1>
            <p className="hero__tagline">
              Lecteur Android pour votre bibliothèque locale — paroles
              synchronisées, égaliseur 10 bandes, thèmes personnalisables.
              Sans compte, sans tracking.
            </p>
            <DownloadButton />
          </div>

          <div className="hero__visual">
            <AppMockup />
          </div>
        </div>
      </header>

      <Features />
      <AppScreens />
      <Privacy />
      <InstallGuide />
      <FAQ />
      <Footer />
    </>
  );
}
