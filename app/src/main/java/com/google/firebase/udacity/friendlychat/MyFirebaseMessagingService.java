package com.google.firebase.udacity.friendlychat;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private String TAG = "MyFirebaseMsgService" ;
    public MyFirebaseMessagingService() {
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {

        // Check if messages contain data payload
        if(remoteMessage.getData().size()>0){
            Log.d(TAG, "Data Payload" + remoteMessage.getData()) ;
        }

        // Check if messages contain notifications

        if(remoteMessage.getNotification() != null){

            Log.d(TAG, "Notifications Body" + remoteMessage.getNotification().getBody()) ;
        }
    }
}
