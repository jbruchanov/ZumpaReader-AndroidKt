package com.scurab.android.zumpareader.ui.compose.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

/**
 * Not part of any screen - the sheet that proves [AppTheme] resolves and that the values match
 * `theme_black.xml`. Keep it: it is the cheapest place to see a colour or a text size regress.
 */
@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 620)
@Composable
private fun AppThemePreview() = AppTheme {
    Column(
        Modifier
            .background(AppTheme.colorScheme.primaryBackground)
            .padding(AppTheme.spaces.large)
    ) {
        Text("colorScheme", style = AppTheme.typography.subject)
        Spacer(Modifier.size(AppTheme.spaces.normal))
        Swatch("context", AppTheme.colorScheme.context)
        Swatch("context50p", AppTheme.colorScheme.context50p)
        Swatch("context25p", AppTheme.colorScheme.context25p)
        Swatch("contextText2", AppTheme.colorScheme.contextText2)
        Swatch("primaryText", AppTheme.colorScheme.primaryText)
        Swatch("primaryBackground80p", AppTheme.colorScheme.primaryBackground80p)
        Swatch("secondaryBackground", AppTheme.colorScheme.secondaryBackground)
        Swatch("selectedBackground", AppTheme.colorScheme.selectedBackground)
        Swatch("ratingGood", AppTheme.colorScheme.ratingGood)
        Swatch("ratingBad", AppTheme.colorScheme.ratingBad)
        Swatch("threadStateNew", AppTheme.colorScheme.threadStateNew)
        Swatch("threadStateUpdated", AppTheme.colorScheme.threadStateUpdated)
        Swatch("threadStateOwn", AppTheme.colorScheme.threadStateOwn)
        Swatch("threadStateResponseForYou", AppTheme.colorScheme.threadStateResponseForYou)

        Spacer(Modifier.size(AppTheme.spaces.large))
        Text("typography", style = AppTheme.typography.subject)
        Spacer(Modifier.size(AppTheme.spaces.normal))
        Text("title 20sp", style = AppTheme.typography.title)
        Text("subject 16sp", style = AppTheme.typography.subject)
        Text("body 14sp", style = AppTheme.typography.body)
        Text("message 13sp", style = AppTheme.typography.message)
        Text("date 12sp", style = AppTheme.typography.date, color = AppTheme.colorScheme.date)
        Text("answerCount 12sp bold", style = AppTheme.typography.answerCount)
        Text("author 12sp", style = AppTheme.typography.author, color = AppTheme.colorScheme.author)
        Text("nickName 12sp", style = AppTheme.typography.nickName, color = AppTheme.colorScheme.nickName)
        Text("surveyButton 10.5sp", style = AppTheme.typography.surveyButton)
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = AppTheme.spaces.tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .size(AppTheme.spaces.large)
                .background(color, AppTheme.shapes.editText)
        )
        Spacer(Modifier.width(AppTheme.spaces.normal))
        Text(name, style = AppTheme.typography.author)
    }
}
