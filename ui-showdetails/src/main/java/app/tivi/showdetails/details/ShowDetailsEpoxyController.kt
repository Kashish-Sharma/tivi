/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.tivi.showdetails.details

import android.content.Context
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.forEach
import app.tivi.common.epoxy.TotalSpanOverride
import app.tivi.common.epoxy.tiviCarousel
import app.tivi.common.epoxy.withModelsFrom
import app.tivi.common.layouts.DetailsBadgeBindingModel_
import app.tivi.common.layouts.detailsHeader
import app.tivi.data.entities.ActionDate
import app.tivi.data.entities.Episode
import app.tivi.data.entities.Season
import app.tivi.data.entities.TiviShow
import app.tivi.data.entities.findHighestRatedPoster
import app.tivi.data.resultentities.RelatedShowEntryWithShow
import app.tivi.data.resultentities.SeasonWithEpisodesAndWatches
import app.tivi.data.views.FollowedShowsWatchStats
import app.tivi.inject.PerActivity
import app.tivi.showdetails.details.databinding.ViewHolderDetailsSeasonBinding
import app.tivi.ui.widget.PopupMenuButton
import com.airbnb.epoxy.Carousel
import com.airbnb.epoxy.EpoxyModelGroup
import com.airbnb.epoxy.IdUtils
import com.airbnb.epoxy.TypedEpoxyController
import com.airbnb.mvrx.Async
import com.airbnb.mvrx.Success
import javax.inject.Inject
import kotlin.math.roundToInt

