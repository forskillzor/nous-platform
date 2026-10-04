/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

/**
 * Операции над деревом LayoutNode — split, remove, replace, collect, move.
 * Все функции immutable: возвращают новый root, не мутируют оригинал.
 */
object LayoutEngine {

    /** Зоны дропа при перетаскивании панели (как в IntelliJ IDEA). */
    enum class DropZone { LEFT, RIGHT, TOP, BOTTOM, CENTER }

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

    /**
     * Переместить панель [panelId] относительно [targetPanelId] в зону [zone]:
     * края — сплит целевой панели, CENTER — обмен панелей местами.
     */
    fun movePanel(
        root: LayoutNode,
        panelId: String,
        targetPanelId: String,
        zone: DropZone,
    ): LayoutNode {
        if (panelId == targetPanelId) return root

        // Защита от битой цели (например, из протухших rect'ов drag-состояния):
        // иначе removeNode вырежет панель, insertRelative не найдёт цель —
        // и панель молча исчезнет из дерева.
        val ids = collectPanelIds(root)
        if (panelId !in ids || targetPanelId !in ids) return root

        if (zone == DropZone.CENTER) {
            return swapPanels(root, panelId, targetPanelId)
        }

        val (withoutMoved, removed) = removeNode(root, panelId)
        if (!removed) return root
        val without = withoutMoved ?: return root

        return insertRelative(without, panelId, targetPanelId, zone)
    }

    /**
     * Переместить панель на корневой уровень: над/под/слева/справа от ВСЕХ панелей.
     * Края — новая ветка сплита вокруг всего дерева; панель занимает [ratio] (по умолч. половину).
     */
    fun movePanelToRoot(
        root: LayoutNode,
        panelId: String,
        zone: DropZone,
        ratio: Float = 0.5f,
    ): LayoutNode {
        if (zone == DropZone.CENTER) return root
        val (withoutMoved, removed) = removeNode(root, panelId)
        if (!removed) return root
        val rest = withoutMoved ?: return root // была единственная панель — некуда двигать

        val moved = LayoutNode.Leaf(panelId)
        return when (zone) {
            DropZone.TOP -> LayoutNode.Split(LayoutNode.Direction.VERTICAL, ratio, listOf(moved, rest))
            DropZone.BOTTOM -> LayoutNode.Split(LayoutNode.Direction.VERTICAL, 1f - ratio, listOf(rest, moved))
            DropZone.LEFT -> LayoutNode.Split(LayoutNode.Direction.HORIZONTAL, ratio, listOf(moved, rest))
            DropZone.RIGHT -> LayoutNode.Split(LayoutNode.Direction.HORIZONTAL, 1f - ratio, listOf(rest, moved))
            DropZone.CENTER -> root
        }
    }

    /** Обмен местами: [panelId] встаёт на место [targetPanelId] и наоборот. */
    private fun swapPanels(root: LayoutNode, panelId: String, targetPanelId: String): LayoutNode {
        val ids = collectPanelIds(root)
        if (panelId !in ids || targetPanelId !in ids) return root
        val marker = "__swap_marker__"
        var tree = replaceLeaf(root, panelId, LayoutNode.Leaf(marker))
        tree = replaceLeaf(tree, targetPanelId, LayoutNode.Leaf(panelId))
        tree = replaceLeaf(tree, marker, LayoutNode.Leaf(targetPanelId))
        return tree
    }

    private fun replaceLeaf(node: LayoutNode, panelId: String, replacement: LayoutNode.Leaf): LayoutNode =
        when (node) {
            is LayoutNode.Leaf -> if (node.panelId == panelId) replacement else node
            is LayoutNode.Split -> node.copy(children = node.children.map { replaceLeaf(it, panelId, replacement) })
        }

    private fun removeNode(node: LayoutNode, panelId: String): Pair<LayoutNode?, Boolean> {
        return when (node) {
            is LayoutNode.Leaf ->
                if (node.panelId == panelId) null to true else node to false
            is LayoutNode.Split -> {
                var removed = false
                val children = node.children.mapNotNull { child ->
                    val (newChild, wasRemoved) = removeNode(child, panelId)
                    if (wasRemoved) removed = true
                    newChild
                }
                val result = when (children.size) {
                    0 -> null
                    1 -> children[0]
                    else -> node.copy(children = children)
                }
                result to removed
            }
        }
    }

    private fun insertRelative(
        node: LayoutNode,
        panelId: String,
        targetPanelId: String,
        zone: DropZone,
    ): LayoutNode {
        return when (node) {
            is LayoutNode.Leaf -> {
                if (node.panelId == targetPanelId) {
                    val moved = LayoutNode.Leaf(panelId)
                    when (zone) {
                        DropZone.CENTER -> moved
                        DropZone.LEFT -> LayoutNode.Split(LayoutNode.Direction.HORIZONTAL, 0.5f, listOf(moved, node))
                        DropZone.RIGHT -> LayoutNode.Split(LayoutNode.Direction.HORIZONTAL, 0.5f, listOf(node, moved))
                        DropZone.TOP -> LayoutNode.Split(LayoutNode.Direction.VERTICAL, 0.5f, listOf(moved, node))
                        DropZone.BOTTOM -> LayoutNode.Split(LayoutNode.Direction.VERTICAL, 0.5f, listOf(node, moved))
                    }
                } else node
            }
            is LayoutNode.Split ->
                node.copy(children = node.children.map { insertRelative(it, panelId, targetPanelId, zone) })
        }
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
