package gsoc.google.com.byop.ui.main;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;
import gsoc.google.com.byop.utils.PW.BeaconConfigFragment;

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

    OptionalPendingResult<GoogleSignInResult> opr;

    GoogleSignInAccount acct;


    public static SignInFragment newInstance() {

        SignInFragment signInFragment = new SignInFragment();

        return signInFragment;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        view = inflater.inflate(R.layout.activity_sign_in, container, false);

        getActivity().setTitle(getActivity().getResources().getString(R.string.app_name));

        fragmentStackManager = FragmentStackManager.getInstance(getActivity());

        // Views
        mStatusTextView = (TextView) view.findViewById(R.id.status);

        // Button listeners
        view.findViewById(R.id.sign_in_button).setOnClickListener(this);
        view.findViewById(R.id.sign_out_button).setOnClickListener(this);
        view.findViewById(R.id.disconnect_button).setOnClickListener(this);
        view.findViewById(R.id.proceedToDocumentList).setOnClickListener(this);
        view.findViewById(R.id.configureBeacon).setOnClickListener(this);


        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                    .enableAutoManage(getActivity() /* FragmentActivity */, this /* OnConnectionFailedListener */)
                    .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                    .build();
        }

        SignInButton signInButton = (SignInButton) view.findViewById(R.id.sign_in_button);
        signInButton.setSize(SignInButton.SIZE_STANDARD);
        signInButton.setScopes(gso.getScopeArray());

        return view;
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

            String id_token = acct.getIdToken();


            SharedPreferences settings =
                    this.getActivity().getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(Constants.PREF_ACCOUNT_EMAIL, acct.getEmail());
            editor.putString("GG_LOGED", id_token);
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

//        AndroidUtils.clearApplicationData(getActivity().getApplication());
    }

    private void loadDocumentsList() {

        FolderListFragment folderListFragment = FolderListFragment.newInstance();
        fragmentStackManager.loadFragment(folderListFragment, R.id.main_frame);

    }

    private void configureBeacon() {
        BeaconConfigFragment beaconConfigFragment = BeaconConfigFragment.newInstance();
        fragmentStackManager.loadFragment(beaconConfigFragment, R.id.main_frame);
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
            view.findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            view.findViewById(R.id.sign_out_and_disconnect).setVisibility(View.VISIBLE);
            view.findViewById(R.id.subLogoButtons).setVisibility(View.VISIBLE);


        } else {
            mStatusTextView.setText(R.string.signed_out);

            view.findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            view.findViewById(R.id.sign_out_and_disconnect).setVisibility(View.GONE);
            view.findViewById(R.id.subLogoButtons).setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sign_in_button:
                signIn();
                break;
            case R.id.sign_out_button:
                signOut();
                break;
            case R.id.disconnect_button:
                revokeAccess();
                break;
            case R.id.proceedToDocumentList:
                loadDocumentsList();
                break;
            case R.id.configureBeacon:
                configureBeacon();
                break;
        }
    }
}

