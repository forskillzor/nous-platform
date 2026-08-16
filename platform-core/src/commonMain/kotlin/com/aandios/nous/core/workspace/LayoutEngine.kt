/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

/**
 * Операции над деревом LayoutNode — split, remove, replace, collect.
 * Все функции immutable: возвращают новый root, не мутируют оригинал.
 */
object LayoutEngine {

    /** Разделить панель на две (вертикально или горизонтально) */
    fun split(
        root: LayoutNode,
        targetPanelId: String,
        direction: LayoutNode.Direction,
        newPanelId: String
    ): LayoutNode {
        return transform(root) { node ->
            if (node is LayoutNode.Leaf && node.panelId == targetPanelId) {
                LayoutNode.Split(
                    direction = direction,
                    children = listOf(
                        LayoutNode.Leaf(targetPanelId),
                        LayoutNode.Leaf(newPanelId)
                    )
                )
            } else node
        }
    }

    /** Удалить панель */
    fun removePanel(root: LayoutNode, panelId: String): LayoutNode? {
        val result = transformOrNull(root) { node ->
            if (node is LayoutNode.Split) {
                val remaining = node.children.filterNot { child ->
                    child is LayoutNode.Leaf && child.panelId == panelId
                }
                when (remaining.size) {
                    0 -> null                                          // all children removed → collapse
                    1 -> remaining[0]                                  // single child → unwrap
                    else -> if (remaining.size < node.children.size)
                        LayoutNode.Split(node.direction, node.ratio, remaining)  // filtered
                    else node                                          // nothing removed → unchanged
                }
            } else node                                               // Leaf not affected → unchanged
        } ?: return null
        return if (root is LayoutNode.Leaf && root.panelId == panelId) null
        else result
    }

    /** Собрать все panelId */
    fun collectPanelIds(root: LayoutNode): List<String> {
        val ids = mutableListOf<String>()
        fun walk(node: LayoutNode) {
            when (node) {
                is LayoutNode.Leaf -> ids.add(node.panelId)
                is LayoutNode.Split -> node.children.forEach { walk(it) }
            }
        }
        walk(root)
        return ids
    }

    private fun transform(node: LayoutNode, fn: (LayoutNode) -> LayoutNode): LayoutNode {
        return when (val transformed = fn(node)) {
            node -> when (node) {
                is LayoutNode.Leaf -> transformed
                is LayoutNode.Split -> LayoutNode.Split(
                    node.direction, node.ratio,
                    node.children.map { transform(it, fn) }
                )
            }
            else -> transformed
        }
    }

    private fun transformOrNull(node: LayoutNode, fn: (LayoutNode) -> LayoutNode?): LayoutNode? {
        val transformed = fn(node)
        // fn returned a specific result — use it (could be null = "delete", or new node = "replace")
        if (transformed !== node) return transformed
        // fn returned the SAME reference = "not affected, recurse into children"
        return when (node) {
            is LayoutNode.Leaf -> node
            is LayoutNode.Split -> {
                val newChildren = node.children.mapNotNull { transformOrNull(it, fn) }
                when (newChildren.size) {
                    0 -> null
                    1 -> newChildren[0]
                    else -> if (newChildren == node.children) node
                    else LayoutNode.Split(node.direction, node.ratio, newChildren)
                }
            }
        }
    }
}
