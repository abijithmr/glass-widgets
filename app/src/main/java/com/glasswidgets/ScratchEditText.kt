package com.glasswidgets

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText

/** An EditText that tells the editor where the cursor is, for the "Ln, Col" status line. */
class ScratchEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : EditText(context, attrs) {

    var onCursorMoved: (() -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onCursorMoved?.invoke()
    }
}