class ShowDetailsEpoxyController @Inject constructor(
    @PerActivity private val context: Context,
    private val textCreator: ShowDetailsTextCreator
) : TypedEpoxyController<ShowDetailsViewState>() {

    var callbacks: Callbacks? = null

    interface Callbacks {
        fun onRelatedShowClicked(show: TiviShow, itemView: View)
        fun onEpisodeClicked(episode: Episode, itemView: View)
        fun onMarkSeasonWatched(season: Season, onlyAired: Boolean, date: ActionDate)
        fun onMarkSeasonUnwatched(season: Season)
        fun onCollapseSeason(season: Season, itemView: View)
        fun onExpandSeason(season: Season, itemView: View)
        fun onMarkSeasonFollowed(season: Season)
        fun onMarkSeasonIgnored(season: Season)
        fun onMarkPreviousSeasonsIgnored(season: Season)
    }

    override fun buildModels(viewState: ShowDetailsViewState) {
        buildShowModels(viewState.show)

        val episodeWithSeason = viewState.nextEpisodeToWatch()
        if (episodeWithSeason?.episode != null) {
            detailsHeader {
                id("next_episode_header")
                title(R.string.details_next_episode_to_watch)
                spanSizeOverride(TotalSpanOverride)
            }
            detailsNextEpisodeToWatch {
                id("next_episode_${episodeWithSeason.episode!!.id}")
                spanSizeOverride(TotalSpanOverride)
                season(episodeWithSeason.season)
                episode(episodeWithSeason.episode)
                textCreator(textCreator)
                clickListener { view ->
                    callbacks?.onEpisodeClicked(episodeWithSeason.episode!!, view)
                }
            }
        }

        buildRelatedShowsModels(viewState.relatedShows)

        buildSeasonsModels(viewState.viewStats, viewState.seasons, viewState.expandedSeasonIds)
    }

    private fun buildShowModels(show: TiviShow) {
        val badges = ArrayList<DetailsBadgeBindingModel_>()
        show.traktRating?.let { rating ->
            badges += DetailsBadgeBindingModel_().apply {
                val ratingOutOfOneHundred = (rating * 10).roundToInt()
                id("rating")
                label(context.getString(R.string.percentage_format, ratingOutOfOneHundred))
                icon(R.drawable.ic_details_rating)
                contentDescription(context.getString(R.string.rating_content_description_format,
                        ratingOutOfOneHundred))
            }
        }
        show.network?.let { network ->
            badges += DetailsBadgeBindingModel_().apply {
                id("network")
                label(network)
                icon(R.drawable.ic_details_network)
                contentDescription(context.getString(R.string.network_content_description_format, network))
            }
        }
        show.certification?.let { certificate ->
            badges += DetailsBadgeBindingModel_().apply {
                id("cert")
                label(certificate)
                icon(R.drawable.ic_details_certificate)
                contentDescription(context.getString(R.string.certificate_content_description_format, certificate))
            }
        }
        show.runtime?.let { runtime ->
            badges += DetailsBadgeBindingModel_().apply {
                val runtimeMinutes = context.getString(R.string.minutes_format, runtime)
                id("runtime")
                label(runtimeMinutes)
                icon(R.drawable.ic_details_runtime)
                contentDescription(context.resources?.getQuantityString(
                        R.plurals.runtime_content_description_format, runtime, runtime))
            }
        }
        if (badges.isNotEmpty()) {
            EpoxyModelGroup(R.layout.layout_badge_holder, badges).addTo(this)
        }

        detailsHeader {
            id("about_show_header")
            title(R.string.details_about)
            spanSizeOverride(TotalSpanOverride)
        }
        detailsSummary {
            id("summary")
            entity(show)
            spanSizeOverride(TotalSpanOverride)
        }
        if (show.genres.isNotEmpty()) {
            detailsGenres {
                id("genres")
                tiviShow(show)
                textCreator(textCreator)
                spanSizeOverride(TotalSpanOverride)
            }
        }
    }

    private fun buildRelatedShowsModels(relatedShows: Async<List<RelatedShowEntryWithShow>>) {
        if (relatedShows is Success) {
            val related = relatedShows()
            if (related.isNotEmpty()) {
                detailsHeader {
                    id("related_header")
                    title(R.string.details_related)
                    spanSizeOverride(TotalSpanOverride)
                }
                tiviCarousel {
                    id("related_shows")
                    itemWidth(context.resources.getDimensionPixelSize(R.dimen.related_shows_item_width))
                    hasFixedSize(true)

                    val vert = context.resources.getDimensionPixelSize(R.dimen.spacing_small)
                    val horiz = context.resources.getDimensionPixelSize(R.dimen.spacing_normal)
                    val itemSpacing = context.resources.getDimensionPixelSize(R.dimen.spacing_micro)
                    padding(Carousel.Padding(horiz, vert, horiz, vert, itemSpacing))

                    withModelsFrom(related) { relatedEntry ->
                        val relatedShow = relatedEntry.show
                        DetailsRelatedItemBindingModel_()
                                .id("related_${relatedShow.id}")
                                .tiviShow(relatedShow)
                                .posterImage(relatedEntry.images.findHighestRatedPoster())
                                .clickListener { view -> callbacks?.onRelatedShowClicked(relatedShow, view) }
                    }
                }
            }
        }
    }

    private fun buildSeasonsModels(
        asyncStats: Async<FollowedShowsWatchStats>,
        asyncSeasons: Async<List<SeasonWithEpisodesAndWatches>>,
        expandedSeasonIds: Set<Long>
    ) {
        val stats = asyncStats()
        if (stats != null) {
            detailsHeader {
                id("view_stats_header")
                title(R.string.details_view_stats)
                spanSizeOverride(TotalSpanOverride)
            }
            detailsStats {
                id("view_stats")
                stats(stats)
                textCreator(textCreator)
                spanSizeOverride(TotalSpanOverride)
            }
        }

        if (asyncSeasons is Success) {
            val seasons = asyncSeasons()
            if (seasons.isNotEmpty()) {
                detailsHeader {
                    id("title_seasons")
                    title(R.string.show_details_seasons)
                    spanSizeOverride(TotalSpanOverride)
                }

                for (season in seasons) {
                    val expanded = expandedSeasonIds.contains(season.season.id)

                    detailsSeason {
                        id("season_${season.season.id}")
                        season(season)
                        spanSizeOverride(TotalSpanOverride)
                        textCreator(textCreator)
                        expanded(expanded)
                        clickListener { model, _, clickedView, _ ->
                            if (model.expanded()) {
                                callbacks?.onCollapseSeason(season.season, clickedView)
                            } else {
                                callbacks?.onExpandSeason(season.season, clickedView)
                            }
                        }
                        popupMenuListener(SeasonPopupMenuListener(season))
                        popupMenuClickListener(SeasonPopupClickListener(season.season))

                        onBind { _, view, _ ->
                            val binding = view.dataBinding as ViewHolderDetailsSeasonBinding
                            val listener = binding.popupMenuListener as SeasonPopupMenuListener
                            listener.season = binding.season!!
                        }
                    }

                    if (expanded) {
                        season.episodes.forEach { episodeWithWatches ->
                            detailsSeasonEpisode {
                                val episode = episodeWithWatches.episode!!
                                val itemId = IdUtils.hashString64Bit("episode_${episode.id}")
                                id(itemId)
                                textCreator(textCreator)
                                episodeWithWatches(episodeWithWatches)
                                expanded(true)
                                spanSizeOverride(TotalSpanOverride)
                                clickListener { view ->
                                    callbacks?.onEpisodeClicked(episode, view)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private class SeasonPopupMenuListener(
        var season: SeasonWithEpisodesAndWatches
    ) : PopupMenuButton.PopupMenuListener {
        override fun onPreparePopupMenu(popupMenu: PopupMenu) {
            popupMenu.menu.forEach { item ->
                when (item.itemId) {
                    R.id.season_stats_ignore -> {
                        item.isVisible = !season.season.ignored
                    }
                    R.id.season_stats_ignore_previous -> {
                        item.isVisible = (season.season.number ?: -1) >= 2
                    }
                    R.id.season_stats_include -> {
                        item.isVisible = season.season.ignored
                    }
                    R.id.season_mark_all_unwatched -> {
                        item.isVisible = season.numberWatched > 0
                    }
                    R.id.season_mark_watched_all -> {
                        item.isVisible = season.numberWatched < season.numberEpisodes
                    }
                    R.id.season_mark_watched_aired -> {
                        item.isVisible = season.numberWatched < season.numberAired &&
                                season.numberAired < season.numberEpisodes
                    }
                }
            }
        }
    }

    private inner class SeasonPopupClickListener(
        var season: Season
    ) : PopupMenu.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.season_mark_all_watched_now -> {
                    callbacks?.onMarkSeasonWatched(season, false, ActionDate.NOW)
                }
                R.id.season_mark_all_watched_air_date -> {
                    callbacks?.onMarkSeasonWatched(season, false, ActionDate.AIR_DATE)
                }
                R.id.season_mark_aired_watched_now -> {
                    callbacks?.onMarkSeasonWatched(season, true, ActionDate.NOW)
                }
                R.id.season_mark_aired_watched_air_date -> {
                    callbacks?.onMarkSeasonWatched(season, true, ActionDate.AIR_DATE)
                }
                R.id.season_mark_all_unwatched -> {
                    callbacks?.onMarkSeasonUnwatched(season)
                }
                R.id.season_stats_ignore -> {
                    callbacks?.onMarkSeasonIgnored(season)
                }
                R.id.season_stats_include -> {
                    callbacks?.onMarkSeasonFollowed(season)
                }
                R.id.season_stats_ignore_previous -> {
                    callbacks?.onMarkPreviousSeasonsIgnored(season)
                }
            }
            return true
        }
    }
}
