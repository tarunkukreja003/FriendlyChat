/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.BuildConfig;
import com.firebase.ui.auth.ui.ResultCodes;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final int RC_SIGN_IN = 1;
    public static final int  RC_PHOTO_PICKER = 2 ;
    public static final String MESSAGE_LENGTH_KEY = "message_length" ;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private ImageButton mSendButton;

    private FirebaseDatabase mFirebaseData ;//entry point for our app to access the database
    private DatabaseReference mMessagesDatabaseRef ;//references a specific part of the database, here it is referencing Messages only
    private ChildEventListener mChildEventListener ;
    private FirebaseAuth mFirebaseAuth ;
    private FirebaseAuth.AuthStateListener mAuthStateListener ;
    private FirebaseStorage mFirebaseStorage ;
    private StorageReference mStorageReference ;
    private FirebaseRemoteConfig mFirebaseRemoteConfig ;
    private String mUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        mUsername = ANONYMOUS;

        mFirebaseData = FirebaseDatabase.getInstance() ;//access the database
        mFirebaseAuth = FirebaseAuth.getInstance() ;
        mFirebaseStorage = FirebaseStorage.getInstance() ;
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance() ;

        mMessagesDatabaseRef = mFirebaseData.getReference().child("messages") ;//using the instance of database we get the reference to the child messages
        mStorageReference = mFirebaseStorage.getReference().child("chat-photos") ;


        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (ImageButton) findViewById(R.id.sendButton);


        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker

                Intent intent = new Intent() ;
                intent.setType("image/*") ;
                intent.setAction(Intent.ACTION_GET_CONTENT) ;
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true) ;
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER );
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click

                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null) ;
                mMessagesDatabaseRef.push().setValue(friendlyMessage) ;

                // Clear input box
                mMessageEditText.setText("");
            }
        });


        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) { //this parameter of FirebaseAuth is different
                //from that created above, this one is guaranteed to contain whether the user is authenticated or not

                FirebaseUser user = firebaseAuth.getCurrentUser() ;

                if(user != null){
                    //user is signed in



                    onSignedInInitialize(user.getDisplayName()) ;
                }
                else{
                    //user is signed out
                    onSignedOutCleanUp() ;
                    //if the user is signed out we use sign -in flow
                    //https://github.com/firebase/FirebaseUI-Android
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)//smart lock saves user's credentials so that they could automatically sign-in the next time
                                    .setProviders(Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                                            new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()))
                                    .setTheme(R.style.AppTheme)
                                    .build(),
                            RC_SIGN_IN);
                }


            }
        };

        FirebaseRemoteConfigSettings remoteConfigSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build() ;
         mFirebaseRemoteConfig.setConfigSettings(remoteConfigSettings);

        Map<String,Object> defaultConfigMap = new HashMap<>() ;
        defaultConfigMap.put(MESSAGE_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT) ;
        mFirebaseRemoteConfig.setDefaults(defaultConfigMap);
        fetchConfig();

        }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == RC_SIGN_IN) {

            if (resultCode == RESULT_OK) {
                Toast.makeText(MainActivity.this, "You are now signed in , Welcome to Friendly Chat", Toast.LENGTH_SHORT).show();
            }

            else if(resultCode == RESULT_CANCELED){
                Toast.makeText(MainActivity.this, "Sign In Cancelled", Toast.LENGTH_SHORT).show();
                finish();
            }

            else if(resultCode == ResultCodes.RESULT_NO_NETWORK){
                Toast.makeText(MainActivity.this, "Check Your Internet Connetion", Toast.LENGTH_SHORT).show();
            }
        }

        else if(requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK){

            Uri selectedImageUri = data.getData() ;
            // Reference to store file
            StorageReference photoRef = mStorageReference.child(selectedImageUri.getLastPathSegment()) ;// lastpathsegment is that if we get the image from the device we will only store
            // its last segment

            // upload file on firebase storage

            photoRef.putFile(selectedImageUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) { //the parameter here is the key to get the
                    // URL of the file that was just sent to storage
                     Uri dowloadUrl = taskSnapshot.getDownloadUrl() ;

                    FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, dowloadUrl.toString()) ;
                    mMessagesDatabaseRef.push().setValue(friendlyMessage) ; // URL pushed into database
                }
            }) ;
        }



    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.sign_out_menu :
                // sign out
                AuthUI.getInstance().signOut(this) ;
                return true ;

            case R.id.del_account :
                // delete account

                AuthUI.getInstance().delete(this) ;

            default :
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        //we need to add the following 2 lines to detach the listener when the user is logged out
        detachReadDatabaseListener();
        mMessageAdapter.clear();
    }

    @Override
    protected void onResume(){
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    private void onSignedInInitialize(String username) {
        mUsername = username;
        //we are adding this here because the user should only be able to read messages when he/she is logged in
        attachDatabaseReadListener();
    }
    private void onSignedOutCleanUp(){
      //when the user is signed out we want the opposite of sign-in
        mUsername = ANONYMOUS ;
        mMessageAdapter.clear();

        detachReadDatabaseListener();

    }

    private void attachDatabaseReadListener(){

        if(mChildEventListener == null) { //if the child event listener is null only then initialise it

            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) { //datasnapshot contains the exact data from the firebase at the specified location
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);//since datasnapshot returns data which matches the friendlyMessage class hence it returns an object
                    mMessageAdapter.add(friendlyMessage);
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {

                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.remove(friendlyMessage);

                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            };
            mMessagesDatabaseRef.addChildEventListener(mChildEventListener);

        }
    }

    private void detachReadDatabaseListener(){
        if(mChildEventListener != null){
            mMessagesDatabaseRef.removeEventListener(mChildEventListener);
            mChildEventListener = null ;
        }
    }

    private void fetchConfig(){

        long cacheExpiration = 3600 ;

       if( mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()){
            cacheExpiration = 0 ;
        }

        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener(new OnSuccessListener<Void>() { // if the data is fetched from the server
                    @Override
                    public void onSuccess(Void aVoid) {

                        mFirebaseRemoteConfig.activateFetched() ; // this activates the parameters
                        applyRetrievedLengthLimit();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {

                        Log.w(TAG, "Error Remote Config") ;
                        applyRetrievedLengthLimit();
                    }
                }) ;


    }

    private void applyRetrievedLengthLimit() {
        long message_length = mFirebaseRemoteConfig.getLong(MESSAGE_LENGTH_KEY);

        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter((int) message_length)});

        Log.d(TAG, MESSAGE_LENGTH_KEY + " = " + message_length) ;
    }


}
