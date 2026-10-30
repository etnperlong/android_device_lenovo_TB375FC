/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.service.quicksettings.TileService;
import android.view.ContextThemeWrapper;
import android.widget.TextView;

import java.util.function.IntConsumer;

/**
 * Single-choice dialog a Quick Settings tile shows over the shade: picking an entry applies it
 * and closes the dialog.
 */
public final class TileChoiceDialog {
    private TileChoiceDialog() {}

    /**
     * Shows the dialog, after unlocking if needed.
     *
     * @param note shown under the entries, or null
     */
    public static void show(TileService tile, int titleRes, CharSequence[] entries, int checked,
            CharSequence note, IntConsumer onChoice) {
        if (tile.isLocked()) {
            tile.unlockAndRun(() -> showNow(tile, titleRes, entries, checked, note, onChoice));
        } else {
            showNow(tile, titleRes, entries, checked, note, onChoice);
        }
    }

    private static void showNow(TileService tile, int titleRes, CharSequence[] entries,
            int checked, CharSequence note, IntConsumer onChoice) {
        // The system dialog look; the Settings theme is not the same resource in every build.
        final Context context =
                new ContextThemeWrapper(tile, android.R.style.Theme_DeviceDefault_DayNight);
        final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(titleRes)
                .setSingleChoiceItems(entries, checked, (dialog, which) -> {
                    onChoice.accept(which);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (note != null) {
            // A message would replace the list, so the note goes below it as a view.
            builder.setView(noteView(context, note));
        }
        tile.showDialog(builder.create());
    }

    private static TextView noteView(Context context, CharSequence note) {
        final TypedArray a = context.obtainStyledAttributes(new int[] {
                android.R.attr.dialogPreferredPadding,
                android.R.attr.textAppearanceSmall,
                android.R.attr.textColorSecondary});
        final int padding = a.getDimensionPixelSize(0, 0);
        final TextView view = new TextView(context);
        view.setTextAppearance(a.getResourceId(1, 0));
        view.setTextColor(a.getColorStateList(2));
        a.recycle();
        view.setPadding(padding, padding / 2, padding, 0);
        view.setText(note);
        return view;
    }
}
