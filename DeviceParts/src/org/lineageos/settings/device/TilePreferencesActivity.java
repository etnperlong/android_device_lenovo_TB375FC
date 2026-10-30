/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import org.lineageos.settings.device.color.ColorAdaptiveTile;
import org.lineageos.settings.device.color.ScreenColorActivity;
import org.lineageos.settings.device.color.ScreenColorTile;
import org.lineageos.settings.device.color.VideoEnhanceTile;
import org.lineageos.settings.device.color.VideoPictureActivity;
import org.lineageos.settings.device.cover.SmartCoverTile;
import org.lineageos.settings.device.display.ReadingLowRefreshTile;
import org.lineageos.settings.device.dolby.DolbyActivity;
import org.lineageos.settings.device.dolby.DolbyTile;
import org.lineageos.settings.device.perf.PerformanceActivity;
import org.lineageos.settings.device.perf.PerformanceTile;
import org.lineageos.settings.device.touch.TouchSensitivityTile;

import java.util.Map;

/**
 * Opens the settings page of a long-pressed Quick Settings tile. SystemUI starts the one
 * ACTION_QS_TILE_PREFERENCES activity of the tile's package and names the tile in
 * EXTRA_COMPONENT_NAME.
 */
public class TilePreferencesActivity extends Activity {
    private static final Map<String, Class<? extends Activity>> PAGES = Map.of(
            ReadingLowRefreshTile.class.getName(), DisplayPartsActivity.class,
            TouchSensitivityTile.class.getName(), DisplayPartsActivity.class,
            SmartCoverTile.class.getName(), AccessoriesActivity.class,
            DolbyTile.class.getName(), DolbyActivity.class,
            ScreenColorTile.class.getName(), ScreenColorActivity.class,
            ColorAdaptiveTile.class.getName(), ScreenColorActivity.class,
            VideoEnhanceTile.class.getName(), VideoPictureActivity.class,
            PerformanceTile.class.getName(), PerformanceActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ComponentName tile =
                getIntent().getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName.class);
        final Class<? extends Activity> page =
                tile != null ? PAGES.get(tile.getClassName()) : null;
        startActivity(new Intent(this, page != null ? page : DevicePartsActivity.class));
        finish();
    }
}
