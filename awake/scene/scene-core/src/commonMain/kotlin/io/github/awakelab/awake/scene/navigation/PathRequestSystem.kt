/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.navigation

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Answers every [PathStatus.Pending] request from [navMesh].
 *
 * Requests in any other state are left alone, so an answered route survives until its owner asks
 * for another one — a behaviour that has already reached [PathStatus.Ready] must not have its
 * waypoints replaced underneath it.
 *
 * With no [searchScope] the search runs inline on the frame thread, which is what a single-tile
 * grid and a handful of agents want: no dispatch, no latency, and a deterministic single-threaded
 * schedule. Pass a scope — the same one the runtime gives `WorldPartitionSystem` — and searches run
 * off the frame thread instead, with answers applied here on the next update. The component's API
 * is identical either way, which is the whole reason it was request-and-poll from the start.
 *
 * The off-thread half never touches the `World`: it is handed copies of the endpoints and returns
 * waypoints, exactly as [CellContent][io.github.awakelab.awake.scene.world.CellContent] does
 * for cell loads. `World` is not thread-safe and this is not the place that finds out.
 *
 * [navMesh] is read from whichever thread the scope dispatches to, so an implementation used this
 * way must tolerate that — `StreamedNavGrid` does, by snapshotting its resident tiles per search.
 */
class PathRequestSystem(
    private val navMesh: NavMesh,
    private val searchScope: CoroutineScope? = null,
) : System {

    /** In-flight searches, so a cancelled or re-issued request can drop the one it replaced. */
    private val inFlight = HashMap<Entity, Search>()

    /**
     * Finished searches waiting for a frame thread. Unlimited for the same reason cell streaming's
     * is: silently dropping an answer would leave an agent pending forever.
     */
    private val answers = Channel<Answer>(Channel.UNLIMITED)

    private var nextSearchId = 0L

    override fun update(world: World, delta: Float) {
        applyAnswers(world)
        world.family<PathRequest>().forEach { entity, request ->
            when {
                request.status != PathStatus.Pending -> abandon(entity)
                searchScope == null -> answer(request, navMesh.findPath(request.start, request.goal))
                entity !in inFlight -> issue(entity, request)
            }
        }
        abandonRequestsThatAreGone(world)
    }

    /**
     * Starts one search, against copies of the endpoints.
     *
     * Copies because [PathRequest.start] and [PathRequest.goal] are owned, mutable, and rewritten
     * the moment the entity asks for a different route — a search reading them from another thread
     * would answer a question nobody asked.
     */
    private fun issue(entity: Entity, request: PathRequest) {
        val scope = searchScope ?: return
        val id = nextSearchId++
        val generation = request.queryGeneration
        val start = Vec3f(request.start.x, request.start.y, request.start.z)
        val goal = Vec3f(request.goal.x, request.goal.y, request.goal.z)
        val job = scope.launch {
            val waypoints = navMesh.findPath(start, goal)
            answers.trySend(Answer(entity, id, generation, waypoints))
        }
        inFlight[entity] = Search(id, job)
    }

    /**
     * Applies every answer that is still the answer to the question its request is asking.
     *
     * Two checks, because they catch different things. The search id rejects an answer from a job
     * this system has already replaced; [PathRequest.queryGeneration] rejects an answer whose
     * question the *requester* withdrew, which a status check cannot see once it has asked again.
     */
    private fun applyAnswers(world: World) {
        while (true) {
            val answer = answers.tryReceive().getOrNull() ?: return
            apply(world, answer)
        }
    }

    private fun apply(world: World, answer: Answer) {
        if (inFlight[answer.entity]?.id != answer.id) return
        inFlight.remove(answer.entity)
        val request = world.get<PathRequest>(answer.entity) ?: return
        val current = request.status == PathStatus.Pending &&
            request.queryGeneration == answer.generation
        if (current) answer(request, answer.waypoints)
    }

    private fun answer(request: PathRequest, waypoints: List<Vec3f>) {
        request.waypoints = waypoints
        request.status = if (waypoints.isEmpty()) PathStatus.Unreachable else PathStatus.Ready
    }

    /** Stops [entity]'s search, if it has one. Cheap and idempotent for the common no-search case. */
    private fun abandon(entity: Entity) {
        inFlight.remove(entity)?.job?.cancel()
    }

    /** An entity destroyed mid-search never comes back through the family, so sweep for it. */
    private fun abandonRequestsThatAreGone(world: World) {
        if (inFlight.isEmpty()) return
        val gone = inFlight.keys.filter { world.get<PathRequest>(it) == null }
        gone.forEach { abandon(it) }
    }

    private class Search(val id: Long, val job: Job)

    private class Answer(
        val entity: Entity,
        val id: Long,
        val generation: Int,
        val waypoints: List<Vec3f>,
    )
}
