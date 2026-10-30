/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.ContentResolver;
import android.graphics.Color;
import android.provider.Settings;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Settings keys and value conversions for the screen color screens and ScreenColorService.
 *
 * Stock ZUI keeps the Standard and Vivid choices in Secure mtk_color_mode /
 * mtk_color_temperature and System auto_color_temperature, read by ZuiDisplayService in
 * services.jar; here they live in this app's own Secure keys. Disabled withdraws this app's
 * picture-quality tuning so the system's Display color mode and panel defaults take over.
 * Picture adjustments and per-app color settings are additions.
 */
public final class ColorSettings {
    /** Color mode, one of the MODE_* values. */
    public static final String KEY_MODE = "tb375fc_color_mode";
    /** Color temperature, one of the TEMPERATURE_* values. */
    public static final String KEY_TEMPERATURE = "tb375fc_color_temperature";
    /** Point picked on the color wheel for TEMPERATURE_CUSTOM: "x,y", each 0..1. */
    public static final String KEY_TEMPERATURE_POINT = "tb375fc_color_temperature_point";
    /** 1 = adapt the white point to the ambient light (MediaTek Chameleon). */
    public static final String KEY_ADAPTIVE = "tb375fc_color_adaptive";
    /** 1 = use the app color settings. */
    public static final String KEY_APP_PROFILES_ENABLED = "tb375fc_color_app_profiles_enabled";
    /** App color settings, "package=value,...", each value a MODE_* value. */
    public static final String KEY_APP_PROFILES = "tb375fc_color_app_profiles";
    /** 1 = MediaTek's video picture enhancements (VIDEO_FEATURES) apply, 0 = all off. */
    public static final String KEY_VIDEO_ENHANCE = "tb375fc_video_enhance";

    // Values of stock's mtk_color_mode, as ZuiDisplayService.setPictureMode handles them.
    public static final int MODE_STANDARD = 0;
    public static final int MODE_VIVID = 1;
    /** Use panel PQ XML defaults and let the system's Display color mode decide the look. */
    public static final int MODE_DISABLED = 3;
    /** Stock's default (ZuiDisplayService reads mtk_color_mode with default 1). */
    public static final int DEFAULT_MODE = MODE_VIVID;

    public static final int TEMPERATURE_STANDARD = 0;
    public static final int TEMPERATURE_WARM = 1;
    public static final int TEMPERATURE_COOL = 2;
    public static final int TEMPERATURE_CUSTOM = 3;

    /** App color setting: the screen color settings apply. Not stored. */
    public static final int APP_DEFAULT = -1;

    /**
     * One MediaTek picture adjustment: a setPQIndex index with levels 0..LEVEL_MAX. The
     * default level is the one the vendor tables start from (libjni_pq.so returns it when
     * persist.vendor.sys.pq.gsatidx / contrastidx / shp.idx are unset). LEVEL_DEFAULT stored
     * means the user never changed it, so nothing is sent.
     */
    public static final class Adjustment {
        public static final int LEVEL_DEFAULT = -1;
        public static final int LEVEL_MAX = 9;

        public final String key;
        public final int pqIndex;
        public final int defaultLevel;

        Adjustment(String key, int pqIndex, int defaultLevel) {
            this.key = key;
            this.pqIndex = pqIndex;
            this.defaultLevel = defaultLevel;
        }

        /** The stored level, LEVEL_DEFAULT if unchanged. */
        public int get(ContentResolver cr) {
            final int level = Settings.Secure.getInt(cr, key, LEVEL_DEFAULT);
            return level < 0 || level > LEVEL_MAX ? LEVEL_DEFAULT : level;
        }

        public void put(ContentResolver cr, int level) {
            Settings.Secure.putInt(cr, key, level == defaultLevel ? LEVEL_DEFAULT : level);
        }
    }

    // setPQIndex indexes libjni_pq.so uses for setSaturationIndex, setContrastIndex and
    // setSharpnessIndex of com.mediatek.pq.PictureQuality.
    public static final Adjustment SATURATION =
            new Adjustment("tb375fc_color_saturation", 1, 4);
    public static final Adjustment CONTRAST =
            new Adjustment("tb375fc_color_contrast", 9, 4);
    public static final Adjustment SHARPNESS =
            new Adjustment("tb375fc_color_sharpness", 0, 2);
    public static final Adjustment[] ADJUSTMENTS = {SATURATION, CONTRAST, SHARPNESS};

