package gsoc.google.com.byop.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.utils.Constants;
import gsoc.google.com.byop.utils.FragmentStackManager;

/**
 * Created by lgwork on 20/06/16.
 */
public class SettingsFragment extends Fragment {

    protected FragmentStackManager fragmentStackManager;
    private EditText passwordInput1;
    private EditText passwordInput2;
    private EditText driveLgFolderInput;
    private EditText driveLgFolderNameInput;

    private TextInputLayout password1;
    private TextInputLayout password2;

    Button saveChanges;
    Button cancelChanges;


    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        fragmentStackManager = FragmentStackManager.getInstance(getActivity());

        View rootView = inflater.inflate(R.layout.settings_layout, container, false);

        SharedPreferences prefs = getActivity().getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE);
        String password = prefs.getString("password", "");
        String folderLink = prefs.getString("lgDriveFolder", "https://drive.google.com/open?id=0B8LNo6g9735tTklCbUtwSkRiYm8");
        String folderName = prefs.getString("lgDriveFolderName", "");


        passwordInput1 = (EditText) rootView.findViewById(R.id.lg_password_1_input);
        if (!password.equals("")) passwordInput1.setText(password);
        password1 = (TextInputLayout) rootView.findViewById(R.id.lg_password_1);

        passwordInput2 = (EditText) rootView.findViewById(R.id.lg_password_2_input);
        if (!password.equals("")) passwordInput2.setText(password);
        password2 = (TextInputLayout) rootView.findViewById(R.id.lg_password_2);

        driveLgFolderInput = (EditText) rootView.findViewById(R.id.lg_drive_folder_input);
        driveLgFolderInput.setText(folderLink);

        driveLgFolderNameInput = (EditText) rootView.findViewById(R.id.lg_shared_folder_name_input);
        driveLgFolderNameInput.setText(folderName);

        saveChanges = (Button) rootView.findViewById(R.id.btn_save_preferences);
        saveChanges.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Resources res = getActivity().getResources();
                if (!passwordInput1.getText().toString().equals(passwordInput2.getText().toString())) {
                    password1.setError(res.getString(R.string.passwords_no_match));
                    password2.setError(res.getString(R.string.passwords_no_match));
                } else {
                    password1.setErrorEnabled(false);
                    password2.setErrorEnabled(false);
                    SharedPreferences.Editor editor = getActivity().getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE).edit();
                    editor.putString("password", passwordInput1.getText() != null ? passwordInput1.getText().toString() : "");
                    editor.putString("lgDriveFolder", driveLgFolderInput.getText() != null ? driveLgFolderInput.getText().toString() : "");
                    editor.putString("lgDriveFolderName", driveLgFolderNameInput.getText() != null ? driveLgFolderNameInput.getText().toString() : "");
                    editor.commit();

                    fragmentStackManager.popBackStatFragment();
                }
            }
        });

        cancelChanges = (Button) rootView.findViewById(R.id.btn_cancel_preferences);
        cancelChanges.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fragmentStackManager.popBackStatFragment();
            }
        });


        return rootView;
    }
}
