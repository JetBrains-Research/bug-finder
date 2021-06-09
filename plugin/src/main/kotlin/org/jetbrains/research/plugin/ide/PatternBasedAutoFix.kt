package org.jetbrains.research.plugin.ide

import com.github.gumtreediff.actions.model.*
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.util.collectDescendantsOfType
import com.jetbrains.python.psi.PyElement
import org.jetbrains.research.common.gumtree.PyElementTransformer
import org.jetbrains.research.common.gumtree.PyPsiGumTree
import org.jetbrains.research.common.gumtree.getAllTreesFromActions
import org.jetbrains.research.common.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.plugin.PatternsStorage
import org.jgrapht.GraphMapping

class PatternBasedAutoFix(
    private val problematicVertex: PatternSpecificVertex,
    private val mappingsHolder: RevizorInspection.PyMethodsAnalyzer.DetectedVertexMappingsHolder
) : LocalQuickFix {

    private val logger = Logger.getInstance(this::class.java)

    override fun getFamilyName(): String = "Revizor: autofix using pattern"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        try {
            val suggestionsPopup = JBPopupFactory.getInstance()
                .createListPopup(FixSuggestionsListPopupStep())
            FileEditorManager.getInstance(project).selectedTextEditor?.let { editor ->
                suggestionsPopup.showInBestPositionFor(editor)
            }
        } catch (ex: Exception) {
            logger.error(ex)
        }
    }

    inner class FixSuggestionsListPopupStep : BaseListPopupStep<String>(
        "Patterns", mappingsHolder.patternsIdsByVertex[problematicVertex]?.toList() ?: listOf()
    ) {
        private var selectedPatternId: String = ""
        private lateinit var vertexMapping: GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>
        private lateinit var namesMapping: Map<String, String>
        private val elementByTree = hashMapOf<PyPsiGumTree, PyElement>()

        /**
         * This map contains chains of PSI elements. It is useful for finding current revision of needed PSI element
         */
        private val revisions = hashMapOf<PyElement, PyElement>()

        override fun getTextFor(patternId: String) = PatternsStorage.getPatternDescriptionById(patternId)

        override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
            selectedPatternId = selectedValue
            return super.onChosen(selectedValue, finalChoice)
        }

        override fun getFinalRunnable(): Runnable {
            return Runnable { applyEditFromPattern(selectedPatternId) }
        }

        private fun applyEditFromPattern(patternId: String) {
            val actions = PatternsStorage.getPatternEditActionsById(patternId)
            val transformer = PyElementTransformer(PatternsStorage.project)
            vertexMapping = mappingsHolder.vertexMappingsByTargetVertex[problematicVertex]!![patternId]!!
            namesMapping = mappingsHolder.varNamesMappingByVertexMapping[vertexMapping]!!
            elementByTree.clear()
            revisions.clear()

            // Prepare mappings from PyPsiGumTrees to actual PSI elements
            for (tree in getAllTreesFromActions(actions)) {
                val pyPsiGumTree = tree as PyPsiGumTree
                val element = vertexMapping.getVertexCorrespondence(pyPsiGumTree.rootVertex, false)
                    ?.origin?.psi ?: continue
                elementByTree[pyPsiGumTree] = element
            }

            WriteCommandAction.runWriteCommandAction(PatternsStorage.project) {
                for (action in actions) {
                    try {
                        when (action) {
                            is Update -> {
                                val preprocessedAction = updateLabels(action) as Update
                                val targetElement =
                                    findRevision(elementByTree[action.node as PyPsiGumTree]!!, revisions)
                                val newElement = transformer.applyUpdate(targetElement, preprocessedAction)
                                updateRevisions(targetElement, newElement, revisions)
                            }
                            is Delete -> {
                                val targetElement =
                                    findRevision(elementByTree[action.node as PyPsiGumTree]!!, revisions)
                                transformer.applyDelete(targetElement)
                            }
                            is Insert -> {
                                val targetParentElement =
                                    findRevision(elementByTree[action.parent as PyPsiGumTree]!!, revisions)
                                val preprocessedAction = updateLabels(action) as Insert
                                val newElement = transformer.applyInsert(targetParentElement, preprocessedAction)
                                elementByTree[action.node as PyPsiGumTree] = newElement
                            }
                            is Move -> {
                                val targetParentElement =
                                    findRevision(elementByTree[action.parent as PyPsiGumTree]!!, revisions)
                                val targetElement =
                                    findRevision(elementByTree[action.node as PyPsiGumTree]!!, revisions)
                                val movedElement = transformer.applyMove(targetElement, targetParentElement, action)
                                updateRevisions(targetElement, movedElement, revisions)
                            }
                        }
                    } catch (ex: Exception) {
                        logger.warn("Can not apply the action $action")
                        logger.warn(ex)
                        continue
                    }
                }
            }
        }

        private fun updateRevisions(
            oldElement: PyElement,
            newElement: PyElement,
            revisions: MutableMap<PyElement, PyElement>
        ) {
            val oldDescendants = oldElement.collectDescendantsOfType<PyElement>()
            val newDescendants = newElement.collectDescendantsOfType<PyElement>()
            for ((old, new) in oldDescendants.zip(newDescendants)) {
                revisions[old] = new
            }
            revisions[oldElement] = newElement
        }

        private fun findRevision(element: PyElement, revisions: MutableMap<PyElement, PyElement>): PyElement {
            var currentRevision = element
            while (revisions.containsKey(currentRevision)) {
                if (currentRevision == revisions[currentRevision])
                    break
                currentRevision = revisions[currentRevision]!!
            }
            return currentRevision
        }

        private fun updateLabels(action: Action): Action {
            // Labels and values inside actions are presented in format "PsiElementType: Label"
            val oldLabel = action.node.label.substringAfterLast(":", "").trim()
            val newLabel = getNewLabel(oldLabel)

            return when (action) {
                is Insert -> {
                    val newNode = (action.node as PyPsiGumTree).deepCopy()
                    newNode.label = action.node.label.replaceAfterLast(": ", newLabel)
                    Insert(newNode, action.parent, action.position)
                }
                is Update -> {
                    val updatedOldVarName = action.value.substringAfterLast(":", "").trim()
                    val updatedNewVarName = getNewLabel(updatedOldVarName)
                    Update(action.node, action.value.replaceAfterLast(": ", updatedNewVarName))
                }
                else -> throw IllegalStateException()
            }
        }

        private fun getNewLabel(oldName: String): String {
            return if (namesMapping.containsKey(oldName)) {
                // If we already have the mapping for the full name, return it
                namesMapping.getValue(oldName)
            } else {
                // If not, try to find mapping for each attribute in the oldName
                oldName.split(".").joinToString(".") { attr -> namesMapping[attr] ?: attr }
            }
        }
    }

}