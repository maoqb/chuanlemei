package com.maoqb.chuanlemei;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
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
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.maoqb.chuanlemei.data.ImageStore;
import com.maoqb.chuanlemei.data.WardrobeDatabase;
import com.maoqb.chuanlemei.domain.Category;
import com.maoqb.chuanlemei.domain.DateTools;
import com.maoqb.chuanlemei.domain.Garment;
import com.maoqb.chuanlemei.domain.PeriodRange;
import com.maoqb.chuanlemei.domain.StatsCalculator;
import com.maoqb.chuanlemei.domain.WearRecord;
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

    private static final String TAB_RECORD = "record";
    private static final String TAB_WARDROBE = "wardrobe";
    private static final String TAB_OUTFITS = "outfits";
    private static final String TAB_STATS = "stats";

    private static final int PAPER = 0xfff7f4ee;
    private static final int INK = 0xff1e2523;
    private static final int MUTED = 0xff6d665f;
    private static final int LINE = 0xffded4c8;
    private static final int CARD = 0xfffffbf6;
    private static final int PINE = 0xff263b37;
    private static final int CLAY = 0xffb8653c;
    private static final int GREEN_TINT = 0xffedf8f1;
    private static final int WARNING_TINT = 0xfffff0e8;

    private WardrobeDatabase database;
    private LinearLayout content;
    private TextView statusText;

    private List<Garment> garments = new ArrayList<>();
    private List<WearRecord> records = new ArrayList<>();
    private String currentTab = TAB_RECORD;
    private String statusMessage = "正在读取本地数据";

    private String draftName = "";
    private String draftCategory = Category.TOP;
    private String draftBrand = "";
    private String draftNote = "";
    private String draftColor = "#2F6F73";
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
        database = new WardrobeDatabase(this);
        reloadData();
        buildShell();
        render();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            setStatus("操作已取消");
            return;
        }

        try {
            if (requestCode == REQUEST_IMPORT_GARMENT_PHOTO && data != null && data.getData() != null) {
                handleImportedGarmentPhoto(data.getData());
            } else if (requestCode == REQUEST_CAPTURE_WEAR && pendingCaptureUri != null) {
                handleCapturedWearPhoto(pendingCaptureUri);
            }
        } catch (Exception error) {
            setStatus(error.getMessage() == null ? "图片处理失败" : error.getMessage());
        }
    }

    private void buildShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(PAPER);

        shell.addView(buildHeader());
        shell.addView(buildTabs());
        shell.addView(buildStatus());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        content = vertical();
        content.setPadding(dp(16), dp(16), dp(16), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        shell.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(shell);
    }

    private View buildHeader() {
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(18), dp(16), dp(14));
        header.setBackgroundColor(PINE);

        TextView mark = text("穿", 22, 0xfff7f4ee, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(rounded(0x22ffffff, 0x66f7f4ee, dp(8)));
        header.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titleBlock = vertical();
        titleBlock.setPadding(dp(12), 0, 0, 0);
        TextView title = text("穿了没", 24, 0xffffffff, Typeface.BOLD);
        TextView subtitle = text("Android 原生 · 相机拍照记录衣物穿着次数", 13, 0xffd8e5dc, Typeface.NORMAL);
        titleBlock.addView(title);
        titleBlock.addView(subtitle);
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return header;
    }

    private View buildTabs() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackgroundColor(PAPER);
        LinearLayout tabs = horizontal();
        tabs.setPadding(dp(12), dp(12), dp(12), dp(4));
        tabs.addView(tabButton("记录", TAB_RECORD));
        tabs.addView(tabButton("衣橱", TAB_WARDROBE));
        tabs.addView(tabButton("组合", TAB_OUTFITS));
        tabs.addView(tabButton("统计", TAB_STATS));
        scroll.addView(tabs);
        return scroll;
    }

    private View buildStatus() {
        LinearLayout wrapper = vertical();
        wrapper.setPadding(dp(16), dp(8), dp(16), 0);
        statusText = text(statusMessage, 13, MUTED, Typeface.NORMAL);
        statusText.setPadding(dp(12), dp(10), dp(12), dp(10));
        statusText.setBackground(rounded(CARD, LINE, dp(8)));
        wrapper.addView(statusText, matchWrap());
        return wrapper;
    }

    private Button tabButton(String label, String tab) {
        Button button = button(label, tab.equals(currentTab));
        button.setMinWidth(dp(82));
        button.setOnClickListener(view -> {
            currentTab = tab;
            render();
        });
        return button;
    }

    private void render() {
        statusText.setText(statusMessage);
        content.removeAllViews();
        if (TAB_WARDROBE.equals(currentTab)) {
            renderWardrobe();
        } else if (TAB_OUTFITS.equals(currentTab)) {
            renderOutfits();
        } else if (TAB_STATS.equals(currentTab)) {
            renderStats();
        } else {
            renderRecord();
        }
    }

    private void renderRecord() {
        addTitle(content, "Capture", "今日拍照记录");

        LinearLayout cameraCard = card();
        LinearLayout dateRow = horizontal();
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateRow.addView(labelBlock("记录日期", recordDate), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button dateButton = button("选择日期", false);
        dateButton.setOnClickListener(view -> showDatePicker(recordDate, value -> {
            recordDate = value;
            render();
        }));
        dateRow.addView(dateButton);
        cameraCard.addView(dateRow);

        TextView dateStatus = text(DateTools.isToday(recordDate) ? "日期校验通过：本地当天" : "日期不是本地当天，禁止保存", 13,
                DateTools.isToday(recordDate) ? 0xff285d47 : 0xff8d3f24, Typeface.BOLD);
        dateStatus.setPadding(dp(12), dp(10), dp(12), dp(10));
        dateStatus.setBackground(rounded(DateTools.isToday(recordDate) ? GREEN_TINT : WARNING_TINT, LINE, dp(8)));
        addWithTop(cameraCard, dateStatus, dp(10));

        Button capture = button("调用相机拍照并识别", true);
        capture.setOnClickListener(view -> launchWearCamera());
        addWithTop(cameraCard, capture, dp(12));

        if (recordPhotoPath != null) {
            addWithTop(cameraCard, image(recordPhotoPath, 220), dp(12));
        }

        content.addView(cameraCard);

        LinearLayout recognitionCard = card();
        TextView recognitionTitle = text("识别结果确认", 18, INK, Typeface.BOLD);
        recognitionCard.addView(recognitionTitle);

        if (recognitionSlots.isEmpty()) {
            addWithTop(recognitionCard, muted("拍照后会按上衣、裤子、鞋生成候选。"), dp(8));
        } else {
            for (RecognitionSlot slot : recognitionSlots) {
                addWithTop(recognitionCard, recognitionSlotView(slot), dp(10));
            }
        }

        EditText note = editText("备注，可选", recordNote, value -> recordNote = value);
        note.setMinLines(2);
        note.setImeOptions(EditorInfo.IME_ACTION_DONE);
        addWithTop(recognitionCard, note, dp(12));

        Button save = button("保存今日穿着", true);
        save.setEnabled(canSaveWearRecord());
        save.setOnClickListener(view -> saveWearRecord());
        addWithTop(recognitionCard, save, dp(12));
        content.addView(recognitionCard);

        renderRecentRecords();
    }

    private View recognitionSlotView(RecognitionSlot slot) {
        LinearLayout box = vertical();
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackground(rounded(0xfffffaf4, LINE, dp(8)));

        TextView title = text(
                Category.label(slot.category) + " · " + Math.round(slot.confidence * 100) + "%",
                15,
                INK,
                Typeface.BOLD
        );
        box.addView(title);

        Spinner spinner = garmentSpinner(slot.category, slot.selectedGarmentId, selected -> {
            slot.selectedGarmentId = selected;
            slot.confidence = selected == null ? 0 : confidenceFor(slot, selected);
            render();
        });
        addWithTop(box, spinner, dp(8));

        if (!slot.alternatives.isEmpty()) {
            LinearLayout candidates = vertical();
            for (RecognitionCandidate candidate : slot.alternatives) {
                Button candidateButton = button(
                        candidate.garmentName + " " + Math.round(candidate.confidence * 100) + "%",
                        false
                );
                candidateButton.setOnClickListener(view -> {
                    slot.selectedGarmentId = candidate.garmentId;
                    slot.confidence = candidate.confidence;
                    render();
                });
                addWithTop(candidates, candidateButton, dp(6));
            }
            addWithTop(box, candidates, dp(6));
        }
        return box;
    }

    private void renderRecentRecords() {
        LinearLayout recent = card();
        recent.addView(text("最近记录", 18, INK, Typeface.BOLD));
        if (records.isEmpty()) {
            addWithTop(recent, empty("暂无记录，拍照保存后会显示。"), dp(8));
        } else {
            int limit = Math.min(5, records.size());
            for (int index = 0; index < limit; index++) {
                WearRecord record = records.get(index);
                addWithTop(recent, recordRow(record), dp(10));
            }
        }
        content.addView(recent);
    }

    private View recordRow(WearRecord record) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setBackground(rounded(0xffffffff, LINE, dp(8)));
        row.addView(image(record.photoPath, 56), new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout info = vertical();
        info.setPadding(dp(10), 0, dp(8), 0);
        info.addView(text(DateTools.readable(record.wornAt), 15, INK, Typeface.BOLD));
        info.addView(muted(recordGarmentNames(record)));
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button delete = button("删除", false);
        delete.setOnClickListener(view -> {
            database.deleteWearRecord(record.id);
            reloadData();
            setStatus("记录已删除");
        });
        row.addView(delete);
        return row;
    }

    private void renderWardrobe() {
        addTitle(content, "Wardrobe", "衣橱导入");

        LinearLayout form = card();
        if (draftPhotoPath != null) {
            form.addView(image(draftPhotoPath, 220));
        } else {
            form.addView(empty("先选择一张衣物照片，保存时会生成识别特征。"));
        }

        Button choosePhoto = button("选择衣物照片", false);
        choosePhoto.setOnClickListener(view -> launchImageImport());
        addWithTop(form, choosePhoto, dp(12));

        addWithTop(form, editText("名称，例如 黑色衬衫", draftName, value -> draftName = value), dp(12));
        addWithTop(form, categorySpinner(draftCategory, value -> {
            draftCategory = value;
            draftColor = String.format(Locale.US, "#%06X", Category.accentColor(value) & 0xffffff);
        }), dp(12));
        addWithTop(form, editText("主色 Hex，例如 #2F6F73", draftColor, value -> draftColor = value), dp(12));
        addWithTop(form, editText("品牌，可选", draftBrand, value -> draftBrand = value), dp(12));
        EditText note = editText("备注，可选", draftNote, value -> draftNote = value);
        note.setMinLines(2);
        addWithTop(form, note, dp(12));

        Button save = button("加入衣橱", true);
        save.setOnClickListener(view -> saveGarment());
        addWithTop(form, save, dp(12));
        content.addView(form);

        LinearLayout filters = horizontal();
        filters.addView(filterButton("全部", "all"));
        filters.addView(filterButton("上衣", Category.TOP));
        filters.addView(filterButton("裤子", Category.BOTTOM));
        filters.addView(filterButton("鞋", Category.SHOES));
        addWithTop(content, filters, dp(12));

        LinearLayout list = card();
        list.addView(text("衣物列表", 18, INK, Typeface.BOLD));
        List<Garment> visible = visibleGarments();
        if (visible.isEmpty()) {
            addWithTop(list, empty("还没有衣物，先导入照片。"), dp(8));
        } else {
            for (Garment garment : visible) {
                addWithTop(list, garmentRow(garment), dp(10));
            }
        }
        content.addView(list);
        renderSelectedGarmentDetail();
    }

    private Button filterButton(String label, String filter) {
        Button button = button(label, filter.equals(wardrobeFilter));
        button.setOnClickListener(view -> {
            wardrobeFilter = filter;
            render();
        });
        return button;
    }

    private View garmentRow(Garment garment) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setBackground(rounded(0xffffffff, selectedGarmentId != null && selectedGarmentId.equals(garment.id) ? 0xff668d7d : LINE, dp(8)));
        row.setOnClickListener(view -> {
            selectedGarmentId = garment.id;
            render();
        });
        row.addView(image(garment.photoPath, 60), new LinearLayout.LayoutParams(dp(60), dp(60)));

        LinearLayout info = vertical();
        info.setPadding(dp(10), 0, dp(8), 0);
        info.addView(text(garment.name, 16, INK, Typeface.BOLD));
        info.addView(muted(Category.label(garment.category) + " · 穿过 " + StatsCalculator.countForGarment(records, garment.id) + " 次"));
        if (garment.brand != null && !garment.brand.isEmpty()) {
            info.addView(muted(garment.brand));
        }
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button archive = button("停用", false);
        archive.setOnClickListener(view -> {
            database.saveGarment(garment.archived(DateTools.nowIsoSecond()));
            reloadData();
            setStatus(garment.name + " 已停用");
        });
        row.addView(archive);
        return row;
    }

    private void renderOutfits() {
        addTitle(content, "Outfits", "上衣、裤子和鞋组合");

        LinearLayout composer = card();
        for (String category : Category.ORDER) {
            String effectiveId = effectiveComboId(category);
            TextView label = text(Category.label(category), 15, INK, Typeface.BOLD);
            addWithTop(composer, label, dp(8));
            Spinner spinner = garmentSpinner(category, effectiveId, selected -> {
                if (selected == null) {
                    comboSelection.remove(category);
                } else {
                    comboSelection.put(category, selected);
                }
                render();
            });
            addWithTop(composer, spinner, dp(6));
            Garment garment = garmentById(effectiveId);
            if (garment != null) {
                addWithTop(composer, image(garment.photoPath, category.equals(Category.SHOES) ? 120 : 180), dp(8));
            } else {
                addWithTop(composer, empty("未选择" + Category.label(category)), dp(8));
            }
        }
        TextView summary = text(describeCombo(), 14, 0xff285d47, Typeface.BOLD);
        summary.setPadding(dp(12), dp(10), dp(12), dp(10));
        summary.setBackground(rounded(GREEN_TINT, LINE, dp(8)));
        addWithTop(composer, summary, dp(12));
        content.addView(composer);

        LinearLayout outfitStats = card();
        outfitStats.addView(text("周期内常穿组合", 18, INK, Typeface.BOLD));
        StatsCalculator.DashboardStats stats = currentStats();
        if (stats.outfitStats.isEmpty()) {
            addWithTop(outfitStats, empty("保存穿着记录后自动统计组合。"), dp(8));
        } else {
            int limit = Math.min(6, stats.outfitStats.size());
            for (int index = 0; index < limit; index++) {
                StatsCalculator.OutfitStat stat = stats.outfitStats.get(index);
                addWithTop(outfitStats, outfitStatRow(stat), dp(10));
            }
        }
        content.addView(outfitStats);
    }

    private View outfitStatRow(StatsCalculator.OutfitStat stat) {
        LinearLayout row = vertical();
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setBackground(rounded(0xffffffff, LINE, dp(8)));
        row.addView(text(outfitNames(stat.topId, stat.bottomId, stat.shoesId), 15, INK, Typeface.BOLD));
        row.addView(muted("穿过 " + stat.count + " 次 · 最近 " + (stat.lastWornAt == null ? "-" : DateTools.readable(stat.lastWornAt))));
        LinearLayout photos = horizontal();
        addOutfitImage(photos, stat.topId);
        addOutfitImage(photos, stat.bottomId);
        addOutfitImage(photos, stat.shoesId);
        addWithTop(row, photos, dp(8));
        return row;
    }

    private void renderStats() {
        addTitle(content, "Analytics", "历史周期数据");

        LinearLayout period = card();
        period.addView(text("统计周期", 18, INK, Typeface.BOLD));
        Spinner spinner = periodSpinner();
        addWithTop(period, spinner, dp(10));
        if (DateTools.PERIOD_CUSTOM.equals(periodPreset)) {
            Button start = button("开始 " + customStart, false);
            start.setOnClickListener(view -> showDatePicker(customStart, value -> {
                customStart = value;
                render();
            }));
            addWithTop(period, start, dp(8));
            Button end = button("结束 " + customEnd, false);
            end.setOnClickListener(view -> showDatePicker(customEnd, value -> {
                customEnd = value;
                render();
            }));
            addWithTop(period, end, dp(8));
        }
        PeriodRange range = currentRange();
        period.addView(muted(range.start + " 至 " + range.end));
        content.addView(period);

        StatsCalculator.DashboardStats stats = StatsCalculator.build(garments, records, range);
        LinearLayout metrics = card();
        metrics.addView(metric("穿着记录", stats.totalRecords, "周期内记录数"));
        metrics.addView(metric("衣物计次", stats.totalGarmentWears, "上衣、裤子、鞋分别计数"));
        metrics.addView(metric("活跃衣物", stats.activeGarments, "未停用衣物"));
        content.addView(metrics);

        LinearLayout categories = card();
        categories.addView(text("分类计次", 18, INK, Typeface.BOLD));
        for (String category : Category.ORDER) {
            addWithTop(categories, muted(Category.label(category) + ": " + stats.categoryCounts.get(category) + " 次"), dp(6));
        }
        content.addView(categories);

        LinearLayout days = card();
        days.addView(text("日历趋势", 18, INK, Typeface.BOLD));
        List<StatsCalculator.DailyCount> dailyCounts = stats.dailyCounts;
        int start = Math.max(0, dailyCounts.size() - 30);
        for (int index = start; index < dailyCounts.size(); index++) {
            StatsCalculator.DailyCount day = dailyCounts.get(index);
            addWithTop(days, muted(DateTools.shortDate(day.date) + "  " + bar(day.count) + " " + day.count), dp(4));
        }
        content.addView(days);

        LinearLayout ranking = card();
        ranking.addView(text("衣物排行", 18, INK, Typeface.BOLD));
        if (stats.garmentStats.isEmpty()) {
            addWithTop(ranking, empty("暂无衣物数据。"), dp(8));
        } else {
            int limit = Math.min(10, stats.garmentStats.size());
            for (int index = 0; index < limit; index++) {
                StatsCalculator.GarmentStat stat = stats.garmentStats.get(index);
                addWithTop(ranking, garmentStatRow(stat), dp(8));
            }
        }
        content.addView(ranking);
        renderSelectedGarmentDetail();
    }

    private View metric(String label, int value, String detail) {
        LinearLayout box = vertical();
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackground(rounded(0xfffffaf4, LINE, dp(8)));
        box.addView(muted(label));
        box.addView(text(String.valueOf(value), 28, INK, Typeface.BOLD));
        box.addView(muted(detail));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(8));
        box.setLayoutParams(params);
        return box;
    }

    private View garmentStatRow(StatsCalculator.GarmentStat stat) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setBackground(rounded(0xffffffff, LINE, dp(8)));
        row.setOnClickListener(view -> {
            selectedGarmentId = stat.garment.id;
            render();
        });
        row.addView(image(stat.garment.photoPath, 52), new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout info = vertical();
        info.setPadding(dp(10), 0, 0, 0);
        info.addView(text(stat.garment.name, 15, INK, Typeface.BOLD));
        info.addView(muted("本期 " + stat.rangeCount + " 次 · 总计 " + stat.totalCount + " 次"));
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView count = text(String.valueOf(stat.rangeCount), 22, INK, Typeface.BOLD);
        row.addView(count);
        return row;
    }

    private void renderSelectedGarmentDetail() {
        Garment selected = selectedGarment();
        LinearLayout detail = card();
        detail.addView(text("单件衣物数据", 18, INK, Typeface.BOLD));
        if (selected == null) {
            addWithTop(detail, empty("选择一件衣物查看明细。"), dp(8));
            content.addView(detail);
            return;
        }

        LinearLayout hero = horizontal();
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(image(selected.photoPath, 72), new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout info = vertical();
        info.setPadding(dp(12), 0, 0, 0);
        info.addView(text(selected.name, 17, INK, Typeface.BOLD));
        info.addView(muted(Category.label(selected.category) + " · " + (selected.brand == null ? "未填写品牌" : selected.brand)));
        hero.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        addWithTop(detail, hero, dp(12));

        List<WearRecord> garmentRecords = StatsCalculator.recordsForGarment(records, selected.id);
        addWithTop(detail, text("总次数 " + garmentRecords.size(), 22, INK, Typeface.BOLD), dp(12));
        if (garmentRecords.isEmpty()) {
            detail.addView(muted("还没有穿着记录。"));
        } else {
            int limit = Math.min(6, garmentRecords.size());
            for (int index = 0; index < limit; index++) {
                WearRecord record = garmentRecords.get(index);
                addWithTop(detail, muted(DateTools.readable(record.wornAt) + " · " + recordGarmentNames(record)), dp(6));
            }
        }
        content.addView(detail);
    }

    private void launchImageImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_GARMENT_PHOTO);
        } catch (ActivityNotFoundException error) {
            setStatus("没有可用的图片选择器");
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
                setStatus("无法创建相机输出文件");
                return;
            }
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCaptureUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CAPTURE_WEAR);
        } catch (ActivityNotFoundException error) {
            setStatus("没有可用的相机应用");
        } catch (Exception error) {
            setStatus(error.getMessage() == null ? "打开相机失败" : error.getMessage());
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
        setStatus("衣物照片已导入");
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
        setStatus("拍照识别完成，请确认衣物");
    }

    private void saveGarment() {
        String name = draftName.trim();
        if (name.isEmpty()) {
            setStatus("衣物名称不能为空");
            return;
        }
        if (draftPhotoPath == null || draftSignature == null) {
            setStatus("请先选择衣物照片");
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
                draftBrand,
                draftNote,
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
        reloadData();
        setStatus(garment.name + " 已加入衣橱");
    }

    private void saveWearRecord() {
        if (!canSaveWearRecord()) {
            setStatus("需要相机照片、当天日期和至少一件衣物");
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
                recordNote
        );
        database.saveWearRecord(record);
        recordPhotoPath = null;
        recordNote = "";
        recognitionSlots.clear();
        reloadData();
        setStatus("今日穿着已记录");
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
        statusMessage = garments.isEmpty() ? "先导入衣物照片" : "本地数据已就绪";
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

    private Garment selectedGarment() {
        if (selectedGarmentId != null) {
            Garment selected = garmentById(selectedGarmentId);
            if (selected != null) {
                return selected;
            }
        }
        return garments.isEmpty() ? null : garments.get(0);
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

    private Spinner categorySpinner(String selected, Consumer<String> onSelected) {
        Spinner spinner = new Spinner(this);
        String[] labels = {"上衣", "裤子", "鞋"};
        String[] values = {Category.TOP, Category.BOTTOM, Category.SHOES};
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        int selectedIndex = 0;
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(selected)) {
                selectedIndex = index;
                break;
            }
        }
        spinner.setSelection(selectedIndex);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onSelected.accept(values[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        return spinner;
    }

    private Spinner garmentSpinner(String category, String selectedId, Consumer<String> onSelected) {
        Spinner spinner = new Spinner(this);
        ArrayList<Garment> items = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        labels.add("未选择");
        for (Garment garment : activeGarments()) {
            if (category.equals(garment.category)) {
                items.add(garment);
                labels.add(garment.name);
            }
        }
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        int selectedIndex = 0;
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).id.equals(selectedId)) {
                selectedIndex = index + 1;
                break;
            }
        }
        spinner.setSelection(selectedIndex);
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

    private Spinner periodSpinner() {
        Spinner spinner = new Spinner(this);
        String[] labels = {"7天", "30天", "90天", "今年", "全部", "自定义"};
        String[] values = {
                DateTools.PERIOD_7_DAYS,
                DateTools.PERIOD_30_DAYS,
                DateTools.PERIOD_90_DAYS,
                DateTools.PERIOD_YEAR,
                DateTools.PERIOD_ALL,
                DateTools.PERIOD_CUSTOM
        };
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        int selectedIndex = 1;
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(periodPreset)) {
                selectedIndex = index;
                break;
            }
        }
        spinner.setSelection(selectedIndex);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String next = values[position];
                if (!next.equals(periodPreset)) {
                    periodPreset = next;
                    render();
                }
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

    private void setStatus(String message) {
        statusMessage = message;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        render();
    }

    private void addTitle(LinearLayout parent, String eyebrow, String title) {
        TextView eyebrowView = text(eyebrow, 12, CLAY, Typeface.BOLD);
        parent.addView(eyebrowView);
        TextView titleView = text(title, 26, INK, Typeface.BOLD);
        parent.addView(titleView);
    }

    private LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(CARD, LINE, dp(8)));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(12), 0, 0);
        card.setLayoutParams(params);
        return card;
    }

    private TextView labelBlock(String label, String value) {
        TextView textView = text(label + "\n" + value, 15, INK, Typeface.BOLD);
        textView.setLineSpacing(dp(2), 1);
        return textView;
    }

    private EditText editText(String hint, String value, Consumer<String> onChange) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value == null ? "" : value);
        editText.setTextColor(INK);
        editText.setHintTextColor(0xff978b7e);
        editText.setTextSize(14);
        editText.setSingleLine(false);
        editText.setBackground(rounded(0xffffffff, LINE, dp(7)));
        editText.setPadding(dp(10), dp(8), dp(10), dp(8));
        editText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                onChange.accept(editable.toString());
            }
        });
        return editText;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : INK);
        button.setBackground(rounded(primary ? PINE : 0xffffffff, primary ? PINE : LINE, dp(7)));
        button.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
        );
        params.setMargins(0, 0, dp(8), 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.DEFAULT, style);
        textView.setIncludeFontPadding(true);
        return textView;
    }

    private TextView muted(String value) {
        return text(value, 13, MUTED, Typeface.NORMAL);
    }

    private TextView empty(String value) {
        TextView textView = text(value, 13, MUTED, Typeface.BOLD);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(dp(14), dp(20), dp(14), dp(20));
        textView.setBackground(rounded(0xfffffaf4, 0xffcabfae, dp(8)));
        return textView;
    }

    private ImageView image(String path, int heightDp) {
        ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundColor(0xffebe2d5);
        imageView.setAdjustViewBounds(false);
        imageView.setMinimumHeight(dp(heightDp));
        Bitmap bitmap = ImageStore.loadBitmap(path, Math.max(320, heightDp * 3));
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }
        imageView.setClipToOutline(false);
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
        ImageView image = image(garment.photoPath, 76);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(76), 1);
        params.setMargins(0, 0, dp(6), 0);
        parent.addView(image, params);
    }

    private String bar(int count) {
        if (count <= 0) {
            return "";
        }
        int length = Math.min(16, count);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < length; index++) {
            builder.append('|');
        }
        return builder.toString();
    }

    private void addWithTop(LinearLayout parent, View view, int topMargin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, topMargin, 0, 0);
        parent.addView(view, params);
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

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
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

    private String id(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
