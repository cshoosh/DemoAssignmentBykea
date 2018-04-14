package com.shaizy.demoassignmentbykea.models

import com.google.gson.annotations.SerializedName

class Bounds {

    @SerializedName("northeast")
    var northeast: Northeast? = null
    @SerializedName("southwest")
    var southwest: Southwest? = null

}
