const { invoke, convertFileSrc } = window.__TAURI__.core;
const { listen } = window.__TAURI__.event;

const MAX_SCAN = 500;
const STORAGE_KEY = "soundgroove.library.v1";

/** @typedef {{ path: string, title: string, artist: string, album: string, durationMs: number, coverPath?: string|null, coverDataUrl?: string|null }} Track */

const state = {
  /** @type {Track[]} */
  tracks: [],
  index: -1,
  playing: false,
  useMpv: false,
  remoteUrl: null,
};

const els = {
  empty: document.getElementById("empty-state"),
  library: document.getElementById("library-view"),
  list: document.getElementById("track-list"),
  meta: document.getElementById("library-meta"),
  hint: document.getElementById("scan-hint"),
  nowPlaying: document.getElementById("now-playing"),
  npCover: document.getElementById("np-cover"),
  npFallback: document.getElementById("np-cover-fallback"),
  npTitle: document.getElementById("np-title"),
  npArtist: document.getElementById("np-artist"),
  npAlbum: document.getElementById("np-album"),
  btnPlay: document.getElementById("btn-play"),
  iconPlay: document.getElementById("icon-play"),
  iconPause: document.getElementById("icon-pause"),
  btnPrev: document.getElementById("btn-prev"),
  btnNext: document.getElementById("btn-next"),
  backend: document.getElementById("player-backend"),
  btnRemote: document.getElementById("btn-remote"),
  toast: document.getElementById("toast"),
  audio: document.getElementById("html-audio"),
};

function showToast(message, ms = 3200) {
  els.toast.hidden = false;
  els.toast.textContent = message;
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => {
    els.toast.hidden = true;
  }, ms);
}

function formatDuration(ms) {
  if (!ms || ms < 1000) return "";
  const total = Math.floor(ms / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

function coverSrc(track) {
  if (track?.coverDataUrl) return track.coverDataUrl;
  if (track?.coverPath) {
    try {
      return convertFileSrc(track.coverPath);
    } catch {
      return null;
    }
  }
  return null;
}

function persistLibrary() {
  try {
    const slim = state.tracks.map((t) => ({
      path: t.path,
      title: t.title,
      artist: t.artist,
      album: t.album,
      durationMs: t.durationMs,
      coverPath: t.coverPath ?? null,
      // data URLs peuvent être lourdes — on garde coverPath en priorité
      coverDataUrl: t.coverDataUrl && t.coverDataUrl.length < 120_000 ? t.coverDataUrl : null,
    }));
    localStorage.setItem(STORAGE_KEY, JSON.stringify(slim));
  } catch (e) {
    console.warn("persist failed", e);
  }
}

function loadPersisted() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed) && parsed.length) {
      state.tracks = parsed;
      renderLibrary();
    }
  } catch (e) {
    console.warn("load persist failed", e);
  }
}

function setLibraryVisible(hasTracks) {
  els.empty.classList.toggle("hidden", hasTracks);
  els.library.classList.toggle("hidden", !hasTracks);
  els.nowPlaying.hidden = !hasTracks;
}

function renderLibrary() {
  const n = state.tracks.length;
  setLibraryVisible(n > 0);
  els.meta.textContent = n === 1 ? "1 titre" : `${n} titres`;
  els.list.replaceChildren();

  const frag = document.createDocumentFragment();
  state.tracks.forEach((track, i) => {
    const row = document.createElement("div");
    row.className = "track-row" + (i === state.index ? " active" : "");
    row.role = "listitem";
    row.tabIndex = 0;

    const src = coverSrc(track);
    let coverEl;
    if (src) {
      coverEl = document.createElement("img");
      coverEl.className = "track-cover";
      coverEl.alt = "";
      coverEl.loading = "lazy";
      coverEl.src = src;
      coverEl.onerror = () => {
        coverEl.replaceWith(fallbackCover(track));
      };
    } else {
      coverEl = fallbackCover(track);
    }

    const text = document.createElement("div");
    text.className = "track-text";
    const title = document.createElement("p");
    title.className = "track-title";
    title.textContent = track.title || "Titre inconnu";
    const artist = document.createElement("p");
    artist.className = "track-artist";
    artist.textContent = track.artist || "Artiste inconnu";
    text.append(title, artist);

    const dur = document.createElement("span");
    dur.className = "track-duration";
    dur.textContent = formatDuration(track.durationMs);

    row.append(coverEl, text, dur);
    row.addEventListener("click", () => playAt(i));
    row.addEventListener("keydown", (ev) => {
      if (ev.key === "Enter" || ev.key === " ") {
        ev.preventDefault();
        playAt(i);
      }
    });
    frag.append(row);
  });
  els.list.append(frag);
}

function fallbackCover(track) {
  const el = document.createElement("div");
  el.className = "track-cover fallback";
  el.textContent = (track.title || "?").trim().charAt(0).toUpperCase() || "?";
  return el;
}

function updateNowPlaying() {
  const track = state.tracks[state.index];
  if (!track) {
    els.npTitle.textContent = "Rien en lecture";
    els.npArtist.textContent = "—";
    els.npAlbum.textContent = "";
    els.npCover.hidden = true;
    els.npFallback.hidden = false;
    return;
  }
  els.npTitle.textContent = track.title;
  els.npArtist.textContent = track.artist;
  els.npAlbum.textContent = track.album || "";
  const src = coverSrc(track);
  if (src) {
    els.npCover.hidden = false;
    els.npFallback.hidden = true;
    els.npCover.src = src;
  } else {
    els.npCover.hidden = true;
    els.npFallback.hidden = false;
  }
  els.iconPlay.hidden = state.playing;
  els.iconPause.hidden = !state.playing;

  document.querySelectorAll(".track-row").forEach((row, i) => {
    row.classList.toggle("active", i === state.index);
  });
}

