package com.example.nneeranjun.bluetoothapp;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Picture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PreviewTask extends AppCompatActivity{
    private static final String TAG = "BluetoothApplication";
    CameraPreview mPreview;


    Button takePicture;


    BluetoothAdapter mBluetoothAdapter;
    Set<BluetoothDevice> pairedDevices;
    ArrayAdapter<String> mArrayAdapter;
    List<BluetoothDevice> list;
    SharedPreferences sharedPreferences;
    AcceptThread acceptThread;
    ConnectThread connectThread;
    ConnectedThread connectedThread;
    BluetoothDevice selectedDevice;
    Camera.PictureCallback pictureCallback;
    FrameLayout preview;
    Camera camera;

    private ProgressDialog mProgressDlg;
    private Context mContext;

    String user;
    byte[] acceptTempBytes;
    int purpose;
    public static final int TAKE_PICTURE = 18289;
    public static final int SEND_FRAME = 34902;
    public static final int DISCOVERABLE=16898;
    public static final int SEND_PICTURE = 12312;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_camera_preview);
        takePicture = (Button) findViewById(R.id.takePictureUser);
        mArrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        user = sharedPreferences.getString("user", "");
        if (mBluetoothAdapter == null) {
            AlertDialog.Builder bluetoothNotSupported = new AlertDialog.Builder(getApplicationContext());
            bluetoothNotSupported.setTitle("Error");
            bluetoothNotSupported.setMessage("Bluetooth not supported");
            bluetoothNotSupported.setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    System.exit(0);
                }
            });
            bluetoothNotSupported.create().show();
        } else if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
        }

        if (user.equals("host")) {
            takePicture  = (Button)findViewById(R.id.button);
            takePicture.setVisibility(View.GONE);
            Intent discoverableIntent = new
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivityForResult(discoverableIntent, DISCOVERABLE);

            //start camera
            if (this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
                // this device has a camera
                try {
                     camera = Camera.open();
                    pictureCallback = new Camera.PictureCallback() {
                        @Override
                        public void onPictureTaken(byte[] data, Camera camera) {
                            SavePhotoTask save = new SavePhotoTask();
                            save.execute(data);
                            byte[] picture = Arrays.copyOf(data,data.length+1);
                            picture[data.length]  = (byte) SEND_PICTURE;
                            connectedThread.write(picture);
                        }
                    };
                    // Create our Preview view and set it as the content of our activity.
                    mPreview = new CameraPreview(this, camera);
                     preview = (FrameLayout) findViewById(R.id.camera_preview);
                    preview.addView(mPreview);

                    camera.setPreviewCallback(new Camera.PreviewCallback() {
                        @Override
                        public void onPreviewFrame(byte[] data, Camera camera) {
                            if(connectedThread!=null){
                                byte[] frame = Arrays.copyOf(data,data.length+1);
                                frame[data.length] = (byte) SEND_FRAME;
                                connectedThread.write(frame);
                            }
                        }
                    });
                }
                catch(Exception e) {
                    Log.d(TAG, "Error in opening camera: "  + e.getMessage());
                }
            } else {
                // no camera on this device
                Log.d(TAG, "This device does not have a camera");
            }
            } else {
            pairedDevices = mBluetoothAdapter.getBondedDevices();
            list = new ArrayList<BluetoothDevice>(pairedDevices);
            if (pairedDevices.size() != 0) {
                for (BluetoothDevice device : pairedDevices) {
                    mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            }

            mProgressDlg 		= new ProgressDialog(this);

            mProgressDlg.setMessage("Scanning...");
            mProgressDlg.setCancelable(false);
            mProgressDlg.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    mBluetoothAdapter.cancelDiscovery();
                }
            });


