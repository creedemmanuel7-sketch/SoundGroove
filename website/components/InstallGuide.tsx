const STEPS = [
  {
    step: "01",
    title: "Télécharger l'APK",
    text: "Cliquez sur le bouton de téléchargement ci-dessus ou en bas de page. Le fichier soundgroove.apk (~28 Mo) se télécharge directement.",
  },
  {
    step: "02",
    title: "Autoriser les sources inconnues",
    text: "Dans Paramètres > Sécurité (ou Applications), activez l'installation depuis des sources inconnues pour votre navigateur ou gestionnaire de fichiers.",
  },
  {
    step: "03",
    title: "Installer et profiter",
    text: "Ouvrez le fichier APK téléchargé, confirmez l'installation, puis lancez SoundGroove. Accordez l'accès au stockage pour scanner votre bibliothèque musicale.",
  },
] as const;

export function InstallGuide() {
  return (
    <section className="install" id="installation" aria-labelledby="install-heading">
      <div className="install__inner">
        <p className="section-label">Installation</p>
        <h2 id="install-heading" className="section-title">
          Comment installer
        </h2>
        <p className="section-lead">
          SoundGroove n&apos;est pas encore sur le Play Store. L&apos;installation
          par APK (sideload) prend moins d&apos;une minute.
        </p>

        <ol className="install__steps">
          {STEPS.map((item) => (
            <li key={item.step} className="install__step">
              <span className="install__step-num">{item.step}</span>
              <div>
                <h3 className="install__step-title">{item.title}</h3>
                <p className="install__step-text">{item.text}</p>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
