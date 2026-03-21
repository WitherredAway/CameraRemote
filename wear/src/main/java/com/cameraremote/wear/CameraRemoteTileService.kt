package com.cameraremote.wear

import android.util.Log
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.DimensionBuilders.expand
import androidx.wear.tiles.DimensionBuilders.sp
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.LayoutElementBuilders.Box
import androidx.wear.tiles.LayoutElementBuilders.Column
import androidx.wear.tiles.LayoutElementBuilders.FontStyles
import androidx.wear.tiles.LayoutElementBuilders.Row
import androidx.wear.tiles.LayoutElementBuilders.Spacer
import androidx.wear.tiles.LayoutElementBuilders.Text
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class CameraRemoteTileService : TileService() {

    companion object {
        private const val TAG = "CameraTile"
        private const val RESOURCES_VERSION = "1"
        private const val PATH_CAMERA_REMOTE = "/camera_remote"

        private const val CMD_TAKE_PHOTO = "take_photo"
        private const val CMD_TOGGLE_FLASH = "toggle_flash"
        private const val CMD_SWITCH_CAMERA = "switch_camera"
        private const val CMD_START_VIDEO = "start_video"
        private const val CMD_ZOOM_IN = "zoom_in"
        private const val CMD_ZOOM_OUT = "zoom_out"
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
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .addContent(
                Column.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder()
                            .setText("CameraRemote")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(12f))
                                    .setColor(argb(0xFFE0E0E0.toInt()))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                                    .build()
                            )
                            .build()
                    )
                    .addContent(Spacer.Builder().setHeight(dp(6f)).build())
                    // Main capture button
                    .addContent(buildButton("CAPTURE", CMD_TAKE_PHOTO, 0xFFD32F2F.toInt(), 0xFFFFFFFF.toInt(), 120f, 40f))
                    .addContent(Spacer.Builder().setHeight(dp(6f)).build())
                    // Row: Flash | Flip
                    .addContent(
                        Row.Builder()
                            .addContent(buildButton("FLASH", CMD_TOGGLE_FLASH, 0xFF424242.toInt(), 0xFFE0E0E0.toInt(), 56f, 32f))
                            .addContent(Spacer.Builder().setWidth(dp(6f)).build())
                            .addContent(buildButton("FLIP", CMD_SWITCH_CAMERA, 0xFF424242.toInt(), 0xFFE0E0E0.toInt(), 56f, 32f))
                            .build()
                    )
                    .addContent(Spacer.Builder().setHeight(dp(4f)).build())
                    // Row: Record | Zoom+/-
                    .addContent(
                        Row.Builder()
                            .addContent(buildButton("REC", CMD_START_VIDEO, 0xFFB71C1C.toInt(), 0xFFFFFFFF.toInt(), 38f, 32f))
                            .addContent(Spacer.Builder().setWidth(dp(4f)).build())
                            .addContent(buildButton(" - ", CMD_ZOOM_OUT, 0xFF424242.toInt(), 0xFFE0E0E0.toInt(), 38f, 32f))
                            .addContent(Spacer.Builder().setWidth(dp(4f)).build())
                            .addContent(buildButton(" + ", CMD_ZOOM_IN, 0xFF424242.toInt(), 0xFFE0E0E0.toInt(), 38f, 32f))
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun buildButton(
        label: String,
        command: String,
        bgColor: Int,
        textColor: Int,
        width: Float,
        height: Float
    ): LayoutElementBuilders.LayoutElement {
        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setClassName("com.cameraremote.wear.TileActionActivity")
                    .setPackageName("com.cameraremote.wear")
                    .addKeyToExtraMapping(
                        "command",
                        ActionBuilders.AndroidStringExtra.Builder()
                            .setValue(command)
                            .build()
                    )
                    .build()
            )
            .build()

        return Box.Builder()
            .setWidth(dp(width))
            .setHeight(dp(height))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(bgColor))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(height / 2))
                                    .build()
                            )
                            .build()
                    )
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setOnClick(launchAction)
                            .setId(command)
                            .build()
                    )
                    .build()
            )
            .addContent(
                Text.Builder()
                    .setText(label)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(11f))
                            .setColor(argb(textColor))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_MEDIUM)
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
