package com.shaizy.demoassignmentbykea

import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import android.widget.TextView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class RxSearchObservable {
    companion object {
        fun <T : TextView> fromView(textView: T) : Observable<String> {

            val subject = PublishSubject.create<String>()

            if (textView is AutoCompleteTextView){
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

            textView.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    subject.onNext(s?.toString() ?: "")
                }
            })

            return subject.debounce(300, TimeUnit.MILLISECONDS)
                    .filter { it.length > 3 }
                    .distinctUntilChanged()
        }
    }
}