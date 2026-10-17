package com.mycodex.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.TextViewCompat
import kotlin.math.roundToInt

internal data class NativeAccessibleAction(
    val id: Int,
    val label: String,
    val onAction: () -> Boolean,
)

private class SendActionEditText(context: Context) : EditText(context) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs)
        outAttrs.imeOptions =
            (outAttrs.imeOptions and EditorInfo.IME_MASK_ACTION.inv() and
                EditorInfo.IME_FLAG_NO_ENTER_ACTION.inv()) or EditorInfo.IME_ACTION_SEND
        return connection
    }
}

@Composable
internal fun NativeAccessibleActionRow(
    title: String,
    body: String,
    testTag: String,
    restoreFocus: Boolean,
    onFocusRestored: () -> Unit,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    accessibilityActions: List<NativeAccessibleAction> = emptyList(),
    modifier: Modifier = Modifier,
    contentDescription: String = "$title，$body",
    bodyMaxLines: Int? = null,
    footerText: String? = null,
    restoreFocusGeneration: Long? = null,
    titleTextColor: Color? = null,
    bodyTextColor: Color? = null,
    footerTextColor: Color? = null,
) {
    val titleColor = (titleTextColor ?: MaterialTheme.colorScheme.onSurface).toArgb()
    val bodyColor = (bodyTextColor ?: MaterialTheme.colorScheme.onSurfaceVariant).toArgb()
    val primaryColor = (footerTextColor ?: MaterialTheme.colorScheme.primary).toArgb()
    val disabledColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val rippleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f).toArgb()

    AndroidView(
        modifier = modifier.testTag(testTag),
        factory = { context ->
            LinearLayout(context).apply {
                tag = testTag
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                setPadding(dp(16), dp(12), dp(16), dp(12))
                minimumHeight = dp(48)
                foreground = selectableItemBackground()?.mutate()

                addView(TextView(context).apply {
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    typeface = semiboldTypeface()
                    textSize = 16f
                    includeFontPadding = false
                    setTextLineHeight(24)
                })
                addView(TextView(context).apply {
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    textSize = 14f
                    includeFontPadding = false
                    setTextLineHeight(20)
                })
                addView(TextView(context).apply {
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    textSize = 12f
                    includeFontPadding = false
                    setTextLineHeight(16)
                })
            }
        },
        update = { row ->
            row.tag = testTag
            val titleView = row.getChildAt(0) as TextView
            val bodyView = row.getChildAt(1) as TextView
            val footerView = row.getChildAt(2) as TextView
            titleView.text = title
            titleView.setTextColor(if (enabled) titleColor else disabledColor)
            bodyView.text = body
            bodyView.setTextColor(bodyColor)
            if (bodyMaxLines == null) {
                bodyView.maxLines = Int.MAX_VALUE
                bodyView.ellipsize = null
            } else {
                bodyView.maxLines = bodyMaxLines
                bodyView.ellipsize = TextUtils.TruncateAt.END
            }
            bodyView.visibility = if (body.isBlank()) View.GONE else View.VISIBLE
            footerView.text = footerText.orEmpty()
            footerView.setTextColor(if (enabled) primaryColor else disabledColor)
            footerView.visibility = if (footerText.isNullOrBlank()) View.GONE else View.VISIBLE
            row.contentDescription = contentDescription
            row.isEnabled = enabled
            row.setOnClickListener(
                if (enabled && onClick != null) View.OnClickListener { onClick() } else null,
            )
            row.setOnLongClickListener(
                if (enabled) onLongClick?.let { callback ->
                    View.OnLongClickListener {
                        callback()
                        true
                    }
                } else null,
            )
            row.setOnKeyListener(
                if (enabled && onClick != null) {
                    View.OnKeyListener { view, keyCode, event ->
                        if (keyCode != KeyEvent.KEYCODE_ENTER && keyCode != KeyEvent.KEYCODE_SPACE) {
                            return@OnKeyListener false
                        }
                        if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) {
                            view.performClick()
                        }
                        true
                    }
                } else {
                    null
                },
            )
            row.isClickable = enabled && onClick != null
            row.isLongClickable = enabled && onLongClick != null
            (row.foreground as? RippleDrawable)?.setColor(ColorStateList.valueOf(rippleColor))
            val activeAccessibilityActions = if (enabled) accessibilityActions else emptyList()
            row.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    activeAccessibilityActions.forEach { action ->
                        info.addAction(
                            AccessibilityNodeInfo.AccessibilityAction(
                                action.id,
                                action.label,
                            ),
                        )
                    }
                }

                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: Bundle?,
                ): Boolean {
                    if (enabled) {
                        val customAction = activeAccessibilityActions.firstOrNull { it.id == action }
                        if (customAction != null) {
                            return customAction.onAction()
                        }
                    }
                    return super.performAccessibilityAction(host, action, args)
                }
            }
            if (!restoreFocus) {
                row.setTag(R.id.tag_native_focus_generation, null)
            }
            val queuedGeneration: Any = restoreFocusGeneration ?: true
            if (
                restoreFocus &&
                row.getTag(R.id.tag_native_focus_generation) != queuedGeneration
            ) {
                row.setTag(R.id.tag_native_focus_generation, queuedGeneration)
                row.post {
                    row.requestFocus()
                    row.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    )
                    onFocusRestored()
                }
            }
        },
    )
}

