package gsoc.google.com.byop.ui.main;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.utils.AndroidUtils;
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {


    private FragmentStackManager fragmentStackManager;
    private GoogleApiClient mGoogleApiClient;

    public static AlertDialog.Builder closeDialog(Context context, DialogInterface.OnClickListener accept, DialogInterface.OnClickListener cancel) {
        final AlertDialog.Builder closeDialog = new AlertDialog.Builder(context);
        closeDialog.setTitle(context.getResources().getString(R.string.close));
        closeDialog.setMessage(context.getResources().getString(R.string.close_body));
        closeDialog.setPositiveButton(context.getResources().getString(R.string.Accept), accept);
        closeDialog.setNegativeButton(context.getResources().getString(R.string.cancel_dialog), cancel);
        return closeDialog;
    }

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

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        fragmentStackManager = FragmentStackManager.getInstance(this);

        SignInFragment fragment = new SignInFragment();
        fragmentStackManager.loadFragment(fragment, R.id.main_frame);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.actionbar_menu, menu);

        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, Constants.SERVICE_INVALID_POIS_LIST);
            } catch (IntentSender.SendIntentException e) {
                // Unable to resolve, message user appropriately
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this, 0).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
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

    @Override
    public void onConnected(@Nullable Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

}
