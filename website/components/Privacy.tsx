const POINTS = [
  {
    title: "100 % local",
    text: "Vos fichiers restent sur l'appareil. Aucun upload, aucun serveur distant pour indexer votre bibliothèque.",
  },
  {
    title: "Zéro compte",
    text: "Pas d'inscription, pas de profil, pas de mot de passe. Installez et écoutez.",
  },
  {
    title: "Sans tracking",
    text: "Pas d'analytics tiers, pas de publicité, pas de revente de données. L'app ne sait pas qui vous êtes.",
  },
  {
    title: "Open source",
    text: "Code auditable sur GitHub. Vous savez exactement ce que l'application fait — et ne fait pas.",
  },
] as const;

export function Privacy() {
  return (
    <section className="privacy" id="vie-privee" aria-labelledby="privacy-heading">
      <div className="privacy__inner">
        <div className="privacy__content">
          <p className="section-label">Vie privée</p>
          <h2 id="privacy-heading" className="section-title">
            Pourquoi local ?
          </h2>
          <p className="section-lead">
            Le streaming collecte vos goûts, vos habitudes, vos écoutes. SoundGroove
            repose sur un principe simple : votre musique vous appartient, pas à
            un algorithme.
          </p>
        </div>

        <ul className="privacy__list">
          {POINTS.map((point) => (
            <li key={point.title} className="privacy__item">
              <h3 className="privacy__item-title">{point.title}</h3>
              <p className="privacy__item-text">{point.text}</p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
