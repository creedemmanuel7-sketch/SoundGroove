import { resolveSafeApkUrl } from "@/lib/security/validate";

const APK_FALLBACK = "/downloads/soundgroove.apk";

function getApkUrl(): string {
  return resolveSafeApkUrl(process.env.NEXT_PUBLIC_APK_URL, APK_FALLBACK);
}

export function DownloadButton() {
  const apkUrl = getApkUrl();
  const isExternal = apkUrl.startsWith("https://");

  return (
    <div className="cta-wrap">
      <a
        href={apkUrl}
        className="cta"
        download={isExternal ? undefined : "soundgroove.apk"}
        {...(isExternal ? { target: "_blank", rel: "noopener noreferrer" } : {})}
      >
        <svg
          className="cta__icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <polyline points="7 10 12 15 17 10" />
          <line x1="12" y1="15" x2="12" y2="3" />
        </svg>
        Télécharger l&apos;APK
      </a>
      <span className="cta__hint">Android · Gratuit · Sans compte</span>
    </div>
  );
}
