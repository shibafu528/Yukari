package shibafu.yukari.entity

object StatusComparator {
    val BY_OWNED_STATUS = Comparator.comparing<Status, _> { !it.isOwnedStatus() }
    val BY_MENTIONED = Comparator.comparing<Status, _> { status ->
        status.mentions.find { it.isMentionedTo(status.representUser) } == null
    }
    val BY_PRIMARY_ACCOUNT_RECEIVED = Comparator.comparing<Status, _> { !it.representUser.isPrimary }
    val BY_RECEIVER_ID = Comparator.comparingLong<Status> { it.representUser.InternalId }
}