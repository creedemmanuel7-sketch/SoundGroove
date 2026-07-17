import { DownloadButton } from "@/components/DownloadButton";
import { AppMockup } from "@/components/AppMockup";
import { Features } from "@/components/Features";
import { AppScreens } from "@/components/AppScreens";
import { Privacy } from "@/components/Privacy";
import { InstallGuide } from "@/components/InstallGuide";
import { FAQ } from "@/components/FAQ";
import { Footer } from "@/components/Footer";

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
