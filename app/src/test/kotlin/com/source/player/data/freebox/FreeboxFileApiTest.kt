package com.source.player.data.freebox

import com.source.player.data.freebox.api.FileOperationRequestDto
import com.source.player.data.freebox.api.FsTaskDto
import com.source.player.data.freebox.api.MkdirRequestDto
import com.source.player.data.freebox.api.PathCodec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeboxFileApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Paths ────────────────────────────────────────────────────────────

    @Test fun childOfFolder() = assertEquals("fbx://D/Musique/A", FreeboxPaths.child("fbx://D/Musique", "A"))

    @Test fun childOfRoot() = assertEquals("fbx://D", FreeboxPaths.child(FreeboxPaths.SCHEME, "D"))

    @Test fun sameOrUnder() {
        assertTrue(FreeboxPaths.isSameOrUnder("fbx://D/M/A", "fbx://D/M/A"))
        assertTrue(FreeboxPaths.isSameOrUnder("fbx://D/M/A/b.flac", "fbx://D/M/A"))
        assertFalse(FreeboxPaths.isSameOrUnder("fbx://D/M/AB", "fbx://D/M/A"))
    }

    @Test fun toB64IsRooted() = assertEquals("/D/M/a b.flac", PathCodec.decode(FreeboxPaths.toB64("fbx://D/M/a b.flac")))

    // ── Request bodies ───────────────────────────────────────────────────

    @Test fun mvBodyHasBase64FilesDstAndSkipMode() {
        val body = json.parseToJsonElement(
            json.encodeToString(
                FileOperationRequestDto.serializer(),
                FileOperationRequestDto(
                    files = listOf(FreeboxPaths.toB64("fbx://D/M/a.flac")),
                    dst = FreeboxPaths.toB64("fbx://D/M/.corbeille"),
                    mode = FileOperationRequestDto.MODE_SKIP,
                ),
            ),
        ).jsonObject
        assertEquals("/D/M/a.flac", PathCodec.decode(body["files"]!!.jsonArray[0].jsonPrimitive.content))
        assertEquals("/D/M/.corbeille", PathCodec.decode(body["dst"]!!.jsonPrimitive.content))
        assertEquals("skip", body["mode"]!!.jsonPrimitive.content)
    }

    @Test fun mkdirBodyHasBase64ParentAndClearName() {
        val body = json.parseToJsonElement(
            json.encodeToString(MkdirRequestDto.serializer(), MkdirRequestDto(FreeboxPaths.toB64("fbx://D/M"), ".corbeille")),
        ).jsonObject
        assertEquals("/D/M", PathCodec.decode(body["parent"]!!.jsonPrimitive.content))
        assertEquals(".corbeille", body["dirname"]!!.jsonPrimitive.content)
    }

    @Test fun taskParsesAndIgnoresUnknownFields() {
        val task = json.decodeFromString(
            FsTaskDto.serializer(),
            """{"id":7,"type":"mv","state":"failed","error":"file_exists","progress":24,"nfiles":3}""",
        )
        assertTrue(task.isFailed)
        assertEquals("file_exists", task.errorCode)
        assertEquals(null, FsTaskDto(id = 1, state = "done", error = "none").errorCode)
    }

    // ── Task polling ─────────────────────────────────────────────────────

    @Test fun taskIsPolledUntilDoneThenForgotten() = runBlocking {
        val states = ArrayDeque(listOf("running", "done"))
        val forgotten = ArrayList<Int>()
        val result = awaitFsTask(
            initial = FsTaskDto(id = 5, state = "queued"),
            poll = { id -> FbxResult.Ok(FsTaskDto(id = id, state = states.removeFirst())) },
            forget = { id -> forgotten += id; FbxResult.Ok(Unit) },
            pollIntervalMs = 0,
        )
        assertTrue(result is FbxResult.Ok)
        assertEquals(listOf(5), forgotten)
    }

    @Test fun failedTaskReportsItsError() = runBlocking {
        val result = awaitFsTask(
            initial = FsTaskDto(id = 5, state = "failed", error = "disk_full"),
            poll = { error("not polled") },
            forget = { FbxResult.Ok(Unit) },
            pollIntervalMs = 0,
        )
        assertEquals(FreeboxError.Api("disk_full", "Freebox fs task 5 failed"), (result as FbxResult.Err).error)
    }

    @Test fun runningTaskIsNotDeletedOnTimeout() = runBlocking {
        var forgotten = false
        val result = awaitFsTask(
            initial = FsTaskDto(id = 5, state = "running"),
            poll = { id -> FbxResult.Ok(FsTaskDto(id = id, state = "running")) },
            forget = { forgotten = true; FbxResult.Ok(Unit) },
            pollIntervalMs = 0,
            attempts = 3,
        )
        assertEquals(FreeboxError.Api(TASK_TIMEOUT), (result as FbxResult.Err).error)
        assertFalse(forgotten)
    }
}
