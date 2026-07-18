import { invoke } from "@tauri-apps/api/core";

export type MpvStatus = {
  running: boolean;
  path: string | null;
  paused: boolean;
  positionSecs: number;
  durationSecs: number;
  volume: number;
};

export type ScannedTrack = {
  path: string;
  fileName: string;
  extension: string;
  sizeBytes: number;
};

export const mpvEnsure = () => invoke<void>("mpv_ensure");
export const mpvQuit = () => invoke<void>("mpv_quit");
export const mpvLoad = (path: string) => invoke<void>("mpv_load", { path });
export const mpvPlay = () => invoke<void>("mpv_play");
export const mpvPause = () => invoke<void>("mpv_pause");
export const mpvTogglePause = () => invoke<void>("mpv_toggle_pause");
export const mpvStop = () => invoke<void>("mpv_stop");
export const mpvSeek = (positionSecs: number) =>
  invoke<void>("mpv_seek", { positionSecs });
export const mpvSetVolume = (volume: number) =>
  invoke<void>("mpv_set_volume", { volume });
export const mpvGetStatus = () => invoke<MpvStatus>("mpv_get_status");

export const openAudioPath = (path: string) =>
  invoke<void>("open_audio_path", { path });
export const pickAudioFile = () => invoke<string | null>("pick_audio_file");
export const pickMusicFolder = () => invoke<string | null>("pick_music_folder");
export const scanMusicFolder = (path: string) =>
  invoke<ScannedTrack[]>("scan_music_folder", { path });
