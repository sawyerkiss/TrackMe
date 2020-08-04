package com.tamtran.trackme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : AppCompatActivity() {

    private val linearLayoutManager: LinearLayoutManager by lazy {
        LinearLayoutManager(this)
    }

    private val gridLayoutManager: GridLayoutManager by lazy {
        GridLayoutManager(this, 2)
    }

    companion object{

        const val MY_PERMISSIONS_REQUEST = 0

    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var mapAdapter: RecyclerView.Adapter<MapAdapter.ViewHolder>

    /** A list of locations of to show in the RecyclerView */
    private val trackInfoList: List<TrackInfo> = ArrayList()

    private val recycleListener = RecyclerView.RecyclerListener { holder ->
        val mapHolder = holder as MapAdapter.ViewHolder
        mapHolder.clearView()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermission(Manifest.permission.ACCESS_FINE_LOCATION);

        mapAdapter = MapAdapter()

        // Initialise the RecyclerView.
        recyclerView = findViewById<RecyclerView>(R.id.recycler_view).apply {
            setHasFixedSize(true)
            layoutManager = linearLayoutManager
            adapter = mapAdapter
            setRecyclerListener(recycleListener)
        }

        if (getString(R.string.maps_api_key).isEmpty()) {
            Toast.makeText(
                this,
                "Add your own API key in MAPS_API_KEY=YOUR_API_KEY",
                Toast.LENGTH_LONG
            ).show()
        }

    }

    fun onClickToRecord(view: View) {
            startActivityForResult(Intent(this, RecordActivity::class.java),1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (data?.extras != null) {
                val trackInfo: TrackInfo = data.getParcelableExtra("trackInfo") as TrackInfo
                (trackInfoList as ArrayList).add(trackInfo)
                mapAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun requestPermission(permission: String) {
        if (ContextCompat.checkSelfPermission(
                this,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this, arrayOf(permission),
                MY_PERMISSIONS_REQUEST
            )
        }
    }

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

    inner class MapAdapter : RecyclerView.Adapter<MapAdapter.ViewHolder>() {

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindView(position)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflated = LayoutInflater.from(parent.context)
                .inflate(R.layout.lite_list_demo_row, parent, false)
            return ViewHolder(inflated)
        }

        override fun getItemCount() = trackInfoList.size

        /** A view holder for the map and title. */
        inner class ViewHolder(view: View) :
            RecyclerView.ViewHolder(view),
            OnMapReadyCallback {

            private val layout: View = view
            private val mapView: MapView = layout.findViewById(R.id.lite_listrow_map)
            private val textDistance: TextView = layout.findViewById(R.id.text_distance)
            private val textAvgSpeed: TextView = layout.findViewById(R.id.text_avg_speed)
            private val textDuration: TextView = layout.findViewById(R.id.text_time)
            private lateinit var map: GoogleMap
            private lateinit var latLng1: LatLng
            private lateinit var latLng2: LatLng

            /** Initialises the MapView by calling its lifecycle methods */
            init {
                with(mapView) {
                    // Initialise the MapView
                    onCreate(null)
                    // Set the map ready callback to receive the GoogleMap object
                    getMapAsync(this@ViewHolder)
                }
            }

            private fun setMapLocation() {
                if (!::map.isInitialized) return
                with(map) {
                    val builder = LatLngBounds.Builder()
                    builder.include(latLng1)
                    builder.include(latLng2)
                    val bounds = builder.build()
                    val padding = 100 // padding around start and end marker
                    val cu = CameraUpdateFactory.newLatLngBounds(bounds, padding)
                    map.animateCamera(cu)
                    addMarker(MarkerOptions().position(latLng1))
                    addMarker(MarkerOptions().position(latLng2))
                    TaskDirectionRequest().execute(getRequestedUrl(latLng1, latLng2))
                    mapType = GoogleMap.MAP_TYPE_NORMAL
                    setOnMapClickListener {

                    }
                }
            }

            override fun onMapReady(googleMap: GoogleMap?) {
                MapsInitializer.initialize(applicationContext)
                // If map is not initialised properly
                map = googleMap ?: return
                setMapLocation()
            }

            /** This function is called when the RecyclerView wants to bind the ViewHolder. */
            fun bindView(position: Int) {
                trackInfoList[position].let {
                    latLng1 = it.startLocation
                    latLng2 = it.endLocation
                    textDistance.text = it.distance
                    textAvgSpeed.text = it.avgSpeed + " km/h"
                    textDuration.text = it.duration

                    mapView.tag = this
                    // We need to call setMapLocation from here because RecyclerView might use the
                    // previously loaded maps
                    setMapLocation()
                }
            }

            /** This function is called by the recycleListener, when we need to clear the map. */
            fun clearView() {
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
                        map.addPolyline(polylineOptions)
                    } else {
                        Toast.makeText(applicationContext, "Direction not found", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }
    }
}
