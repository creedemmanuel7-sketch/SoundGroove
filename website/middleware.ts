import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

import {
  RATE_LIMITS,
  checkRateLimit,
  classifySensitivePath,
  getClientIp,
  rateLimitResponse,
} from "@/lib/security/rate-limit";

/**
 * Middleware sécurité :
 * - rate-limit strict sur chemins sensibles (auth / upload / search / api / downloads)
 * - en-têtes de sécurité sur toutes les réponses
 *
 * Aucune route auth/upload/search n'existe encore ; le garde-fou s'applique
 * dès qu'elles seront ajoutées sous /api/*.
 */
export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const category = classifySensitivePath(pathname);

  if (category) {
    const ip = getClientIp(request);
    const { limit, windowMs } = RATE_LIMITS[category];
    const result = checkRateLimit(`${ip}:${category}`, limit, windowMs);

    if (!result.allowed) {
      return rateLimitResponse(result.retryAfterSec);
    }
  }

  const response = NextResponse.next();
  applySecurityHeaders(response);
  return response;
}

function applySecurityHeaders(response: NextResponse): void {
  response.headers.set("X-Frame-Options", "DENY");
  response.headers.set("X-Content-Type-Options", "nosniff");
  response.headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
  response.headers.set(
    "Permissions-Policy",
    "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
  );
  response.headers.set(
    "Strict-Transport-Security",
    "max-age=63072000; includeSubDomains; preload",
  );
  response.headers.set(
    "Content-Security-Policy",
    [
      "default-src 'self'",
      "script-src 'self' 'unsafe-inline'",
      "style-src 'self' 'unsafe-inline'",
      "font-src 'self' data:",
      "img-src 'self' data: blob:",
      "connect-src 'self'",
      "frame-ancestors 'none'",
      "base-uri 'self'",
      "form-action 'self'",
      "object-src 'none'",
      "upgrade-insecure-requests",
    ].join("; "),
  );
}

export const config = {
  matcher: [
    "/((?!_next/static|_next/image|favicon.ico|favicon-32.png|apple-touch-icon.png|icon.png|brand/).*)",
  ],
};
