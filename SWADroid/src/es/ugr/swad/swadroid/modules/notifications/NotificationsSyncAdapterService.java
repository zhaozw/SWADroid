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

package es.ugr.swad.swadroid.modules.notifications;

import android.accounts.Account;
import android.app.Service;
import android.content.*;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.bugsense.trace.BugSenseHandler;

import es.ugr.swad.swadroid.Constants;
import es.ugr.swad.swadroid.Preferences;
import es.ugr.swad.swadroid.R;
import es.ugr.swad.swadroid.gui.AlertNotification;
import es.ugr.swad.swadroid.model.DataBaseHelper;
import es.ugr.swad.swadroid.model.SWADNotification;
import es.ugr.swad.swadroid.model.User;
import es.ugr.swad.swadroid.ssl.SecureConnection;

import org.ksoap2.SoapEnvelope;
import org.ksoap2.SoapFault;
import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.ksoap2.transport.KeepAliveHttpsTransportSE;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.TimeoutException;

/**
 * Service for notifications sync adapter.
 *
 * @author Juan Miguel Boyero Corral <juanmi1982@gmail.com>
 */
public class NotificationsSyncAdapterService extends Service {
    private static final String TAG = "NotificationsSyncAdapterService";
    private static SyncAdapterImpl sSyncAdapter = null;
    private static int notifCount;
    private static final int NOTIF_ALERT_ID = 1982;
    private static int SIZE_LIMIT;
    private static Preferences prefs;
    private static DataBaseHelper dbHelper;
    private static String METHOD_NAME = "";
    private static final String NAMESPACE = "urn:swad";
    private final static String SOAP_ACTION = "";
    private static String SERVER; // = "swad.ugr.es";
    private static SoapObject request;
    private static Object result;
    private static String errorMessage = "";
    public static final String START_SYNC = "es.ugr.swad.swadroid.sync.start";
    public static final String STOP_SYNC = "es.ugr.swad.swadroid.sync.stop";
    private static KeepAliveHttpsTransportSE connection;

    public NotificationsSyncAdapterService() {
        super();
    }

    private static class SyncAdapterImpl extends AbstractThreadedSyncAdapter {
        private final Context mContext;

        public SyncAdapterImpl(Context context) {
            super(context, true);
            mContext = context;
            prefs = new Preferences();

            try {
                dbHelper = new DataBaseHelper(mContext);
            } catch (Exception e) {
                e.printStackTrace();
                errorMessage = e.getMessage();

                //Send exception details to Bugsense
                BugSenseHandler.sendException(e);
            }
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            try {
                prefs.getPreferences(mContext);
                SIZE_LIMIT = prefs.getNotifLimit();
                SERVER = prefs.getServer();
                NotificationsSyncAdapterService.performSync(mContext, account, extras, authority, provider, syncResult);
                
                //If synchronization was successful, update last synchronization time in preferences
                prefs.setLastSyncTime(System.currentTimeMillis());
            } catch (Exception e) {                
                if (e instanceof SoapFault) {
                    SoapFault es = (SoapFault) e;

                    if (es.faultstring.equals("Bad log in")) {
                    	errorMessage = mContext.getString(R.string.errorBadLoginMsg);
                    } else if (es.faultstring.equals("Unknown application key")) {
                    	errorMessage = mContext.getString(R.string.errorBadAppKeyMsg);
                    } else {
                    	errorMessage = "Server error: " + es.getMessage();
                    }
                } else if (e instanceof XmlPullParserException) {
                	errorMessage = mContext.getString(R.string.errorServerResponseMsg);

                    e.printStackTrace();

                    //Send exception details to Bugsense
                    BugSenseHandler.sendException(e);
                } else if (e instanceof TimeoutException) {
                	errorMessage = mContext.getString(R.string.errorTimeoutMsg);
                //} else if (e instanceof IOException) {
                //    errorMsg = getString(R.string.errorConnectionMsg);
                } else {
                	errorMessage = e.getMessage();

                    e.printStackTrace();

                    //Send exception details to Bugsense
                    BugSenseHandler.sendException(e);
                }
                
                //Notify synchronization stop
                Intent stopIntent = new Intent();
                stopIntent.setAction(STOP_SYNC);
                stopIntent.putExtra("notifCount", notifCount);  
                stopIntent.putExtra("errorMessage", errorMessage);        
                mContext.sendBroadcast(stopIntent);
            }
        }
    }

