package com.example.personalcustomide

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.IOException

class FileExplorerFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter
    private lateinit var fab: FloatingActionButton

    private var rootDir: File? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_file_explorer, container, false)
        recyclerView = view.findViewById(R.id.fileList)
        fab = view.findViewById(R.id.fabCreateFile)
        recyclerView.layoutManager = LinearLayoutManager(context)

        rootDir = requireContext().filesDir.resolve("home")
        try {
            rootDir?.mkdirs()
        } catch (e: SecurityException) {
            Toast.makeText(context, "Cannot create directory: ${e.message}", Toast.LENGTH_LONG).show()
        }

        refreshFileList()

        fab.setOnClickListener {
            showCreateFileDialog()
        }

        return view
    }

    private fun refreshFileList() {
        val root = rootDir ?: return
        val files = try {
            root.listFiles()?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Toast.makeText(context, "Cannot read directory: ${e.message}", Toast.LENGTH_SHORT).show()
            emptyList()
        }
        adapter = FileAdapter(files) { file ->
            if (file.isFile) {
                val success = EditorNative.loadFile(file.absolutePath)
                if (success) {
                    (activity as? MainActivity)?.updateEditor()
                } else {
                    Toast.makeText(context, "Failed to load ${file.name}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Directory: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }
        // Set adapter only if recyclerView is attached
        if (::recyclerView.isInitialized) {
            recyclerView.adapter = adapter
        }
    }

    private fun showCreateFileDialog() {
        val input = EditText(context)
        input.hint = "Enter file name (e.g., main.py)"
        AlertDialog.Builder(context)
            .setTitle("Create New File")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val fileName = input.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    val file = rootDir?.resolve(fileName)
                    if (file != null) {
                        try {
                            if (file.createNewFile()) {
                                Toast.makeText(context, "Created $fileName", Toast.LENGTH_SHORT).show()
                                refreshFileList()
                            } else {
                                Toast.makeText(context, "File already exists", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: IOException) {
                            Toast.makeText(context, "Failed to create file: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun refresh() = refreshFileList()

    inner class FileAdapter(
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
            holder.itemView.setOnLongClickListener {
                showDeleteDialog(file)
                true
            }
        }

        override fun getItemCount(): Int = files.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(R.id.fileName)
        }

        private fun showDeleteDialog(file: File) {
            AlertDialog.Builder(context)
                .setTitle("Delete ${file.name}?")
                .setMessage("This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    try {
                        if (file.delete()) {
                            Toast.makeText(context, "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
                            refreshFileList()
                        } else {
                            Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: SecurityException) {
                        Toast.makeText(context, "Cannot delete: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}