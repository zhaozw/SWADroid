/*
 *  This file is part of SWADroid.
 *
 *  Copyright (C) 2010 Juan Miguel Boyero Corral <juanmi1982@gmail.com>
 *
 *  SWADroid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  SWADroid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with SWADroid.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.ugr.swad.swadroid;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.bugsense.trace.BugSenseHandler;

import es.ugr.swad.swadroid.gui.DialogFactory;
import es.ugr.swad.swadroid.gui.ImageExpandableListAdapter;
import es.ugr.swad.swadroid.gui.MenuExpandableListActivity;
import es.ugr.swad.swadroid.model.Course;
import es.ugr.swad.swadroid.model.DataBaseHelper;
import es.ugr.swad.swadroid.model.Model;
import es.ugr.swad.swadroid.modules.Courses;
import es.ugr.swad.swadroid.modules.GenerateQR;
import es.ugr.swad.swadroid.modules.Messages;
import es.ugr.swad.swadroid.modules.Notices;
import es.ugr.swad.swadroid.modules.RecoverPassword;
import es.ugr.swad.swadroid.modules.downloads.DownloadsManager;
import es.ugr.swad.swadroid.modules.groups.MyGroupsManager;
import es.ugr.swad.swadroid.modules.information.Information;
import es.ugr.swad.swadroid.modules.notifications.Notifications;
import es.ugr.swad.swadroid.modules.rollcall.Rollcall;
import es.ugr.swad.swadroid.modules.tests.Tests;
import es.ugr.swad.swadroid.ssl.SecureConnection;
import es.ugr.swad.swadroid.sync.AccountAuthenticator;
import es.ugr.swad.swadroid.sync.SyncUtils;
import es.ugr.swad.swadroid.utils.Crypto;
import es.ugr.swad.swadroid.utils.Utils;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main class of the application.
 *
 * @author Juan Miguel Boyero Corral <juanmi1982@gmail.com>
 * @author Antonio Aguilera Malagon <aguilerin@gmail.com>
 * @author Helena Rodriguez Gijon <hrgijon@gmail.com>
 * @author Jose Antonio Guerrero Aviles <cany20@gmail.com>
 */
public class SWADMain extends MenuExpandableListActivity implements OnClickListener {
	/**
	 * Application preferences
	 */
	Preferences prefs;
	/**
	 * SSL connection
	 */
	SecureConnection conn;
    /**
     * Array of strings for main ListView
     */
    protected String[] functions;
    /**
     * Function name field
     */
    private final String NAME = "listText";
    /**
     * Function text field
     */
    private final String IMAGE = "listIcon";
    /**
     * Code of selected course
     */
    private long courseCode;
    /**
     * Cursor for database access
     */
    private Cursor dbCursor;
    /**
     * User courses list
     */
    private List<Model> listCourses;
    /**
     * Tests tag name for Logcat
     */
    public static final String TAG = Constants.APP_TAG;
    
    public static final String USER_TO_RECOVER =  "es.ugr.swad.swadroid.USER_TO_RECOVER";
    /**
     * Indicates if it is the first run
     */
    private boolean firstRun = false;

    /**
     * Current role 2 - student 3 - teacher -1 - none role was chosen
     */
    private int currentRole = -1;

    private boolean dBCleaned = false;

    private boolean mLoginError = false;
    // UI references for the login form.
    private EditText mDniView;
    private EditText mPasswordView;
    private EditText mServerView;
    private View mLoginFormView;
    private View mLoginStatusView;
    private View mMainScreenView;
    private TextView mLoginStatusMessageView;
    private ImageButton mUpdateButton;
    private ProgressBar mProgressBar;
    private TextView mWhyPasswordText;
    private TextView mLostPasswordText;
    private ExpandableListView mExpandableListview;
    private OnChildClickListener mExpandableClickListener;
    
    /**
     * Gets the database helper
     *
     * @return the database helper
     */
    public static DataBaseHelper getDbHelper() {
        return dbHelper;
    }

