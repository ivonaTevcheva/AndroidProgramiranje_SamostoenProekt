package com.example.androidprogramiranje_samostoenproekt;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseUser user;
    private ListView songListView;
    private ArrayAdapter<String> arrayAdapter;
    private List<String> songNameList;
    private Map<String, String> nameUrlMap;
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        user = auth.getCurrentUser();
        songListView = findViewById(R.id.song_list);

        songNameList = new ArrayList<>();
        nameUrlMap = new HashMap<>();
        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, songNameList);
        songListView.setAdapter(arrayAdapter);

        songListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String name = songNameList.get(position);
                String url = nameUrlMap.get(name);
                if (url != null) {
                    playAudio(url);
                }
            }
        });

        if (user != null) {
            fetchRecordingsForUser(user.getUid());
        }
    }

    private void fetchRecordingsForUser(String userId) { // od databazata gi zema site snimki od recordings spored userId odnosno user-ot shto e najaven
        db.collection("recordings")
                .whereEqualTo("userId", userId)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String name = document.getString("name");
                                String url = document.getString("url");
                                if (name != null && url != null) {
                                    songNameList.add(name); // zemenite podatoci gi stava vo lista songListView po ime kreirana na red 33
                                    nameUrlMap.put(name, url); /* vo nameUrlMap gi stava name i url kade url kje ni treba za funkcijata
                                    playAudio, kade na sekoja linija i stavame clickListener */
                                }
                            }
                            arrayAdapter.notifyDataSetChanged();
                        } else {
                            Toast.makeText(ListActivity.this, "Error fetching recordings", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void playAudio(String url) { // ako klikneme od edno audio (edna linija) na drugo audio (druga linija) vo listata
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mediaPlayer = new MediaPlayer();
        try { // treba da go zapre prvoto audio, odnosno da go ischisti mediaPlayer-ot i da go pushti vtoroto audio
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() { // koga kje se vratime nazad na nekoj drug ekran, pushtenoto audio da go iskluchi (da ne pee)
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
