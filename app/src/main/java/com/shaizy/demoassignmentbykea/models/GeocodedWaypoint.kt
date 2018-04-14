package com.shaizy.demoassignmentbykea.models

import com.google.gson.annotations.SerializedName

class GeocodedWaypoint {

    @SerializedName("geocoder_status")
    var geocoderStatus: String? = null
    @SerializedName("place_id")
    var placeId: String? = null
    @SerializedName("types")
    var types: List<String>? = null

}
