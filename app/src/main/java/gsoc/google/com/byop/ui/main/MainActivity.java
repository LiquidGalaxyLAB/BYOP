package gsoc.google.com.byop.ui.main;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.ui.poisList.POISListFragment;
import gsoc.google.com.byop.utils.AndroidUtils;
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;


public class MainActivity extends AppCompatActivity {


    private FragmentStackManager fragmentStackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        fragmentStackManager = FragmentStackManager.getInstance(this);

        SignInFragment fragment = new SignInFragment();
        fragmentStackManager.loadFragment(fragment, R.id.main_frame);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.SERVICE_INVALID_POIS_LIST) {
            fragmentStackManager.popBackStatFragment();
            POISListFragment poisListFragment = POISListFragment.getInstance();
            fragmentStackManager.loadFragment(poisListFragment, R.id.main_frame);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static AlertDialog.Builder closeDialog(Context context, DialogInterface.OnClickListener accept, DialogInterface.OnClickListener cancel) {
        final AlertDialog.Builder closeDialog = new AlertDialog.Builder(context);
        closeDialog.setTitle(context.getResources().getString(R.string.close));
        closeDialog.setMessage(context.getResources().getString(R.string.close_body));
        closeDialog.setPositiveButton(context.getResources().getString(R.string.Accept), accept);
        closeDialog.setNegativeButton(context.getResources().getString(R.string.cancel_dialog), cancel);
        return closeDialog;
    }


    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        ActivityManager am = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        // The first in the list of RunningTasks is always the foreground task.
        ActivityManager.RunningTaskInfo foregroundTaskInfo = am.getRunningTasks(1).get(0);
        if (!foregroundTaskInfo.topActivity.getPackageName().equals(this.getPackageName()) && !foregroundTaskInfo.topActivity.getPackageName().equals("com.google.android.gms")) {
            // The app is exiting no other activity of your app is brought to front
            //AndroidUtils.clearApplicationData(getApplication());
            finish();
            System.exit(0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ActivityManager am = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        // The first in the list of RunningTasks is always the foreground task.
        ActivityManager.RunningTaskInfo foregroundTaskInfo = am.getRunningTasks(1).get(0);
        if (!foregroundTaskInfo.topActivity.getPackageName().equals(this.getPackageName()) && !foregroundTaskInfo.topActivity.getPackageName().equals("com.google.android.gms")) {
            // The app is exiting no other activity of your app is brought to front
            // AndroidUtils.clearApplicationData(getApplication());
            finish();
            System.exit(0);
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if (!fragmentStackManager.popBackStatFragment()) {
                closeDialog(MainActivity.this, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        AndroidUtils.clearApplicationData(getApplication());
                        finish();
                        System.exit(0);
                    }
                }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
