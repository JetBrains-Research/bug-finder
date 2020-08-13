package org.jetbrains.research.pyflowgraph

import com.jetbrains.python.psi.*

fun getNodeFullName(node: PyElement): String =
    when (node) {
        is PyFunction, is PyNamedParameter -> node.name ?: ""
        is PyReferenceExpression, is PyTargetExpression -> {
            var fullName = (node as PyQualifiedExpression).referencedName
            var currentNode = node.qualifier
            while (currentNode is PyReferenceExpression
                || currentNode is PyCallExpression && currentNode.callee is PyReferenceExpression
            ) {
                if (currentNode is PyReferenceExpression) {
                    fullName = currentNode.referencedName + "." + fullName
                    currentNode = currentNode.qualifier
                } else {
                    // TODO: add argument reference name inside parenthesis?
                    fullName = (currentNode as PyCallExpression).callee?.name + "()." + fullName
                    currentNode = (currentNode.callee as PyReferenceExpression).qualifier
                }
            }
            fullName ?: ""
        }
        is PySubscriptionExpression -> {
            val operand = getNodeFullName(node.operand)
            val index = node.indexExpression?.let { getNodeFullName(it) } ?: ""
            "$operand[$index]"
        }
        is PySliceExpression -> {
            val operand = getNodeFullName(node.operand)
            val sliceItems = arrayListOf<String>()
            node.sliceItem?.lowerBound?.let { getNodeFullName(it) }?.let { sliceItems.add(it) }
            node.sliceItem?.stride?.let { getNodeFullName(it) }?.let { sliceItems.add(it) }
            node.sliceItem?.upperBound?.let { getNodeFullName(it) }?.let { sliceItems.add(it) }
            "$operand[${sliceItems.joinToString(":")}]"
        }
        is PyLiteralExpression -> "."
        else -> throw IllegalArgumentException()
    }

fun getNodeKey(node: PyElement): String =
    when (node) {
        is PyFunction, is PyNamedParameter -> node.name ?: ""
        is PyReferenceExpression, is PyTargetExpression ->
            (node as PyQualifiedExpression).asQualifiedName()?.toString() ?: ""
        else -> throw IllegalArgumentException()
    }

fun getNodeShortName(node: PyElement): String =
    when (node) {
        is PyNamedParameter, is PyFunction -> node.name ?: ""
        is PyReferenceExpression, is PyTargetExpression -> (node as PyQualifiedExpression).referencedName ?: ""
        else -> throw IllegalArgumentException()
    }

fun getOperationName(node: PyElement): String? =
    if (node is PyAugAssignmentStatement) {
        when (node.operation?.text) {
            "+=" -> "add"
            "-=" -> "sub"
            "*=" -> "mult"
            "/=" -> "div"
            "%=" -> "mod"
            "**=" -> "pow"
            ">>=" -> "rshift"
            "<<-" -> "lshift"
            "&=" -> "bitand"
            "|=" -> "bitor"
            "^=" -> "bitxor"
            else -> throw NotImplementedError("Unsupported operation type")
        }
    } else {
        throw NotImplementedError("Unsupported operation node")
    }

fun getCollectionLabel(node: PySequenceExpression): String =
    when (node) {
        is PyListLiteralExpression -> "List"
        is PyTupleExpression -> "Tuple"
        is PySetLiteralExpression -> "Set"
        is PyDictLiteralExpression -> "Dict"
        else -> throw IllegalArgumentException()
    }

object DuplicateEntryNodeException : Throwable()

object GraphBuildingException : Throwable()