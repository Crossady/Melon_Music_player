package com.example.dfz.myapplication;

import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.dfz.myapplication.MUtils.AlbumLoader;
import com.example.dfz.myapplication.Model.Album;
import com.example.dfz.myapplication.Model.Song;
import com.example.dfz.myapplication.Service.MusicService;

import java.util.ArrayList;
import java.util.Collections;

import jp.wasabeef.glide.transformations.BlurTransformation;

import static android.widget.Toast.makeText;

public class AlbumActivity extends AppCompatActivity implements LowerBar.LowerBarFragmentTouchListener, LowerBar.LowerBarPlayButtonClickListener, LowerBar.LowerBarNextButtonClickListener{
    private RecyclerView mRecyclerView;
    private MySongAdapterWithoutImage mAdapter;
    private Album album;
    private ArrayList<Song> songs = new ArrayList<>();

    public static boolean isVisible = false;

    private long currentTimeMs = 0;
    private boolean isPlaying = false;

    private String TAG = "AlbumActivity";

    private MusicService myService;
    boolean mBound = false;

    public static final int MSG_NEXT_SONG = 1;
    private static Handler handler;
    public static Messenger messenger;

    private ImageView albumCover;
    private TextView albumName;
    private TextView artist;
    private TextView publicationYear;

    private class AlbumActivityHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (isDestroyed()) {
                return;
            }
            switch (msg.what) {
                case MSG_NEXT_SONG:
                    updateFragment();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album_layout);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Album");
        }

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "onCreate: bind service : mBound"+mBound);
        handler = new AlbumActivityHandler();
        messenger = new Messenger(handler);

        albumCover = findViewById(R.id.album_info_cover);
        albumName = findViewById(R.id.album_info_name);
        artist = findViewById(R.id.album_info_artist);
        publicationYear = findViewById(R.id.album_info_publication_year);

        Intent intent1 = getIntent();
        int albumId = intent1.getIntExtra("albumId", 0);
        album = AlbumLoader.getAlbum(this, albumId);
        songs = album.songs;
        Collections.sort(songs, (song1, song2) -> song1.getTrackNumber()-song2.getTrackNumber());
        Uri imageUri = album.safeGetFirstSong().getAlbumArt();
        Glide.with(this).load(imageUri).into(albumCover);
        albumName.setText(album.getTitle());
        artist.setText(album.getArtistName());
        publicationYear.setText(String.valueOf(album.getYear()));

        View view = findViewById(R.id.album_page);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;

        Glide.with(this).load(imageUri).apply(RequestOptions.bitmapTransform(new BlurTransformation(this, 25)))
                .into(new SimpleTarget<Drawable>(width, height) {
            @Override
            public void onResourceReady(Drawable resource, Transition<? super Drawable> transition) {
                view.setBackground(resource);
            }
        });

//        Glide.with(this).asBitmap().load(imageUri)
//                .into(new SimpleTarget<Bitmap>(180,180) {
//                    @Override
//                    public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
//
//                    }

//                    {@literal @Override}
//                    public void onResourceReady(Bitmap resource, GlideAnimation<Bitmap> glideAnimation) {
//                        Drawable drawable = new BitmapDrawable(resource);
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
//                            rlVenueBg.setBackground(drawable);
//                        }
//                    }
//                });

        mRecyclerView = findViewById(R.id.songs_of_the_album);
        mRecyclerView.setHasFixedSize(true);

        mAdapter = new MySongAdapterWithoutImage(this, songs);
        mAdapter.setOnItemClickListener(new MySongAdapterWithoutImage.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                isPlaying = true;
                Bundle bundle = new Bundle();
                Song s = songs.get(position);
                bundle.putString("title", s.getTitle());
                bundle.putString("artist", s.getArtist());
                bundle.putInt("albumId", s.getAlbumID());
                bundle.putLong("duration", s.getDuration());
                bundle.putBoolean("isPause", !isPlaying);

                LowerBar lowerBar = new LowerBar();
                lowerBar.setArguments(bundle);
                //getFragmentManager().popBackStack();
                if(getFragmentManager().getBackStackEntryCount()>0) {
                    getFragmentManager().popBackStack();
                    FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                    fragmentTransaction.replace(R.id.lowerbar_container, lowerBar).addToBackStack(null).commit();
                }
                else {
                    FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                    fragmentTransaction.add(R.id.lowerbar_container, lowerBar).addToBackStack(null).commit();
                }

                if (mBound) {
                    myService.playSong(s);
                }

            }

            @Override
            public void onItemLongClick(View itemView, int pos) {

            }
        });

        mRecyclerView.setAdapter(mAdapter);

        Log.d(TAG, "onCreate: look bind end: mBound"+mBound);
        if (!mBound){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isVisible = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isVisible = true;
        if (mBound) {
            Log.d(TAG, "onResume: yes bound");
            updateFragment();
        }else {
            Log.d(TAG, "onResume: oh, not bound?");

        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
        }
        mBound = false;
        handler.removeCallbacksAndMessages(null);
    }

    public ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            MusicService.MyBinder binder = (MusicService.MyBinder) service;
            myService = binder.getService();
            mBound = true;
            Log.d(TAG, "onServiceConnected: mBound: "+mBound);

            if (myService.nowPlaySong() != null) {
                Log.d("show lowerbar", "show");
                Song nowSong = myService.nowPlaySong();
                Bundle bundle = new Bundle();
                bundle.putString("title", nowSong.getTitle());
                bundle.putString("artist", nowSong.getArtist());
                bundle.putInt("albumId", nowSong.getAlbumID());
                bundle.putLong("duration", nowSong.getDuration());
                bundle.putBoolean("isPause", myService.isPause);

                LowerBar lowerBar = new LowerBar();
                lowerBar.setArguments(bundle);
                //getFragmentManager().popBackStack();
                FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                fragmentTransaction.add(R.id.lowerbar_container, lowerBar).addToBackStack(null).commit();
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "onServiceDisConnected: ");
            mBound = false;
        }
    };

    @Override
    public void goToPlayer(Bundle bundle) {
        if (myService!=null){
            Song s = myService.nowPlaySong();
            currentTimeMs = bundle.getLong("currentMs");
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("albumId", s.getAlbumID());
            intent.putExtra("title", s.getTitle());
            intent.putExtra("artist", s.getArtist());
            intent.putExtra("duration", s.getDuration());
            intent.putExtra("currentTimeMs", currentTimeMs);
            intent.putExtra("isPlaying", isPlaying);
            startActivity(intent);
        }

    }

    @Override
    public void playOrPause() {

        if (isPlaying) {
            myService.pause();
        } else {
            myService.start();
        }
        isPlaying = !isPlaying;
    }

    @Override
    public void nextSong() {
        myService.playNext();

        updateFragment();


        isPlaying = true;
    }

    private void updateFragment() {
        Song s = myService.nowPlaySong();
        makeText(getBaseContext(), "now Song is" + s, Toast.LENGTH_SHORT).show();
        isPlaying = myService.isPlaying();
        Bundle bundle = new Bundle();
        bundle.putString("title", s.getTitle());
        bundle.putString("artist", s.getArtist());
        bundle.putInt("albumId", s.getAlbumID());
        bundle.putLong("duration", s.getDuration());
        bundle.putBoolean("isPause", !myService.isPlaying());

        LowerBar lowerBar = new LowerBar();
        lowerBar.setArguments(bundle);
        getFragmentManager().popBackStack();
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.lowerbar_container, lowerBar).addToBackStack(null).commit();
    }


    @Override
    public void onBackPressed() {
        finish();
    }

}