    /* (non-Javadoc)
	 * @see android.app.Service#onCreate()
	 */
	@Override
	public void onCreate() {        
        //Initialize Bugsense plugin
        BugSenseHandler.initAndStartSession(this, Constants.BUGSENSE_API_KEY);
        
		super.onCreate();
	}

	/* (non-Javadoc)
	 * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		 // return START_NOT_STICKY - we want this Service to be left running 
        //  unless explicitly stopped, and it's process is killed, we want it to
        //  be restarted
        return START_STICKY;
	}

	/* (non-Javadoc)
	 * @see android.app.Service#onDestroy()
	 */
	@Override
	public void onDestroy() {
		BugSenseHandler.closeSession(this);
		
		super.onDestroy();
	}

	@Override
    public IBinder onBind(Intent intent) {        
        return getSyncAdapter().getSyncAdapterBinder();
    }

	/* (non-Javadoc)
	 * @see android.app.Service#onUnbind(android.content.Intent)
	 */
	@Override
	public boolean onUnbind(Intent intent) {
		return super.onUnbind(intent);
	}

	private SyncAdapterImpl getSyncAdapter() {
        if (sSyncAdapter == null)
            sSyncAdapter = new SyncAdapterImpl(this);
        return sSyncAdapter;
    }

    private static void createRequest() {
        request = new SoapObject(NAMESPACE, METHOD_NAME);
        result = null;
    }

    private static void addParam(String param, Object value) {
        request.addProperty(param, value);
    }

    private static void sendRequest(Class<?> cl, boolean simple)
            throws IOException, XmlPullParserException {

    	// Variables for URL splitting
        String delimiter = "/";
        String PATH;
        String[] URLArray;
        String URL;

        // Split URL
        URLArray = SERVER.split(delimiter, 2);
        URL = URLArray[0];
        if (URLArray.length == 2) {
            PATH = delimiter + URLArray[1];
        } else {
            PATH = "";
        }

        /**
         * Use of KeepAliveHttpsTransport deals with the problems with the
         * Android ssl libraries having trouble with certificates and
         * certificate authorities somehow messing up connecting/needing
         * reconnects.
         */
        connection = new KeepAliveHttpsTransportSE(URL, 443, PATH, Constants.CONNECTION_TIMEOUT);
        SoapSerializationEnvelope envelope = new SoapSerializationEnvelope(
                SoapEnvelope.VER11);
        System.setProperty("http.keepAlive", "false");
        envelope.encodingStyle = SoapEnvelope.ENC;
        envelope.setAddAdornments(false);
        envelope.implicitTypes = true;
        envelope.dotNet = false;
        envelope.setOutputSoapObject(request);
        envelope.addMapping(NAMESPACE, cl.getSimpleName(), cl);
    	connection.call(SOAP_ACTION, envelope);  
        
        /*connection.debug = true;
        try {
        	connection.call(SOAP_ACTION, envelope);
	        Log.d(TAG, connection.getHost() + " " + connection.getPath() + " " +
	        connection.getPort());
	        Log.d(TAG, connection.requestDump.toString());
	        Log.d(TAG, connection.responseDump.toString());
        } catch (Exception e) {
	        Log.e(TAG, connection.getHost() + " " + connection.getPath() + " " +
	        connection.getPort());
	        Log.e(TAG, connection.requestDump.toString());
	        Log.e(TAG, connection.responseDump.toString());
        }*/

        if (simple && !(envelope.getResponse() instanceof SoapFault)) {
            result = envelope.bodyIn;
        } else {
            result = envelope.getResponse();
        }
    }
    
    private static void logUser() throws IOException, XmlPullParserException {
    	Log.d(TAG, "Not logged");

        METHOD_NAME = "loginByUserPasswordKey";
       /* MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(prefs.getUserPassword().getBytes());
        String userPassword = Base64.encodeBytes(md.digest());
        userPassword = userPassword.replace('+', '-').replace('/', '_').replace('=', ' ').replaceAll("\\s+", "").trim();*/

        createRequest();
        addParam("userID", prefs.getUserID());
        addParam("userPassword", prefs.getUserPassword());
        addParam("appKey", Constants.SWAD_APP_KEY);
        sendRequest(User.class, true);

        if (result != null) {
            KvmSerializable ks = (KvmSerializable) result;
            SoapObject soap = (SoapObject) result;

            //Stores user data returned by webservice response
            User loggedUser = new User(
                    Long.parseLong(ks.getProperty(0).toString()),                    // id
                    soap.getProperty("wsKey").toString(),                            // wsKey
                    soap.getProperty("userID").toString(),                            // userID
                    //soap.getProperty("userNickname").toString(),					// userNickname
                    null,                                                            // userNickname
                    soap.getProperty("userSurname1").toString(),                    // userSurname1
                    soap.getProperty("userSurname2").toString(),                    // userSurname2
                    soap.getProperty("userFirstname").toString(),                    // userFirstname
                    soap.getProperty("userPhoto").toString(),                        // userPhoto
                    Integer.parseInt(soap.getProperty("userRole").toString())        // userRole
            );

            Constants.setLoggedUser(loggedUser);
            Constants.setLogged(true);

            //Update application last login time
            Constants.setLastLoginTime(System.currentTimeMillis());
        }
    }
    