async function refreshBackendLabel() {
  try {
    const st = await invoke("mpv_status");
    state.useMpv = !!st.available;
    els.backend.textContent = st.available ? "mpv" : "HTML audio";
  } catch {
    state.useMpv = false;
    els.backend.textContent = "HTML audio";
  }
}

async function playAt(index) {
  if (index < 0 || index >= state.tracks.length) return;
  state.index = index;
  const track = state.tracks[index];
  updateNowPlaying();

  try {
    if (state.useMpv) {
      const st = await invoke("mpv_play", { path: track.path });
      if (st.available) {
        state.playing = true;
        els.audio.pause();
        updateNowPlaying();
        return;
      }
    }
    const src = convertFileSrc(track.path);
    els.audio.src = src;
    await els.audio.play();
    state.playing = true;
    updateNowPlaying();
  } catch (e) {
    state.playing = false;
    updateNowPlaying();
    showToast(`Lecture impossible: ${e}`);
  }
}

async function togglePlay() {
  if (state.index < 0 && state.tracks.length) {
    await playAt(0);
    return;
  }
  if (state.index < 0) return;

  try {
    if (state.useMpv) {
      await invoke("mpv_pause");
      state.playing = !state.playing;
      updateNowPlaying();
      return;
    }
    if (els.audio.paused) {
      await els.audio.play();
      state.playing = true;
    } else {
      els.audio.pause();
      state.playing = false;
    }
    updateNowPlaying();
  } catch (e) {
    showToast(`Contrôle lecture: ${e}`);
  }
}

function playNext(delta) {
  if (!state.tracks.length) return;
  const next = (state.index + delta + state.tracks.length) % state.tracks.length;
  playAt(next);
}

async function pickAndScan({ directory }) {
  try {
    const selected = directory
      ? await invoke("pick_music_folder")
      : await invoke("pick_music_files");

    const list = directory
      ? selected
        ? [selected]
        : []
      : Array.isArray(selected)
        ? selected
        : [];

    if (!list.length) return;

    showToast(directory ? "Scan du dossier…" : "Lecture des tags…");
    els.hint.hidden = true;

    const result = await invoke("scan_library", {
      args: { paths: list, maxFiles: MAX_SCAN },
    });

    mergeTracks(result.tracks || []);
    renderLibrary();
    persistLibrary();

    let msg = `${result.tracks?.length ?? 0} titre(s) importé(s)`;
    if (result.truncated) {
      msg += ` (limite ${MAX_SCAN})`;
      els.hint.hidden = false;
      els.hint.textContent = `Scan tronqué à ${MAX_SCAN} fichiers pour rester fluide.`;
    }
    if (result.skippedErrors) {
      msg += ` · ${result.skippedErrors} ignoré(s)`;
    }
    showToast(msg);
  } catch (e) {
    console.error(e);
    showToast(`Import échoué: ${e}`);
  }
}

function mergeTracks(incoming) {
  const byPath = new Map(state.tracks.map((t) => [t.path, t]));
  for (const t of incoming) {
    byPath.set(t.path, t);
  }
  state.tracks = Array.from(byPath.values()).sort((a, b) => {
    const aa = (a.artist || "").toLowerCase();
    const bb = (b.artist || "").toLowerCase();
    if (aa !== bb) return aa < bb ? -1 : 1;
    const ta = (a.title || "").toLowerCase();
    const tb = (b.title || "").toLowerCase();
    return ta < tb ? -1 : ta > tb ? 1 : 0;
  });
}

async function toggleRemote() {
  try {
    const cur = await invoke("remote_status");
    if (cur.running) {
      const st = await invoke("remote_stop");
      state.remoteUrl = null;
      showToast(`Remote arrêtée (${st.url})`);
      return;
    }
    const st = await invoke("remote_start");
    state.remoteUrl = st.url;
    showToast(`Remote: ${st.url} — /play /pause /next /prev`);
  } catch (e) {
    showToast(`Remote: ${e}`);
  }
}

function wireUi() {
  const scan = () => pickAndScan({ directory: true });
  const files = () => pickAndScan({ directory: false });

  document.getElementById("btn-scan-folder").addEventListener("click", scan);
  document.getElementById("btn-empty-scan").addEventListener("click", scan);
  document.getElementById("btn-add-files").addEventListener("click", files);
  document.getElementById("btn-empty-files").addEventListener("click", files);
  els.btnPlay.addEventListener("click", togglePlay);
  els.btnPrev.addEventListener("click", () => playNext(-1));
  els.btnNext.addEventListener("click", () => playNext(1));
  els.btnRemote.addEventListener("click", toggleRemote);

  els.audio.addEventListener("ended", () => playNext(1));
  els.audio.addEventListener("play", () => {
    state.playing = true;
    updateNowPlaying();
  });
  els.audio.addEventListener("pause", () => {
    state.playing = false;
    updateNowPlaying();
  });
}

async function wireRemoteEvents() {
  try {
    await listen("remote-command", (event) => {
      const action = event.payload;
      if (action === "play" || action === "pause") togglePlay();
      else if (action === "next") playNext(1);
      else if (action === "prev") playNext(-1);
    });
  } catch (e) {
    console.warn("remote listen", e);
  }
}

async function boot() {
  wireUi();
  loadPersisted();
  await refreshBackendLabel();
  await wireRemoteEvents();
  updateNowPlaying();
}

boot();