    /**
     * One of MediaTek's enhancements of video layers (MDP/MML), switched with the picture
     * quality service's setFeatureSwitch. The service turns them all on when unset
     * (persist.vendor.sys.pq.*.en default to 1 in initPQProperty); the defaults here are
     * stock's: ZuiDisplayService only turns ultra resolution off (its "video quality enhance"
     * switch, off by default) and leaves the others on.
     */
    public static final class VideoFeature {
        public final String key;
        /** PQFeatureID values sent together. */
        final int[] featureIds;
        public final boolean defaultEnabled;

        VideoFeature(String key, boolean defaultEnabled, int... featureIds) {
            this.key = key;
            this.defaultEnabled = defaultEnabled;
            this.featureIds = featureIds;
        }

        public boolean isEnabled(ContentResolver cr) {
            return Settings.Secure.getInt(cr, key, defaultEnabled ? 1 : 0) != 0;
        }

        public void setEnabled(ContentResolver cr, boolean enabled) {
            Settings.Secure.putInt(cr, key, enabled ? 1 : 0);
        }
    }

    // PQFeatureID values the enable methods of com.mediatek.pq.PictureQuality pass to
    // setFeatureSwitch in libjni_pq.so, with the property the service keeps each in.
    /** enableSharpness: 3, persist.vendor.sys.pq.shp.en. */
    public static final VideoFeature VIDEO_SHARPNESS =
            new VideoFeature("tb375fc_video_sharpness", true, 3);
    /** enableDynamicSharpness: 5, dshp.en. */
    public static final VideoFeature VIDEO_DYNAMIC_SHARPNESS =
            new VideoFeature("tb375fc_video_dynamic_sharpness", true, 5);
    /** enableISOAdaptiveSharpness: 9, iso.shp.en. */
    public static final VideoFeature VIDEO_ISO_SHARPNESS =
            new VideoFeature("tb375fc_video_iso_sharpness", true, 9);
    /** enableUltraResolution: 10, ultrares.en. */
    public static final VideoFeature VIDEO_ULTRA_RESOLUTION =
            new VideoFeature("tb375fc_video_ultra_resolution", false, 10);
    /** enableDynamicContrast: 4, adl.idx; enableMdpDRE: 13, mdp.vp.dre.en. */
    public static final VideoFeature VIDEO_DYNAMIC_CONTRAST =
            new VideoFeature("tb375fc_video_dynamic_contrast", true, 4, 13);
    /** enableColorEffect (content color for video): 2, mdp.color.idx. */
    public static final VideoFeature VIDEO_COLOR =
            new VideoFeature("tb375fc_video_color", true, 2);
    public static final VideoFeature[] VIDEO_FEATURES = {
            VIDEO_SHARPNESS, VIDEO_DYNAMIC_SHARPNESS, VIDEO_ISO_SHARPNESS,
            VIDEO_ULTRA_RESOLUTION, VIDEO_DYNAMIC_CONTRAST, VIDEO_COLOR};

    /** The panel's unit for CCORR coefficients and RGB gains: stock uses 1024 on peridot. */
    static final int UNIT = 1024;

    // Stock ScreenTonePrefsX presets as 8-bit RGB, and where it puts the wheel's dot for them.
    private static final int[] RGB_STANDARD = {255, 255, 255};
    private static final int[] RGB_WARM = {255, 238, 215};
    private static final int[] RGB_COOL = {228, 229, 255};
    private static final float[] POINT_STANDARD = {0.5f, 0.5f};
    private static final float[] POINT_WARM = {0.286f, 0.195f};
    private static final float[] POINT_COOL = {0.878f, 0.666f};

    /**
     * Saturation of the wheel's rim as drawn, and how much of it the white point takes, by hue
     * band: stock's color ring image is about 0.6 saturated at the rim and its
     * ColorTemperaturePickerView scales the picked saturation by these factors.
     */
    static final float WHEEL_RIM_SATURATION = 0.6f;

    private ColorSettings() {
    }

    public static int getMode(ContentResolver cr) {
        return sanitizeMode(Settings.Secure.getInt(cr, KEY_MODE, DEFAULT_MODE));
    }