    private static void getNotifications() throws IOException, XmlPullParserException {
    	Log.d(TAG, "Logged");

        //Calculates next timestamp to be requested
        Long timestamp = Long.valueOf(dbHelper.getFieldOfLastNotification("eventTime"));
        timestamp++;

        //Creates webservice request, adds required params and sends request to webservice
        METHOD_NAME = "getNotifications";
        createRequest();
        addParam("wsKey", Constants.getLoggedUser().getWsKey());
        addParam("beginTime", timestamp);
        sendRequest(SWADNotification.class, false);

        if (result != null) {
            dbHelper.beginTransaction();

            //Stores notifications data returned by webservice response
            ArrayList<?> res = new ArrayList<Object>((Vector<?>) result);
            SoapObject soap = (SoapObject) res.get(1);
            notifCount = soap.getPropertyCount();
            for (int i = 0; i < notifCount; i++) {
                SoapObject pii = (SoapObject) soap.getProperty(i);
                Long notificationCode = Long.valueOf(pii.getProperty("notificationCode").toString());
                String eventType = pii.getProperty("eventType").toString();
                Long eventTime = Long.valueOf(pii.getProperty("eventTime").toString());
                String userSurname1 = pii.getProperty("userSurname1").toString();
                String userSurname2 = pii.getProperty("userSurname2").toString();
                String userFirstName = pii.getProperty("userFirstname").toString();
                String userPhoto = pii.getProperty("userPhoto").toString();
                String location = pii.getProperty("location").toString();
                String summary = pii.getProperty("summary").toString();
                Integer status = Integer.valueOf(pii.getProperty("status").toString());
                String content = pii.getProperty("content").toString();
                SWADNotification n = new SWADNotification(notificationCode, eventType, eventTime, userSurname1, userSurname2, userFirstName, userPhoto, location, summary, status, content);
                dbHelper.insertNotification(n);

                //Log.d(TAG, n.toString());
            }

            //Request finalized without errors
            Log.i(TAG, "Retrieved " + notifCount + " notifications");

            //Clear old notifications to control database size
            dbHelper.clearOldNotifications(SIZE_LIMIT);

            dbHelper.endTransaction();
        }
    }

    private static void performSync(Context context, Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult)
            throws IOException, XmlPullParserException, NoSuchAlgorithmException, KeyManagementException {

        //Notify synchronization start
        Intent startIntent = new Intent();
        startIntent.setAction(START_SYNC);
        context.sendBroadcast(startIntent);

        //Initialize HTTPS connections
        SecureConnection.initSecureConnection();

        //If last login time > Global.RELOGIN_TIME, force login
        if (System.currentTimeMillis() - Constants.getLastLoginTime() > Constants.RELOGIN_TIME) {
            Constants.setLogged(false);
        }

        if (!Constants.isLogged()) {
        	logUser();
        }

        if (Constants.isLogged()) {
        	getNotifications();
        	
        	if (notifCount > 0) {
	            //If the notifications counter exceeds the limit, set it to the max allowed
	            if (notifCount > SIZE_LIMIT) {
	                notifCount = SIZE_LIMIT;
	            }
	            
	        	AlertNotification.alertNotif(context,
	            		NOTIF_ALERT_ID,
	            		context.getString(R.string.app_name),
	            		notifCount + " " + context.getString(R.string.notificationsAlertMsg),
	            		context.getString(R.string.app_name));
        	}
        }

        //Notify synchronization stop
        Intent stopIntent = new Intent();
        stopIntent.setAction(STOP_SYNC);
        stopIntent.putExtra("notifCount", notifCount);  
        stopIntent.putExtra("errorMessage", errorMessage);        
        context.sendBroadcast(stopIntent);
    }
}
