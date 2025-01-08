/*
 * Copyright © 2023 Github Lzhiyong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package x.code.app.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.DocumentsContract
import android.graphics.Color
import android.graphics.drawable.InsetDrawable
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.os.Environment
import android.os.Process
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.Magnifier
import android.Manifest.permission
import android.content.pm.PackageManager
import android.provider.Settings
import android.window.OnBackInvokedDispatcher

import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.updateLayoutParams
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.ui.AppBarConfiguration
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import android.content.SharedPreferences
import androidx.preference.PreferenceManager

import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

import x.code.app.filetree.FileListLoader
import x.code.app.filetree.FileNodeGenerator
import x.code.app.filetree.FileViewBinder
import x.code.app.filetree.OnFileClickListener

import x.code.app.R
import x.code.app.databinding.ActivityEditorBinding
import x.code.app.model.BaseAdapter
import x.code.app.model.DatabaseManager
import x.code.app.model.EntityDao
import x.code.app.model.HeaderEntity
import x.code.app.model.DownloadManager
import x.code.app.model.DownloadState
import x.code.app.model.Span
import x.code.app.util.DeviceUtils
import x.code.app.util.FileUtils
import x.code.app.util.JsonUtils
import x.code.app.util.PackageUtils
import x.code.app.view.HighlightTextView
import x.code.app.view.ContentTranslatingDrawerLayout

import x.github.module.alerter.Alerter
import x.github.module.document.DocumentFile
import x.github.module.piecetable.PieceTreeTextBuffer
import x.github.module.piecetable.PieceTreeTextBufferBuilder
import x.github.module.piecetable.common.Range
import x.github.module.piecetable.common.Strings
import x.github.module.treesitter.*
import x.github.module.treeview.Tree
import x.github.module.treeview.TreeNode
import x.github.module.treeview.TreeView
import x.github.module.crash.CrashReport

import kotlin.system.measureTimeMillis

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

import java.io.File
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.lang.Runtime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class EditorActivity : BaseActivity(), OnFileClickListener {

    private lateinit var binding: ActivityEditorBinding
    // opened file
    private lateinit var openedFiles: MutableSet<DocumentFile>
    
    // search matches list
    private lateinit var searchMatches: MutableList<Range>
    // lambda for perform the search operation
    private lateinit var performSearch: (String) -> Job
    
    private var searchTextJob: Job? = null
    
    private lateinit var sharedPref: SharedPreferences
    
    
    // suffix => tree_sitter_grammar
    private val filetype = mutableMapOf<String, String>()
    // token => span
    private val spans = mutableMapOf<String, Span>()
    
    // the single thread pool dispatcher, for search operations
    private val searchThreadExecutor by lazy {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }
    
    // initial the vibrator
    private val vibrator: Vibrator by lazy {
        if(Build.VERSION.SDK_INT >= 31) {
            val vibratorManager = getSystemService(
                Context.VIBRATOR_MANAGER_SERVICE
            ) as VibratorManager
        
            vibratorManager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    
    // log tag
    private val LOG_TAG = this::class.simpleName
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        
        sharedPref = getPreferences(Context.MODE_PRIVATE)
        
        val toggle = ActionBarDrawerToggle(
            this@EditorActivity,
            binding.drawerLayout,
            binding.toolbar,
            R.string.app_name,
            R.string.app_name
        )
        
        binding.drawerLayout.apply {
            addDrawerListener(toggle)
            toggle.syncState() 
            childId = binding.contentMain.id
            translationBehaviorStart = ContentTranslatingDrawerLayout.TranslationBehavior.FULL
            translationBehaviorEnd = ContentTranslatingDrawerLayout.TranslationBehavior.FULL
            setScrimColor(Color.TRANSPARENT)
        }

        binding.editor.apply {            
            //setTypeface(resources.getFont(R.font.jetbrains_mono_regular))            
            //post { setWordwrap(true) }            
            if (getUiMode() == Configuration.UI_MODE_NIGHT_YES) {
                setPaintColor(Color.LTGRAY)
            } else if (getUiMode() == Configuration.UI_MODE_NIGHT_NO) {
                setPaintColor(Color.DKGRAY)
            } else {
                setPaintColor(Color.GRAY)
            }
        }
        
        binding.progressBar.setVisibility(View.GONE)
        if (PackageUtils.checkStoragePermission(this)) {
            createFileTree(Environment.getExternalStorageDirectory().absolutePath)
        }
        
        openedFiles = mutableSetOf<DocumentFile>()
        
        addShortcutKey(
            mutableListOf("+", "-", "*", "/", "=", "<", ">", "|", "(", ")", "{", "}", "$")
        )

        flowSubscribers()
        //showSearchBottonSheet()
        binding.apply {
            shortcutKeyView.post {
                // set the bottom margin for shortcut key
                editor.setMarginLayout(
                    bottom = shortcutKeyView.getHeight()
                )
            }
        }
        
        window.decorView.post {
            checkTreeSitter()
            // parser file type
            detectFileType(resources.assets.open("filetypes.json"))
            if(intent.action == Intent.ACTION_VIEW) {
                intent.data?.let { openExtraUri(it) }
            }   
        }       
    }
    
    private fun createFileTree(path: String) {
        val fileListLoader = FileListLoader()
        val filetree = Tree.createTree<File>().apply {
            generator = FileNodeGenerator(File(path), fileListLoader)
            initTree()
        }
        @Suppress("UNCHECKED_CAST")
        (binding.treeview as TreeView<File>).apply {
            supportHorizontalScroll = false
            bindCoroutineScope(lifecycleScope)
            tree = filetree
            binder = FileViewBinder(this@EditorActivity, this@EditorActivity)
            nodeEventListener = binder as FileViewBinder
            selectionMode = TreeView.SelectionMode.MULTIPLE_WITH_CHILDREN
        }
        
        lifecycleScope.launch {
            fileListLoader.loadFileList(path)
            binding.treeview.refresh()
        }
    }
    
    // parse the filetypes.json
    // get the file type by suffix
    @Throws(SerializationException::class)
    fun detectFileType(input: InputStream) {
        JsonUtils.parse(input) { _, element ->
            element.jsonObject?.forEach { entry ->
                // Map.Entry<String, JsonElement>           
                entry.value.jsonArray?.forEach {
                    filetype.put(it.jsonPrimitive.content, entry.key)
                }                
            }
        }
    }
    
    @Throws(SerializationException::class)
    fun tokenize(input: InputStream) {
        JsonUtils.parse(input) { _, element ->
            element.jsonObject["highlights"]?.jsonObject?.forEach {                
                if (it.value is JsonObject) {
                    val span = Span(null, null)
                    it.value.jsonObject["fg"]?.let {
                        // foreground color
                        span.fg = Color.parseColor(it.jsonPrimitive.content)
                    }
               
                    it.value.jsonObject["bg"]?.let {
                        // background color
                        span.bg = Color.parseColor(it.jsonPrimitive.content)
                    }
               
                    it.value.jsonObject["bold"]?.let {
                        // bold style
                        span.bold = it.jsonPrimitive.boolean
                    }
               
                    it.value.jsonObject["italic"]?.let {
                        // italic style
                        span.italic = it.jsonPrimitive.boolean
                    }
               
                    it.value.jsonObject["strikethrough"]?.let {
                        // strikethrough style
                        span.strikethrough = it.jsonPrimitive.boolean
                    }
               
                    it.value.jsonObject["underline"]?.let {
                        // underline style
                        span.underline = it.jsonPrimitive.boolean
                    }
                    // add span
                    spans.put(it.key, span)  
                } else if (it.value !is JsonNull) {
                    // foreground color and add span
                    spans.put(it.key, Span(Color.parseColor(it.value.jsonPrimitive.content)))  
                }                     
            }
        }
    }
    
    override fun onBackKeyPressed() {
        with(binding.drawerLayout) {
            if(isDrawerOpen(GravityCompat.START)) {
                closeDrawer(GravityCompat.START)
            } else if(isDrawerOpen(GravityCompat.END)) {
                closeDrawer(GravityCompat.END)
            } else {
                showDialog(
                    dialogTitle = getString(R.string.app_name),
                    dialogMessage = getString(R.string.app_exit_prompt),                   
                    positiveText = getString(android.R.string.ok),
                    negativeText = getString(android.R.string.cancel),
                    positiveCallback = { 
                        this@EditorActivity.finishAffinity()
                    }
                )
            }
        }
    }
    
    fun loadTreeSitter(name: String = "dark") {
        // load the tree-sitter grammar libraries
        File(getFilesDir(), "lib").listFiles()?.forEach {
            System.load(it.absolutePath)
        }
        
        // parse the theme file
        File(getFilesDir(), "themes/${name}.json").also {
            if (it.exists()) {
                tokenize(it.inputStream())
            }
        }
    }
    
    fun checkTreeSitter() = lifecycleScope.launch {         
        // load the tree-sitter
        loadTreeSitter()       
        // check the network state
        if (DownloadManager.isConnected(this@EditorActivity)) {
            // database for save the download links
            val database = DatabaseManager.getInstance(this@EditorActivity).entityDao()
            val baseUrl = getString(R.string.download_base_url)
            val versionName = PackageUtils.getVersionName(this@EditorActivity)
            val archName = DeviceUtils.getArchName()
            
            // the pair first is outputFile, pair second is extractFile
            val links = mutableMapOf(
                "$baseUrl/v$versionName/tree-sitter-$archName.zip" to 
                Pair(File(getCacheDir(), "tree-sitter-$archName.zip"), File(getFilesDir(), "lib")), 
                "$baseUrl/v$versionName/tree-sitter-queries.zip" to 
                Pair(File(getCacheDir(), "tree-sitter-queries.zip"), File(getFilesDir(), "queries")),
                "$baseUrl/v$versionName/tree-sitter-themes.zip" to 
                Pair(File(getCacheDir(), "tree-sitter-themes.zip"), File(getFilesDir(), "themes"))
            )

            // check need download
            val needDownloadLinks = links.filter {
                database.query(it.key) == null
            }
                        
            // check if need download
            if (needDownloadLinks.isEmpty()) {
                val needUpdateLinks = links.filter {
                    val headerEntity = database.query(it.key)
                    headerEntity != null &&
                    DownloadManager.validate(it.key, headerEntity.etag)
                }
                
                // check if need update
                if (needUpdateLinks.size > 0) {                    
                    showAlerter(
                        contentText = getString(R.string.tree_sitter_update),
                        onCancelCallback = {
                            lifecycleScope.launch {
                                coroutineContext[Job]?.parent?.cancel()
                            }
                        },
                        onOkCallback = { alerter ->
                            val downloadJob = downloadTreeSitter(
                                needUpdateLinks, 
                                alerter, 
                                database
                            )
                            lifecycleScope.launch {
                                downloadJob.join()
                                loadTreeSitter()
                                alerter.dismiss()
                                // cancel the parent coroutine
                                coroutineContext[Job]?.parent?.cancel()
                            }
                        }          
                    )
                    // waiting for the parent coroutine to cancel and exit
                    try { awaitCancellation() } finally { /* TODO */ }              
                }
            } else {
                val alerter = showAlerter(
                    titleText = getString(R.string.download),
                    contentText = needDownloadLinks.keys.first()
                )
                downloadTreeSitter(needDownloadLinks, alerter, database).apply {
                    // waiting for the download task to complete
                    this.join()
                    loadTreeSitter()
                    alerter.dismiss()
                }
            }
        }
    }
    
    fun downloadTreeSitter(
        downloadLinks: Map<String, Pair<File, File>>,
        alerter: Alerter,
        database: EntityDao
    ) = lifecycleScope.launch(Dispatchers.IO) {
        downloadLinks.forEach {
            // download...
            DownloadManager.downloadFile(
                it.key,
                it.value.first,
                onProgress = { progress ->
                    // show progress
                    alerter.setTitle("${getString(R.string.download)}")
                    alerter.setText("${it.key}...($progress%)")
                },
                onComplete = { file, etag ->
                    // extract file
                    FileUtils.unzipFile(file, it.value.second)

                    val headerEntity = database.query(it.key)
                    if (headerEntity != null) {
                        // delete old HeaderEntity for current url
                        database.delete(headerEntity)
                    }
                    // add new HeaderEntity
                    database.add(HeaderEntity(it.key, etag))
                    // delete the cache zip file
                    file.delete()
                },
                onError = { error ->                    
                    alerter.setIcon(R.drawable.ic_error_outline)
                    alerter.setTitle(
                        "${getString(R.string.download)}${getString(R.string.fail)}"
                    )
                    alerter.setText(error)
                }
            )
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // requires to set the new intent
        setIntent(intent)
        // receive the intent from external app
        if(intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { openExtraUri(it) }
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val uiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        //
        when (uiMode) {
            Configuration.UI_MODE_NIGHT_NO -> {
                // Night mode is not active, we're using the light theme
                Log.i(LOG_TAG, "light theme")
            }
            Configuration.UI_MODE_NIGHT_YES -> {
                // Night mode is active, we're using dark theme
                Log.i(LOG_TAG, "night theme")
            }
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        //outState.putInt("theme", myTheme)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        //myTheme = savedInstanceState.getInt("theme")
    }
    
    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        // show menu icons
        (menu as? MenuBuilder)?.setOptionalIconsVisible(true)
        return super.onMenuOpened(featureId, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // set undo menu icon
        menu.findItem(R.id.action_undo).apply {
            setEnabled(mainViewModel.stateUndo.value)
        }

        // set redo menu icon
        menu.findItem(R.id.action_redo).apply {
            setEnabled(mainViewModel.stateRedo.value)
        }

        // set edit mode menu icon
        menu.findItem(R.id.action_edit_mode).apply {
            when (binding.editor.isEditable()) {
                true -> setIcon(R.drawable.ic_read_write)
                else -> setIcon(R.drawable.ic_read_only)
            }
        }

        // set save menu icon state
        menu.findItem(R.id.action_save_file).apply { 
            setEnabled(mainViewModel.stateTextChanged.value) 
        }

        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_undo -> binding.editor.undo()
            R.id.action_redo -> binding.editor.redo()
            R.id.action_edit_mode -> {            
                with(binding.editor) {
                    // toggle the read-only and write mode
                    setEditable(!isEditable())
                }
                invalidateOptionsMenu()
            }
            R.id.action_open_file -> {
                openDocumentManager()
            }
            R.id.action_search -> {
                
            }
            R.id.action_save_file -> {
                saveFile(openedFiles.elementAt(0))
            }
            R.id.action_tree_sitter -> {
                                                           
            }
        }

        return true
    }
    
    fun openDocumentManager() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
                
        startActivityForResult(intent) { result ->
            result.data?.let { uri ->
                // TODO
                // do something for the uri
            }
        }
    }
    
    fun addShortcutKey(symbolTable: MutableList<String>) {
        val baseAdapter = BaseAdapter(symbolTable, R.layout.item_shortcut_key)
        
        baseAdapter.onBindView = { holder, datas, position ->
            with(holder.getView<TextView>(R.id.shortcut_text_view)) {
                setText(datas[position] as String)
            }
        }
        
        baseAdapter.onItemClick = { _, datas, position ->
            val effect = VibrationEffect.createPredefined(
                VibrationEffect.EFFECT_CLICK
            )
            vibrator.vibrate(effect)
            binding.editor.insert(datas[position] as String)
        }
        
        baseAdapter.onItemLongClick = { _, datas, position ->
            binding.editor.insert(datas[position] as String)
        }
        
        // shortcut key recycler view
        binding.shortcutKeyView.apply {
            layoutManager = LinearLayoutManager(this@EditorActivity).apply {
                setOrientation(LinearLayoutManager.HORIZONTAL)
            }
            // recyclerview adapter
            adapter = baseAdapter
        }
    }      

    fun popupFileActionMenu(anchorView: View) {
        val popupMenu = PopupMenu(this@EditorActivity, anchorView).apply {
            menuInflater.inflate(R.menu.file_action_menu, menu)
        }
        
        val iconMarginPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            0.toFloat(), 
            resources.displayMetrics
        ).toInt()
        
        val menuBuilder = popupMenu as MenuBuilder
        // show menu icon
        menuBuilder.setOptionalIconsVisible(true)
        menuBuilder.visibleItems.forEach { item ->
            item.icon?.let { icon ->
                // Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP
                item.icon = InsetDrawable(icon, iconMarginPx, 0, iconMarginPx, 0)
            }
        }
        
        popupMenu.setOnMenuItemClickListener { menuItem ->
            // menuItem click callback
            when (menuItem.itemId) {
            
            }
            return@setOnMenuItemClickListener true
        }
    }

    // stateFlow subscriber
    fun flowSubscribers() {
        lifecycleScope.launch {
            mainViewModel.stateUndo.collect {
                // when undo state changed refresh the UI
                invalidateOptionsMenu()
            }
        }

        lifecycleScope.launch {
            mainViewModel.stateRedo.collect {
                // when redo state changed refresh the UI
                invalidateOptionsMenu()
            }
        }

        lifecycleScope.launch {
            mainViewModel.stateTextChanged.collect {
                // when text changed refresh the UI
                invalidateOptionsMenu()
            }
        }
        
        lifecycleScope.launch {
            mainViewModel.stateTextScaled.collect { value ->
                // when text changed refresh the UI
                when (value) {
                    true -> refreshComponentState(View.GONE)
                    else -> refreshComponentState(View.VISIBLE)
                }
            }
        }
    }
    
    /**
     * Get the tree-sitter language
     *
     * @name file name like `hello.c`, `test.cpp`, `foo.kt` etc
     * @return TSLanguage
     */
    fun getTreesitterLanguage(filename: String): TSLanguage? {
        val index = filename.lastIndexOf(".")
        return if (index >= 0) {
            // file extension name
            val extension = filename.substring(index)
            // get the grammar name via the file extension name
            // like `tree_sitter_c`, `tree_sitter_cpp` etc
            val name = filetype[extension]
            if (name != null) TSLanguage(name) else null
        } else {
            // get the grammar name via the file full name
            val name = filetype[filename]
            if (name != null) TSLanguage(name) else null
        }
    }
    
    /**
     * Get the tree-sitter query s-expression pattern
     *
     * @name grammar name like `c`, `cpp`, `kotlin` etc
     * @return s-expression pattern
     */
    fun getQueryPattern(name: String): String? {
        var pattern: String? = null
        val queryFile = File(getFilesDir(), "queries/${name}/highlights.scm")
        if (queryFile.exists()) {
            // super class pattern, like c++ inherits c
            val source = queryFile.readText()
            Regex("; inherits: (\\w+)").find(source)?.let {
                val name = it.value.substring(it.value.indexOf(":") + 1).trim()
                val parentFile = File(getFilesDir(), "queries/${name}/highlights.scm")
                if (parentFile.exists()) {
                    // (super + current) s-expression pattern
                    pattern = parentFile.readText() + source
                }
            } ?: run {
                // the s-expression query pattern        
                pattern = source
            }
        }
        return pattern
    }
    
    // received intent content uri from external
    fun openExtraUri(uri: Uri) {
        if(!TextUtils.isEmpty(uri.scheme)) {
            if(uri.scheme == "content") {
                DocumentFile.fromSingleUri(this, uri)?.let {
                    openFile(it)
                }
            }
        }
    }
    
    override fun onFileClick(file: File) {
        DocumentFile.fromFile(this, file)?.let { document ->
            openFile(document)
        }
    }
    
    override fun onFileLongClick(file: File) {
        DocumentFile.fromFile(this, file)?.let { document ->
            // TODO
        }
    }
    
    private fun openFile(document: DocumentFile) {
        with(binding.drawerLayout) {
            if(isDrawerOpen(GravityCompat.START)) {
                closeDrawer(GravityCompat.START)
            }
        }
        // update the UI state
        binding.editor.setEditable(false)
        binding.progressBar.setVisibility(View.VISIBLE)
        invalidateOptionsMenu()
        // add the opened document
        openedFiles.add(document)
         
        lifecycleScope.launch(Dispatchers.IO) {                              
            val language = getTreesitterLanguage(document.getName())
            val pattern = language?.run {
               getQueryPattern(this.getName())
            }
            with(binding.editor) {
                setBuffer(readFile(document))                      
                if(language != null && pattern != null) {
                    treeSitterConfig(language, pattern, spans, true)
                }
            }                     
                
            // update the UI on main thread
            withContext(Dispatchers.Main) {
                // the document read finished
                binding.progressBar.setVisibility(View.GONE)
                binding.editor.updateDisplayList()
                binding.editor.setEditable(true)
                invalidateOptionsMenu()
            }
        }
    }
    
    // should be running on background thread
    suspend fun readFile(
        document: DocumentFile,
        encoding: String = "UTF-8"
    ): PieceTreeTextBuffer {
        val pieceBuilder = PieceTreeTextBufferBuilder()
        contentResolver.openInputStream(document.getUri())?.use {
            BufferedReader(InputStreamReader(it, encoding)).run {
                // 64k size per read
                val buffer = CharArray(1024 * 64)
                var len = 0
                while ({ len = read(buffer, 0, buffer.size); len }() > 0) {
                    pieceBuilder.acceptChunk(String(buffer, 0, len))
                }
            }
        }
        return pieceBuilder.build()
    }

    fun saveFile(document: DocumentFile) {
        // update the UI state
        binding.editor.setEditable(false)
        binding.progressBar.setVisibility(View.VISIBLE)
        invalidateOptionsMenu()
        // running on background thread
        lifecycleScope.launch(Dispatchers.IO) {
            writeFile(document, binding.editor.getTextBuffer())
            // running on main thread
            withContext(Dispatchers.Main) { // write file finished
                // the document read finished
                binding.progressBar.setVisibility(View.GONE)
                binding.editor.setEditable(true)
                invalidateOptionsMenu()
                mainViewModel.setTextChangedState(false)                
            }
        }
    }
    
    suspend fun writeFile(
        document: DocumentFile,
        textBuffer: PieceTreeTextBuffer,
        encoding: String = "UTF-8"
    ) {
        contentResolver.openOutputStream(document.getUri())?.use {
            BufferedWriter(OutputStreamWriter(it, encoding)).run {
                with(textBuffer) {
                    for (line in 1 until getLineCount()) {
                        // contains line feed \n
                        write(getLineContentWithEOL(line))
                    }
                    // the lastest line, not contains the line feed \n
                    write(getLineContent(getLineCount()))
                }
                // flush the IO cache
                flush()
            }
        }
    }

    fun refreshComponentState(state: Int) {
        // running on main thread
        when (state) {
            View.VISIBLE -> binding.editor.setEditable(false)
            else -> binding.editor.setEditable(true)
        }
        binding.progressBar.setVisibility(state)
        invalidateOptionsMenu()
    }

    override fun onDestroy() {
        super.onDestroy()
        this.searchThreadExecutor.close()
        binding.editor.recycleRenderNode()
    }
    
}
