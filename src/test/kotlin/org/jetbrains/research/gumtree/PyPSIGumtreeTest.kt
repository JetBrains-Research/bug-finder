package org.jetbrains.research.gumtree

import com.github.gumtreediff.actions.ActionGenerator
import com.github.gumtreediff.matchers.Matchers
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.psi.PyElement


class PyPSIGumtreeTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/main/resources/patterns/2"

    fun `test gumtree matching on python psi`() {
        val rootNodeBefore = myFixture.configureByFile("before.py").children.first() as PyElement
        val rootNodeAfter = myFixture.configureByFile("after.py").children.first() as PyElement
        val src = PyPsiGumTreeGenerator().generate(rootNodeBefore)
        val dst = PyPsiGumTreeGenerator().generate(rootNodeAfter)
        val matcher = Matchers.getInstance().getMatcher(src.root, dst.root)
        matcher.match()
        val mappings = matcher.mappings
        val generator = ActionGenerator(src.root, dst.root, mappings)
        val actions = generator.generate()
        UsefulTestCase.assertSize(22, actions)
    }
}