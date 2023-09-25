package com.example.androidprogramiranje_samostoenproekt;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public class vtorEkranActivity extends AppCompatActivity {
    TextView timerText;
    Button stopStartButton;
    Timer timer;
    TimerTask timerTask;
    Double time = 0.0;
    boolean timerStarted = false;
    private MediaRecorder recorder;
    private String fileName;
    FirebaseStorage storage = FirebaseStorage.getInstance();
    StorageReference storageRef = storage.getReference();

    FirebaseFirestore db = FirebaseFirestore.getInstance();
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    // dozvola za koristenje na mikrofon
    private String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.MANAGE_EXTERNAL_STORAGE};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vtor_ekran);

        timerText = (TextView) findViewById(R.id.timerText);
        stopStartButton = (Button) findViewById(R.id.startStopButton);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION); // permisija za da pravi record video
        timer = new Timer();
    }

    public void resetTapped(View view) {
        AlertDialog.Builder resetAlert = new AlertDialog.Builder(this);
        resetAlert.setTitle("Reset Timer");
        resetAlert.setMessage("Are you sure you want to reset the timer?");
        resetAlert.setPositiveButton("Reset", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (timerTask != null) {
                    timerTask.cancel();
                    setButtonUI("START", R.color.green);
                    time = 0.0;
                    timerStarted = false;
                    timerText.setText(formatTime(0, 0, 0));
                }
            }
        });

        resetAlert.setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // do nothing
            }
        });

        resetAlert.show();
    }

    public void startStopTapped(View view) {
        if (timerStarted == false) {
            timerStarted = true;
            setButtonUI("STOP", R.color.red);
            startRecording();
        } else {
            timerStarted = false;
            setButtonUI("START", R.color.white);
            timerTask.cancel();
            stopRecording();
        }
    }

    public void startRecording() {
        // Initialize the MediaRecorder
        recorder = new MediaRecorder(); // standard kod
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); // za da go napravi vo mpeg4
        // za sekoe video da ima razlichno ime vo Storage vo Firebase se pravi so red 123
        fileName = getExternalCacheDir().getAbsolutePath() + "/recording_" + System.currentTimeMillis() + ".m4a";
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setOutputFile(fileName);

        try {
            recorder.prepare();
            recorder.start();
            timerTask = new TimerTask() {
                @Override
                public void run() {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            time++;
                            timerText.setText(getTimerText());
                        }
                    });
                }
            };
            timer.scheduleAtFixedRate(timerTask, 0, 1000);
        } catch (IOException e) {
            Toast.makeText(vtorEkranActivity.this, "Problem with timer counting:" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void stopRecording() { // default-en kod
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;

            // Upload the recording to Firebase Storage
            uploadRecordingToFirebase(); // nasha funkcija upload
        }
    }

    private void uploadRecordingToFirebase() {
        Uri file = Uri.fromFile(new File(fileName));
        StorageReference recordingRef = storageRef.child("recordings/" + file.getLastPathSegment()); // mu se stava ime
        UploadTask uploadTask = recordingRef.putFile(file); // Firebase taka funkcionira so recordingRef, ne znam zoshto e taka

        uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) { // se dobiva link od kade e prikacheno videoto
                recordingRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) { // koga kje go dobieme toj link, prikachuvame metadata
                        String downloadUrl = uri.toString();

                        // Save metadata to Firestore
                        saveMetadataToFirestore(downloadUrl); // metadata se podatoci za kade se naogja prikachenata snimka
                    }
                });
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // Handle failure
            }
        });
    }

    private void saveMetadataToFirestore(String downloadUrl) {
        if (user != null) {
            // Create an AlertDialog.Builder to build the dialog
            // se pojavuva pop-up prozorec za da ja imenuvame snimkata
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Name the recording");

            // Set up the input
            final EditText input = new EditText(this);
            // Specify the type of input expected
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            String name = "Recording " + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            input.setText(name);
            builder.setView(input);

            // Set up the buttons
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String name = input.getText().toString();

                    /* Vo Firestore Database so url, userId i name, vo recordings se kreira videoto so dadenite podatoci i potoa vo
                    saved recordings odnosno vo listata gi lista site podatoci kade shto userId e toj najaveniot
                     */
                    Map<String, Object> recording = new HashMap<>();
                    recording.put("url", downloadUrl);
                    recording.put("userId", user.getUid());
                    recording.put("name", name);

                    // Save the recording with the entered name
                    db.collection("recordings").add(recording)
                            .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                                @Override
                                public void onSuccess(DocumentReference documentReference) {
                                    Toast.makeText(vtorEkranActivity.this, "Recording saved successfully!", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Toast.makeText(vtorEkranActivity.this, "Recording NOT saved!", Toast.LENGTH_SHORT).show();
                                }
                            });
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            // Show the AlertDialog
            builder.show();
        }
    }

    private void setButtonUI(String START, int color) {
        stopStartButton.setText(START);
        stopStartButton.setTextColor(ContextCompat.getColor(this, color));
    }

    private String getTimerText() {
        int rounded = (int) Math.round(time);
        int seconds = ((rounded % 86400) % 3600) % 60;
        int minutes = ((rounded % 86400) % 3600) / 60;
        int hours = ((rounded % 86400) / 3600);

        return formatTime(seconds, minutes, hours);
    }

    private String formatTime(int seconds, int minutes, int hours) {
        return String.format("%02d", hours) + " : " + String.format("%02d", minutes) + " : " + String.format("%02d", seconds);
    }

    public void savedRecordings2(View view) {
        Intent intent = new Intent(this, ListActivity.class);
        startActivity(intent);
    }

    public void recordButton(View view) {
        Intent intent = new Intent(this, vtorEkranActivity.class);
        startActivity(intent);
    }

    public void soundRecorderButtonTapped(View view) {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        if (!permissionToRecordAccepted) finish();
    }
}