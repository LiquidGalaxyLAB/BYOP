package gsoc.google.com.byop.ui.poisList;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.model.File;
import com.poliveira.parallaxrecyclerview.ParallaxRecyclerAdapter;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.model.DriveDocument;
import gsoc.google.com.byop.model.POI;
import gsoc.google.com.byop.utils.AndroidUtils;
import gsoc.google.com.byop.utils.BYOPXmlPullParser;
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;
import gsoc.google.com.byop.utils.GooglePlayUtils;
import gsoc.google.com.byop.utils.POIUtils;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * Created by lgwork on 26/05/16.
 */
public class POISListFragment extends Fragment implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, EasyPermissions.PermissionCallbacks {
    protected FragmentStackManager fragmentStackManager;


    private static String TAG = POISListFragment.class.toString();
    private RecyclerView rv = null;
    private ParallaxRecyclerAdapter<POI> parallaxRecyclerAdapter;
    private SwipeRefreshLayout refreshLayout;
    private RequestContentsTask requestContentsTask;
    private FloatingActionButton fab;

    List<POI> poisList = new ArrayList<POI>();

    public static final String ARG_DOCUMENT = "document";
    private DriveDocument document;

    private POIUtils poiUtils;

    private static POISListFragment poisFragment;


    /**
     * Google API client.
     */
    private GoogleApiClient mGoogleApiClient;

    /**
     * Needed for  DRIVE REST API V3
     */
    GoogleAccountCredential mCredential;


