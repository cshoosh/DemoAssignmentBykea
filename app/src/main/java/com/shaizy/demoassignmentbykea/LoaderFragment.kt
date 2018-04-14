package com.royalcyber.circuitcity.screens.fragments

import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.shaizy.demoassignmentbykea.R

/**
 * Created by syed.shahnawaz on 7/20/2017.
 * $Class
 */

class LoaderFragment : DialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.view_large_progress_bar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog.window.setBackgroundDrawableResource(android.R.color.transparent)
    }

    companion object {
        const val TAG = "loader"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return

            val fragment = LoaderFragment()
            fragment.isCancelable = false

            fragment.show(fragmentManager, TAG)
        }

        fun hide(fragmentManager: FragmentManager){
            val loader = fragmentManager.findFragmentByTag(TAG)
                    as? LoaderFragment

            loader?.dismiss()
        }


    }
}