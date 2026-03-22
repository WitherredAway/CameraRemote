package com.cameraremote.wear

import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.DimensionBuilders.expand
import androidx.wear.tiles.DimensionBuilders.sp
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TimelineBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class CameraRemoteTileService : androidx.wear.tiles.TileService() {

    companion object {
        private const val RESOURCES_VERSION = "3"

        private const val COLOR_BG = 0xFF000000.toInt()
        private const val COLOR_LABEL = 0xFFCAC4D0.toInt()
        private const val COLOR_TITLE = 0xFFD0BCFF.toInt()

        // Button colors (from colors.xml)
        private const val COLOR_SHUTTER = 0xFFF5F5F5.toInt()
        private const val COLOR_CAMERA = 0xFF90CAF9.toInt()
        private const val COLOR_VIDEO = 0xFFEF9A9A.toInt()
        private const val COLOR_FLASH = 0xFFFFE082.toInt()
        private const val COLOR_SWITCH = 0xFFB0BEC5.toInt()
        private const val COLOR_TIMER = 0xFFFFCC80.toInt()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(buildLayout())
                            .build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTimeline(timeline)
                .build()
        )
    }

    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }

    private fun buildLayout(): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(COLOR_BG))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(spacer(10f))
                    .addContent(title())
                    .addContent(spacer(8f))
                    .addContent(row(
                        button("Camera", "open_camera", COLOR_CAMERA),
                        button("Snap", "capture", COLOR_SHUTTER),
                        button("Video", "open_video", COLOR_VIDEO)
                    ))
                    .addContent(spacer(6f))
                    .addContent(row(
                        button("Flash", "toggle_flash", COLOR_FLASH),
                        button("Flip", "switch_camera", COLOR_SWITCH),
                        button("Timer", "capture_timer", COLOR_TIMER)
                    ))
                    .build()
            )
            .build()
    }

    private fun title(): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Text.Builder()
            .setText("Camera Remote")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(13f))
                    .setColor(argb(COLOR_TITLE))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .build()
            )
            .build()
    }

    private fun spacer(h: Float): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()
    }

    private fun row(vararg items: LayoutElementBuilders.LayoutElement): LayoutElementBuilders.LayoutElement {
        val builder = LayoutElementBuilders.Row.Builder()
        items.forEachIndexed { i, item ->
            if (i > 0) builder.addContent(
                LayoutElementBuilders.Spacer.Builder().setWidth(dp(8f)).build()
            )
            builder.addContent(item)
        }
        return builder.build()
    }

    private fun button(
        label: String,
        command: String,
        bgColor: Int
    ): LayoutElementBuilders.LayoutElement {
        val action = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName("$packageName.TileActionActivity")
                    .addKeyToExtraMapping(
                        "command",
                        ActionBuilders.AndroidStringExtra.Builder()
                            .setValue(command)
                            .build()
                    )
                    .build()
            )
            .build()

        return LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(dp(44f))
                    .setHeight(dp(44f))
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setClickable(
                                ModifiersBuilders.Clickable.Builder()
                                    .setId(command)
                                    .setOnClick(action)
                                    .build()
                            )
                            .setBackground(
                                ModifiersBuilders.Background.Builder()
                                    .setColor(argb(bgColor))
                                    .setCorner(
                                        ModifiersBuilders.Corner.Builder()
                                            .setRadius(dp(22f))
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(dp(3f))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(label)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(10f))
                            .setColor(argb(COLOR_LABEL))
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
