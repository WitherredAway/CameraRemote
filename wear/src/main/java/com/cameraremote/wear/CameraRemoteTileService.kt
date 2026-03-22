package com.cameraremote.wear

import android.content.Context
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
        private const val RESOURCES_VERSION = "1"
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
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(buildTitle())
                    .addContent(buildSpacer(4f))
                    .addContent(buildButtonRow1())
                    .addContent(buildSpacer(4f))
                    .addContent(buildButtonRow2())
                    .build()
            )
            .build()
    }

    private fun buildTitle(): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Text.Builder()
            .setText("Camera")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(14f))
                    .setColor(argb(0xFFFFFFFF.toInt()))
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

    private fun buildButtonRow1(): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Row.Builder()
            .addContent(buildTileButton("OPEN", "open_camera", 0xFFBB86FC.toInt()))
            .addContent(buildHSpacer())
            .addContent(buildTileButton("SNAP", "take_photo", 0xFFD32F2F.toInt()))
            .addContent(buildHSpacer())
            .addContent(buildTileButton("TIMER", "take_photo_timer", 0xFFFF7043.toInt()))
            .build()
    }

    private fun buildButtonRow2(): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Row.Builder()
            .addContent(buildTileButton("FLASH", "toggle_flash", 0xFFFFCA28.toInt()))
            .addContent(buildHSpacer())
            .addContent(buildTileButton("FLIP", "switch_camera", 0xFF42A5F5.toInt()))
            .addContent(buildHSpacer())
            .addContent(buildTileButton("VIDEO", "open_video", 0xFF66BB6A.toInt()))
            .build()
    }

    private fun buildHSpacer(): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Spacer.Builder()
            .setWidth(dp(4f))
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

        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(48f))
            .setHeight(dp(36f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(bgColor))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(12f))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(label)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(10f))
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