    public static void putMode(ContentResolver cr, int mode) {
        Settings.Secure.putInt(cr, KEY_MODE, sanitizeMode(mode));
    }

    public static int getTemperature(ContentResolver cr) {
        final int t = Settings.Secure.getInt(cr, KEY_TEMPERATURE, TEMPERATURE_STANDARD);
        return t >= TEMPERATURE_STANDARD && t <= TEMPERATURE_CUSTOM ? t : TEMPERATURE_STANDARD;
    }

    /** Selects a preset; TEMPERATURE_CUSTOM keeps the stored point. */
    public static void putTemperature(ContentResolver cr, int temperature) {
        Settings.Secure.putInt(cr, KEY_TEMPERATURE, temperature);
    }

    /** Selects TEMPERATURE_CUSTOM at the given wheel point. */
    public static void putCustomTemperature(ContentResolver cr, float x, float y) {
        Settings.Secure.putString(cr, KEY_TEMPERATURE_POINT,
                String.format(Locale.ROOT, "%.4f,%.4f", x, y));
        Settings.Secure.putInt(cr, KEY_TEMPERATURE, TEMPERATURE_CUSTOM);
    }

    /** Where the wheel's dot is for the current temperature. */
    public static float[] getTemperaturePoint(ContentResolver cr) {
        switch (getTemperature(cr)) {
            case TEMPERATURE_WARM:
                return POINT_WARM.clone();
            case TEMPERATURE_COOL:
                return POINT_COOL.clone();
            case TEMPERATURE_CUSTOM:
                return parsePoint(Settings.Secure.getString(cr, KEY_TEMPERATURE_POINT));
            default:
                return POINT_STANDARD.clone();
        }
    }

    /** RGB gains of the current temperature, in UNIT. */
    static int[] getTemperatureGains(ContentResolver cr) {
        switch (getTemperature(cr)) {
            case TEMPERATURE_WARM:
                return toGains(RGB_WARM);
            case TEMPERATURE_COOL:
                return toGains(RGB_COOL);
            case TEMPERATURE_CUSTOM:
                final float[] point = getTemperaturePoint(cr);
                return toGains(wheelPointToRgb(point[0], point[1]));
            default:
                return neutralGains();
        }
    }

    static int[] neutralGains() {
        return toGains(RGB_STANDARD);
    }

    public static boolean isAdaptive(ContentResolver cr) {
        return Settings.Secure.getInt(cr, KEY_ADAPTIVE, 0) != 0;
    }

    public static void setAdaptive(ContentResolver cr, boolean enabled) {
        Settings.Secure.putInt(cr, KEY_ADAPTIVE, enabled ? 1 : 0);
    }

    public static boolean isAppProfilesEnabled(ContentResolver cr) {
        return Settings.Secure.getInt(cr, KEY_APP_PROFILES_ENABLED, 1) != 0;
    }

    public static void setAppProfilesEnabled(ContentResolver cr, boolean enabled) {
        Settings.Secure.putInt(cr, KEY_APP_PROFILES_ENABLED, enabled ? 1 : 0);
    }

    public static boolean isVideoEnhance(ContentResolver cr) {
        return Settings.Secure.getInt(cr, KEY_VIDEO_ENHANCE, 1) != 0;
    }

    public static void setVideoEnhance(ContentResolver cr, boolean enabled) {
        Settings.Secure.putInt(cr, KEY_VIDEO_ENHANCE, enabled ? 1 : 0);
    }

    /** Whether each VIDEO_FEATURES entry applies: its switch, and the main switch. */
    static boolean[] getVideoFeatures(ContentResolver cr) {
        final boolean main = isVideoEnhance(cr);
        final boolean[] enabled = new boolean[VIDEO_FEATURES.length];
        for (int i = 0; i < enabled.length; i++) {
            enabled[i] = main && VIDEO_FEATURES[i].isEnabled(cr);
        }
        return enabled;
    }

    public static Map<String, Integer> getAppProfiles(ContentResolver cr) {
        return parseAppProfiles(Settings.Secure.getString(cr, KEY_APP_PROFILES));
    }

