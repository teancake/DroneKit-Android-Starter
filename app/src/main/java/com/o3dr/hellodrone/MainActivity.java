package com.o3dr.hellodrone;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.ProgressBar;
import android.app.ProgressDialog;

import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;

import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeEventExtra;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionResult;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.drone.property.Parameters;
import com.o3dr.services.android.lib.drone.property.Parameter;



import java.util.List;
import java.util.ArrayList;


/* NOTE: this program is written with  compile 'com.o3dr:3dr-services-lib:2.2.16', which is
 * a pretty old version of 3DR Services.
 * Refreshing parameters works pretty well on this version of lib, but is very unstable on
 * the most up-to-date version.
 * Some of the functions in this app maynot be compatable with
 * the most up-to-date library. This version of the service lib corresponds to
 * 3DR_Services V1.2.8 and Tower V3.1.3 apps.
 */

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private Drone drone;
    private Parameters params;
    private int droneType = Type.TYPE_COPTER;
    private ControlTower controlTower;

    private final Handler handler = new Handler();
    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;

    private Spinner modeSelector;

    private double pitchParamVal = 0;
    private double rollParamVal = 0;
    private double thrustParamVal = 0;

    private double pitchParamInterval = 0.1;
    private double rollParamInterval = 0.1;
    private double thrustParamInterval = 0.1;

    private ProgressDialog progressDialog;

    private ProgressBar mLoadingProgress;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Context context = getApplicationContext();

        controlTower = new ControlTower(context);
        drone = new Drone();
        params = new Parameters();

        mLoadingProgress = (ProgressBar) this.findViewById(R.id.reload_progress);

        mLoadingProgress.setVisibility(View.GONE);



        modeSelector = (Spinner) findViewById(R.id.modeSelect);
        modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
        Spinner connectionSelector = (Spinner) findViewById(R.id.selectConnectionType);
        connectionSelector.setSelection(1);
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        controlTower.connect(this);
        updateVehicleModesForType(droneType);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (drone.isConnected()) {
            drone.disconnect();
            updateConnectedButton(false);
        };
        controlTower.unregisterDrone(drone);
        controlTower.disconnect();
    }

    // 3DR Services Listener
    // ==========================================================

    @Override
    public void onTowerConnected() {
        alertUser("3DR Services Connected");
        drone.unregisterDroneListener(this);
        controlTower.registerDrone(drone, handler);
        drone.registerDroneListener(this);
    }


    @Override
    public void onTowerDisconnected() {
        alertUser("3DR Service Interrupted");
    }

    // Drone Listener
    // ==========================================================

    @Override
    public void onDroneEvent(String event, Bundle extras) {

        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateConnectedButton(drone.isConnected());
                updateArmButton();
                // get parameters
                // A dialogue box will be displayed showing the progression
                drone.refreshParameters();

                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                updateConnectedButton(drone.isConnected());
                updateArmButton();
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != droneType) {
                    droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(droneType);
                }
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.SPEED_UPDATED:
                break;
/*
            case AttributeEvent.ALTITUDE_UPDATED:
                break;
*/
            case AttributeEvent.HOME_UPDATED:
                break;

            case AttributeEvent.PARAMETERS_RECEIVED:
                /* The following code does not work.
                final int defaultValue = -1;
                int index = extras.getInt(AttributeEventExtra.EXTRA_PARAMETER_INDEX, defaultValue);
                int count = extras.getInt(AttributeEventExtra.EXTRA_PARAMETERS_COUNT, defaultValue);
                     if (index != defaultValue && count != defaultValue)
                         updateProgress(index, count);
                */
                break;

            case AttributeEvent.PARAMETERS_REFRESH_STARTED:
                if(drone.isConnected()) {
                    startProgress();
                }
                break;

            case AttributeEvent.PARAMETERS_REFRESH_ENDED:
                stopProgress();
            //    alertUser("Parameters refreshed.");
                params = drone.getAttribute(AttributeType.PARAMETERS);
                if (params != null) {
                    Parameter paramTemp = params.getParameter("SENS_BOARD_Y_OFF");
                    pitchParamVal = paramTemp.getValue();
                    TextView tvTemp = (TextView) findViewById(R.id.textPitchParam);
                    tvTemp.setText(String.format("%.2f",pitchParamVal));

                    paramTemp = params.getParameter("SENS_BOARD_X_OFF");
                    rollParamVal = paramTemp.getValue();
                    tvTemp = (TextView) findViewById(R.id.textRollParam);
                    tvTemp.setText(String.format("%.2f",rollParamVal));

                }
                break;
            default:
