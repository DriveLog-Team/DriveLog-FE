package kr.drivelog.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StoryActivity extends Activity {
    private static final String INSTAGRAM = "com.instagram.android";
    private static final ExecutorService DOWNLOADS = Executors.newSingleThreadExecutor();
    private static final String ACTION = "com.instagram.share.ADD_TO_STORY";
    private TextView message;
    private Button share;
    private String id;
    private String code;
    private boolean launched;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (24 * getResources().getDisplayMetrics().density);
        panel.setPadding(p, p, p, p);
        panel.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(p + insets.getSystemWindowInsetLeft(), p + insets.getSystemWindowInsetTop(),
                    p + insets.getSystemWindowInsetRight(), p + insets.getSystemWindowInsetBottom());
            return insets;
        });
        message = new TextView(this);
        message.setTextSize(18);
        panel.addView(message);
        share = new Button(this);
        share.setText(R.string.share_story);
        share.setOnClickListener(v -> openStory());
        panel.addView(share);
        Button settings = new Button(this);
        settings.setText(R.string.configure_id);
        settings.setOnClickListener(v -> configure());
        panel.addView(settings);
        setContentView(panel);

        Uri data = getIntent().getData();
        if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && data != null
                && "drivelog".equals(data.getScheme()) && "story".equals(data.getHost()) && data.getPathSegments().size() == 1) {
            id = data.getLastPathSegment();
            code = getIntent().getStringExtra("ticket");
        }
        if (id == null || !id.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                || code == null || !code.matches("[A-Za-z0-9_-]{43}")) {
            id = null;
            message.setText(R.string.scan_qr);
            share.setVisibility(View.GONE);
            return;
        }
        launched = state != null && state.getBoolean("launched");
        if (launched) message.setText(R.string.reopen_story);
        else openStory();
    }

    private String appId() {
        return getPreferences(MODE_PRIVATE).getString("appId", getString(R.string.meta_app_id));
    }

    private void configure() {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(appId());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("DriveLog 공개 Meta 앱 ID")
                .setView(field).setNegativeButton("취소", null).setPositiveButton("저장", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = field.getText().toString().trim();
            if (!value.matches("[1-9][0-9]{1,24}")) { field.setError("숫자 앱 ID를 입력하세요."); return; }
            getPreferences(MODE_PRIVATE).edit().putString("appId", value).apply();
            dialog.dismiss();
            if (id != null) openStory();
        });
    }

    private Intent composer(Uri uri) {
        Intent intent = new Intent(ACTION).setPackage(INSTAGRAM).setDataAndType(uri, "video/mp4")
                .putExtra("source_application", appId()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("DriveLog", uri));
        return intent;
    }

    private void openStory() {
        if (!share.isEnabled()) return;
        if (appId().isEmpty()) {
            message.setText(R.string.setup_required);
            return;
        }
        Uri probe = Uri.parse("content://kr.drivelog.phone.clips/clip/check.mp4");
        if (composer(probe).resolveActivity(getPackageManager()) == null) {
            message.setText(R.string.install_instagram);
            return;
        }
        share.setEnabled(false);
        message.setText(R.string.receiving_video);
        DOWNLOADS.execute(() -> {
            try {
                File clip = ClipDownload.get(getCacheDir(), id, code);
                Uri uri = FileProvider.getUriForFile(this, "kr.drivelog.phone.clips", clip);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    share.setEnabled(true);
                    try {
                        startActivity(composer(uri));
                        launched = true;
                        message.setText(R.string.finish_in_instagram);
                    } catch (android.content.ActivityNotFoundException | SecurityException e) {
                        message.setText(R.string.try_again);
                    }
                });
            } catch (IOException | IllegalArgumentException e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    share.setEnabled(true);
                    message.setText(e.getMessage() != null && e.getMessage().matches("^[가-힣].*")
                            ? e.getMessage() : "영상 연결이 끊겼습니다. 다시 시도하고 필요하면 새 QR을 발급하세요.");
                });
            }
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("launched", launched);
    }
}
