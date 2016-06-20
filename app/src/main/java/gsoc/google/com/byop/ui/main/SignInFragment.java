package gsoc.google.com.byop.ui.main;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.ui.documentsList.FolderListFragment;
import gsoc.google.com.byop.ui.settings.SettingsFragment;
import gsoc.google.com.byop.utils.AndroidUtils;
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;

/**
 * A login screen
 */
public class SignInFragment extends Fragment implements GoogleApiClient.OnConnectionFailedListener,
        View.OnClickListener {

    private static final String TAG = "SignInActivity";
    private static final int RC_SIGN_IN = 9001;
    protected FragmentStackManager fragmentStackManager;
    private GoogleApiClient mGoogleApiClient;
    private TextView mStatusTextView;
    private ProgressDialog mProgressDialog;
    private View view;

    ActionBar toolbar;

    OptionalPendingResult<GoogleSignInResult> opr;

    GoogleSignInAccount acct;

    public static SignInFragment newInstance() {
        SignInFragment signInFragment = new SignInFragment();
        return signInFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }


    private void populateViewForOrientation(LayoutInflater inflater, ViewGroup container) {
        container.removeAllViewsInLayout();
        view = inflater.inflate(R.layout.activity_sign_in, container, false);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
//        LayoutInflater inflater = LayoutInflater.from(getActivity());
//        populateViewForOrientation(inflater, (ViewGroup) getView());
        mGoogleApiClient.disconnect();
        mGoogleApiClient.stopAutoManage(this.getActivity());
        fragmentStackManager.popBackStatFragment();
        fragmentStackManager.loadFragment(SignInFragment.newInstance(), R.id.main_frame);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        view = inflater.inflate(R.layout.activity_sign_in, container, false);

        getActivity().setTitle(getActivity().getResources().getString(R.string.app_name));

        fragmentStackManager = FragmentStackManager.getInstance(getActivity());

        // Views
        mStatusTextView = (TextView) view.findViewById(R.id.status);

        // Button listeners
        view.findViewById(R.id.sign_in_button).setOnClickListener(this);
        view.findViewById(R.id.proceedToDocumentList).setOnClickListener(this);

        toolbar = ((AppCompatActivity) getActivity()).getSupportActionBar();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                    .enableAutoManage(getActivity(), this)
                    .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                    .build();
        }

        SignInButton signInButton = (SignInButton) view.findViewById(R.id.sign_in_button);
        signInButton.setSize(SignInButton.SIZE_STANDARD);
        signInButton.setScopes(gso.getScopeArray());

        return view;
    }


    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
        opr.cancel();
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();

        mGoogleApiClient.connect();

        opr = Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);
        if (opr.isDone()) {
            // If the user's cached credentials are valid, the OptionalPendingResult will be "done"
            // and the GoogleSignInResult will be available instantly.
            Log.d(TAG, "Got cached sign-in");
            GoogleSignInResult result = opr.get();
            handleSignInResult(result);
        } else {
            // If the user has not previously signed in on this device or the sign-in has expired,
            // this asynchronous branch will attempt to sign in the user silently.  Cross-device
            // single sign-on will occur in this branch.
            showProgressDialog();
            opr.setResultCallback(new ResultCallback<GoogleSignInResult>() {
                @Override
                public void onResult(GoogleSignInResult googleSignInResult) {
                    hideProgressDialog();
                    handleSignInResult(googleSignInResult);
                }
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleSignInResult(result);
        }
    }

    private void handleSignInResult(GoogleSignInResult result) {
        Log.d(TAG, "handleSignInResult:" + result.isSuccess());
        if (result.isSuccess()) {
            // Signed in successfully, show authenticated UI.
            acct = result.getSignInAccount();
            mStatusTextView.setText(getString(R.string.signed_in_fmt, acct.getDisplayName()));
            updateUI(true);

            SharedPreferences settings =
                    this.getActivity().getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(Constants.PREF_ACCOUNT_EMAIL, acct.getEmail());
            editor.apply();
        } else {
            // Signed out, show unauthenticated UI.
            updateUI(false);
        }
    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void signOut() {
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        updateUI(false);
                    }
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_disconnect:
                revokeAccess();
                return true;
            case R.id.action_logout:
                signOut();
                return true;
            case R.id.action_settings:
                loadSettings();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void revokeAccess() {
        Auth.GoogleSignInApi.revokeAccess(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        updateUI(false);
                    }
                });

        //Delete shared Preferences
        SharedPreferences settings = this.getActivity().getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        editor.commit();

        AndroidUtils.clearApplicationData(getActivity().getApplication());
    }

    private void loadDocumentsList() {
        FolderListFragment folderListFragment = FolderListFragment.newInstance();
        fragmentStackManager.loadFragment(folderListFragment, R.id.main_frame);
    }

    private void loadSettings() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());
        builder.setTitle(getResources().getString(R.string.enter_password));


        final EditText input = new EditText(this.getActivity());

        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);


        builder.setPositiveButton(getResources().getString(R.string.Accept), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences prefs = getActivity().getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE);
                String password = prefs.getString("password", "");
                if (password.equals(input.getText().toString())) {
                    SettingsFragment settingsFragment = SettingsFragment.newInstance();
                    fragmentStackManager.loadFragment(settingsFragment, R.id.main_frame);
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setMessage(getResources().getString(R.string.error_incorrect_password));
                    builder.setPositiveButton(getResources().getString(R.string.Accept), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    builder.show();
                }
            }
        });
        builder.setNegativeButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();


    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
    }

    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(getActivity());
            mProgressDialog.setMessage(getString(R.string.loading));
            mProgressDialog.setIndeterminate(true);
        }
        mProgressDialog.show();
    }

    private void hideProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.hide();
        }
    }

    private void updateUI(boolean signedIn) {
        if (signedIn) {
            toolbar.dispatchMenuVisibilityChanged(false);
            view.findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            view.findViewById(R.id.proceedToDocumentList).setVisibility(View.VISIBLE);
        } else {
            mStatusTextView.setText(R.string.signed_out);
            view.findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            view.findViewById(R.id.proceedToDocumentList).setVisibility(View.GONE);
            toolbar.dispatchMenuVisibilityChanged(true);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sign_in_button:
                signIn();
                break;
            case R.id.proceedToDocumentList:
                loadDocumentsList();
                break;
        }
    }
}

