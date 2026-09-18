package com.nutriflow.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Window;
import android.view.WindowManager;
import android.provider.Settings;
import android.app.DatePickerDialog;

import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.InputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(247, 250, 248);
    private static final int GREEN = Color.rgb(13, 135, 95);
    private static final int DARK = Color.rgb(23, 51, 43);
    private static final int MUTED = Color.rgb(102, 124, 115);
    private static final int MINT = Color.rgb(230, 246, 239);
    private static final int LINE = Color.rgb(220, 232, 226);
    private static final int ORANGE = Color.rgb(224, 116, 55);
    private static final String ROLE_USER = "user";
    private static final String ROLE_DIETITIAN = "dietitian";
    private static final String ROLE_MERCHANT = "merchant";
    private static final int PICK_VERIFICATION_IMAGE = 4101;
    private static final String[] DELIVERY_PACKAGES = {"com.sankuai.meituan", "com.sankuai.meituan.takeoutnew", "me.ele", "com.taobao.taobao", "com.jingdong.app.mall"};
    private static final String[] DELIVERY_LABELS = {"美团", "美团外卖", "饿了么", "淘宝闪购", "京东外卖"};
    private SharedPreferences prefs;
    private LinearLayout root;
    private LinearLayout content;
    private int currentPage = 0;
    private List<DeliveryApp> deliveryApps = new ArrayList<>();
    private String selectedLogDate;
    private boolean standalonePage = false;
    private Uri pendingVerificationUri;
    private String pendingVerificationRole;
    private ImageView verificationPreview;
    private TextView verificationStatus;
    private Button verificationSubmit;
    private boolean authPage = false;
    private boolean professionalHome = false;
    private TextView crossAppStatusText;
    private Button crossAppSettingsButton;
    private Button crossAppPauseButton;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        if (android.os.Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        prefs = getSharedPreferences("nutriflow", MODE_PRIVATE);
        selectedLogDate = NutritionLogStore.todayKey();
        refreshInstalledApps();
        if (prefs.getBoolean("auth_logged_in", false)) routeAfterLogin(); else showRoleChooser();
    }

    @Override protected void onResume() {
        super.onResume();
        updateCrossAppStatus();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_VERIFICATION_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        pendingVerificationUri = data.getData();
        try { getContentResolver().takePersistableUriPermission(pendingVerificationUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
        if (verificationPreview != null) {
            verificationPreview.setImageURI(pendingVerificationUri);
            verificationPreview.setVisibility(View.VISIBLE);
        }
        if (verificationStatus != null) verificationStatus.setText("已选择证件图片，请点击“提交并审查”开始本地 OCR 关键词检查。");
        if (verificationSubmit != null) verificationSubmit.setEnabled(true);
    }

    @Override public void onBackPressed() {
        if (authPage) {
            authPage = false;
            showRoleChooser();
        } else if (professionalHome) {
            professionalHome = false;
            showRoleChooser();
        } else if (standalonePage || currentPage != 0) {
            showApp(0);
        } else {
            super.onBackPressed();
        }
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern"); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private TextView title(String value, float size) { TextView t = text(value, size, DARK); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private GradientDrawable bg(int color, float radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }
    private GradientDrawable stroke(int color, int lineColor, float radius) { GradientDrawable g = bg(color, radius); g.setStroke(dp(1), lineColor); return g; }
    private void margin(View v, int l, int t, int r, int b) { v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2)); ((ViewGroup.MarginLayoutParams)v.getLayoutParams()).setMargins(dp(l), dp(t), dp(r), dp(b)); }
    private void pad(View v, int l, int t, int r, int b) { v.setPadding(dp(l), dp(t), dp(r), dp(b)); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    private void base() {
        crossAppStatusText = null;
        crossAppSettingsButton = null;
        crossAppPauseButton = null;
        root = column(); root.setBackgroundColor(BG); setContentView(root);
    }

    private String roleLabel(String role) {
        if (ROLE_DIETITIAN.equals(role)) return "营养师";
        if (ROLE_MERCHANT.equals(role)) return "商户端";
        return "普通用户";
    }

    private void showRoleChooser() {
        authPage = false; professionalHome = false;
        pendingVerificationUri = null;
        pendingVerificationRole = null;
        base();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout page = column(); pad(page, 22, 24, 22, 30); scroll.addView(page);
        LinearLayout brand = row();
        TextView mark = text("NF", 16, Color.WHITE); mark.setGravity(Gravity.CENTER); mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD); mark.setBackground(bg(GREEN, 18));
        brand.addView(mark, new LinearLayout.LayoutParams(dp(36), dp(36))); brand.addView(title("  营养流", 20), new LinearLayout.LayoutParams(-2, dp(36))); page.addView(brand);
        TextView eyebrow = text("NutriFlow · AI 动态营养算法", 12, GREEN); margin(eyebrow, 0, 34, 0, 8); page.addView(eyebrow);
        TextView heading = title("选择你的进入身份", 27); page.addView(heading);
        TextView intro = text("不同身份会看到对应的工作空间。首次使用请先注册，之后可直接使用账号密码登录。", 14, MUTED); intro.setLineSpacing(0, 1.25f); margin(intro, 0, 8, 0, 18); page.addView(intro);
        roleCard(page, ROLE_USER, "记录饮食、查看推荐与营养进度", "适合日常饮食管理和外卖营养分析");
        roleCard(page, ROLE_DIETITIAN, "审核营养方案与用户异常记录", "需上传包含“营养师”等关键词的证件");
        roleCard(page, ROLE_MERCHANT, "管理菜品、录入营养标签与菜单", "需上传包含“食品经营许可证”等关键词的证件");
        TextView note = text("当前为本地演示版本：账号记录保存在本机，认证通过后无需重复认证。暂不启用验证码登录。", 11, MUTED); note.setLineSpacing(0, 1.25f); note.setGravity(Gravity.CENTER); margin(note, 6, 18, 6, 0); page.addView(note);
    }

    private void roleCard(LinearLayout page, final String role, String subtitle, String helper) {
        LinearLayout card = card(); card.setClickable(true); card.setFocusable(true);
        LinearLayout top = row(); TextView badge = text(role.equals(ROLE_USER) ? "用" : role.equals(ROLE_DIETITIAN) ? "师" : "商", 20, Color.WHITE); badge.setGravity(Gravity.CENTER); badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD); badge.setBackground(bg(role.equals(ROLE_MERCHANT) ? ORANGE : GREEN, 18)); top.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout labels = column(); TextView label = title(roleLabel(role), 17); labels.addView(label); TextView sub = text(subtitle, 12, MUTED); margin(sub, 3, 0, 0, 0); labels.addView(sub); margin(labels, 12, 0, 8, 0); top.addView(labels, new LinearLayout.LayoutParams(0, -2, 1)); TextView arrow = text("›", 28, GREEN); arrow.setGravity(Gravity.CENTER); top.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(42))); card.addView(top);
        TextView hint = text(helper, 11, MUTED); margin(hint, 0, 9, 0, 0); card.addView(hint); margin(card, 0, 0, 0, 11); page.addView(card); card.setOnClickListener(v -> showAuth(role));
    }

    private void showAuth(final String role) {
        authPage = true; professionalHome = false;
        base(); ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); LinearLayout page = column(); pad(page, 22, 18, 22, 28); scroll.addView(page);
        LinearLayout top = row(); TextView back = text("‹", 34, DARK); back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> showRoleChooser()); top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(44))); top.addView(title(roleLabel(role) + "入口", 21), new LinearLayout.LayoutParams(-2, dp(44))); page.addView(top);
        TextView intro = text("注册完成后可直接使用账号密码登录；当前演示版把账号保存在本机，真正跨设备登录需要后续接入云端账户服务。", 13, MUTED); intro.setLineSpacing(0, 1.25f); margin(intro, 42, -2, 0, 18); page.addView(intro);
        LinearLayout form = card(); form.addView(title("账号密码登录", 17));
        final EditText account = field("账号", "请输入账号（支持汉字）"); addLabeled(form, "账号", "不限制字母、数字或汉字，按注册时的账号原样输入", account, 10);
        final EditText password = field("密码", "请输入密码"); password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); password.setTransformationMethod(PasswordTransformationMethod.getInstance()); addLabeled(form, "密码", "请输入注册时设置的密码", password, 0);
        page.addView(form); margin(form, 0, 0, 0, 12);
        Button login = primary("登录"); page.addView(login, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout actions = row(); Button forgot = outline("忘记密码"); Button register = outline("注册账号"); LinearLayout.LayoutParams forgotParams = new LinearLayout.LayoutParams(0, dp(46), 1); LinearLayout.LayoutParams registerParams = new LinearLayout.LayoutParams(0, dp(46), 1); registerParams.leftMargin = dp(8); actions.addView(forgot, forgotParams); actions.addView(register, registerParams); margin(actions, 0, 10, 0, 0); page.addView(actions);
        TextView status = text("密码规则：7-14 位，至少包含小写字母和数字，不含汉字。", 11, MUTED); status.setGravity(Gravity.CENTER); status.setLineSpacing(0, 1.25f); margin(status, 4, 14, 4, 0); page.addView(status);
        login.setOnClickListener(v -> {
            String a = account.getText().toString(); String pw = password.getText().toString();
            if (a.length() == 0 || pw.length() == 0) { status.setText("请输入账号和密码。"); status.setTextColor(ORANGE); return; }
            JSONObject user = findAccount(role, a);
            if (user == null || !pw.equals(user.optString("password", ""))) { status.setText("账号、身份或密码不正确，请重新输入。"); status.setTextColor(ORANGE); return; }
            loginUser(role, a); status.setTextColor(GREEN); status.setText("登录成功，正在进入……"); routeAfterLogin();
        });
        forgot.setOnClickListener(v -> showForgotPassword(role));
        register.setOnClickListener(v -> showRegister(role));
    }

    private void showForgotPassword(final String role) {
        authPage = true; professionalHome = false;
        base(); ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); LinearLayout page = column(); pad(page, 22, 18, 22, 28); scroll.addView(page);
        LinearLayout top = row(); TextView back = text("‹", 34, DARK); back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> showAuth(role)); top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(44))); top.addView(title("找回" + roleLabel(role) + "账号", 21), new LinearLayout.LayoutParams(-2, dp(44))); page.addView(top);
        TextView intro = text("输入注册时使用的手机号。确认后可以重新设置账号和密码，原有认证状态与资料会保留。当前版本不发送验证码。", 13, MUTED); intro.setLineSpacing(0, 1.25f); margin(intro, 42, -2, 0, 18); page.addView(intro);
        LinearLayout form = card(); form.addView(title("确认注册手机号", 17));
        final EditText phone = field("手机号", "请输入 11 位手机号"); phone.setInputType(InputType.TYPE_CLASS_PHONE); addLabeled(form, "手机号", "必须是已经注册过的 11 位手机号", phone, 0); page.addView(form); margin(form, 0, 0, 0, 12);
        TextView status = text("一个手机号只能绑定一个账号。", 11, MUTED); status.setLineSpacing(0, 1.25f); margin(status, 2, 0, 2, 12); page.addView(status);
        Button confirm = primary("确认手机号"); page.addView(confirm, new LinearLayout.LayoutParams(-1, dp(52)));
        confirm.setOnClickListener(v -> {
            String phoneValue = phone.getText().toString();
            if (!phoneValue.matches("\\d{11}")) { status.setText("手机号必须是恰好 11 位数字。"); status.setTextColor(ORANGE); return; }
            JSONObject owner = findAccountByPhone(phoneValue);
            if (owner == null) { status.setText("没有找到这个手机号对应的账号，请检查输入或先注册。"); status.setTextColor(ORANGE); return; }
            if (!role.equals(owner.optString("role"))) { status.setText("该手机号绑定的是“" + roleLabel(owner.optString("role")) + "”身份，请返回选择对应身份。"); status.setTextColor(ORANGE); return; }
            showResetCredentials(role, phoneValue, owner.optString("account", ""));
        });
    }

    private void showResetCredentials(final String role, final String phone, final String oldAccount) {
        authPage = true; professionalHome = false;
        base(); ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); LinearLayout page = column(); pad(page, 22, 18, 22, 28); scroll.addView(page);
        LinearLayout top = row(); TextView back = text("‹", 34, DARK); back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> showForgotPassword(role)); top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(44))); top.addView(title("重置登录凭据", 21), new LinearLayout.LayoutParams(-2, dp(44))); page.addView(top);
        TextView intro = text("已确认手机号对应的“" + oldAccount + "”账号。请设置新的账号和密码，身份认证状态不会被清除。", 13, MUTED); intro.setLineSpacing(0, 1.25f); margin(intro, 42, -2, 0, 18); page.addView(intro);
        LinearLayout form = card(); form.addView(title("新的登录信息", 17));
        final EditText account = field("新账号", "请输入新账号，可使用汉字"); account.setText(oldAccount); addLabeled(form, "新账号", "不能为空；修改后请使用新账号登录", account, 10);
        final EditText password = field("新密码", "7-14 位，含小写字母和数字"); password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); password.setTransformationMethod(PasswordTransformationMethod.getInstance()); addLabeled(form, "新密码", "必须含小写字母和数字，不能含汉字", password, 10);
        final EditText confirm = field("确认新密码", "请再次输入新密码"); confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); confirm.setTransformationMethod(PasswordTransformationMethod.getInstance()); addLabeled(form, "确认新密码", "两次密码必须一致", confirm, 0);
        page.addView(form); margin(form, 0, 0, 0, 12);
        TextView status = text("保存后请用新的账号和密码登录。", 11, MUTED); status.setLineSpacing(0, 1.25f); margin(status, 2, 0, 2, 12); page.addView(status);
        Button reset = primary("保存新的账号和密码"); page.addView(reset, new LinearLayout.LayoutParams(-1, dp(52)));
        reset.setOnClickListener(v -> {
            String newAccount = account.getText().toString(); String newPassword = password.getText().toString(); String confirmValue = confirm.getText().toString();
            if (newAccount.length() == 0) { status.setText("新账号不能为空。"); status.setTextColor(ORANGE); return; }
            if (!validPassword(newPassword)) { status.setText("新密码需为 7-14 位，含小写字母和数字，且不能含汉字。"); status.setTextColor(ORANGE); return; }
            if (!newPassword.equals(confirmValue)) { status.setText("两次新密码输入不一致。"); status.setTextColor(ORANGE); return; }
            JSONObject conflict = findAccount(role, newAccount);
            if (conflict != null && !phone.equals(conflict.optString("phone", ""))) { status.setText("这个账号名已被当前身份下的其他账号使用，请换一个。"); status.setTextColor(ORANGE); return; }
            if (!updateAccountCredentials(role, phone, newAccount, newPassword)) { status.setText("账号已不存在，请返回重新注册。"); status.setTextColor(ORANGE); return; }
            Toast.makeText(this, "账号和密码已重置，请使用新信息登录", Toast.LENGTH_SHORT).show(); showAuth(role);
        });
    }

    private void showRegister(final String role) {
        authPage = true; professionalHome = false;
        base(); ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); LinearLayout page = column(); pad(page, 22, 18, 22, 28); scroll.addView(page);
        LinearLayout top = row(); TextView back = text("‹", 34, DARK); back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> showAuth(role)); top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(44))); top.addView(title("注册" + roleLabel(role) + "账号", 21), new LinearLayout.LayoutParams(-2, dp(44))); page.addView(top);
        TextView intro = text("三种身份都需要注册。手机号只用于账号资料记录，当前版本不发送验证码。", 13, MUTED); intro.setLineSpacing(0, 1.25f); margin(intro, 42, -2, 0, 18); page.addView(intro);
        LinearLayout form = card(); form.addView(title("账号资料", 17));
        final EditText phone = field("手机号", "请输入 11 位手机号"); phone.setInputType(InputType.TYPE_CLASS_PHONE); addLabeled(form, "手机号", "必须是 11 位数字", phone, 10);
        final EditText account = field("账号", "请输入账号，可使用汉字"); addLabeled(form, "账号", "不限制字母、数字或汉字，但不能为空", account, 10);
        final EditText password = field("密码", "7-14 位，含小写字母和数字"); password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); password.setTransformationMethod(PasswordTransformationMethod.getInstance()); addLabeled(form, "密码", "必须含至少 1 个小写字母和 1 个数字，不能含汉字", password, 10);
        final EditText confirm = field("确认密码", "请再次输入密码"); confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); confirm.setTransformationMethod(PasswordTransformationMethod.getInstance()); addLabeled(form, "确认密码", "两次输入必须一致", confirm, 0);
        page.addView(form); margin(form, 0, 0, 0, 12);
        TextView status = text("注册后可直接回到登录页使用账号密码登录。", 11, MUTED); status.setLineSpacing(0, 1.25f); margin(status, 2, 0, 2, 12); page.addView(status);
        Button create = primary("完成注册"); page.addView(create, new LinearLayout.LayoutParams(-1, dp(52)));
        create.setOnClickListener(v -> {
            String phoneValue = phone.getText().toString(); String accountValue = account.getText().toString(); String pw = password.getText().toString(); String confirmValue = confirm.getText().toString();
            if (!phoneValue.matches("\\d{11}")) { status.setText("手机号必须是恰好 11 位数字。"); status.setTextColor(ORANGE); return; }
            if (accountValue.length() == 0) { status.setText("账号不能为空，汉字账号也可以使用。"); status.setTextColor(ORANGE); return; }
            if (!validPassword(pw)) { status.setText("密码需为 7-14 位，含小写字母和数字，且不能含汉字。"); status.setTextColor(ORANGE); return; }
            if (!pw.equals(confirmValue)) { status.setText("两次密码输入不一致。"); status.setTextColor(ORANGE); return; }
            JSONObject phoneOwner = findAccountByPhone(phoneValue);
            if (phoneOwner != null) { status.setText("这个手机号已经注册过“" + roleLabel(phoneOwner.optString("role")) + "”账号，一个手机号只能注册一个账号。"); status.setTextColor(ORANGE); return; }
            if (findAccount(role, accountValue) != null) { status.setText("该身份下账号已存在，请直接登录。"); status.setTextColor(ORANGE); return; }
            addAccount(role, phoneValue, accountValue, pw); status.setTextColor(GREEN); Toast.makeText(this, "注册成功，请使用账号密码登录", Toast.LENGTH_SHORT).show(); showAuth(role);
        });
    }

    private boolean validPassword(String value) {
        return value != null && value.length() > 6 && value.length() < 15 && value.matches(".*[a-z].*") && value.matches(".*\\d.*") && !value.matches(".*[\\u4e00-\\u9fff].*");
    }

    private JSONArray accounts() {
        try { return new JSONArray(prefs.getString("accounts_json", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private JSONObject findAccount(String role, String account) {
        JSONArray all = accounts(); for (int i = 0; i < all.length(); i++) { JSONObject item = all.optJSONObject(i); if (item != null && role.equals(item.optString("role")) && account.equals(item.optString("account"))) return item; } return null;
    }

    private JSONObject findAccountByPhone(String phone) {
        JSONArray all = accounts(); for (int i = 0; i < all.length(); i++) { JSONObject item = all.optJSONObject(i); if (item != null && phone.equals(item.optString("phone"))) return item; } return null;
    }

    private void addAccount(String role, String phone, String account, String password) {
        JSONArray all = accounts(); JSONObject item = new JSONObject(); try { item.put("role", role); item.put("phone", phone); item.put("account", account); item.put("password", password); item.put("verified", false); all.put(item); prefs.edit().putString("accounts_json", all.toString()).apply(); } catch (Exception ignored) {}
    }

    private void updateAccountVerified(String role, String account) {
        JSONArray all = accounts(); for (int i = 0; i < all.length(); i++) { JSONObject item = all.optJSONObject(i); if (item != null && role.equals(item.optString("role")) && account.equals(item.optString("account"))) { try { item.put("verified", true); } catch (Exception ignored) {} } } prefs.edit().putString("accounts_json", all.toString()).apply();
    }

    private boolean updateAccountCredentials(String role, String phone, String account, String password) {
        JSONArray all = accounts(); boolean updated = false;
        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);
            if (item != null && role.equals(item.optString("role")) && phone.equals(item.optString("phone"))) {
                try { item.put("account", account); item.put("password", password); updated = true; } catch (Exception ignored) {}
            }
        }
        if (updated) prefs.edit().putString("accounts_json", all.toString()).apply();
        return updated;
    }

    private void loginUser(String role, String account) { prefs.edit().putBoolean("auth_logged_in", true).putString("auth_role", role).putString("auth_account", account).apply(); }

    private void logoutUser() { prefs.edit().remove("auth_logged_in").remove("auth_role").remove("auth_account").apply(); pendingVerificationUri = null; pendingVerificationRole = null; showRoleChooser(); }

    private void routeAfterLogin() {
        String role = prefs.getString("auth_role", ROLE_USER); String account = prefs.getString("auth_account", ""); JSONObject user = findAccount(role, account);
        if (user == null) { logoutUser(); return; }
        if (ROLE_USER.equals(role)) { if (!prefs.getBoolean("profile_complete", false) || prefs.getInt("profile_version", 0) < 3) showOnboarding(); else showApp(0); }
        else if (user.optBoolean("verified", false)) showProfessionalHome(role); else showVerification(role);
    }

    private void showVerification(final String role) {
        authPage = true; professionalHome = false; pendingVerificationRole = role; pendingVerificationUri = null;
        base(); ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); LinearLayout page = column(); pad(page, 22, 18, 22, 28); scroll.addView(page);
        LinearLayout top = row(); TextView back = text("‹", 34, DARK); back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> showAuth(role)); top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(44))); top.addView(title(roleLabel(role) + "身份认证", 21), new LinearLayout.LayoutParams(-2, dp(44))); page.addView(top);
        TextView intro = text(ROLE_DIETITIAN.equals(role) ? "请上传营养师相关证件，系统会自动尝试原图、裁切、放大和增强识别“营养师”等关键词。" : "请上传食品经营许可证，系统会自动尝试原图、裁切、放大和增强识别“食品经营许可证”等关键词。", 13, MUTED); intro.setLineSpacing(0, 1.25f); margin(intro, 42, -2, 0, 17); page.addView(intro);
        LinearLayout upload = card(); upload.addView(title("上传证件图片", 17));
        verificationPreview = new ImageView(this); verificationPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE); verificationPreview.setAdjustViewBounds(true); verificationPreview.setBackground(stroke(Color.WHITE, LINE, 12)); verificationPreview.setVisibility(View.GONE); margin(verificationPreview, 0, 10, 0, 10); upload.addView(verificationPreview, new LinearLayout.LayoutParams(-1, dp(210)));
        Button choose = outline("从相册选择图片"); choose.setOnClickListener(v -> { Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("image/*"); startActivityForResult(intent, PICK_VERIFICATION_IMAGE); }); upload.addView(choose, new LinearLayout.LayoutParams(-1, dp(46)));
        verificationStatus = text("支持拍照和截图。图片只在本机处理，不会上传；若 OCR 仍不完整，会进入人工复核确认。", 11, MUTED); verificationStatus.setLineSpacing(0, 1.25f); margin(verificationStatus, 0, 10, 0, 0); upload.addView(verificationStatus); page.addView(upload); margin(upload, 0, 0, 0, 12);
        verificationSubmit = primary("提交并智能审查"); verificationSubmit.setEnabled(false); page.addView(verificationSubmit, new LinearLayout.LayoutParams(-1, dp(52))); verificationSubmit.setOnClickListener(v -> runVerification(role));
        TextView rule = text(ROLE_DIETITIAN.equals(role) ? "营养师证件示例关键词：营养师、公共营养师、职业技能等级证书。" : "商户证件示例关键词：食品经营许可证、食品经营、许可证。", 11, MUTED); rule.setLineSpacing(0, 1.25f); margin(rule, 4, 15, 4, 0); page.addView(rule);
        Button logout = outline("退出当前账号"); margin(logout, 0, 20, 0, 0); page.addView(logout); logout.setOnClickListener(v -> logoutUser());
    }

    private void runVerification(final String role) {
        if (pendingVerificationUri == null) { Toast.makeText(this, "请先选择证件图片", Toast.LENGTH_SHORT).show(); return; }
        verificationSubmit.setEnabled(false); verificationStatus.setTextColor(MUTED); verificationStatus.setText("正在准备证件图片：会自动裁切、放大和增强后再识别……");
        final List<Bitmap> variants;
        try { variants = buildVerificationVariants(pendingVerificationUri); }
        catch (Exception e) { verificationSubmit.setEnabled(true); verificationStatus.setTextColor(ORANGE); verificationStatus.setText("无法读取图片，请重新选择本地图片。"); return; }
        if (variants.size() == 0) { verificationSubmit.setEnabled(true); verificationStatus.setTextColor(ORANGE); verificationStatus.setText("图片为空或格式不受支持，请重新选择证件图片。"); return; }
        runOcrVariant(role, variants, 0, new StringBuilder());
    }

    private void runOcrVariant(final String role, final List<Bitmap> variants, final int index, final StringBuilder recognizedText) {
        if (index >= variants.size()) { String all = recognizedText.toString(); releaseVerificationVariants(variants); finishVerification(role, all); return; }
        Bitmap bitmap = variants.get(index);
        verificationStatus.setTextColor(MUTED); verificationStatus.setText("正在识别证件区域 " + (index + 1) + "/" + variants.size() + "……");
        final TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        try {
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            recognizer.process(image).addOnSuccessListener(result -> {
                if (result != null && result.getText() != null) recognizedText.append("\n").append(result.getText());
                recognizer.close();
                if (isStrongCertificateMatch(role, recognizedText.toString())) { String all = recognizedText.toString(); releaseVerificationVariants(variants); finishVerification(role, all); }
                else runOcrVariant(role, variants, index + 1, recognizedText);
            }).addOnFailureListener(error -> { recognizer.close(); runOcrVariant(role, variants, index + 1, recognizedText); });
        } catch (Exception e) { recognizer.close(); runOcrVariant(role, variants, index + 1, recognizedText); }
    }

    private List<Bitmap> buildVerificationVariants(Uri uri) throws Exception {
        List<Bitmap> variants = new ArrayList<>(); InputStream input = null; Bitmap decoded = null;
        try {
            input = getContentResolver().openInputStream(uri); decoded = BitmapFactory.decodeStream(input);
        } finally { if (input != null) try { input.close(); } catch (Exception ignored) {} }
        if (decoded == null) return variants;
        Bitmap base = scaleBitmap(decoded, 1500); if (base != decoded) decoded.recycle();
        variants.add(base);
        variants.add(enhanceBitmap(base));
        Bitmap trimmed = cropBitmap(base, 0.03f, 0.03f, 0.97f, 0.97f); variants.add(enhanceBitmap(trimmed)); if (trimmed != base) trimmed.recycle();
        float aspect = base.getHeight() / (float) Math.max(1, base.getWidth());
        if (aspect > 1.35f) {
            Bitmap screenCrop = cropBitmap(base, 0.04f, 0.12f, 0.96f, 0.72f); Bitmap screenScaled = scaleBitmap(screenCrop, 1800); variants.add(enhanceBitmap(screenScaled)); if (screenScaled != screenCrop) screenScaled.recycle(); if (screenCrop != base) screenCrop.recycle();
            Bitmap titleCrop = cropBitmap(base, 0.04f, 0.18f, 0.96f, 0.62f); Bitmap titleScaled = scaleBitmap(titleCrop, 1800); variants.add(enhanceBitmap(titleScaled)); if (titleScaled != titleCrop) titleScaled.recycle(); if (titleCrop != base) titleCrop.recycle();
        }
        return variants;
    }

    private Bitmap scaleBitmap(Bitmap source, int maxDimension) {
        int current = Math.max(source.getWidth(), source.getHeight()); if (current <= maxDimension) return source;
        float ratio = maxDimension / (float) current; return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * ratio)), Math.max(1, Math.round(source.getHeight() * ratio)), true);
    }

    private Bitmap cropBitmap(Bitmap source, float left, float top, float right, float bottom) {
        int x = Math.max(0, Math.min(source.getWidth() - 1, Math.round(source.getWidth() * left))); int y = Math.max(0, Math.min(source.getHeight() - 1, Math.round(source.getHeight() * top)));
        int width = Math.max(1, Math.min(source.getWidth() - x, Math.round(source.getWidth() * (right - left)))); int height = Math.max(1, Math.min(source.getHeight() - y, Math.round(source.getHeight() * (bottom - top))));
        if (x == 0 && y == 0 && width == source.getWidth() && height == source.getHeight()) return source;
        return Bitmap.createBitmap(source, x, y, width, height);
    }

    private Bitmap enhanceBitmap(Bitmap source) {
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(output); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ColorMatrix matrix = new ColorMatrix(); matrix.setSaturation(0f); ColorMatrix contrast = new ColorMatrix(new float[]{1.35f,0,0,0,-35, 0,1.35f,0,0,-35, 0,0,1.35f,0,-35, 0,0,0,1,0}); matrix.postConcat(contrast); paint.setColorFilter(new ColorMatrixColorFilter(matrix)); canvas.drawBitmap(source, 0, 0, paint); return output;
    }

    private void releaseVerificationVariants(List<Bitmap> variants) { for (Bitmap bitmap : variants) if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); }

    private String normalizeOcrText(String value) { return value == null ? "" : value.toLowerCase(Locale.CHINA).replaceAll("[^\\u4e00-\\u9fffA-Za-z0-9]", ""); }

    private boolean isStrongCertificateMatch(String role, String recognized) {
        String t = normalizeOcrText(recognized);
        if (ROLE_DIETITIAN.equals(role)) {
            if (t.contains("营养师") || t.contains("公共营养") || t.contains("dietitian")) return true;
            int score = (t.contains("营养") ? 1 : 0) + (t.contains("职业") ? 1 : 0) + (t.contains("技能") ? 1 : 0) + (t.contains("等级") ? 1 : 0) + (t.contains("证书") ? 1 : 0);
            return score >= 3;
        }
        int score = (t.contains("食品") ? 1 : 0) + (t.contains("经营") ? 1 : 0) + ((t.contains("许可证") || (t.contains("许可") && t.contains("证"))) ? 1 : 0);
        int fields = (t.contains("统一社会信用代码") ? 1 : 0) + (t.contains("法定代表人") || t.contains("经营者") ? 1 : 0) + (t.contains("经营场所") || t.contains("住所") ? 1 : 0) + (t.contains("许可项目") || t.contains("有效期") ? 1 : 0);
        return t.contains("食品经营许可证") || (score >= 3 && fields >= 1);
    }

    private boolean isWeakCertificateMatch(String role, String recognized) {
        String t = normalizeOcrText(recognized);
        if (ROLE_DIETITIAN.equals(role)) return t.contains("营养") || t.contains("职业技能") || t.contains("证书") || t.contains("等级");
        return t.contains("食品") || t.contains("经营") || t.contains("许可证") || t.contains("许可");
    }

    private void finishVerification(String role, String recognized) {
        if (isStrongCertificateMatch(role, recognized)) {
            updateAccountVerified(role, prefs.getString("auth_account", "")); Toast.makeText(this, "证件标题和关键字段匹配，认证通过", Toast.LENGTH_SHORT).show(); showProfessionalHome(role); return;
        }
        verificationSubmit.setEnabled(true);
        if (isWeakCertificateMatch(role, recognized) || recognized.trim().length() == 0) { verificationStatus.setTextColor(ORANGE); verificationStatus.setText("自动 OCR 结果不完整，已进入人工复核确认。"); showManualReview(role); return; }
        verificationStatus.setTextColor(ORANGE); verificationStatus.setText(ROLE_DIETITIAN.equals(role) ? "未识别到营养师证件结构，请确认图片中包含证件标题和完整内容。" : "未识别到食品经营许可证结构，请确认图片中包含证件标题和完整内容。");
    }

    private void showManualReview(final String role) {
        String expected = ROLE_DIETITIAN.equals(role) ? "营养师、公共营养师或职业技能等级证书" : "食品经营许可证";
        new AlertDialog.Builder(this).setTitle("需要人工确认").setMessage("图片文字较小、倾斜或被截图压缩，自动 OCR 没有完整读出证件标题。\n\n请确认你上传的图片中确实有“" + expected + "”标题，并且证件主体清晰。当前为本地演示版，确认后将进入对应端；正式上线应交由后台人工审核。").setNegativeButton("重新上传", null).setPositiveButton("确认证件无误", (dialog, which) -> { updateAccountVerified(role, prefs.getString("auth_account", "")); Toast.makeText(this, "人工复核通过，认证状态已保存", Toast.LENGTH_SHORT).show(); showProfessionalHome(role); }).show();
    }

    private void showProfessionalHome(final String role) {
        authPage = false; professionalHome = true; standalonePage = false; base();
        LinearLayout header = row(); pad(header, 20, 12, 20, 8); root.addView(header, new LinearLayout.LayoutParams(-1, dp(64)));
        LinearLayout brand = row(); TextView mark = text("NF", 12, Color.WHITE); mark.setGravity(Gravity.CENTER); mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD); mark.setBackground(bg(GREEN, 14)); brand.addView(mark, new LinearLayout.LayoutParams(dp(28), dp(28))); brand.addView(title("  营养流", 18)); header.addView(brand, new LinearLayout.LayoutParams(0, -1, 1));
        Button logout = outline("退出"); logout.setOnClickListener(v -> logoutUser()); header.addView(logout, new LinearLayout.LayoutParams(dp(70), dp(42)));
        ScrollView scroll = new ScrollView(this); LinearLayout page = column(); pad(page, 20, 5, 20, 28); scroll.addView(page); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout hero = column(); hero.setBackground(bg(DARK, 20)); pad(hero, 18, 18, 18, 18); TextView over = text(roleLabel(role) + "工作空间 · 已认证", 12, Color.rgb(175, 220, 199)); hero.addView(over); TextView heading = title(ROLE_DIETITIAN.equals(role) ? "让专业建议更可追溯" : "让每一道菜更透明", 24); heading.setTextColor(Color.WHITE); margin(heading, 0, 6, 0, 0); hero.addView(heading); TextView account = text("当前账号：" + prefs.getString("auth_account", ""), 12, Color.rgb(208, 231, 220)); hero.addView(account); page.addView(hero);
        TextView tip = text("认证状态已保存，下次使用同一账号登录无需重复认证。", 12, MUTED); margin(tip, 0, 13, 0, 0); page.addView(tip);
        if (ROLE_DIETITIAN.equals(role)) {
            professionalCard(page, "营养方案审核", "查看用户提交的饮食计划，标记风险项并留下专业意见。", "进入审核工作台");
            professionalCard(page, "异常记录", "按过敏、慢病、能量缺口等维度查看需要关注的用户记录。", "查看异常记录");
            professionalCard(page, "知识库维护", "维护营养标签、食物替换建议与动态算法的规则说明。", "维护知识库");
        } else {
            professionalCard(page, "菜品营养标注", "录入原料、分量、能量和过敏原，让用户点餐前看懂营养信息。", "录入菜品");
            professionalCard(page, "菜单管理", "管理店内菜品、上下架状态和可供外卖端读取的标签。", "管理菜单");
            professionalCard(page, "经营数据", "查看菜品营养标签覆盖率与用户反馈，持续优化菜单。", "查看数据");
        }
        TextView safe = text("当前工作台为本地演示版，后续可接入服务端审核和商户数据。", 11, MUTED); safe.setGravity(Gravity.CENTER); safe.setLineSpacing(0, 1.25f); margin(safe, 10, 18, 10, 0); page.addView(safe);
    }

    private void professionalCard(LinearLayout page, String heading, String description, String actionLabel) {
        LinearLayout item = card(); item.addView(title(heading, 16)); TextView desc = text(description, 12, MUTED); desc.setLineSpacing(0, 1.25f); margin(desc, 0, 5, 0, 9); item.addView(desc); Button action = outline(actionLabel); action.setOnClickListener(v -> Toast.makeText(this, "演示功能：已打开“" + heading + "”", Toast.LENGTH_SHORT).show()); item.addView(action, new LinearLayout.LayoutParams(-1, dp(42))); margin(item, 0, 0, 0, 10); page.addView(item);
    }

    private void showOnboarding() {
        authPage = false; professionalHome = false;
        base();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout page = column(); pad(page, 22, 20, 22, 30); scroll.addView(page);
        LinearLayout brand = row();
        TextView mark = text("NF", 16, Color.WHITE); mark.setGravity(Gravity.CENTER); mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD); mark.setBackground(bg(GREEN, 18));
        brand.addView(mark, new LinearLayout.LayoutParams(dp(36), dp(36))); brand.addView(title("  营养流", 20), new LinearLayout.LayoutParams(-2, dp(36))); page.addView(brand);
        TextView eyebrow = text("AI 动态营养算法 · 新用户设置", 12, GREEN); margin(eyebrow, 0, 28, 0, 8); page.addView(eyebrow);
        TextView heading = title("先告诉我你的饮食习惯", 27); page.addView(heading);
        TextView intro = text("每一项填写栏上方都有中文说明和示例。填写后，推荐会更贴合你的忌口、预算和口味。", 14, MUTED); intro.setLineSpacing(0, 1.25f); margin(intro, 0, 8, 0, 18); page.addView(intro);

        LinearLayout profile = card();
        profile.addView(title("一、基础信息", 17));
        final EditText age = field("年龄", "请输入年龄，例如：20"); age.setInputType(2); addLabeled(profile, "年龄（岁）", "用于估算每日能量需求", age, 10);
        final EditText height = field("身高", "请输入身高，例如：170"); height.setInputType(2); addLabeled(profile, "身高（厘米）", "用于估算体重和食量范围", height, 10);
        final EditText weight = field("体重", "请输入体重，例如：60"); weight.setInputType(2); addLabeled(profile, "体重（千克）", "用于估算每日蛋白质和能量范围", weight, 0);
        page.addView(profile, new LinearLayout.LayoutParams(-1, -2)); margin(profile, 0, 0, 0, 14);

        LinearLayout prefsCard = card();
        prefsCard.addView(title("二、这顿饭和你的偏好", 17));
        TextView hint = text("请尽量填写完整；没有忌口请填写“无”。", 11, MUTED); margin(hint, 0, 3, 0, 12); prefsCard.addView(hint);
        EditText allergy = field("忌口/过敏食物", "例如：花生、香菜、乳糖不耐；没有就填“无”"); addLabeled(prefsCard, "忌口 / 过敏食物（必填）", "可写多个，用顿号分隔；这是安全检查的依据", allergy, 10);
        EditText budget = field("本餐预算", "例如：25（元）"); budget.setInputType(2); addLabeled(prefsCard, "本餐预算（元，必填）", "填写你愿意为这一顿饭支付的金额", budget, 10);
        EditText taste = field("口味偏好", "例如：喜欢吃辣、偏甜、不喜欢香菜"); addLabeled(prefsCard, "口味偏好（必填）", "可以直接写自然语言：喜欢吃辣、喜欢甜、清淡少油等", taste, 10);
        Spinner mealType = spinner(new String[]{"早餐", "午餐", "晚餐", "加餐"}, "餐次"); addLabeled(prefsCard, "这顿饭是？", "先选择餐次，AI 会使用对应的正常食量范围", mealType, 10);
        EditText grams = field("本餐食量", "例如：500"); grams.setInputType(2); addLabeled(prefsCard, "这顿饭吃多少克？（必填）", "按所有食物总重量填写，饮料可单独备注", grams, 0);
        TextView portion = text("", 12, GREEN); portion.setBackground(bg(MINT, 10)); pad(portion, 11, 9, 11, 9); margin(portion, 0, 9, 0, 0); prefsCard.addView(portion);
        page.addView(prefsCard, new LinearLayout.LayoutParams(-1, -2)); margin(prefsCard, 0, 0, 0, 16);
        mealType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() { public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { portion.setText(portionHint(mealType.getSelectedItem().toString(), grams.getText().toString())); } public void onNothingSelected(android.widget.AdapterView<?> p) {} });
        grams.addTextChangedListener(new android.text.TextWatcher() { public void beforeTextChanged(CharSequence s, int st, int c, int a) {} public void onTextChanged(CharSequence s, int st, int b, int c) { portion.setText(portionHint(mealType.getSelectedItem().toString(), s.toString())); } public void afterTextChanged(android.text.Editable e) {} });
        portion.setText(portionHint("早餐", ""));

        Button save = primary("保存设置，生成今日建议"); page.addView(save, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView medical = text("营养流只提供日常饮食建议，不替代医生诊疗。涉及疾病、用药、孕期或严重过敏时，请咨询专业人士。", 11, MUTED); medical.setGravity(Gravity.CENTER); medical.setLineSpacing(0, 1.25f); margin(medical, 10, 13, 10, 0); page.addView(medical);
        save.setOnClickListener(v -> {
            
            if (age.getText().length() == 0 || height.getText().length() == 0 || weight.getText().length() == 0 || allergy.getText().length() == 0 || budget.getText().length() == 0 || taste.getText().length() == 0 || grams.getText().length() == 0) { Toast.makeText(this, "请按上方中文说明补完整信息；没有忌口请填写“无”", Toast.LENGTH_LONG).show(); return; }
            try { if (Double.parseDouble(budget.getText().toString()) <= 0 || Double.parseDouble(grams.getText().toString()) <= 0) throw new NumberFormatException(); } catch (NumberFormatException e) { Toast.makeText(this, "预算和克数请输入大于 0 的数字，例如 25、500", Toast.LENGTH_SHORT).show(); return; }
            saveProfileV3(age.getText().toString(), height.getText().toString(), weight.getText().toString(), allergy.getText().toString(), budget.getText().toString(), taste.getText().toString(), mealType.getSelectedItem().toString(), grams.getText().toString());
            showApp(0);
        });
    }

    private void addLabeled(LinearLayout parent, String label, String helper, View input, int bottom) {
        TextView title = title(label, 14); parent.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView help = text(helper, 11, MUTED); margin(help, 0, 2, 0, 5); parent.addView(help);
        margin(input, 0, 0, 0, bottom); parent.addView(input);
    }

    private String portionHint(String type, String entered) {
        String range = "早餐".equals(type) ? "300-500 克" : "午餐".equals(type) ? "450-700 克" : "晚餐".equals(type) ? "350-600 克" : "150-300 克";
        if (entered == null || entered.trim().length() == 0) return "AI 建议范围：" + range + "，大约是 7-8 分饱；实际还要看食物密度。";
        try { double g = Double.parseDouble(entered.trim()); double low = "早餐".equals(type) ? 300 : "午餐".equals(type) ? 450 : "晚餐".equals(type) ? 350 : 150; double high = "早餐".equals(type) ? 500 : "午餐".equals(type) ? 700 : "晚餐".equals(type) ? 600 : 300; if (g < low) return "AI 建议范围：" + range + "；当前约 " + Math.round(g) + " 克，可能只有 4-6 分饱。"; if (g > high) return "AI 建议范围：" + range + "；当前约 " + Math.round(g) + " 克，可能超过 8 分饱。"; return "AI 建议范围：" + range + "；当前约 " + Math.round(g) + " 克，约 7-8 分饱。"; } catch (NumberFormatException e) { return "AI 建议范围：" + range + "，请输入数字克数后查看饱腹度提示。"; }
    }    private EditText field(String label, String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setTextSize(14); e.setTextColor(DARK); e.setHintTextColor(Color.rgb(156, 172, 164)); e.setSingleLine(true); e.setBackground(stroke(Color.WHITE, LINE, 12)); pad(e, 13, 0, 13, 0); e.setContentDescription(label); return e;
    }
    private Spinner spinner(String[] values, String label) {
        Spinner s = new Spinner(this); ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) { @Override public View getView(int p, View c, android.view.ViewGroup parent) { TextView t = (TextView) super.getView(p,c,parent); t.setTextSize(14); t.setTextColor(DARK); t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(dp(13),0,dp(8),0); return t; } }; a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(a); s.setBackground(stroke(Color.WHITE, LINE, 12)); s.setContentDescription(label); s.setMinimumHeight(dp(54)); return s;
    }
    private Button primary(String label) { Button b = new Button(this); b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(15); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(bg(GREEN, 13)); b.setMinHeight(dp(52)); return b; }
    private Button outline(String label) { Button b = new Button(this); b.setText(label); b.setTextColor(GREEN); b.setTextSize(13); b.setAllCaps(false); b.setBackground(stroke(Color.WHITE, GREEN, 12)); b.setMinHeight(dp(42)); return b; }

    private void saveProfile(String age, String height, String weight, String goal, String activity, String taste, String budget, String allergy) {
        prefs.edit().putBoolean("profile_complete", true).putString("age", age).putString("height", height).putString("weight", weight).putString("goal", goal).putString("activity", activity).putString("taste", taste).putString("budget", budget).putString("allergy", allergy).apply();
    }
    private void saveProfileV3(String age, String height, String weight, String allergy, String budget, String taste, String type, String grams) { prefs.edit().putBoolean("profile_complete", true).putInt("profile_version", 3).putString("age", age).putString("height", height).putString("weight", weight).putString("allergy", allergy).putString("budget", budget).putString("taste", taste).putString("meal_type", type).putString("meal_grams", grams).apply(); }
    private String p(String key, String fallback) { return prefs.getString(key, fallback); }
    private double budgetValue() {
        String raw = p("budget", "25");
        String cleaned = raw.replace("元", "").replace("¥", "")
                .replaceAll("[^0-9.-]", "").trim();
        if (cleaned.length() == 0) return raw.contains("弹性") ? 50.0 : 25.0;
        String[] values = cleaned.split("-");
        try { return Double.parseDouble(values[values.length - 1]); }
        catch (NumberFormatException e) { return 25.0; }
    }

    private void showApp(int pageIndex) {
        authPage = false; professionalHome = false;
        currentPage = pageIndex; standalonePage = false; base();
        LinearLayout header = row(); pad(header, 20, 14, 20, 10); root.addView(header, new LinearLayout.LayoutParams(-1, dp(64)));
        LinearLayout brand = row(); TextView mark = text("NF", 12, Color.WHITE); mark.setGravity(Gravity.CENTER); mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD); mark.setBackground(bg(GREEN, 14)); brand.addView(mark, new LinearLayout.LayoutParams(dp(28), dp(28))); TextView bn = title("  营养流", 18); brand.addView(bn); header.addView(brand, new LinearLayout.LayoutParams(0, -1, 1));
        TextView sync = text("●  已同步", 11, GREEN); header.addView(sync, new LinearLayout.LayoutParams(-2, -1));
        ScrollView scroll = new ScrollView(this); content = column(); pad(content, 20, 5, 20, 26); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (pageIndex == 0) home(); else if (pageIndex == 1) recommendations(); else if (pageIndex == 2) records(); else profile();
        bottomNav();
    }

    private void bottomNav() {
        LinearLayout nav = row(); nav.setGravity(Gravity.CENTER); nav.setBackgroundColor(Color.WHITE); nav.setPadding(0, dp(7), 0, dp(8)); root.addView(nav, new LinearLayout.LayoutParams(-1, dp(65)));
        String[] labels = {"首页", "推荐", "记录", "我的"};
        for (int i=0;i<labels.length;i++) { final int index=i; TextView item = text(labels[i], 12, i==currentPage ? GREEN : MUTED); item.setGravity(Gravity.CENTER); if (i==currentPage) item.setTypeface(Typeface.DEFAULT, Typeface.BOLD); item.setOnClickListener(v -> showApp(index)); nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1)); }
    }

    private TextView section(String value) { TextView t = title(value, 19); margin(t, 0, 20, 0, 10); content.addView(t); return t; }
    private TextView small(String value) { return text(value, 12, MUTED); }
    private void add(View v, int top, int bottom) { margin(v, 0, top, 0, bottom); content.addView(v); }
    private LinearLayout card() { LinearLayout c=column(); c.setBackground(stroke(Color.WHITE, LINE, 16)); pad(c, 16, 15, 16, 15); return c; }

    private void home() {
        String goal=p("goal","均衡饮食");
        LinearLayout welcome = column(); welcome.setBackground(bg(DARK, 20)); pad(welcome, 18, 18, 18, 18);
        TextView over = text("今天 · 9 月 10 日  星期四", 12, Color.rgb(175, 220, 199)); welcome.addView(over);
        TextView wh = title("把健康，放进下一单", 24); wh.setTextColor(Color.WHITE); margin(wh,0,6,0,0); welcome.addView(wh);
        TextView ws = text("目标："+goal+"    ·    算法已根据你的画像更新", 12, Color.rgb(208, 231, 220)); welcome.addView(ws);
        LinearLayout stat = row(); margin(stat,0,18,0,0); welcome.addView(stat);
        TextView score = title("82", 30); score.setTextColor(Color.WHITE); stat.addView(score, new LinearLayout.LayoutParams(dp(52),dp(40))); TextView st = text("今日营养分\n较昨日 +8", 11, Color.rgb(208,231,220)); st.setLineSpacing(0,1.2f); stat.addView(st,new LinearLayout.LayoutParams(0,dp(40),1)); Button adjust=outline("调整画像"); adjust.setTextColor(Color.WHITE); adjust.setBackground(stroke(Color.TRANSPARENT, Color.rgb(120,190,162), 12)); adjust.setOnClickListener(v -> showOnboarding()); stat.addView(adjust,new LinearLayout.LayoutParams(dp(92),dp(42))); add(welcome, 4, 0);
        section("外卖应用");
        TextView appHint = text("已读取手机中可用的外卖应用；点击图标可以打开对应 App。", 12, MUTED); margin(appHint, 0, -4, 0, 9); content.addView(appHint);
        add(deliveryShortcutCard(), 0, 0);
        section("为你推荐");
        TextView explain = text("结合营养匹配、口味、预算与配送时效的动态结果", 12, MUTED); margin(explain,0,-4,0,10); content.addView(explain);
        mealCard("青柠鸡胸能量碗", "高蛋白  ·  低油  ·  4.8 km", "¥28", "468 kcal", "蛋白质 36 g", "匹配 96%", true);
        mealCard("番茄虾仁荞麦面", "高纤维  ·  少盐  ·  3.2 km", "¥24", "412 kcal", "蛋白质 27 g", "匹配 91%", false);
        Button more=outline("查看全部推荐  →"); more.setOnClickListener(v -> showApp(1)); add(more, 10, 0);
        section("今日营养进度");
        LinearLayout nutrition=card(); content.addView(nutrition);
        TextView nt=text("已记录 1 餐",12,GREEN); nt.setTypeface(Typeface.DEFAULT,Typeface.BOLD); nutrition.addView(nt);
        progressRow(nutrition,"能量","612 / 1,850 kcal",0.33f,GREEN); progressRow(nutrition,"蛋白质","36 / 92 g",0.39f,Color.rgb(84,152,218)); progressRow(nutrition,"蔬菜","1 / 3 份",0.34f,Color.rgb(244,161,69));
        section("需要留意");
        LinearLayout alert=card(); LinearLayout ar=row(); TextView icon=text("!",16,Color.WHITE);icon.setGravity(Gravity.CENTER);icon.setBackground(bg(Color.rgb(218,111,71),15));ar.addView(icon,new LinearLayout.LayoutParams(dp(30),dp(30))); TextView at=title("本周外卖偏咸",14); margin(at,0,0,0,0); ar.addView(at,new LinearLayout.LayoutParams(0,dp(30),1)); alert.addView(ar); TextView ad=text("最近 3 次记录的钠摄入偏高，下一餐优先选择少盐并补充一份蔬菜。",12,MUTED);ad.setLineSpacing(0,1.25f);margin(ad,0,10,0,0);alert.addView(ad); add(alert,0,0);
        section("今天的小任务");
        taskRow("完成 2 餐蔬菜搭配", "1 / 2", false); taskRow("饭后步行 15 分钟", "待完成", true);
        TextView safe=text("健康建议仅供日常管理参考，不替代医生诊疗。",11,MUTED); safe.setGravity(Gravity.CENTER); add(safe,22,0);
    }

    private void mealCard(String name, String tags, String price, String kcal, String protein, String match, boolean featured) {
        final String cleanPrice = price.replace("¥", "").trim();
        final String cleanKcal = kcal.replace(" kcal", "").trim();
        final String cleanProtein = protein.replace("蛋白质", "").replace("g", "").trim();
        final String cleanMatch = match.replace("匹配", "").replace("%", "").trim();
        final LinearLayout c = card(); if (featured) c.setBackground(stroke(MINT, Color.rgb(183, 225, 207), 16)); c.setClickable(true);
        c.setOnClickListener(v -> showMealAnalysis(name, tags, parseDouble(cleanPrice, 25), parseInt(cleanKcal, 450), parseInt(cleanProtein, 25)));
        LinearLayout top = row(); top.addView(title(name, 16), new LinearLayout.LayoutParams(0, dp(28), 1)); TextView m = text("匹配 " + cleanMatch + "%", 12, GREEN); m.setTypeface(Typeface.DEFAULT, Typeface.BOLD); top.addView(m, new LinearLayout.LayoutParams(-2, dp(28))); c.addView(top);
        TextView tg = text(tags + " · 点击查看营养分析", 12, MUTED); margin(tg, 0, 2, 0, 10); c.addView(tg);
        LinearLayout stats = row(); stats.addView(text(kcal, 13, DARK), new LinearLayout.LayoutParams(0, dp(30), 1)); stats.addView(text(protein, 13, DARK), new LinearLayout.LayoutParams(0, dp(30), 1)); TextView p = text(price, 14, GREEN); p.setTypeface(Typeface.DEFAULT, Typeface.BOLD); stats.addView(p, new LinearLayout.LayoutParams(dp(60), dp(30))); c.addView(stats);
        LinearLayout buttons = row(); Button analyze = outline("营养分析"); analyze.setTextSize(12); analyze.setOnClickListener(v -> showMealAnalysis(name, tags, parseDouble(cleanPrice, 25), parseInt(cleanKcal, 450), parseInt(cleanProtein, 25))); buttons.addView(analyze, new LinearLayout.LayoutParams(0, dp(40), 1)); Button eat = primary("记录已吃"); eat.setTextSize(12); eat.setMinHeight(dp(38)); margin(eat, 8, 0, 0, 0); eat.setOnClickListener(v -> { String meal = p("meal_type", "午餐"); NutritionLogStore.append(this, NutritionLogStore.todayKey(), meal, name, 500, parseDouble(cleanPrice, 25), "NutriFlow推荐"); prefs.edit().putInt("meals", prefs.getInt("meals", 0) + 1).apply(); eat.setText("已记录 ✓"); eat.setEnabled(false); Toast.makeText(this, "已按 500 克加入今日" + meal + "日志", Toast.LENGTH_SHORT).show(); }); buttons.addView(eat, new LinearLayout.LayoutParams(0, dp(40), 1)); margin(buttons, 0, 8, 0, 0); c.addView(buttons);
        add(c, 0, 10);
    }
    private double parseDouble(String value, double fallback) { try { return Double.parseDouble(value); } catch (NumberFormatException e) { return fallback; } }
    private int parseInt(String value, int fallback) { try { return Integer.parseInt(value); } catch (NumberFormatException e) { return fallback; } }
    private void progressRow(LinearLayout parent,String label,String value,float amount,int color){ LinearLayout line=row(); margin(line,0,12,0,0); TextView l=text(label,12,MUTED);line.addView(l,new LinearLayout.LayoutParams(dp(54),dp(24))); TextView val=text(value,12,DARK);val.setGravity(Gravity.RIGHT);line.addView(val,new LinearLayout.LayoutParams(0,dp(24),1));parent.addView(line); android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress((int)(amount*100));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(235,241,237))); parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(7))); }
    private void taskRow(String label,String status,boolean done){ LinearLayout r=row();r.setBackgroundColor(Color.TRANSPARENT);TextView box=text(done?"✓":"○",18,done?GREEN:MUTED);r.addView(box,new LinearLayout.LayoutParams(dp(30),dp(34)));TextView l=text(label,13,DARK);r.addView(l,new LinearLayout.LayoutParams(0,dp(34),1));TextView s=text(status,12,done?GREEN:MUTED);r.addView(s);add(r,0,2); }

    private void recommendations() {
        section("今日推荐清单"); TextView intro=text("你的偏好："+p("taste","均衡口味")+"  ·  "+p("budget","¥20-35"),12,MUTED);margin(intro,0,-4,0,14);content.addView(intro);
        LinearLayout chips=row(); String[] cs={"全部","高蛋白","低油少盐","预算友好"}; for(String c:cs){TextView chip=text(c,12,c.equals("全部")?Color.WHITE:GREEN);chip.setGravity(Gravity.CENTER);chip.setBackground(bg(c.equals("全部")?GREEN:Color.WHITE,18));chip.setPadding(dp(13),0,dp(13),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(34));lp.setMargins(0,0,dp(8),0);chips.addView(chip,lp);}content.addView(chips);
        mealCard("青柠鸡胸能量碗","高蛋白 · 低油 · 预计 28 分钟","¥28","468 kcal","蛋白质 36 g","匹配 96%",true);
        mealCard("番茄虾仁荞麦面","高纤维 · 少盐 · 预计 25 分钟","¥24","412 kcal","蛋白质 27 g","匹配 91%",false);
        mealCard("菌菇豆腐杂粮饭","植物蛋白 · 高纤维 · 预计 32 分钟","¥22","506 kcal","蛋白质 24 g","匹配 88%",false);
        mealCard("低脂牛肉蔬菜卷","铁元素 · 蔬菜充足 · 预计 30 分钟","¥31","438 kcal","蛋白质 31 g","匹配 86%",false);
        TextView why=title("为什么推荐给你？",17);add(why,18,8); LinearLayout explain=card();explain.addView(text("算法把营养匹配、口味匹配、预算、时效和历史反馈综合成匹配分。每张卡片都标注了主要理由，方便你做决定。",13,DARK));add(explain,0,0);
    }

    private void records() {
        final String date = selectedLogDate == null ? NutritionLogStore.todayKey() : selectedLogDate;
        section("营养日志");
        LinearLayout dateBar = row();
        Button dateButton = outline("日期：" + NutritionLogStore.displayDate(date) + "  ▾");
        dateButton.setContentDescription("选择查看日期");
        dateButton.setOnClickListener(v -> {
            Calendar cal = NutritionLogStore.calendar(date);
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(Calendar.YEAR, year);
                chosen.set(Calendar.MONTH, month);
                chosen.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                selectedLogDate = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(chosen.getTime());
                showApp(2);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        dateBar.addView(dateButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button today = outline("回到今天");
        today.setOnClickListener(v -> { selectedLogDate = NutritionLogStore.todayKey(); showApp(2); });
        margin(today, 8, 0, 0, 0);
        dateBar.addView(today, new LinearLayout.LayoutParams(dp(86), dp(44)));
        add(dateBar, 0, 8);

        TextView tip = text("默认每餐按 500 克估算；点击“修改食用量”后，油、盐、糖、蛋白质、膳食纤维和能量会按克数重新计算。", 11, MUTED);
        tip.setLineSpacing(0, 1.25f);
        add(tip, 0, 10);

        NutritionLogStore.Metrics total = NutritionLogStore.daily(this, date);
        LinearLayout summary = card();
        TextView summaryTitle = title(NutritionLogStore.displayDate(date) + " · 当日摄入（估算）", 15);
        summary.addView(summaryTitle);
        if (total.grams <= 0) {
            TextView empty = text("未录入信息\n这一天没有读取到外卖点单记录，请先在外卖 App 中点击餐品并记录，或手动录入餐次。", 12, MUTED);
            empty.setLineSpacing(0, 1.25f);
            margin(empty, 0, 8, 0, 4);
            summary.addView(empty);
        } else {
            double progress = Math.min(1.0, total.energy / 1850.0);
            progressRow(summary, "能量", String.format(Locale.CHINA, "%.0f / 1,850 kcal", total.energy), (float) progress, GREEN);
            summary.addView(text(String.format(Locale.CHINA, "油 %.1f g  ·  盐 %.1f g  ·  糖 %.1f g", total.oil, total.salt, total.sugar), 12, DARK));
            margin(summary.getChildAt(summary.getChildCount() - 1), 0, 8, 0, 0);
            summary.addView(text(String.format(Locale.CHINA, "蛋白质 %.1f g  ·  膳食纤维 %.1f g", total.protein, total.fiber), 12, DARK));
        }
        add(summary, 0, 12);

        section("早餐 / 午餐 / 晚餐");
        addLogMeal(date, "早餐");
        addLogMeal(date, "午餐");
        addLogMeal(date, "晚餐");
        if (NutritionLogStore.exists(this, date, "加餐")) addLogMeal(date, "加餐");

        TextView sourceTip = text("跨应用记录只在你主动开启“营养流跨应用分析”并点击外卖餐品后写入；平台没有提供可直接读取的历史订单数据库时，未读取到的日期会保留为“未录入信息”。", 11, MUTED);
        sourceTip.setLineSpacing(0, 1.25f);
        add(sourceTip, 10, 0);
    }

    private void addLogMeal(final String date, final String meal) {
        final boolean exists = NutritionLogStore.exists(this, date, meal);
        LinearLayout box = card();
        LinearLayout heading = row();
        heading.addView(title(meal, 16), new LinearLayout.LayoutParams(0, dp(28), 1));
        heading.addView(text(exists ? "已录入" : "未录入信息", 11, exists ? GREEN : MUTED), new LinearLayout.LayoutParams(-2, dp(28)));
        box.addView(heading);
        if (!exists) {
            TextView empty = text("尚未读取到该餐次的外卖点单记录。默认食用量为 500 克，你也可以手动录入。", 12, MUTED);
            empty.setLineSpacing(0, 1.2f);
            margin(empty, 0, 4, 0, 8);
            box.addView(empty);
            Button addButton = outline("手动录入 " + meal);
            addButton.setOnClickListener(v -> editMealLog(date, meal));
            box.addView(addButton, new LinearLayout.LayoutParams(-1, dp(42)));
        } else {
            double grams = NutritionLogStore.grams(this, date, meal);
            NutritionLogStore.Metrics m = NutritionLogStore.metrics(grams);
            TextView detail = text(NutritionLogStore.name(this, date, meal) + "  ·  " + Math.round(grams) + " 克  ·  来源：" + NutritionLogStore.source(this, date, meal), 12, DARK);
            detail.setLineSpacing(0, 1.15f);
            margin(detail, 0, 4, 0, 5);
            box.addView(detail);
            TextView metrics = text(String.format(Locale.CHINA, "能量 %.0f kcal  ·  油 %.1f g  ·  盐 %.1f g  ·  糖 %.1f g\n蛋白质 %.1f g  ·  膳食纤维 %.1f g", m.energy, m.oil, m.salt, m.sugar, m.protein, m.fiber), 12, GREEN);
            metrics.setLineSpacing(0, 1.2f);
            margin(metrics, 0, 0, 0, 7);
            box.addView(metrics);
            Button edit = outline("修改食用量（重新计算）");
            edit.setOnClickListener(v -> editMealLog(date, meal));
            box.addView(edit, new LinearLayout.LayoutParams(-1, dp(42)));
        }
        add(box, 0, 8);
    }

    private void editMealLog(final String date, final String meal) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = column();
        sheet.setBackground(bg(Color.WHITE, 20));
        pad(sheet, 18, 16, 18, 18);
        LinearLayout header = row();
        header.addView(title((NutritionLogStore.exists(this, date, meal) ? "修改" : "录入") + meal + "记录", 18), new LinearLayout.LayoutParams(0, dp(36), 1));
        TextView close = text("×", 28, MUTED);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(38), dp(38)));
        sheet.addView(header);
        TextView hint = text("默认食用量为 500 克，保存后当天营养指标会立即刷新。", 12, MUTED);
        hint.setLineSpacing(0, 1.2f);
        margin(hint, 0, 2, 0, 10);
        sheet.addView(hint);
        EditText name = field("餐品名称", "例如：鸡胸肉沙拉");
        if (NutritionLogStore.exists(this, date, meal)) name.setText(NutritionLogStore.name(this, date, meal));
        sheet.addView(name, new LinearLayout.LayoutParams(-1, dp(54)));
        margin(name, 0, 0, 0, 9);
        EditText grams = field("食用量（克）", "默认 500");
        grams.setInputType(2 | 8192);
        grams.setText(NutritionLogStore.exists(this, date, meal) ? String.valueOf(Math.round(NutritionLogStore.grams(this, date, meal))) : "500");
        sheet.addView(grams, new LinearLayout.LayoutParams(-1, dp(54)));
        LinearLayout actions = row();
        Button cancel = outline("取消");
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button save = primary("保存并重新计算");
        margin(save, 8, 0, 0, 0);
        save.setOnClickListener(v -> {
            double value = parseDouble(grams.getText().toString().trim(), 500);
            if (value <= 0 || value > 10000) {
                Toast.makeText(this, "食用量请输入 1～10000 克", Toast.LENGTH_SHORT).show();
                return;
            }
            String food = name.getText().toString().trim();
            if (food.length() == 0) food = "外卖餐品";
            NutritionLogStore.save(this, date, meal, food, value, NutritionLogStore.price(this, date, meal), "手动记录");
            dialog.dismiss();
            selectedLogDate = date;
            showApp(2);
        });
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));
        margin(actions, 0, 12, 0, 0);
        sheet.addView(actions);
        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.CENTER);
        }
        dialog.setOnShowListener(d -> {
            Window shown = dialog.getWindow();
            if (shown != null) shown.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92f), -2);
        });
        dialog.show();
    }
    private void logRow(String meal,String food,String kcal,String time){LinearLayout r=row();r.setBackgroundColor(Color.WHITE);pad(r,13,11,13,11);LinearLayout left=column();TextView a=title(meal,13);left.addView(a);TextView b=text(food,12,MUTED);margin(b,3,0,0,0);left.addView(b);r.addView(left,new LinearLayout.LayoutParams(0,-2,1));LinearLayout right=column();right.setGravity(Gravity.RIGHT);TextView k=text(kcal,13,GREEN);k.setTypeface(Typeface.DEFAULT,Typeface.BOLD);right.addView(k);right.addView(text(time,11,MUTED));r.addView(right);add(r,0,6);}

    private void profile() { section("我的画像"); TextView intro=text("画像会影响推荐结果，你可以随时调整。",13,MUTED);margin(intro,0,-3,0,14);content.addView(intro);LinearLayout info=card();info.addView(pair("健康目标",p("goal","均衡饮食")));info.addView(pair("活动水平",p("activity","日常活动较少")));info.addView(pair("口味偏好",p("taste","均衡口味")));info.addView(pair("单餐预算",p("budget","¥20-35")));info.addView(pair("过敏/忌口",p("allergy","暂未填写")));add(info,0,0);Button edit=primary("编辑营养画像");edit.setOnClickListener(v -> showOnboarding());add(edit,12,0);
        section("三方协同入口"); LinearLayout collab=card();collab.addView(collabRow("商家菜品营养标注","录入原料与营养标签，帮助用户做出更透明的选择"));collab.addView(collabRow("营养师审核工作台","审核规则、查看异常并留下可追溯的专业意见"));add(collab,0,0);
        section("账号与安全"); LinearLayout accountCard=card(); accountCard.addView(text("当前登录：" + prefs.getString("auth_account", "普通用户"), 13, DARK)); TextView local=text("账号资料保存在本机；退出后可从入口选择其他身份登录。", 11, MUTED); local.setLineSpacing(0, 1.25f); margin(local, 4, 7, 0, 9); accountCard.addView(local); Button logout=outline("退出登录"); logout.setOnClickListener(v -> logoutUser()); accountCard.addView(logout, new LinearLayout.LayoutParams(-1, dp(42))); add(accountCard,0,0);
        section("关于与安全"); LinearLayout about=card();about.addView(text("营养流 NutriFlow  ·  MVP 0.1",13,DARK));TextView ab=text("建议由动态算法生成，涉及慢病、用药、孕期等高风险情况时，请及时咨询专业医生。",12,MUTED);ab.setLineSpacing(0,1.25f);margin(ab,6,8,0,0);about.addView(ab);add(about,0,0);
    }
    private TextView pair(String k,String v){TextView t=text(k+"\n"+v,13,DARK);t.setLineSpacing(0,1.25f);pad(t,0,10,0,10);t.setCompoundDrawablesWithIntrinsicBounds(0,0,0,0);return t;}
    private LinearLayout collabRow(String a,String b){LinearLayout r=column();TextView x=title(a,14);r.addView(x);TextView y=text(b,12,MUTED);margin(y,4,0,0,10);r.addView(y);return r;}

    private LinearLayout deliveryShortcutCard() {
        LinearLayout card = card(); LinearLayout apps = row(); int count = 0;
        for (final DeliveryApp app : deliveryApps) {
            if (!app.added || !app.installed) continue;
            LinearLayout item = column(); item.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(this); icon.setImageDrawable(app.icon); item.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
            TextView name = text(app.label, 11, DARK); name.setGravity(Gravity.CENTER); item.addView(name, new LinearLayout.LayoutParams(dp(68), dp(28)));
            item.setOnClickListener(v -> openDeliveryApp(app)); apps.addView(item, new LinearLayout.LayoutParams(0, dp(78), 1)); count++;
        }
        if (count == 0) apps.addView(text("还没有添加外卖应用，点击右侧按钮开始添加", 12, MUTED), new LinearLayout.LayoutParams(0, dp(50), 1));
        Button manage = outline(count == 0 ? "添加应用" : "管理"); manage.setOnClickListener(v -> showAppsPage()); apps.addView(manage, new LinearLayout.LayoutParams(dp(76), dp(44)));
        card.addView(apps); return card;
    }

    private void showAppsPage() {
        refreshInstalledApps(); standalonePage = true; base(); LinearLayout header = row(); pad(header, 20, 12, 20, 8);
        TextView back = text("‹", 34, DARK); back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> showApp(0)); header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(44))); header.addView(title("外卖应用", 20), new LinearLayout.LayoutParams(-2, dp(44))); root.addView(header, new LinearLayout.LayoutParams(-1, dp(64)));
        ScrollView scroll = new ScrollView(this); LinearLayout page = column(); pad(page, 20, 5, 20, 25); scroll.addView(page); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView intro = text("营养流会读取可见的手机应用列表，只展示外卖相关应用。添加后可以从首页图标直接打开。", 13, MUTED);
        intro.setLineSpacing(0, 1.25f);
        margin(intro, 0, 8, 0, 10);
        page.addView(intro);
        LinearLayout access = card();
        access.addView(title("跨应用餐品分析", 15));
        TextView accessText = text(isAccessibilityEnabled() ? "已开启：在美团、饿了么、淘宝闪购或京东外卖中点击餐品，会弹出营养分析并可记录到对应餐次。" : "尚未开启。Android 需要你在系统设置中主动授权，营养流才可以在外卖 App 的餐品页面显示分析。", 12, MUTED);
        accessText.setLineSpacing(0, 1.25f);
        margin(accessText, 0, 4, 0, 8);
        access.addView(accessText);
        Button accessButton = primary(isAccessibilityEnabled() ? "打开系统设置查看权限" : "开启跨应用分析权限");
        accessButton.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Exception e) { Toast.makeText(this, "无法打开系统无障碍设置，请在手机设置中搜索“无障碍”", Toast.LENGTH_LONG).show(); }
        });
        access.addView(accessButton);
        boolean analysisEnabled = prefs.getBoolean(MealAccessibilityService.PREF_ANALYSIS_ENABLED, true);
        Button pauseButton = outline(analysisEnabled ? "暂停餐品分析" : "恢复餐品分析");
        margin(pauseButton, 0, 8, 0, 0);
        pauseButton.setOnClickListener(v -> {
            boolean currentlyEnabled = prefs.getBoolean(MealAccessibilityService.PREF_ANALYSIS_ENABLED, true);
            prefs.edit().putBoolean(MealAccessibilityService.PREF_ANALYSIS_ENABLED, !currentlyEnabled).apply();
            showAppsPage();
        });
        access.addView(pauseButton);
        crossAppStatusText = accessText;
        crossAppSettingsButton = accessButton;
        crossAppPauseButton = pauseButton;
        updateCrossAppStatus();
        margin(access, 0, 0, 0, 12);
        page.addView(access);
        for (final DeliveryApp app : deliveryApps) {
            LinearLayout c = card(); LinearLayout line = row(); ImageView icon = new ImageView(this); icon.setImageDrawable(app.icon); line.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));
            LinearLayout names = column(); names.addView(title(app.label, 15)); names.addView(text(app.installed ? "已安装 · 可直接打开" : "未检测到安装", 11, app.installed ? GREEN : MUTED)); margin(names, 10, 0, 6, 0); line.addView(names, new LinearLayout.LayoutParams(0, dp(50), 1));
            Button add = outline(app.added ? "已添加" : "添加"); add.setEnabled(app.installed || app.added); add.setOnClickListener(v -> { prefs.edit().putBoolean("app_added_" + app.packageName, !app.added).apply(); showAppsPage(); }); line.addView(add, new LinearLayout.LayoutParams(dp(72), dp(40)));
            Button open = outline("打开"); open.setEnabled(app.installed); margin(open, 7, 0, 0, 0); open.setOnClickListener(v -> openDeliveryApp(app)); line.addView(open, new LinearLayout.LayoutParams(dp(72), dp(40))); c.addView(line); margin(c, 0, 0, 0, 9); page.addView(c);
        }
        LinearLayout demo = card(); demo.addView(title("没有安装外卖 App？先体验分析演示", 15)); TextView d = text("点击下方餐品，可以看到每 100 克营养、正常一餐 1/3 日摄入量、忌口、预算和口味对比。", 12, MUTED); d.setLineSpacing(0, 1.25f); margin(d, 0, 6, 0, 10); demo.addView(d); Button demoButton = primary("打开餐品分析演示"); demoButton.setOnClickListener(v -> showMealAnalysis("店长推荐 A（香辣鸡腿堡）", "香辣 · 咸香 · 含鸡蛋和乳制品", 11.99, 590, 22)); demo.addView(demoButton); margin(demo, 8, 0, 0, 0); page.addView(demo);
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.toLowerCase(Locale.CHINA).contains(getPackageName().toLowerCase(Locale.CHINA));
    }
    private void updateCrossAppStatus() {
        if (crossAppStatusText == null || prefs == null) return;
        boolean granted = isAccessibilityEnabled();
        crossAppStatusText.setText(granted
                ? "已开启：在美团、饿了么、淘宝闪购或京东外卖中点击餐品，会弹出营养分析并可记录到对应餐次。"
                : "尚未开启。Android 需要你在系统设置中主动授权，营养流才可以在外卖 App 的餐品页面显示分析。");
        if (crossAppSettingsButton != null) crossAppSettingsButton.setText(
                granted ? "打开系统设置查看权限" : "开启跨应用分析权限");
        if (crossAppPauseButton != null) crossAppPauseButton.setText(
                prefs.getBoolean(MealAccessibilityService.PREF_ANALYSIS_ENABLED, true)
                        ? "暂停餐品分析" : "恢复餐品分析");
    }
    private void openDeliveryApp(DeliveryApp app) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (launch == null) { Toast.makeText(this, app.label + "未安装或没有可打开的页面", Toast.LENGTH_SHORT).show(); return; }
        try { startActivity(launch); } catch (Exception e) { Toast.makeText(this, "暂时无法打开 " + app.label, Toast.LENGTH_SHORT).show(); }
    }

    private void refreshInstalledApps() {
        if (prefs == null) return;
        PackageManager pm = getPackageManager(); Map<String, DeliveryApp> found = new LinkedHashMap<>();
        for (int i = 0; i < DELIVERY_PACKAGES.length; i++) {
            String pkg = DELIVERY_PACKAGES[i]; DeliveryApp app = new DeliveryApp(DELIVERY_LABELS[i], pkg);
            try { ApplicationInfo info = pm.getApplicationInfo(pkg, 0); app.installed = true; app.label = info.loadLabel(pm).toString(); app.icon = info.loadIcon(pm); }
            catch (PackageManager.NameNotFoundException e) { app.icon = getResources().getDrawable(android.R.drawable.sym_def_app_icon); }
            app.added = prefs.getBoolean("app_added_" + pkg, false); found.put(pkg, app);
        }
        Intent launcher = new Intent(Intent.ACTION_MAIN); launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : pm.queryIntentActivities(launcher, 0)) {
            String label = info.loadLabel(pm).toString(); if (!label.contains("美团") && !label.contains("饿了么") && !label.contains("淘宝") && !label.contains("京东")) continue;
            String pkg = info.activityInfo.packageName; if (!found.containsKey(pkg)) { DeliveryApp app = new DeliveryApp(label, pkg); app.installed = true; app.icon = info.loadIcon(pm); app.added = prefs.getBoolean("app_added_" + pkg, false); found.put(pkg, app); }
        }
        deliveryApps = new ArrayList<>(found.values());
    }
    private void showMealAnalysis(String name, String tags, double price, int kcal, int protein) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = column();
        sheet.setBackground(bg(Color.WHITE, 22));
        pad(sheet, 18, 14, 18, 16);

        LinearLayout top = row();
        top.addView(title("餐品营养分析", 19), new LinearLayout.LayoutParams(0, dp(36), 1));
        TextView close = text("×", 28, MUTED);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭餐品营养分析");
        close.setOnClickListener(v -> dialog.dismiss());
        top.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
        sheet.addView(top);

        TextView source = text("餐品：" + name + "\n当前按一份约 500 克进行演示估算", 12, MUTED);
        source.setLineSpacing(0, 1.2f);
        margin(source, 0, 1, 0, 8);
        sheet.addView(source);

        double energy = kcal;
        double oil = 8.0;
        double salt = 1.8;
        double sugar = 6.0;
        double proteinValue = protein;
        double fiber = 2.5;
        double[] currentValues = {energy, oil, salt, sugar, proteinValue, fiber};
        double[] mealTargets = {600.0, 8.3, 1.7, 8.3, 21.7, 8.3};

        double recommendationPrice = 9.0;
        double[] recommendedValues = {
                energy + 168.0,
                oil + 1.1,
                salt + 0.2,
                sugar + 2.8,
                proteinValue + 11.0,
                fiber + 6.4
        };

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = column();
        pad(body, 0, 0, 0, 8);

        addAnalysisSectionTitle(body, "本餐每 500 克营养估算", 0);
        TextView referenceNote = text("一餐参考值按常用每日参考量约三分之一计算，仅用于点餐比较。", 11, MUTED);
        referenceNote.setLineSpacing(0, 1.2f);
        body.addView(referenceNote);
        addAnalysisRow(body, "能量", formatMetric(energy) + " kcal", "约 600 kcal",
                metricStatus(energy, 600.0, false, "kcal"), energy < 480.0);
        addAnalysisRow(body, "油脂", formatMetric(oil) + " g", "不高于 8.3 g",
                metricStatus(oil, 8.3, true, "g"), oil > 8.3);
        addAnalysisRow(body, "食盐", formatMetric(salt) + " g", "不高于 1.7 g",
                metricStatus(salt, 1.7, true, "g"), salt > 1.7);
        addAnalysisRow(body, "添加糖", formatMetric(sugar) + " g", "不高于 8.3 g",
                metricStatus(sugar, 8.3, true, "g"), sugar > 8.3);
        addAnalysisRow(body, "蛋白质", formatMetric(proteinValue) + " g", "不少于 21.7 g",
                metricStatus(proteinValue, 21.7, false, "g"), proteinValue < 21.7);
        addAnalysisRow(body, "膳食纤维", formatMetric(fiber) + " g", "不少于 8.3 g",
                metricStatus(fiber, 8.3, false, "g"), fiber < 8.3);

        addAnalysisSectionTitle(body, "按你的设置核对", 14);
        String allergy = allergyResult(name, tags);
        boolean allergyWarning = allergy.startsWith("发现");
        addAnalysisRow(body, "忌口检查", allergy, "以商家配料表为准",
                allergyWarning ? "请谨慎食用" : "未发现明确匹配", allergyWarning);
        double budget = budgetValue();
        double difference = price - budget;
        String budgetResult = difference > 0
                ? "超预算 " + formatMoney(difference) + " 元"
                : "还可使用 " + formatMoney(-difference) + " 元";
        addAnalysisRow(body, "资金预算", "¥" + formatMoney(price),
                "本餐预算 ¥" + formatMoney(budget), budgetResult, difference > 0);
        addAnalysisRow(body, "口味匹配", tasteResult(tags),
                "偏好：" + p("taste", "未填写"), "已完成差异对照", false);

        addAnalysisSectionTitle(body, "AI 补充建议", 14);
        LinearLayout combo = card();
        combo.setBackground(stroke(MINT, Color.rgb(183, 225, 207), 14));
        TextView comboTitle = title("优先在当前店铺搜索", 14);
        combo.addView(comboTitle);
        TextView comboItems = text("西兰花或清炒时蔬 120 克 · 约 ¥5.00\n"
                + "无糖豆浆 250 毫升 · 约 ¥4.00", 13, DARK);
        comboItems.setLineSpacing(0, 1.3f);
        margin(comboItems, 0, 6, 0, 0);
        combo.addView(comboItems);
        TextView comboEffect = text("预计补充约 168 kcal、蛋白质 11.0 g、膳食纤维 6.4 g", 11, GREEN);
        comboEffect.setLineSpacing(0, 1.2f);
        margin(comboEffect, 0, 7, 0, 0);
        combo.addView(comboEffect);
        margin(combo, 0, 6, 0, 0);
        body.addView(combo);

        double totalPrice = price + recommendationPrice;
        double totalDifference = totalPrice - budget;
        String totalBudgetResult = totalDifference > 0
                ? "合计超预算 " + formatMoney(totalDifference) + " 元"
                : "合计仍低于预算 " + formatMoney(-totalDifference) + " 元";
        TextView note = text("餐品 ¥" + formatMoney(price) + " + 推荐搭配 ¥"
                + formatMoney(recommendationPrice) + " = ¥" + formatMoney(totalPrice)
                + "，" + totalBudgetResult + "。若店内没有同类餐品，可按该克数自行补充。", 11, MUTED);
        note.setLineSpacing(0, 1.25f);
        margin(note, 0, 8, 0, 0);
        body.addView(note);

        addAnalysisSectionTitle(body, "补充前后营养对比", 14);
        TextView chartHint = text("黄色表示当前餐品，绿色表示加入推荐食物后；虚线为对应的一餐参考值。各指标按自身参考值归一化显示。", 11, MUTED);
        chartHint.setLineSpacing(0, 1.2f);
        body.addView(chartHint);
        LinearLayout chartCard = card();
        NutrientComparisonChart chart = new NutrientComparisonChart(this,
                new String[]{"能量", "油脂", "盐", "糖", "蛋白质", "纤维"},
                currentValues, recommendedValues, mealTargets);
        chartCard.addView(chart, new LinearLayout.LayoutParams(-1, dp(250)));
        TextView chartSummary = text(String.format(Locale.CHINA,
                "推荐后：能量 %.0f kcal · 蛋白质 %.1f g · 膳食纤维 %.1f g\n"
                        + "食盐仍约 %.1f g，已超参考值时不因补充食物而隐藏提示。",
                recommendedValues[0], recommendedValues[4], recommendedValues[5], recommendedValues[2]),
                11, MUTED);
        chartSummary.setLineSpacing(0, 1.2f);
        chartCard.addView(chartSummary);
        margin(chartCard, 0, 7, 0, 0);
        body.addView(chartCard);

        scroll.addView(body);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView disclaimer = text("营养与价格为演示估算，不是商家标签或医学诊断。", 10, MUTED);
        disclaimer.setGravity(Gravity.CENTER);
        margin(disclaimer, 0, 7, 0, 0);
        sheet.addView(disclaimer);

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.35f;
            window.setAttributes(attrs);
            window.setGravity(Gravity.BOTTOM);
        }
        dialog.setOnShowListener(d -> {
            Window shown = dialog.getWindow();
            if (shown != null) {
                shown.setLayout(-1,
                        (int) (getResources().getDisplayMetrics().heightPixels * 0.75f));
                shown.setGravity(Gravity.BOTTOM);
            }
        });
        dialog.show();
    }

    private void addAnalysisSectionTitle(LinearLayout parent, String value, int topMargin) {
        TextView heading = title(value, 15);
        margin(heading, 0, topMargin, 0, 4);
        parent.addView(heading);
    }

    private String metricStatus(double value, double target, boolean upperLimit, String unit) {
        double difference = value - target;
        if (upperLimit) {
            return difference > 0.05
                    ? "超出 " + formatMetric(difference) + " " + unit
                    : "未超过参考值";
        }
        return difference < -0.05
                ? "还差 " + formatMetric(-difference) + " " + unit
                : "达到参考值";
    }

    private String formatMetric(double value) {
        return Math.abs(value - Math.rint(value)) < 0.05
                ? String.format(Locale.CHINA, "%.0f", value)
                : String.format(Locale.CHINA, "%.1f", value);
    }
    private void addAnalysisRow(LinearLayout parent, String label, String value, String reference, String result, boolean warning) { LinearLayout r = column(); margin(r, 0, 8, 0, 0); LinearLayout first = row(); first.addView(title(label, 13), new LinearLayout.LayoutParams(0, dp(24), 1)); TextView valueText = text(value, 13, warning ? ORANGE : GREEN); valueText.setTypeface(Typeface.DEFAULT, Typeface.BOLD); first.addView(valueText, new LinearLayout.LayoutParams(-2, dp(24))); r.addView(first); LinearLayout second = row(); second.addView(text(reference, 11, MUTED), new LinearLayout.LayoutParams(0, dp(22), 1)); TextView resultText = text(result, 11, warning ? ORANGE : MUTED); resultText.setGravity(Gravity.RIGHT); second.addView(resultText, new LinearLayout.LayoutParams(-2, dp(22))); r.addView(second); parent.addView(r);
    }
    private String allergyResult(String name, String tags) {
        String allergy = p("allergy", "无").trim();
        if (allergy.length() == 0 || "无".equals(allergy)) return "未设置忌口";
        String foodText = name + " " + tags;
        for (String token : allergy.split("[、,，;； ]+")) {
            if (token.length() == 0) continue;
            boolean matched = foodText.contains(token)
                    || (token.contains("鸡") && foodText.contains("鸡"))
                    || (token.contains("虾") && foodText.contains("虾"))
                    || (token.contains("牛") && foodText.contains("牛"))
                    || (token.contains("奶") && (foodText.contains("乳") || foodText.contains("奶")));
            if (matched) return "发现：" + token;
        }
        return "未发现明确匹配";
    }
    private String tasteResult(String tags) { String taste = p("taste", "未填写"); if (taste.contains("辣") && tags.contains("辣")) return "匹配：你的偏好含辣，餐品为" + tags; if ((taste.contains("清淡") || taste.contains("少油")) && (tags.contains("咸") || tags.contains("香辣"))) return "差异：餐品偏咸偏辣，可能比清淡偏好重"; if (taste.contains("甜") && tags.contains("甜")) return "匹配：你的偏好偏甜，餐品含甜味"; return "差异：餐品为" + tags + "，请结合个人口味选择"; }
    private String formatMoney(double value) { return String.format(Locale.CHINA, "%.2f", value); }
    private void showProfileEdit(){
        base(); ScrollView scroll=new ScrollView(this);LinearLayout page=column();pad(page,22,18,22,28);scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout top=row();TextView back=text("‹",34,DARK);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->showApp(3));top.addView(back,new LinearLayout.LayoutParams(dp(42),dp(44)));TextView tt=title("编辑营养画像",20);top.addView(tt);page.addView(top);TextView helper=text("保存后，下一次推荐会立即更新。",13,MUTED);margin(helper,dp(42),-3,0,18);page.addView(helper);
        EditText age=field("年龄","年龄");age.setText(p("age","20"));page.addView(age,new LinearLayout.LayoutParams(-1,dp(54)));EditText height=field("身高","身高 cm");height.setText(p("height","170"));margin(height,0,10,0,0);page.addView(height);EditText weight=field("体重","体重 kg");weight.setText(p("weight","60"));margin(weight,0,10,0,0);page.addView(weight);
        Spinner goal=spinner(new String[]{"均衡饮食","体重管理","增肌塑形","血糖友好"},"健康目标");goal.setSelection(indexOf(goal,p("goal","均衡饮食")));margin(goal,0,10,0,0);page.addView(goal);Spinner activity=spinner(new String[]{"日常活动较少","轻度活动（每周 1-2 次）","中度活动（每周 3-4 次）","高频运动（每周 5 次以上）"},"活动水平");activity.setSelection(indexOf(activity,p("activity","日常活动较少")));margin(activity,0,10,0,0);page.addView(activity);Spinner taste=spinner(new String[]{"清淡少油","均衡口味","偏辣开胃","高蛋白优先"},"口味偏好");taste.setSelection(indexOf(taste,p("taste","均衡口味")));margin(taste,0,10,0,0);page.addView(taste);Spinner budget=spinner(new String[]{"¥20 以内","¥20-35","¥35-50","预算弹性"},"单餐预算");budget.setSelection(indexOf(budget,p("budget","¥20-35")));margin(budget,0,10,0,0);page.addView(budget);EditText allergy=field("过敏或忌口","过敏或忌口（选填）");allergy.setText(p("allergy",""));margin(allergy,0,10,0,0);page.addView(allergy);Button save=primary("保存并更新推荐");margin(save,0,20,0,0);page.addView(save);save.setOnClickListener(v->{saveProfile(age.getText().toString(),height.getText().toString(),weight.getText().toString(),goal.getSelectedItem().toString(),activity.getSelectedItem().toString(),taste.getSelectedItem().toString(),budget.getSelectedItem().toString(),allergy.getText().toString());Toast.makeText(this,"画像已更新",Toast.LENGTH_SHORT).show();showApp(0);});
    }
    private int indexOf(Spinner s,String value){ArrayAdapter a=(ArrayAdapter)s.getAdapter();for(int i=0;i<a.getCount();i++)if(a.getItem(i).toString().equals(value))return i;return 0;}
    private static class DeliveryApp {
        String label; final String packageName; boolean installed; boolean added; Drawable icon;
        DeliveryApp(String label, String packageName) { this.label = label; this.packageName = packageName; }
    }}
















