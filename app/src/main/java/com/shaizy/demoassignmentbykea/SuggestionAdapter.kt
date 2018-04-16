package com.shaizy.demoassignmentbykea

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

class SuggestionAdapter(context: Context) : ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item){

    private val mFilter = object : Filter(){
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            return FilterResults()
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
        }
    }

    override fun getFilter(): Filter {
        return mFilter
    }
}