    /**
     * Shows initial dialog after application upgrade.
     */
    public void showUpgradeDialog(Context context) {        
        AlertDialog alertDialog = DialogFactory.createWebViewDialog(context,
        		R.string.changelogTitle,
        		R.raw.changes);

        alertDialog.show();
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onCreate()
     */
    @Override
    public void onCreate(Bundle icicle) {
        int lastVersion, currentVersion;
        ImageView image;
        TextView text;
        Intent activity;
        
        //Initialize Bugsense plugin
        try {
        	BugSenseHandler.initAndStartSession(this, Constants.BUGSENSE_API_KEY);
        } catch (Exception e) {
        	Log.e(TAG, "Error initializing BugSense", e);
        }

        //Initialize screen
        super.onCreate(icicle);
        setContentView(R.layout.main);

        //Initialize preferences
        prefs = new Preferences(this);
        initializeViews();
        
        image = (ImageView) this.findViewById(R.id.moduleIcon);
        image.setBackgroundResource(R.drawable.ic_launcher_swadroid);

        text = (TextView) this.findViewById(R.id.moduleName);
        text.setText(R.string.app_name);
        
        try {
        	
            //Initialize HTTPS connections
        	/*
        	 * Terena root certificate is not included by default on Gingerbread and older
        	 * If Android API < 11 (HONEYCOMB) add Terena certificate manually
        	 */
        	if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
        		conn = new SecureConnection();
        		conn.initSecureConnection(this);        		
        		Log.i(TAG, "Android API < 11 (HONEYCOMB). Adding Terena certificate manually");
        	} else {
        		Log.i(TAG, "Android API >= 11 (HONEYCOMB). Using Terena built-in certificate");
        	}

            //Check if this is the first run after an install or upgrade
            lastVersion = Preferences.getLastVersion();
            currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            dbHelper.initializeDB();
            //lastVersion = 57;
            //currentVersion = 58;

            //If this is the first run, show configuration dialog
            if (lastVersion == 0) {
                loginForm(true);
                //Configure automatic synchronization
                Preferences.setSyncTime(String.valueOf(Constants.DEFAULT_SYNC_TIME));
                activity = new Intent(this, AccountAuthenticator.class);
                startActivity(activity);

                Preferences.setLastVersion(currentVersion);
                firstRun = true;
                Constants.setSelectedCourseCode(-1);

                Constants.setSelectedCourseShortName("");
                Constants.setSelectedCourseFullName("");

                Constants.setCurrentUserRole(-1);

            //If this is an upgrade, show upgrade dialog
            } else if (lastVersion < currentVersion) {
                showUpgradeDialog(this);
                dbHelper.upgradeDB(this);
                
                if(lastVersion < 52) {
                	//Encrypts users table
                	dbHelper.encryptUsers();
                	
                	//If the app is updating from an unencrypted user password version, encrypt user password
                	Preferences.upgradeCredentials();
                	
                	Preferences.setSyncTime(String.valueOf(Constants.DEFAULT_SYNC_TIME));
                } else if(lastVersion < 57) {
                	//Reconfigure automatic synchronization
                	SyncUtils.removePeriodicSync(Constants.AUTHORITY, Bundle.EMPTY, this);                	
                	if(!Preferences.getSyncTime().equals("0") && Preferences.isSyncEnabled()) {
                		SyncUtils.addPeriodicSync(Constants.AUTHORITY, Bundle.EMPTY,
                				Long.valueOf(Preferences.getSyncTime()), this);
                	}
                }

                Preferences.setLastVersion(currentVersion);
            }

            listCourses = dbHelper.getAllRows(Constants.DB_TABLE_COURSES, "", "shortName");
            if (listCourses.size() > 0) {
                Course c = (Course) listCourses.get(Preferences.getLastCourseSelected());
                Constants.setSelectedCourseCode(c.getId());
                Constants.setSelectedCourseShortName(c.getShortName());
                Constants.setSelectedCourseFullName(c.getFullName());
                Constants.setCurrentUserRole(c.getUserRole()); 
            } else {
                Constants.setSelectedCourseCode(-1);
                Constants.setSelectedCourseShortName("");
                Constants.setSelectedCourseFullName("");
                Constants.setCurrentUserRole(-1);
            }
            currentRole = -1;
        } catch (Exception ex) {
            error(TAG, ex.getMessage(), ex, true);
        }
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
        
        boolean showLoginForm = isUserOrPasswordEmpty() || (listCourses.size() == 0) || mLoginError;
        
        loginForm(showLoginForm);
    	if (!Preferences.isPreferencesChanged() && !Utils.isDbCleaned()) {
            createSpinnerAdapter();
            if (!firstRun) {
                courseCode = Constants.getSelectedCourseCode();
                createMenu();
        	}
        } else {
            Preferences.setPreferencesChanged(false);
            Utils.setDbCleaned(false);
            setMenuDbClean();
        }
    }

