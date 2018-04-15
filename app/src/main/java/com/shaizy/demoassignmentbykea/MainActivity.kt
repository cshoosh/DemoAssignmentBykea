package com.shaizy.demoassignmentbykea

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.location.Geocoder
import android.os.Bundle
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.places.AutocompleteFilter
import com.google.android.gms.location.places.Places
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.gson.Gson
import com.google.maps.android.PolyUtil
import com.royalcyber.circuitcity.screens.fragments.LoaderFragment
import com.shaizy.demoassignmentbykea.models.DirectionsResponse
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

class MainActivity : AppCompatActivity() {

    private val mScreen by lazy {
        val point = Point()
        window.windowManager.defaultDisplay.getSize(point)
        point
    }

    // Rough bounds fetched from google maps. For KARACHI
    private val mKarachiBounds = LatLngBounds.builder()
            .include(LatLng(25.0241748, 66.8994962))
            .include(LatLng(24.753090, 67.267905))
            .build()

    private val mTypeFilter = AutocompleteFilter.Builder()
            .setCountry("PK")
            .build()

    private lateinit var mMap: GoogleMap

    private var mPickupSubscription: Disposable? = null
    private var mDropOffSubscription: Disposable? = null
    private var mGeoCoderSubscription: Disposable? = null

    private lateinit var mGoogleClient: GoogleApiClient

    private val mPickUpMarkerOption by lazy {
        MarkerOptions()
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
    }

