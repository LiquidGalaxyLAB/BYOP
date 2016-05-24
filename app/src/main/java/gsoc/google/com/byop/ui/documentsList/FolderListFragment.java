package gsoc.google.com.byop.ui.documentsList;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.query.Filter;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.poliveira.parallaxrecyclerview.ParallaxRecyclerAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.model.DriveDocument;
import gsoc.google.com.byop.utils.AndroidUtils;
import gsoc.google.com.byop.utils.DriveUtils;
import gsoc.google.com.byop.utils.FragmentStackManager;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * Created by lgwork on 23/05/16.
 */
public class FolderListFragment extends Fragment implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, EasyPermissions.PermissionCallbacks {

    /**
     * Standard activity result: operation canceled.
     */
    public static final int RESULT_CANCELED = 0;
    /**
     * Standard activity result: operation succeeded.
     */
    public static final int RESULT_OK = -1;
    /**
     * Start of user-defined activity results.
     */
    public static final int RESULT_FIRST_USER = 1;

    private static String TAG = FolderListFragment.class.toString();
    private RecyclerView rv = null;
    private ParallaxRecyclerAdapter<DriveDocument> parallaxRecyclerAdapter;
    private SwipeRefreshLayout refreshLayout;
    private MakeRequestTask requestTask;
    private FloatingActionButton fab;

    protected FragmentStackManager fragmentStackManager;

    /**
     * Google API client.
     */
    private GoogleApiClient mGoogleApiClient;

    /**
     * Needed for  DRIVE REST API V3
     */
    GoogleAccountCredential mCredential;
    private static final String[] SCOPES = {DriveScopes.DRIVE_METADATA_READONLY};

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    private static final String PREF_ACCOUNT_NAME = "accountName";

