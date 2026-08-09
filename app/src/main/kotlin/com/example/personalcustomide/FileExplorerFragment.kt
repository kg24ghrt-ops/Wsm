package com.example.personalcustomide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileExplorerFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_file_explorer, container, false)
        recyclerView = view.findViewById(R.id.fileList)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val rootDir = requireContext().filesDir.resolve("home")
        rootDir.mkdirs() // ensure exists
        val files = rootDir.listFiles()?.toList() ?: emptyList()

        adapter = FileAdapter(files) { file ->
            // Load file into editor
            if (file.isFile) {
                EditorNative.loadFile(file.absolutePath)
                (requireActivity() as MainActivity).findViewById<EditorView>(R.id.editorView).invalidate()
            }
        }
        recyclerView.adapter = adapter
        return view
    }

    class FileAdapter(
        private val files: List<File>,
        private val onFileClick: (File) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.textView.text = file.name + (if (file.isDirectory) "/" else "")
            holder.itemView.setOnClickListener { onFileClick(file) }
        }

        override fun getItemCount(): Int = files.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(R.id.fileName)
        }
    }
}