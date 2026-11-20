package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.PersonDetail
import com.cooper.homelab.ephyra.browse.TitleDetail

private const val TICKS_PER_MINUTE = 600_000_000L

/** The person page from who they are and the titles read so far. */
fun personPageUi(
    person: PersonDetail,
    credits: List<BrowseItem>,
    state: CreditsState,
): PersonPageUi =
    PersonPageUi(
        id = person.id,
        name = person.name,
        photo = person.imageTag?.let { TitleImage(person.id, ImageKind.Primary, it) },
        facts = PersonFacts(person.birthDate, person.deathDate, person.birthPlace?.takeIf { it.isNotBlank() }),
        biography = person.overview?.takeIf { it.isNotBlank() },
        credits = credits.map(::titleCard),
        creditsState = state,
    )

/** The collection page from the box set's own item and its parts, in the order the server gave them (release order). */
fun collectionPageUi(
    detail: TitleDetail,
    parts: List<BrowseItem>,
): CollectionPageUi {
    val item = detail.item
    return CollectionPageUi(
        id = item.id,
        name = item.name,
        overview = detail.overview?.takeIf { it.isNotBlank() },
        backdrop = item.imageTags[ImageKind.Backdrop]?.let { TitleImage(item.id, ImageKind.Backdrop, it) },
        poster = item.imageTags[ImageKind.Primary]?.let { TitleImage(item.id, ImageKind.Primary, it) },
        parts = parts.map { CollectionPart(titleCard(it), it.runTimeTicks?.let { ticks -> (ticks / TICKS_PER_MINUTE).toInt() }) },
        watchedCount = parts.count { it.userData.played },
    )
}
