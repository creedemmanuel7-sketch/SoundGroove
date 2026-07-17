const FEATURES = [
  {
    title: "Musique locale",
    description:
      "Parcourez et écoutez les fichiers stockés sur votre appareil — MP3, FLAC, AAC et plus. Aucun streaming, aucun abonnement.",
  },
  {
    title: "Paroles intégrées",
    description:
      "Affichez les paroles synchronisées pendant la lecture. Importez vos fichiers LRC ou laissez l'app les détecter.",
  },
  {
    title: "Player immersif",
    description:
      "Interface fluide avec pochette, file d'attente, favoris et historique. Contrôles depuis l'écran de verrouillage.",
  },
  {
    title: "Égaliseur 10 bandes",
    description:
      "Ajustez le son à votre goût avec un égaliseur graphique complet et des préréglages prêts à l'emploi.",
  },
  {
    title: "Thèmes & accents",
    description:
      "Mode sombre par défaut, accent violet personnalisable et palettes pour adapter l'app à votre style.",
  },
  {
    title: "Vie privée",
    description:
      "Pas de compte, pas de tracking, pas de publicité. Vos titres restent sur votre téléphone, point final.",
  },
] as const;

export function Features() {
  return (
    <section className="features" id="fonctionnalites" aria-labelledby="features-heading">
      <div className="features__inner">
        <div className="features__header">
          <p className="section-label">Fonctionnalités</p>
          <h2 id="features-heading" className="section-title">
            Tout ce qu&apos;il faut, rien de superflu
          </h2>
          <p className="section-lead">
            Un lecteur complet pensé pour l&apos;écoute locale — chaque
            fonctionnalité sert la musique, pas la monétisation.
          </p>
        </div>
        <ul className="features__grid">
          {FEATURES.map((feature) => (
            <li key={feature.title} className="feature">
              <h3 className="feature__title">{feature.title}</h3>
              <p className="feature__desc">{feature.description}</p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
