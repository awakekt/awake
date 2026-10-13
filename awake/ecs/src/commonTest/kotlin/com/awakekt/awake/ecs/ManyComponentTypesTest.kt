/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A world with 200 component types. Type IDs 64 and 128 open the second and third word of each
 * entity's signature, so every check here pairs a type on each side of a word boundary.
 */
class ManyComponentTypesTest {

    @Test
    fun addsRemovesAndChecksEveryTypeAcrossSignatureWords() {
        val world = World()
        val entity = world.create()
        val bystander = world.create()

        manyComponents.forEach { world.addAny(entity, it) }

        assertEquals(199, world.typeId(manyTypes.last()).value)
        manyTypes.forEach { type ->
            assertTrue(world.has(entity, type), "has $type")
            assertFalse(world.has(bystander, type), "bystander has $type")
        }
        assertEquals(manyTypes, world.componentTypes(entity))
        assertEquals(manyTypes, world.inspectStorage(entity)!!.components.map { it.type })

        val removed = boundaryIds.map { manyTypes[it] }.toSet()
        removed.forEach { assertTrue(world.remove(entity, it) != null, "remove $it") }

        manyTypes.forEach { type -> assertEquals(type !in removed, world.has(entity, type), "has $type") }
        assertEquals(manyTypes - removed, world.componentTypes(entity))
    }

    @Test
    fun queriesAndTypedFamiliesMatchAcrossSignatureWords() {
        val world = World()
        manyTypes.forEach { world.typeId(it) }
        val lowAndMiddle = world.create().also { world.addAll(it, 63, 64) }
        val middleAndHigh = world.create().also { world.addAll(it, 64, 128) }
        val highOnly = world.create().also { world.addAll(it, 128, 199) }

        assertEquals(setOf(lowAndMiddle, middleAndHigh), world.query(manyTypes[64]).toSet())
        assertEquals(setOf(lowAndMiddle), world.query(manyTypes[63], manyTypes[64]).toSet())
        assertEquals(setOf(highOnly), world.query(manyTypes[128], manyTypes[199]).toSet())
        assertEquals(setOf(middleAndHigh), world.family(manyTypes[64], manyTypes[128]).members())
    }

    @Test
    fun familySpecAllOneAndExcludeSpanSignatureWords() {
        val world = World()
        manyTypes.forEach { world.typeId(it) }
        val family = world.family {
            all(manyTypes[0], manyTypes[64], manyTypes[128])
            one(manyTypes[63], manyTypes[199])
            exclude(manyTypes[127])
        }
        val match = world.create().also { world.addAll(it, 0, 64, 128, 199) }
        val missingAll = world.create().also { world.addAll(it, 0, 128, 199) }
        val missingOne = world.create().also { world.addAll(it, 0, 64, 128) }
        val excluded = world.create().also { world.addAll(it, 0, 64, 128, 63, 127) }

        assertEquals(setOf(match), family.members())

        world.addAll(missingAll, 64)
        world.addAll(missingOne, 63)
        world.remove(excluded, manyTypes[127])
        assertEquals(setOf(match, missingAll, missingOne, excluded), family.members())

        world.remove(match, manyTypes[128])
        world.remove(missingOne, manyTypes[63])
        world.addAll(excluded, 127)
        assertEquals(setOf(missingAll), family.members())

        // A family built after the fact reads the same signatures.
        val rebuilt = world.family {
            all(manyTypes[0], manyTypes[64], manyTypes[128])
            one(manyTypes[63], manyTypes[199])
            exclude(manyTypes[127])
        }
        assertEquals(setOf(missingAll), rebuilt.members())
    }

