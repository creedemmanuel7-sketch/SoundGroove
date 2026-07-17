package com.credo.soundgroove.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.credo.soundgroove.R

private val sgFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val outfitGoogleFont = GoogleFont("Outfit")
private val dmSansGoogleFont = GoogleFont("DM Sans")

/** Display — titres, greetings, hero. */
val SgDisplayFontFamily: FontFamily = FontFamily(
    Font(googleFont = outfitGoogleFont, fontProvider = sgFontProvider, weight = FontWeight.Normal),
    Font(googleFont = outfitGoogleFont, fontProvider = sgFontProvider, weight = FontWeight.Medium),
    Font(googleFont = outfitGoogleFont, fontProvider = sgFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = outfitGoogleFont, fontProvider = sgFontProvider, weight = FontWeight.Bold),
    Font(googleFont = outfitGoogleFont, fontProvider = sgFontProvider, weight = FontWeight.Black),
)

/** Body — texte courant, labels, paragraphes. */
val SgBodyFontFamily: FontFamily = FontFamily(
    Font(googleFont = dmSansGoogleFont, fontProvider = sgFontProvider, weight = FontWeight.Normal),
    Font(googleFont = dmSansGoogleFont, fontProvider = sgFontProvider, weight = FontWeight.Medium),
    Font(googleFont = dmSansGoogleFont, fontProvider = sgFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = dmSansGoogleFont, fontProvider = sgFontProvider, weight = FontWeight.Bold),
)