    private String folderId = "";

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        getActivity().setTitle(getActivity().getResources().getString(R.string.app_name));
        populateUI(folderId);
        fragmentStackManager = FragmentStackManager.getInstance(getActivity());
        refreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        populateUI(folderId);
                    }
                }
        );
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                PatientRegister patientRegister = PatientRegister.newInstance(profile);
//                fragmentStackManager.loadFragment(patientRegister, R.id.responsiblePatientFrame);
//            }
//        });
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.folder_list, container, false);
        rv = (RecyclerView) rootView.findViewById(R.id.rv);
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        rv.setLayoutManager(llm);
        rv.setHasFixedSize(true);
        refreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipeRefresh);

        mGoogleApiClient = new GoogleApiClient.Builder(this.getActivity())
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(getContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

        refreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        populateUI(folderId);
                    }
                }
        );

        refreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipeRefresh);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }


    public GoogleApiClient getGoogleApiClient() {
        return mGoogleApiClient;
    }

    @Override
    public void onConnected(Bundle bundle) {


        final String folderName = "BYOP POIS";

        DriveId driveId = Drive.DriveApi.getRootFolder(mGoogleApiClient).getDriveId();

        DriveId existingFolderId = DriveUtils.getFolder(this, driveId, folderName, mGoogleApiClient);


        ArrayList<Filter> fltrs = new ArrayList<>();
        fltrs.add(Filters.eq(SearchableField.TITLE, folderName));
        fltrs.add(Filters.eq(SearchableField.MIME_TYPE, "application/vnd.google-apps.folder"));
        Query qry = new Query.Builder().addFilter(Filters.and(fltrs)).build();


        Drive.DriveApi.query(mGoogleApiClient, qry).setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
            DriveId byopFolderId = null;

            @Override
            public void onResult(DriveApi.MetadataBufferResult result) {

                if (result.getStatus().isSuccess()) {
                    boolean isFound = false;
                    for (Metadata m : result.getMetadataBuffer()) {
                        if (!isFound) {
                            if (!m.isTrashed() && m.getTitle().equals(folderName)) {
                                //Folder exists"
                                isFound = true;
                                byopFolderId = m.getDriveId();
                            }
                        }
                    }
                    if (isFound) {
                        //Existing Folder
                        //Fetch the folder contents
                        DriveFolder folder = Drive.DriveApi.getFolder(getGoogleApiClient(), byopFolderId);
                        folderId = byopFolderId.getResourceId();
                        getResultsFromApi();
                    }
                }

            }

        });

        if (existingFolderId != null) {
            Log.d(TAG, existingFolderId.toString());
        }
    }


    private void getResultsFromApi() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            AndroidUtils.showMessage("No network connection available.", getActivity());
        } else {
            new MakeRequestTask(mCredential, folderId).execute();
        }
    }

    /**
     * Checks whether the device currently has a network connection.
     *
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) this.getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }


    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this.getActivity(), Manifest.permission.GET_ACCOUNTS)) {
            String accountName = this.getActivity().getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this.getActivity(),
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }


    /**
     * Check that Google Play services APK is installed and up to date.
     *
     * @return true if Google Play Services is available and up to
     * date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this.getActivity());
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this.getActivity());
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                this.getActivity(), connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this.getActivity(), ConnectionResult.SERVICE_INVALID);
            } catch (IntentSender.SendIntentException e) {
                // Unable to resolve, message user appropriately
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this.getActivity(), 0).show();
        }

    }

    private void populateUI(String folderId) {
        requestTask = new MakeRequestTask(mCredential, folderId);
        requestTask.execute();

    }


    private void fillAdapter(final List<DriveDocument> documents) {
        parallaxRecyclerAdapter = new ParallaxRecyclerAdapter<DriveDocument>(documents) {
            @Override
            public void onBindViewHolderImpl(RecyclerView.ViewHolder viewHolder, ParallaxRecyclerAdapter<DriveDocument> parallaxRecyclerAdapter, int i) {
                DriveDocument driveDoc = parallaxRecyclerAdapter.getData().get(i);
                DriveDocumentHolder documentHolder = (DriveDocumentHolder) viewHolder;
                documentHolder.documentTitle.setText(driveDoc.getTitle());
                documentHolder.documentExtension.setText(driveDoc.getExtension());
                documentHolder.filePhoto.setImageDrawable(ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.xml_file));
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolderImpl(ViewGroup viewGroup, ParallaxRecyclerAdapter<DriveDocument> parallaxRecyclerAdapter, int i) {
                return new DriveDocumentHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.drivedocument_list_item_card, viewGroup, false));
            }

            @Override
            public int getItemCountImpl(ParallaxRecyclerAdapter<DriveDocument> parallaxRecyclerAdapter) {
                return documents.size();
            }
        };
        parallaxRecyclerAdapter.setParallaxHeader(getActivity().getLayoutInflater().inflate(R.layout.drivedocument_list_header_layout, rv, false), rv);
        parallaxRecyclerAdapter.setOnClickEvent(new ParallaxRecyclerAdapter.OnClickEvent() {
            @Override
            public void onClick(View view, int position) {
                DriveDocument driveDocument = parallaxRecyclerAdapter.getData().get(position);
            }
        });
        rv.setAdapter(parallaxRecyclerAdapter);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {
        // Do nothing.
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {
// Do nothing.
    }

    @Override
    public void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    AndroidUtils.showMessage(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.", getActivity());
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                this.getActivity().getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     *
     * @param requestCode  The request code passed in
     *                     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    private class DriveDocumentHolder extends RecyclerView.ViewHolder {
        CardView cv;
        TextView documentTitle;
        TextView documentExtension;
        ImageView filePhoto;


        public DriveDocumentHolder(View itemView) {
            super(itemView);
            documentTitle = (TextView) itemView.findViewById(R.id.document_title);
            documentExtension = (TextView) itemView.findViewById(R.id.document_extension);
            filePhoto = (ImageView) itemView.findViewById(R.id.file_photo);
        }
    }


    /**
     * An asynchronous task that handles the Drive API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<DriveDocument>> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private String folderId = "";

        public MakeRequestTask(GoogleAccountCredential credential, String folderId) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("BYOP")
                    .build();

            this.folderId = folderId;
        }

        /**
         * Background task to call Drive API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<DriveDocument> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of up to 10 file names and IDs.
         *
         * @return List of Strings describing files, or an empty list if no files
         * found.
         * @throws IOException
         */
        private List<DriveDocument> getDataFromApi() throws IOException {
            List<DriveDocument> documentsList = new ArrayList<DriveDocument>();


            FileList result = mService.files().list().setQ("\'" + this.folderId + "\' in parents").execute();

            List<File> files = result.getFiles();
            if (files != null) {
                for (File file : files) {
                    DriveDocument document = new DriveDocument();
                    document.setTitle(file.getName());
                    document.setExtension(file.getFileExtension());
                    document.setDriveId(file.getId());
                    documentsList.add(document);
                }
            }
            return documentsList;
        }


        @Override
        protected void onPostExecute(List<DriveDocument> output) {
            fillAdapter(output);
            refreshLayout.setRefreshing(false);
        }

        @Override
        protected void onCancelled() {

            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    AndroidUtils.showMessage(("The following error occurred:\n"
                            + mLastError.getMessage()), getActivity());
                }
            } else {
                AndroidUtils.showMessage("Request cancelled.", getActivity());
            }
        }
    }
}
