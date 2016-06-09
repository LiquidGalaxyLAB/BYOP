package gsoc.google.com.byop.ui.documentsList;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
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
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.model.File;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.utils.AndroidUtils;
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;
import gsoc.google.com.byop.utils.GooglePlayUtils;

/**
 * Created by lgwork on 25/05/16.
 */
public class CreateDocumentFragment extends Fragment implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String ARG_FOLDER_ID = "folderId";
    protected FragmentStackManager fragmentStackManager;
    GoogleAccountCredential mCredential;
    private EditText new_document_name_input;
    private TextInputLayout new_document_name;
    private EditText new_document_description_input;
    private CreateTask createTask;
    private MakePermissionsTask permissionsTask;
    private String folderId;
    private String accountEmail;
    private String documentId;

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

        Button saveDocument = (Button) rootView.findViewById(R.id.btn_add_document);

        new_document_name_input = (EditText) rootView.findViewById(R.id.new_document_name_input);
        new_document_name = (TextInputLayout) rootView.findViewById(R.id.new_document_name);

        new_document_description_input = (EditText) rootView.findViewById(R.id.new_document_description_input);

        saveDocument.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Resources res = getActivity().getResources();

                if (new_document_name_input.getText().toString().length() == 0) {
                    new_document_name.setError(res.getString(R.string.empty_name_error));
                } else {
                    new_document_name.setErrorEnabled(false);
                    createTask = new CreateTask(mCredential, new_document_name_input, new_document_description_input, folderId);
                    createTask.execute();
                }
            }
        });

        SharedPreferences settings = this.getActivity().getPreferences(Context.MODE_PRIVATE);

        accountEmail = settings.getString(Constants.PREF_ACCOUNT_EMAIL, "");

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(getContext(), Arrays.asList(Constants.SCOPES))
                .setBackOff(new ExponentialBackOff());

        mCredential.setSelectedAccountName(accountEmail);

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        folderId = getArguments().getString(ARG_FOLDER_ID);
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {/*Do Nothing*/}

    @Override
    public void onConnectionSuspended(int i) {/*Do Nothing*/}

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {/*Do Nothing*/}


    private class CreateTask extends AsyncTask<Void, Void, Void> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private String documentName = "";
        private String documentDescription;
        private String folderId = "";

        private ProgressDialog dialog;

        public CreateTask(GoogleAccountCredential credential, EditText documentName, EditText documentDescription, String folderId) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();

            this.documentName = documentName.getText().toString();
            this.documentDescription = documentDescription != null ? documentDescription.getText().toString() : "";
            this.folderId = folderId;
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
                createFile();
            } catch (Exception e) {
                e.printStackTrace();
                mLastError = e;
                cancel(true);
            }
            return null;
        }

        private void createFile() throws IOException {
            File fileMetadata = new File();
            fileMetadata.setName(this.documentName + ".xml");
            fileMetadata.setDescription(this.documentDescription);

            List<String> parents = new ArrayList<>();
            parents.add(this.folderId);
            fileMetadata.setParents(parents);
            fileMetadata.setMimeType("text/xml");

            FileContent xmlSkeleton = addXmlSkeleton();

            File newDriveDocument = mService.files().create(fileMetadata, xmlSkeleton).execute();
            documentId = newDriveDocument.getId();
        }

        private FileContent addXmlSkeleton() throws IOException {
            //We add the xml format
            String contentStr = getKMLSkeleton();

            java.io.File outputDir = getContext().getCacheDir(); // context being the Activity pointer
            java.io.File outputFile = java.io.File.createTempFile("prefix", "extension", outputDir);

            BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
            bw.write(contentStr);
            bw.close();

            return new FileContent("text/xml", outputFile);
        }

        private String getKMLSkeleton() {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n" +
                    "  <Document>\n" +
                    "    <name></name>\n" +
                    "    <open></open>\n" +
                    "    <description></description>\n" +
                    "    <Folder>\n" +
                    "  </Folder>\n" +
                    "</Document>\n" +
                    "</kml>";
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

            permissionsTask = new MakePermissionsTask(mCredential, documentId);
            permissionsTask.execute();

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


    private class MakePermissionsTask extends AsyncTask<Void, Void, Void> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private String documentId = "";
        private ProgressDialog dialog;

        public MakePermissionsTask(GoogleAccountCredential credential, String documentId) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();

            this.documentId = documentId;
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
                        permissionsTask.cancel(true);
                    }
                });
                dialog.show();
            }
        }


        @Override
        protected Void doInBackground(Void... params) {
            try {
                setViewPermissions();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
            }
            return null;
        }

        private void setViewPermissions() throws IOException {

            com.google.api.services.drive.model.Permission permission = new com.google.api.services.drive.model.Permission();
            permission.setType("anyone");
            permission.setRole("reader");

            mService.permissions().create(this.documentId, permission).execute();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            if (dialog != null && dialog.isShowing())
                dialog.hide();
            //refreshLayout.setRefreshing(false);
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
                    AndroidUtils.showMessage((getResources().getString(R.string.following_error) + "\n"
                            + mLastError.getMessage()), getActivity());
                }
            } else {
                AndroidUtils.showMessage(getResources().getString(R.string.request_cancelled), getActivity());
            }
        }
    }


}
