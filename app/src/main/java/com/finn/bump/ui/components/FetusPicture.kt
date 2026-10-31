package com.finn.bump.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.finn.bump.R

/**
 * One illustration per week, 5 to 40, in `res/drawable-nodpi/fetus_wNN.webp`: a typical
 * baby at that gestational age, not a scan and not this baby. Weeks 3 and 4 are a dot too
 * small to draw as a picture, so they fall back to the drawn [FetusRender].
 */
@Composable
fun FetusPicture(week: Int, modifier: Modifier = Modifier) {
    val picture = fetusPicture(week)
    if (picture == null) {
        FetusRender(week, modifier)
        return
    }
    Image(
        painter = painterResource(picture),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = "Illustration of baby at $week weeks, approximate" },
    )
}

fun fetusPicture(week: Int): Int? = when (week) {
    5 -> R.drawable.fetus_w05
    6 -> R.drawable.fetus_w06
    7 -> R.drawable.fetus_w07
    8 -> R.drawable.fetus_w08
    9 -> R.drawable.fetus_w09
    10 -> R.drawable.fetus_w10
    11 -> R.drawable.fetus_w11
    12 -> R.drawable.fetus_w12
    13 -> R.drawable.fetus_w13
    14 -> R.drawable.fetus_w14
    15 -> R.drawable.fetus_w15
    16 -> R.drawable.fetus_w16
    17 -> R.drawable.fetus_w17
    18 -> R.drawable.fetus_w18
    19 -> R.drawable.fetus_w19
    20 -> R.drawable.fetus_w20
    21 -> R.drawable.fetus_w21
    22 -> R.drawable.fetus_w22
    23 -> R.drawable.fetus_w23
    24 -> R.drawable.fetus_w24
    25 -> R.drawable.fetus_w25
    26 -> R.drawable.fetus_w26
    27 -> R.drawable.fetus_w27
    28 -> R.drawable.fetus_w28
    29 -> R.drawable.fetus_w29
    30 -> R.drawable.fetus_w30
    31 -> R.drawable.fetus_w31
    32 -> R.drawable.fetus_w32
    33 -> R.drawable.fetus_w33
    34 -> R.drawable.fetus_w34
    35 -> R.drawable.fetus_w35
    36 -> R.drawable.fetus_w36
    37 -> R.drawable.fetus_w37
    38 -> R.drawable.fetus_w38
    39 -> R.drawable.fetus_w39
    40 -> R.drawable.fetus_w40
    else -> null
}
