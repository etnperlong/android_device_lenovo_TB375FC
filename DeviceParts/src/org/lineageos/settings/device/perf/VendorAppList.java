/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MediaTek's per-app power rules from the stock vendor partition.
 *
 * The vendor IMtkpower_applist service (lib_power_applist.so) reads /vendor/etc/power_app_cfg.xml
 * once at start and, for the app it is told is in the foreground, acquires a power HAL lock with
 * the resources of the matching rule. Resource names are turned into IDs with
 * /vendor/etc/command.csv. This class reads the same files so the rules can be shown and reused
 * as presets for apps that are not in the list. Only the generic rules (Activity, FPS and WINDOW
 * all "Common") are used; the per-frame-rate variants of a few games stay with the vendor service.
 */
public final class VendorAppList {
    private static final String TAG = "VendorAppList";

    private static final String APP_CFG = "/vendor/etc/power_app_cfg.xml";
    private static final String COMMANDS = "/vendor/etc/command.csv";
    private static final String COMMON = "Common";

    static final String THERMAL_POLICY = "PERF_RES_THERMAL_POLICY";
    static final String FPSGO_RENDER_PID = "PERF_RES_FPS_FPSGO_RENDER_PID";
    private static final String CPUFREQ_MIN = "PERF_RES_CPUFREQ_MIN_CLUSTER_";
    private static final String CPUFREQ_MAX = "PERF_RES_CPUFREQ_MAX_CLUSTER_";

    /** One resource setting of a rule. */
    public static final class Rule {
        public final String command;
        public final int value;

        Rule(String command, int value) {
            this.command = command;
            this.value = value;
        }
    }

    /**
     * One distinct rule set. Several packages can share it; it is identified by the first of
     * them in the file.
     */
    public static final class Preset {
        public final String id;
        public final List<Rule> rules;
        public final List<String> packages = new ArrayList<>();

        Preset(String id, List<Rule> rules) {
            this.id = id;
            this.rules = Collections.unmodifiableList(rules);
        }

        /** Value of the given resource, or null if the rule does not set it. */
        public Integer valueOf(String command) {
            for (Rule rule : rules) {
                if (rule.command.equals(command)) {
                    return rule.value;
                }
            }
            return null;
        }
    }

    private static VendorAppList sInstance;

    private final Map<String, Integer> mCommandIds;
    private final List<Preset> mPresets = new ArrayList<>();
    private final Map<String, Preset> mPresetsById = new HashMap<>();
    /** Every listed package, mapped to its generic rule set or null if it has none. */
    private final Map<String, Preset> mAppPresets = new LinkedHashMap<>();

    VendorAppList(Map<String, Integer> commandIds) {
        mCommandIds = commandIds;
    }

    /** The vendor files never change at runtime, so they are read once per process. */
    public static synchronized VendorAppList get() {
        if (sInstance == null) {
            Map<String, Integer> ids = Collections.emptyMap();
            try (Reader reader = new BufferedReader(new FileReader(COMMANDS))) {
                ids = parseCommands(reader);
            } catch (IOException e) {
                Log.e(TAG, "Cannot read " + COMMANDS, e);
            }
            final VendorAppList list = new VendorAppList(ids);
            try (Reader reader = new BufferedReader(new FileReader(APP_CFG))) {
                final XmlPullParser parser = Xml.newPullParser();
                parser.setInput(reader);
                list.parseAppConfig(parser);
            } catch (IOException | XmlPullParserException e) {
                Log.e(TAG, "Cannot read " + APP_CFG, e);
            }
            sInstance = list;
        }
        return sInstance;
    }

    /**
     * command.csv lines are "COMMAND,MAJOR,MINOR,GROUP"; the resource ID libpowerhal expects is
     * (major << 22) | (minor << 14) | (group << 8), e.g. PERF_RES_THERMAL_POLICY 12,0,0 is
     * 0x03000000 and PERF_RES_SCHED_UCLAMP_MIN_TA 5,2,3 is 0x01408300 as in powercontable.xml.
     */
    static Map<String, Integer> parseCommands(Reader reader) throws IOException {
        final Map<String, Integer> ids = new HashMap<>();
        final BufferedReader lines = new BufferedReader(reader);
        String line;
        while ((line = lines.readLine()) != null) {
            final String[] fields = line.trim().split(",");
            if (fields.length != 4) {
                continue;
            }
            try {
                final int major = Integer.parseInt(fields[1].trim());
                final int minor = Integer.parseInt(fields[2].trim());
                final int group = Integer.parseInt(fields[3].trim());
                ids.put(fields[0].trim(), (major << 22) | (minor << 14) | (group << 8));
            } catch (NumberFormatException e) {
                // The header line.
            }
        }
        return ids;
    }

