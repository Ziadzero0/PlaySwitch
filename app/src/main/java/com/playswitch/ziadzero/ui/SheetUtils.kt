package com.playswitch.ziadzero.ui

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * ModalBottomSheet wrapper that eliminates Android stretch-overscroll conflicts and
 * allows smooth drag-down-to-dismiss from anywhere inside the sheet body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmoothModalBottomSheet(
    onDismissRequest: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            if (!scrollable) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    content = content
                )
            } else {
                val scrollState = rememberScrollState()
                val blocker = remember(scrollState) {
                    object : NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset =
                            if (available.y < 0f && scrollState.value > 0) available else Offset.Zero
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .nestedScroll(blocker)
                        .verticalScroll(
                            state = scrollState,
                            enabled = scrollState.maxValue > 0,
                            overscrollEffect = null
                        )
                        .navigationBarsPadding(),
                    content = content
                )
            }
        }
    }
}

@Composable
fun rememberLazyBottomSheetScrollBlocker(listState: LazyListState): NestedScrollConnection =
    remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset =
                if (available.y < 0f && listState.canScrollBackward) available else Offset.Zero
        }
    }