    @Test
    fun signaturesSurviveWideningAndRecycling() {
        val world = World()
        manyTypes.forEach { world.typeId(it) }
        val early = world.create().also { world.addAll(it, 0, 63, 64) }

        // Type 150 opens the third word; the earlier entity keeps its bits in the first two.
        val wide = world.create().also { world.addAll(it, 150) }
        assertEquals(listOf(manyTypes[0], manyTypes[63], manyTypes[64]), world.componentTypes(early))
        assertEquals(listOf(manyTypes[150]), world.componentTypes(wide))

        // Growing past the initial entity capacity keeps every word.
        val later = List(40) { world.create().also { world.addAll(it, 1, 65, 151) } }
        assertEquals(listOf(manyTypes[0], manyTypes[63], manyTypes[64]), world.componentTypes(early))
        later.forEach { assertEquals(listOf(manyTypes[1], manyTypes[65], manyTypes[151]), world.componentTypes(it)) }

        world.destroy(wide)
        val recycled = world.create()
        assertEquals(wide.id, recycled.id)
        assertEquals(emptyList(), world.componentTypes(recycled))
        assertFalse(world.has(recycled, manyTypes[150]))
    }

    private fun World.addAll(entity: Entity, vararg ids: Int) = ids.forEach { addAny(entity, manyComponents[it]) }

    @Suppress("UNCHECKED_CAST") // Each component is registered under its own class.
    private fun World.addAny(entity: Entity, component: Any) {
        add(entity, component::class as KClass<Any>, component)
    }

    private fun Family.members(): Set<Entity> = buildSet { this@members.forEach { add(it) } }

    private fun Family2<*, *>.members(): Set<Entity> = buildSet { this@members.forEach { entity, _, _ -> add(entity) } }
}

/** Last ID of one signature word and first of the next, plus the final ID. */
private val boundaryIds = listOf(63, 64, 127, 128, 199)

private val manyComponents: List<Any> = listOf(
    Wide000, Wide001, Wide002, Wide003, Wide004, Wide005, Wide006, Wide007, Wide008, Wide009,
    Wide010, Wide011, Wide012, Wide013, Wide014, Wide015, Wide016, Wide017, Wide018, Wide019,
    Wide020, Wide021, Wide022, Wide023, Wide024, Wide025, Wide026, Wide027, Wide028, Wide029,
    Wide030, Wide031, Wide032, Wide033, Wide034, Wide035, Wide036, Wide037, Wide038, Wide039,
    Wide040, Wide041, Wide042, Wide043, Wide044, Wide045, Wide046, Wide047, Wide048, Wide049,
    Wide050, Wide051, Wide052, Wide053, Wide054, Wide055, Wide056, Wide057, Wide058, Wide059,
    Wide060, Wide061, Wide062, Wide063, Wide064, Wide065, Wide066, Wide067, Wide068, Wide069,
    Wide070, Wide071, Wide072, Wide073, Wide074, Wide075, Wide076, Wide077, Wide078, Wide079,
    Wide080, Wide081, Wide082, Wide083, Wide084, Wide085, Wide086, Wide087, Wide088, Wide089,
    Wide090, Wide091, Wide092, Wide093, Wide094, Wide095, Wide096, Wide097, Wide098, Wide099,
    Wide100, Wide101, Wide102, Wide103, Wide104, Wide105, Wide106, Wide107, Wide108, Wide109,
    Wide110, Wide111, Wide112, Wide113, Wide114, Wide115, Wide116, Wide117, Wide118, Wide119,
    Wide120, Wide121, Wide122, Wide123, Wide124, Wide125, Wide126, Wide127, Wide128, Wide129,
    Wide130, Wide131, Wide132, Wide133, Wide134, Wide135, Wide136, Wide137, Wide138, Wide139,
    Wide140, Wide141, Wide142, Wide143, Wide144, Wide145, Wide146, Wide147, Wide148, Wide149,
    Wide150, Wide151, Wide152, Wide153, Wide154, Wide155, Wide156, Wide157, Wide158, Wide159,
    Wide160, Wide161, Wide162, Wide163, Wide164, Wide165, Wide166, Wide167, Wide168, Wide169,
    Wide170, Wide171, Wide172, Wide173, Wide174, Wide175, Wide176, Wide177, Wide178, Wide179,
    Wide180, Wide181, Wide182, Wide183, Wide184, Wide185, Wide186, Wide187, Wide188, Wide189,
    Wide190, Wide191, Wide192, Wide193, Wide194, Wide195, Wide196, Wide197, Wide198, Wide199,
)