    private val mDropOffMarkerOption by lazy {
        MarkerOptions()
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE))
    }

    private var mPickupMarker: Marker? = null
    private var mDropoffMarker: Marker? = null

    private val mGeoCoder by lazy { Geocoder(this) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapsInitializer.initialize(this)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            checkForPermissions()
            return
        }

        init()
    }


    private fun checkForPermissions() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            AlertDialog.Builder(this)
                    .setMessage(R.string.permission_denied)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, { _, _ ->
                        startActivityForResult(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_SETTINGS)
                    })
                    .show()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    REQUEST_LOCATION_PERMISSION_CODE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun init() {
        setContentView(R.layout.activity_main)

        mGoogleClient = GoogleApiClient.Builder(this)
                .addOnConnectionFailedListener {
                    AlertDialog.Builder(this)
                            .setMessage(R.string.google_client_needed)
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.ok, { _, _ ->
                                finish()
                            })
                            .show()
                }
                .addApi(Places.PLACE_DETECTION_API)
                .addApi(Places.GEO_DATA_API)
                .build()

        mGoogleClient.connect()

        (map as SupportMapFragment).getMapAsync {
            mMap = it

            // Hard Coded BYKEA LatLng for quick development
            val bykeaLatlng = LatLng(24.7784557, 67.0546954)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(bykeaLatlng, 20f))

            mMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
                override fun onMarkerDragEnd(p0: Marker?) {
                    if (p0 == null || mPickupMarker == null || mDropoffMarker == null) return

                    val isPickUpMarker = p0 == mPickupMarker

                    mMap.clear()

                    getNavigationObservable(mPickupMarker!!.position, mDropoffMarker!!.position)
                            .subscribe({

                                if (isPickUpMarker) {
                                    val address = it.first.routes?.firstOrNull()?.legs?.firstOrNull()?.startAddress
                                    mPickUpMarkerOption.title(address)
                                    mPickupSubscription?.dispose()

                                    edtAutoPickUp.setText(address)
                                    subscribePickUp()
                                } else {
                                    val address = it.first.routes?.firstOrNull()?.legs?.firstOrNull()?.endAddress
                                    mDropOffMarkerOption.title(address)
                                    mDropOffSubscription?.dispose()

                                    edtAutoDestination.setText(address)
                                    subscribeDestination(mPickupMarker!!.position)
                                }

                                mPickupMarker = mMap.addMarker(mPickUpMarkerOption.position(mPickupMarker!!.position))
                                mDropoffMarker = mMap.addMarker(mDropOffMarkerOption.position(mDropoffMarker!!.position))

                                it?.second?.let { polyData ->
                                    addNavigation(polyData)
                                }

                                mMap.animateCamera(CameraUpdateFactory
                                        .newLatLngBounds(LatLngBounds.builder()
                                                .include(mPickupMarker!!.position)
                                                .include(mDropoffMarker!!.position)
                                                .build(), mScreen.x - 150, mScreen.y - 150, 100))


                            }, {
                                it.printStackTrace()
                                Toast.makeText(this@MainActivity, R.string.something_went_wrong, Toast.LENGTH_SHORT)
                                        .show()
                            })
                }

                override fun onMarkerDragStart(p0: Marker?) {
                }

                override fun onMarkerDrag(p0: Marker?) {
                }
            })
        }

        edtAutoPickUp.setAdapter(SuggestionAdapter(this))
        edtAutoDestination.setAdapter(SuggestionAdapter(this))

        edtAutoPickUp.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                if (::mMap.isInitialized)
                    mMap.clear()

                edtAutoDestination.visibility = View.GONE

                subscribePickUp()
                mDropOffSubscription?.dispose()
            }
        }

        edtAutoDestination.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                edtAutoDestination.text.clear()
                mDropoffMarker?.isVisible = false
            }
        }

        subscribePickUp()
    }

    private fun subscribePickUp() {

        mPickupSubscription?.dispose()
        mPickupSubscription = RxSearchObservable.fromView(edtAutoPickUp)
                .flatMap { input ->
                    Log.d("Places", "API Called")

                    val list = Places.GeoDataApi.getAutocompletePredictions(mGoogleClient, input, mKarachiBounds, mTypeFilter)
                            .await().toList()

                    Observable.just(list)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val adapter = edtAutoPickUp.adapter as SuggestionAdapter

                    adapter.clear()
                    adapter.addAll(
                            *it.take(5)
                                    .map { it.getFullText(null).toString() }
                                    .toTypedArray()
                    )

                }, {
                    it.printStackTrace()
                }, {
                    subscribePickUp()

                    val address = edtAutoPickUp.text.toString()
                    if (Geocoder.isPresent()) {

                        mGeoCoderSubscription = getGeoCoderObservable(address)
                                .doOnSubscribe { LoaderFragment.show(supportFragmentManager) }
                                .doOnDispose { LoaderFragment.hide(supportFragmentManager) }
                                .doOnComplete { LoaderFragment.hide(supportFragmentManager) }
                                .doOnEach { LoaderFragment.hide(supportFragmentManager) }
                                .subscribe({
                                    subscribeDestination(it)

                                    mMap.clear()

                                    mPickupMarker = mMap.addMarker(mPickUpMarkerOption.title(address).position(it))

                                    mMap.animateCamera(
                                            CameraUpdateFactory.newLatLngZoom(it, 17f)
                                    )
                                }, {
                                    it.printStackTrace()
                                })

                    }
                })
    }

    private fun subscribeDestination(latLongSource: LatLng) {
        edtAutoDestination.visibility = View.VISIBLE
        edtAutoDestination.requestFocus()

        mDropOffSubscription?.dispose()
        mDropOffSubscription = RxSearchObservable.fromView(edtAutoDestination)
                .flatMap { input ->
                    Observable.just(Places.GeoDataApi.getAutocompletePredictions(mGoogleClient, input, mKarachiBounds, mTypeFilter)
                            .await().toList())
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val adapter = edtAutoDestination.adapter as SuggestionAdapter

                    adapter.clear()
                    adapter.addAll(
                            *it.take(5)
                                    .map { it.getFullText(null).toString() }
                                    .toTypedArray()
                    )
                }, {
                    it.printStackTrace()
                }, {
                    subscribeDestination(latLongSource)

                    val address = edtAutoDestination.text.toString()
                    if (Geocoder.isPresent()) {
                        lateinit var destination: LatLng
                        mGeoCoderSubscription = getGeoCoderObservable(address)
                                .flatMap {
                                    destination = it
                                    getNavigationObservable(latLongSource, it)
                                }
                                .doOnSubscribe { LoaderFragment.show(supportFragmentManager) }
                                .doOnDispose { LoaderFragment.hide(supportFragmentManager) }
                                .doOnComplete { LoaderFragment.hide(supportFragmentManager) }
                                .doOnError { LoaderFragment.hide(supportFragmentManager) }
                                .subscribe({
                                    mMap.clear()

                                    mPickupMarker = mMap.addMarker(mPickUpMarkerOption)
                                    mDropoffMarker = mMap.addMarker(mDropOffMarkerOption.position(destination).title(address))

                                    it?.second?.let { polyData ->
                                        addNavigation(polyData)
                                    }

                                    mMap.animateCamera(CameraUpdateFactory
                                            .newLatLngBounds(LatLngBounds.builder()
                                                    .include(destination)
                                                    .include(latLongSource)
                                                    .build(), mScreen.x - 150, mScreen.y - 150, 100))
                                }, {
                                    it.printStackTrace()
                                })
                    }
                })
    }

    private fun addNavigation(polyData: List<LatLng>) {
        mMap.addPolyline(
                PolylineOptions()
                        .addAll(polyData))
    }

    override fun onResume() {
        super.onResume()
        if (::mGoogleClient.isInitialized && !mGoogleClient.isConnected) {
            mGoogleClient.connect()
        }

        if (edtAutoPickUp != null) {
            subscribePickUp()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::mGoogleClient.isInitialized && mGoogleClient.isConnected) {
            mGoogleClient.disconnect()
        }

        mPickupSubscription?.dispose()
        mDropOffSubscription?.dispose()
        mGeoCoderSubscription?.dispose()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_SETTINGS -> {
                if (resultCode == Activity.RESULT_OK) {
                    init()
                } else {
                    checkForPermissions()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_LOCATION_PERMISSION_CODE) {
            if (permissions.first() == Manifest.permission.ACCESS_COARSE_LOCATION
                    && grantResults.first() == PackageManager.PERMISSION_GRANTED) {
                init()
            } else {
                AlertDialog.Builder(this)
                        .setMessage(R.string.permission_denied)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, { _, _ ->
                            checkForPermissions()
                        })
                        .show()
            }
        }
    }

    private fun getNavigationObservable(origin: LatLng, destination: LatLng): Observable<Pair<DirectionsResponse, List<LatLng>?>> {
        return Observable.create<Pair<DirectionsResponse, List<LatLng>?>> {

            val stringBuilder = StringBuilder()


            val utf8 = "UTF-8"
            val query = String.format("origin=%s&destination=%s&key=%s",
                    URLEncoder.encode(origin.latitude.toString() + "," + origin.longitude.toString(), utf8),
                    URLEncoder.encode(destination.latitude.toString() + "," + destination.longitude.toString(), utf8),
                    URLEncoder.encode(getString(R.string.google_places_key), utf8))

            val connection = (URL("https://maps.googleapis.com/maps/api/directions/json?$query")
                    .openConnection() as HttpsURLConnection)
            try {
                connection.connectTimeout = 5000
                connection.addRequestProperty("Content-Type", "application/json")
                connection.requestMethod = "GET"
                connection.connect()

                val reader = BufferedReader(InputStreamReader(connection.inputStream))


                var line: String?

                do {
                    line = reader.readLine()
                    if (line != null)
                        stringBuilder.append(line)
                } while (line != null)


            } finally {
                connection.disconnect()
            }

            if (it.isDisposed) return@create

            if (stringBuilder.isEmpty())
                it.onError(IOException("Navigation Not Found"))
            else {
                val route = Gson().fromJson(stringBuilder.toString(), DirectionsResponse::class.java)
                it.onNext(
                        Pair(route,
                                PolyUtil.decode(route.routes?.firstOrNull()?.overviewPolyline?.points)))
            }

            it.onComplete()
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())


    }

    private fun getGeoCoderObservable(address: String): Observable<LatLng> {
        return Observable.create<LatLng> {

            val geoCodedAddress = mGeoCoder.getFromLocationName(address, 1).firstOrNull()

            if (geoCodedAddress != null && !it.isDisposed) {
                it.onNext(LatLng(geoCodedAddress.latitude, geoCodedAddress.longitude))
            }

            if (!it.isDisposed)
                it.onComplete()
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    companion object {
        const val REQUEST_LOCATION_PERMISSION_CODE = 1001
        const val REQUEST_SETTINGS = 1002
    }
}
