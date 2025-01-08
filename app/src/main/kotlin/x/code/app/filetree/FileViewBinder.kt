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

package x.code.app.filetree

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Space
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isInvisible
import androidx.core.view.updateLayoutParams

import x.code.app.databinding.ItemDirBinding
import x.code.app.databinding.ItemFileBinding
import x.code.app.R

import x.github.module.treeview.TreeNode
import x.github.module.treeview.TreeNodeEventListener
import x.github.module.treeview.TreeView
import x.github.module.treeview.TreeViewBinder

import java.io.File

class FileViewBinder(
    private val context: Context,
    private val listener: OnFileClickListener
) : TreeViewBinder<File>(), TreeNodeEventListener<File> {
    
    private val Int.dp: Int 
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            this.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    
    override fun createView(
        parent: ViewGroup,
        viewType: Int
    ): View {
        val layoutInflater = LayoutInflater.from(parent.context)
        return if (viewType == 1) {
            ItemDirBinding.inflate(layoutInflater, parent, false).root
        } else {
            ItemFileBinding.inflate(layoutInflater, parent, false).root
        }
    }

    override fun getItemViewType(node: TreeNode<File>): Int {
        if (node.isChild) {
            return 1
        }
        return 0
    }

    override fun bindView(
        holder: TreeView.ViewHolder,
        node: TreeNode<File>,
        listener: TreeNodeEventListener<File>
    ) {
        if (node.isChild) {
            applyDir(holder, node)
        } else {
            applyFile(holder, node)
        }

        val itemView = holder.itemView.findViewById<Space>(R.id.space)

        itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            width = node.depth * 22.dp
        }
    }

    private fun applyFile(
        holder: TreeView.ViewHolder,
        node: TreeNode<File>
    ) {
        val binding = ItemFileBinding.bind(holder.itemView)
        binding.tvName.text = node.name.toString()
    }

    private fun applyDir(
        holder: TreeView.ViewHolder,
        node: TreeNode<File>
    ) {
        val binding = ItemDirBinding.bind(holder.itemView)
        binding.tvName.text = node.name.toString()

        binding
            .ivArrow
            .animate()
            .rotation(if (node.expand) 90f else 0f)
            .setDuration(200)
            .start()
    }

    override fun onClick(
        node: TreeNode<File>,
        holder: TreeView.ViewHolder,
    ) {
        if (node.isChild) {
            applyDir(holder, node)
        } else {
            listener.onFileClick(node.requireData())
        }
    }
    
    override fun onLongClick(
        node: TreeNode<File>,
        holder: TreeView.ViewHolder,
    ): Boolean {
        listener.onFileLongClick(node.requireData())
        return true
    }

    override fun onRefresh(status: Boolean) {
        // TODO
    }

    override fun onToggle(
        node: TreeNode<File>,
        isExpand: Boolean,
        holder: TreeView.ViewHolder,
    ) {
        applyDir(holder, node)
    }
}
