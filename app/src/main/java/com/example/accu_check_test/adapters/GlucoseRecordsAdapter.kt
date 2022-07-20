package com.example.accu_check_test.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.accu_check_test.data.GlucoseRecord
import com.example.accu_check_test.R

class GlucoseRecordsAdapter(
  private val dataSet: MutableList<GlucoseRecord>,
) : RecyclerView.Adapter<GlucoseRecordsAdapter.ViewHolder>() {

  fun addToList(newData: GlucoseRecord) {
    if (dataSet.any {
        it.seqNumber == newData.seqNumber && it.value == newData.value
      }) {
      Log.e("GlucoseRecordsAdapter", "Duplicate new data -> Skipped")
      return
    }
    dataSet.add(newData)
    notifyDataSetChanged()
  }
  fun clearList() {
    dataSet.clear()
    notifyDataSetChanged()
  }

  inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val tvCount: TextView = view.findViewById(R.id.tvItemCount)
    val tvNum: TextView = view.findViewById(R.id.tvItemNumber)
    val tvValue: TextView = view.findViewById(R.id.tvItemValue)
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): GlucoseRecordsAdapter.ViewHolder {
    // Create a new view, which defines the UI of the list item
    val view = LayoutInflater.from(parent.context)
      .inflate(R.layout.list_item_glucose_record, parent, false)

    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: GlucoseRecordsAdapter.ViewHolder, position: Int) {
    // Get element from your dataset at this position and replace the
    // contents of the view with that element
    holder.tvCount.text = "Index : ${position + 1}"
    holder.tvNum.text = "Sequence No. : ${dataSet[position].seqNumber}"
    holder.tvValue.text = "Glucose : ${dataSet[position].value} mg/dL"
  }

  override fun getItemCount(): Int {
    return dataSet.size
  }

}