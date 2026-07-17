const FAQ_ITEMS = [
  {
    question: "Quels formats audio sont supportés ?",
    answer:
      "MP3, FLAC, AAC, OGG, WAV, M4A et la plupart des formats courants. Si Android peut le lire, SoundGroove aussi.",
  },
  {
    question: "L'app fonctionne-t-elle hors ligne ?",
    answer:
      "Oui, entièrement. Toute votre bibliothèque est locale — pas besoin de connexion Internet pour écouter.",
  },
  {
    question: "Comment importer mes paroles ?",
    answer:
      "Placez un fichier .lrc à côté de votre piste audio (même nom). L'app détecte et synchronise automatiquement les paroles.",
  },
  {
    question: "Est-ce gratuit ?",
    answer:
      "Oui, SoundGroove est gratuit et open source. Pas d'abonnement, pas de fonctionnalités payantes.",
  },
  {
    question: "Pourquoi pas sur le Play Store ?",
    answer:
      "Le déploiement sur le Play Store est prévu. En attendant, l'APK direct permet de tester et d'utiliser l'app dès maintenant.",
  },
] as const;

export function FAQ() {
  return (
    <section className="faq" id="faq" aria-labelledby="faq-heading">
      <div className="faq__inner">
        <p className="section-label">FAQ</p>
        <h2 id="faq-heading" className="section-title">
          Questions fréquentes
        </h2>

        <dl className="faq__list">
          {FAQ_ITEMS.map((item) => (
            <div key={item.question} className="faq__item">
              <dt className="faq__question">{item.question}</dt>
              <dd className="faq__answer">{item.answer}</dd>
            </div>
          ))}
        </dl>
      </div>
    </section>
  );
}
