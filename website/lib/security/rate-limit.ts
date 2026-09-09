/**
 * Rate-limit in-memory par IP — compatible middleware Next.js / Netlify.
 * Sur serverless multi-instance, chaque instance a son propre compteur ;
 * pour un plafond global strict, brancher Upstash via RATE_LIMIT_* (voir .env.example).
 */

export type RateLimitResult = {
  allowed: boolean;
  remaining: number;
  retryAfterSec: number;
};

type Bucket = {
  count: number;
  resetAt: number;
};

const buckets = new Map<string, Bucket>();

const MAX_BUCKETS = 10_000;

function pruneIfNeeded(now: number): void {
  if (buckets.size < MAX_BUCKETS) return;
  for (const [key, bucket] of buckets) {
    if (bucket.resetAt <= now) buckets.delete(key);
  }
  if (buckets.size >= MAX_BUCKETS) {
    const oldest = buckets.keys().next().value;
    if (oldest !== undefined) buckets.delete(oldest);
  }
}

/**
 * Limite glissante par fenêtre fixe.
 * @param key identifiant (ex. `ip:pathCategory`)
 * @param limit nombre max de requêtes par fenêtre
 * @param windowMs durée de la fenêtre en ms
 */
export function checkRateLimit(
  key: string,
  limit: number,
  windowMs: number,
): RateLimitResult {
  const now = Date.now();
  pruneIfNeeded(now);

  const existing = buckets.get(key);
  if (!existing || existing.resetAt <= now) {
    buckets.set(key, { count: 1, resetAt: now + windowMs });
    return { allowed: true, remaining: limit - 1, retryAfterSec: 0 };
  }

  if (existing.count >= limit) {
    const retryAfterSec = Math.max(
      1,
      Math.ceil((existing.resetAt - now) / 1000),
    );
    return { allowed: false, remaining: 0, retryAfterSec };
  }

  existing.count += 1;
  return {
    allowed: true,
    remaining: Math.max(0, limit - existing.count),
    retryAfterSec: 0,
  };
}

/** Extrait une IP client fiable derrière Netlify / reverse-proxy. */
export function getClientIp(request: Request): string {
  const forwarded = request.headers.get("x-forwarded-for");
  if (forwarded) {
    const first = forwarded.split(",")[0]?.trim();
    if (first) return first.slice(0, 64);
  }

  const netlifyIp = request.headers.get("x-nf-client-connection-ip");
  if (netlifyIp?.trim()) return netlifyIp.trim().slice(0, 64);

  const realIp = request.headers.get("x-real-ip");
  if (realIp?.trim()) return realIp.trim().slice(0, 64);

  return "unknown";
}

/** Catégories sensibles pour plafonds plus stricts. */
export type SensitiveCategory = "auth" | "upload" | "search" | "api" | "download";

export function classifySensitivePath(pathname: string): SensitiveCategory | null {
  const path = pathname.toLowerCase();

  if (
    path.startsWith("/api/auth") ||
    path.startsWith("/api/login") ||
    path.startsWith("/api/signin") ||
    path.startsWith("/api/session")
  ) {
    return "auth";
  }

  if (path.startsWith("/api/upload") || path.startsWith("/api/files")) {
    return "upload";
  }

  if (path.startsWith("/api/search") || path.startsWith("/api/music")) {
    return "search";
  }

  if (path.startsWith("/downloads/") || path.endsWith(".apk")) {
    return "download";
  }

  if (path.startsWith("/api/")) {
    return "api";
  }

  return null;
}

/** Plafonds par catégorie (requêtes / fenêtre). */
export const RATE_LIMITS: Record<
  SensitiveCategory,
  { limit: number; windowMs: number }
> = {
  auth: { limit: 10, windowMs: 60_000 },
  upload: { limit: 5, windowMs: 60_000 },
  search: { limit: 30, windowMs: 60_000 },
  api: { limit: 60, windowMs: 60_000 },
  download: { limit: 20, windowMs: 60_000 },
};

export function rateLimitResponse(retryAfterSec: number): Response {
  return new Response(
    JSON.stringify({
      error: "Trop de requêtes. Réessayez plus tard.",
    }),
    {
      status: 429,
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "Retry-After": String(retryAfterSec),
        "Cache-Control": "no-store",
      },
    },
  );
}
