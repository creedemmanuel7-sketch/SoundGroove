import { DownloadButton } from "@/components/DownloadButton";

export function Footer() {
  return (
    <footer className="footer">
      <div className="footer__inner">
        <div className="footer__brand">
          <div className="footer__brand-row">
            <img
              className="footer__logo"
              src="/brand/logo-mark.svg"
              alt=""
              width={36}
              height={36}
            />
            <span className="footer__name">SoundGroove</span>
          </div>
          <p className="footer__tagline">
            Lecteur musical open source pour Android — local, privé, sans
            compromis.
          </p>
        </div>

        <nav className="footer__nav" aria-label="Navigation pied de page">
          <a href="#fonctionnalites">Fonctionnalités</a>
          <a href="#apercu">Aperçu</a>
          <a href="#vie-privee">Vie privée</a>
          <a href="#installation">Installation</a>
          <a href="#faq">FAQ</a>
        </nav>

        <div className="footer__cta">
          <DownloadButton />
        </div>

        <p className="footer__copy">
          &copy; {new Date().getFullYear()} SoundGroove — Tous droits réservés
        </p>
      </div>
    </footer>
  );
}
