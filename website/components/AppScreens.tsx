const SCREENS = [
  {
    id: "library",
    label: "Bibliothèque",
    title: "Vos titres, organisés",
    description:
      "Albums, artistes, playlists et dossiers — tout votre catalogue local, trié et filtrable en un geste.",
  },
  {
    id: "player",
    label: "Lecteur",
    title: "Immersion totale",
    description:
      "Pochette animée, paroles synchronisées, file d'attente et contrôles depuis l'écran de verrouillage.",
  },
  {
    id: "equalizer",
    label: "Égaliseur",
    title: "Son sur mesure",
    description:
      "10 bandes, préréglages Rock, Jazz, Flat — ajustez chaque fréquence pour votre casque ou enceinte.",
  },
] as const;

function LibraryScreen() {
  return (
    <div className="screen-preview screen-preview--library">
      <div className="screen-preview__search" />
      <ul className="screen-preview__list">
        {["Midnight Echoes", "Solar Drift", "Neon Horizon", "Quiet Storm"].map(
          (track, i) => (
            <li key={track} className={i === 0 ? "screen-preview__item screen-preview__item--active" : "screen-preview__item"}>
              <span className="screen-preview__thumb" />
              <span className="screen-preview__meta">
                <span className="screen-preview__track">{track}</span>
                <span className="screen-preview__artist">Nova Collective</span>
              </span>
              <span className="screen-preview__duration">{["4:08", "3:42", "5:11", "2:58"][i]}</span>
            </li>
          ),
        )}
      </ul>
      <div className="screen-preview__mini-player">
        <span className="screen-preview__mini-art" />
        <span className="screen-preview__mini-info">
          <span>Midnight Echoes</span>
          <span>Nova Collective</span>
        </span>
        <span className="screen-preview__mini-play" />
      </div>
    </div>
  );
}

function PlayerScreen() {
  return (
    <div className="screen-preview screen-preview--player">
      <div className="screen-preview__player-art">
        <div className="screen-preview__player-ring" />
      </div>
      <p className="screen-preview__player-title">Midnight Echoes</p>
      <p className="screen-preview__player-artist">Nova Collective</p>
      <div className="screen-preview__player-bar">
        <div className="screen-preview__player-fill" />
      </div>
      <div className="screen-preview__player-controls">
        <span /><span className="screen-preview__player-play" /><span />
      </div>
      <div className="screen-preview__player-lyrics">
        <p className="screen-preview__lyric-active">Under violet skies we drift</p>
        <p>Every beat a pulse of light</p>
      </div>
    </div>
  );
}

function EqualizerScreen() {
  return (
    <div className="screen-preview screen-preview--eq">
      <p className="screen-preview__eq-title">Égaliseur</p>
      <div className="screen-preview__eq-bars">
        {[40, 65, 80, 55, 70, 90, 60, 45, 75, 50].map((h, i) => (
          <div key={i} className="screen-preview__eq-bar">
            <div className="screen-preview__eq-fill" style={{ height: `${h}%` }} />
          </div>
        ))}
      </div>
      <div className="screen-preview__eq-presets">
        {["Flat", "Rock", "Jazz"].map((p, i) => (
          <span key={p} className={i === 1 ? "screen-preview__preset screen-preview__preset--active" : "screen-preview__preset"}>
            {p}
          </span>
        ))}
      </div>
    </div>
  );
}

const SCREEN_COMPONENTS = {
  library: LibraryScreen,
  player: PlayerScreen,
  equalizer: EqualizerScreen,
} as const;

export function AppScreens() {
  return (
    <section className="screens" id="apercu" aria-labelledby="screens-heading">
      <div className="screens__inner">
        <p className="section-label">Aperçu</p>
        <h2 id="screens-heading" className="section-title">
          L&apos;app en action
        </h2>
        <p className="section-lead">
          Interface sombre, accent violet, navigation fluide — conçue pour
          écouter, pas pour distraire.
        </p>

        <div className="screens__grid">
          {SCREENS.map((screen) => {
            const ScreenComponent = SCREEN_COMPONENTS[screen.id];
            return (
              <article key={screen.id} className="screens__card">
                <div className="screens__device">
                  <div className="screens__device-notch" />
                  <ScreenComponent />
                </div>
                <span className="screens__label">{screen.label}</span>
                <h3 className="screens__card-title">{screen.title}</h3>
                <p className="screens__card-desc">{screen.description}</p>
              </article>
            );
          })}
        </div>
      </div>
    </section>
  );
}
