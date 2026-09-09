/**
 * Schémas Zod pour toute future route API SoundGroove.
 * Aucune route auth/upload/search n'existe aujourd'hui — ces schémas
 * sont prêts à brancher dès qu'une surface API apparaîtra.
 */

import { z } from "zod";

import { sanitizeString, stripNoSqlOperators } from "./sanitize";

const safeText = (max: number) =>
  z
    .string()
    .max(max)
    .transform((v) => sanitizeString(v, max));

/** Recherche musicale / texte. */
export const searchQuerySchema = z.object({
  q: safeText(200).optional().default(""),
  limit: z.coerce.number().int().min(1).max(50).optional().default(20),
  offset: z.coerce.number().int().min(0).max(10_000).optional().default(0),
});

/** Payload auth générique (si une auth est ajoutée plus tard). */
export const authCredentialsSchema = z.object({
  email: z
    .string()
    .email()
    .max(254)
    .transform((v) => v.trim().toLowerCase()),
  password: z.string().min(8).max(128),
});

/** Métadonnées d'upload (pas de path traversal). */
export const uploadMetaSchema = z.object({
  filename: z
    .string()
    .min(1)
    .max(180)
    .regex(/^[a-zA-Z0-9._ -]+$/, "Nom de fichier invalide")
    .refine((name) => !name.includes(".."), "Nom de fichier invalide"),
  contentType: z
    .string()
    .max(120)
    .regex(/^[a-zA-Z0-9.+/-]+$/, "Content-Type invalide")
    .optional(),
});

/** URL publique APK (https uniquement). */
export const publicHttpsUrlSchema = z
  .string()
  .url()
  .max(2048)
  .refine((url) => {
    try {
      const parsed = new URL(url);
      return parsed.protocol === "https:";
    } catch {
      return false;
    }
  }, "URL https requise");

export type SearchQuery = z.infer<typeof searchQuerySchema>;
export type AuthCredentials = z.infer<typeof authCredentialsSchema>;
export type UploadMeta = z.infer<typeof uploadMetaSchema>;

/**
 * Parse un body JSON avec schéma + strip NoSQL.
 * Retourne des erreurs génériques (pas de fuite de détail interne).
 */
export function parseJsonBody<T>(
  raw: unknown,
  schema: z.ZodType<T>,
): { ok: true; data: T } | { ok: false; error: string } {
  const cleaned = stripNoSqlOperators(raw);
  const result = schema.safeParse(cleaned);
  if (!result.success) {
    return { ok: false, error: "Requête invalide." };
  }
  return { ok: true, data: result.data };
}

/** Valide une URL APK depuis l'env (fallback local si invalide). */
export function resolveSafeApkUrl(
  envUrl: string | undefined,
  fallback = "/downloads/soundgroove.apk",
): string {
  const trimmed = envUrl?.trim();
  if (!trimmed) return fallback;

  if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
    if (trimmed.includes("..")) return fallback;
    return trimmed.slice(0, 512);
  }

  const parsed = publicHttpsUrlSchema.safeParse(trimmed);
  return parsed.success ? parsed.data : fallback;
}
