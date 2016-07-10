package shibafu.yukari.filter.sexp

import org.junit.Test
import shibafu.yukari.stub.FakeTextStatus
import kotlin.test.assertEquals

/**
 * Created by shibafu on 2016/07/10.
 */
class AddOperatorNodeTest {

    @Test fun singleNumericTest() {
        val children = listOf(
                ValueNode(1)
        )
        val addOperatorNode = AddOperatorNode(children)
        val result = addOperatorNode.evaluate(FakeTextStatus(0, ""), emptyList()) as Long

        assertEquals(1, result)
    }

    @Test fun numericPairTest() {
        val children = listOf(
                ValueNode(1),
                ValueNode(2)
        )
        val addOperatorNode = AddOperatorNode(children)
        val result = addOperatorNode.evaluate(FakeTextStatus(0, ""), emptyList()) as Long

        assertEquals(3, result)
    }

    @Test fun numericPairTest2() {
        val children = listOf(
                ValueNode(1),
                ValueNode(-2)
        )
        val addOperatorNode = AddOperatorNode(children)
        val result = addOperatorNode.evaluate(FakeTextStatus(0, ""), emptyList()) as Long

        assertEquals(-1, result)
    }

    @Test fun numericTripleTest() {
        val children = listOf(
                ValueNode(1),
                ValueNode(2),
                ValueNode(3)
        )
        val addOperatorNode = AddOperatorNode(children)
        val result = addOperatorNode.evaluate(FakeTextStatus(0, ""), emptyList()) as Long

        assertEquals(6, result)
    }
}