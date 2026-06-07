package org.autojs.autojs.external.open;

import android.app.Activity;
import android.database.Cursor;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.script.StringScriptSource;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Created by Stardust on Feb 22, 2017.
 */
public class RunIntentActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            handleIntent(getIntent());
        } catch (Exception e) {
            e.printStackTrace();
            ViewUtils.showToast(this, R.string.edit_and_run_handle_intent_error, true);
        }
        finish();
    }

    private void handleIntent(Intent intent) throws FileNotFoundException {
        Uri uri = intent.getData();
        if (uri != null && "content".equals(uri.getScheme())) {
            InputStream stream = getContentResolver().openInputStream(uri);
            StringScriptSource source = new StringScriptSource(getSourceName(uri), PFiles.read(stream));
            source.setOverriddenFullPath(uri.toString());
            Scripts.run(this, source);
        } else {
            ScriptIntents.handleIntent(this, intent);
        }
    }

    private String getSourceName(Uri uri) {
        String displayName = getDisplayName(uri);
        if (isBlank(displayName)) {
            displayName = uri.getLastPathSegment();
        }
        if (isBlank(displayName)) {
            return "Tmp";
        }
        return PFiles.getNameWithoutExtension(displayName);
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (columnIndex >= 0) {
                    return cursor.getString(columnIndex);
                }
            }
        } catch (Exception ignored) {
            /* Fall back to Uri#getLastPathSegment. */
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}
