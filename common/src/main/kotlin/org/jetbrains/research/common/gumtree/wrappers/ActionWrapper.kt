package org.jetbrains.research.common.gumtree.wrappers

import com.github.gumtreediff.actions.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.research.common.PatternGraph
import org.jetbrains.research.common.gumtree.PyPsiGumTree
import org.jetbrains.research.common.jgrapht.findVertexById

@Serializable
sealed class ActionWrapper {

    abstract fun reconstructAction(
        correspondingDirectedAcyclicGraph: PatternGraph,
        reconstructedTrees: HashMap<Int, PyPsiGumTree>
    ): Action

    @Serializable
    @SerialName("Delete")
    class DeleteActionWrapper : ActionWrapper {
        private val targetTreeWrapper: PyPsiGumTreeWrapper
        private val targetTreeHashCode: Int

        constructor(action: Delete) : super() {
            this.targetTreeWrapper = PyPsiGumTreeWrapper(action.node as PyPsiGumTree)
            this.targetTreeHashCode = (action.node as PyPsiGumTree).hashCode()
        }

        override fun reconstructAction(
            correspondingDirectedAcyclicGraph: PatternGraph,
            reconstructedTrees: HashMap<Int, PyPsiGumTree>
        ): Delete {
            targetTreeWrapper.rootVertex = targetTreeWrapper.rootVertexId
                ?.let { correspondingDirectedAcyclicGraph.findVertexById(it) }
            val targetNode = reconstructedTrees.getOrPut(targetTreeHashCode) { targetTreeWrapper.getNode() }
            return Delete(targetNode)
        }
    }

    @Serializable
    @SerialName("Update")
    class UpdateActionWrapper : ActionWrapper {
        private val targetTreeWrapper: PyPsiGumTreeWrapper
        private val targetTreeHashCode: Int
        private val value: String

        constructor(action: Update) : super() {
            this.targetTreeWrapper = PyPsiGumTreeWrapper(action.node as PyPsiGumTree)
            this.targetTreeHashCode = (action.node as PyPsiGumTree).hashCode()
            this.value = action.value
        }

        override fun reconstructAction(
            correspondingDirectedAcyclicGraph: PatternGraph,
            reconstructedTrees: HashMap<Int, PyPsiGumTree>
        ): Update {
            targetTreeWrapper.rootVertex = targetTreeWrapper.rootVertexId
                ?.let { correspondingDirectedAcyclicGraph.findVertexById(it) }
            val targetNode = reconstructedTrees.getOrPut(targetTreeHashCode) { targetTreeWrapper.getNode() }
            return Update(targetNode, value)
        }
    }

    @Serializable
    @SerialName("Insert")
    class InsertActionWrapper : ActionWrapper {
        private val targetTreeWrapper: PyPsiGumTreeWrapper
        private val parentTreeWrapper: PyPsiGumTreeWrapper
        private val targetTreeHashCode: Int
        private val parentTreeHashCode: Int
        private val position: Int

        constructor(action: Insert) : super() {
            this.targetTreeWrapper = PyPsiGumTreeWrapper(action.node as PyPsiGumTree)
            this.parentTreeWrapper = PyPsiGumTreeWrapper(action.parent as PyPsiGumTree)
            this.targetTreeHashCode = (action.node as PyPsiGumTree).hashCode()
            this.parentTreeHashCode = (action.parent as PyPsiGumTree).hashCode()
            this.position = action.position
        }

        override fun reconstructAction(
            correspondingDirectedAcyclicGraph: PatternGraph,
            reconstructedTrees: HashMap<Int, PyPsiGumTree>
        ): Insert {
            targetTreeWrapper.rootVertex = null
            parentTreeWrapper.rootVertex = parentTreeWrapper.rootVertexId
                ?.let { correspondingDirectedAcyclicGraph.findVertexById(it) }
            val targetNode = reconstructedTrees.getOrPut(targetTreeHashCode) { targetTreeWrapper.getNode() }
            val parentNode = reconstructedTrees.getOrPut(parentTreeHashCode) { parentTreeWrapper.getNode() }
            return Insert(targetNode, parentNode, position)
        }
    }

    @Serializable
    @SerialName("Move")
    class MoveActionWrapper : ActionWrapper {
        private val targetTreeWrapper: PyPsiGumTreeWrapper
        private val parentTreeWrapper: PyPsiGumTreeWrapper
        private val targetTreeHashCode: Int
        private val parentTreeHashCode: Int
        private val position: Int

        constructor(action: Move) : super() {
            this.targetTreeWrapper = PyPsiGumTreeWrapper(action.node as PyPsiGumTree)
            this.parentTreeWrapper = PyPsiGumTreeWrapper(action.parent as PyPsiGumTree)
            this.targetTreeHashCode = (action.node as PyPsiGumTree).hashCode()
            this.parentTreeHashCode = (action.parent as PyPsiGumTree).hashCode()
            this.position = action.position
        }

        override fun reconstructAction(
            correspondingDirectedAcyclicGraph: PatternGraph,
            reconstructedTrees: HashMap<Int, PyPsiGumTree>
        ): Move {
            targetTreeWrapper.rootVertex = targetTreeWrapper.rootVertexId
                ?.let { correspondingDirectedAcyclicGraph.findVertexById(it) }
            parentTreeWrapper.rootVertex = parentTreeWrapper.rootVertexId
                ?.let { correspondingDirectedAcyclicGraph.findVertexById(it) }
            val targetNode = reconstructedTrees.getOrPut(targetTreeHashCode) { targetTreeWrapper.getNode() }
            val parentNode = reconstructedTrees.getOrPut(parentTreeHashCode) { parentTreeWrapper.getNode() }
            return Move(targetNode, parentNode, position)
        }
    }
}