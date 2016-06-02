package gsoc.google.com.byop.ui.poisList;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.model.File;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.model.DriveDocument;
import gsoc.google.com.byop.model.POI;
import gsoc.google.com.byop.model.Point;
import gsoc.google.com.byop.utils.AndroidUtils;
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;
import gsoc.google.com.byop.utils.GooglePlayUtils;
import gsoc.google.com.byop.utils.StringUtils;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * Created by lgwork on 30/05/16.
 */
public class EditPOIDataFragment extends Fragment implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, EasyPermissions.PermissionCallbacks {


    public static final String POI_LOCATION_LON = "LONGITUDE";
    public static final String POI_LOCATION_LAT = "LATITUDE";
    public static final String POI_NAME = "POI_NAME";
    public static final String POI_DESC = "POI_DESC";
    public double newPoiLatitude;
    public double newPoiLongitude;
    public String actualPOIName;
    public String actualPOIDescription;
    protected FragmentStackManager fragmentStackManager;
    POI managedPoi;
    GoogleAccountCredential mCredential;
    private EditText poi_name_input;
    private EditText poi_description_input;
    private EditText poi_latitude_input;
    private EditText poi_longitude_input;
    private EditPOITask editPoiTask;
    private Button saveChanges;

    private GoogleApiClient mGoogleApiClient;

    private DriveDocument driveDocument;

