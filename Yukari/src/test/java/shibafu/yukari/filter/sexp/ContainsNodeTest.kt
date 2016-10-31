package shibafu.yukari.filter.sexp

import org.junit.Test
import shibafu.yukari.stub.FakeTextStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Created by shibafu on 2015/12/11.
 */
public class ContainsNodeTest {

    @Test fun stringEvaluateTest() {
        val children = listOf(
                ValueNode("yuzuki yukari"),
                ValueNode("yukari")
        )
        val containsNode = ContainsNode(children)
        val result = containsNode.evaluate(EvaluateContext(FakeTextStatus(0, ""), emptyList())) as Boolean

        assertEquals(true, result)
    }

    @Test fun listEvaluateTest() {
        val children = listOf(
                ValueNode(listOf("yukari", "maki", "akane", "aoi")),
                ValueNode("yukari")
        )
        val containsNode = ContainsNode(children)
        val result = containsNode.evaluate(EvaluateContext(FakeTextStatus(0, ""), emptyList())) as Boolean

        assertEquals(true, result)
    }

    @Test fun listInvertEvaluateTest() {
        val children = listOf(
                ValueNode(listOf("yukari", "maki", "akane", "aoi")),
                ValueNode("miku")
        )
        val containsNode = ContainsNode(children)
        val result = containsNode.evaluate(EvaluateContext(FakeTextStatus(0, ""), emptyList())) as Boolean

        assertNotEquals(true, result)
    }
}