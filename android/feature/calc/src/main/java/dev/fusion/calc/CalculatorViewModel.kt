package dev.fusion.calc

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.fusion.storage.CheckedAtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/** Activity-owned state survives module hiding/rotation; work and checkpoints run off main. */
class CalculatorViewModel(application: Application) : AndroidViewModel(application) {
    data class State(val session: CalculatorSession = CalculatorSession(), val loading: Boolean = true,
                     val busy: Boolean = false, val recoveryBlocked: Boolean = false,
                     val message: String = "Loading local calculator history…", val checkpoint: String = "Not yet loaded")
    private val mutable=MutableStateFlow(State())
    val state=mutable.asStateFlow()
    private val file=AtomicFile(File(application.noBackupFilesDir,"calculator-v1.bin"))
    private val disk=Mutex()
    private val updates=Channel<Unit>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            try {
                val session=withContext(Dispatchers.IO) {
                    if(file.baseFile.exists() || File(file.baseFile.path+".bak").exists()) file.openRead().use(CalculatorHistory::read)
                    else CalculatorSession()
                }
                mutable.value=State(session,loading=false,message="Local history only; recovered calculations are not re-executed.",checkpoint="Loaded")
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                mutable.value=State(loading=false,recoveryBlocked=true,
                    message="Recovery rejected (${e.javaClass.simpleName}). Existing bytes retained; explicitly reset below to continue.",checkpoint="Recovery blocked")
            }
            for(ignored in updates) { delay(500); flush() }
        }
    }
    private fun ready()=!state.value.loading && !state.value.busy && !state.value.recoveryBlocked
    private fun update(session: CalculatorSession) {
        mutable.value=state.value.copy(session=session,checkpoint="Checkpoint pending (not a backup)")
        updates.trySend(Unit)
    }
    fun setRequest(request: CalculationRequest) {
        if(!ready()) return
        if(request.expression.length>Calculator.MAX_INPUT) {
            mutable.value=state.value.copy(message="Input rejected: limit ${Calculator.MAX_INPUT} characters."); return
        }
        update(state.value.session.copy(request=request))
        mutable.value=state.value.copy(message="Input or context changed; press Calculate. Existing results keep their original context.")
    }
    fun evaluate() {
        if(!ready()) return
        val request=state.value.session.request
        mutable.value=state.value.copy(busy=true,message="Calculating…")
        viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.Default) { Calculator.calculate(request) }
                result.fold(onSuccess={ calculation ->
                    val record=CalculationRecord(UUID.randomUUID().toString(),System.currentTimeMillis().coerceAtLeast(0),calculation)
                    update(CalculatorHistory.append(state.value.session,record))
                    mutable.value=state.value.copy(message="Calculated with the context shown in the result. Last 100 results retained locally.")
                }, onFailure={ error ->
                    mutable.value=state.value.copy(message="Cannot calculate: ${error.message?.take(240) ?: error.javaClass.simpleName}. Previous history unchanged.")
                })
            } finally { mutable.value=state.value.copy(busy=false) }
        }
    }
    fun recall(record: CalculationRecord) {
        if(!ready()) return
        setRequest(record.calculation.request)
        mutable.value=state.value.copy(message="Recalled input and its original mode/angle/radix/units. Press Calculate to recompute.")
    }
    /** R51 pure-JVM Goal Seek preview (Brent/Secant root finder; never mutates calculator history). */
    fun previewLinearGoalSeek(slope: Double, intercept: Double, target: Double, initialGuess: Double = 1.0): GoalSeekSimplexSolver.GoalSeekResult =
        GoalSeekSimplexSolver.goalSeek(targetValue = target, initialGuess = initialGuess) { x -> slope * x + intercept }
    /** R51 pure-JVM Simplex LP preview for resource-allocation inspection. */
    fun previewSimplexLp(objective: List<Double>, lhs: List<List<Double>>, rhs: List<Double>): GoalSeekSimplexSolver.LpSolution =
        GoalSeekSimplexSolver.solveLinearProgramMax(objective = objective, lhs = lhs, rhs = rhs)
    /** UI requires explicit confirmation. Failure does not claim deletion or secure erasure. */
    fun clearHistory() {
        if(!ready()) return
        update(state.value.session.copy(history=emptyList()))
        mutable.value=state.value.copy(busy=true)
        viewModelScope.launch {
            try {
                mutable.value=state.value.copy(message=if(flush()) "History cleared from the current checkpoint. Retained snapshots/backups are not securely erased."
                    else "History clear could not be confirmed on disk; old entries may reappear after relaunch.")
            } finally { mutable.value=state.value.copy(busy=false) }
        }
    }
    suspend fun flush(): Boolean {
        if(state.value.loading || state.value.recoveryBlocked) return false
        return try {
            disk.withLock {
                val snapshot=state.value.session
                withContext(Dispatchers.IO) { CheckedAtomicFile.write(file,snapshot,CalculatorHistory::write,CalculatorHistory::read) }
                if(state.value.session==snapshot) mutable.value=state.value.copy(checkpoint="Local checkpoint read back and verified; not a backup")
            }
            true
        } catch(e: Exception) {
            if(e is CancellationException) throw e
            mutable.value=state.value.copy(checkpoint="Checkpoint unconfirmed (${e.javaClass.simpleName}); keep this app open if the result matters")
            false
        }
    }
    fun resetRecovery() {
        if(!state.value.recoveryBlocked || state.value.loading || state.value.busy) return
        mutable.value=state.value.copy(loading=true)
        viewModelScope.launch {
            try {
                disk.withLock {
                    withContext(Dispatchers.IO) {
                        for(suffix in listOf("", ".bak", ".new")) {
                            val source=File(file.baseFile.path+suffix)
                            if(source.exists()) check(source.renameTo(File(source.parentFile,"calculator-retained-${UUID.randomUUID()}.bin")))
                        }
                        CheckedAtomicFile.write(file,CalculatorSession(),CalculatorHistory::write,CalculatorHistory::read)
                    }
                }
                mutable.value=State(loading=false,message="Empty calculator session created. Old snapshot bytes retained; editor and layout data unchanged.",checkpoint="Verified empty checkpoint")
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                mutable.value=state.value.copy(loading=false,recoveryBlocked=true,message="Reset failed (${e.javaClass.simpleName}); no persistence claim.")
            }
        }
    }
}