    public static EditPOIDataFragment newInstance(double poiLatitude, double poiLongitude, String poiName, String poiDescription) {
        EditPOIDataFragment editPoiDataFragment = new EditPOIDataFragment();
        Bundle bundle = new Bundle();
        bundle.putDouble(POI_LOCATION_LON, poiLongitude);
        bundle.putDouble(POI_LOCATION_LAT, poiLatitude);
        bundle.putString(POI_NAME, poiName);
        bundle.putString(POI_DESC, poiDescription);
        editPoiDataFragment.setArguments(bundle);
        return editPoiDataFragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {/*Do Nothing*/}

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {/*Do Nothing*/}

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
                    editPoiThroughApi();
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
                        editPoiThroughApi();
                    }
                }
                break;
            case Constants.REQUEST_AUTHORIZATION:
                if (resultCode == Constants.RESULT_OK) {
                    editPoiThroughApi();
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    @NonNull
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        fragmentStackManager = FragmentStackManager.getInstance(getActivity());

        View rootView = inflater.inflate(R.layout.edit_poi_dialog, null);

        newPoiLatitude = getArguments().getDouble(POI_LOCATION_LAT);
        newPoiLongitude = getArguments().getDouble(POI_LOCATION_LON);
        actualPOIName = getArguments().getString(POI_NAME);
        actualPOIDescription = getArguments().getString(POI_DESC);

        poi_name_input = (EditText) rootView.findViewById(R.id.edit_poi_name_input);
        poi_description_input = (EditText) rootView.findViewById(R.id.edit_poi_desc_input);
        poi_latitude_input = (EditText) rootView.findViewById(R.id.edit_poi_latitude_input);
        poi_longitude_input = (EditText) rootView.findViewById(R.id.edit_poi_longitude_input);

        poi_name_input.setText(actualPOIName);
        poi_description_input.setText(actualPOIDescription);
        poi_latitude_input.setText(String.valueOf(newPoiLatitude));
        poi_longitude_input.setText(String.valueOf(newPoiLongitude));


        saveChanges = (Button) rootView.findViewById(R.id.btn_edit_poi);

        mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        saveChanges.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                POI editedPoi = new POI();
                editedPoi.setName(poi_name_input.getText().toString());
                editedPoi.setDescription(poi_description_input.getText().toString());
                Point point = new Point();
                point.setLatitude(poi_latitude_input.getText().toString());
                point.setLongitude(poi_longitude_input.getText().toString());
                editedPoi.setPoint(point);

                editPoiThroughApi();
            }
        });

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(getContext(), Arrays.asList(Constants.SCOPES))
                .setBackOff(new ExponentialBackOff());

        return rootView;
    }

    private void editPoiThroughApi() {
        if (!GooglePlayUtils.isGooglePlayServicesAvailable(getActivity())) {
            GooglePlayUtils.acquireGooglePlayServices(getActivity());
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccountForEdition();
        } else if (!GooglePlayUtils.isDeviceOnline(getActivity())) {
            AndroidUtils.showMessage("No network connection available.", getActivity());
        } else {
            POI editedPoi = new POI();
            editedPoi.setName(poi_name_input.getText().toString());
            editedPoi.setDescription(poi_description_input.getText().toString());
            Point point = new Point();
            point.setLatitude(poi_latitude_input.getText().toString());
            point.setLongitude(poi_longitude_input.getText().toString());
            editedPoi.setPoint(point);

            editPoiTask = new EditPOITask(mCredential, editedPoi);
            editPoiTask.execute();
        }
    }

    @AfterPermissionGranted(Constants.REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccountForEdition() {
        if (EasyPermissions.hasPermissions(
                getActivity(), Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getActivity().getPreferences(Context.MODE_PRIVATE)
                    .getString(Constants.PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                editPoiThroughApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        Constants.REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    getActivity(),
                    "This app needs to access your Google account (via Contacts).",
                    Constants.REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }


    @Override
    public void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    @Override
    public void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {/*Do Nothing*/}

    @Override
    public void onConnectionSuspended(int i) {/*Do Nothing*/}

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {/*Do Nothing*/}

    public void setDriveDocument(DriveDocument driveDoc) {
        this.driveDocument = driveDoc;
    }

    public void setManagedPoi(POI managedPoi) {
        this.managedPoi = managedPoi;
    }

    private class EditPOITask extends AsyncTask<Void, Void, Void> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private ProgressDialog dialog;
        private POI editedPoi = null;

        private boolean isCompleted = false;

        public EditPOITask(GoogleAccountCredential credential, POI meditedPoi) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();

            editedPoi = meditedPoi;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (dialog == null) {
                dialog = new ProgressDialog(getActivity());
                dialog.setMessage(getActivity().getResources().getString(R.string.saving));
                dialog.setIndeterminate(false);
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        editPoiTask.cancel(true);
                    }
                });
                dialog.show();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                getFileContentsFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
            }
            return null;
        }

        private void getFileContentsFromApi() throws IOException {

            File driveFile = mService.files().get(driveDocument.getResourceId()).execute();

            //DRIVE API
            Drive.DriveApi.fetchDriveId(mGoogleApiClient, driveFile.getId()).setResultCallback(new ResultCallback<DriveApi.DriveIdResult>() {
                @Override
                public void onResult(@NonNull DriveApi.DriveIdResult driveIdResult) {
                    if (driveIdResult.getStatus().isSuccess()) {
                        final DriveFile[] file = {Drive.DriveApi.getFile(mGoogleApiClient, driveIdResult.getDriveId())};
                        file[0].open(mGoogleApiClient, DriveFile.MODE_READ_WRITE, null)
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
                                        ParcelFileDescriptor parcelFileDescriptor = contents.getParcelFileDescriptor();
                                        FileInputStream fileInputStream = new FileInputStream(parcelFileDescriptor
                                                .getFileDescriptor());

                                        String newContents = editPOIContents(fileInputStream, managedPoi, editedPoi);

                                        //Update the file contents
                                        FileOutputStream fileOutputStream = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());

                                        try {
                                            fileOutputStream.getChannel().position(0);
                                            Writer writer = new OutputStreamWriter(fileOutputStream);
                                            writer.write(newContents);
                                            writer.close();

                                            MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setMimeType("text/xml").build();

                                            contents.commit(mGoogleApiClient, changeSet).setResultCallback(new ResultCallback<com.google.android.gms.common.api.Status>() {
                                                @Override
                                                public void onResult(com.google.android.gms.common.api.Status result) {
                                                    //Do nothing
                                                }
                                            });
                                            isCompleted = true;
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                    }
                }
            });
            while (!isCompleted) {/*wait for complete*/}

            return;
        }


        private String editPOIContents(FileInputStream fileInputStream, POI managedPoi, POI editedPoi) {
            String originalPOIStr = "      <Placemark>\n" +
                    "        <name>" + managedPoi.getName() + "</name>\n" +
                    "        <description>" + managedPoi.getDescription() + "</description>\n" +
                    "        <Point>\n" +
                    "          <coordinates>" + managedPoi.getPoint().getLongitude() + "," + managedPoi.getPoint().getLatitude() + ",0</coordinates>\n" +
                    "        </Point>\n" +
                    "      </Placemark>";

            String xmlString = StringUtils.getStringFromInputStream(fileInputStream);

            String editedPOIStr = "      <Placemark>\n" +
                    "        <name>" + editedPoi.getName() + "</name>\n" +
                    "        <description>" + editedPoi.getDescription() + "</description>\n" +
                    "        <Point>\n" +
                    "          <coordinates>" + editedPoi.getPoint().getLongitude() + "," + editedPoi.getPoint().getLatitude() + ",0</coordinates>\n" +
                    "        </Point>\n" +
                    "      </Placemark>";

            String newStr = xmlString.trim().replaceAll(originalPOIStr.trim(), editedPOIStr.trim());

            return newStr;
        }


        @Override
        protected void onPostExecute(Void aVoid) {
            View view = getActivity().getCurrentFocus();
            if (view != null) {
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }

            if (dialog != null && dialog.isShowing())
                dialog.hide();

            fragmentStackManager.popBackStatFragment();
        }

        @Override
        protected void onCancelled() {

            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    GooglePlayUtils.showGooglePlayServicesAvailabilityErrorDialog(getActivity(),
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    getActivity().startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            Constants.REQUEST_AUTHORIZATION);
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
