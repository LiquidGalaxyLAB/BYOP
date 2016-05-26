package gsoc.google.com.byop.ui.documentsList;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
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
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.utils.AndroidUtils;
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;
import gsoc.google.com.byop.utils.GooglePlayUtils;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * Created by lgwork on 25/05/16.
 */
public class CreateDocumentFragment extends Fragment  implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
    private static String TAG = CreateDocumentFragment.class.toString();
    protected FragmentStackManager fragmentStackManager;

    public static final String ARG_FOLDER_ID = "folderId";
    public static final String ARG_API_CLIENT = "apliClient";

    private EditText new_document_name_input;

    private TextInputLayout new_document_name;

    private Button saveDocument;

    private CreateTask createTask;

    private String folderId;

    GoogleAccountCredential mCredential;

    /**
     * Google API client.
     */
    private GoogleApiClient mGoogleApiClient;



    public static CreateDocumentFragment newInstance(String folderId) {
        CreateDocumentFragment createDocument = new CreateDocumentFragment();
        Bundle bundle = new Bundle();
        bundle.putString(ARG_FOLDER_ID, folderId);
        createDocument.setArguments(bundle);
        return createDocument;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.add_new_document, container, false);
        fragmentStackManager = FragmentStackManager.getInstance(getActivity());

        saveDocument = (Button) rootView.findViewById(R.id.btn_add_document);


        new_document_name_input = (EditText) rootView.findViewById(R.id.new_document_name_input);

        new_document_name = (TextInputLayout) rootView.findViewById(R.id.new_document_name);

        saveDocument.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Resources res = getActivity().getResources();

                if (new_document_name_input.getText().toString().length() == 0) {
                    new_document_name.setError(res.getString(R.string.empty_name_error));
                }else{
                    new_document_name.setErrorEnabled(false);
                    createFileThroughApi(new_document_name_input);
                }


            }
        });

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

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        folderId = getArguments().getString(ARG_FOLDER_ID);
    }
/*

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
*/


    private void createFileThroughApi(EditText documentName){
        if (!GooglePlayUtils.isGooglePlayServicesAvailable(this.getActivity())) {
            GooglePlayUtils.acquireGooglePlayServices(this.getActivity());
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccountForCreation(documentName);
        } else if (!GooglePlayUtils.isDeviceOnline(this.getActivity())) {
            AndroidUtils.showMessage("No network connection available.", getActivity());
        } else {
            CreateTask createTask = new CreateTask(mCredential,documentName,this.folderId);
            createTask.execute();
        }
    }

    @AfterPermissionGranted(Constants.REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccountForCreation(EditText documentName){
        if (EasyPermissions.hasPermissions(
                this.getActivity(), Manifest.permission.GET_ACCOUNTS)) {
            String accountName = this.getActivity().getPreferences(Context.MODE_PRIVATE)
                    .getString(Constants.PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                createFileThroughApi(documentName);
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
                Constants.REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        //AndroidUtils.showMessage("Connected", getActivity());
    }

    @Override
    public void onConnectionSuspended(int i) {
        //Do nothing
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //Do nothing
    }


    private class CreateTask extends AsyncTask<Void, Void, Void> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private String documentName = "";
        private String folderId = "";

        private File newDocument;


        public CreateTask(GoogleAccountCredential credential, EditText documentName,String folderId) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("BYOP")
                    .build();

            this.documentName = documentName.getText().toString();
            this.folderId = folderId;
        }

        /**
         * Background task to call Drive API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected Void doInBackground(Void... params) {
            try {
                createFile();
            } catch (Exception e) {
                e.printStackTrace();
                mLastError = e;
                cancel(true);
            }
            return null;
        }

        /**
         * Fetch a list of up to 10 file names and IDs.
         *
         * @return List of Strings describing files, or an empty list if no files
         * found.
         * @throws IOException
         */
        private void createFile() throws IOException {
            File fileMetadata = new File();
            fileMetadata.setName(this.documentName+".xml");
            List<String> parents = new ArrayList<String>();
            parents.add(this.folderId);
            fileMetadata.setParents(parents);
            fileMetadata.setMimeType("text/xml");

            FileContent xmlSkeleton = addXmlSkeleton();


           newDocument =  mService.files().create(fileMetadata,xmlSkeleton).execute();
        }

        private FileContent addXmlSkeleton() throws IOException {
            //We add the xml format
            String contentStr = getKMLSkeleton();

            File newFile = new File();
            newFile.setName(this.documentName+".xml");
            newFile.setMimeType("text/xml");

            java.io.File outputDir = getContext().getCacheDir(); // context being the Activity pointer
            java.io.File outputFile = java.io.File.createTempFile("prefix", "extension", outputDir);

            BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
            bw.write(contentStr);
            bw.close();

            FileContent mediaContent = new FileContent("text/xml", outputFile);

            return mediaContent;
        }

        private String getKMLSkeleton() {
            String str = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n" +
                    "  <Document>\n" +
                    "    <name></name>\n" +
                    "    <open></open>\n" +
                    "    <description></description>\n" +
                    "    <Folder>\n" +
                    "  </Folder>\n" +
                    "</Document>\n" +
                    "</kml>";

            return str;
        }


        @Override
        protected void onPostExecute(Void aVoid) {
            View view = getActivity().getCurrentFocus();
            if (view != null) {
                InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
           fragmentStackManager.popBackStatFragment();
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


}