// Register the BroadcastReceiver
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
            mContext = this;
            mBluetoothAdapter.startDiscovery();
        }

    }

    // Create a BroadcastReceiver for Bluetooth actions like ACTION_FOUND
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        int count=0;
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                if (state == BluetoothAdapter.STATE_ON) {
                    Log.d(TAG, "Bluetooth enabled");
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                mProgressDlg.show();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                mProgressDlg.dismiss();
                if(count==0) {

                    final AlertDialog.Builder devices = new AlertDialog.Builder(PreviewTask.this);

                    devices.setTitle("Select Device");


                    devices.setAdapter(mArrayAdapter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            selectedDevice = list.get(which);

                            dialog.cancel();
                            connectThread = new ConnectThread(selectedDevice);
                            connectThread.start();


                        }
                    });
                    devices.create().show();
                    Toast.makeText(PreviewTask.this,Integer.toString(pairedDevices.size()),Toast.LENGTH_LONG).show();
                    count++;
                }
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                Toast.makeText(PreviewTask.this,"FOUND",Toast.LENGTH_LONG).show();
                list.add(device);
                mArrayAdapter.add(device.getName().toString() + "\n" + device.getAddress().toString());

            }

        }


    };

    public void takePhoto(){
        camera.takePicture(null,null,pictureCallback);
    }


    private final Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case TAKE_PICTURE:
                    takePhoto();
                    break;
                case SEND_FRAME:
                    byte[] frame = (byte[]) msg.obj;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(frame,0,frame.length);
                   Canvas canvas = new Canvas(bitmap);
                    preview.draw(canvas);
                    break;
                case SEND_PICTURE:
                   SavePhotoTask save = new SavePhotoTask();
                    save.execute((byte[])msg.obj);
            }



        }
    };

    public class AcceptThread extends Thread {
        private BluetoothServerSocket mmServerSocket=null;
        public  final UUID MY_UUID= UUID.fromString("471d2108-69bb-48f7-964e-5d303ac2aa3c");
        public static final String NAME = "BluetoothApp";

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final

            BluetoothServerSocket tmp = null;
            try {

                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {e.printStackTrace(); }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection was accepted
                if (socket != null) {

                    connectedThread = new ConnectedThread(socket);
                    // Do work to manage the connection (in a separate thread)
                    connectedThread.start();
                    try{
                        mmServerSocket.close();
                    }
                    catch (IOException e){
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) { }
        }
    }
    public class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        public final UUID MY_UUID = UUID.fromString("471d2108-69bb-48f7-964e-5d303ac2aa3c");

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;


            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {

            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    Log.d("Kobe","ERROR");
                    mmSocket.close();
                } catch (IOException closeException) {
                }
                return;
            }


        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }
    public class ConnectedThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            Toast.makeText(PreviewTask.this,"Connected",Toast.LENGTH_LONG).show();

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    if (new BigInteger(buffer).intValue()==TAKE_PICTURE) {

                            mHandler.obtainMessage(TAKE_PICTURE, bytes, -1, buffer).sendToTarget();

                    }
                    else{
                        switch (buffer[buffer.length-1]){
                            case (byte)SEND_FRAME:
                                byte[] frame = new byte[buffer.length-2];
                                for(int i=0;i<frame.length;i++){
                                    frame[i] = buffer[i];
                                }
                                mHandler.obtainMessage(SEND_FRAME,bytes,-1,frame).sendToTarget();
                                break;
                            case (byte)SEND_PICTURE:
                                byte[] picture = new byte[buffer.length-2];
                                for(int i=0;i<picture.length;i++){
                                    picture[i] = buffer[i];
                                }
                                mHandler.obtainMessage(TAKE_PICTURE,bytes,-1,picture).sendToTarget();
                                break;
                        }
                    }


                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }

        }
}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode==DISCOVERABLE){
            switch (resultCode){
                case RESULT_OK:
                    acceptThread = new AcceptThread();
                    acceptThread.start();
                    break;
                case RESULT_CANCELED:
                    System.exit(1);
            }

        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }
}