//                Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    @Override
    public void onDroneConnectionFailed(ConnectionResult result) {
        alertUser("Connection Failed:" + result.getErrorMessage());
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    // UI Events
    // ==========================================================

    public void onBtnConnectTap(View view) {
        if (drone.isConnected()) {
            drone.disconnect();
        } else {
            Spinner connectionSelector = (Spinner) findViewById(R.id.selectConnectionType);
            int selectedConnectionType = connectionSelector.getSelectedItemPosition();

            Bundle extraParams = new Bundle();
            if (selectedConnectionType == ConnectionType.TYPE_USB) {
                extraParams.putInt(ConnectionType.EXTRA_USB_BAUD_RATE, DEFAULT_USB_BAUD_RATE); // Set default baud rate to 57600
            } else {
                extraParams.putInt(ConnectionType.EXTRA_UDP_SERVER_PORT, DEFAULT_UDP_PORT); // Set default baud rate to 14550
            }

            ConnectionParameter connectionParams = new ConnectionParameter(selectedConnectionType, extraParams, null);
            this.drone.connect(connectionParams);
        }

    }
/*
    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
            }
        });
    }
*/

    public void onArmButtonTap(View view) {
        State vehicleState = drone.getAttribute(AttributeType.STATE);
        if (vehicleState.isFlying()) {
            // Land
            drone.arm(false);
        } else if (vehicleState.isArmed()) {
            drone.arm(false);
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUser("Connect to a drone first");
        } else {
            // Connected but not Armed
            drone.arm(true);
        }
    }

    public void onBtnPitchPTap(View view){
        if(this.drone.isConnected()){
            Parameter paramTemp = params.getParameter("SENS_BOARD_Y_OFF");
            pitchParamVal = paramTemp.getValue();
            pitchParamVal += pitchParamInterval;
            TextView tvTemp = (TextView) findViewById(R.id.textPitchParam);
            tvTemp.setText(String.format("%.2f",pitchParamVal));
            paramTemp.setValue(pitchParamVal);
            List<Parameter> parametersList = new ArrayList<Parameter>(1);
            parametersList.add(paramTemp);
            drone.writeParameters(new Parameters(parametersList));
            mLoadingProgress.setVisibility(View.VISIBLE);
        }
    }

    public void onBtnPitchMTap(View view){
        if(this.drone.isConnected()){
            Parameter paramTemp = params.getParameter("SENS_BOARD_Y_OFF");
            pitchParamVal = paramTemp.getValue();
            pitchParamVal -= pitchParamInterval;
            TextView tvTemp = (TextView) findViewById(R.id.textPitchParam);
            tvTemp.setText(String.format("%.2f",pitchParamVal));
            paramTemp.setValue(pitchParamVal);
            List<Parameter> parametersList = new ArrayList<Parameter>(1);
            parametersList.add(paramTemp);
            drone.writeParameters(new Parameters(parametersList));
            mLoadingProgress.setVisibility(View.VISIBLE);
        }
    }

    public void onBtnRollPTap(View view){
        if(this.drone.isConnected()){
            Parameter paramTemp = params.getParameter("SENS_BOARD_X_OFF");
            rollParamVal = paramTemp.getValue();
            rollParamVal += rollParamInterval;
            TextView tvTemp = (TextView) findViewById(R.id.textRollParam);
            tvTemp.setText(String.format("%.2f",rollParamVal));
            paramTemp.setValue(rollParamVal);
            List<Parameter> parametersList = new ArrayList<Parameter>(1);
            parametersList.add(paramTemp);
            drone.writeParameters(new Parameters(parametersList));
            mLoadingProgress.setVisibility(View.VISIBLE);
        }
    }

    public void onBtnRollMTap(View view){
        if(this.drone.isConnected()){
            Parameter paramTemp = params.getParameter("SENS_BOARD_X_OFF");
            rollParamVal = paramTemp.getValue();
            rollParamVal -= rollParamInterval;
            TextView tvTemp = (TextView) findViewById(R.id.textRollParam);
            tvTemp.setText(String.format("%.2f",rollParamVal));
            paramTemp.setValue(rollParamVal);
            List<Parameter> parametersList = new ArrayList<Parameter>(1);
            parametersList.add(paramTemp);
            drone.writeParameters(new Parameters(parametersList));
            mLoadingProgress.setVisibility(View.VISIBLE);
        }

    }

    public void onBtnThrustPTap(View view){

    }

    public void onBtnThrustMTap(View view){

    }


    // UI updating
    // ==========================================================

    protected void updateConnectedButton(Boolean isConnected) {
        Button connectButton = (Button) findViewById(R.id.btnConnect);
        if (isConnected) {
            connectButton.setText("Disconnect");
        } else {
            connectButton.setText("Connect");
        }
    }

    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.btnArmTakeOff);

        if (!this.drone.isConnected()) {
            armButton.setVisibility(View.INVISIBLE);
        } else {
            armButton.setVisibility(View.VISIBLE);
        }

        if (vehicleState.isFlying()) {
            // Land
            armButton.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            armButton.setText("TAKE OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            armButton.setText("ARM");
        }
    }



    protected void updateVehicleModesForType(int droneType) {

        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    // Helper methods
    // ==========================================================

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    protected double distanceBetweenPoints(LatLongAlt pointA, LatLongAlt pointB) {
        if (pointA == null || pointB == null) {
            return 0;
        }
        double dx = pointA.getLatitude() - pointB.getLatitude();
        double dy = pointA.getLongitude() - pointB.getLongitude();
        double dz = pointA.getAltitude() - pointB.getAltitude();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void startProgress() {

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Refreshing parameters...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(true);
        progressDialog.show();

        mLoadingProgress.setIndeterminate(true);

        mLoadingProgress.setVisibility(View.VISIBLE);
    }

    private void updateProgress(int progress, int max) {
        if (progressDialog == null) {
            startProgress();
        }

        if (progressDialog.isIndeterminate()) {
            progressDialog.setIndeterminate(false);
            progressDialog.setMax(max);
        }
        progressDialog.setProgress(progress);

        if (mLoadingProgress.isIndeterminate()) {
            mLoadingProgress.setIndeterminate(false);
            mLoadingProgress.setMax(max);
        }
        mLoadingProgress.setProgress(progress);
    }

    private void stopProgress() {
        // dismiss progress dialog

        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }

        mLoadingProgress.setVisibility(View.GONE);
    }
}
