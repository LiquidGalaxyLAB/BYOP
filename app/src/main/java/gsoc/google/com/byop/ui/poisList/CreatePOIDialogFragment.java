package gsoc.google.com.byop.ui.poisList;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

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

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.model.DriveDocument;
import gsoc.google.com.byop.utils.AndroidUtils;
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;
import gsoc.google.com.byop.utils.GooglePlayUtils;
import gsoc.google.com.byop.utils.StringUtils;

/**
 * Created by lgwork on 31/05/16.
 */
public class CreatePOIDialogFragment extends Fragment implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String ARG_LATITUDE = "latitude";
    public static final String ARG_LONGITUDE = "longitude";
    public static final String ARG_DOCUMENT = "document";

    protected FragmentStackManager fragmentStackManager;
    GoogleAccountCredential mCredential;
    private GoogleApiClient googleApiClient;
    private DriveDocument document;
    private double latitude;
    private double longitude;

    private String accountEmail;

    private TextInputLayout new_poi_name;

    private EditText poi_name_input;
    private EditText poi_description_input;
    private TextView poi_longitude_input;

    private CreateTask createTask;

    public static CreatePOIDialogFragment newInstance(double latitude, double longitude, DriveDocument document) {
        CreatePOIDialogFragment createPOIDialogFragment = new CreatePOIDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(ARG_DOCUMENT, document);
        bundle.putDouble(ARG_LATITUDE, latitude);
        bundle.putDouble(ARG_LONGITUDE, longitude);

        createPOIDialogFragment.setArguments(bundle);
        return createPOIDialogFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        fragmentStackManager = FragmentStackManager.getInstance(getActivity());

        View rootView = inflater.inflate(R.layout.create_poi_dialog, container, false);

        document = getArguments().getParcelable(ARG_DOCUMENT);
        latitude = getArguments().getDouble(ARG_LATITUDE);
        longitude = getArguments().getDouble(ARG_LONGITUDE);


        new_poi_name = (TextInputLayout) rootView.findViewById(R.id.create_poi_name);

        poi_name_input = (EditText) rootView.findViewById(R.id.create_poi_name_input);
        poi_description_input = (EditText) rootView.findViewById(R.id.create_poi_desc_input);
        TextView poi_latitude_input = (TextView) rootView.findViewById(R.id.create_poi_latitude_input);
        poi_longitude_input = (TextView) rootView.findViewById(R.id.create_poi_longitude_input);


        poi_latitude_input.setText(getResources().getString(R.string.latitude) + String.valueOf(latitude));
        poi_longitude_input.setText(getResources().getString(R.string.longitude) + String.valueOf(longitude));


        Button saveChanges = (Button) rootView.findViewById(R.id.btn_create_poi);

        saveChanges.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Resources res = getActivity().getResources();

                if (poi_name_input.getText().toString().length() == 0) {
                    new_poi_name.setError(res.getString(R.string.empty_name_error));
                } else {
                    new_poi_name.setErrorEnabled(false);
                    createTask = new CreateTask(mCredential, poi_name_input.getText().toString(), poi_description_input.getText().toString(), latitude, longitude, document);
                    createTask.execute();
                }
            }
        });

        googleApiClient = new GoogleApiClient.Builder(getActivity())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .build();

        SharedPreferences settings = this.getActivity().getPreferences(Context.MODE_PRIVATE);

        accountEmail = settings.getString(Constants.PREF_ACCOUNT_EMAIL, "");

        mCredential = GoogleAccountCredential.usingOAuth2(getContext(), Arrays.asList(Constants.SCOPES))
                .setBackOff(new ExponentialBackOff());

        mCredential.setSelectedAccountName(accountEmail);

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        googleApiClient.disconnect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {/*Do Nothing*/}

    @Override
    public void onConnectionSuspended(int i) {/*Do Nothing*/ }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {/*Do Nothing*/}


    private class CreateTask extends AsyncTask<Void, Void, Void> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private String name = "";
        private String description = "";
        private double latitude;
        private double longitude;
        private DriveDocument document;

        private boolean isCompleted = false;

        private ProgressDialog dialog;

        public CreateTask(GoogleAccountCredential mCredential, String name, String description, double latitude, double longitude, DriveDocument document) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, mCredential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();

            this.name = name;
            this.description = description;
            this.latitude = latitude;
            this.longitude = longitude;
            this.document = document;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (dialog == null) {
                dialog = new ProgressDialog(getContext());
                dialog.setMessage(getActivity().getResources().getString(R.string.saving));
                dialog.setIndeterminate(false);
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        createTask.cancel(true);
                    }
                });
                dialog.show();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                addPoi();
            } catch (Exception e) {
                e.printStackTrace();
                mLastError = e;
                cancel(true);
            }
            return null;
        }

        private void addPoi() throws IOException {
            File driveFile = mService.files().get(document.getResourceId()).execute();

            //DRIVE API
            Drive.DriveApi.fetchDriveId(googleApiClient, driveFile.getId()).setResultCallback(new ResultCallback<DriveApi.DriveIdResult>() {
                @Override
                public void onResult(@NonNull DriveApi.DriveIdResult driveIdResult) {
                    if (driveIdResult.getStatus().isSuccess()) {
                        final DriveFile[] file = {Drive.DriveApi.getFile(googleApiClient, driveIdResult.getDriveId())};
                        file[0].open(googleApiClient, DriveFile.MODE_READ_WRITE, null)
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

                                        String newContents = addPoiContents(fileInputStream, name, description, latitude, longitude);

                                        //Update the file contents
                                        FileOutputStream fileOutputStream = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());

                                        try {
                                            fileOutputStream.getChannel().position(0);
                                            Writer writer = new OutputStreamWriter(fileOutputStream);
                                            writer.write(newContents);
                                            writer.close();

                                            MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setMimeType("text/xml").build();

                                            contents.commit(googleApiClient, changeSet).setResultCallback(new ResultCallback<com.google.android.gms.common.api.Status>() {
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

        private String addPoiContents(FileInputStream fileInputStream, String name, String description, double latitude, double longitude) {

            String xmlString = StringUtils.getStringFromInputStream(fileInputStream);

            String newPoiStr = "      <Placemark>\n" +
                    "        <name>" + name + "</name>\n" +
                    "        <description>" + description + "</description>\n" +
                    "        <Point>\n" +
                    "          <coordinates>" + longitude + "," + latitude + ",0</coordinates>\n" +
                    "        </Point>\n" +
                    "      </Placemark>\n" +
                    "</Folder>";

            return xmlString.replaceAll("</Folder>", newPoiStr);
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
            fragmentStackManager.popBackStatFragment();
        }

        @Override
        protected void onCancelled() {

            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    GooglePlayUtils.showGooglePlayServicesAvailabilityErrorDialog(getActivity(), ((GooglePlayServicesAvailabilityIOException) mLastError)
                            .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            Constants.REQUEST_AUTHORIZATION);
                } else {
                    AndroidUtils.showMessage((getResources().getString(R.string.following_error) + "\n"
                            + mLastError.getMessage()), getActivity());
                }
            } else {
                AndroidUtils.showMessage(getResources().getString(R.string.request_cancelled), getActivity());
            }
        }
    }


}
