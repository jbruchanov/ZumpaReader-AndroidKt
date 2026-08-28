package com.scurab.android.zumpareader.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * A [TextFieldValue] mirroring a string the ViewModel owns, whose selection survives a recreation
 * and follows text the ViewModel changed underneath it.
 *
 * The `String` overload of the text fields keeps its [TextFieldValue] - and so the caret - out of
 * reach, so after a rotation the text came back from the ViewModel while the caret jumped to
 * whatever the field inferred. The text is still the ViewModel's; this adds only the selection, and
 * only that is saved.
 *
 * **The caret goes to the end whenever the text changes from underneath.** That is where whatever
 * is typed next belongs in every case there is: a finished image upload appending its link, the
 * last sent message being put back, and a reply header being pushed onto the front of a draft -
 * which used to leave the caret at the same offset and therefore inside the header it just gained.
 */
@Composable
fun rememberFieldValue(text: String): MutableState<TextFieldValue> {
    val state = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    LaunchedEffect(text) {
        if (text != state.value.text) {
            state.value = state.value.copy(text = text, selection = TextRange(text.length))
        }
    }
    return state
}
