import { useEffect, useState, useTransition } from "react";
import {
  mpvGetStatus,
  mpvPause,
  mpvPlay,
  mpvStop,
  openAudioPath,
  pickAudioFile,
  pickMusicFolder,
  scanMusicFolder,
  type MpvStatus,
  type ScannedTrack,
} from "./lib/tauriCommands";

function formatTime(secs: number): string {
  if (!Number.isFinite(secs) || secs < 0) return "0:00";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
}

export default function App() {
  const [status, setStatus] = useState<MpvStatus | null>(null);
  const [tracks, setTracks] = useState<ScannedTrack[]>([]);
  const [folder, setFolder] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      try {
        const next = await mpvGetStatus();
        if (!cancelled) setStatus(next);
      } catch {
        // mpv absent ou pas encore démarré — silencieux en poll
      }
    };
    tick();
    const id = window.setInterval(tick, 1000);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, []);

  const run = (label: string, fn: () => Promise<unknown>) => {
    setError(null);
    startTransition(() => {
      void (async () => {
        try {
          await fn();
        } catch (e) {
          setError(e instanceof Error ? e.message : String(e));
          console.error(label, e);
        }
      })();
    });
  };

  const onPickFile = () =>
    run("pick_audio_file", async () => {
      const path = await pickAudioFile();
      if (path) await openAudioPath(path);
    });

  const onPickFolder = () =>
    run("pick_music_folder", async () => {
      const path = await pickMusicFolder();
      if (!path) return;
      setFolder(path);
      const scanned = await scanMusicFolder(path);
      setTracks(scanned);
    });

  const onPlayTrack = (path: string) =>
    run("open_audio_path", () => openAudioPath(path));

  return (
    <div className="shell">
      <header className="hero">
        <p className="brand">SoundGroove</p>
        <h1>Lecteur desktop</h1>
        <p className="lede">
          Bibliothèque locale via dialogues async et lecture mpv.
        </p>
        <div className="cta">
          <button type="button" onClick={onPickFile} disabled={pending}>
            Ouvrir un fichier
          </button>
          <button type="button" className="ghost" onClick={onPickFolder} disabled={pending}>
            Scanner un dossier
          </button>
        </div>
      </header>

      <section className="player" aria-label="Contrôles lecture">
        <div className="meta">
          <span className="label">En cours</span>
          <strong>{status?.path ?? "Aucun morceau"}</strong>
          <span className="time">
            {formatTime(status?.positionSecs ?? 0)} /{" "}
            {formatTime(status?.durationSecs ?? 0)}
          </span>
        </div>
        <div className="controls">
          <button
            type="button"
            onClick={() => run("mpv_play", () => mpvPlay())}
            disabled={pending}
          >
            Lecture
          </button>
          <button
            type="button"
            className="ghost"
            onClick={() => run("mpv_pause", () => mpvPause())}
            disabled={pending}
          >
            Pause
          </button>
          <button
            type="button"
            className="ghost"
            onClick={() => run("mpv_stop", () => mpvStop())}
            disabled={pending}
          >
            Stop
          </button>
        </div>
      </section>

      {error ? <p className="error">{error}</p> : null}

      <section className="library" aria-label="Bibliothèque">
        <h2>Bibliothèque</h2>
        <p className="lede">
          {folder
            ? `${tracks.length} morceau${tracks.length > 1 ? "x" : ""} — ${folder}`
            : "Choisissez un dossier musique pour scanner les fichiers audio."}
        </p>
        <ul>
          {tracks.map((t) => (
            <li key={t.path}>
              <button type="button" className="track" onClick={() => onPlayTrack(t.path)}>
                <span>{t.fileName}</span>
                <span className="ext">{t.extension}</span>
              </button>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
