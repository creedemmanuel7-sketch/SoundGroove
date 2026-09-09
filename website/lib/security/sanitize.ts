/**
 * Sanitization défensive des chaînes (XSS / caractères de contrôle).
 * À utiliser avant tout rendu HTML non échappé ou stockage.
 */

const CONTROL_CHARS = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g;

/** Échappe les caractères HTML dangereux. */
export function escapeHtml(input: string): string {
  return input
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/**
 * Nettoie une chaîne utilisateur : trim, coupe la longueur,
 * retire caractères de contrôle et balises HTML grossières.
 */
export function sanitizeString(
  input: unknown,
  maxLength = 500,
): string {
  if (typeof input !== "string") return "";
  return input
    .replace(CONTROL_CHARS, "")
    .replace(/<[^>]*>/g, "")
    .trim()
    .slice(0, maxLength);
}

/**
 * Rejette les objets contenant des opérateurs NoSQL ($gt, $ne, etc.)
 * ou des prototypes dangereux — à appeler avant toute requête document.
 */
export function stripNoSqlOperators<T>(value: T): T {
  return stripDeep(value) as T;
}

function stripDeep(value: unknown): unknown {
  if (value === null || typeof value !== "object") return value;

  if (Array.isArray(value)) {
    return value.map(stripDeep);
  }

  const out: Record<string, unknown> = {};
  for (const [key, nested] of Object.entries(value as Record<string, unknown>)) {
    if (key.startsWith("$") || key === "__proto__" || key === "constructor" || key === "prototype") {
      continue;
    }
    out[key] = stripDeep(nested);
  }
  return out;
}