private val manyTypes: List<KClass<out Any>> = manyComponents.map { it::class }

private object Wide000
private object Wide001
private object Wide002
private object Wide003
private object Wide004
private object Wide005
private object Wide006
private object Wide007
private object Wide008
private object Wide009
private object Wide010
private object Wide011
private object Wide012
private object Wide013
private object Wide014
private object Wide015
private object Wide016
private object Wide017
private object Wide018
private object Wide019
private object Wide020
private object Wide021
private object Wide022
private object Wide023
private object Wide024
private object Wide025
private object Wide026
private object Wide027
private object Wide028
private object Wide029
private object Wide030
private object Wide031
private object Wide032
private object Wide033
private object Wide034
private object Wide035
private object Wide036
private object Wide037
private object Wide038
private object Wide039
private object Wide040
private object Wide041
private object Wide042
private object Wide043
private object Wide044
private object Wide045
private object Wide046
private object Wide047
private object Wide048
private object Wide049
private object Wide050
private object Wide051
private object Wide052
private object Wide053
private object Wide054
private object Wide055
private object Wide056
private object Wide057
private object Wide058
private object Wide059
private object Wide060
private object Wide061
private object Wide062
private object Wide063
private object Wide064
private object Wide065
private object Wide066
private object Wide067
private object Wide068
private object Wide069
private object Wide070
private object Wide071
private object Wide072
private object Wide073
private object Wide074
private object Wide075
private object Wide076
private object Wide077
private object Wide078
private object Wide079
private object Wide080
private object Wide081
private object Wide082
private object Wide083
private object Wide084
private object Wide085
private object Wide086
private object Wide087
private object Wide088
private object Wide089
private object Wide090
private object Wide091
private object Wide092
private object Wide093
private object Wide094
private object Wide095
private object Wide096
private object Wide097
private object Wide098
private object Wide099
private object Wide100
private object Wide101
private object Wide102
private object Wide103
private object Wide104
private object Wide105
private object Wide106
private object Wide107
private object Wide108
private object Wide109
private object Wide110
private object Wide111
private object Wide112
private object Wide113
private object Wide114
private object Wide115
private object Wide116
private object Wide117
private object Wide118
private object Wide119
private object Wide120
private object Wide121
private object Wide122
private object Wide123
private object Wide124
private object Wide125
private object Wide126
private object Wide127
private object Wide128
private object Wide129
private object Wide130
private object Wide131
private object Wide132
private object Wide133
private object Wide134
private object Wide135
private object Wide136
private object Wide137
private object Wide138
private object Wide139
private object Wide140
private object Wide141
private object Wide142
private object Wide143
private object Wide144
private object Wide145
private object Wide146
private object Wide147
private object Wide148
private object Wide149
private object Wide150
private object Wide151
private object Wide152
private object Wide153
private object Wide154
private object Wide155
private object Wide156
private object Wide157
private object Wide158
private object Wide159
private object Wide160
private object Wide161
private object Wide162
private object Wide163
private object Wide164
private object Wide165
private object Wide166
private object Wide167
private object Wide168
private object Wide169
private object Wide170
private object Wide171
private object Wide172
private object Wide173
private object Wide174
private object Wide175
private object Wide176
private object Wide177
private object Wide178
private object Wide179
private object Wide180
private object Wide181
private object Wide182
private object Wide183
private object Wide184
private object Wide185
private object Wide186
private object Wide187
private object Wide188
private object Wide189
private object Wide190
private object Wide191
private object Wide192
private object Wide193
private object Wide194
private object Wide195
private object Wide196
private object Wide197
private object Wide198
private object Wide199
