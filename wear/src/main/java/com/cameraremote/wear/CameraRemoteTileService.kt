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
        private const val RESOURCES_VERSION = "2"

        // Match app theme colors
        private const val COLOR_SURFACE = 0xFF1B1B1B.toInt()
        private const val COLOR_ON_SURFACE = 0xFFE6E1E5.toInt()
        private const val COLOR_PRIMARY = 0xFFD0BCFF.toInt()

        // Pastel button colors (matching circle buttons in app — from colors.xml)
        private const val COLOR_BTN_CAMERA = 0xFF90CAF9.toInt()  // blue
        private const val COLOR_BTN_CAPTURE = 0xFFF5F5F5.toInt() // white/shutter
        private const val COLOR_BTN_TIMER = 0xFFFFCC80.toInt()   // orange
        private const val COLOR_BTN_FLASH = 0xFFFFE082.toInt()   // yellow
        private const val COLOR_BTN_SWITCH = 0xFFB0BEC5.toInt()  // blue-grey
        private const val COLOR_BTN_VIDEO = 0xFFEF9A9A.toInt()   // red
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
                            .setColor(argb(COLOR_SURFACE))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(buildTitle())
                    .addContent(buildSpacer(6f))
                    .addContent(buildTopRow())
                    .addContent(buildSpacer(6f))
                    .addContent(buildBottomRow())
                    .build()
            )
            .build()
    }

    private fun buildTitle(): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Text.Builder()
            .setText("Camera Remote")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(12f))
                    .setColor(argb(COLOR_PRIMARY))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .build()
            )
            .build()
    }

    private fun buildSpacer(height: Float): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Spacer.Builder()
            .setHeight(dp(height))
            .build()
    }

    // Top row: Camera + Video (matching 9 and 3 o'clock)
    private fun buildTopRow(): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Row.Builder()
            .addContent(buildTileButton("Camera", "open_camera", COLOR_BTN_CAMERA))
            .addContent(buildHSpacer())
            .addContent(buildTileButton("Snap", "capture", COLOR_BTN_CAPTURE))
            .addContent(buildHSpacer())
            .addContent(buildTileButton("Video", "open_video", COLOR_BTN_VIDEO))
            .build()
    }

    // Bottom row: Timer + Flip + Flash
    private fun buildBottomRow(): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Row.Builder()
            .addContent(buildTileButton("Timer", "capture_timer", COLOR_BTN_TIMER))
            .addContent(buildHSpacer())
            .addContent(buildTileButton("Flip", "switch_camera", COLOR_BTN_SWITCH))
            .addContent(buildHSpacer())
            .addContent(buildTileButton("Flash", "toggle_flash", COLOR_BTN_FLASH))
            .build()
    }

    private fun buildHSpacer(): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Spacer.Builder()
            .setWidth(dp(6f))
            .build()
    }

    private fun buildTileButton(
        label: String,
        command: String,
        bgColor: Int
    ): LayoutElementBuilders.LayoutElement {
        val launchAction = ActionBuilders.LaunchAction.Builder()
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

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId(command)
            .setOnClick(launchAction)
            .build()

        // Circle button with label below
        return LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(dp(40f))
                    .setHeight(dp(40f))
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setClickable(clickable)
                            .setBackground(
                                ModifiersBuilders.Background.Builder()
                                    .setColor(argb(bgColor))
                                    .setCorner(
                                        ModifiersBuilders.Corner.Builder()
                                            .setRadius(dp(20f))
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
                    .setHeight(dp(2f))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(label)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(9f))
                            .setColor(argb(COLOR_ON_SURFACE))
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
