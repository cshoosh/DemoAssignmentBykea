package com.shaizy.demoassignmentbykea

import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import android.widget.TextView
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.places.AutocompleteFilter
import com.google.android.gms.location.places.Places
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class RxSearchObservable {
    companion object {

        fun <T : TextView> fromView(textView: T): Observable<String> {

            val subject = PublishSubject.create<String>()

            if (textView is AutoCompleteTextView) {
                textView.setOnItemClickListener { parent, view, position, id ->
                    val item = parent.adapter?.getItem(position)?.toString()

                    subject.onNext(item ?: "")
                    subject.onComplete()
                }
            }

            textView.setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
                        || event?.keyCode == KeyEvent.KEYCODE_ENTER) {

                    subject.onComplete()
                    return@setOnEditorActionListener true
                }

                false
            }

            val listener = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    subject.onNext(s?.toString() ?: "")
                }
            }

            textView.addTextChangedListener(listener)

            return subject
                    .doOnDispose { textView.removeTextChangedListener(listener) }
                    .doOnComplete { textView.removeTextChangedListener(listener) }
                    .filter { it.trim().length > 3 }
                    .distinctUntilChanged()
                    .debounce(500, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())

        }
    }
}