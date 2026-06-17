package org.autojs.autojs.external.shortcut;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.external.open.RunIntentActivity;
import org.autojs.autojs.model.script.PathChecker;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.util.ViewUtils;

/**
 * Created by Stardust on Jan 23, 2017.
 */
public class ShortcutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ScriptIntents.isTrusted(getIntent())) {
            startActivity(new Intent(this, RunIntentActivity.class).putExtras(getIntent()));
            finish();
            return;
        }
        final String path = getIntent().getStringExtra(ScriptIntents.EXTRA_KEY_PATH);
        if (new PathChecker(this).checkAndToastError(path)) {
            runScriptFile(path);
        }
        finish();
    }

    private void runScriptFile(String path) {
        try {
            Scripts.run(this, new ScriptFile(path));
        } catch (Exception e) {
            e.printStackTrace();
            ViewUtils.showToast(this, e.getMessage(), true);
        }
    }

}
