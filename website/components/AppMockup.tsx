export function AppMockup() {
  return (
    <div className="mockup" aria-hidden="true">
      <div className="mockup__frame">
        <div className="mockup__screen">
          <div className="mockup__status">
            <span>21:42</span>
            <span className="mockup__status-icons">
              <span className="mockup__signal" />
              <span className="mockup__battery" />
            </span>
          </div>

          <div className="mockup__header">
            <span className="mockup__back" />
            <span className="mockup__header-title">En lecture</span>
            <span className="mockup__menu" />
          </div>

          <div className="mockup__art-wrap">
            <div className="mockup__wave-ring">
              <div className="mockup__wave-ring-inner" />
            </div>
            <div className="mockup__art">
              <div className="mockup__art-gradient" />
            </div>
          </div>

          <div className="mockup__track">
            <p className="mockup__title">Midnight Echoes</p>
            <p className="mockup__artist">Nova Collective</p>
          </div>

          <div className="mockup__progress">
            <div className="mockup__progress-bar">
              <div className="mockup__progress-fill" />
            </div>
            <div className="mockup__progress-times">
              <span>1:24</span>
              <span>4:08</span>
            </div>
          </div>

          <div className="mockup__controls">
            <span className="mockup__ctrl mockup__ctrl--sm" />
            <span className="mockup__ctrl mockup__ctrl--play" />
            <span className="mockup__ctrl mockup__ctrl--sm" />
          </div>

          <div className="mockup__lyrics">
            <p className="mockup__lyrics-line mockup__lyrics-line--active">
              Under violet skies we drift away
            </p>
            <p className="mockup__lyrics-line">Every beat a pulse of light</p>
            <p className="mockup__lyrics-line mockup__lyrics-line--dim">
              Lost inside the rhythm of the night
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