    public static POISListFragment newInstance(DriveDocument document) {
        poisFragment = new POISListFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(ARG_DOCUMENT, document);

        poisFragment.setArguments(bundle);
        return poisFragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        document = getArguments().getParcelable(ARG_DOCUMENT);
        getActivity().setTitle(getActivity().getResources().getString(R.string.app_name) + " " + getResources().getString(R.string.poisList) + ": " + document.getTitle());

        setHasOptionsMenu(true);


        fragmentStackManager = FragmentStackManager.getInstance(getActivity());

        refreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        populateUI();
                    }
                }
        );

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //    CreateDocumentFragment newDocumentFragment = CreateDocumentFragment.newInstance(folderId);
                //  fragmentStackManager.loadFragment(newDocumentFragment, R.id.main_layout);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.pois_list, container, false);

        rv = (RecyclerView) rootView.findViewById(R.id.rvPOIS);
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        rv.setLayoutManager(llm);
        rv.setHasFixedSize(true);
        refreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipeRefreshPois);
        fab = (FloatingActionButton) rootView.findViewById(R.id.add_POI);

        mGoogleApiClient = new GoogleApiClient.Builder(this.getActivity())
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(getContext(), Arrays.asList(Constants.SCOPES))
                .setBackOff(new ExponentialBackOff());


        return rootView;
    }

    private void populateUI() {
        requestContentsTask = new RequestContentsTask(mCredential, document);
        requestContentsTask.execute();

    }

    private void getFileContensFromAPI() {
        if (!GooglePlayUtils.isGooglePlayServicesAvailable(this.getActivity())) {
            GooglePlayUtils.acquireGooglePlayServices(this.getActivity());
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!GooglePlayUtils.isDeviceOnline(this.getActivity())) {
            AndroidUtils.showMessage("No network connection available.", getActivity());
        } else {
            new RequestContentsTask(mCredential, document).execute();
        }
    }


    @AfterPermissionGranted(Constants.REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this.getActivity(), Manifest.permission.GET_ACCOUNTS)) {
            String accountName = this.getActivity().getPreferences(Context.MODE_PRIVATE)
                    .getString(Constants.PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getFileContensFromAPI();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        Constants.REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this.getActivity(),
                    "This app needs to access your Google account (via Contacts).",
                    Constants.REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
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
        getFileContensFromAPI();
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
            case Constants.REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != Constants.RESULT_OK) {
                    AndroidUtils.showMessage(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.", getActivity());
                } else {
                    getFileContensFromAPI();
                }
                break;
            case Constants.REQUEST_ACCOUNT_PICKER:
                if (resultCode == Constants.RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                this.getActivity().getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(Constants.PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getFileContensFromAPI();
                    }
                }
                break;
            case Constants.REQUEST_AUTHORIZATION:
                if (resultCode == Constants.RESULT_OK) {
                    getFileContensFromAPI();
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


    private class POIHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {
        CardView cv;
        TextView poiName;
        TextView poiDescription;
        TextView poiLatitude;
        TextView poiLongitude;
        ImageView filePhoto;

        String latitude;
        String longitude;


        public POIHolder(View itemView) {
            super(itemView);
            poiName = (TextView) itemView.findViewById(R.id.poi_name);
            poiDescription = (TextView) itemView.findViewById(R.id.poi_description);
            filePhoto = (ImageView) itemView.findViewById(R.id.file_photo_poi);
            poiLatitude = (TextView) itemView.findViewById(R.id.poi_latitude);
            poiLongitude = (TextView) itemView.findViewById(R.id.poi_longitude);
            itemView.setOnCreateContextMenuListener(this);
        }


        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            menu.setHeaderTitle(getResources().getString(R.string.context_menu_title));

            MenuItem deleteItem =  menu.add(0, v.getId(), 0, R.string.context_menu_delete);
            deleteItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener(){
                @Override
                public boolean onMenuItemClick(MenuItem item) {

                    AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                    alert.setTitle(getResources().getString(R.string.are_you_sure));

                    alert.setPositiveButton(getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            //Delete POI
                            poiUtils = new POIUtils(poiName.getText().toString(), poiDescription.getText().toString(),
                                    latitude, longitude, document, mGoogleApiClient, mCredential, getActivity(), poisFragment);

                            poiUtils.deletePOI();

                            populateUI();
                        }
                    });

                    alert.setNegativeButton(getResources().getString(R.string.no),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                }
                            });

                    alert.show();
                    return true;
                }
            });


            MenuItem editItem =  menu.add(0, v.getId(), 0, R.string.context_menu_edit);
            editItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener(){
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    //TODO: Edit POI
//                    RenameDocumentFragment renameDocumentFragment = RenameDocumentFragment.newInstance(fileResourceId,documentTitle.getText().toString());
//                    fragmentStackManager.loadFragment(renameDocumentFragment, R.id.main_layout);
                    return true;
                }
            });

            MenuItem viewItem = menu.add(0, v.getId(), 0, R.string.context_menu_view);
            viewItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    //View POI
                    POIMapFragment poiMapFragment = POIMapFragment.newInstance(latitude, longitude, poiName.getText().toString(), poiDescription.getText().toString());
                    fragmentStackManager.loadFragment(poiMapFragment, R.id.main_layout);
                    return true;
                }
            });

        }
    }

    public void fillAdapter(final List<POI> poisList) {
        parallaxRecyclerAdapter = new ParallaxRecyclerAdapter<POI>(poisList) {
            @Override
            public void onBindViewHolderImpl(RecyclerView.ViewHolder viewHolder, ParallaxRecyclerAdapter<POI> parallaxRecyclerAdapter, int i) {
                POI poi = parallaxRecyclerAdapter.getData().get(i);


                POIHolder poiHolder = (POIHolder) viewHolder;
                poiHolder.poiName.setText(poi.getName());
                poiHolder.poiDescription.setText(poi.getDescription());

                poiHolder.poiLatitude.setText("Lat:" + poi.getPoint().getLatitude());
                poiHolder.poiLongitude.setText("Lon:" + poi.getPoint().getLongitude());

                poiHolder.latitude = poi.getPoint().getLatitude();
                poiHolder.longitude = poi.getPoint().getLongitude();

                poiHolder.filePhoto.setImageDrawable(ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.poi_icon));
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolderImpl(ViewGroup viewGroup, ParallaxRecyclerAdapter<POI> parallaxRecyclerAdapter, int i) {
                return new POIHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.poi_list_item_card, viewGroup, false));
            }

            @Override
            public int getItemCountImpl(ParallaxRecyclerAdapter<POI> parallaxRecyclerAdapter) {
                return poisList.size();
            }


        };


        parallaxRecyclerAdapter.setParallaxHeader(getActivity().getLayoutInflater().inflate(R.layout.poi_list_header_layout, rv, false), rv);

        rv.setAdapter(parallaxRecyclerAdapter);


        //On click on recycler view item
        parallaxRecyclerAdapter.setOnClickEvent(new ParallaxRecyclerAdapter.OnClickEvent() {
            @Override
            public void onClick(View view, int i) {
                POI poi = poisList.get(i);
                POIMapFragment poiMapFragment = POIMapFragment.newInstance(poi.getPoint().getLatitude(), poi.getPoint().getLongitude(),
                        poi.getName(), poi.getDescription());
                fragmentStackManager.loadFragment(poiMapFragment, R.id.main_layout);
            }
        });
    }


    /**
     * An asynchronous task that handles the Drive API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class RequestContentsTask extends AsyncTask<Void, Void, List<POI>> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private String folderId = "";
        private ProgressDialog dialog;

        private List<POI> innerPOIList = new ArrayList<POI>();
        private boolean isCompleted = false;

        public RequestContentsTask(GoogleAccountCredential credential, DriveDocument document) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();


        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (dialog == null) {
                dialog = new ProgressDialog(getContext());
                dialog.setMessage(getActivity().getResources().getString(R.string.loading));
                dialog.setIndeterminate(false);
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        requestContentsTask.cancel(true);
                    }
                });
                dialog.show();
            }
        }

        /**
         * Background task to call Drive API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<POI> doInBackground(Void... params) {
            try {
                return getFileContentsFromApi();
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
        private List<POI> getFileContentsFromApi() throws IOException {

            File driveFile = mService.files().get(document.getResourceId()).execute();

            //DRIVE API
            Drive.DriveApi.fetchDriveId(mGoogleApiClient, driveFile.getId()).setResultCallback(new ResultCallback<DriveApi.DriveIdResult>() {
                @Override
                public void onResult(@NonNull DriveApi.DriveIdResult driveIdResult) {
                    if (driveIdResult.getStatus().isSuccess()) {
                        final DriveFile[] file = {Drive.DriveApi.getFile(getGoogleApiClient(), driveIdResult.getDriveId())};
                        file[0].open(mGoogleApiClient, DriveFile.MODE_READ_ONLY, null)
                                .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                                    @Override
                                    public void onResult(@NonNull DriveApi.DriveContentsResult driveContentsResult) {
                                        if (!driveContentsResult.getStatus().isSuccess()) {
                                            // display an error saying file can't be opened
                                            return;
                                        }
                                        // DriveContents object contains pointers
                                        // to the actual byte stream
                                        DriveContents contents = driveContentsResult.getDriveContents();
                                        try {

                                            innerPOIList = checkContents(contents.getInputStream());
                                            isCompleted = true;
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        } catch (XmlPullParserException e) {
                                            e.printStackTrace();
                                        }

                                    }
                                });
                    } else {
                        AndroidUtils.showMessage("Something went wrong:" + driveIdResult.getStatus(), getActivity());
                        fragmentStackManager.popBackStatFragment();
                        return;
                    }
                }
            });
            while (!isCompleted) {/*wait for complete*/}
            return innerPOIList;
        }


        @Override
        protected void onPostExecute(List<POI> output) {
            super.onPostExecute(output);
            if (output != null)
                fillAdapter(output);
                if (dialog != null && dialog.isShowing())
                    dialog.hide();
            refreshLayout.setRefreshing(false);
        }

        @Override
        protected void onCancelled() {

            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    GooglePlayUtils.showGooglePlayServicesAvailabilityErrorDialog(getActivity(),
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            Constants.REQUEST_AUTHORIZATION);
                } else {
                   /* AndroidUtils.showMessage(("The following error occurred:\n"
                            + mLastError.getMessage()), getActivity());*/
                }
            } else {
                AndroidUtils.showMessage("Request cancelled.", getActivity());
            }
        }
    }

    private List<POI> checkContents(InputStream inputStream) throws IOException, XmlPullParserException {
        BYOPXmlPullParser parser = new BYOPXmlPullParser();
        List<POI> poiList = parser.parse(inputStream);

        return poiList;
    }
}
