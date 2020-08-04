package com.tamtran.trackme

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

open class RecordActivity : AppCompatActivity(), OnMapReadyCallback,
SharedPreferences.OnSharedPreferenceChangeListener, LocationListener, GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener {

    private lateinit var mapView: MapView
    private lateinit var map: GoogleMap
    private lateinit var latLng1: LatLng
    private lateinit var latLng2: LatLng
    private lateinit var layoutPause: LinearLayout
    private lateinit var buttonPause: Button
    private lateinit var textDistance: TextView
    private lateinit var textSpeed: TextView
    private lateinit var textDuration: TextView

    private val TAG = RecordActivity::class.java.simpleName
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    private val UPDATE_INTERVAL: Long = 60000 // Every 60 seconds.


    /**
     * The fastest rate for active location updates. Updates will never be more frequent
     * than this value, but they may be less frequent.
     */
    private val FASTEST_UPDATE_INTERVAL: Long = 5000 // Every 30 seconds


    /**
     * The max time before batched results are delivered by location services. Results may be
     * delivered sooner than this interval.
     */
    private val MAX_WAIT_TIME = UPDATE_INTERVAL * 5 // Every 5 minutes.

    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    private lateinit var mLocationRequest: LocationRequest

    var mLastLocation: Location? = null
    var mStartLocation: Location? = null
    var mStopLocation: Location? = null
    var mDistance: String? = null
    var mSpeed: String? = null
    var mDuration: String? = null
    var mCurrentLocation: Location? = null
    var mStartLocationMarker: Marker? = null
    var mCurrLocationMarker: Marker? = null
    var mGoogleApiClient: GoogleApiClient? = null
    var polyline: Polyline?= null
    var mSpeedList: List<Int> = ArrayList()
    /**
     * Provides access to the Fused Location Provider API.
     */
    private var mFusedLocationClient: FusedLocationProviderClient? = null

    companion object{

        const val MY_PERMISSIONS_REQUEST = 0
        const val DEFAULT_ZOOM = 15.0f

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_record)

        mapView = findViewById(R.id.lite_listrow_map)

        layoutPause = findViewById(R.id.pause_layout)

        buttonPause = findViewById(R.id.button_pause)

        textDistance = findViewById(R.id.text_distance)

        textSpeed = findViewById(R.id.text_avg_speed)

        textDuration = findViewById(R.id.text_time)

        requestPermission(Manifest.permission.ACCESS_FINE_LOCATION)

        // Check if the user revoked runtime permissions.
        if (!checkPermissions()) {
            requestPermissions()
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()

        buttonPause.visibility = View.VISIBLE
        layoutPause.visibility = View.GONE

        with(mapView) {
            // Initialise the MapView
            onCreate(null)
            // Set the map ready callback to receive the GoogleMap object
            getMapAsync(this@RecordActivity)
        }

        locations.let {
            latLng1 = it.first
            latLng2 = it.second
            mapView.tag = this
            // We need to call setMapLocation from here because RecyclerView might use the
            // previously loaded maps
            setMapLocation()
        }

    }

    @Synchronized
    protected fun buildGoogleApiClient() {
        mGoogleApiClient = GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(LocationServices.API).build()
        mGoogleApiClient?.connect()
    }

    override fun onStart() {
        super.onStart()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
    }


    override fun onResume() {
        super.onResume()
        mapView.onResume()
        Log.d(TAG, Utils.getRequestingLocationUpdates(this).toString())
        Log.d(TAG, Utils.getLocationUpdatesResult(this))
    }

    override fun onStop() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    private fun createLocationRequest() {
        mLocationRequest = LocationRequest()

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        // Note: apps running on "O" devices (regardless of targetSdkVersion) may receive updates
        // less frequently than this interval when the app is no longer in the foreground.
        mLocationRequest.interval = UPDATE_INTERVAL

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.fastestInterval = FASTEST_UPDATE_INTERVAL
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        // Sets the maximum time when batched location updates are delivered. Updates may be
        // delivered sooner than this interval.
        mLocationRequest.maxWaitTime = MAX_WAIT_TIME
    }

    private fun getPendingIntent(): PendingIntent? {
        // Note: for apps targeting API level 25 ("Nougat") or lower, either
        // PendingIntent.getService() or PendingIntent.getBroadcast() may be used when requesting
        // location updates. For apps targeting API level O, only
        // PendingIntent.getBroadcast() should be used. This is due to the limits placed on services
        // started in the background in "O".

        // TODO(developer): uncomment to use PendingIntent.getService().
//        Intent intent = new Intent(this, LocationUpdatesIntentService.class);
//        intent.setAction(LocationUpdatesIntentService.ACTION_PROCESS_UPDATES);
//        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        val intent = Intent(this, LocationUpdatesBroadcastReceiver::class.java)
        intent.action = LocationUpdatesBroadcastReceiver.ACTION_PROCESS_UPDATES
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Return the current state of the permissions needed.
     */
    private fun checkPermissions(): Boolean {
        val fineLocationPermissionState = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val backgroundLocationPermissionState = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
        return if (!Utils.hasPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            fineLocationPermissionState == PackageManager.PERMISSION_GRANTED &&
                    backgroundLocationPermissionState == PackageManager.PERMISSION_GRANTED
        else
            fineLocationPermissionState == PackageManager.PERMISSION_GRANTED

    }

    private fun requestPermissions() {
        val permissionAccessFineLocationApproved =
            (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            )
                    == PackageManager.PERMISSION_GRANTED)
        val backgroundLocationPermissionApproved =
            (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
                    == PackageManager.PERMISSION_GRANTED)
        val shouldProvideRationale =
            if (!Utils.hasPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            permissionAccessFineLocationApproved && backgroundLocationPermissionApproved
            else permissionAccessFineLocationApproved

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(
                TAG,
                "Displaying permission rationale to provide additional context."
            )
            Snackbar.make(
                findViewById<View>(R.id.activity_record),
                R.string.permission_rationale,
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(R.string.ok, View.OnClickListener { // Request permission
                    ActivityCompat.requestPermissions(
                        this@RecordActivity, arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ), REQUEST_PERMISSIONS_REQUEST_CODE
                    )
                })
                .show()
        } else {
            Log.i(TAG, "Requesting permission")
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(
                this@RecordActivity, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ), REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        Log.i(TAG, "onRequestPermissionResult")
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isEmpty()) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.")
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED
            ) {
                // Permission was granted.
                requestLocationUpdates()
            } else {
                // Permission denied.

                // Notify the user via a SnackBar that they have rejected a core permission for the
                // app, which makes the Activity useless. In a real app, core permissions would
                // typically be best requested during a welcome-screen flow.

                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.
                Snackbar.make(
                    findViewById<View>(R.id.activity_record),
                    R.string.permission_denied_explanation,
                    Snackbar.LENGTH_INDEFINITE
                )
                    .setAction(
                        R.string.settings,
                        View.OnClickListener { // Build intent that displays the App settings screen.
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri = Uri.fromParts(
                                "package",
                                BuildConfig.APPLICATION_ID, null
                            )
                            intent.data = uri
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        })
                    .show()
            }
        }
    }

    /**
     * Handles the Request Updates button and requests start of location updates.
     */
    private fun requestLocationUpdates() {
        try {
            Log.i(TAG, "Starting location updates")
            Utils.setRequestingLocationUpdates(this, true)
            mFusedLocationClient!!.requestLocationUpdates(mLocationRequest, getPendingIntent())
        } catch (e: SecurityException) {
            Utils.setRequestingLocationUpdates(this, false)
            e.printStackTrace()
        }
    }

    /**
     * Handles the Remove Updates button, and requests removal of location updates.
     */
    fun removeLocationUpdates() {
        Log.i(TAG, "Removing location updates")
        Utils.setRequestingLocationUpdates(this, false)
        mFusedLocationClient!!.removeLocationUpdates(getPendingIntent())
    }

     fun onClickToPause(view: View) {
         //stop location updates
         if (mGoogleApiClient != null) {
             LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this)
         }
        buttonPause.visibility = View.GONE
        layoutPause.visibility = View.VISIBLE
    }

     fun onClickToStop(view: View) {
         //stop location updates
         if (mGoogleApiClient != null) {
             LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this)
         }

         mStopLocation = mLastLocation
         mSpeed = getAverageSpeed()
         mDistance = textDistance.text as String?
         mDuration = textDuration.text as String?

         val startLatLng = mStartLocation?.latitude?.let { mStartLocation?.longitude?.let { it1 ->
             LatLng(it,
                 it1
             )
         } }

         val endLatLng = mStopLocation?.latitude?.let { mStopLocation?.longitude?.let { it1 ->
             LatLng(it,
                 it1
             )
         } }

         val trackInfo = TrackInfo(startLatLng, endLatLng, mDuration, mSpeed, mDistance)

         val intent = Intent()
         intent.putExtra("trackInfo", trackInfo)
         setResult(1, intent)
        finish()
    }

     fun onClickToReplay(view: View) {
         if (ContextCompat.checkSelfPermission(
                 this,
                 Manifest.permission.ACCESS_FINE_LOCATION
             )
             == PackageManager.PERMISSION_GRANTED
         ) {
             LocationServices.FusedLocationApi.requestLocationUpdates(
                 mGoogleApiClient,
                 mLocationRequest,
                 this
             )
         }
        buttonPause.visibility = View.VISIBLE
        layoutPause.visibility = View.GONE
    }

    private fun requestPermission(permission: String) {
        if (ContextCompat.checkSelfPermission(
                this,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this, arrayOf(permission),
                RecordActivity.MY_PERMISSIONS_REQUEST
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearView()
    }

    private fun setMapLocation() {
        if (!::map.isInitialized) return
        with(map) {
            mapType = GoogleMap.MAP_TYPE_NORMAL
            setOnMapClickListener {

            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        MapsInitializer.initialize(applicationContext)
        // If map is not initialised properly
        map = googleMap ?: return
        map.uiSettings.isMyLocationButtonEnabled = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                buildGoogleApiClient()
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = true
            }
        }
        else {
            buildGoogleApiClient();
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
        }

        setMapLocation()
    }

    /** This function is called by the recycleListener, when we need to clear the map. */
    private fun clearView() {
        with(map) {
            // Clear the map and free up resources by changing the map type to none
            clear()
            mapType = GoogleMap.MAP_TYPE_NONE
        }
    }

    //Get JSON data from Google Direction
    inner class TaskDirectionRequest : AsyncTask<String, Void?, String>() {
        override fun doInBackground(vararg p0: String): String? {
            var responseString = ""
            try {
                responseString = requestDirection(p0[0])
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            return responseString
        }

        override fun onPostExecute(responseString: String) {
            super.onPostExecute(responseString)
            //Json object parsing
            val parseResult = TaskParseDirection()
            parseResult.execute(responseString)
            val parseTrackResult = TaskParseTrackInfo()
            parseTrackResult.execute(responseString)
        }
    }

    inner class TaskParseTrackInfo : AsyncTask<String, Void, Pair<String, String>>() {
        override fun doInBackground(vararg jsonString: String): Pair<String, String> {
            val distance = JSONObject(jsonString[0]).getJSONArray("routes").getJSONObject(0)
                .getJSONArray("legs").getJSONObject(0).getJSONObject("distance").getString("text");
            val duration = JSONObject(jsonString[0]).getJSONArray("routes").getJSONObject(0)
                .getJSONArray("legs").getJSONObject(0).getJSONObject("duration").getString("text");
            return Pair(distance, duration)
        }

        override fun onPostExecute(lists: Pair<String, String>) {
            textDistance.text = lists.first
            textDuration.text = lists.second
        }
    }


    //Parse JSON Object from Google Direction API & display it on Map
    inner class TaskParseDirection :
        AsyncTask<String, Void, List<List<HashMap<String, String>>>>() {
        override fun doInBackground(vararg jsonString: String): List<List<HashMap<String, String>>>? {
            var routes: List<List<HashMap<String, String>>>? = null
            var jsonObject: JSONObject? = null
            try {
                jsonObject = JSONObject(jsonString[0])
                val parser = DirectionParser()
                routes = parser.parse(jsonObject)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return routes
        }

        override fun onPostExecute(lists: List<List<HashMap<String, String>>>?) {
            super.onPostExecute(lists)
            var points: ArrayList<LatLng>? = null
            var polylineOptions: PolylineOptions? = null

            if (lists == null) {
                return
            }

            for (path in lists!!) {
                points = ArrayList()
                polylineOptions = PolylineOptions()
                for (point in path) {
                    val lat = point["lat"]?.let { java.lang.Double.parseDouble(it) }
                    val lon = point["lng"]?.let { java.lang.Double.parseDouble(it) }
                    points?.add(lat?.let { lon?.let { it1 -> LatLng(it, it1) } }!!)
                }
                polylineOptions.addAll(points)
                polylineOptions.width(5f)
                polylineOptions.color(Color.BLUE)
                polylineOptions.geodesic(true)
            }
            if (polylineOptions != null) {
                polyline?.remove()
               polyline = map.addPolyline(polylineOptions)
            } else {
                Toast.makeText(applicationContext, "Direction not found", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private val locations: Pair<LatLng, LatLng> = Pair(LatLng(10.770457, 106.706177), LatLng(10.789196, 106.715597))

    /**
     * Create requested url for Direction API to get routes from origin to destination
     *
     * @param origin
     * @param destination
     * @return
     */
    private fun getRequestedUrl(origin: LatLng, destination: LatLng): String? {
        val strOrigin = "origin=" + origin.latitude + "," + origin.longitude
        val strDestination =
            "destination=" + destination.latitude + "," + destination.longitude
        val sensor = "sensor=false"
        val mode = "mode=driving"
        val param = "$strOrigin&$strDestination&$sensor&$mode&"
        val output = "json"
        val APIKEY = "key=" + getString(R.string.maps_api_key)
        return "https://maps.googleapis.com/maps/api/directions/$output?$param$APIKEY"
    }

    /**
     * Request direction from Google Direction API
     *
     * @param requestedUrl see [.buildRequestUrl]
     * @return JSON data routes/direction
     */
    private fun requestDirection(requestedUrl: String): String {
        var responseString = ""
        var inputStream: InputStream? = null
        var httpURLConnection: HttpURLConnection? = null
        try {
            val url = URL(requestedUrl)
            httpURLConnection = url.openConnection() as HttpURLConnection
            httpURLConnection.readTimeout = 15000
            httpURLConnection.connectTimeout = 15000
            httpURLConnection.doInput = true
            httpURLConnection.connect()
            inputStream = httpURLConnection.inputStream
            val reader = InputStreamReader(inputStream)
            val bufferedReader = BufferedReader(reader)
            val stringBuffer = StringBuffer()
            var line: String? = ""
            while (bufferedReader.readLine().also { line = it } != null) {
                stringBuffer.append(line)
            }
            responseString = stringBuffer.toString()
            bufferedReader.close()
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        httpURLConnection?.disconnect()
        return responseString
    }

    override fun onSharedPreferenceChanged(p0: SharedPreferences?, p1: String?) {
        if (p1 == Utils.KEY_LOCATION_UPDATES_RESULT) {
           Log.d(TAG, Utils.getLocationUpdatesResult(this))
        } else if (p1 == Utils.KEY_LOCATION_UPDATES_REQUESTED) {
            Log.d(TAG, Utils.getRequestingLocationUpdates(this).toString())
        }
    }

    override fun onLocationChanged(location: Location?) {
        mLastLocation = location
        if (mCurrLocationMarker != null) {
            mCurrLocationMarker!!.remove()
        }
        //Place current location marker
        val latLng = location?.latitude?.let { LatLng(it, location?.longitude) }

        if (mStartLocation == null) {
            mStartLocation = location
            latLng1 = mStartLocation?.longitude?.let { mStartLocation?.latitude?.let { it1 ->
                LatLng(
                    it1, it)
            } }!!
            val markerOptions = MarkerOptions()
            latLng1?.let { markerOptions.position(it) }
            markerOptions.title("Start Position")
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            mStartLocationMarker = map.addMarker(markerOptions)
        }

        //move map camera
        mCurrentLocation = location
        latLng2 = mCurrentLocation?.longitude?.let { mCurrentLocation?.latitude?.let { it1 ->
            LatLng(
                it1, it)
        } }!!
        val markerOptions = MarkerOptions()
        latLng?.let { markerOptions.position(it) }
        markerOptions.title("End Position")
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        mCurrLocationMarker = map.addMarker(markerOptions)
        //move map camera
        val builder = LatLngBounds.Builder()
        builder.include(latLng1)
        builder.include(latLng2)
        val bounds = builder.build()
        val padding = 100 // padding around start and end marker
        val cu = CameraUpdateFactory.newLatLngBounds(bounds, padding)
        map.animateCamera(cu)

        //stop location updates

        TaskDirectionRequest().execute(getRequestedUrl(latLng1, latLng2))

        var speed = mCurrentLocation?.speed
        var realSpeed = ((speed?.times(3600))?.div(1000))?.toInt()
        realSpeed?.let { (mSpeedList as ArrayList).add(it) }
        Handler().post{ textSpeed.text = realSpeed.toString() + " km/h"}

    }

    override fun onConnected(p0: Bundle?) {
        mLocationRequest = LocationRequest()
        mLocationRequest.interval = 5000
        mLocationRequest.fastestInterval = 5000
        mLocationRequest.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient,
                mLocationRequest,
                this
            )
        }
    }

    override fun onConnectionSuspended(p0: Int) {

    }

    override fun onConnectionFailed(p0: ConnectionResult) {

    }

    private fun getAverageSpeed(): String {
        var averageSpeed = 0

        for (speed in mSpeedList) {
            averageSpeed += speed
        }
        return (averageSpeed/mSpeedList.size).toString()
    }
}