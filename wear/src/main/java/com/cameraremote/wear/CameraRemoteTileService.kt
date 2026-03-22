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
        private const val RESOURCES_VERSION = "6"

        // The actual class package differs from applicationId
        private const val TILE_ACTION_CLASS = "com.cameraremote.wear.TileActionActivity"
        private const val REMOTE_ACTIVITY_CLASS = "com.cameraremote.wear.RemoteActivity"

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
        private const val COLOR_OPEN_APP = 0xFFD0BCFF.toInt()

        // Command strings
        private const val CMD_OPEN_CAMERA = "open_camera"
        private const val CMD_CAPTURE = "capture"
        private const val CMD_OPEN_VIDEO = "open_video"
        private const val CMD_TOGGLE_FLASH = "toggle_flash"
        private const val CMD_SWITCH_CAMERA = "switch_camera"
        private const val CMD_CAPTURE_TIMER = "capture_timer"

        // Extra key
        private const val EXTRA_COMMAND = "command"

        // Dimensions
        private const val BUTTON_SIZE = 44f
        private const val ICON_SIZE = 20f
        private const val BUTTON_CORNER_RADIUS = 22f
        private const val ROW_ITEM_SPACING = 8f
        private const val LABEL_SPACING = 3f
        private const val TITLE_FONT_SIZE = 13f
        private const val LABEL_FONT_SIZE = 10f

        // Resource IDs for icons
        private const val RES_IC_CAMERA = "ic_camera"
        private const val RES_IC_SHUTTER = "ic_shutter"
        private const val RES_IC_VIDEO = "ic_video"
        private const val RES_IC_FLASH = "ic_flash"
        private const val RES_IC_FLIP = "ic_flip"
        private const val RES_IC_TIMER = "ic_timer"
        private const val RES_IC_OPEN_APP = "ic_open_app"
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
        val builder = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)

        // Register drawable resources by Android resource ID
        mapOf(
            RES_IC_CAMERA to R.drawable.tile_ic_photo_camera,
            RES_IC_SHUTTER to R.drawable.tile_ic_shutter,
            RES_IC_VIDEO to R.drawable.tile_ic_videocam,
            RES_IC_FLASH to R.drawable.tile_ic_flash,
            RES_IC_FLIP to R.drawable.tile_ic_flip_camera,
            RES_IC_TIMER to R.drawable.tile_ic_timer,
            RES_IC_OPEN_APP to R.drawable.tile_ic_open_app
        ).forEach { (id, drawableRes) ->
            builder.addIdToImageMapping(
                id,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(drawableRes)
                            .build()
                    )
                    .build()
            )
        }

        return Futures.immediateFuture(builder.build())
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
                        button("Camera", CMD_OPEN_CAMERA, COLOR_CAMERA, RES_IC_CAMERA),
                        button("Snap", CMD_CAPTURE, COLOR_SHUTTER, RES_IC_SHUTTER),
                        button("Video", CMD_OPEN_VIDEO, COLOR_VIDEO, RES_IC_VIDEO)
                    ))
                    .addContent(spacer(6f))
                    .addContent(row(
                        button("Flash", CMD_TOGGLE_FLASH, COLOR_FLASH, RES_IC_FLASH),
                        button("Flip", CMD_SWITCH_CAMERA, COLOR_SWITCH, RES_IC_FLIP),
                        button("Timer", CMD_CAPTURE_TIMER, COLOR_TIMER, RES_IC_TIMER)
                    ))
                    .addContent(spacer(6f))
                    .addContent(openAppButton())
                    .build()
            )
            .build()
    }

    private fun title(): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Text.Builder()
            .setText("Camera Remote")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(TITLE_FONT_SIZE))
                    .setColor(argb(COLOR_TITLE))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .build()
            )
            .build()
    }

    private fun spacer(h: Float): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()
    }

    private fun openAppButton(): LayoutElementBuilders.LayoutElement {
        val action = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(REMOTE_ACTIVITY_CLASS)
                    .build()
            )
            .build()

        return LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(dp(BUTTON_SIZE))
                    .setHeight(dp(BUTTON_SIZE))
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setClickable(
                                ModifiersBuilders.Clickable.Builder()
                                    .setId("open_app")
                                    .setOnClick(action)
                                    .build()
                            )
                            .setBackground(
                                ModifiersBuilders.Background.Builder()
                                    .setColor(argb(COLOR_OPEN_APP))
                                    .setCorner(
                                        ModifiersBuilders.Corner.Builder()
                                            .setRadius(dp(BUTTON_CORNER_RADIUS))
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Image.Builder()
                            .setResourceId(RES_IC_OPEN_APP)
                            .setWidth(dp(ICON_SIZE))
                            .setHeight(dp(ICON_SIZE))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setWidth(dp(ROW_ITEM_SPACING))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("Open App")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(LABEL_FONT_SIZE))
                            .setColor(argb(COLOR_LABEL))
                            .build()
                    )
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setClickable(
                                ModifiersBuilders.Clickable.Builder()
                                    .setId("open_app_label")
                                    .setOnClick(action)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun row(vararg items: LayoutElementBuilders.LayoutElement): LayoutElementBuilders.LayoutElement {
        val builder = LayoutElementBuilders.Row.Builder()
        items.forEachIndexed { i, item ->
            if (i > 0) builder.addContent(
                LayoutElementBuilders.Spacer.Builder().setWidth(dp(ROW_ITEM_SPACING)).build()
            )
            builder.addContent(item)
        }
        return builder.build()
    }

    private fun button(
        label: String,
        command: String,
        bgColor: Int,
        iconResId: String
    ): LayoutElementBuilders.LayoutElement {
        val action = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(TILE_ACTION_CLASS)
                    .addKeyToExtraMapping(
                        EXTRA_COMMAND,
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
                    .setWidth(dp(BUTTON_SIZE))
                    .setHeight(dp(BUTTON_SIZE))
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
                                            .setRadius(dp(BUTTON_CORNER_RADIUS))
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Image.Builder()
                            .setResourceId(iconResId)
                            .setWidth(dp(ICON_SIZE))
                            .setHeight(dp(ICON_SIZE))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(dp(LABEL_SPACING))
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(label)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(LABEL_FONT_SIZE))
                            .setColor(argb(COLOR_LABEL))
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
