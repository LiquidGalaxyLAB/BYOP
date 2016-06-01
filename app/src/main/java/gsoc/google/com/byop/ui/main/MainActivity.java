package gsoc.google.com.byop.ui.main;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import gsoc.google.com.byop.R;
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
        ab.setDisplayHomeAsUpEnabled(true);

        fragmentStackManager = FragmentStackManager.getInstance(this);

        SignInFragment fragment = new SignInFragment();
        fragmentStackManager.loadFragment(fragment, R.id.main_frame);
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
    public void onBackPressed() {
        try {
            if (!fragmentStackManager.popBackStatFragment()) {
                closeDialog(MainActivity.this, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
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
