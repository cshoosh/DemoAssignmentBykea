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
import android.view.View
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.location.places.AutocompleteFilter
import com.google.android.gms.location.places.AutocompletePredictionBuffer
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
            .include(LatLng(24.750203, 66.890898))
            .include(LatLng(25.0241748, 66.8994962))
            .include(LatLng(25.009097, 67.329168))
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

    private val mPickUpMarker by lazy {
        MarkerOptions()
                .draggable(true)
                .visible(false)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
    }

    private val mDropOffMarker by lazy {
        MarkerOptions()
                .draggable(true)
                .visible(false)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE))
    }


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
            mMap.addMarker(mPickUpMarker.position(bykeaLatlng))
            mMap.addMarker(mDropOffMarker.position(bykeaLatlng))

        }

        edtAutoPickUp.setAdapter(SuggestionAdapter(this))
        edtAutoDestination.setAdapter(SuggestionAdapter(this))

        edtAutoPickUp.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                mPickUpMarker.visible(false)
                mDropOffMarker.visible(false)

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
                mDropOffMarker.visible(false)
            }
        }

        subscribePickUp()
    }

    private fun subscribePickUp() {

        var pendingResultPickUp: PendingResult<AutocompletePredictionBuffer>? = null

        mPickupSubscription?.dispose()
        mPickupSubscription = RxSearchObservable.fromView(edtAutoPickUp)
                .doOnDispose { pendingResultPickUp?.cancel() }
                .subscribe({
                    pendingResultPickUp?.cancel()
                    pendingResultPickUp =
                            Places.GeoDataApi.getAutocompletePredictions(mGoogleClient, it, mKarachiBounds, mTypeFilter)

                    pendingResultPickUp?.setResultCallback {
                        val adapter = edtAutoPickUp.adapter as SuggestionAdapter

                        adapter.clear()
                        adapter.addAll(
                                *it.take(5)
                                        .map { it.getFullText(null).toString() }
                                        .toTypedArray()
                        )
                    }
                }, {
                    it.printStackTrace()
                }, {
                    subscribePickUp()

                    val address = edtAutoPickUp.text.toString()
                    if (Geocoder.isPresent()) {

                        mGeoCoderSubscription = Observable.create<LatLng> {
                            val geoCodedAddress = Geocoder(this)
                                    .getFromLocationName(address, 1).firstOrNull()

                            if (geoCodedAddress != null && !it.isDisposed) {
                                it.onNext(LatLng(geoCodedAddress.latitude, geoCodedAddress.longitude))
                            }

                            if (!it.isDisposed)
                                it.onComplete()
                        }
                                .doOnSubscribe { LoaderFragment.show(supportFragmentManager) }
                                .doOnComplete { LoaderFragment.hide(supportFragmentManager) }
                                .doOnDispose { LoaderFragment.hide(supportFragmentManager) }
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe({
                                    subscribeDestination(it)

                                    mPickUpMarker.title(address)
                                            .visible(true)
                                            .position(it)

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

        var pendingResultDestination: PendingResult<AutocompletePredictionBuffer>? = null

        mDropOffSubscription?.dispose()
        mDropOffSubscription = RxSearchObservable.fromView(edtAutoDestination)
                .doOnDispose { pendingResultDestination?.cancel() }
                .subscribe({
                    pendingResultDestination?.cancel()
                    pendingResultDestination =
                            Places.GeoDataApi.getAutocompletePredictions(mGoogleClient, it, mKarachiBounds, mTypeFilter)

                    pendingResultDestination?.setResultCallback {
                        val adapter = edtAutoDestination.adapter as SuggestionAdapter

                        adapter.clear()
                        adapter.addAll(
                                *it.take(5)
                                        .map { it.getFullText(null).toString() }
                                        .toTypedArray()
                        )
                    }
                }, {
                    it.printStackTrace()
                }, {
                    subscribeDestination(latLongSource)

                    val address = edtAutoDestination.text.toString()
                    if (Geocoder.isPresent()) {
                        mGeoCoderSubscription = Observable.create<Pair<LatLng, String>> {
                            val geoCodedAddress = Geocoder(this)
                                    .getFromLocationName(address, 1).firstOrNull()

                            val stringBuilder = StringBuilder()

                            if (geoCodedAddress != null && !it.isDisposed) {
                                val latLong = LatLng(geoCodedAddress.latitude, geoCodedAddress.longitude)

                                val utf8 = "UTF-8"
                                val query = String.format("origin=%s&destination=%s&key=%s",
                                        URLEncoder.encode(latLongSource.latitude.toString() + "," + latLongSource.longitude.toString(), utf8),
                                        URLEncoder.encode(latLong.latitude.toString() + "," + latLong.longitude.toString(), utf8),
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

                                it.onNext(Pair(latLong, stringBuilder.toString()))
                            }

                            if (!it.isDisposed)
                                it.onComplete()
                        }
                                .doOnSubscribe { LoaderFragment.show(supportFragmentManager) }
                                .doOnComplete { LoaderFragment.hide(supportFragmentManager) }
                                .doOnDispose { LoaderFragment.hide(supportFragmentManager) }
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe({
                                    mMap.clear()

                                    val json =
                                            if (it.second.isNotEmpty()) Gson().fromJson(it.second, DirectionsResponse::class.java)
                                            else null

                                    mDropOffMarker.visible(true)
                                            .position(it.first)
                                            .title(address)

                                    mMap.addMarker(mPickUpMarker)
                                    mMap.addMarker(mDropOffMarker)

                                    json?.routes?.firstOrNull()?.overviewPolyline?.points?.let { polyData ->
                                        mMap.addPolyline(
                                                PolylineOptions()
                                                        .addAll(
                                                                PolyUtil.decode(polyData)
                                                        ))
                                    }

                                    mMap.animateCamera(CameraUpdateFactory
                                            .newLatLngBounds(LatLngBounds.builder()
                                                    .include(it.first)
                                                    .include(latLongSource)
                                                    .build(), mScreen.x - 150, mScreen.y - 150, 100))
                                }, {
                                    it.printStackTrace()
                                })

                    }
                })
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

    companion object {
        const val REQUEST_LOCATION_PERMISSION_CODE = 1001
        const val REQUEST_SETTINGS = 1002
    }
}
