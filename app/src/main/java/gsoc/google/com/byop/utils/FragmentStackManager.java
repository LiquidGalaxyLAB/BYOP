package gsoc.google.com.byop.utils;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;


public class FragmentStackManager {

    private static FragmentStackManager mInstance = null;
    private static Context mContext = null;
    private static String TAG = "FragmentStackManager.class";
    private FragmentManager fragmentManager;

    public FragmentStackManager(){
        try{
            final AppCompatActivity activity = (AppCompatActivity) mContext;
            fragmentManager = activity.getSupportFragmentManager();
        } catch (ClassCastException e) {
            Log.d(TAG, "Can't get the fragment manager with this");
        }
    }

    public static FragmentStackManager getInstance(Context context){
        if(mInstance==null || !mContext.equals(context)){
            mContext = context;
            mInstance =  new FragmentStackManager();
        }
        return mInstance;
    }

    /**
     * Load new fragment to a resource given as parameter
     */
    public void loadFragment(Fragment toLoad, int backFrame) {
        String fragmentName = toLoad.getClass().getName();
        FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.replace(backFrame, toLoad, fragmentName);
        ft.addToBackStack(fragmentName);
        ft.commit();
    }

    /**
     * Pop 1 fragment from de backstack and returns false if you can't pop more items.
     */
    public boolean popBackStatFragment(){
        if (fragmentManager.getBackStackEntryCount() > 1) {
            fragmentManager.popBackStack();
            fragmentManager.beginTransaction().commit();
            return true;
        }
        return false;
    }


    /**
     * Pop until last fragment.
     */
    public void resetBackStack(Fragment fragment){
        fragmentManager.popBackStack(fragment.getClass().getName(), 0);
    }
}