    /* (non-Javadoc)
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		try {
			BugSenseHandler.closeSession(this);
		} catch (Exception e) {
			Log.e(TAG, "Error initializing BugSense", e);
        }
		
		super.onDestroy();
	}

	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                //After get the list of courses, a dialog is launched to choice the course
                case Constants.COURSES_REQUEST_CODE:
                    mProgressBar.setVisibility(View.GONE);
                    mUpdateButton.setVisibility(View.VISIBLE);

                    setMenuDbClean();
                    createSpinnerAdapter();
                    createMenu();
                    showProgress(false);
                    mLoginError = false;
                    loginForm(false);
                    
                    //User credentials are correct. Set periodic synchronization if enabled
        	        if (!Preferences.getSyncTime().equals("0")
        	        		&& Preferences.isSyncEnabled() && SyncUtils.isPeriodicSynced(this)) {
        	            SyncUtils.addPeriodicSync(Constants.AUTHORITY, Bundle.EMPTY,
        	            		Long.parseLong(Preferences.getSyncTime()), this);
        	        }
                    break;
                case Constants.RECOVER_PASSWORD_REQUEST_CODE:
                    Toast.makeText(getApplicationContext(), R.string.lost_password_success,
                                   Toast.LENGTH_LONG).show();
                    break;
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            switch (requestCode) {
                //After get the list of courses, a dialog is launched to choice the course
                case Constants.COURSES_REQUEST_CODE:
                    mLoginError = true;
                    showProgress(false);
                    loginForm(true);
                    
                    //User credentials are wrong. Remove periodic synchronization
                    SyncUtils.removePeriodicSync(Constants.AUTHORITY, Bundle.EMPTY, this);                    
                    
                    mUpdateButton.setVisibility(View.GONE);
                    mProgressBar.setVisibility(View.VISIBLE);
                    
                    break;
            }
        }
    }

    private void createSpinnerAdapter() {
        Spinner spinner = (Spinner) this.findViewById(R.id.spinner);
        listCourses = dbHelper.getAllRows(Constants.DB_TABLE_COURSES, null, "fullName");
        dbCursor = dbHelper.getDb().getCursor(Constants.DB_TABLE_COURSES, null, "fullName");
        startManagingCursor(dbCursor);
        if (listCourses.size() != 0) {
            SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                    android.R.layout.simple_spinner_item,
                    dbCursor,
                    new String[]{"fullName"},
                    new int[]{android.R.id.text1});

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setOnItemSelectedListener(new onItemSelectedListener());
            if (Preferences.getLastCourseSelected() < listCourses.size())
                spinner.setSelection(Preferences.getLastCourseSelected());
            else
                spinner.setSelection(0);
            	spinner.setOnTouchListener(Spinner_OnTouch);
        } else {
            cleanSpinner();
        }

    }

    /**
     * Create an empty spinner. It is called when the database is cleaned.
     */
    private void cleanSpinner() {
        Spinner spinner = (Spinner) this.findViewById(R.id.spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{getString(R.string.clickToGetCourses)});
        spinner.setAdapter(adapter);
        spinner.setOnTouchListener(Spinner_OnTouch);
    }

