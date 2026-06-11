// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.phrase

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.keyboard.internal.KeyDrawParams
import helium314.keyboard.keyboard.internal.KeyVisualAttributes
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.event.HapticEvent

@SuppressLint("ViewConstructor")
class PhraseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyle: Int = R.attr.phraseViewStyle
) : LinearLayout(context, attrs, defStyle), View.OnClickListener {

    private var selectedCategoryIndex = 0
    private lateinit var categoryTabContainer: LinearLayout
    private lateinit var phraseRecyclerView: RecyclerView
    private lateinit var phraseAdapter: PhraseAdapter
    private var keyboardActionListener: KeyboardActionListener? = null

    private val categoryTabs = mutableListOf<TextView>()

    init {
        fitsSystemWindows = true
    }

    fun setKeyboardActionListener(listener: KeyboardActionListener) {
        keyboardActionListener = listener
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initialize() {
        if (this::phraseAdapter.isInitialized) return

        val colors = Settings.getValues().mColors

        categoryTabContainer = findViewById(R.id.phrase_category_tabs)
        phraseRecyclerView = findViewById<RecyclerView>(R.id.phrase_list).apply {
            layoutManager = LinearLayoutManager(context)
        }

        // Create category tabs
        PhraseData.categories.forEachIndexed { index, category ->
            val tab = LayoutInflater.from(context).inflate(R.layout.phrase_category_tab, this, false) as TextView
            tab.text = category
            tab.tag = index
            tab.setOnClickListener(this@PhraseView)
            tab.isSelected = (index == 0)
            tab.setTextColor(colors.get(ColorType.KEY_TEXT))
            categoryTabs.add(tab)
            categoryTabContainer.addView(tab)
        }

        phraseAdapter = PhraseAdapter { phrase ->
            keyboardActionListener?.onTextInput(phrase.text)
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                KeyCode.NOT_SPECIFIED, this@PhraseView, HapticEvent.KEY_PRESS
            )
        }
        phraseRecyclerView.adapter = phraseAdapter
        phraseAdapter.submitList(PhraseData.getPhrasesByCategory(PhraseData.categories[0]))
    }

    private fun setupBottomRowKeyboard(editorInfo: EditorInfo, listener: KeyboardActionListener?) {
        val keyboardView = findViewById<MainKeyboardView>(R.id.phrase_bottom_row_keyboard)
        if (listener != null) {
            keyboardView.setKeyboardActionListener(listener)
        }
        PointerTracker.switchTo(keyboardView)
        val kls = KeyboardLayoutSet.Builder.buildEmojiClipBottomRow(context, editorInfo)
        val keyboard = kls.getKeyboard(KeyboardId.ELEMENT_CLIPBOARD_BOTTOM_ROW)
        keyboardView.setKeyboard(keyboard)
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (!enabled) return
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun startPhraseView(
        keyVisualAttr: KeyVisualAttributes?,
        editorInfo: EditorInfo,
        listener: KeyboardActionListener
    ) {
        keyboardActionListener = listener
        initialize()

        val params = KeyDrawParams()
        val res = context.resources
        val sv = Settings.getValues()
        val defaultKeyboardHeight = ResourceUtils.getSecondaryKeyboardHeight(res, sv)
        val keyVerticalGap = (res.getFraction(R.fraction.config_key_vertical_gap_holo,
            defaultKeyboardHeight, defaultKeyboardHeight) * sv.mKeyGapScale).toInt()
        val bottomPadding = (res.getFraction(R.fraction.config_keyboard_bottom_padding_holo,
            defaultKeyboardHeight, defaultKeyboardHeight) * sv.mBottomPaddingScale).toInt()
        val topPadding = res.getFraction(R.fraction.config_keyboard_top_padding_holo,
            defaultKeyboardHeight, defaultKeyboardHeight).toInt()
        val rowCount = KeyboardParams.DEFAULT_KEYBOARD_ROWS + if (sv.mShowsNumberRow) 1 else 0
        val bottomRowKeyboardHeight = (defaultKeyboardHeight - bottomPadding - topPadding) / rowCount - keyVerticalGap / 2

        params.updateParams(bottomRowKeyboardHeight, keyVisualAttr)
        setupBottomRowKeyboard(editorInfo, keyboardActionListener)
    }

    fun stopPhraseView() {
        if (!this::phraseAdapter.isInitialized) return
        phraseRecyclerView.adapter = null
    }

    override fun onClick(view: View) {
        val index = view.tag as? Int ?: return
        if (index == selectedCategoryIndex) return

        // Update tab selection
        categoryTabs[selectedCategoryIndex].isSelected = false
        categoryTabs[index].isSelected = true
        selectedCategoryIndex = index

        // Update phrase list
        val category = PhraseData.categories[index]
        phraseAdapter.submitList(PhraseData.getPhrasesByCategory(category))
        phraseRecyclerView.scrollToPosition(0)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val res = context.resources
        val width = ResourceUtils.getKeyboardWidth(context, Settings.getValues()) + paddingLeft + paddingRight
        val height = ResourceUtils.getSecondaryKeyboardHeight(res, Settings.getValues()) + paddingTop + paddingBottom
        setMeasuredDimension(width, height)
    }
}

class PhraseAdapter(
    private val onClick: (PhraseEntry) -> Unit
) : RecyclerView.Adapter<PhraseAdapter.ViewHolder>() {

    private var phrases: List<PhraseEntry> = emptyList()

    fun submitList(list: List<PhraseEntry>) {
        phrases = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.phrase_entry_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(phrases[position])
    }

    override fun getItemCount() = phrases.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        private val textView: TextView = view.findViewById(R.id.phrase_text)

        init {
            view.setOnClickListener(this)
        }

        fun bind(phrase: PhraseEntry) {
            textView.text = phrase.text
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }

        override fun onClick(v: View) {
            val pos = bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onClick(phrases[pos])
            }
        }
    }
}
