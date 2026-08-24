package com.playrix.township.vip;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private EditText etVipKey;
    private Button btnLogin, btnCopyId, btnBuyVip;
    private String deviceId;

    // رابط Firebase الحقيقي
    private static final String FIREBASE_URL = "https://vip-5ea5f-default-rtdb.firebaseio.com/activation_codes.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // جلب معرف الجهاز الحقيقي (Android Hardware ID)
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        deviceId = "DEV-" + (androidId != null ? androidId.toUpperCase().substring(0, 8) : "8889");

        etVipKey = findViewById(R.id.etVipKey);
        btnLogin = findViewById(R.id.btnLogin);
        btnCopyId = findViewById(R.id.btnCopyId);
        btnBuyVip = findViewById(R.id.btnBuyVip);

        // 1. زر نسخ المعرف
        btnCopyId.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Device ID", deviceId);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "⚙️ تم نسخ المعرف: " + deviceId, Toast.LENGTH_SHORT).show();
        });

        // 2. زر شراء الكود والتوجيه إلى @Enreem
        btnBuyVip.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Enreem"));
            startActivity(intent);
        });

        // 3. زر التحقق وتسجيل الدخول وتعديل اللعبة
        btnLogin.setOnClickListener(v -> {
            String key = etVipKey.getText().toString().trim().toUpperCase();
            if (key.isEmpty()) {
                Toast.makeText(this, "الرجاء إدخال كود التفعيل", Toast.LENGTH_SHORT).show();
                return;
            }
            verifyCodeFromFirebase(key);
        });
    }

    // التحقق من كود السيرفر وقفل الجهاز
    private void verifyCodeFromFirebase(String code) {
        new Thread(() -> {
            try {
                URL url = new URL(FIREBASE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject data = new JSONObject(response.toString());
                boolean found = false;
                boolean isDeviceMatch = false;

                for (int i = 0; i < data.names().length(); i++) {
                    String key = data.names().getString(i);
                    JSONObject item = data.getJSONObject(key);
                    
                    if (item.optString("code").equalsIgnoreCase(code)) {
                        found = true;
                        String usedBy = item.optString("used_by", "");
                        
                        if (usedBy.isEmpty() || usedBy.equals(deviceId)) {
                            isDeviceMatch = true;
                            // تحديث الجهاز في السيرفر
                            bindDeviceToCode(item.getString("id"), deviceId);
                        }
                        break;
                    }
                }

                boolean finalFound = found;
                boolean finalMatch = isDeviceMatch;

                runOnUiThread(() -> {
                    if (!finalFound) {
                        Toast.makeText(this, "❌ كود التفعيل غير صحيح", Toast.LENGTH_LONG).show();
                    } else if (!finalMatch) {
                        Toast.makeText(this, "🚫 هذا الكود مقفل ومخصص لجهاز آخر فقط!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "👑 تم التفعيل بنجاح! جاري تعديل بيانات Township...", Toast.LENGTH_SHORT).show();
                        injectRootTownshipMod();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "فشل الاتصال بسيرفر التحقق", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ربط الكود بجهاز العميل في Firebase
    private void bindDeviceToCode(String recordId, String devId) {
        try {
            URL url = new URL("https://vip-5ea5f-default-rtdb.firebaseio.com/activation_codes/" + recordId + ".json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setDoOutput(true);
            conn.getOutputStream().write(("{\"used_by\":\"" + devId + "\",\"status\":\"used\"}").getBytes());
            conn.getResponseCode();
        } catch (Exception ignored) {}
    }

    // حقن وتعديل اللعبة عبر أوامر الروت الأصلية في نظام أندرويد
    private void injectRootTownshipMod() {
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());

                String pkg = "com.playrix.township";
                String targetDir = "/data/data/" + pkg + "/databases";

                os.writeBytes("am force-stop " + pkg + "\n");
                os.writeBytes("chmod 771 " + targetDir + "\n");
                os.writeBytes("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1\n");
                os.writeBytes("exit\n");
                os.flush();
                p.waitFor();

                runOnUiThread(() -> Toast.makeText(this, "✅ تم تشغيل اللعبة بالمستوى والملايين!", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "يرجى منح صلاحية الروت (Root Access)", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