    private class onItemSelectedListener implements OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position,
                                   long id) {
            if (!listCourses.isEmpty()) {
            	Preferences.setLastCourseSelected(position);
                Course courseSelected = (Course) listCourses.get(position);
                courseCode = courseSelected.getId();
                Constants.setSelectedCourseCode(courseCode);
                Constants.setSelectedCourseShortName(courseSelected.getShortName());
                Constants.setSelectedCourseFullName(courseSelected.getFullName());
                Constants.setCurrentUserRole(courseSelected.getUserRole());
                createMenu();
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {

        }
    }

    private final View.OnTouchListener Spinner_OnTouch = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {

                if (dbHelper.getAllRows(Constants.DB_TABLE_COURSES).size() == 0) {
                    if (Utils.connectionAvailable(getApplicationContext()))
                        getCurrentCourses();
                    //else

                } else {
                    v.performClick();
                }
            }
            return true;
        }
    };

    private void getCurrentCourses() {
        Intent activity;
        activity = new Intent(this, Courses.class);
        Toast.makeText(this, R.string.coursesProgressDescription, Toast.LENGTH_LONG).show();
        startActivityForResult(activity, Constants.COURSES_REQUEST_CODE);
    }

    private void createMenu() {
        if (listCourses.size() != 0) {
            Course courseSelected;
            if (Constants.getSelectedCourseCode() != -1) {
                courseSelected = (Course) dbHelper.getRow(Constants.DB_TABLE_COURSES, "id", String.valueOf(Constants.getSelectedCourseCode()));
            } else {
                int lastSelected = Preferences.getLastCourseSelected();
                if (lastSelected != -1 && lastSelected < listCourses.size()) {
                    courseSelected = (Course) listCourses.get(lastSelected);
                    Constants.setSelectedCourseCode(courseSelected.getId());
                    Constants.setSelectedCourseShortName(courseSelected.getShortName());
                    Constants.setSelectedCourseFullName(courseSelected.getFullName());
                    Constants.setCurrentUserRole(courseSelected.getUserRole());
                    Preferences.setLastCourseSelected(lastSelected);
                } else {
                    courseSelected = (Course) listCourses.get(0);
                    Constants.setSelectedCourseCode(courseSelected.getId());
                    Constants.setSelectedCourseShortName(courseSelected.getShortName());
                    Constants.setSelectedCourseFullName(courseSelected.getFullName());
                    Constants.setCurrentUserRole(courseSelected.getUserRole());
                    Preferences.setLastCourseSelected(0);
                }
            }

            if (courseSelected != null) {
                if ((mExpandableListview.getAdapter() == null) || dBCleaned) {
                    createBaseMenu();
                }
                int userRole = courseSelected.getUserRole();
                if ((userRole == Constants.TEACHER_TYPE_CODE) && (currentRole != Constants.TEACHER_TYPE_CODE))
                    changeToTeacherMenu();
                if ((userRole == Constants.STUDENT_TYPE_CODE) && (currentRole != Constants.STUDENT_TYPE_CODE))
                    changeToStudentMenu();
                dBCleaned = false;
            }
        }
    }

    /**
     * Creates base menu. The menu base is common for students and teachers.
     * Sets currentRole to student role
     */
    private void createBaseMenu() {
        mExpandableListview.setVisibility(View.VISIBLE);
        if (mExpandableListview.getAdapter() == null || currentRole == -1) {
            //the menu base is equal to students menu.
            currentRole = Constants.STUDENT_TYPE_CODE;
            //Construct Expandable List
            final ArrayList<HashMap<String, Object>> headerData = new ArrayList<HashMap<String, Object>>();

            //Order:
            // 1- Course
            // 2- Evaluation
            // 3- Messages
            // 4- Enrollment
            // 5- Users
            final HashMap<String, Object> courses = new HashMap<String, Object>();
            courses.put(NAME, getString(R.string.course));
            courses.put(IMAGE, getResources().getDrawable(R.drawable.blackboard));
            headerData.add(courses);

            final HashMap<String, Object> evaluation = new HashMap<String, Object>();
            evaluation.put(NAME, getString(R.string.evaluation));
            evaluation.put(IMAGE, getResources().getDrawable(R.drawable.grades));
            headerData.add(evaluation);

            final HashMap<String, Object> users = new HashMap<String, Object>();
            users.put(NAME, getString(R.string.users));
            users.put(IMAGE, getResources().getDrawable(R.drawable.users));
            headerData.add(users);

            final HashMap<String, Object> messages = new HashMap<String, Object>();
            messages.put(NAME, getString(R.string.messages));
            messages.put(IMAGE, getResources().getDrawable(R.drawable.msg));
            headerData.add(messages);

            /*final HashMap<String, Object> enrolment = new HashMap<String, Object>();
            enrolment.put(NAME, getString(R.string.enrollment));
            enrolment.put(IMAGE, getResources().getDrawable(R.drawable.enrollment));
            headerData.add(enrolment);*/

            final ArrayList<ArrayList<HashMap<String, Object>>> childData = new ArrayList<ArrayList<HashMap<String, Object>>>();

            final ArrayList<HashMap<String, Object>> courseData = new ArrayList<HashMap<String, Object>>();
            childData.add(courseData);

            final ArrayList<HashMap<String, Object>> evaluationData = new ArrayList<HashMap<String, Object>>();
            childData.add(evaluationData);

            ArrayList<HashMap<String, Object>> usersData = new ArrayList<HashMap<String, Object>>();
            childData.add(usersData);

            final ArrayList<HashMap<String, Object>> messagesData = new ArrayList<HashMap<String, Object>>();
            childData.add(messagesData);

            /*final ArrayList<HashMap<String, Object>> enrollmentData = new ArrayList<HashMap<String, Object>>();
            childData.add(enrollmentData);*/

            HashMap<String, Object> map = new HashMap<String, Object>();
  
            //Documents category
            map.put(NAME, getString(R.string.documentsDownloadModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.folder));
            courseData.add(map);
            //Shared area category
            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.sharedsDownloadModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.folder_users));
            courseData.add(map);
            //TODO Disable Information options until the Information module is finished
            //Introduction category
            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.introductionModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.notif));
            courseData.add(map);
            //Theory Program category
            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.syllabusLecturesModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.notif));
            courseData.add(map);
            //Practices Program category
            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.syllabusPracticalsModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.notif));
            courseData.add(map);
            //Teaching Guide category
            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.teachingguideModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.notif));
            courseData.add(map);
            //Bibliography category
            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.bibliographyModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.notif));
            courseData.add(map);
            //FAQs category
            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.faqsModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.notif));
            courseData.add(map);
            //Links category
            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.linksModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.notif));
            courseData.add(map);
            
            //Evaluation category
            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.testsModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.test));
            evaluationData.add(map);
            //Assessment category
            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.assessmentModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.notif));
            evaluationData.add(map);

            //Users category
            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.myGroupsModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.my_groups));
            usersData.add(map);

            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.generateQRModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.scan_qr));
            usersData.add(map);

            //Messages category
            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.notificationsModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.bell));
            messagesData.add(map);

            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.messagesModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.msg_write));
            messagesData.add(map);

            mExpandableListview.setAdapter(new ImageExpandableListAdapter(
                    this,
                    headerData,
                    R.layout.image_list_item,
                    new String[]{NAME},            // the name of the field data
                    new int[]{R.id.listText}, // the text field to populate with the field data
                    childData,
                    0,
                    null,
                    new int[]{}
            ));
        }
    }

    /**
     * Adapts the current menu to students view. Removes options unique to teachers and adds options unique to students
     */
    private void changeToStudentMenu() {
        if (currentRole == Constants.TEACHER_TYPE_CODE) {
            //Removes Publish Note from messages menu
            ((ImageExpandableListAdapter) mExpandableListview.getAdapter()).removeChild(Constants.MESSAGES_GROUP, Constants.PUBLISH_NOTE_CHILD);
            //Removes Rollcall from users menu
            ((ImageExpandableListAdapter) mExpandableListview.getAdapter()).removeChild(Constants.USERS_GROUP, Constants.ROLLCALL_CHILD);
        }
        currentRole = Constants.STUDENT_TYPE_CODE;
    }

    /**
     * Adapts the current menu to teachers view. Removes options unique to students and adds options unique to teachers
     */
    private void changeToTeacherMenu() {
        if (currentRole == Constants.STUDENT_TYPE_CODE) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.noticesModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.note));
            ((ImageExpandableListAdapter) mExpandableListview.getAdapter()).addChild(Constants.MESSAGES_GROUP, Constants.PUBLISH_NOTE_CHILD, map);

            map = new HashMap<String, Object>();
            map.put(NAME, getString(R.string.rollcallModuleLabel));
            map.put(IMAGE, getResources().getDrawable(R.drawable.roll_call));
            ((ImageExpandableListAdapter) mExpandableListview.getAdapter()).addChild(Constants.USERS_GROUP, Constants.ROLLCALL_CHILD, map);
        }
        currentRole = Constants.TEACHER_TYPE_CODE;
    }

    /**
     * Creates an empty Menu and spinner when the data base is empty
     */
    protected void setMenuDbClean() {
        Utils.setDbCleaned(false);
        Constants.setSelectedCourseCode(-1);
        Constants.setSelectedCourseShortName("");
        Constants.setSelectedCourseFullName("");
        Constants.setCurrentUserRole(-1);
        Preferences.setLastCourseSelected(-1);
        dBCleaned = true;
        listCourses.clear();
        cleanSpinner();
        
        mExpandableListview.setVisibility(View.GONE);
    }

    /**
     * Launches an action when refresh button is pushed
     *
     * @param v Actual view
     */
    public void onRefreshClick(View v) {
        mUpdateButton.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);

        getCurrentCourses();
    }
    
    /**
     * @return true if user or password preference is empty
     */
	private boolean isUserOrPasswordEmpty() {
		return TextUtils.isEmpty(Preferences.getUserID())
				|| TextUtils.isEmpty(Preferences.getUserPassword());
	}
    
	private void initializeViews(){
		mUpdateButton = (ImageButton) findViewById(R.id.refresh);
	    mMainScreenView = findViewById(R.id.main_screen);
        mLoginFormView = findViewById(R.id.login_form);
        mLoginStatusView = findViewById(R.id.login_status);        
        mProgressBar = (ProgressBar) this.findViewById(R.id.progress_refresh);
        mWhyPasswordText = (TextView) findViewById(R.id.why_password);
        mLostPasswordText = (TextView) findViewById(R.id.lost_password);
        mExpandableListview = (ExpandableListView) findViewById(R.id.expandableList);
        
        SpannableString lostPasswordUnderline = new SpannableString(getString(R.string.lost_password));
        lostPasswordUnderline.setSpan(new UnderlineSpan(), 0, lostPasswordUnderline.length(), 0);
        mLostPasswordText.setText(lostPasswordUnderline);
        
        SpannableString whyPasswordUnderline = new SpannableString(getString(R.string.why_password));
        whyPasswordUnderline.setSpan(new UnderlineSpan(), 0, whyPasswordUnderline.length(), 0);
        mWhyPasswordText.setText(whyPasswordUnderline);
        
        
        mWhyPasswordText.setVisibility(View.GONE);
        mUpdateButton.setVisibility(View.VISIBLE);
        mProgressBar.setVisibility(View.GONE);
        
        mWhyPasswordText.setOnClickListener(this);
        mLostPasswordText.setOnClickListener(this);
        
        mExpandableClickListener = new OnChildClickListener() {
            
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                    int childPosition, long id) {
                // Get the item that was clicked
                Object o = parent.getExpandableListAdapter().getChild(groupPosition, childPosition);
                @SuppressWarnings("unchecked")
                String keyword = (String) ((Map<String, Object>) o).get(NAME);
                // boolean rollCallAndroidVersionOK =
                // (android.os.Build.VERSION.SDK_INT >=
                // android.os.Build.VERSION_CODES.FROYO);
                // PackageManager pm = getPackageManager();
                // boolean rearCam;

                // It would be safer to use the constant
                // PackageManager.FEATURE_CAMERA_FRONT
                // but since it is not defined for Android 2.2, I substituted
                // the literal value
                // frontCam =
                // pm.hasSystemFeature("android.hardware.camera.front");
                // rearCam = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);

                Intent activity;
                Context ctx = getApplicationContext();
                if (keyword.equals(getString(R.string.notificationsModuleLabel))) {
                    activity = new Intent(ctx, Notifications.class);
                    startActivityForResult(activity, Constants.NOTIFICATIONS_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.testsModuleLabel))) {
                    activity = new Intent(ctx, Tests.class);
                    startActivityForResult(activity, Constants.TESTS_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.messagesModuleLabel))) {
                    activity = new Intent(ctx, Messages.class);
                    activity.putExtra("eventCode", Long.valueOf(0));
                    startActivityForResult(activity, Constants.MESSAGES_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.noticesModuleLabel))) {
                    activity = new Intent(ctx, Notices.class);
                    startActivityForResult(activity, Constants.NOTICES_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.rollcallModuleLabel))) {
                    activity = new Intent(ctx, Rollcall.class);
                    startActivityForResult(activity, Constants.ROLLCALL_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.generateQRModuleLabel))) {
                    activity = new Intent(ctx, GenerateQR.class);
                    startActivityForResult(activity, Constants.GENERATE_QR_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.documentsDownloadModuleLabel))) {
                    activity = new Intent(ctx, DownloadsManager.class);
                    activity.putExtra("downloadsAreaCode", Constants.DOCUMENTS_AREA_CODE);
                    startActivityForResult(activity, Constants.DOWNLOADSMANAGER_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.sharedsDownloadModuleLabel))) {
                    activity = new Intent(ctx, DownloadsManager.class);
                    activity.putExtra("downloadsAreaCode", Constants.SHARE_AREA_CODE);
                    startActivityForResult(activity, Constants.DOWNLOADSMANAGER_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.myGroupsModuleLabel))) {
                    activity = new Intent(ctx, MyGroupsManager.class);
                    activity.putExtra("courseCode", Constants.getSelectedCourseCode());
                    startActivityForResult(activity, Constants.MYGROUPSMANAGER_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.introductionModuleLabel))) {
                    activity = new Intent(ctx, Information.class);
                    activity.putExtra("requestCode", Constants.INTRODUCTION_REQUEST_CODE);
                    startActivityForResult(activity, Constants.INTRODUCTION_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.faqsModuleLabel))) {
                    activity = new Intent(ctx, Information.class);
                    activity.putExtra("requestCode", Constants.FAQS_REQUEST_CODE);
                    startActivityForResult(activity, Constants.FAQS_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.bibliographyModuleLabel))) {
                    activity = new Intent(ctx, Information.class);
                    activity.putExtra("requestCode", Constants.BIBLIOGRAPHY_REQUEST_CODE);
                    startActivityForResult(activity, Constants.BIBLIOGRAPHY_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.syllabusPracticalsModuleLabel))) {
                    activity = new Intent(ctx, Information.class);
                    activity.putExtra("requestCode", Constants.SYLLABUSPRACTICALS_REQUEST_CODE);
                    startActivityForResult(activity, Constants.SYLLABUSPRACTICALS_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.syllabusLecturesModuleLabel))) {
                    activity = new Intent(ctx, Information.class);
                    activity.putExtra("requestCode", Constants.SYLLABUSLECTURES_REQUEST_CODE);
                    startActivityForResult(activity, Constants.SYLLABUSLECTURES_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.linksModuleLabel))) {
                    activity = new Intent(ctx, Information.class);
                    activity.putExtra("requestCode", Constants.LINKS_REQUEST_CODE);
                    startActivityForResult(activity, Constants.LINKS_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.teachingguideModuleLabel))) {
                    activity = new Intent(ctx, Information.class);
                    activity.putExtra("requestCode", Constants.TEACHINGGUIDE_REQUEST_CODE);
                    startActivityForResult(activity, Constants.TEACHINGGUIDE_REQUEST_CODE);
                } else if (keyword.equals(getString(R.string.assessmentModuleLabel))) {
                    activity = new Intent(ctx, Information.class);
                    activity.putExtra("requestCode", Constants.ASSESSMENT_REQUEST_CODE);
                    startActivityForResult(activity, Constants.ASSESSMENT_REQUEST_CODE);

                }

                return true;
            }
        };
        
        mExpandableListview.setOnChildClickListener(mExpandableClickListener);
	}
	
	/**
	 * Shows a login form
	 */
	private void loginForm(boolean show) {
		mUpdateButton.setVisibility(show ? View.GONE : View.VISIBLE);
        mProgressBar.setVisibility(View.GONE);
	    mMainScreenView.setVisibility(show ? View.GONE : View.VISIBLE);
		mLoginFormView.setVisibility(show ? View.VISIBLE : View.GONE);

		if (Preferences.getServer().equals(Constants.DEFAULT_SERVER)) {
		    mWhyPasswordText.setVisibility(show ? View.VISIBLE : View.GONE);
		}
		
		setupLoginForm();
	}
    
    private void setupLoginForm() {
        mDniView = (EditText) findViewById(R.id.DNI);
        mDniView.setText(Preferences.getUserID());
        
        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setText("");
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });
        
        mServerView = (EditText) findViewById(R.id.server);
        mServerView.setText(Preferences.getServer());
        if (mServerView.getText().toString().equals(Constants.DEFAULT_SERVER)) 
            mPasswordView.setError(getString(R.string.error_password_summaryUGR));
        
        mLoginStatusMessageView = (TextView) findViewById(R.id.login_status_message);

        findViewById(R.id.sign_in_button).setOnClickListener(this);
    }
    
    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid DNI, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    public void attemptLogin() {

        // Values for DNI and password at the time of the login attempt.
        String DniValue;
        String passwordValue;
        String serverValue;
        String toastMsg;
        
        // Reset errors.
        mDniView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        DniValue = mDniView.getText().toString();
        passwordValue = mPasswordView.getText().toString();
        serverValue = mServerView.getText().toString();
        toastMsg = mServerView.getText().toString().equals("swad.ugr.es") ? getString(R.string.error_password_summaryUGR) : getString(R.string.error_invalid_password);
        
        boolean cancel = false;
        View focusView = null;

        // Check for a valid password.
        if (TextUtils.isEmpty(passwordValue)) {
			mPasswordView.setError(getString(R.string.error_field_required));
			focusView = mPasswordView;
			cancel = true;
        } else if ((passwordValue.length() < 8)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
            Toast.makeText(getApplicationContext(), toastMsg,
                    Toast.LENGTH_LONG).show();
        } else if(Utils.isLong(passwordValue)) {
			mPasswordView.setError(getString(R.string.error_incorrect_password));
			focusView = mPasswordView;
			cancel = true;
			Toast.makeText(getApplicationContext(), toastMsg,
                    Toast.LENGTH_LONG).show();
        }

        // Check for a valid DNI.
        if (TextUtils.isEmpty(DniValue)) {
            mDniView.setError(getString(R.string.error_field_required));
            focusView = mDniView;
            cancel = true;
        }
        
        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            mLoginStatusMessageView.setText(R.string.login_progress_signing_in);
            try {
                Preferences.setUserID(DniValue);
                Preferences.setUserPassword(Crypto.encryptPassword(passwordValue));
                Preferences.setServer(serverValue);
            } catch (NoSuchAlgorithmException e) {
                error(TAG, e.getMessage(), e, true);
            }
            showProgress(true);
            getCurrentCourses();
        }
    }
    
    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginStatusView.setVisibility(View.VISIBLE);
            mLoginStatusView.animate()
                    .setDuration(shortAnimTime)
                    .alpha(show ? 1 : 0)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mLoginStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
                        }
                    });

            mLoginFormView.setVisibility(View.VISIBLE);
            mLoginFormView.animate()
                    .setDuration(shortAnimTime)
                    .alpha(show ? 0 : 1)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mLoginFormView.setVisibility(mLoginError ? View.VISIBLE : View.GONE);
                        }
                    });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mLoginStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private void whyMyPasswordNotWorkDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LayoutInflater inflater = getLayoutInflater();

        builder.setView(inflater.inflate(R.layout.dialog_why_password, null))
               .setTitle(R.string.why_password_dialog_title)
               .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int which) {
                       dialog.dismiss();
                   }
               });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void recoverPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final EditText dni = new EditText(getApplicationContext());
        dni.setHint("DNI");
        
        builder.setView(dni)
               .setTitle(R.string.lost_password_dialog_title)
               .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int which) {
                       dialog.dismiss();
                       Intent i = new Intent(getBaseContext(), RecoverPassword.class);
                       i.putExtra(USER_TO_RECOVER, dni.getText().toString());
                       startActivityForResult(i, Constants.RECOVER_PASSWORD_REQUEST_CODE);
                   }
               });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.why_password:
                whyMyPasswordNotWorkDialog();
                break;
            case R.id.sign_in_button:
                attemptLogin();
                break;
            case R.id.lost_password:
                recoverPasswordDialog();
                break;
            default:
                break;
        }
    }

}