    /**
     * Layout: WHITELIST / Package name / Activity name / FPS value / WINDOW mode / data cmd
     * param1. A package may be listed twice; like the vendor service, the last generic rule
     * wins (com.vng.pubgmobile, listed as scenarios 24 and 38, gets scenario 38's 7 rules:
     * "AppScenarioEnable idx:38", "perf_lock_acq num:16").
     */
    void parseAppConfig(XmlPullParser parser) throws IOException, XmlPullParserException {
        String pkg = null;
        boolean commonActivity = false;
        boolean commonFps = false;
        List<Rule> rules = null;
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT;
                event = parser.next()) {
            if (event == XmlPullParser.START_TAG) {
                switch (parser.getName()) {
                    case "Package":
                        pkg = parser.getAttributeValue(null, "name");
                        if (pkg != null && !mAppPresets.containsKey(pkg)) {
                            mAppPresets.put(pkg, null);
                        }
                        break;
                    case "Activity":
                        commonActivity = COMMON.equals(parser.getAttributeValue(null, "name"));
                        break;
                    case "FPS":
                        commonFps = COMMON.equals(parser.getAttributeValue(null, "value"));
                        break;
                    case "WINDOW":
                        if (commonActivity && commonFps
                                && COMMON.equals(parser.getAttributeValue(null, "mode"))) {
                            rules = new ArrayList<>();
                        }
                        break;
                    case "data":
                        if (rules != null) {
                            final String cmd = parser.getAttributeValue(null, "cmd");
                            final String value = parser.getAttributeValue(null, "param1");
                            try {
                                rules.add(new Rule(cmd.trim(), Integer.parseInt(value.trim())));
                            } catch (NullPointerException | NumberFormatException e) {
                                Log.w(TAG, "Skipping malformed rule of " + pkg);
                            }
                        }
                        break;
                    default:
                        break;
                }
            } else if (event == XmlPullParser.END_TAG && "WINDOW".equals(parser.getName())
                    && rules != null) {
                addRules(pkg, rules);
                rules = null;
            }
        }
    }

    private void addRules(String pkg, List<Rule> rules) {
        if (pkg == null) {
            return;
        }
        final Preset previous = mAppPresets.get(pkg);
        if (previous != null) {
            previous.packages.remove(pkg);
        }
        Preset preset = null;
        for (Preset existing : mPresets) {
            if (sameRules(existing.rules, rules)) {
                preset = existing;
                break;
            }
        }
        if (preset == null) {
            // Named after the package, unless that package already named an earlier set.
            String id = pkg;
            for (int n = 2; mPresetsById.containsKey(id); n++) {
                id = pkg + "#" + n;
            }
            preset = new Preset(id, rules);
            mPresets.add(preset);
            mPresetsById.put(preset.id, preset);
        }
        preset.packages.add(pkg);
        mAppPresets.put(pkg, preset);
    }

    private static boolean sameRules(List<Rule> a, List<Rule> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).command.equals(b.get(i).command) || a.get(i).value != b.get(i).value) {
                return false;
            }
        }
        return true;
    }

    /** Whether the vendor service has rules for the package. */
    public boolean isListed(String pkg) {
        return mAppPresets.containsKey(pkg);
    }

    /** The package's generic rule set, or null. */
    public Preset getAppPreset(String pkg) {
        return mAppPresets.get(pkg);
    }

    /** Distinct generic rule sets in file order. */
    public List<Preset> getPresets() {
        return Collections.unmodifiableList(mPresets);
    }

    public Preset getPreset(String id) {
        return id == null ? null : mPresetsById.get(id);
    }

    /** Resource ID of a command, or null if command.csv does not define it. */
    public Integer getCommandId(String command) {
        return mCommandIds.get(command);
    }

    static String cpuFreqMinCommand(int cluster) {
        return CPUFREQ_MIN + cluster;
    }

    static String cpuFreqMaxCommand(int cluster) {
        return CPUFREQ_MAX + cluster;
    }

    static boolean isCpuFreqCommand(String command) {
        return command.startsWith(CPUFREQ_MIN) || command.startsWith(CPUFREQ_MAX);
    }
}
