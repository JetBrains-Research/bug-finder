package org.jetbrains.research.plugin.ide

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.research.plugin.localization.PyMethodsAnalyzer

class BugFinderInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return PyMethodsAnalyzer(holder)
    }
}