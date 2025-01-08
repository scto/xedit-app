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
import android.content.pm.PackageManager
import android.net.Uri
import android.Manifest.permission
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity

import x.code.app.databinding.ActivityMainBinding
import x.code.app.util.PackageUtils

class MainActivity : BaseActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // (intent.getFlags() and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0
        if (!this.isTaskRoot() && intent != null) {
            if (
                intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
                Intent.ACTION_MAIN == intent.action
            ) {
                this.finish()
                return@onCreate
            }
        }
        
        if (!PackageUtils.checkStoragePermission(this)) {
            createView(savedInstanceState)
        } else {
            startActivity(Intent(this, EditorActivity::class.java))
        }
    }
    
    fun createView(savedInstanceState: Bundle?) {       
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.versionTextView.setText("v${PackageUtils.getVersionName(this)}")
        
        binding.storageAccessButton.setOnClickListener {
            requestStoragePermission()
        }
        
        binding.goEditorButton.setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }
        
        binding.appExitButton.setOnClickListener {
            finishAffinity()
        }
    }
    
    override fun onBackKeyPressed() {
        this.finishAffinity()
    }
    
    // request read and write permission
    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT < 23) {
            return@requestStoragePermission
        }

        if (Build.VERSION.SDK_INT < 30) {
            requestPermissions(
                arrayOf(
                    permission.WRITE_EXTERNAL_STORAGE,
                    permission.READ_EXTERNAL_STORAGE
                ), 0x1
            )
        } else {            
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:x.code.app")
                )
            )
        }
    }
    
}
