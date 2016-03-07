package shibafu.yukari.stub

import shibafu.yukari.twitter.statusimpl.FakeStatus

public class FakeTextStatus(id: Long, private val text: String) : FakeStatus(id) {
    public override fun getText(): String = this.text
}