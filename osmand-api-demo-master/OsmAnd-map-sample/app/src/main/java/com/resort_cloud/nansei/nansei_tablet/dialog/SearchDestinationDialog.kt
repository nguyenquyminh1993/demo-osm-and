package com.resort_cloud.nansei.nansei_tablet.dialog

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.resort_cloud.nansei.nansei_tablet.R
import com.resort_cloud.nansei.nansei_tablet.adapter.FacilitySearchAdapter
import com.resort_cloud.nansei.nansei_tablet.data.model.FacilityKind
import com.resort_cloud.nansei.nansei_tablet.utils.FacilityItem
import com.resort_cloud.nansei.nansei_tablet.utils.SearchHelper

/**
 * Dialog để search và chọn destination facility
 * Tương tự Flutter OpenStreetMapSelectFacilityComponent nhưng chỉ cho destination
 */
class SearchDestinationDialog : DialogFragment() {

    private var facilityKinds: List<FacilityKind> = emptyList()
    private var onFacilitySelected: ((FacilityItem.FacilityData) -> Unit)? = null
    private var currentDestinationText: String = ""

    private lateinit var etSearch: EditText
    private lateinit var btnClear: ImageView
    private lateinit var rvFacilities: RecyclerView
    private lateinit var adapter: FacilitySearchAdapter

    // Debounce handler
    private val handler = Handler(Looper.getMainLooper())
    private var updateCount = 0

    companion object {
        fun newInstance(
            facilityKinds: List<FacilityKind>,
            currentDestinationText: String = "",
            onFacilitySelected: (FacilityItem.FacilityData) -> Unit
        ): SearchDestinationDialog {
            return SearchDestinationDialog().apply {
                this.facilityKinds = facilityKinds
                this.currentDestinationText = currentDestinationText
                this.onFacilitySelected = onFacilitySelected
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenDialogStyle)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.let { window ->
            window.requestFeature(Window.FEATURE_NO_TITLE)
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_search_destination, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etSearch = view.findViewById(R.id.et_search)
        btnClear = view.findViewById(R.id.btn_clear)
        rvFacilities = view.findViewById(R.id.rv_facilities)
        val btnCancel: TextView = view.findViewById(R.id.btn_cancel)

        // Set initial text
        etSearch.setText(currentDestinationText)

        // Setup RecyclerView
        adapter = FacilitySearchAdapter { facility ->
            onFacilitySelected?.invoke(facility)
            dismiss()
        }
        rvFacilities.layoutManager = LinearLayoutManager(context)
        rvFacilities.adapter = adapter

        // Load initial data
        updateFacilityList(etSearch.text.toString())

        // Setup search text watcher với debounce (300ms như Flutter)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateCount++
                val nowCount = updateCount
                
                // Debounce 300ms
                handler.postDelayed({
                    if (nowCount == updateCount) {
                        val searchTerm = s?.toString() ?: ""
                        updateFacilityList(searchTerm)
                        updateClearButtonVisibility(searchTerm)
                    }
                }, 300)
            }
        })

        // Clear button
        btnClear.setOnClickListener {
            etSearch.setText("")
            updateFacilityList("")
            updateClearButtonVisibility("")
        }

        // Cancel button
        btnCancel.setOnClickListener {
            dismiss()
        }

        // Update clear button visibility
        updateClearButtonVisibility(etSearch.text.toString())
    }

    private fun updateFacilityList(searchTerm: String) {
        val filteredList = SearchHelper.filterFacilities(facilityKinds, searchTerm)
        adapter.submitList(filteredList)
    }

    private fun updateClearButtonVisibility(searchTerm: String) {
        btnClear.visibility = if (searchTerm.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Set full screen flags
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }
}

