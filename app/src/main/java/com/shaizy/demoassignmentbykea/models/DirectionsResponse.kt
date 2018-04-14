package com.shaizy.demoassignmentbykea.models

import com.google.gson.annotations.SerializedName

class DirectionsResponse {

    @SerializedName("geocoded_waypoints")
    var geocodedWaypoints: List<GeocodedWaypoint>? = null
    @SerializedName("routes")
    var routes: List<Route>? = null
    @SerializedName("status")
    var status: String? = null

}