@Composable
internal fun NativeAccessibleTextInput(
    text: String,
    testTag: String,
    enabled: Boolean,
    label: String,
    hint: String,
    restoreFocus: Boolean,
    onFocusRestored: () -> Unit,
    onTextChange: (String) -> Unit,
    onImeSend: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = 5,
    restoreFocusGeneration: Long? = null,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val outlineColor = MaterialTheme.colorScheme.outline.toArgb()
    val disabledOutlineColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    AndroidView(
        modifier = modifier.testTag(testTag),
        factory = { context ->
            SendActionEditText(context).apply {
                tag = testTag
                isFocusable = true
                isFocusableInTouchMode = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = label
                minimumHeight = dp(48)
                setHint(hint)
                setSingleLine(false)
                this.minLines = minLines
                this.maxLines = maxLines
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_ACTION_SEND
                setHorizontallyScrolling(false)
                textSize = 16f
                includeFontPadding = false
                setTextLineHeight(24)
            }
        },
        update = { input ->
            input.tag = testTag
            val watcher = input.getTag(R.id.tag_native_text_watcher) as? TextWatcher
            if (watcher != null) {
                input.removeTextChangedListener(watcher)
            }

            if (input.text.toString() != text) {
                input.setText(text)
                input.setSelection(input.text.length)
            }

            input.isEnabled = enabled
            input.contentDescription = label
            input.hint = hint
            input.minLines = minLines
            input.maxLines = maxLines
            input.setTextColor(if (enabled) textColor else disabledTextColor)
            input.setHintTextColor(hintColor)
            input.backgroundTintList = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_focused),
                    intArrayOf(),
                ),
                intArrayOf(disabledOutlineColor, primaryColor, outlineColor),
            )
            input.setOnFocusChangeListener { _, hasFocus -> onFocusChanged(hasFocus) }
            input.setOnEditorActionListener { _, actionId, event ->
                val softKeyboardSend = actionId == EditorInfo.IME_ACTION_SEND && event == null
                val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    !event.isShiftPressed
                if (enabled && (softKeyboardSend || enterPressed)) {
                    if (event == null || event.action == KeyEvent.ACTION_DOWN) {
                        onImeSend()
                    }
                    true
                } else {
                    false
                }
            }

            val newWatcher = object : TextWatcher {
                override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                    onTextChange(value?.toString().orEmpty())
                }
                override fun afterTextChanged(value: Editable?) = Unit
            }
            input.addTextChangedListener(newWatcher)
            input.setTag(R.id.tag_native_text_watcher, newWatcher)

            if (!restoreFocus) {
                input.setTag(R.id.tag_native_focus_generation, null)
            }
            val queuedGeneration: Any = restoreFocusGeneration ?: true
            if (
                restoreFocus &&
                enabled &&
                input.getTag(R.id.tag_native_focus_generation) != queuedGeneration
            ) {
                input.setTag(R.id.tag_native_focus_generation, queuedGeneration)
                input.post {
                    input.requestFocus()
                    input.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    )
                    onFocusRestored()
                }
            }
        },
    )
}

@Composable
internal fun NativeAccessibleButton(
    text: String,
    testTag: String,
    restoreFocus: Boolean,
    onFocusRestored: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    restoreFocusGeneration: Long? = null,
) {
    val backgroundTint = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(),
        ),
        intArrayOf(
            MaterialTheme.colorScheme.surfaceContainerHighest.toArgb(),
            MaterialTheme.colorScheme.primary.toArgb(),
        ),
    )
    val textColors = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(),
        ),
        intArrayOf(
            MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
            MaterialTheme.colorScheme.onPrimary.toArgb(),
        ),
    )
    AndroidView(
        modifier = modifier.testTag(testTag),
        factory = { context ->
            Button(context).apply {
                tag = testTag
                isFocusable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                isAllCaps = false
                minimumWidth = dp(48)
                minimumHeight = dp(48)
                textSize = 14f
                includeFontPadding = false
                setTextLineHeight(20)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
        },
        update = { button ->
            button.tag = testTag
            button.text = text
            button.contentDescription = text
            button.isEnabled = enabled
            button.backgroundTintList = backgroundTint
            button.setTextColor(textColors)
            button.setOnClickListener(if (enabled) View.OnClickListener { onClick() } else null)
            button.isClickable = enabled
            if (!restoreFocus) {
                button.setTag(R.id.tag_native_focus_generation, null)
            }
            val queuedGeneration: Any = restoreFocusGeneration ?: true
            if (
                restoreFocus &&
                enabled &&
                button.getTag(R.id.tag_native_focus_generation) != queuedGeneration
            ) {
                button.setTag(R.id.tag_native_focus_generation, queuedGeneration)
                button.post {
                    button.requestFocus()
                    button.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    )
                    onFocusRestored()
                }
            }
        },
    )
}

private fun View.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

private fun TextView.setTextLineHeight(lineHeightSp: Int) {
    TextViewCompat.setLineHeight(
        this,
        (
            lineHeightSp *
                resources.displayMetrics.density *
                resources.configuration.fontScale
            ).roundToInt(),
    )
}

private fun semiboldTypeface(): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 600, false)
    } else {
        Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

private fun View.selectableItemBackground() =
    context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).let { attributes ->
        try {
            attributes.getDrawable(0)
        } finally {
            attributes.recycle()
        }
    }
