package dev.fusion.windowing

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

object WindowRestoreChecks {
    private var checks=0
    private fun expect(value:Boolean) { checks++; check(value) { "Window restore check $checks failed" } }
    private inline fun refused(action:()->Unit) { checks++; try { action() } catch(_:Exception) { return }; error("Expected window rejection") }
    private fun streamChecks(directory: File) {
        val legacy2=File(directory,"desktop-v2.bin").inputStream().use(DesktopCodec::read)
        expect(legacy2.commandEpoch==null && legacy2.lastSequence==0L)
        expect(legacy2.windows.single().bounds==Bounds(20f,30f,400f,300f))
        val open=WindowCommand.Open("editor","Editor",Bounds(20f,30f,400f,300f),"retry")
        val empty=DesktopCommandStream.start(DesktopState(),"epoch-a")
        refused { DesktopCommandStream.start(empty,"epoch-b") }
        refused { DesktopCommandStream.start(DesktopState(),"") }
        refused { DesktopCommandStream.start(DesktopState(),"x".repeat(81)) }
        refused { DesktopCommandStream.next(DesktopState(),open) }
        refused { WindowEngine.restore(empty.copy(lastSequence=-1)) }
        refused { WindowEngine.restore(DesktopState(lastSequence=1)) }
        val first=DesktopCommandStream.next(empty,open)
        expect(DesktopCommandStream.accept(DesktopState(),first).disposition==CommandDisposition.UNINITIALIZED)
        var state=DesktopCommandStream.accept(empty,first).state
        expect(state.windows.size==1 && state.lastSequence==1L)
        val close=DesktopCommandStream.next(state,WindowCommand.Close(1))
        state=DesktopCommandStream.accept(state,close).state
        expect(state.windows.isEmpty() && state.lastSequence==2L)
        // Retrying the original envelope after Close and codec recovery cannot reopen it.
        val encoded=ByteArrayOutputStream().also { DesktopCodec.write(state,it) }.toByteArray()
        state=DesktopCodec.read(ByteArrayInputStream(encoded))
        val retry=DesktopCommandStream.accept(state,first)
        expect(retry.disposition==CommandDisposition.ALREADY_CONSUMED && retry.state==state)
        // Same sequence with a DIFFERENT payload is consumed, not asserted equal/authenticated.
        expect(DesktopCommandStream.accept(state,first.copy(command=WindowCommand.SwitchWorkspace(2))).state==state)
        val gap=first.copy(sequence=4)
        expect(DesktopCommandStream.accept(state,gap).disposition==CommandDisposition.GAP)
        expect(DesktopCommandStream.accept(state,gap).state==state)
        for(sequence in listOf(0L,-1L,Long.MIN_VALUE)) {
            val result=DesktopCommandStream.accept(state,first.copy(sequence=sequence))
            expect(result.disposition==CommandDisposition.INVALID_SEQUENCE && result.state==state)
        }
        val reset=DesktopCommandStream.start(DesktopState(),"epoch-b")
        expect(DesktopCommandStream.accept(reset,first).disposition==CommandDisposition.WRONG_EPOCH)
        expect(DesktopCommandStream.accept(state,first.copy(epoch="x".repeat(81))).disposition==CommandDisposition.WRONG_EPOCH)
        // A consumed semantic no-op still advances the watermark, so later envelopes are not gaps.
        val noop=DesktopCommandStream.next(state,WindowCommand.Close(999))
        state=DesktopCommandStream.accept(state,noop).state
        expect(state.lastSequence==3L && state.windows.isEmpty())
        state=DesktopCommandStream.accept(state,DesktopCommandStream.next(state,open)).state
        expect(state.windows.single().id==2L)
        val input=state.windows.toMutableList()
        val rejected=DesktopCommandStream.accept(state.copy(windows=input),first).state
        input.clear(); expect(rejected.windows.size==1)
        refused { (rejected.windows as MutableList<ShellWindow>).clear() }
        // No signed overflow or wraparound can convert exhaustion into a fresh sequence.
        val almost=empty.copy(lastSequence=Long.MAX_VALUE-1)
        val last=DesktopCommandStream.next(almost,open)
        val exhausted=DesktopCommandStream.accept(almost,last).state
        expect(exhausted.lastSequence==Long.MAX_VALUE && exhausted.windows.size==1)
        refused { DesktopCommandStream.next(exhausted,open) }
        expect(DesktopCommandStream.accept(exhausted,last).state==exhausted)
        expect(DesktopCommandStream.accept(exhausted,last.copy(sequence=Long.MIN_VALUE)).disposition==CommandDisposition.INVALID_SEQUENCE)
        val maximumBytes=ByteArrayOutputStream().also { DesktopCodec.write(exhausted,it) }.toByteArray()
        expect(DesktopCodec.read(ByteArrayInputStream(maximumBytes))==exhausted)
        // Actual production reductions for relative events; retrying the envelope applies no second delta.
        var relative=DesktopCommandStream.accept(empty,first).state
        val actions=listOf<WindowCommand>(
            WindowCommand.Move(1,5f,9f,800f,600f), WindowCommand.Resize(1,12f,8f,800f,600f),
            WindowCommand.FocusNext(),WindowCommand.TileWindow(1,Tile.MAXIMIZE,800f,600f),
            WindowCommand.RestoreBounds(1),WindowCommand.MoveToWorkspace(1,2),WindowCommand.SwitchWorkspace(2))
        repeat(32) {
            for(action in actions) {
                val envelope=DesktopCommandStream.next(relative,action)
                val once=DesktopCommandStream.accept(relative,envelope)
                expect(once.disposition==CommandDisposition.CONSUMED)
                val twice=DesktopCommandStream.accept(once.state,envelope)
                expect(twice.disposition==CommandDisposition.ALREADY_CONSUMED && twice.state==once.state)
                val bytes=ByteArrayOutputStream().also { DesktopCodec.write(once.state,it) }.toByteArray()
                val restored=DesktopCodec.read(ByteArrayInputStream(bytes))
                expect(restored==once.state)
                expect(DesktopCommandStream.accept(restored,envelope).state==restored)
                relative=restored
            }
        }
        for(action in listOf(WindowCommand.Move(1,10f,0f,1200f,900f),WindowCommand.Resize(1,10f,0f,1200f,900f))) {
            val initial=WindowEngine.reduce(DesktopState(),open)
            expect(WindowEngine.reduce(initial,action)==initial)
        }
    }
    @JvmStatic fun main(args:Array<String>) {
        streamChecks(File(args.single()))
        val legacy=File(args.single(),"desktop-v1.bin").inputStream().use(DesktopCodec::read)
        expect(legacy.windows.single().bounds==Bounds(23f,41f,400f,300f))
        expect(legacy.windows.single().restoreBounds==null && legacy.windows.single().tile==null)
        val published=WindowEngine.reduce(legacy,WindowCommand.Focus(1))
        refused { (published.windows as MutableList<ShellWindow>).clear() }
        refused { (legacy.windows as MutableList<ShellWindow>).clear() }
        // Patch-specific attack: no-op publication must not leak caller-owned input lists.
        val caller=legacy.windows.toMutableList()
        val admitted=WindowEngine.reduce(legacy.copy(windows=caller),WindowCommand.Close(999))
        caller.clear()
        expect(admitted.windows.size==1)
        refused { (admitted.windows as MutableList<ShellWindow>).add(legacy.windows.single()) }
        for(density in WindowDensity.entries) {
            var state=WindowEngine.reduce(DesktopState(screenWidth=1200f,screenHeight=900f,density=density),
                WindowCommand.Open("editor","Editor",Bounds(23f,41f,400f,300f),"restore"))
            val normal=state.windows.single().bounds
            for(tile in Tile.entries) {
                state=WindowEngine.reduce(state,WindowCommand.TileWindow(1,tile,1200f,900f))
                expect(state.windows.single().restoreBounds==normal)
                expect(state==WindowEngine.reduce(state,WindowCommand.TileWindow(1,tile,1200f,900f)))
                val encoded=ByteArrayOutputStream().also { DesktopCodec.write(state,it) }.toByteArray()
                val recovered=DesktopCodec.read(ByteArrayInputStream(encoded))
                expect(recovered==state)
                val restored=WindowEngine.reduce(recovered,WindowCommand.RestoreBounds(1))
                expect(restored.windows.single().bounds==normal && restored.windows.single().tile==null)
                expect(restored==WindowEngine.reduce(restored,WindowCommand.RestoreBounds(1)))
                expect(WindowEngine.reduce(recovered,WindowCommand.RestoreBounds(99))==recovered)
                refused { DesktopCodec.read(ByteArrayInputStream(encoded.copyOf(encoded.size-1))) }
            }
            val large=WindowEngine.reduce(state,WindowCommand.SetViewport(1600f,1000f))
            expect(large.windows.single().bounds==Bounds(0f,0f,1600f,1000f))
            expect(WindowEngine.reduce(large,WindowCommand.TileWindow(1,Tile.LEFT,1200f,900f))==large)
            val small=WindowEngine.reduce(large,WindowCommand.SetViewport(400f,500f))
            expect(!WindowEngine.canTile(small,Tile.LEFT))
            expect(WindowEngine.reduce(small,WindowCommand.TileWindow(1,Tile.LEFT,400f,500f))==small)
            val left=WindowEngine.reduce(large,WindowCommand.TileWindow(1,Tile.LEFT,1600f,1000f))
            val unfit=WindowEngine.reduce(left,WindowCommand.SetViewport(400f,500f))
            expect(unfit.windows.single().tile==null && unfit.windows.single().restoreBounds==null)
            expect(unfit.windows.single().bounds.width>=density.minWidth)
            val edit=WindowEngine.reduce(left,WindowCommand.SetBounds(1,normal))
            expect(edit.windows.single().restoreBounds==null)
            expect(WindowEngine.reduce(edit,WindowCommand.RestoreBounds(1))==edit)
            // Invalid saved pair must not be silently accepted as a recoverable tile.
            refused { WindowEngine.restore(state.copy(windows=state.windows.map { it.copy(restoreBounds=null) })) }
            refused { WindowEngine.restore(state.copy(windows=state.windows.map { it.copy(restoreBounds=Bounds(Float.NaN,0f,400f,300f)) })) }
        }
        for(width in listOf(1f,40f,239f,240f,479f,480f,639f,640f,1000f)) {
            val s=DesktopState(screenWidth=width,screenHeight=900f)
            expect(WindowEngine.canTile(s,Tile.LEFT)==(width>=480f))
            expect(WindowEngine.canTile(s,Tile.MAXIMIZE)==(width>=240f))
        }
        var state=WindowEngine.reduce(DesktopState(),WindowCommand.Open("editor","Editor",Bounds(0f,0f,400f,300f)))
        state=WindowEngine.reduce(state,WindowCommand.MoveToWorkspace(1,2))
        expect(state.workspace==0 && state.activeId==null)
        state=WindowEngine.reduce(state,WindowCommand.SwitchWorkspace(2))
        expect(state.activeId==1L)
        println("PASS: $checks window restore assertions; v1 historical migration, v2 historical migration, v3 ordered epoch/watermark replay protection, tile/normal geometry, density/viewport boundaries, no-op replays and workspace movement. No Android UI acceptance.")
    }
}
