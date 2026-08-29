package com.maoqb.chuanlemei;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.maoqb.chuanlemei.data.DemoDataSeeder;
import com.maoqb.chuanlemei.data.ImageStore;
import com.maoqb.chuanlemei.data.WardrobeDatabase;
import com.maoqb.chuanlemei.domain.Category;
import com.maoqb.chuanlemei.domain.ChartSeries;
import com.maoqb.chuanlemei.domain.DateTools;
import com.maoqb.chuanlemei.domain.Garment;
import com.maoqb.chuanlemei.domain.PeriodRange;
import com.maoqb.chuanlemei.domain.StatsCalculator;
import com.maoqb.chuanlemei.domain.WearRecord;
import com.maoqb.chuanlemei.ui.AppIconDrawable;
import com.maoqb.chuanlemei.ui.BarChartView;
import com.maoqb.chuanlemei.ui.DonutChartView;
import com.maoqb.chuanlemei.vision.GarmentRecognizer;
import com.maoqb.chuanlemei.vision.ImageSignature;
import com.maoqb.chuanlemei.vision.RecognitionCandidate;
import com.maoqb.chuanlemei.vision.RecognitionSlot;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

public class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_GARMENT_PHOTO = 1001;
    private static final int REQUEST_CAPTURE_WEAR = 1002;

    private static final String TAB_HOME = "home";
    private static final String TAB_WARDROBE = "wardrobe";
    private static final String TAB_OUTFITS = "outfits";
    private static final String TAB_STATS = "stats";

    private static final int BACKGROUND = 0xfff4f6f5;
    private static final int CARD = 0xffffffff;
    private static final int FIELD = 0xfff8faf9;
    private static final int INK = 0xff17201d;
    private static final int MUTED = 0xff6e7773;
    private static final int LINE = 0xffe1e7e4;
    private static final int GREEN = 0xff167d5a;
    private static final int GREEN_DARK = 0xff0f5d43;
    private static final int GREEN_TINT = 0xffe7f3ee;
    private static final int BLUE = 0xff4d7fa8;
    private static final int ORANGE = 0xffdf8241;
    private static final int ORANGE_TINT = 0xfffff2e9;
    private static final int DANGER = 0xffb64848;

    private WardrobeDatabase database;
    private LinearLayout headerContainer;
    private LinearLayout content;
    private LinearLayout bottomNavigation;
    private ScrollView scrollView;
    private Dialog garmentDialog;

    private List<Garment> garments = new ArrayList<>();
    private List<WearRecord> records = new ArrayList<>();
    private String currentTab = TAB_HOME;
    private String lastRenderedTab;

    private String draftName = "";
    private String draftCategory = Category.TOP;
    private String draftBrand = "";
    private String draftNote = "";
    private String draftColor = "#167D5A";
    private String draftPhotoPath;
    private ImageSignature draftSignature;
    private String wardrobeFilter = "all";

    private String recordDate = DateTools.today();
    private String recordPhotoPath;
    private String recordNote = "";
    private ArrayList<RecognitionSlot> recognitionSlots = new ArrayList<>();
    private Uri pendingCaptureUri;

    private String selectedGarmentId;
    private String periodPreset = DateTools.PERIOD_30_DAYS;
    private String customStart = DateTools.format(LocalDate.now().minusDays(29));
    private String customEnd = DateTools.today();
    private final HashMap<String, String> comboSelection = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        database = new WardrobeDatabase(this);
        DemoDataSeeder.seedIfEmpty(this, database);
        reloadData();
        buildShell();
        render();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            if (requestCode == REQUEST_CAPTURE_WEAR && pendingCaptureUri != null) {
                getContentResolver().delete(pendingCaptureUri, null, null);
                pendingCaptureUri = null;
            }
            showMessage("操作已取消");
            return;
        }

        try {
            if (requestCode == REQUEST_IMPORT_GARMENT_PHOTO && data != null && data.getData() != null) {
                handleImportedGarmentPhoto(data.getData());
            } else if (requestCode == REQUEST_CAPTURE_WEAR && pendingCaptureUri != null) {
                handleCapturedWearPhoto(pendingCaptureUri);
                pendingCaptureUri = null;
            }
        } catch (Exception error) {
            showMessage(error.getMessage() == null ? "图片处理失败" : error.getMessage());
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(CARD);
        window.setNavigationBarColor(CARD);
        window.setNavigationBarDividerColor(LINE);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void buildShell() {
        LinearLayout shell = vertical();
        shell.setBackgroundColor(BACKGROUND);
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        headerContainer = vertical();
        shell.addView(headerContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
        ));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        content = vertical();
        content.setPadding(dp(16), dp(12), dp(16), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        shell.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        bottomNavigation = horizontal();
        bottomNavigation.setGravity(Gravity.CENTER_VERTICAL);
        bottomNavigation.setBackgroundColor(CARD);
        bottomNavigation.setElevation(dp(10));
        shell.addView(bottomNavigation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
        ));
        setContentView(shell);
    }

    private void render() {
        headerContainer.removeAllViews();
        headerContainer.addView(buildAppBar(), matchWrap());
        bottomNavigation.removeAllViews();
        buildBottomNavigation();
        content.removeAllViews();

        if (TAB_WARDROBE.equals(currentTab)) {
            renderWardrobe();
        } else if (TAB_OUTFITS.equals(currentTab)) {
            renderOutfits();
        } else if (TAB_STATS.equals(currentTab)) {
            renderStats();
        } else {
            renderHome();
        }

        if (!currentTab.equals(lastRenderedTab)) {
            scrollView.post(() -> scrollView.scrollTo(0, 0));
            lastRenderedTab = currentTab;
        }
    }

    private View buildAppBar() {
        LinearLayout bar = horizontal();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(7), dp(16), dp(7));
        bar.setBackgroundColor(CARD);

        if (TAB_HOME.equals(currentTab)) {
            TextView mark = text("穿", 17, Color.WHITE, Typeface.BOLD);
            mark.setGravity(Gravity.CENTER);
            mark.setBackground(rounded(GREEN, GREEN, dp(8)));
            bar.addView(mark, new LinearLayout.LayoutParams(dp(36), dp(36)));
        }

        LinearLayout titles = vertical();
        titles.setPadding(TAB_HOME.equals(currentTab) ? dp(10) : dp(2), 0, 0, 0);
        TextView title = text(appBarTitle(), 20, INK, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        TextView subtitle = text(appBarSubtitle(), 11, MUTED, Typeface.NORMAL);
        subtitle.setIncludeFontPadding(false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(3);
        titles.addView(title);
        titles.addView(subtitle, subtitleParams);
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (TAB_WARDROBE.equals(currentTab)) {
            ImageView add = iconButton(AppIconDrawable.PLUS, "添加衣物", GREEN);
            add.setOnClickListener(view -> showGarmentDialog());
            bar.addView(add);
        }
        return bar;
    }

    private String appBarTitle() {
        if (TAB_WARDROBE.equals(currentTab)) {
            return "我的衣橱";
        }
        if (TAB_OUTFITS.equals(currentTab)) {
            return "搭配";
        }
        if (TAB_STATS.equals(currentTab)) {
            return "穿着统计";
        }
        return "穿了没";
    }

    private String appBarSubtitle() {
        if (TAB_WARDROBE.equals(currentTab)) {
            return activeGarments().size() + " 件在用衣物";
        }
        if (TAB_OUTFITS.equals(currentTab)) {
            return "上衣、裤子和鞋";
        }
        if (TAB_STATS.equals(currentTab)) {
            return "看见衣橱的真实使用率";
        }
        return DateTools.readable(DateTools.today());
    }

    private void buildBottomNavigation() {
        bottomNavigation.addView(navigationItem("首页", TAB_HOME, AppIconDrawable.HOME), weightedMatch());
        bottomNavigation.addView(navigationItem("衣橱", TAB_WARDROBE, AppIconDrawable.WARDROBE), weightedMatch());
        bottomNavigation.addView(navigationItem("搭配", TAB_OUTFITS, AppIconDrawable.OUTFIT), weightedMatch());
        bottomNavigation.addView(navigationItem("统计", TAB_STATS, AppIconDrawable.STATS), weightedMatch());
    }

    private View navigationItem(String label, String tab, String icon) {
        boolean selected = tab.equals(currentTab);
        int color = selected ? GREEN : 0xff8a938f;
        LinearLayout item = vertical();
        item.setGravity(Gravity.CENTER);
        item.setMinimumHeight(dp(64));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(label);

        ImageView iconView = new ImageView(this);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        iconView.setImageDrawable(icon(icon, color, 22));
        item.addView(iconView, new LinearLayout.LayoutParams(dp(23), dp(23)));
        TextView title = text(label, 11, color, selected ? Typeface.BOLD : Typeface.NORMAL);
        title.setIncludeFontPadding(false);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(17)
        );
        titleParams.topMargin = dp(2);
        item.addView(title, titleParams);
        item.setOnClickListener(view -> {
            if (!tab.equals(currentTab)) {
                currentTab = tab;
                render();
            }
        });
        return item;
    }

    private void renderHome() {
        content.addView(homeHero(), matchWrap());

        if (activeGarments().isEmpty()) {
            TextView setup = actionBanner("衣橱还是空的，先添加衣物才能进行自动识别", "去添加");
            setup.setOnClickListener(view -> {
                currentTab = TAB_WARDROBE;
                render();
                showGarmentDialog();
            });
            addWithTop(content, setup, dp(10));
        }

        if (recordPhotoPath != null) {
            addWithTop(content, buildRecognitionPanel(), dp(18));
        }

        StatsCalculator.DashboardStats thirtyDays = StatsCalculator.build(
                garments,
                records,
                DateTools.rangeForPreset(DateTools.PERIOD_30_DAYS, customStart, customEnd)
        );
        LinearLayout metrics = horizontal();
        metrics.addView(homeMetric(String.valueOf(activeGarments().size()), "衣橱", "件"), weightedWrap());
        metrics.addView(homeMetric(String.valueOf(thirtyDays.totalRecords), "近30天", "次"), weightedWrapWithMargins(dp(8)));
        metrics.addView(homeMetric(String.valueOf(records.size()), "累计记录", "次"), weightedWrap());
        addWithTop(content, metrics, dp(18));

        addWithTop(content, sectionHeader("最近穿着", records.isEmpty() ? "还没有记录" : "最近 " + Math.min(5, records.size()) + " 条"), dp(24));
        if (records.isEmpty()) {
            addWithTop(content, emptyState(AppIconDrawable.CALENDAR, "从今天开始记录", "每次穿着必须拍照，记录会显示在这里"), dp(10));
        } else {
            int limit = Math.min(5, records.size());
            for (int index = 0; index < limit; index++) {
                addWithTop(content, recordRow(records.get(index)), dp(8));
            }
        }
    }

    private View homeHero() {
        LinearLayout hero = vertical();
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(rounded(GREEN_DARK, GREEN_DARK, dp(8)));
        hero.setElevation(dp(2));

        TextView date = text("今天  " + DateTools.readable(DateTools.today()), 12, 0xffd8eee5, Typeface.BOLD);
        date.setCompoundDrawablePadding(dp(6));
        date.setCompoundDrawablesWithIntrinsicBounds(icon(AppIconDrawable.CHECK, 0xffd8eee5, 15), null, null, null);
        hero.addView(date);
        addWithTop(hero, text(recordPhotoPath == null ? "今天穿了什么？" : "照片已拍好", 23, Color.WHITE, Typeface.BOLD), dp(10));
        addWithTop(hero, text(
                recordPhotoPath == null ? "拍照识别今天的上衣、裤子和鞋" : "确认识别结果后保存本次穿着",
                13,
                0xffd8eee5,
                Typeface.NORMAL
        ), dp(4));

        ActionButton capture = actionButton(
                recordPhotoPath == null ? "拍照并自动识别" : "重新拍照",
                false,
                AppIconDrawable.CAMERA
        );
        capture.setTextColor(GREEN_DARK);
        capture.setBackground(rounded(Color.WHITE, Color.WHITE, dp(8)));
        capture.setOnClickListener(view -> launchWearCamera());
        addWithTop(hero, capture, dp(16));
        return hero;
    }

    private View buildRecognitionPanel() {
        LinearLayout panel = vertical();
        panel.addView(sectionHeader("确认识别结果", "日期和衣物确认无误后保存"));

        ImageView photo = image(recordPhotoPath, 196);
        addWithTop(panel, photo, dp(10));

        boolean today = DateTools.isToday(recordDate);
        TextView verified = text(
                today ? "已校验为本地当天 · " + recordDate : "日期已变化，请重新拍照",
                12,
                today ? GREEN : DANGER,
                Typeface.BOLD
        );
        verified.setGravity(Gravity.CENTER_VERTICAL);
        verified.setPadding(dp(12), dp(9), dp(12), dp(9));
        verified.setCompoundDrawablePadding(dp(6));
        verified.setCompoundDrawablesWithIntrinsicBounds(icon(today ? AppIconDrawable.CHECK : AppIconDrawable.CALENDAR,
                today ? GREEN : DANGER, 16), null, null, null);
        verified.setBackground(rounded(today ? GREEN_TINT : ORANGE_TINT, today ? GREEN_TINT : ORANGE_TINT, dp(8)));
        addWithTop(panel, verified, dp(8));

        if (recognitionSlots.isEmpty()) {
            addWithTop(panel, emptyState(AppIconDrawable.WARDROBE, "没有识别候选", "先在衣橱中导入衣物照片"), dp(10));
        } else {
            for (RecognitionSlot slot : recognitionSlots) {
                addWithTop(panel, recognitionSlotView(slot), dp(8));
            }
        }

        addWithTop(panel, fieldLabel("备注"), dp(14));
        EditText note = editText("例如：通勤、运动", recordNote, value -> recordNote = value);
        note.setMinLines(2);
        note.setImeOptions(EditorInfo.IME_ACTION_DONE);
        addWithTop(panel, note, dp(6));

        ActionButton save = actionButton("保存今日穿着", true, AppIconDrawable.CHECK);
        save.setEnabled(canSaveWearRecord());
        save.setAlpha(save.isEnabled() ? 1f : 0.42f);
        save.setOnClickListener(view -> saveWearRecord());
        addWithTop(panel, save, dp(12));
        return panel;
    }

    private View recognitionSlotView(RecognitionSlot slot) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));
        row.setBackground(rounded(CARD, LINE, dp(8)));
        row.setElevation(dp(1));

        TextView category = text(Category.label(slot.category).substring(0, 1), 16, Color.WHITE, Typeface.BOLD);
        category.setGravity(Gravity.CENTER);
        category.setBackground(circle(Category.accentColor(slot.category), Category.accentColor(slot.category)));
        row.addView(category, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout labels = vertical();
        labels.setPadding(dp(10), 0, dp(8), 0);
        labels.addView(text(Category.label(slot.category), 14, INK, Typeface.BOLD));
        labels.addView(text(slot.selectedGarmentId == null ? "请选择匹配衣物" : "识别可信度 " + Math.round(slot.confidence * 100) + "%",
                11, MUTED, Typeface.NORMAL));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Spinner spinner = garmentSpinner(slot.category, slot.selectedGarmentId, selected -> {
            if (same(selected, slot.selectedGarmentId)) {
                return;
            }
            slot.selectedGarmentId = selected;
            slot.confidence = selected == null ? 0 : confidenceFor(slot, selected);
            render();
        });
        row.addView(spinner, new LinearLayout.LayoutParams(dp(132), dp(46)));
        return row;
    }

    private View homeMetric(String value, String label, String unit) {
        LinearLayout box = vertical();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6), dp(12), dp(6), dp(12));
        box.setBackground(rounded(CARD, LINE, dp(8)));
        box.setElevation(dp(1));
        LinearLayout number = horizontal();
        number.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        number.addView(text(value, 23, INK, Typeface.BOLD));
        TextView unitView = text(unit, 10, MUTED, Typeface.NORMAL);
        unitView.setPadding(dp(2), 0, 0, dp(3));
        number.addView(unitView);
        box.addView(number);
        TextView labelView = text(label, 11, MUTED, Typeface.NORMAL);
        labelView.setGravity(Gravity.CENTER);
        labelView.setIncludeFontPadding(false);
        box.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(18)
        ));
        return box;
    }

    private View recordRow(WearRecord record) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(6), dp(8));
        row.setBackground(rounded(CARD, LINE, dp(8)));
        row.setElevation(dp(1));
        ImageView photo = image(record.photoPath, 60);
        row.addView(photo, new LinearLayout.LayoutParams(dp(60), dp(60)));

        LinearLayout info = vertical();
        info.setPadding(dp(10), 0, dp(4), 0);
        info.addView(text(DateTools.readable(record.wornAt), 14, INK, Typeface.BOLD));
        TextView names = text(recordGarmentNames(record), 12, MUTED, Typeface.NORMAL);
        names.setMaxLines(2);
        info.addView(names);
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageView delete = iconButton(AppIconDrawable.TRASH, "删除记录", MUTED);
        delete.setOnClickListener(view -> {
            database.deleteWearRecord(record.id);
            reloadData();
            showMessage("记录已删除");
            render();
        });
        row.addView(delete);
        return row;
    }

    private void renderWardrobe() {
        HorizontalScrollView filters = new HorizontalScrollView(this);
        filters.setHorizontalScrollBarEnabled(false);
        filters.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout filterRow = horizontal();
        filterRow.addView(filterChip("全部", "all"));
        filterRow.addView(filterChip("上衣", Category.TOP));
        filterRow.addView(filterChip("裤子", Category.BOTTOM));
        filterRow.addView(filterChip("鞋", Category.SHOES));
        filters.addView(filterRow);
        content.addView(filters);

        List<Garment> visible = visibleGarments();
        if (visible.isEmpty()) {
            View empty = emptyState(AppIconDrawable.WARDROBE, "这个分类还没有衣物", "点击添加，导入一张清晰的衣物照片");
            empty.setOnClickListener(view -> showGarmentDialog());
            addWithTop(content, empty, dp(14));
            return;
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int columns = getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        int gap = dp(10);
        int tileWidth = (screenWidth - dp(32) - gap * (columns - 1)) / columns;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        for (int index = 0; index < visible.size(); index++) {
            Garment garment = visible.get(index);
            View tile = garmentTile(garment, tileWidth);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = tileWidth;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.setMargins(index % columns == 0 ? 0 : gap, dp(10), 0, 0);
            grid.addView(tile, params);
        }
        addWithTop(content, grid, dp(4));
    }

    private View filterChip(String label, String filter) {
        boolean selected = filter.equals(wardrobeFilter);
        TextView chip = text(label, 13, selected ? Color.WHITE : MUTED, selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setGravity(Gravity.CENTER);
        chip.setMinWidth(dp(72));
        chip.setPadding(dp(16), dp(9), dp(16), dp(9));
        chip.setBackground(rounded(selected ? GREEN : CARD, selected ? GREEN : LINE, dp(18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.rightMargin = dp(8);
        chip.setLayoutParams(params);
        chip.setOnClickListener(view -> {
            wardrobeFilter = filter;
            render();
        });
        return chip;
    }

    private View garmentTile(Garment garment, int width) {
        LinearLayout tile = vertical();
        tile.setBackground(rounded(CARD, LINE, dp(8)));
        tile.setClipToOutline(true);
        tile.setElevation(dp(1));
        tile.setOnClickListener(view -> showGarmentDetail(garment));

        int photoHeight = Math.min(dp(170), Math.round(width * 0.9f));
        tile.addView(image(garment.photoPath, 150), new LinearLayout.LayoutParams(width, photoHeight));

        LinearLayout info = vertical();
        info.setPadding(dp(10), dp(9), dp(10), dp(11));
        TextView name = text(garment.name, 14, INK, Typeface.BOLD);
        name.setSingleLine(true);
        info.addView(name);

        LinearLayout meta = horizontal();
        meta.setGravity(Gravity.CENTER_VERTICAL);
        View swatch = new View(this);
        swatch.setBackground(circle(garment.color, 0x22000000));
        meta.addView(swatch, new LinearLayout.LayoutParams(dp(10), dp(10)));
        TextView category = text(Category.label(garment.category), 11, MUTED, Typeface.NORMAL);
        category.setPadding(dp(5), 0, 0, 0);
        meta.addView(category, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        meta.addView(text(StatsCalculator.countForGarment(records, garment.id) + " 次", 12, GREEN, Typeface.BOLD));
        addWithTop(info, meta, dp(5));
        tile.addView(info);
        return tile;
    }

    private void showGarmentDialog() {
        if (garmentDialog != null && garmentDialog.isShowing()) {
            garmentDialog.dismiss();
        }
        Dialog dialog = new Dialog(this);
        garmentDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(16), dp(18), dp(22));
        form.setBackground(rounded(CARD, CARD, dp(8)));
        scroll.addView(form, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout titleRow = horizontal();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleCopy = vertical();
        titleCopy.addView(text("添加衣物", 20, INK, Typeface.BOLD));
        titleCopy.addView(text("正面照片越清晰，自动识别越准确", 11, MUTED, Typeface.NORMAL));
        titleRow.addView(titleCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView close = iconButton(AppIconDrawable.CLOSE, "关闭", MUTED);
        close.setOnClickListener(view -> dialog.dismiss());
        titleRow.addView(close);
        form.addView(titleRow);

        View photo;
        if (draftPhotoPath == null) {
            photo = emptyState(AppIconDrawable.CAMERA, "选择衣物照片", "建议单件、正面、光线均匀");
        } else {
            photo = image(draftPhotoPath, 176);
        }
        photo.setOnClickListener(view -> launchImageImport());
        addWithTop(form, photo, dp(16));

        ActionButton choose = actionButton(draftPhotoPath == null ? "从相册选择" : "更换照片", false, AppIconDrawable.CAMERA);
        choose.setOnClickListener(view -> launchImageImport());
        addWithTop(form, choose, dp(8));

        addWithTop(form, fieldLabel("名称"), dp(16));
        addWithTop(form, editText("例如：白色牛津纺衬衫", draftName, value -> draftName = value), dp(6));

        addWithTop(form, fieldLabel("分类"), dp(14));
        LinearLayout categories = horizontal();
        categories.addView(categoryChoice("上衣", Category.TOP), weightedWrap());
        categories.addView(categoryChoice("裤子", Category.BOTTOM), weightedWrapWithMargins(dp(8)));
        categories.addView(categoryChoice("鞋", Category.SHOES), weightedWrap());
        addWithTop(form, categories, dp(6));

        addWithTop(form, fieldLabel("主色"), dp(14));
        LinearLayout swatches = horizontal();
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        String[] colors = {"#171B1A", "#F4F4F2", "#8A918E", "#167D5A", "#4D7FA8", "#D97850", "#8A654A"};
        for (String color : colors) {
            swatches.addView(colorSwatch(color));
        }
        addWithTop(form, swatches, dp(8));

        addWithTop(form, fieldLabel("品牌"), dp(14));
        addWithTop(form, editText("选填", draftBrand, value -> draftBrand = value), dp(6));
        addWithTop(form, fieldLabel("备注"), dp(14));
        EditText note = editText("版型、季节或其他信息", draftNote, value -> draftNote = value);
        note.setMinLines(2);
        addWithTop(form, note, dp(6));

        ActionButton save = actionButton("加入衣橱", true, AppIconDrawable.PLUS);
        save.setOnClickListener(view -> saveGarment());
        addWithTop(form, save, dp(18));

        dialog.setContentView(scroll);
        dialog.setOnDismissListener(dismissed -> {
            if (garmentDialog == dialog) {
                garmentDialog = null;
            }
        });
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.46f;
            window.setAttributes(params);
        }
        dialog.show();
        if (window != null) {
            int width = getResources().getDisplayMetrics().widthPixels - dp(24);
            int height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.88f);
            window.setLayout(width, height);
            window.setGravity(Gravity.CENTER);
        }
    }

    private View categoryChoice(String label, String category) {
        boolean selected = category.equals(draftCategory);
        TextView choice = text(label, 13, selected ? Color.WHITE : MUTED, selected ? Typeface.BOLD : Typeface.NORMAL);
        choice.setGravity(Gravity.CENTER);
        choice.setBackground(rounded(selected ? GREEN : FIELD, selected ? GREEN : LINE, dp(8)));
        choice.setOnClickListener(view -> {
            draftCategory = category;
            if (draftColor == null || draftColor.isEmpty()) {
                draftColor = String.format(Locale.US, "#%06X", Category.accentColor(category) & 0xffffff);
            }
            showGarmentDialog();
        });
        return choice;
    }

    private View colorSwatch(String hex) {
        boolean selected = hex.equalsIgnoreCase(draftColor);
        TextView swatch = text("", 1, Color.TRANSPARENT, Typeface.NORMAL);
        int value = parseColor(hex, GREEN);
        swatch.setBackground(circle(value, selected ? GREEN : LINE));
        swatch.setContentDescription("颜色 " + hex);
        if (selected) {
            swatch.setCompoundDrawablesWithIntrinsicBounds(icon(AppIconDrawable.CHECK,
                    value == 0xfff4f4f2 ? INK : Color.WHITE, 16), null, null, null);
            swatch.setGravity(Gravity.CENTER);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(34), dp(34));
        params.rightMargin = dp(9);
        swatch.setLayoutParams(params);
        swatch.setOnClickListener(view -> {
            draftColor = hex;
            showGarmentDialog();
        });
        return swatch;
    }

    private void showGarmentDetail(Garment garment) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout detail = vertical();
        detail.setPadding(dp(18), dp(16), dp(18), dp(22));
        detail.setBackground(rounded(CARD, CARD, dp(8)));
        scroll.addView(detail);

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView category = text(Category.label(garment.category), 12, GREEN, Typeface.BOLD);
        category.setPadding(dp(10), dp(5), dp(10), dp(5));
        category.setBackground(rounded(GREEN_TINT, GREEN_TINT, dp(14)));
        top.addView(category);
        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        ImageView close = iconButton(AppIconDrawable.CLOSE, "关闭", MUTED);
        close.setOnClickListener(view -> dialog.dismiss());
        top.addView(close);
        detail.addView(top);

        addWithTop(detail, image(garment.photoPath, 220), dp(10));
        addWithTop(detail, text(garment.name, 22, INK, Typeface.BOLD), dp(14));
        String brand = garment.brand == null || garment.brand.trim().isEmpty() ? "未填写品牌" : garment.brand;
        detail.addView(text(brand, 12, MUTED, Typeface.NORMAL));

        List<WearRecord> garmentRecords = StatsCalculator.recordsForGarment(records, garment.id);
        LinearLayout metrics = horizontal();
        metrics.addView(detailMetric(String.valueOf(garmentRecords.size()), "累计穿着"), weightedWrap());
        String latest = garmentRecords.isEmpty() ? "暂无" : DateTools.shortDate(garmentRecords.get(0).wornAt);
        metrics.addView(detailMetric(latest, "最近穿着"), weightedWrapWithMargins(dp(8)));
        addWithTop(detail, metrics, dp(14));

        addWithTop(detail, sectionHeader("穿着历史", garmentRecords.isEmpty() ? "暂无记录" : "最近记录"), dp(20));
        if (garmentRecords.isEmpty()) {
            addWithTop(detail, text("还没有穿过这件衣物", 13, MUTED, Typeface.NORMAL), dp(8));
        } else {
            int limit = Math.min(6, garmentRecords.size());
            for (int index = 0; index < limit; index++) {
                WearRecord record = garmentRecords.get(index);
                TextView history = text(DateTools.readable(record.wornAt) + "  ·  " + recordGarmentNames(record), 12, INK, Typeface.NORMAL);
                history.setPadding(0, dp(8), 0, dp(8));
                detail.addView(history);
            }
        }

        ActionButton archive = actionButton("停用这件衣物", false, AppIconDrawable.TRASH);
        archive.setTextColor(DANGER);
        archive.setOnClickListener(view -> {
            database.saveGarment(garment.archived(DateTools.nowIsoSecond()));
            dialog.dismiss();
            reloadData();
            showMessage(garment.name + " 已停用");
            render();
        });
        addWithTop(detail, archive, dp(18));

        dialog.setContentView(scroll);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(getResources().getDisplayMetrics().widthPixels - dp(24),
                    Math.round(getResources().getDisplayMetrics().heightPixels * 0.84f));
            window.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.46f;
            window.setAttributes(params);
        }
    }

    private View detailMetric(String value, String label) {
        LinearLayout metric = vertical();
        metric.setPadding(dp(12), dp(11), dp(12), dp(11));
        metric.setBackground(rounded(FIELD, LINE, dp(8)));
        metric.addView(text(value, 20, INK, Typeface.BOLD));
        metric.addView(text(label, 11, MUTED, Typeface.NORMAL));
        return metric;
    }

    private void renderOutfits() {
        content.addView(sectionHeader("今日搭配预览", "从衣橱中选择上衣、裤子和鞋"));
        if (activeGarments().isEmpty()) {
            View empty = emptyState(AppIconDrawable.OUTFIT, "还不能创建搭配", "先添加衣物，再回来组合完整穿搭");
            empty.setOnClickListener(view -> {
                currentTab = TAB_WARDROBE;
                render();
                showGarmentDialog();
            });
            addWithTop(content, empty, dp(12));
            return;
        }

        for (String category : Category.ORDER) {
            addWithTop(content, outfitPiece(category), dp(10));
        }

        TextView summary = text(describeCombo(), 13, GREEN_DARK, Typeface.BOLD);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(dp(12), dp(11), dp(12), dp(11));
        summary.setCompoundDrawablePadding(dp(7));
        summary.setCompoundDrawablesWithIntrinsicBounds(icon(AppIconDrawable.CHECK, GREEN, 16), null, null, null);
        summary.setBackground(rounded(GREEN_TINT, GREEN_TINT, dp(8)));
        addWithTop(content, summary, dp(10));

        StatsCalculator.DashboardStats stats = currentStats();
        addWithTop(content, sectionHeader("常穿组合", stats.outfitStats.isEmpty() ? "有记录后自动生成" : "当前统计周期"), dp(24));
        if (stats.outfitStats.isEmpty()) {
            addWithTop(content, emptyState(AppIconDrawable.CALENDAR, "暂无组合记录", "完成一次拍照记录后，这里会出现常穿搭配"), dp(10));
        } else {
            int limit = Math.min(5, stats.outfitStats.size());
            for (int index = 0; index < limit; index++) {
                addWithTop(content, outfitStatCard(stats.outfitStats.get(index)), dp(8));
            }
        }
    }

    private View outfitPiece(String category) {
        String selectedId = effectiveComboId(category);
        Garment garment = garmentById(selectedId);
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(10), dp(8));
        row.setBackground(rounded(CARD, LINE, dp(8)));
        row.setElevation(dp(1));

        if (garment == null) {
            row.addView(photoPlaceholder(AppIconDrawable.WARDROBE, 94), new LinearLayout.LayoutParams(dp(94), dp(94)));
        } else {
            row.addView(image(garment.photoPath, 94), new LinearLayout.LayoutParams(dp(94), dp(94)));
        }

        LinearLayout controls = vertical();
        controls.setPadding(dp(12), 0, 0, 0);
        controls.addView(text(Category.label(category), 12, Category.accentColor(category), Typeface.BOLD));
        controls.addView(text(garment == null ? "未选择" : garment.name, 16, INK, Typeface.BOLD));
        Spinner spinner = garmentSpinner(category, selectedId, selected -> {
            if (same(selected, comboSelection.get(category)) || (comboSelection.get(category) == null && same(selected, effectiveComboId(category)))) {
                return;
            }
            if (selected == null) {
                comboSelection.remove(category);
            } else {
                comboSelection.put(category, selected);
            }
            render();
        });
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        spinnerParams.topMargin = dp(7);
        controls.addView(spinner, spinnerParams);
        row.addView(controls, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View outfitStatCard(StatsCalculator.OutfitStat stat) {
        LinearLayout card = horizontal();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8), dp(8), dp(10), dp(8));
        card.setBackground(rounded(CARD, LINE, dp(8)));
        card.setElevation(dp(1));

        LinearLayout photos = horizontal();
        addOutfitImage(photos, stat.topId);
        addOutfitImage(photos, stat.bottomId);
        addOutfitImage(photos, stat.shoesId);
        card.addView(photos, new LinearLayout.LayoutParams(dp(132), dp(50)));

        LinearLayout info = vertical();
        info.setPadding(dp(10), 0, dp(4), 0);
        TextView names = text(outfitNames(stat.topId, stat.bottomId, stat.shoesId), 12, INK, Typeface.BOLD);
        names.setMaxLines(2);
        info.addView(names);
        info.addView(text("最近 " + (stat.lastWornAt == null ? "-" : DateTools.shortDate(stat.lastWornAt)), 11, MUTED, Typeface.NORMAL));
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(text(stat.count + " 次", 15, GREEN, Typeface.BOLD));
        return card;
    }

    private void renderStats() {
        HorizontalScrollView periodScroll = new HorizontalScrollView(this);
        periodScroll.setHorizontalScrollBarEnabled(false);
        periodScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout periods = horizontal();
        periods.addView(periodChip("7天", DateTools.PERIOD_7_DAYS));
        periods.addView(periodChip("30天", DateTools.PERIOD_30_DAYS));
        periods.addView(periodChip("90天", DateTools.PERIOD_90_DAYS));
        periods.addView(periodChip("今年", DateTools.PERIOD_YEAR));
        periods.addView(periodChip("全部", DateTools.PERIOD_ALL));
        periods.addView(periodChip("自定义", DateTools.PERIOD_CUSTOM));
        periodScroll.addView(periods);
        content.addView(periodScroll);

        if (DateTools.PERIOD_CUSTOM.equals(periodPreset)) {
            LinearLayout dates = horizontal();
            TextView start = dateControl("开始", customStart, value -> customStart = value);
            TextView end = dateControl("结束", customEnd, value -> customEnd = value);
            dates.addView(start, weightedWrap());
            dates.addView(end, weightedWrapWithMargins(dp(8)));
            addWithTop(content, dates, dp(10));
        }

        PeriodRange range = currentRange();
        StatsCalculator.DashboardStats stats = StatsCalculator.build(garments, records, range);
        TextView rangeText = text(range.start + " 至 " + range.end, 11, MUTED, Typeface.NORMAL);
        addWithTop(content, rangeText, dp(10));

        LinearLayout metrics = horizontal();
        metrics.addView(statMetric(String.valueOf(stats.totalRecords), "穿着记录", GREEN), weightedWrap());
        metrics.addView(statMetric(String.valueOf(stats.totalGarmentWears), "衣物计次", ORANGE), weightedWrapWithMargins(dp(8)));
        metrics.addView(statMetric(String.valueOf(stats.activeGarments), "活跃衣物", BLUE), weightedWrap());
        addWithTop(content, metrics, dp(14));

        LinearLayout trendCard = chartCard("穿着趋势", "按日期汇总记录次数");
        BarChartView chart = new BarChartView(this, GREEN, LINE, MUTED);
        chart.setPoints(ChartSeries.fromDailyCounts(stats.dailyCounts, 12));
        addWithTop(trendCard, chart, dp(8), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(220)
        ));
        addWithTop(content, trendCard, dp(14));

        LinearLayout categoryCard = chartCard("分类占比", "上衣、裤子和鞋的穿着计次");
        LinearLayout distribution = horizontal();
        distribution.setGravity(Gravity.CENTER_VERTICAL);
        DonutChartView donut = new DonutChartView(this);
        int[] values = {
                count(stats, Category.TOP),
                count(stats, Category.BOTTOM),
                count(stats, Category.SHOES)
        };
        int[] colors = {Category.accentColor(Category.TOP), Category.accentColor(Category.BOTTOM), Category.accentColor(Category.SHOES)};
        donut.setData(values, colors, INK, MUTED, LINE);
        distribution.addView(donut, new LinearLayout.LayoutParams(dp(168), dp(168)));
        LinearLayout legend = vertical();
        legend.setPadding(dp(12), 0, 0, 0);
        legend.addView(legendRow("上衣", values[0], colors[0]));
        addWithTop(legend, legendRow("裤子", values[1], colors[1]), dp(12));
        addWithTop(legend, legendRow("鞋", values[2], colors[2]), dp(12));
        distribution.addView(legend, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        addWithTop(categoryCard, distribution, dp(8));
        addWithTop(content, categoryCard, dp(14));

        addWithTop(content, sectionHeader("衣物排行", "按本周期穿着次数排序"), dp(24));
        if (stats.garmentStats.isEmpty()) {
            addWithTop(content, emptyState(AppIconDrawable.STATS, "暂无排行数据", "添加衣物并完成拍照记录后即可查看"), dp(10));
        } else {
            int max = 1;
            for (StatsCalculator.GarmentStat stat : stats.garmentStats) {
                max = Math.max(max, stat.rangeCount);
            }
            int limit = Math.min(8, stats.garmentStats.size());
            for (int index = 0; index < limit; index++) {
                addWithTop(content, rankingRow(index + 1, stats.garmentStats.get(index), max), dp(8));
            }
        }
    }

    private View periodChip(String label, String preset) {
        boolean selected = preset.equals(periodPreset);
        TextView chip = text(label, 13, selected ? Color.WHITE : MUTED, selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(15), dp(9), dp(15), dp(9));
        chip.setBackground(rounded(selected ? GREEN : CARD, selected ? GREEN : LINE, dp(18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.rightMargin = dp(8);
        chip.setLayoutParams(params);
        chip.setOnClickListener(view -> {
            periodPreset = preset;
            render();
        });
        return chip;
    }

    private TextView dateControl(String label, String date, Consumer<String> consumer) {
        TextView control = text(label + "\n" + date, 12, INK, Typeface.BOLD);
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setLineSpacing(dp(2), 1f);
        control.setPadding(dp(12), dp(8), dp(12), dp(8));
        control.setCompoundDrawablePadding(dp(8));
        control.setCompoundDrawablesWithIntrinsicBounds(icon(AppIconDrawable.CALENDAR, GREEN, 18), null, null, null);
        control.setBackground(rounded(CARD, LINE, dp(8)));
        control.setOnClickListener(view -> showDatePicker(date, value -> {
            consumer.accept(value);
            render();
        }));
        return control;
    }

    private View statMetric(String value, String label, int accent) {
        LinearLayout metric = vertical();
        metric.setPadding(dp(10), dp(12), dp(8), dp(12));
        metric.setBackground(rounded(CARD, LINE, dp(8)));
        metric.setElevation(dp(1));
        metric.addView(text(value, 22, accent, Typeface.BOLD));
        metric.addView(text(label, 10, MUTED, Typeface.NORMAL));
        return metric;
    }

    private LinearLayout chartCard(String title, String caption) {
        LinearLayout card = vertical();
        card.setPadding(dp(14), dp(14), dp(14), dp(12));
        card.setBackground(rounded(CARD, LINE, dp(8)));
        card.setElevation(dp(1));
        card.addView(text(title, 16, INK, Typeface.BOLD));
        card.addView(text(caption, 11, MUTED, Typeface.NORMAL));
        return card;
    }

    private View legendRow(String label, int value, int color) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(circle(color, color));
        row.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        TextView name = text(label, 12, MUTED, Typeface.NORMAL);
        name.setPadding(dp(8), 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text(value + " 次", 13, INK, Typeface.BOLD));
        return row;
    }

    private View rankingRow(int rank, StatsCalculator.GarmentStat stat, int max) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(9), dp(10), dp(9));
        row.setBackground(rounded(CARD, LINE, dp(8)));
        row.setElevation(dp(1));
        row.setOnClickListener(view -> showGarmentDetail(stat.garment));

        TextView rankView = text(String.valueOf(rank), 12, rank <= 3 ? Color.WHITE : MUTED, Typeface.BOLD);
        rankView.setGravity(Gravity.CENTER);
        rankView.setBackground(circle(rank <= 3 ? GREEN : FIELD, rank <= 3 ? GREEN : LINE));
        row.addView(rankView, new LinearLayout.LayoutParams(dp(28), dp(28)));

        ImageView photo = image(stat.garment.photoPath, 48);
        LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        photoParams.leftMargin = dp(8);
        row.addView(photo, photoParams);

        LinearLayout info = vertical();
        info.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout title = horizontal();
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(text(stat.garment.name, 13, INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        title.addView(text(stat.rangeCount + " 次", 13, GREEN, Typeface.BOLD));
        info.addView(title);
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(max);
        progress.setProgress(stat.rangeCount);
        progress.setProgressTintList(ColorStateList.valueOf(Category.accentColor(stat.garment.category)));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(5)
        );
        progressParams.topMargin = dp(7);
        info.addView(progress, progressParams);
        info.addView(text("累计 " + stat.totalCount + " 次", 10, MUTED, Typeface.NORMAL));
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private int count(StatsCalculator.DashboardStats stats, String category) {
        Integer value = stats.categoryCounts.get(category);
        return value == null ? 0 : value;
    }

    private void launchImageImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_GARMENT_PHOTO);
        } catch (ActivityNotFoundException error) {
            showMessage("没有可用的图片选择器");
        }
    }

    private void launchWearCamera() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "chuanlemei_wear_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ChuanLeMei");
            pendingCaptureUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (pendingCaptureUri == null) {
                showMessage("无法创建相机输出文件");
                return;
            }
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCaptureUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CAPTURE_WEAR);
        } catch (ActivityNotFoundException error) {
            showMessage("没有可用的相机应用");
        } catch (Exception error) {
            showMessage(error.getMessage() == null ? "打开相机失败" : error.getMessage());
        }
    }

    private void handleImportedGarmentPhoto(Uri uri) throws IOException {
        Bitmap bitmap = ImageStore.loadBitmap(this, uri, 1280);
        if (bitmap == null) {
            throw new IOException("无法读取衣物图片");
        }
        draftPhotoPath = ImageStore.saveBitmap(this, bitmap, "garment");
        draftSignature = ImageSignature.fromBitmap(bitmap);
        bitmap.recycle();
        showMessage("衣物照片已导入");
        showGarmentDialog();
    }

    private void handleCapturedWearPhoto(Uri uri) throws IOException {
        Bitmap bitmap = ImageStore.loadBitmap(this, uri, 1280);
        if (bitmap == null) {
            throw new IOException("无法读取相机照片");
        }
        recordPhotoPath = ImageStore.saveBitmap(this, bitmap, "wear");
        ImageSignature signature = ImageSignature.fromBitmap(bitmap);
        bitmap.recycle();
        recognitionSlots = new ArrayList<>(GarmentRecognizer.recognize(signature, activeGarments()));
        recordDate = DateTools.today();
        currentTab = TAB_HOME;
        showMessage("识别完成，请确认衣物");
        render();
    }

    private void saveGarment() {
        String name = draftName.trim();
        if (name.isEmpty()) {
            showMessage("请填写衣物名称");
            return;
        }
        if (draftPhotoPath == null || draftSignature == null) {
            showMessage("请先选择衣物照片");
            return;
        }
        if (!Category.isValid(draftCategory)) {
            draftCategory = Category.TOP;
        }

        String now = DateTools.nowIsoSecond();
        Garment garment = new Garment(
                id("garment"),
                name,
                draftCategory,
                parseColor(draftColor, Category.accentColor(draftCategory)),
                draftBrand.trim(),
                draftNote.trim(),
                draftPhotoPath,
                draftSignature,
                now,
                now,
                null
        );
        database.saveGarment(garment);
        draftName = "";
        draftBrand = "";
        draftNote = "";
        draftPhotoPath = null;
        draftSignature = null;
        selectedGarmentId = garment.id;
        if (garmentDialog != null && garmentDialog.isShowing()) {
            garmentDialog.dismiss();
        }
        reloadData();
        showMessage(garment.name + " 已加入衣橱");
        render();
    }

    private void saveWearRecord() {
        if (!canSaveWearRecord()) {
            showMessage("需要当天相机照片和至少一件已确认衣物");
            return;
        }
        WearRecord record = new WearRecord(
                id("wear"),
                recordDate,
                DateTools.nowIsoSecond(),
                recordPhotoPath,
                selectedFor(Category.TOP),
                selectedFor(Category.BOTTOM),
                selectedFor(Category.SHOES),
                GarmentRecognizer.summarize(recognitionSlots),
                recordNote.trim()
        );
        database.saveWearRecord(record);
        recordPhotoPath = null;
        recordNote = "";
        recognitionSlots.clear();
        reloadData();
        showMessage("今日穿着已记录");
        render();
    }

    private boolean canSaveWearRecord() {
        return recordPhotoPath != null
                && DateTools.isToday(recordDate)
                && (selectedFor(Category.TOP) != null || selectedFor(Category.BOTTOM) != null || selectedFor(Category.SHOES) != null);
    }

    private String selectedFor(String category) {
        for (RecognitionSlot slot : recognitionSlots) {
            if (category.equals(slot.category) && slot.selectedGarmentId != null && !slot.selectedGarmentId.isEmpty()) {
                return slot.selectedGarmentId;
            }
        }
        return null;
    }

    private double confidenceFor(RecognitionSlot slot, String garmentId) {
        for (RecognitionCandidate candidate : slot.alternatives) {
            if (candidate.garmentId.equals(garmentId)) {
                return candidate.confidence;
            }
        }
        return 1;
    }

    private void reloadData() {
        garments = database.getGarments(true);
        records = database.getWearRecords();
        if (selectedGarmentId == null && !garments.isEmpty()) {
            selectedGarmentId = garments.get(0).id;
        }
    }

    private List<Garment> activeGarments() {
        ArrayList<Garment> active = new ArrayList<>();
        for (Garment garment : garments) {
            if (!garment.isArchived()) {
                active.add(garment);
            }
        }
        return active;
    }

    private List<Garment> visibleGarments() {
        ArrayList<Garment> visible = new ArrayList<>();
        for (Garment garment : activeGarments()) {
            if ("all".equals(wardrobeFilter) || wardrobeFilter.equals(garment.category)) {
                visible.add(garment);
            }
        }
        return visible;
    }

    private Garment garmentById(String id) {
        if (id == null) {
            return null;
        }
        for (Garment garment : garments) {
            if (id.equals(garment.id)) {
                return garment;
            }
        }
        return null;
    }

    private PeriodRange currentRange() {
        return DateTools.rangeForPreset(periodPreset, customStart, customEnd);
    }

    private StatsCalculator.DashboardStats currentStats() {
        return StatsCalculator.build(garments, records, currentRange());
    }

    private String effectiveComboId(String category) {
        String selected = comboSelection.get(category);
        Garment garment = garmentById(selected);
        if (garment != null && !garment.isArchived() && category.equals(garment.category)) {
            return selected;
        }
        for (Garment item : activeGarments()) {
            if (category.equals(item.category)) {
                return item.id;
            }
        }
        return null;
    }

    private String describeCombo() {
        return outfitNames(
                effectiveComboId(Category.TOP),
                effectiveComboId(Category.BOTTOM),
                effectiveComboId(Category.SHOES)
        );
    }

    private String outfitNames(String topId, String bottomId, String shoesId) {
        ArrayList<String> names = new ArrayList<>();
        addGarmentName(names, topId);
        addGarmentName(names, bottomId);
        addGarmentName(names, shoesId);
        return names.isEmpty() ? "选择衣物生成组合预览" : join(names, " / ");
    }

    private String recordGarmentNames(WearRecord record) {
        return outfitNames(record.topId, record.bottomId, record.shoesId);
    }

    private void addGarmentName(List<String> names, String id) {
        Garment garment = garmentById(id);
        if (garment != null) {
            names.add(garment.name);
        }
    }

    private Spinner garmentSpinner(String category, String selectedId, Consumer<String> onSelected) {
        Spinner spinner = new Spinner(this);
        spinner.setPadding(dp(8), 0, dp(5), 0);
        spinner.setBackground(rounded(FIELD, LINE, dp(8)));
        ArrayList<Garment> items = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        labels.add("未选择");
        for (Garment garment : activeGarments()) {
            if (category.equals(garment.category)) {
                items.add(garment);
                labels.add(garment.name);
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (view instanceof TextView) {
                    ((TextView) view).setTextSize(12);
                    ((TextView) view).setTextColor(INK);
                    ((TextView) view).setSingleLine(true);
                }
                return view;
            }
        };
        spinner.setAdapter(adapter);
        int selectedIndex = 0;
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).id.equals(selectedId)) {
                selectedIndex = index + 1;
                break;
            }
        }
        spinner.setSelection(selectedIndex, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onSelected.accept(position == 0 ? null : items.get(position - 1).id);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        return spinner;
    }

    private void showDatePicker(String current, Consumer<String> onSelected) {
        LocalDate date = DateTools.parse(current);
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> onSelected.accept(DateTools.format(LocalDate.of(year, month + 1, dayOfMonth))),
                date.getYear(),
                date.getMonthValue() - 1,
                date.getDayOfMonth()
        );
        dialog.show();
    }

    private ActionButton actionButton(String label, boolean primary, String iconName) {
        ActionButton button = new ActionButton(label, primary, iconName);
        button.setMinimumHeight(dp(46));
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(rounded(primary ? GREEN : CARD, primary ? GREEN : LINE, dp(8)));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private ImageView iconButton(String iconName, String description, int color) {
        ImageView button = new ImageView(this);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setContentDescription(description);
        button.setImageDrawable(icon(iconName, color, 21));
        button.setBackground(rounded(FIELD, LINE, dp(8)));
        button.setClickable(true);
        button.setFocusable(true);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return button;
    }

    private TextView actionBanner(String message, String command) {
        TextView banner = text(message + "    " + command, 12, GREEN_DARK, Typeface.BOLD);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(12), dp(11), dp(10), dp(11));
        banner.setCompoundDrawablePadding(dp(8));
        banner.setCompoundDrawablesWithIntrinsicBounds(icon(AppIconDrawable.WARDROBE, GREEN, 17), null,
                icon(AppIconDrawable.CHEVRON, GREEN, 16), null);
        banner.setBackground(rounded(GREEN_TINT, GREEN_TINT, dp(8)));
        return banner;
    }

    private View sectionHeader(String title, String caption) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.BOTTOM);
        TextView heading = text(title, 18, INK, Typeface.BOLD);
        row.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text(caption, 11, MUTED, Typeface.NORMAL));
        return row;
    }

    private TextView fieldLabel(String value) {
        return text(value, 12, INK, Typeface.BOLD);
    }

    private EditText editText(String hint, String value, Consumer<String> onChange) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value == null ? "" : value);
        editText.setTextColor(INK);
        editText.setHintTextColor(0xff9aa29e);
        editText.setTextSize(14);
        editText.setSingleLine(false);
        editText.setBackground(rounded(FIELD, LINE, dp(8)));
        editText.setPadding(dp(12), dp(10), dp(12), dp(10));
        editText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                onChange.accept(editable.toString());
            }
        });
        return editText;
    }

    private View emptyState(String iconName, String title, String caption) {
        LinearLayout state = vertical();
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(18), dp(26), dp(18), dp(26));
        state.setBackground(rounded(CARD, LINE, dp(8)));
        ImageView iconView = new ImageView(this);
        iconView.setImageDrawable(icon(iconName, 0xff95a19c, 28));
        state.addView(iconView, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView heading = text(title, 14, INK, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        addWithTop(state, heading, dp(9));
        TextView description = text(caption, 12, MUTED, Typeface.NORMAL);
        description.setGravity(Gravity.CENTER);
        addWithTop(state, description, dp(4));
        return state;
    }

    private ImageView photoPlaceholder(String iconName, int sizeDp) {
        ImageView image = new ImageView(this);
        image.setImageDrawable(icon(iconName, 0xff99a49f, 26));
        image.setBackground(rounded(FIELD, LINE, dp(8)));
        image.setPadding(dp(28), dp(28), dp(28), dp(28));
        image.setContentDescription("暂无图片");
        image.setMinimumHeight(dp(sizeDp));
        return image;
    }

    private ImageView image(String path, int heightDp) {
        ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackground(rounded(0xffedf0ee, 0xffedf0ee, dp(8)));
        imageView.setClipToOutline(true);
        imageView.setAdjustViewBounds(false);
        imageView.setContentDescription("衣物照片");
        Bitmap bitmap = ImageStore.loadBitmap(path, Math.max(320, heightDp * 3));
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(heightDp)
        ));
        return imageView;
    }

    private void addOutfitImage(LinearLayout parent, String id) {
        Garment garment = garmentById(id);
        if (garment == null) {
            return;
        }
        ImageView image = image(garment.photoPath, 50);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(40), dp(50));
        params.rightMargin = dp(4);
        parent.addView(image, params);
    }

    private AppIconDrawable icon(String name, int color, int sizeDp) {
        return new AppIconDrawable(name, color, dp(sizeDp), 1.8f);
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.create("sans", style));
        textView.setIncludeFontPadding(true);
        textView.setLetterSpacing(0);
        return textView;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightedWrap() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout.LayoutParams weightedMatch() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
    }

    private LinearLayout.LayoutParams weightedWrapWithMargins(int margin) {
        LinearLayout.LayoutParams params = weightedWrap();
        params.leftMargin = margin;
        params.rightMargin = margin;
        return params;
    }

    private void addWithTop(LinearLayout parent, View view, int topMargin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = topMargin;
        parent.addView(view, params);
    }

    private void addWithTop(LinearLayout parent, View view, int topMargin, LinearLayout.LayoutParams params) {
        params.topMargin = topMargin;
        parent.addView(view, params);
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable circle(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        drawable.setStroke(dp(2), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int parseColor(String value, int fallback) {
        try {
            return Color.parseColor(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String join(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private String id(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    private final class ActionButton extends LinearLayout {
        private final TextView labelView;

        ActionButton(String label, boolean primary, String iconName) {
            super(MainActivity.this);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            int color = primary ? Color.WHITE : GREEN;
            if (iconName != null) {
                ImageView iconView = new ImageView(MainActivity.this);
                iconView.setImageDrawable(icon(iconName, color, 18));
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
                iconParams.rightMargin = dp(8);
                addView(iconView, iconParams);
            }
            labelView = text(label, 14, primary ? Color.WHITE : INK, Typeface.BOLD);
            addView(labelView);
        }

        void setTextColor(int color) {
            labelView.setTextColor(color);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            for (int index = 0; index < getChildCount(); index++) {
                getChildAt(index).setEnabled(enabled);
            }
        }
    }
}