    /** Stores the app's color setting, or forgets it for APP_DEFAULT. */
    public static void putAppProfile(ContentResolver cr, String pkg, int profile) {
        final Map<String, Integer> profiles = getAppProfiles(cr);
        if (isAppProfile(profile) && profile != APP_DEFAULT) {
            profiles.put(pkg, profile);
        } else {
            profiles.remove(pkg);
        }
        Settings.Secure.putString(cr, KEY_APP_PROFILES, formatAppProfiles(profiles));
    }

    /**
     * The 8-bit color whose components are the white point gains for a wheel point (x and y
     * relative to the wheel's bounding square). Hue follows the drawn wheel: red at the top,
     * then counter-clockwise through yellow, green, cyan and blue to magenta. Saturation grows
     * with the distance from the centre up to the rim's, scaled down by stock's factors so the
     * whole wheel stays a gentle tint; value is 1, so the strongest channel keeps full gain.
     */
    static int[] wheelPointToRgb(float x, float y) {
        final double dx = x - 0.5;
        final double dy = y - 0.5;
        final double radius = Math.min(1.0, Math.hypot(dx, dy) / 0.5);
        final double clockwiseFromTop = Math.toDegrees(Math.atan2(dx, -dy));
        final float hue = wheelHue(clockwiseFromTop);
        final float saturation = (float) (radius * WHEEL_RIM_SATURATION * hueBandFactor(hue));
        final int color = Color.HSVToColor(new float[] {hue, saturation, 1f});
        return new int[] {Color.red(color), Color.green(color), Color.blue(color)};
    }

    /** Hue drawn at an angle, in degrees clockwise from the top. */
    static float wheelHue(double clockwiseFromTop) {
        final double hue = (360.0 - clockwiseFromTop) % 360.0;
        return (float) (hue < 0 ? hue + 360.0 : hue);
    }

    /** ColorTemperaturePickerView.dopixel saturation factors. */
    private static double hueBandFactor(float hue) {
        if (hue > 60f && hue <= 180f) {
            return 0.2275;
        }
        if (hue > 180f && hue <= 300f) {
            return 0.1804;
        }
        return 0.2706;
    }

    /**
     * As stock: ScreenTonePrefsX stores each component / 255 with two decimals and
     * PictureQuality.setRGBGain truncates value * 1024.
     */
    private static int[] toGains(int[] rgb) {
        final int[] gains = new int[3];
        for (int i = 0; i < 3; i++) {
            final double rounded = Math.round(rgb[i] / 255.0 * 100.0) / 100.0;
            gains[i] = (int) (UNIT * rounded);
        }
        return gains;
    }

    /** Missing or malformed points read as the centre. */
    static float[] parsePoint(String value) {
        if (value != null) {
            final String[] parts = value.split(",");
            if (parts.length == 2) {
                try {
                    return new float[] {
                            clamp01(Float.parseFloat(parts[0].trim())),
                            clamp01(Float.parseFloat(parts[1].trim()))};
                } catch (NumberFormatException e) {
                    // Fall through.
                }
            }
        }
        return POINT_STANDARD.clone();
    }

    /** Malformed entries are skipped. */
    static Map<String, Integer> parseAppProfiles(String value) {
        final Map<String, Integer> profiles = new TreeMap<>();
        if (value == null || value.isEmpty()) {
            return profiles;
        }
        for (String entry : value.split(",")) {
            final int sep = entry.indexOf('=');
            if (sep <= 0) {
                continue;
            }
            try {
                final int profile = Integer.parseInt(entry.substring(sep + 1).trim());
                if (isAppProfile(profile) && profile != APP_DEFAULT) {
                    profiles.put(entry.substring(0, sep).trim(), profile);
                }
            } catch (NumberFormatException e) {
                // Skip.
            }
        }
        return profiles;
    }

    static String formatAppProfiles(Map<String, Integer> profiles) {
        final StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(profiles).entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    static boolean isAppProfile(int profile) {
        return profile == APP_DEFAULT || isAppMode(profile);
    }

    /** Modes an app can have instead of the screen color settings: all of them. */
    static boolean isAppMode(int mode) {
        return mode == MODE_STANDARD || mode == MODE_VIVID || mode == MODE_DISABLED;
    }

    /** Unrecognized stored modes, including the retired value 2, fall back to Vivid. */
    static int sanitizeMode(int mode) {
        return mode == MODE_STANDARD || mode == MODE_VIVID || mode == MODE_DISABLED
                ? mode : MODE_VIVID;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
