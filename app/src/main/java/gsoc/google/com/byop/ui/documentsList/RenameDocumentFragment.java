package gsoc.google.com.byop.ui.documentsList;

import android.Manifest;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.util.Arrays;

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
public class RenameDocumentFragment extends Fragment {
    private static String TAG = RenameDocumentFragment.class.toString();
    protected FragmentStackManager fragmentStackManager;

    public static final String ARG_FILE_ID = "fileId";
    public static final String ARG_NAME = "name";
    public static final String ARG_DESC = "description";

    private EditText document_name_input;
    private TextInputLayout document_name;

    private EditText document_description_input;
    private TextInputLayout document_description;

    private Button saveDocument;

    private RenameTask renameTask;

    private String fileId;
    private String actualName;
    private String actualDescription;

    GoogleAccountCredential mCredential;

    public static RenameDocumentFragment newInstance(String fileId, String actualName, String actualDescription) {
        RenameDocumentFragment renameDocument = new RenameDocumentFragment();
        Bundle bundle = new Bundle();
        bundle.putString(ARG_FILE_ID, fileId);
        bundle.putString(ARG_NAME, actualName);
        bundle.putString(ARG_DESC, actualDescription);
        renameDocument.setArguments(bundle);
        return renameDocument;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.rename_document, container, false);
        fragmentStackManager = FragmentStackManager.getInstance(getActivity());

        saveDocument = (Button) rootView.findViewById(R.id.btn_rename_document);

        document_name_input = (EditText) rootView.findViewById(R.id.rename_document_name_input);
        document_name = (TextInputLayout) rootView.findViewById(R.id.rename_document_name);

        document_description_input = (EditText) rootView.findViewById(R.id.rename_document_description_input);
        document_description = (TextInputLayout) rootView.findViewById(R.id.rename_document_description);

        saveDocument.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Resources res = getActivity().getResources();

                if (document_name_input.getText().toString().length() == 0) {
                    document_name.setError(res.getString(R.string.empty_name_error));
                }else{
                    document_name.setErrorEnabled(false);
                    renameFileThroughApi(document_name_input, document_description_input);
                }


            }
        });

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(getContext(), Arrays.asList(Constants.SCOPES))
                .setBackOff(new ExponentialBackOff());

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        fileId = getArguments().getString(ARG_FILE_ID);
        actualName = getArguments().getString(ARG_NAME);
        actualDescription = getArguments().getString(ARG_DESC);

        document_name_input.setText(actualName);
        document_description_input.setText(actualDescription);
    }


    private void renameFileThroughApi(EditText documentName, EditText documentDescription) {
        if (!GooglePlayUtils.isGooglePlayServicesAvailable(this.getActivity())) {
            GooglePlayUtils.acquireGooglePlayServices(this.getActivity());
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccountForEdition(documentName);
        } else if (!GooglePlayUtils.isDeviceOnline(this.getActivity())) {
            AndroidUtils.showMessage("No network connection available.", getActivity());
        } else {
            renameTask = new RenameTask(mCredential, documentName, documentDescription, this.fileId);
            renameTask.execute();
        }
    }

    @AfterPermissionGranted(Constants.REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccountForEdition(EditText documentName){
        if (EasyPermissions.hasPermissions(
                this.getActivity(), Manifest.permission.GET_ACCOUNTS)) {
            String accountName = this.getActivity().getPreferences(Context.MODE_PRIVATE)
                    .getString(Constants.PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                renameFileThroughApi(documentName, document_description_input);
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





    private class RenameTask extends AsyncTask<Void, Void, Void> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private String documentName = "";
        private String documentDescription;
        private String fileId = "";


        public RenameTask(GoogleAccountCredential credential, EditText documentName, EditText documentDescription, String fileId) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();

            this.documentName = documentName.getText().toString();
            this.documentDescription = documentDescription != null ? documentDescription.getText().toString() : "";
            this.fileId = fileId;
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
            fileMetadata.setName(this.documentName);
            fileMetadata.setDescription(this.documentDescription);

            mService.files().update(this.fileId,fileMetadata).execute();
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
                    GooglePlayUtils.showGooglePlayServicesAvailabilityErrorDialog(getActivity(),
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
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
