package org.tasks.widget;

import static com.todoroo.andlib.utility.AndroidUtilities.atLeastOreo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnFocusChange;
import com.google.common.base.Strings;
import com.todoroo.astrid.api.Filter;
import javax.inject.Inject;
import org.tasks.R;
import org.tasks.activities.ColorPickerActivity;
import org.tasks.activities.FilterSelectionActivity;
import org.tasks.analytics.Tracker;
import org.tasks.analytics.Tracking;
import org.tasks.injection.ActivityComponent;
import org.tasks.injection.InjectingAppCompatActivity;
import org.tasks.intents.TaskIntents;
import org.tasks.preferences.DefaultFilterProvider;
import org.tasks.themes.ThemeCache;
import org.tasks.themes.ThemeColor;
import org.tasks.ui.MenuColorizer;

public class ShortcutConfigActivity extends InjectingAppCompatActivity {

  private static final int REQUEST_FILTER = 1019;
  private static final int REQUEST_COLOR_PICKER = 1020;

  @Inject DefaultFilterProvider defaultFilterProvider;
  @Inject Tracker tracker;
  @Inject ThemeColor themeColor;
  @Inject ThemeCache themeCache;

  @BindView(R.id.toolbar)
  Toolbar toolbar;

  @BindView(R.id.shortcut_list_layout)
  TextInputLayout shortcutListLayout;

  @BindView(R.id.shortcut_list)
  TextInputEditText shortcutList;

  @BindView(R.id.shortcut_name)
  TextInputEditText shortcutName;

  @BindView(R.id.shortcut_color)
  TextInputEditText shortcutColor;

  private Filter selectedFilter;
  private int selectedTheme = -1;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    setContentView(R.layout.activity_widget_shortcut_layout);

    ButterKnife.bind(this);

    toolbar.setTitle(R.string.FSA_label);
    toolbar.setNavigationIcon(ContextCompat.getDrawable(this, R.drawable.ic_save_24dp));
    toolbar.setNavigationOnClickListener(v -> save());
    MenuColorizer.colorToolbar(this, toolbar);

    selectedFilter = defaultFilterProvider.getDefaultFilter();

    updateFilterAndTheme();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_FILTER) {
      if (resultCode == Activity.RESULT_OK) {
        if (selectedFilter != null && selectedFilter.listingTitle.equals(getShortcutName())) {
          shortcutName.setText(null);
        }
        selectedFilter = data.getParcelableExtra(FilterSelectionActivity.EXTRA_FILTER);
        updateFilterAndTheme();
      }
    } else if (requestCode == REQUEST_COLOR_PICKER) {
      if (resultCode == RESULT_OK) {
        selectedTheme = data.getIntExtra(ColorPickerActivity.EXTRA_THEME_INDEX, 0);
        updateTheme();
      }
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  @OnFocusChange(R.id.shortcut_list)
  void onListFocusChange(boolean focused) {
    if (focused) {
      shortcutList.clearFocus();
      showListPicker();
    }
  }

  @OnClick(R.id.shortcut_list)
  void showListPicker() {
    Intent intent = new Intent(this, FilterSelectionActivity.class);
    intent.putExtra(FilterSelectionActivity.EXTRA_FILTER, selectedFilter);
    intent.putExtra(FilterSelectionActivity.EXTRA_RETURN_FILTER, true);
    startActivityForResult(intent, REQUEST_FILTER);
  }

  @OnFocusChange(R.id.shortcut_color)
  void onColorFocusChange(boolean focused) {
    if (focused) {
      shortcutColor.clearFocus();
      showThemePicker();
    }
  }

  @OnClick(R.id.shortcut_color)
  void showThemePicker() {
    Intent intent = new Intent(this, ColorPickerActivity.class);
    intent.putExtra(ColorPickerActivity.EXTRA_PALETTE, ColorPickerActivity.ColorPalette.COLORS);
    intent.putExtra(ColorPickerActivity.EXTRA_SHOW_NONE, false);
    intent.putExtra(ColorPickerActivity.EXTRA_THEME_INDEX, selectedTheme);
    startActivityForResult(intent, REQUEST_COLOR_PICKER);
  }

  private void updateFilterAndTheme() {
    if (Strings.isNullOrEmpty(getShortcutName()) && selectedFilter != null) {
      shortcutName.setText(selectedFilter.listingTitle);
    }
    if (selectedFilter != null) {
      shortcutList.setText(selectedFilter.listingTitle);
    }
    updateTheme();
  }

  private void updateTheme() {
    ThemeColor color = themeCache.getThemeColor(getThemeIndex());
    shortcutColor.setText(color.getName());
    color.apply(toolbar);
    color.applyToStatusBar(this);
  }

  private int getThemeIndex() {
    if (selectedTheme >= 0) {
      return selectedTheme;
    }
    return selectedFilter == null || selectedFilter.tint == -1
        ? themeColor.getIndex()
        : selectedFilter.tint;
  }

  private String getShortcutName() {
    return shortcutName.getText().toString().trim();
  }

  private void save() {
    tracker.reportEvent(Tracking.Events.WIDGET_ADD, getString(R.string.FSA_label));

    String filterId = defaultFilterProvider.getFilterPreferenceValue(selectedFilter);
    Intent shortcutIntent = TaskIntents.getTaskListByIdIntent(this, filterId);

    Intent intent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
    intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getShortcutName());
    Drawable launcher = ContextCompat.getDrawable(this, ThemeColor.ICONS[getThemeIndex()]);
    if (launcher instanceof BitmapDrawable) {
      intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, ((BitmapDrawable) launcher).getBitmap());
    } else if (atLeastOreo() && launcher instanceof AdaptiveIconDrawable) {
      Bitmap bitmap =
          Bitmap.createBitmap(
              launcher.getIntrinsicWidth(), launcher.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      launcher.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
      launcher.draw(canvas);
      intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap);
    } else {
      throw new IllegalStateException("Launcher icon not found");
    }
    setResult(RESULT_OK, intent);
    finish();
  }

  @Override
  public void inject(ActivityComponent component) {
    component.inject(this);
  }
}
