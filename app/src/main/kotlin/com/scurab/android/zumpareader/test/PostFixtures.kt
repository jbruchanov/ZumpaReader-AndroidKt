package com.scurab.android.zumpareader.test

import com.scurab.android.zumpareader.ui.post.ImageMetaUiState
import com.scurab.android.zumpareader.ui.post.PostImageUiState
import com.scurab.android.zumpareader.ui.post.PostTabUiState
import com.scurab.android.zumpareader.ui.post.PostUiState

fun Fixtures.Post.newThread() = PostUiState(
    subject = "Kam na dovolenou?",
    message = "Nekdo tip na neco levneho v cervnu?\n<https://zunpa.cz/pic.jpg>",
    isSubjectEditable = true,
)

fun Fixtures.Post.reply() = newThread().copy(
    subject = "Kam na dovolenou?",
    message = "@honza: \nJa bych zkusil Chorvatsko.",
    isSubjectEditable = false,
)

fun Fixtures.Post.sending() = newThread().copy(isSending = true)

fun Fixtures.Post.tabs() = newThread().copy(
    tabs = listOf(
        PostTabUiState.Message,
        PostTabUiState.Image("2", android.net.Uri.EMPTY, com.scurab.android.zumpareader.R.drawable.ic_photo_black),
        PostTabUiState.Image("3", android.net.Uri.EMPTY, com.scurab.android.zumpareader.R.drawable.ic_photo_camera_black),
    ),
)

fun Fixtures.PostImage.fresh() = PostImageUiState(
    thumbnailPath = "/storage/emulated/0/Pictures/a1b2c3_thumbnail",
    original = ImageMetaUiState(width = 4032, height = 3024, bytes = 3_812_004),
)

fun Fixtures.PostImage.resized() = fresh().copy(
    resized = ImageMetaUiState(width = 1008, height = 756, bytes = 241_882),
    rotationDegrees = 90,
    sampleSizeIndex = 2,
)

fun Fixtures.PostImage.uploaded() = resized().copy(
    uploadedLink = "https://zunpa.cz/fotodisk/a1b2c3.jpg",
)

fun Fixtures.PostImage.busy() = fresh().copy(isBusy = true)
